# Security model

The generator treats the destination tree as a trust boundary. All adapter paths must remain below the normalized project root. Different existing content is a conflict unless the caller explicitly selects force; temporary same-directory files limit partial replacement risk.

Backsmith never generates real secrets. Generated `.env.example` values are local placeholders, production configuration reads secrets from the environment, and JWT HMAC keys must be at least 32 bytes.

Generated applications can use no security, HTTP Basic, server sessions, JWT, OAuth2, or OIDC resource-server validation. The optional authentication starter persists accounts, BCrypt password hashes, roles, tenant memberships, and hashed rotating refresh tokens. Reuse of a revoked refresh-token family is rejected and revokes the remaining family.

Shared-schema tenancy resolves a validated tenant identifier per request and checks JWT membership before establishing tenant context. Generated customer, payment, cache, outbox, and idempotency paths include tenant scope. Applications must still enforce tenant scope in every custom query and integration added after generation.

Security failures use `application/problem+json`; logs and errors avoid raw tokens and credentials. Operators remain responsible for TLS, key rotation, identity-provider policy, network controls, dependency response, data classification, and compliance review.
