#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# Deployment Smoke Test — Standalone Calculation Backend
# =============================================================================
# Validates that the calculation backend deploys as a distinct service,
# routes correctly through nginx, and is independent of the payments backend.
#
# Usage:
#   cd docker && bash smoke-test-deploy.sh [standalone|behind-proxy]
#
# Prerequisites:
#   - JARs built (make package or sbt assembly)
#   - .env file present with FIRECALC_ENV set
#   - For behind-proxy: external 'proxy' network must exist
# =============================================================================

set -euo pipefail

MODE="${1:-standalone}"
COMPOSE_FILE="docker-compose.${MODE}.yml"

PASS=0
FAIL=0
TOTAL=0

pass() {
  PASS=$((PASS + 1))
  TOTAL=$((TOTAL + 1))
  echo "  ✓ $1"
}

fail() {
  FAIL=$((FAIL + 1))
  TOTAL=$((TOTAL + 1))
  echo "  ✗ $1"
}

info() {
  echo "  → $1"
}

cleanup() {
  info "Tearing down ${MODE} stack..."
  docker compose -f "$COMPOSE_FILE" down --remove-orphases 2>/dev/null || true
}

trap cleanup EXIT

echo "============================================"
echo "Smoke Test: ${MODE} deployment"
echo "============================================"
echo ""

# -----------------------------------------------------------------------------
# Phase 1: Start services
# -----------------------------------------------------------------------------
info "Starting ${MODE} stack..."
docker compose -f "$COMPOSE_FILE" up -d --build 2>&1 | tail -5
info "Waiting for services to become healthy..."

# Wait for health with timeout
TIMEOUT=300
ELAPSED=0
while [ $ELAPSED -lt $TIMEOUT ]; do
  BACKEND_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "firecalc-${FIRECALC_ENV:-test}-payments-backend" 2>/dev/null || echo "not_found")
  CALC_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "firecalc-${FIRECALC_ENV:-test}-calc-backend" 2>/dev/null || echo "not_found")

  if [ "$BACKEND_HEALTH" = "healthy" ] && [ "$CALC_HEALTH" = "healthy" ]; then
    break
  fi

  sleep 5
  ELAPSED=$((ELAPSED + 5))
  info "Waiting... backend=${BACKEND_HEALTH}, calc=${CALC_HEALTH} (${ELAPSED}s)"
done

if [ "$BACKEND_HEALTH" = "healthy" ]; then
  pass "Payments backend is healthy"
else
  fail "Payments backend not healthy after ${TIMEOUT}s (status: ${BACKEND_HEALTH})"
fi

if [ "$CALC_HEALTH" = "healthy" ]; then
  pass "Calculation backend is healthy"
else
  fail "Calculation backend not healthy after ${TIMEOUT}s (status: ${CALC_HEALTH})"
fi

# -----------------------------------------------------------------------------
# Phase 2: Verify service independence
# -----------------------------------------------------------------------------
echo ""
info "Checking service independence..."

# Check calc container has no database volumes
CALC_MOUNTS=$(docker inspect --format='{{range .Mounts}}{{.Source}}->{{.Destination}} {{end}}' "firecalc-${FIRECALC_ENV:-test}-calc-backend" 2>/dev/null || echo "")
if echo "$CALC_MOUNTS" | grep -q "databases"; then
  fail "Calculation backend has database mount (should not)"
else
  pass "Calculation backend has no database mounts"
fi

# Check payments container has database volumes
PAYMENT_MOUNTS=$(docker inspect --format='{{range .Mounts}}{{.Source}}->{{.Destination}} {{end}}' "firecalc-${FIRECALC_ENV:-test}-payments-backend" 2>/dev/null || echo "")
if echo "$PAYMENT_MOUNTS" | grep -q "databases"; then
  pass "Payments backend has database mount (expected)"
else
  fail "Payments backend missing database mount"
fi

# -----------------------------------------------------------------------------
# Phase 3: Test nginx routing
# -----------------------------------------------------------------------------
echo ""
info "Testing nginx routing..."

NGINX_CONTAINER="firecalc-${FIRECALC_ENV:-test}-nginx"

