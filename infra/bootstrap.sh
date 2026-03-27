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
# 2. Provision Terraform state storage
# ------------------------------------------------------------------
echo ""
echo "=== Step 2: Provisioning Terraform state storage ==="

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}/terraform-state"

export TF_VAR_terraform_subscription_id="$TERRAFORM_SUB"

terraform init
terraform apply -auto-approve

echo "OK: Terraform state storage provisioned."

# ------------------------------------------------------------------
# 3. Print next steps
# ------------------------------------------------------------------
echo ""
echo "============================================================"
echo "  Bootstrap Complete!"
echo "============================================================"
echo ""
echo "Next steps: Update the subscription_id in each environment's backend.tf:"
echo ""
echo "  infra/environments/dev/backend.tf  → subscription_id = \"${TERRAFORM_SUB}\""
echo "  infra/environments/prd/backend.tf  → subscription_id = \"${TERRAFORM_SUB}\""
echo ""
echo "Subscription IDs:"
echo "  wms-terraform = ${TERRAFORM_SUB}"
echo "  wms-dev       = ${DEV_SUB}"
echo "  wms-prd       = ${PRD_SUB}"
echo ""
echo "Before running terraform plan/apply, set these environment variables:"
echo "  export TF_VAR_db_admin_password=\"<your DB admin password>\""
echo "  export TF_VAR_jwt_secret=\"<your JWT signing key (>= 32 chars)>\""
echo "============================================================"
