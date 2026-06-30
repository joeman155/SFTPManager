#!/bin/bash
# ================================================================
# SFTP Manager — GKE Deploy Script
# Run this from the project root: ./deploy.sh
# ================================================================
set -e

# ── CONFIGURE THESE ──────────────────────────────────────────────
PROJECT_ID="sftpmanager"
REGION="australia-southeast1"
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
echo "▶ 1/7  Authenticating with Google Cloud..."
gcloud auth configure-docker ${REGION}-docker.pkg.dev --quiet

# ── Step 2: Build Docker image ───────────────────────────────────
echo "▶ 2/7  Building Docker image..."
docker build -t ${IMAGE_URL}:latest .
echo "       Built: ${IMAGE_URL}:latest"

# ── Step 3: Push to Artifact Registry ───────────────────────────
echo "▶ 3/7  Pushing image to Artifact Registry..."
docker push ${IMAGE_URL}:latest
echo "       Pushed: ${IMAGE_URL}:latest"

# ── Step 4: Connect to GKE cluster ──────────────────────────────
echo "▶ 4/7  Connecting to GKE cluster..."
gcloud container clusters get-credentials ${CLUSTER_NAME} \
  --region ${REGION} \
  --project ${PROJECT_ID}

# ── Step 5: Apply namespace, secrets and config ──────────────────
echo "▶ 5/7  Applying namespace, secrets and config..."
kubectl apply -f k8s/namespace.yaml
echo ""
echo "  ⚠️  Have you filled in k8s/secret.yaml with your base64 values?"
read -p "  Press Enter to continue or Ctrl+C to abort..."
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml

# ── Step 6: Deploy app ───────────────────────────────────────────
echo "▶ 6/7  Deploying application..."
sed "s|REGION-docker.pkg.dev/YOUR_PROJECT_ID/sftp-manager/sftp-manager:latest|${IMAGE_URL}:latest|g" \
  k8s/deployment.yaml | kubectl apply -f -

kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/managed-cert.yaml
kubectl apply -f k8s/ingress.yaml

kubectl apply -f k8s/iap-backend-config.yaml

echo "       Waiting for rollout..."
kubectl rollout status deployment/sftp-manager -n sftp-manager --timeout=120s

# ── Step 7: Show status ──────────────────────────────────────────
echo "▶ 7/7  Checking status..."
echo ""
echo "  Ingress (may take 2-5 mins to get external IP):"
kubectl get ingress -n sftp-manager
echo ""
echo "  Certificate status:"
kubectl get managedcertificate -n sftp-manager

echo ""
echo "═══════════════════════════════════════════"
echo "  ✅  Deploy complete!"
echo ""
echo "  DNS A record:"
echo "    ${DOMAIN}  →  <static IP from k8s/static-ip.sh>"
echo ""
echo "  SSL cert provisions automatically after DNS propagates."
echo "  Check cert status anytime:"
echo "    kubectl get managedcertificate -n sftp-manager"
echo ""
echo "  Once cert shows ACTIVE visit:"
echo "    https://${DOMAIN}"
echo ""
echo "  Don't forget to add to Google OAuth redirect URIs:"
echo "    https://${DOMAIN}/login/oauth2/code/google"
echo "═══════════════════════════════════════════"
echo ""

