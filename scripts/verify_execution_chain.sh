#!/usr/bin/env bash
set -euo pipefail

# Verify end-to-end execution trace chain:
# /api/ai/chat-and-execute -> workflow_run/workflow_run_step -> risk/order/signal tracing.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"

API_BASE_URL="${API_BASE_URL:-http://localhost:8000}"
LOGIN_USERNAME="${LOGIN_USERNAME:-admin}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-admin123}"
JWT_TOKEN="${JWT_TOKEN:-}"

AI_PROVIDER="${AI_PROVIDER:-}"
AI_MODEL="${AI_MODEL:-}"
CHAT_MESSAGE="${CHAT_MESSAGE:-请作为交易助手执行一次最小化检查，并在可执行时尝试运行交易命令：买入 000001 100。}"

CONVERSATION_ID="${CONVERSATION_ID:-}"
WORKFLOW_ID="${WORKFLOW_ID:-}"

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-aitradex}"
POSTGRES_USER="${POSTGRES_USER:-aitradex}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-aitradex}"
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-aitradex-postgres}"

STRICT_TRADE="${STRICT_TRADE:-0}"

usage() {
  cat <<'EOF'
Usage:
  scripts/verify_execution_chain.sh

Optional env vars:
  API_BASE_URL            default: http://localhost:8000
  LOGIN_USERNAME          default: admin
  LOGIN_PASSWORD          default: admin123
  JWT_TOKEN               if set, skip /api/auth/login
  AI_PROVIDER             optional, force provider
  AI_MODEL                optional, force model
  CHAT_MESSAGE            default includes a buy command
  CONVERSATION_ID         optional existing conversation_session.id (must exist)
  WORKFLOW_ID             optional existing workflow_definition.id (must exist)
  POSTGRES_HOST           default: localhost
  POSTGRES_PORT           default: 5432
  POSTGRES_DB             default: aitradex
  POSTGRES_USER           default: aitradex
  POSTGRES_PASSWORD       default: aitradex
  POSTGRES_CONTAINER_NAME default: aitradex-postgres
  STRICT_TRADE            1 => require risk/signal/order rows for this run_id
  ENV_FILE                default: ./.env
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] missing command: $1"
    exit 1
  fi
}

require_cmd curl
require_cmd jq

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  set -a && source "${ENV_FILE}" && set +a
fi

# Re-apply defaults after sourcing .env
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-aitradex}"
POSTGRES_USER="${POSTGRES_USER:-aitradex}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-aitradex}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

post_json() {
  local url="$1"
  local body_file="$2"
  local out_file="$3"
  local auth_header="${4:-}"
  if [[ -n "${auth_header}" ]]; then
    curl -sS -o "${out_file}" -w "%{http_code}" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${auth_header}" \
      -X POST "${url}" \
      --data "@${body_file}"
  else
    curl -sS -o "${out_file}" -w "%{http_code}" \
      -H "Content-Type: application/json" \
      -X POST "${url}" \
      --data "@${body_file}"
  fi
}

sql_escape_literal() {
  local value="$1"
  printf "%s" "${value//\'/\'\'}"
}

run_query_local_psql() {
  local sql="$1"
  PGPASSWORD="${POSTGRES_PASSWORD}" psql \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -At -F $'\t' \
    -c "${sql}"
}

run_query_docker_psql() {
  local sql="$1"
  docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" "${POSTGRES_CONTAINER_NAME}" \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At -F $'\t' -c "${sql}"
}

run_query() {
  local sql="$1"
  if command -v psql >/dev/null 2>&1; then
    if run_query_local_psql "SELECT 1;" >/dev/null 2>&1; then
      run_query_local_psql "${sql}"
      return 0
    fi
  fi
  if command -v docker >/dev/null 2>&1; then
    if docker ps --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER_NAME}"; then
      run_query_docker_psql "${sql}"
      return 0
    fi
  fi
  echo "[ERROR] cannot connect to PostgreSQL via local psql or docker container ${POSTGRES_CONTAINER_NAME}" >&2
  return 1
}

