# Security model

The generator treats the destination tree as a trust boundary. All adapter paths must remain below the normalized project root. Different existing content is a conflict unless the caller explicitly selects force; temporary same-directory files limit partial replacement risk.

Backsmith never generates real secrets. Generated `.env.example` values are local placeholders, production configuration reads secrets from the environment, and JWT HMAC keys must be at least 32 bytes.

Generated applications can use no security, HTTP Basic, server sessions, JWT, OAuth2, or OIDC resource-server validation. The optional authentication starter persists accounts, BCrypt password hashes, roles, tenant memberships, and hashed rotating refresh tokens. Reuse of a revoked refresh-token family is rejected and revokes the remaining family.

The optional API Gateway is authenticated by default and rejects configurations that require authentication without a security mode. Its generated route removes browser cookies before proxying, limits request bodies and headers, uses principal-or-remote-address token buckets, returns HTTP 429 when exhausted, trusts forwarded headers only from an operator-supplied proxy regex, and adds HSTS-on-HTTPS, CSP, frame, MIME-sniffing, referrer, permissions, and cross-domain response headers. CORS remains an explicit allowlist.

The default Bucket4j/Caffeine limiter is deliberately local to one gateway process. It provides exact per-client limits for a single replica and per-replica protection when horizontally scaled. Deployments that require a single global quota must replace the generated `AsyncProxyManager` with a shared Bucket4j backend and test its failure policy. See [API gateway](api-gateway.md).

Shared-schema tenancy resolves a validated tenant identifier per request and checks JWT membership before establishing tenant context. Generated customer, payment, cache, outbox, and idempotency paths include tenant scope. Applications must still enforce tenant scope in every custom query and integration added after generation.

Security failures use `application/problem+json`; logs and errors avoid raw tokens and credentials. Operators remain responsible for TLS, key rotation, identity-provider policy, network controls, dependency response, data classification, and compliance review.
