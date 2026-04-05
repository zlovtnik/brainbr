use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
    Json,
};
use jsonwebtoken::{decode, decode_header, Algorithm, DecodingKey, Validation};
use serde_json::Value;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::RwLock;

use crate::config::SecurityConfig;

#[derive(Debug, Clone)]
pub struct AuthenticatedClaims {
    pub sub: String,
    pub tenant_claim: Option<String>,
    pub scopes: Vec<String>,
    pub raw: Value,
}

struct JwksCache {
    value: Value,
    expires_at: Instant,
}

pub struct JwksState {
    pub jwks_uri: Option<String>,
    pub issuer_uri: Option<String>,
    pub tenant_claim: String,
    pub enabled: bool,
    pub dev_tenant_id: Option<String>,
    pub audience: Option<String>,
    client: reqwest::Client,
    cache: RwLock<Option<JwksCache>>,
    /// Caches the resolved JWKS URL after first OIDC discovery so we don't
    /// re-fetch openid-configuration on every request.
    resolved_jwks_url: RwLock<Option<String>>,
}

// Manual Clone: RwLock<Option<JwksCache>> is not Clone, so we share via Arc instead.
// JwksState is always wrapped in Arc<JwksState>.

impl JwksState {
    pub fn new(config: &SecurityConfig) -> Self {
        Self {
            jwks_uri: config.jwt_jwk_set_uri.clone(),
            issuer_uri: config.jwt_issuer_uri.clone(),
            tenant_claim: config.jwt_tenant_claim.clone(),
            enabled: config.jwt_enabled,
            dev_tenant_id: config.dev_tenant_id.clone(),
            audience: config.jwt_audience.clone(),
            client: reqwest::Client::builder()
                .timeout(Duration::from_secs(10))
                .build()
                .expect("Failed to build HTTP client"),
            cache: RwLock::new(None),
            resolved_jwks_url: RwLock::new(None),
        }
    }

    async fn fetch_jwks(&self, jwks_url: &str) -> anyhow::Result<Value> {
        // Fast path: return cached JWKS if still valid
        {
            let guard = self.cache.read().await;
            if let Some(ref c) = *guard {
                if Instant::now() < c.expires_at {
                    return Ok(c.value.clone());
                }
            }
        }
        // Slow path: refresh — check HTTP status before parsing to avoid caching error payloads
        let resp = self.client.get(jwks_url).send().await?.error_for_status()?;
        let jwks: Value = resp.json().await?;
        // Validate JWKS shape: must have a top-level "keys" array with at least one object
        let keys = jwks["keys"].as_array()
            .filter(|a| !a.is_empty() && a.iter().all(|k| k["kty"].is_string()))
            .ok_or_else(|| anyhow::anyhow!("Invalid JWKS: missing or empty 'keys' array"))?;
        let _ = keys; // shape validated
        let mut guard = self.cache.write().await;
        *guard = Some(JwksCache {
            value: jwks.clone(),
            expires_at: Instant::now() + Duration::from_secs(300),
        });
        Ok(jwks)
    }

