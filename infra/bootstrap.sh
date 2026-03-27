#!/usr/bin/env bash
#
# WMS Terraform Bootstrap Script
#
# Prerequisites:
#   - Azure CLI installed and logged in (az login)
#   - Terraform >= 1.5.0 installed
#   - Two Azure subscriptions:
#       main (terraform state + prd), wms-dev
#
# Usage:
#   ./bootstrap.sh \
#     --main-sub <main subscription ID> \
#     --dev-sub <wms-dev subscription ID>
#
set -euo pipefail

# ------------------------------------------------------------------
# Parse arguments
# ------------------------------------------------------------------
MAIN_SUB=""
DEV_SUB=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --main-sub) MAIN_SUB="$2"; shift 2 ;;
    --dev-sub)  DEV_SUB="$2";  shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [[ -z "$MAIN_SUB" || -z "$DEV_SUB" ]]; then
  echo "ERROR: Both subscription IDs are required."
  echo "Usage: $0 --main-sub <ID> --dev-sub <ID>"
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

export TF_VAR_terraform_subscription_id="$MAIN_SUB"

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
echo "Subscription layout:"
echo "  main (tfstate + prd) = ${MAIN_SUB}"
echo "  wms-dev              = ${DEV_SUB}"
echo ""
echo "Next steps: Set the following environment variables before running terraform."
echo ""
echo "  # Terraform state backend (same for both environments)"
echo "  export TF_STATE_SUBSCRIPTION_ID=\"${MAIN_SUB}\""
echo ""
echo "  # dev environment"
echo "  export TF_VAR_subscription_id=\"${DEV_SUB}\""
echo "  export TF_VAR_db_admin_password=\"<your DB admin password>\""
echo "  export TF_VAR_jwt_secret=\"<your JWT signing key (>= 32 chars)>\""
echo ""
echo "  # prd environment"
echo "  export TF_VAR_subscription_id=\"${MAIN_SUB}\""
echo "  export TF_VAR_db_admin_password=\"<your DB admin password>\""
echo "  export TF_VAR_jwt_secret=\"<your JWT signing key (>= 32 chars)>\""
echo ""
echo "Then run:"
echo "  cd infra/environments/dev"
echo "  terraform init -backend-config=\"subscription_id=\$TF_STATE_SUBSCRIPTION_ID\""
echo "  terraform plan"
echo "============================================================"
