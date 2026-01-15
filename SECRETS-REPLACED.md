# Secrets Replacement Summary

This document lists all secrets/tokens that were replaced with placeholders for security.

## Placeholders Used

### Database Credentials
- `[MONGODB_PASSWORD]` - MongoDB root password
- `[MONGODB_HOST]` - MongoDB host address (EC2 instance)
- Database name `2tbot-be` kept as-is (not sensitive)

### API Credentials
- `[API_USER]` - API username
- `[API_PASSWORD]` - API password

### Encryption Keys
- `[ENCRYPT_SECRET_KEY]` - AES encryption secret key
- `[RSA_PUBLIC_KEY]` - RSA public key for encryption

### Telegram
- `[TELEGRAM_CHANNEL_ID]` - Default Telegram channel ID

### AWS/Cloud Infrastructure
- `[AWS_ACCOUNT_ID]` - AWS account ID (12 digits)
- `[EKS_CLUSTER_NAME]` - EKS cluster name
- `[ECR_REPO_NAME]` - ECR repository name prefix

### Docker Hub
- `[DOCKER_HUB_USERNAME]` - Docker Hub username/organization

### Other
- `[PROJECT_PATH]` - Local project path (in commented scripts)

## Files Modified

1. **order-placer/src/main/resources/application.properties**
   - MongoDB URI password replaced

2. **publisher/src/main/resources/application.properties**
   - MongoDB URI password and host replaced
   - API user/password replaced

3. **order-placer/src/main/java/com/tbot/cyclop/orderplacer/util/GenericHttpUtil.java**
   - Encryption secret key replaced
   - RSA public key replaced

4. **order-placer/src/main/java/com/tbot/cyclop/orderplacer/service/NotificationService.java**
   - Telegram channel ID replaced

5. **push-image.sh**
   - AWS ECR account ID replaced

6. **redeploy.sh**
   - AWS EKS cluster ARN replaced

7. **cicd.sh**
   - ECR repository names replaced

8. **cicd-docker.sh**
   - Docker Hub username replaced
   - ECR repository names replaced (in comments)

9. **docker-compose.yml**
   - Docker Hub image names replaced

10. **dev-ops/k8s/apps/order-placer-deployment.yaml**
    - AWS ECR image path replaced

11. **dev-ops/k8s/apps/publisher-deployment.yaml**
    - AWS ECR image path replaced

12. **.gitignore**
    - Added `dev-ops/auth.json` and other secret files to ignore list

## Security Notes

- All actual secrets are stored in `dev-ops/auth.json` (gitignored)
- Never commit `auth.json` or any `.env` files
- Replace placeholders with actual values when deploying
- Consider using environment variables or secret management systems (AWS Secrets Manager, HashiCorp Vault, etc.) for production
