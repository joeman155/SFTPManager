# SFTP Manager

A Spring Boot web application for managing SFTP sites, users, and service accounts.

## Stack
- **Backend:** Spring Boot 3.2 (REST API)
- **Frontend:** React 18 + Bootstrap 5 (served as static files)
- **Database:** PostgreSQL
- **Build:** Maven
- **Deploy:** Tomcat WAR or Kubernetes

## Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL 14+

## Setup

### 1. Create the database
```sql
CREATE DATABASE sftpmanager;
CREATE USER sftpmanager WITH PASSWORD 'changeme';
GRANT ALL PRIVILEGES ON DATABASE sftpmanager TO sftpmanager;
```

### 2. Configure connection
Edit `src/main/resources/application.properties` or set environment variables:
```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=sftpmanager
DB_USER=sftpmanager
DB_PASSWORD=changeme
```

### 3. Build
```bash
mvn clean package
```

### 4. Run locally
```bash
java -jar target/sftp-manager-1.0.0.war
```
Then open: http://localhost:8080

## Deploying to Tomcat
Copy `target/sftp-manager-1.0.0.war` to your Tomcat `webapps/` directory.

## Deploying to Kubernetes
```bash
# Build Docker image
docker build -t sftp-manager:1.0.0 .

# Update secrets in k8s/deployment.yaml, then:
kubectl apply -f k8s/deployment.yaml
```

## API Endpoints

| Entity              | Base URL                       |
|---------------------|-------------------------------|
| Users               | `/api/users`                  |
| SFTP Services       | `/api/sftp-servicess`         |
| Service Accounts    | `/api/sftp-service-accounts`  |
| IP Whitelist        | `/api/sftp-service-ip-whitelists` |
| Account Controls    | `/api/account-controlss`      |
| Runtime Settings    | `/api/runtime-settingss`      |

All endpoints support: `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `DELETE /{id}`

## Opening in NetBeans
File → Open Project → select this folder. NetBeans will detect it as a Maven project automatically.
