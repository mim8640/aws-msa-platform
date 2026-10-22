# AWS EKS MSA Platform

Production-oriented reference project for:

- React frontend
- Spring Cloud Gateway
- Member / Product / Order Spring Boot services
- MySQL (local Docker Compose; use RDS in AWS)
- Redis (local Docker Compose; use ElastiCache in AWS)
- S3 presigned uploads (Product service)
- ECR + GitHub Actions
- Kubernetes + Kustomize
- Argo CD + Argo Rollouts
- Prometheus/Grafana-ready Actuator metrics

> This repository is a deployable starter, not a substitute for environment-specific security review.
> The included Kubernetes secrets are placeholders only. Never commit real credentials.

## 1. Local quick start

Requirements: Docker Desktop with Compose.

```bash
docker compose up --build
```

Endpoints:

- Frontend: http://localhost:3000
- Gateway: http://localhost:8080
- Member: http://localhost:8081
- Product: http://localhost:8082
- Order: http://localhost:8083

Default local MySQL root password: `local-root-only`
Default app password: `local-app-only`

Create a member:

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"ChangeMe123!","name":"Tester"}'
```

Login:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"ChangeMe123!"}'
```

## 2. AWS assumptions

Replace these placeholders before deployment:

- `YOUR_DOMAIN`
- `YOUR_AWS_ACCOUNT_ID`
- `YOUR_AWS_REGION`
- `YOUR_ECR_REGISTRY`
- `YOUR_S3_BUCKET`
- RDS/Redis endpoints
- Secrets Manager / External Secrets integration values

Recommended DNS:

- `www.YOUR_DOMAIN` -> frontend ingress
- `api.YOUR_DOMAIN` -> gateway ingress
- `argocd.YOUR_DOMAIN`
- `grafana.YOUR_DOMAIN`
- `prometheus.YOUR_DOMAIN`

## 3. Kubernetes

Install ingress-nginx, cert-manager, metrics-server, Argo CD, Argo Rollouts and monitoring separately.

Apply application manifests:

```bash
kubectl apply -k k8s/overlays/dev
```

For production:

```bash
kubectl apply -k k8s/overlays/prod
```

Argo CD should own production resources after bootstrap. Avoid manually applying the same resources after Argo CD takes ownership.

## 4. GitHub Actions

Workflow builds only changed services, pushes immutable `${GITHUB_SHA}` tags to ECR, and updates the GitOps manifests.

Required GitHub repository variables:

- `AWS_REGION`
- `AWS_ROLE_TO_ASSUME`
- `ECR_REGISTRY`

Use GitHub OIDC; do not store long-lived AWS access keys.

## 5. S3

Product service returns a presigned PUT URL:

`POST /api/v1/products/images/presigned-url?filename=photo.jpg&contentType=image/jpeg`

The browser uploads directly to S3, then sends the resulting object key when creating the product.

## 6. Important production follow-ups

- Replace placeholder K8s Secret with External Secrets + AWS Secrets Manager.
- Use RDS TLS and validate certificates.
- Put RDS/ElastiCache in private subnets.
- Restrict security groups to service-to-service requirements.
- Add AWS WAF at the public entry point if appropriate.
- Add PodDisruptionBudgets, NetworkPolicies and topology spread constraints.
- Add database migrations via Flyway/Liquibase with a migration-specific identity.
- Use Pod Identity for S3 access.
- Pin all container base images by digest in regulated/high-control environments.
