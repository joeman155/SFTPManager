# 1. Login and set your project
gcloud auth login
gcloud config set project sftpmanager

# 2. Enable required APIs
gcloud services enable \
  container.googleapis.com \
  artifactregistry.googleapis.com \
  sqladmin.googleapis.com

# 3. Create Autopilot cluster
# australia-southeast1 = Sydney (closest to Perth/Leederville)
gcloud container clusters create-auto sftp-manager-cluster \
  --region australia-southeast1 \
  --project sftpmanager

# 4. Get credentials so kubectl works
gcloud container clusters get-credentials sftp-manager-cluster \
  --region australia-southeast1 \
  --project sftpmanager

# 5. Create Artifact Registry repository
gcloud artifacts repositories create sftp-manager \
  --repository-format=docker \
  --location=australia-southeast1 \
  --description="SFTP Manager container images" \
  --project sftpmanager

# 6. Configure Docker to push to Artifact Registry
gcloud auth configure-docker australia-southeast1-docker.pkg.dev