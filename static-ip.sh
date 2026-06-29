#!/bin/bash
# Run this ONCE before deploying to reserve a static IP
# This gives you a permanent IP for your DNS record

PROJECT_ID="sftpmanager"

echo "Reserving static IP..."
gcloud compute addresses create sftp-manager-ip \
  --global \
  --project ${PROJECT_ID}

echo ""
echo "Your static IP is:"
gcloud compute addresses describe sftp-manager-ip \
  --global \
  --project ${PROJECT_ID} \
  --format="get(address)"

echo ""
echo "Point your DNS A record to this IP:"
echo "  sftp.leederville.net  →  <IP above>"
echo ""
echo "Then deploy the app and Google will auto-provision the SSL cert."
echo "Cert provisioning takes 10-15 minutes after DNS propagates."

