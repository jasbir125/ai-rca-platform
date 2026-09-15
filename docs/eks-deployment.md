# EKS / Kubernetes Deployment

Assumes `docs/aws-setup.md` has already provisioned the cluster and ECR repositories,
and images have been pushed. The same Helm chart also deploys to any other Kubernetes
cluster (kind, Docker Desktop's built-in Kubernetes, GKE, etc.) for testing — only the
`ingress.className`/`serviceAccount.annotations` are genuinely EKS/ALB-specific.

## The chart

`infrastructure/helm/rca-platform` — one chart, three deployables (`order-service`,
`payment-service`, `rca-api`) driven by a `services` map in `values.yaml` rather than
one template set per service, so adding a fourth service later is a values.yaml entry,
not new template files. Creates: `Namespace`, `ServiceAccount`, `ConfigMap`, `Secret`,
`Deployment`/`Service`/`HorizontalPodAutoscaler`/`PodDisruptionBudget` per service, one
shared `Ingress`, and a same-namespace-only `NetworkPolicy` per service.

Validated (see `plan.md` Phase 14) with `helm lint`, `helm template` piped through
`kubectl apply --dry-run=client`, and `--dry-run=server` against a real (local)
Kubernetes API server — all 19 rendered resources pass. Not yet applied for real to an
EKS cluster (no AWS account in this environment).

## Deploy

```bash
helm upgrade --install rca-platform infrastructure/helm/rca-platform \
  --namespace rca-platform --create-namespace \
  --set image.registry=<account>.dkr.ecr.<region>.amazonaws.com/rca-platform-dev \
  --set image.tag=<git-sha-or-version> \
  --set postgres.password=<from Secrets Manager, not committed anywhere> \
  --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=<rca_api_iam_role_arn from terraform output> \
  --values infrastructure/helm/rca-platform/values-dev.yaml   # or -uat / -prod
```

## Verify

```bash
kubectl get pods -n rca-platform
kubectl get deployments,svc,hpa,pdb,ingress -n rca-platform
kubectl logs -n rca-platform deploy/rca-api --tail=100
```

## What this chart assumes already exists in the cluster

- An **AWS Load Balancer Controller** (for `ingress.className: alb`) — not provisioned
  by this chart or by the Terraform in this repo; install it separately (it's a
  cluster-wide add-on, typically via its own Helm chart, one per cluster regardless of
  how many apps use it).
- **Postgres+pgvector, Prometheus, Loki, Jaeger** reachable at the hostnames in
  `values.yaml`'s `postgres`/`observability` sections. This POC's `docker-compose`
  stack (`infrastructure/docker`) is local-only; running the observability stack "for
  real" in EKS (managed Prometheus/Grafana, OpenSearch or Loki on EKS, X-Ray or a
  self-hosted Jaeger) is an environment-specific choice intentionally left to whoever
  deploys this, not hardcoded into the chart.
- **Ollama**, if `AI_PROVIDER=OLLAMA`, reachable at `ollama.baseUrl` — for anything
  beyond local dev this almost certainly means switching to `AI_PROVIDER=BEDROCK`
  instead (see `docs/aws-setup.md` — not yet implemented, tracked in `plan.md`).

## Local testing without AWS

```bash
# against Docker Desktop's built-in Kubernetes, or any kind/minikube cluster:
helm lint infrastructure/helm/rca-platform --set postgres.password=x
helm template rca-platform infrastructure/helm/rca-platform --set postgres.password=x \
  | kubectl apply --dry-run=server -f -
```
