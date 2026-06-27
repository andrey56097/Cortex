# Oracle Cloud Deployment Plan
**Goal:** Deploy Cortex to a free Oracle Cloud ARM instance using k3s, with automatic updates via GitHub Actions CI/CD.

**Stack:** Oracle Cloud Free Tier + Ubuntu + k3s (lightweight Kubernetes) + Traefik Ingress + Let's Encrypt HTTPS

---

## Overview

```
GitHub Push → GitHub Actions → build JAR → build Docker image → push to ghcr.io
                                                                    ↓
                                                            Oracle Cloud VM
                                                            (k3s cluster)
                                                                    ↓
                                                            kubectl apply
                                                                    ↓
                                                            Cortex running
                                                            on k8s + HTTPS
```

---

## Phase 11. Oracle Cloud Account & Instance

### 11.1 Sign up for Oracle Cloud Free Tier
- Go to https://www.oracle.com/cloud/free/
- Register with email, phone, and credit card (for verification, not charged)
- **Important:** Choose "Pay as you go" after the free trial — it stays free within limits but gives access to Always Free resources without time limit

### 11.2 Create an ARM Compute Instance

| Setting | Value |
|---------|-------|
| Image | Ubuntu 22.04 LTS (or 24.04 LTS) |
| Instance type | Virtual Machine — ARM (Ampere A1) |
| OCPU count | 4 (Always Free) |
| Memory | 24 GB (Always Free) |
| Boot volume | 200 GB (Always Free) |
| SSH key | Generate/download for first login |

### 11.3 Open Network Ports (Security List)
Must open **ingress** rules for:

| Port | Purpose | Source |
|------|---------|--------|
| `22` | SSH | `0.0.0.0/0` |
| `80` | HTTP (Traefik) | `0.0.0.0/0` |
| `443` | HTTPS (Traefik) | `0.0.0.0/0` |
| `6443` | k3s API (for kubectl from CI) | `0.0.0.0/0` |

> **This is critical:** Oracle blocks traffic at the cloud network level, not just the OS firewall. Ports won't work until added to the Security List.

### 11.4 SSH into the instance
```bash
ssh -i ~/.ssh/oracle-key ubuntu@<public-ip>
```

---

## Phase 12. Install k3s on the Instance

### 12.1 Install k3s
```bash
curl -sfL https://get.k3s.io | sh -
```

### 12.2 Verify cluster
```bash
sudo k3s kubectl get nodes
```

### 12.3 Copy kubeconfig for local access (optional)
```bash
mkdir -p ~/.kube
sudo cat /etc/rancher/k3s/k3s.yaml > ~/.kube/config
sudo chown $(id -u):$(id -g) ~/.kube/config
sed -i 's/127.0.0.1/<your-instance-public-ip>/g' ~/.kube/config
```

---

## Phase 13. Prepare Kubernetes Manifests for Production

### 13.1 Update `k8s/` manifests for the cloud setup
Changes from Minikube:
- `imagePullPolicy: IfNotPresent` → keeps working (pulls from ghcr.io if not cached)
- Add `Ingress` resource (Traefik comes with k3s by default)
- Add `Certificate` resource for Let's Encrypt

### 13.2 New/updated files

**k8s/ingress.yaml** — expose the app via domain:
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: cortex-ingress
  annotations:
    traefik.ingress.kubernetes.io/router.entrypoints: web,websecure
spec:
  rules:
    - host: cortex.<your-domain>
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: cortex-service
                port:
                  number: 8080
```

**k8s/certificate.yaml** — automatic HTTPS:
```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: <your-email>
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
      - http01:
          ingress:
            class: traefik
```

---

## Phase 14. CI/CD — Deploy to Production

### 14.1 Add GitHub Secrets

| Secret | Value |
|--------|-------|
| `KUBECONFIG` | Contents of `/etc/rancher/k3s/k3s.yaml` (with public IP) |
| `SSH_PRIVATE_KEY` | Your SSH private key (optional, if using SSH approach) |

### 14.2 Update `.github/workflows/ci-cd.yaml` — add deploy-to-oracle job

New job after `build-and-push`:

```yaml
  deploy-to-oracle:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up kubeconfig
        run: |
          mkdir -p ~/.kube
          echo "${{ secrets.KUBECONFIG }}" > ~/.kube/config

      - name: Update image tag in manifest
        run: |
          sed -i "s|image:.*|image: ghcr.io/andrey56097/cortex:${{ github.sha }}|" k8s/app-deployment.yaml

      - name: Deploy to k3s
        run: |
          kubectl apply -f k8s/configmap.yaml
          kubectl apply -f k8s/secret.yaml
          kubectl apply -f k8s/postgres-deployment.yaml
          kubectl apply -f k8s/app-deployment.yaml
          kubectl rollout status deployment/cortex --timeout=120s

      - name: Smoke test
        run: |
          kubectl port-forward svc/cortex-service 8080:8080 &
          sleep 5
          curl -f http://localhost:8080/actuator/health
```

---

## Phase 15. Domain & HTTPS (Optional)

### 15.1 Get a domain
- Free: `nip.io` (magic DNS: `cortex.<public-ip>.nip.io`)
- Cheap: buy a domain ($3-10/year on Namecheap, Porkbun)
- Free subdomain: `eu.org` (free for life, takes a few days to approve)

### 15.2 Install cert-manager
```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml
```

### 15.3 Apply ingress + cert manifests
```bash
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/certificate.yaml
```

---

## Final Result

```
https://cortex.your-domain.com/swagger-ui.html
                    ↕
              [Ingress: Traefik]
                    ↕
              [Service: cortex-service]
                   ╱ ╲
           [Pod: cortex-1] [Pod: cortex-2]
                   ╲ ╱
              [Service: postgres-service]
                    ↕
              [Pod: postgres]
```

**On every `git push` to `main`:**
1. Tests run
2. Docker image builds and pushes to ghcr.io
3. `kubectl apply` updates the k3s cluster
4. App rolls out with zero downtime (2 replicas)

---

## Troubleshooting

| Problem | Likely fix |
|---------|-----------|
| Can't connect via SSH | Check Security List port 22 |
| App not reachable on port 80/443 | Check Security List, then `kubectl get ingress` |
| ImagePullBackOff | `kubectl describe pod` — check if ghcr.io credentials needed |
| cert-manager not issuing cert | `kubectl describe certificate` — DNS must point to your IP |
| k3s service not starting | `sudo journalctl -u k3s -f` for logs |