    pub async fn validate_token(&self, token: &str) -> anyhow::Result<AuthenticatedClaims> {
        // Resolve JWKS URL once: prefer explicit jwks_uri (validated https), then
        // OIDC discovery (cached after first successful fetch).
        let jwks_url = if let Some(uri) = &self.jwks_uri {
            if !uri.starts_with("https://") {
                return Err(anyhow::anyhow!("jwt_jwk_set_uri must be an https URL: {uri}"));
            }
            uri.clone()
        } else if let Some(issuer) = &self.issuer_uri {
            // Fast path: return cached discovered URL
            {
                let guard = self.resolved_jwks_url.read().await;
                if let Some(ref url) = *guard {
                    url.clone()
                } else {
                    drop(guard);
                    let discovery_url = format!("{}/.well-known/openid-configuration", issuer.trim_end_matches('/'));
                    let config: Value = self.client.get(&discovery_url).send().await?.error_for_status()?.json().await?;
                    let discovered = config["jwks_uri"].as_str()
                        .ok_or_else(|| anyhow::anyhow!("OIDC discovery response missing 'jwks_uri'"))?
                        .to_string();
                    if !discovered.starts_with("https://") {
                        return Err(anyhow::anyhow!("Discovered jwks_uri is not an https URL: {discovered}"));
                    }
                    *self.resolved_jwks_url.write().await = Some(discovered.clone());
                    discovered
                }
            }
        } else {
            return Err(anyhow::anyhow!("No JWT source configured: set APP_SECURITY_JWT_JWK_SET_URI or APP_SECURITY_JWT_ISSUER_URI"));
        };

        let jwks = self.fetch_jwks(&jwks_url).await?;
        let header = decode_header(token)?;

        let kid = header.kid.as_deref().unwrap_or("");
        let keys = jwks["keys"].as_array().ok_or_else(|| anyhow::anyhow!("Invalid JWKS: missing 'keys' array"))?;

        // If kid is present, require an exact match. If absent, try all keys.
        let key_json = if !kid.is_empty() {
            keys.iter()
                .find(|k| k["kid"].as_str().unwrap_or("") == kid)
                .ok_or_else(|| anyhow::anyhow!("No matching JWK found for kid={kid}"))?
        } else {
            // No kid in header — attempt verification with each key in order.
            let mut matched = None;
            for key in keys {
                let n = key["n"].as_str().filter(|s| !s.is_empty());
                let e = key["e"].as_str().filter(|s| !s.is_empty());
                if let (Some(n), Some(e)) = (n, e) {
                    if let Ok(dk) = DecodingKey::from_rsa_components(n, e) {
                        let mut v = Validation::new(Algorithm::RS256);
                        if let Some(aud) = &self.audience { v.set_audience(&[aud.as_str()]); } else { v.validate_aud = false; }
                        if let Some(iss) = &self.issuer_uri { v.set_issuer(&[iss.as_str()]); }
                        if decode::<Value>(token, &dk, &v).is_ok() {
                            matched = Some(key);
                            break;
                        }
                    }
                }
            }
            matched.ok_or_else(|| anyhow::anyhow!("No JWK verified the token signature"))?
        };

        let n = key_json["n"].as_str()
            .filter(|s| !s.is_empty())
            .ok_or_else(|| anyhow::anyhow!("JWK missing modulus 'n'"))?;
        let e = key_json["e"].as_str()
            .filter(|s| !s.is_empty())
            .ok_or_else(|| anyhow::anyhow!("JWK missing exponent 'e'"))?;

        let decoding_key = DecodingKey::from_rsa_components(n, e)?;

        let mut validation = Validation::new(Algorithm::RS256);
        if let Some(aud) = &self.audience { validation.set_audience(&[aud.as_str()]); } else { validation.validate_aud = false; }
        if let Some(iss) = &self.issuer_uri {
            validation.set_issuer(&[iss.as_str()]);
        }

        let token_data = decode::<Value>(token, &decoding_key, &validation)?;
        let claims = token_data.claims;

        let tenant_claim = claims[&self.tenant_claim].as_str().map(String::from);
        let scopes = claims["scope"].as_str()
            .map(|s| s.split_whitespace().map(String::from).collect())
            .unwrap_or_default();
        let sub = claims.get("sub")
            .and_then(|v| v.as_str())
            .filter(|s| !s.is_empty())
            .ok_or_else(|| anyhow::anyhow!("Invalid token: missing or empty 'sub' claim"))?
            .to_string();

        Ok(AuthenticatedClaims { sub, tenant_claim, scopes, raw: claims })
    }
}

pub async fn auth_middleware(
    State(jwks): State<Arc<JwksState>>,
    mut req: Request,
    next: Next,
) -> Response {
    if !jwks.enabled {
        req.extensions_mut().insert(AuthenticatedClaims {
            sub: "dev".into(),
            tenant_claim: jwks.dev_tenant_id.clone(),
            scopes: vec!["*".into()],
            raw: Value::Null,
        });
        return next.run(req).await;
    }

    let token = req.headers()
        .get("Authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "));

    let Some(token) = token else {
        return (StatusCode::UNAUTHORIZED, Json(serde_json::json!({
            "error_code": "UNAUTHORIZED",
            "message": "Missing or invalid credentials"
        }))).into_response();
    };

    match jwks.validate_token(token).await {
        Ok(claims) => {
            req.extensions_mut().insert(claims);
            next.run(req).await
        }
        Err(e) => {
            tracing::debug!("JWT validation failed: {e}");
            (StatusCode::UNAUTHORIZED, Json(serde_json::json!({
                "error_code": "UNAUTHORIZED",
                "message": "Missing or invalid credentials"
            }))).into_response()
        }
    }
}

pub fn require_scope(claims: &AuthenticatedClaims, scope: &str) -> Result<(), StatusCode> {
    if claims.scopes.contains(&"*".to_string()) || claims.scopes.contains(&scope.to_string()) {
        Ok(())
    } else {
        Err(StatusCode::FORBIDDEN)
    }
}