# Test /v1/calculate routes to calc backend
# We can verify by checking the upstream header or by stopping calc and seeing 502
# For a non-destructive test, hit the endpoint and check it responds
CALC_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost/healthcheck" 2>/dev/null || echo "000")
# Note: In behind-proxy mode, nginx is not exposed to host. Use docker exec.
if [ "$MODE" = "standalone" ]; then
  # Standalone: nginx is on host ports
  CALC_KINDS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "https://localhost/v1/kinds" -k 2>/dev/null || echo "000")
  CALC_CALC=$(curl -s -o /dev/null -w "%{http_code}" -X POST "https://localhost/v1/calculate" \
    -H "Content-Type: application/json" \
    -d '{"kind":"test","inputs":{}}' -k 2>/dev/null || echo "000")

  if [ "$CALC_KINDS" != "000" ]; then
    pass "GET /v1/kinds reachable via nginx (HTTP ${CALC_KINDS})"
  else
    fail "GET /v1/kinds not reachable via nginx"
  fi

  if [ "$CALC_CALC" != "000" ]; then
    pass "POST /v1/calculate reachable via nginx (HTTP ${CALC_CALC})"
  else
    fail "POST /v1/calculate not reachable via nginx"
  fi
else
  # Behind-proxy: use docker exec to hit nginx internally
  CALC_KINDS=$(docker exec "$NGINX_CONTAINER" curl -s -o /dev/null -w "%{http_code}" "http://localhost/v1/kinds" 2>/dev/null || echo "000")
  CALC_CALC=$(docker exec "$NGINX_CONTAINER" curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost/v1/calculate" \
    -H "Content-Type: application/json" \
    -d '{"kind":"test","inputs":{}}' 2>/dev/null || echo "000")

  if [ "$CALC_KINDS" != "000" ]; then
    pass "GET /v1/kinds reachable via nginx (HTTP ${CALC_KINDS})"
  else
    fail "GET /v1/kinds not reachable via nginx"
  fi

  if [ "$CALC_CALC" != "000" ]; then
    pass "POST /v1/calculate reachable via nginx (HTTP ${CALC_CALC})"
  else
    fail "POST /v1/calculate not reachable via nginx"
  fi
fi

# -----------------------------------------------------------------------------
# Phase 4: Test independent stop/start
# -----------------------------------------------------------------------------
echo ""
info "Testing independent stop/start..."

# Stop calc backend, verify payments still works
info "Stopping calculation backend..."
docker stop "firecalc-${FIRECALC_ENV:-test}-calc-backend" >/dev/null 2>&1 || true

sleep 2

if [ "$MODE" = "standalone" ]; then
  PAYMENT_CHECK=$(curl -s -o /dev/null -w "%{http_code}" "https://localhost/v1/healthcheck" -k 2>/dev/null || echo "000")
else
  PAYMENT_CHECK=$(docker exec "$NGINX_CONTAINER" curl -s -o /dev/null -w "%{http_code}" "http://localhost/healthcheck" 2>/dev/null || echo "000")
fi

if [ "$PAYMENT_CHECK" = "200" ]; then
  pass "Payments backend still accessible after calc backend stop"
else
  fail "Payments backend not accessible after calc backend stop (HTTP ${PAYMENT_CHECK})"
fi

# Verify calc endpoints are unavailable when calc backend is down
if [ "$MODE" = "standalone" ]; then
  CALC_DOWN=$(curl -s -o /dev/null -w "%{http_code}" -X GET "https://localhost/v1/kinds" -k 2>/dev/null || echo "000")
else
  CALC_DOWN=$(docker exec "$NGINX_CONTAINER" curl -s -o /dev/null -w "%{http_code}" "http://localhost/v1/kinds" 2>/dev/null || echo "000")
fi

if [ "$CALC_DOWN" = "502" ] || [ "$CALC_DOWN" = "503" ]; then
  pass "Calc endpoints return 502/503 when calc backend is down (HTTP ${CALC_DOWN})"
else
  fail "Calc endpoints should return 502/503 when calc backend is down (got HTTP ${CALC_DOWN})"
fi

# Restart calc backend
info "Restarting calculation backend..."
docker start "firecalc-${FIRECALC_ENV:-test}-calc-backend" >/dev/null 2>&1 || true
sleep 5

# Wait for calc to become healthy again
CALC_RECOVERED="false"
for i in $(seq 1 10); do
  HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "firecalc-${FIRECALC_ENV:-test}-calc-backend" 2>/dev/null || echo "unknown")
  if [ "$HEALTH" = "healthy" ]; then
    CALC_RECOVERED="true"
    break
  fi
  sleep 3
done

if [ "$CALC_RECOVERED" = "true" ]; then
  pass "Calculation backend recovered after restart"
else
  fail "Calculation backend did not recover after restart"
fi

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
echo ""
echo "============================================"
echo "Results: ${PASS} passed, ${FAIL} failed, ${TOTAL} total"
echo "============================================"

if [ $FAIL -gt 0 ]; then
  exit 1
fi
