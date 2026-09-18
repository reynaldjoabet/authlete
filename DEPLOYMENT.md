# Production Deployment Guide

> **Status.** Configuration and health-check sections are verified against
> `src/main/resources/application.conf`. Packaging uses sbt-assembly 2.5.0 (published for sbt 2 as
> `sbt-assembly_sbt2_3`); `sbt assembly` produces `target/authlete.jar`, which has been run and
> exercised directly. The Docker build itself has not been executed here -- no daemon was available
> -- so treat the image steps as unverified until `make image` has run once.

## Overview

This guide covers deploying the Authlete Scala Authorization Server to production environments.

## Prerequisites

- Authlete 3.0 service (https://us.authlete.com or your regional endpoint)
- Service ID and API token from Authlete
- JWT JWKS URI for token validation
- auth-ui instance or similar interaction application

## Configuration

All configuration is via environment variables. Copy `.env.example` to `.env` (development) or set in your deployment platform:

| Variable | Required | Description |
|----------|----------|-------------|
| `AUTHLETE_BASE_URL` | ✅ | Authlete cluster URL (default: `https://us.authlete.com`) |
| `AUTHLETE_SERVICE_ID` | ✅ | Numeric service ID from Authlete console |
| `AUTHLETE_SERVICE_ACCESSTOKEN` | ✅ | Service access token (never commit to Git) |
| `JWT_JWKS_URI` | ✅ | JWKS endpoint for token validation |
| `JWT_EXPECTED_ISSUER` | ✅ | Expected JWT issuer (e.g., `https://your-idp.com`) |
| `JWT_EXPECTED_AUDIENCE` | ✅ | Comma-separated list of allowed audiences |
| `SERVER_HOST` | | Bind address (default: `0.0.0.0` in container, `127.0.0.1` local dev) |
| `SERVER_PORT` | | Listen port (default: `8080`) |
| `SERVER_SHUTDOWN_TIMEOUT` | | Graceful shutdown window (default: `30`), must be less than k8s `terminationGracePeriodSeconds` |
| `AUTHLETE_DPOP_ENABLED` | | Enable DPoP validation (default: `false`) |
| `AUTHLETE_DPOP_KEY` | | DPoP signing key (ES256 JWKS) if enabled |
| `AUTHLETE_CLIENT_CERTIFICATE` | | Client certificate for mTLS to Authlete (PEM format) |
| `INTERACTION_BASE_URL` | | Where the browser is sent when a request needs login or consent; the Authlete ticket is appended as a path segment. Must be absolute. Required together with the secret below. |
| `INTERACTION_SHARED_SECRET` | | Bearer credential the interaction application presents at `/api/v1/authorization/decision`. That callback grants consent, so guard it like `AUTHLETE_SERVICE_ACCESSTOKEN`. |

Setting exactly one of the two `INTERACTION_*` variables fails at boot: a base URL with no secret
would redirect users to an application whose decisions this server could not authenticate. Setting
neither is valid — the decision callback is then not served at all, and the authorization endpoint
reports the gap if a request actually needs interaction.

Note there is no `LOG_LEVEL`: logging is configured through scribe/logback, not an environment
variable read by this application.

### Secrets Management

**Never commit secrets to Git.** In production:

- **Kubernetes:** Use Secrets
  ```yaml
  apiVersion: v1
  kind: Secret
  metadata:
    name: authlete-secrets
  type: Opaque
  stringData:
    AUTHLETE_SERVICE_ACCESSTOKEN: "your-token-here"
  ```

- **AWS:** Use Secrets Manager or Parameter Store
- **GCP:** Use Secret Manager
- **Azure:** Use Key Vault
- **Docker:** Use `--env-file` (development only, never in production)

## Docker Deployment

### Build

```bash
make image          # or: docker build -t authlete-as:latest .
```

The jar can also be built and run without Docker, which is the quickest way to check a change:

```bash
make jar
java -jar target/authlete.jar
```

### Run Locally

```bash
docker run -it \
  --env-file .env \
  -p 8080:8080 \
  authlete-as:latest
```

### Health Checks

Two endpoints for orchestrator health checks:

```bash
# Liveness (is the process alive?)
curl http://localhost:8080/health/live
# → {"status":"ok"}

# Readiness (is it ready for traffic?)
curl http://localhost:8080/health/ready
# → {"status":"ok","checks":{"jwks":"ok"}}
```

**Probe configuration (Kubernetes example):**

```yaml
livenessProbe:
  httpGet:
    path: /health/live
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 10
  timeoutSeconds: 5

readinessProbe:
  httpGet:
    path: /health/ready
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  timeoutSeconds: 5
```

## Kubernetes Deployment

### Manifest Template

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: authlete-as
spec:
  replicas: 3
  selector:
    matchLabels:
      app: authlete-as
  template:
    metadata:
      labels:
        app: authlete-as
    spec:
      # Pod terminates gracefully within this window
      terminationGracePeriodSeconds: 45
      containers:
      - name: authlete-as
        image: your-registry/authlete-as:v1.0.0
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: AUTHLETE_BASE_URL
          value: "https://us.authlete.com"
        - name: AUTHLETE_SERVICE_ID
          valueFrom:
            configMapKeyRef:
              name: authlete-config
              key: service_id
        - name: AUTHLETE_SERVICE_ACCESSTOKEN
          valueFrom:
            secretKeyRef:
              name: authlete-secrets
              key: service_accesstoken
        - name: JWT_JWKS_URI
          valueFrom:
            configMapKeyRef:
              name: authlete-config
              key: jwt_jwks_uri
        - name: JWT_EXPECTED_ISSUER
          valueFrom:
            configMapKeyRef:
              name: authlete-config
              key: jwt_expected_issuer
        - name: JWT_EXPECTED_AUDIENCE
          valueFrom:
            configMapKeyRef:
              name: authlete-config
              key: jwt_expected_audiences
        - name: SERVER_SHUTDOWN_TIMEOUT
          value: "40"
        
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3

        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 5
          failureThreshold: 2

        resources:
          requests:
            cpu: 500m
            memory: 512Mi
          limits:
            cpu: 2000m
            memory: 1Gi

        securityContext:
          runAsNonRoot: true
          runAsUser: 1000
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: false
          capabilities:
            drop:
            - ALL

      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - authlete-as
              topologyKey: kubernetes.io/hostname

---
apiVersion: v1
kind: Service
metadata:
  name: authlete-as
spec:
  type: ClusterIP
  selector:
    app: authlete-as
  ports:
  - port: 8080
    targetPort: 8080
    name: http
```

## Operational Concerns

### Logging

Structured JSON logging goes to stdout. Pipe to your logging infrastructure:

```bash
docker logs <container> | jq .
```

### Metrics

Currently no built-in metrics endpoint. Add Prometheus later if needed.

### Shutdown

The server handles SIGTERM gracefully:
1. Stops accepting new requests
2. Drains in-flight requests (up to `SERVER_SHUTDOWN_TIMEOUT`)
3. Exits with code 0

### SSL/TLS

For production, use a reverse proxy (nginx, Envoy, Istio) or load balancer to terminate TLS. The AS listens on plain HTTP.

### Database

**None required.** All state is in Authlete. The AS is stateless and can be horizontally scaled.

### Scaling

- Stateless design means horizontal scaling is straightforward
- No warm-up needed; any replica can serve traffic immediately
- Use a load balancer to distribute traffic
- Multiple replicas provide high availability

### Rate Limiting

Not built-in. Apply at your reverse proxy, load balancer, or API gateway.

### Network

- Outbound: Must reach Authlete cluster (configure via `AUTHLETE_BASE_URL`)
- Outbound: Must reach JWT JWKS URI (to validate tokens)
- Inbound: Accepts on `SERVER_PORT`

## Troubleshooting

### "JWKS cache warmed" but startup still succeeds

Normal. If the JWKS endpoint is unreachable at startup, the server still starts but `/health/ready` returns false, holding traffic at the load balancer until the JWKS endpoint recovers.

### "Invalid configuration" on startup

Configuration problem. Check all required environment variables are set and non-empty.

### 500 errors in logs

Check Authlete connectivity and credentials. Logs include the error reason.

### Slow authorization endpoint

Usually Authlete latency. Check network path and Authlete service status.

## Security Checklist

- [ ] `AUTHLETE_SERVICE_ACCESSTOKEN` stored in secure secret management (not in code/config files)
- [ ] Reverse proxy enforces TLS (server listens on plain HTTP)
- [ ] CORS origins restricted (or disabled if not needed)
- [ ] DPoP enabled if using token-binding-aware clients
- [ ] Server runs as non-root user in container
- [ ] Logs do not capture secrets (Authorization headers redacted)
- [ ] Shutdown timeout shorter than orchestrator kill grace period
- [ ] Health checks configured to detect stuck replicas