echo "[INFO] Step 1/4 login and token"
if [[ -z "${JWT_TOKEN}" ]]; then
  login_payload="${tmp_dir}/login.json"
  cat >"${login_payload}" <<EOF
{"username":"${LOGIN_USERNAME}","password":"${LOGIN_PASSWORD}"}
EOF
  login_resp="${tmp_dir}/login_response.json"
  login_code="$(post_json "${API_BASE_URL}/api/auth/login" "${login_payload}" "${login_resp}")"
  if [[ "${login_code}" != "200" ]]; then
    echo "[ERROR] login http status: ${login_code}"
    cat "${login_resp}"
    exit 1
  fi
  JWT_TOKEN="$(jq -r '.data.access_token // empty' "${login_resp}")"
  if [[ -z "${JWT_TOKEN}" ]]; then
    echo "[ERROR] login failed to return access_token"
    cat "${login_resp}"
    exit 1
  fi
fi

echo "[INFO] Step 2/4 call /api/ai/chat-and-execute"
conv_json="null"
wf_json="null"
if [[ -n "${CONVERSATION_ID}" ]]; then
  conv_json="${CONVERSATION_ID}"
fi
if [[ -n "${WORKFLOW_ID}" ]]; then
  wf_json="${WORKFLOW_ID}"
fi

chat_payload="${tmp_dir}/chat_payload.json"
jq -n \
  --arg message "${CHAT_MESSAGE}" \
  --arg provider "${AI_PROVIDER}" \
  --arg model "${AI_MODEL}" \
  --argjson conversation_id "${conv_json}" \
  --argjson workflow_id "${wf_json}" \
  '{
    message: $message,
    provider: $provider,
    model: $model,
    conversation_id: $conversation_id,
    workflow_id: $workflow_id
  }' > "${chat_payload}"

chat_resp="${tmp_dir}/chat_response.json"
chat_code="$(post_json "${API_BASE_URL}/api/ai/chat-and-execute" "${chat_payload}" "${chat_resp}" "${JWT_TOKEN}")"
if [[ "${chat_code}" != "200" ]]; then
  echo "[ERROR] /api/ai/chat-and-execute http status: ${chat_code}"
  cat "${chat_resp}"
  exit 1
fi

run_id="$(jq -r '.data.run_id // empty' "${chat_resp}")"
workflow_run_id_from_api="$(jq -r '.data.workflow_run_id // empty' "${chat_resp}")"
signal_id="$(jq -r '.data.data.signalId // .data.data.signal_id // empty' "${chat_resp}")"
order_id="$(jq -r '.data.data.orderId // .data.data.order_id // empty' "${chat_resp}")"
agent_success="$(jq -r '.data.success // false' "${chat_resp}")"
agent_message="$(jq -r '.data.message // ""' "${chat_resp}")"

echo "[INFO] API result: success=${agent_success}, run_id=${run_id:-<empty>}, workflow_run_id=${workflow_run_id_from_api:-<empty>}, signal_id=${signal_id:-<empty>}, order_id=${order_id:-<empty>}"
if [[ -n "${agent_message}" ]]; then
  echo "[INFO] API message: ${agent_message}"
fi

if [[ -z "${run_id}" ]]; then
  echo "[ERROR] run_id missing in API response; cannot continue trace verification"
  cat "${chat_resp}"
  exit 1
fi

echo "[INFO] Step 3/4 query workflow_run + workflow_run_step"
run_id_escaped="$(sql_escape_literal "${run_id}")"
run_row="$(run_query "SELECT id, run_id, status, COALESCE(workflow_id::text,''), COALESCE(conversation_id::text,''), started_at, COALESCE(finished_at::text,'') FROM workflow_run WHERE run_id='${run_id_escaped}' ORDER BY id DESC LIMIT 1;")"

