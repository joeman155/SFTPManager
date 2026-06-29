#!/bin/bash
# ================================================================
# SFTP Manager — GKE Deploy Script
# Run this from the project root: ./deploy.sh
# ================================================================
set -e

# ── CONFIGURE THESE ──────────────────────────────────────────────
PROJECT_ID="your-gcp-project-id"
REGION="australia-southeast1"          # closest GCP region to Perth/Leederville
CLUSTER_NAME="sftp-manager-cluster"
ARTIFACT_REPO="sftp-manager"
IMAGE_NAME="sftp-manager"
DOMAIN="sftp.leederville.net"
# ─────────────────────────────────────────────────────────────────

IMAGE_URL="${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPO}/${IMAGE_NAME}"

echo ""
echo "═══════════════════════════════════════════"
echo "  SFTP Manager — Deploying to GKE"
echo "  Project : $PROJECT_ID"
echo "  Region  : $REGION"
echo "  Domain  : $DOMAIN"
echo "═══════════════════════════════════════════"
echo ""

# ── Step 1: Authenticate ─────────────────────────────────────────
echo "▶ 1/8  Authenticating with Google Cloud..."
gcloud auth configure-docker ${REGION}-docker.pkg.dev --quiet

# ── Step 2: Build Docker image ───────────────────────────────────
echo "▶ 2/8  Building Docker image..."
docker build -t ${IMAGE_URL}:latest .
echo "       Built: ${IMAGE_URL}:latest"

# ── Step 3: Push to Artifact Registry ───────────────────────────
echo "▶ 3/8  Pushing image to Artifact Registry..."
docker push ${IMAGE_URL}:latest
echo "       Pushed: ${IMAGE_URL}:latest"

# ── Step 4: Connect to GKE cluster ──────────────────────────────
echo "▶ 4/8  Connecting to GKE cluster..."
gcloud container clusters get-credentials ${CLUSTER_NAME} \
  --region ${REGION} \
  --project ${PROJECT_ID}

# ── Step 5: Create namespace ─────────────────────────────────────
echo "▶ 5/8  Applying namespace..."
kubectl apply -f k8s/namespace.yaml

# ── Step 6: Apply secrets & config ──────────────────────────────
echo "▶ 6/8  Applying secrets and config..."
echo ""
echo "  ⚠️  Have you filled in k8s/secret.yaml with your base64 values?"
echo "     If not, press Ctrl+C now and run:"
echo "     echo -n 'yourvalue' | base64"
echo ""
read -p "  Press Enter to continue or Ctrl+C to abort..."
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml

# ── Step 7: Install ingress-nginx & cert-manager (first time only) ──
echo "▶ 7/8  Checking ingress-nginx and cert-manager..."

if ! kubectl get ns ingress-nginx &>/dev/null; then
  echo "       Installing ingress-nginx..."
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml
  echo "       Waiting for ingress-nginx to be ready..."
  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=120s
else
  echo "       ingress-nginx already installed ✓"
fi

if ! kubectl get ns cert-manager &>/dev/null; then
  echo "       Installing cert-manager..."
  kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
  echo "       Waiting for cert-manager to be ready..."
  kubectl wait --namespace cert-manager \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/instance=cert-manager \
    --timeout=120s
  echo "       Applying Let's Encrypt issuer..."
  kubectl apply -f k8s/cert-issuer.yaml
else
  echo "       cert-manager already installed ✓"
fi

# ── Step 8: Deploy app ───────────────────────────────────────────
echo "▶ 8/8  Deploying application..."

# Patch image URL into deployment
sed "s|REGION-docker.pkg.dev/YOUR_PROJECT_ID/sftp-manager/sftp-manager:latest|${IMAGE_URL}:latest|g" \
  k8s/deployment.yaml | kubectl apply -f -

kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml

echo ""
echo "       Waiting for rollout..."
kubectl rollout status deployment/sftp-manager -n sftp-manager --timeout=120s

# ── Done ─────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════"
echo "  ✅  Deploy complete!"
echo ""
echo "  Next steps:"
echo "  1. Get the ingress IP:"
echo "     kubectl get ingress -n sftp-manager"
echo ""
echo "  2. Create a DNS A record:"
echo "     ${DOMAIN}  →  <EXTERNAL-IP from above>"
echo ""
echo "  3. Wait ~2 minutes for SSL cert to provision"
echo "     then visit: https://${DOMAIN}"
echo ""
echo "  4. Update Google OAuth redirect URI to:"
echo "     https://${DOMAIN}/login/oauth2/code/google"
echo "═══════════════════════════════════════════"
echo ""
