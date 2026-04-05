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
    client: reqwest::Client,
    cache: RwLock<Option<JwksCache>>,
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
            client: reqwest::Client::builder()
                .timeout(Duration::from_secs(10))
                .build()
                .expect("Failed to build HTTP client"),
            cache: RwLock::new(None),
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
        // Slow path: refresh
        let jwks: Value = self.client.get(jwks_url).send().await?.json().await?;
        let mut guard = self.cache.write().await;
        *guard = Some(JwksCache {
            value: jwks.clone(),
            expires_at: Instant::now() + Duration::from_secs(300), // 5-minute TTL
        });
        Ok(jwks)
    }

    pub async fn validate_token(&self, token: &str) -> anyhow::Result<AuthenticatedClaims> {
        let jwks_uri = self.jwks_uri.as_deref()
            .or(self.issuer_uri.as_deref())
            .ok_or_else(|| anyhow::anyhow!("No JWT source configured"))?;

        let jwks_url = if self.jwks_uri.is_some() {
            jwks_uri.to_string()
        } else {
            format!("{}/.well-known/jwks.json", jwks_uri.trim_end_matches('/'))
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
                        v.validate_aud = false;
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
        validation.validate_aud = false; // audience not required unless explicitly configured
        if let Some(iss) = &self.issuer_uri {
            validation.set_issuer(&[iss.as_str()]);
        }

        let token_data = decode::<Value>(token, &decoding_key, &validation)?;
        let claims = token_data.claims;

        let tenant_claim = claims[&self.tenant_claim].as_str().map(String::from);
        let scopes = claims["scope"].as_str()
            .map(|s| s.split_whitespace().map(String::from).collect())
            .unwrap_or_default();
        let sub = claims["sub"].as_str().unwrap_or("").to_string();

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
