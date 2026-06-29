# Deploying to GKE — sftp.leederville.net

## Prerequisites (one-time installs)
```bash
# Install Google Cloud CLI
https://cloud.google.com/sdk/docs/install

# Install kubectl
gcloud components install kubectl

# Install Docker Desktop
https://www.docker.com/products/docker-desktop
```

## Step 1 — Create GKE Cluster (one time)
```bash
# Login
gcloud auth login
gcloud config set project YOUR_PROJECT_ID

# Enable required APIs
gcloud services enable container.googleapis.com artifactregistry.googleapis.com

# Create cluster (australia-southeast1 = Sydney, closest to Perth)
gcloud container clusters create sftp-manager-cluster \
  --region australia-southeast1 \
  --num-nodes 2 \
  --machine-type e2-small \
  --enable-autoscaling \
  --min-nodes 1 \
  --max-nodes 4

# Create Artifact Registry repo for Docker images
gcloud artifacts repositories create sftp-manager \
  --repository-format=docker \
  --location=australia-southeast1
```

## Step 2 — Create PostgreSQL Database
Options:
- **Google Cloud SQL** (recommended) — managed PostgreSQL, automatic backups
- **External** — any PostgreSQL server with a public IP

### Cloud SQL setup:
```bash
gcloud services enable sqladmin.googleapis.com

gcloud sql instances create sftp-manager-db \
  --database-version=POSTGRES_15 \
  --region=australia-southeast1 \
  --tier=db-f1-micro

gcloud sql databases create sftpmanager --instance=sftp-manager-db
gcloud sql users create sftpmanager --instance=sftp-manager-db --password=YOURPASSWORD
```

## Step 3 — Fill in Secrets
Edit `k8s/secret.yaml` with your base64-encoded values:
```bash
# On Mac/Linux:
echo -n "your-db-host" | base64

# On Windows (PowerShell):
[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("your-db-host"))
```

For Cloud SQL, DB_HOST should be `127.0.0.1` (the Cloud SQL proxy runs as a sidecar).
For external DB, use the actual hostname or IP.

## Step 4 — Update deploy.sh
Open `deploy.sh` and set:
```bash
PROJECT_ID="your-gcp-project-id"
REGION="australia-southeast1"
CLUSTER_NAME="sftp-manager-cluster"
```

## Step 5 — Update cert-issuer.yaml
Open `k8s/cert-issuer.yaml` and set your email:
```yaml
email: YOUR_EMAIL@example.com
```

## Step 6 — Run the deploy script
```bash
./deploy.sh
```

## Step 7 — Create DNS record
After deploy, get the external IP:
```bash
kubectl get ingress -n sftp-manager
```
In your DNS provider, create:
```
A record:  sftp.leederville.net  →  <EXTERNAL-IP>
```

## Step 8 — Update Google OAuth redirect URI
Go to Google Cloud Console → APIs & Services → Credentials → your OAuth client.

Add this to Authorised redirect URIs:
```
https://sftp.leederville.net/login/oauth2/code/google
```

## Useful commands
```bash
# Check pod status
kubectl get pods -n sftp-manager

# View logs
kubectl logs -n sftp-manager -l app=sftp-manager --tail=100

# Restart the app
kubectl rollout restart deployment/sftp-manager -n sftp-manager

# Check SSL certificate
kubectl get certificate -n sftp-manager

# Re-deploy after code changes
./deploy.sh
```

## Cost estimate (GKE, Sydney region)
| Resource | Spec | Est. monthly |
|---|---|---|
| GKE cluster | 2x e2-small | ~$25 USD |
| Cloud SQL | db-f1-micro | ~$8 USD |
| Load balancer | 1x | ~$18 USD |
| Artifact Registry | minimal | ~$1 USD |
| **Total** | | **~$52 USD/month** |
