#!/usr/bin/env node
/**
 * Local dev helper — generates an RSA key pair, serves a JWKS on :8081,
 * inserts a dev company into the DB (if missing), and prints a signed JWT
 * ready to paste into the sign-in form.
 *
 * Usage:  node scripts/dev-jwt.mjs
 * Stop:   Ctrl+C  (keep running while using the app so the backend can fetch JWKS)
 */

import { createServer } from 'node:http';
import { generateKeyPairSync, createSign } from 'node:crypto';
import { execSync } from 'node:child_process';

// ── Config ────────────────────────────────────────────────────────────────────
const TENANT_ID    = '00000000-0000-0000-0000-000000000001';
const COMPANY_NAME = 'Dev Company';
const SUB          = 'dev-user';
const SCOPES       = 'inventory:read inventory:write audit:read audit:query audit:trigger compliance:read split_payment:read split_payment:write ingestion:write';
const ISSUER       = 'http://localhost:8081';
const JWKS_PORT    = 8081;
const TTL_HOURS    = 8;

// ── Key generation ────────────────────────────────────────────────────────────
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwkPublic = publicKey.export({ format: 'jwk' });
const kid = 'dev-key-1';
const jwks = { keys: [{ ...jwkPublic, kid, use: 'sig', alg: 'RS256' }] };

// ── JWT mint ──────────────────────────────────────────────────────────────────
function b64url(obj) {
  return Buffer.from(JSON.stringify(obj)).toString('base64url');
}

const header  = b64url({ alg: 'RS256', typ: 'JWT', kid });
const now     = Math.floor(Date.now() / 1000);
const payload = b64url({
  iss: ISSUER,
  sub: SUB,
  tenant_id: TENANT_ID,
  scope: SCOPES,
  iat: now,
  exp: now + TTL_HOURS * 3600,
});

const sigInput = `${header}.${payload}`;
const signer   = createSign('RSA-SHA256');
signer.update(sigInput);
const sig = signer.sign(privateKey, 'base64url');
const jwt = `${sigInput}.${sig}`;

// ── Seed DB company ───────────────────────────────────────────────────────────
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
if (!UUID_RE.test(TENANT_ID)) throw new Error(`TENANT_ID is not a valid UUID: ${TENANT_ID}`);
const safeName = COMPANY_NAME.replace(/[^A-Za-z0-9 _\-]/g, '');
if (!safeName) throw new Error('COMPANY_NAME contains no safe characters');
const sql = `INSERT INTO companies (id, name, cnpj) VALUES ('${TENANT_ID}', '${safeName}', '00.000.000/0001-00') ON CONFLICT (id) DO NOTHING;`;
try {
  execSync(`docker exec fiscalbrain_db psql -U fiscal_user -d fiscalbrain -c "${sql}"`, { stdio: 'pipe' });
  console.log(`✔  Company seeded (id=${TENANT_ID})`);
} catch (e) {
  console.warn('⚠  Could not seed company:', e.stderr?.toString().trim() || e.message);
}

// ── JWKS server ───────────────────────────────────────────────────────────────
createServer((req, res) => {
  if (req.url === '/.well-known/jwks.json') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(jwks));
  } else {
    res.writeHead(404);
    res.end();
  }
}).listen(JWKS_PORT, () => {
  console.log(`✔  JWKS server → http://localhost:${JWKS_PORT}/.well-known/jwks.json\n`);
  console.log('── JWT (paste into sign-in form) ────────────────────────────────────────\n');
  console.log(jwt);
  console.log('\n─────────────────────────────────────────────────────────────────────────');
  console.log(`\nKeep this process running while using the app (TTL: ${TTL_HOURS}h).\n`);
});
