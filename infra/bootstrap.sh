#!/usr/bin/env bash
#
# WMS Terraform Bootstrap Script
#
# Prerequisites:
#   - Azure CLI installed and logged in (az login)
#   - Terraform >= 1.5.0 installed
#   - Three Azure subscriptions created manually:
#       wms-terraform, wms-dev, wms-prd
#
# Usage:
#   ./bootstrap.sh \
#     --terraform-sub <wms-terraform subscription ID> \
#     --dev-sub <wms-dev subscription ID> \
#     --prd-sub <wms-prd subscription ID>
#
set -euo pipefail

# ------------------------------------------------------------------
# Parse arguments
# ------------------------------------------------------------------
TERRAFORM_SUB=""
DEV_SUB=""
PRD_SUB=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --terraform-sub) TERRAFORM_SUB="$2"; shift 2 ;;
    --dev-sub)       DEV_SUB="$2";       shift 2 ;;
    --prd-sub)       PRD_SUB="$2";       shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [[ -z "$TERRAFORM_SUB" || -z "$DEV_SUB" || -z "$PRD_SUB" ]]; then
  echo "ERROR: All subscription IDs are required."
  echo "Usage: $0 --terraform-sub <ID> --dev-sub <ID> --prd-sub <ID>"
  exit 1
fi

# ------------------------------------------------------------------
# 1. Verify Azure CLI login
# ------------------------------------------------------------------
echo "=== Step 1: Verifying Azure CLI login ==="
if ! az account show > /dev/null 2>&1; then
  echo "ERROR: Not logged in to Azure CLI. Run 'az login' first."
  exit 1
fi
echo "OK: Azure CLI is authenticated."

# ------------------------------------------------------------------
# 2. Create Service Principal for GitHub Actions
# ------------------------------------------------------------------
echo ""
echo "=== Step 2: Creating Service Principal for GitHub Actions ==="

TENANT_ID=$(az account show --query tenantId -o tsv)

# Create SP with Contributor role on all three subscriptions
SP_OUTPUT=$(az ad sp create-for-rbac \
  --name "wms-github-actions" \
  --role "Contributor" \
  --scopes \
    "/subscriptions/${TERRAFORM_SUB}" \
    "/subscriptions/${DEV_SUB}" \
    "/subscriptions/${PRD_SUB}" \
  --sdk-auth 2>/dev/null || true)

if [[ -z "$SP_OUTPUT" ]]; then
  echo "WARNING: Service principal 'wms-github-actions' may already exist."
  echo "If so, retrieve the existing credentials or reset them with:"
  echo "  az ad sp credential reset --name wms-github-actions --sdk-auth"
  echo ""
  SP_OUTPUT=$(az ad sp credential reset --name "wms-github-actions" --sdk-auth 2>/dev/null || echo "{}")
fi

CLIENT_ID=$(echo "$SP_OUTPUT" | jq -r '.clientId // empty')
CLIENT_SECRET=$(echo "$SP_OUTPUT" | jq -r '.clientSecret // empty')

echo "OK: Service Principal created/updated."

# ------------------------------------------------------------------
# 3. Provision Terraform state storage
# ------------------------------------------------------------------
echo ""
echo "=== Step 3: Provisioning Terraform state storage ==="

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}/terraform-state"

export TF_VAR_terraform_subscription_id="$TERRAFORM_SUB"

terraform init
terraform apply -auto-approve

echo "OK: Terraform state storage provisioned."

# ------------------------------------------------------------------
# 4. Print GitHub Secrets configuration guide
# ------------------------------------------------------------------
echo ""
echo "============================================================"
echo "  Bootstrap Complete!"
echo "============================================================"
echo ""
echo "Next steps: Configure the following GitHub Actions Secrets"
echo "in your repository (Settings > Secrets and variables > Actions):"
echo ""
echo "  ARM_CLIENT_ID       = ${CLIENT_ID}"
echo "  ARM_CLIENT_SECRET   = (saved to ~/.azure/wms-sp-credentials.json)"
echo "  ARM_TENANT_ID       = ${TENANT_ID}"
echo "  ARM_SUBSCRIPTION_ID = ${TERRAFORM_SUB}"
echo ""
echo "SP credentials saved to ~/.azure/wms-sp-credentials.json"
echo "Retrieve ARM_CLIENT_SECRET from that file, then delete it."

# Save credentials to file instead of printing to stdout
mkdir -p ~/.azure
echo "$SP_OUTPUT" > ~/.azure/wms-sp-credentials.json
chmod 600 ~/.azure/wms-sp-credentials.json
echo ""
echo "For Terraform backend configuration, update the subscription_id"
echo "in each environment's backend.tf:"
echo ""
echo "  infra/environments/dev/backend.tf  → subscription_id = \"${TERRAFORM_SUB}\""
echo "  infra/environments/prd/backend.tf  → subscription_id = \"${TERRAFORM_SUB}\""
echo ""
echo "Subscription IDs:"
echo "  wms-terraform = ${TERRAFORM_SUB}"
echo "  wms-dev       = ${DEV_SUB}"
echo "  wms-prd       = ${PRD_SUB}"
echo ""
echo "Also set these secrets for sensitive Terraform variables:"
echo "  TF_VAR_db_admin_password  = <your DB admin password>"
echo "  TF_VAR_jwt_secret         = <your JWT signing key (>= 32 chars)>"
echo "============================================================"
