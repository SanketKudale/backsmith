# API gateway

Backsmith can generate an embedded Spring Cloud Gateway Server MVC route without switching a blocking Spring MVC/JPA application to WebFlux.

```shell
backsmith create edge-service \
  --architecture microservice \
  --api-gateway \
  --gateway-upstream http://orders:8080 \
  --gateway-route '/gateway/**' \
  --gateway-requests-per-minute 120 \
  --yes
```

Unless `--gateway-public` is present, the CLI selects JWT security when the user did not choose another security mode. The normal Basic, session, JWT, OAuth2, or OIDC security chain protects the gateway path. Authorization headers continue downstream while browser cookies are removed.

## Generated controls

- HTTP(S)-only upstream origins with embedded credentials, queries, and fragments rejected
- explicit route pattern ending in `/**` and optional one-segment prefix stripping
- Bucket4j token bucket keyed by the authenticated principal or direct remote address
- HTTP 429 responses and remaining-token headers
- configurable request-body and request-header limits with HTTP 413/431 rejection
- CORS allowlist integration
- trusted-proxy regular expression for forwarded-header processing
- removal of hop-by-hop headers by Spring Cloud Gateway and explicit browser-cookie removal
- HSTS on secure requests, CSP, anti-framing, MIME-sniffing, referrer, permissions, and cross-domain response headers
- Actuator health, metrics, Prometheus, correlation IDs, and structured logging through the generated application foundation

Environment overrides are available through `GATEWAY_UPSTREAM_URI`, `GATEWAY_ROUTE_PATH`, `GATEWAY_STRIP_PREFIX`, `GATEWAY_REQUESTS_PER_MINUTE`, `GATEWAY_MAX_REQUEST_SIZE`, `GATEWAY_MAX_HEADER_SIZE`, and `GATEWAY_TRUSTED_PROXIES`.

## Deployment notes

`GATEWAY_TRUSTED_PROXIES` is a Java regular expression matched by Spring Cloud Gateway; it is not CIDR syntax. Keep the default loopback-only expression unless a known reverse proxy or load balancer is in front of the application.

The generated Caffeine proxy manager keeps token buckets in one process. It enforces exact limits for one replica and per-replica protection for multiple replicas. If the product requires a global quota, replace the `AsyncProxyManager<String>` bean with a shared Bucket4j integration such as Redis or another supported distributed backend. Decide explicitly whether datastore failure should fail closed or allow degraded traffic, and load-test that policy.

Do not place credentials in `upstream_uri`. Keep service credentials in the platform secret manager, terminate public TLS at a controlled edge, restrict network access from the gateway to intended upstreams, and review route authorization separately from downstream authorization.