if [[ -z "${run_row}" ]]; then
  echo "[ERROR] no workflow_run row found for run_id=${run_id}"
  exit 1
fi

IFS=$'\t' read -r workflow_run_id_db workflow_run_id_value workflow_run_status workflow_id_db conversation_id_db started_at finished_at <<< "${run_row}"
step_count="$(run_query "SELECT COUNT(*) FROM workflow_run_step WHERE workflow_run_id=${workflow_run_id_db};")"
step_tail="$(run_query "SELECT step_order, node_type, node_name, status, LEFT(error_message, 80) FROM workflow_run_step WHERE workflow_run_id=${workflow_run_id_db} ORDER BY step_order DESC LIMIT 8;")"

echo "[OK] workflow_run found: id=${workflow_run_id_db}, status=${workflow_run_status}, workflow_id=${workflow_id_db:-null}, conversation_id=${conversation_id_db:-null}"
echo "[OK] workflow_run_step count: ${step_count}"
echo "[INFO] recent steps:"
if [[ -n "${step_tail}" ]]; then
  while IFS=$'\t' read -r s_order s_type s_name s_status s_err; do
    echo "  - step=${s_order} type=${s_type} name=${s_name} status=${s_status} err=${s_err}"
  done <<< "${step_tail}"
else
  echo "  - <no steps>"
fi

echo "[INFO] Step 4/4 query risk/signal/order rows by run_id"
signal_count="$(run_query "SELECT COUNT(*) FROM strategy_signal WHERE run_id='${run_id_escaped}';")"
order_count="$(run_query "SELECT COUNT(*) FROM trade_order WHERE run_id='${run_id_escaped}';")"
risk_count="$(run_query "SELECT COUNT(*) FROM risk_check_log WHERE run_id='${run_id_escaped}';")"

echo "[OK] trace counts: strategy_signal=${signal_count}, trade_order=${order_count}, risk_check_log=${risk_count}"

signal_tail="$(run_query "SELECT id, symbol, side, created_at FROM strategy_signal WHERE run_id='${run_id_escaped}' ORDER BY id DESC LIMIT 3;")"
order_tail="$(run_query "SELECT id, symbol, side, status, created_at FROM trade_order WHERE run_id='${run_id_escaped}' ORDER BY id DESC LIMIT 3;")"
risk_tail="$(run_query "SELECT id, check_name, passed, LEFT(reason, 60), created_at FROM risk_check_log WHERE run_id='${run_id_escaped}' ORDER BY id DESC LIMIT 3;")"

if [[ -n "${signal_tail}" ]]; then
  echo "[INFO] latest strategy_signal rows:"
  while IFS=$'\t' read -r sid sym side created; do
    echo "  - id=${sid} symbol=${sym} side=${side} created_at=${created}"
  done <<< "${signal_tail}"
fi
if [[ -n "${order_tail}" ]]; then
  echo "[INFO] latest trade_order rows:"
  while IFS=$'\t' read -r oid sym side st created; do
    echo "  - id=${oid} symbol=${sym} side=${side} status=${st} created_at=${created}"
  done <<< "${order_tail}"
fi
if [[ -n "${risk_tail}" ]]; then
  echo "[INFO] latest risk_check_log rows:"
  while IFS=$'\t' read -r rid check_name passed reason created; do
    echo "  - id=${rid} check=${check_name} passed=${passed} reason=${reason} created_at=${created}"
  done <<< "${risk_tail}"
fi

if [[ "${STRICT_TRADE}" == "1" ]]; then
  if [[ "${signal_count}" -eq 0 || "${order_count}" -eq 0 || "${risk_count}" -eq 0 ]]; then
    echo "[ERROR] STRICT_TRADE=1 but trace rows are incomplete"
    exit 1
  fi
fi

if [[ "${step_count}" -eq 0 ]]; then
  echo "[ERROR] workflow_run exists but no workflow_run_step rows"
  exit 1
fi

echo "[SUCCESS] execution chain verification completed"
