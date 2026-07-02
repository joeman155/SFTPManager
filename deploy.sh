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

# ── Step 1: Enable Cloud Build API (first time only) ─────────────
echo "▶ 1/6  Checking Cloud Build API..."
gcloud services enable cloudbuild.googleapis.com --project=${PROJECT_ID} --quiet

# ── Step 2: Build & push via Cloud Build ─────────────────────────
echo "▶ 2/6  Building and pushing image via Cloud Build..."
echo "       (runs on Google's servers — fast network, no local Docker needed)"
gcloud builds submit \
  --tag ${IMAGE_URL}:latest \
  --project ${PROJECT_ID} \
  .
echo "       Image pushed: ${IMAGE_URL}:latest"

# ── Step 3: Connect to GKE cluster ──────────────────────────────
echo "▶ 3/6  Connecting to GKE cluster..."
gcloud container clusters get-credentials ${CLUSTER_NAME} \
  --region ${REGION} \
  --project ${PROJECT_ID}

# ── Step 4: Apply namespace, secrets and config ──────────────────
echo "▶ 4/6  Applying namespace, secrets and config..."
kubectl apply -f k8s/namespace.yaml
echo ""
echo "  ⚠️  Have you filled in k8s/secret.yaml with your base64 values?"
read -p "  Press Enter to continue or Ctrl+C to abort..."
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml

# ── Step 5: Deploy app ───────────────────────────────────────────
echo "▶ 5/6  Deploying application..."
sed "s|REGION-docker.pkg.dev/YOUR_PROJECT_ID/sftp-manager/sftp-manager:latest|${IMAGE_URL}:latest|g" \
  k8s/deployment.yaml | kubectl apply -f -

kubectl apply -f k8s/backend-config.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/managed-cert.yaml
kubectl apply -f k8s/ingress.yaml

echo "       Waiting for rollout..."
kubectl rollout status deployment/sftp-manager -n sftp-manager --timeout=180s

# ── Step 6: Show status ──────────────────────────────────────────
echo "▶ 6/6  Status..."
echo ""
echo "  Pods:"
kubectl get pods -n sftp-manager
echo ""
echo "  Ingress:"
kubectl get ingress -n sftp-manager
echo ""
echo "  Certificate:"
kubectl get managedcertificate -n sftp-manager

echo ""
echo "═══════════════════════════════════════════"
echo "  ✅  Deploy complete!"
echo ""
echo "  https://${DOMAIN}"
echo "  https://${DOMAIN}/portal"
echo "═══════════════════════════════════════════"
echo ""
