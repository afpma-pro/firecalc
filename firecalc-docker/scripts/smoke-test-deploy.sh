#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Smoke Test Script
# =============================================================================
# Verifies that deployed services are healthy by checking Docker health status
# and HTTP health endpoints.
#
# Health endpoints:
#   Backend     — http://localhost:8181/v1/healthcheck (Docker network only)
#   Calc-server — http://localhost:9000/health (Docker network only, verified via nginx proxy)
#   UI          — http://localhost:80/
#   Nginx       — http://localhost/healthcheck
#                — http://localhost/calc-healthcheck (with-calc modes only)
#
# Usage:
#   bash scripts/smoke-test-deploy.sh                     # Check all services
#   bash scripts/smoke-test-deploy.sh --service backend   # Check specific service
#   bash scripts/smoke-test-deploy.sh --timeout 120       # Custom wait timeout
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

TIMEOUT=90
SINGLE_SERVICE=""
CALC_SERVER="${CALC_SERVER:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --service)
            SINGLE_SERVICE="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [--service <name>] [--timeout <seconds>]"
            echo ""
            echo "  --service <name>   Check a specific service (backend, calc-server, ui-server, nginx)"
            echo "  --timeout <secs>   Max wait time for services to become healthy (default: 90)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 1
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

PASS=0
FAIL=0
SKIP=0

pass() { PASS=$((PASS + 1)); echo "  ✓ $*"; }
fail() { FAIL=$((FAIL + 1)); echo "  ✗ $*" >&2; }
skip() { SKIP=$((SKIP + 1)); echo "  - $* (skipped)"; }
resolve_container_name() {
    local service="$1"
    local name
    name="$(docker compose ps --format '{{.Name}}' "$service" 2>/dev/null | head -1)" || true
    if [[ -n "$name" ]]; then
        echo "$name"
    else
        echo "firecalc-${FIRECALC_ENV:-}-${service}"
    fi
}

# ---------------------------------------------------------------------------
# Docker health check
# ---------------------------------------------------------------------------

check_docker_health() {
    local service="$1"
    if [[ "$SINGLE_SERVICE" != "" && "$SINGLE_SERVICE" != "$service" ]]; then
        skip "Docker health for $service"
        return 0
    fi

    local status
    status=$(docker inspect --format='{{.State.Health.Status}}' "$(resolve_container_name "$service")" 2>/dev/null) || {
        fail "Docker health for $service: container not found"
        return 1
    }

    if [[ "$status" == "healthy" ]]; then
        pass "Docker health for $service: healthy"
    else
        fail "Docker health for $service: $status"
        return 1
    fi
}

# ---------------------------------------------------------------------------
# HTTP health check
# ---------------------------------------------------------------------------

check_http_health() {
    local service="$1" url="$2" expected_code="${3:-200}" method="${4:-GET}" data="${5:-}"
    if [[ "$SINGLE_SERVICE" != "" && "$SINGLE_SERVICE" != "$service" ]]; then
        skip "HTTP health for $service ($url)"
        return 0
    fi
    command -v curl >/dev/null 2>&1 || {
        skip "HTTP health for $service ($url): curl not available"
        return 0
    }

    local http_code curl_args=(-s -o /dev/null -w '%{http_code}' --max-time 10)
    if [[ "$method" != "GET" ]]; then
        curl_args+=(-X "$method")
    fi
    if [[ -n "$data" ]]; then
        curl_args+=(-H 'Content-Type: application/json' -d "$data")
    fi
    http_code=$(curl "${curl_args[@]}" "$url" 2>/dev/null) || {
        fail "HTTP health for $service ($url): connection failed"
        return 1
    }

    if [[ "$expected_code" == "!"* ]]; then
        local negated_code="${expected_code#!}"
        if [[ "$http_code" != "$negated_code" ]]; then
            pass "HTTP health for $service ($url): $http_code (not $negated_code)"
        else
            fail "HTTP health for $service ($url): should not be $negated_code, got $http_code"
            return 1
        fi
    elif [[ "$http_code" == "$expected_code" ]]; then
        pass "HTTP health for $service ($url): $http_code"
    else
        fail "HTTP health for $service ($url): expected $expected_code, got $http_code"
        return 1
    fi
}

# ---------------------------------------------------------------------------
# Wait for services to become healthy
# ---------------------------------------------------------------------------

wait_for_healthy() {
    local service="$1"
    local elapsed=0

    while [[ $elapsed -lt $TIMEOUT ]]; do
        local status
        status=$(docker inspect --format='{{.State.Health.Status}}' "$(resolve_container_name "$service")" 2>/dev/null) || {
            sleep 2
            elapsed=$((elapsed + 2))
            continue
        }
        if [[ "$status" == "healthy" ]]; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    fail "$service did not become healthy within ${TIMEOUT}s"
    return 1
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

echo "========================================"
echo " FireCalc Docker Smoke Tests"
echo "========================================"
echo ""

# Check compose file
COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
if [[ ! -L "$COMPOSE_FILE" ]]; then
    echo "ERROR: No active compose variant." >&2
    echo "Run 'make deploy' or 'make build' first." >&2
    exit 1
fi

ACTIVE_COMPOSE="$(basename "$(readlink "$COMPOSE_FILE")")"
echo "Active compose : $ACTIVE_COMPOSE"
echo "CALC_SERVER    : ${CALC_SERVER:-<unset>}"
echo "Timeout        : ${TIMEOUT}s"
echo ""

# Load FIRECALC_ENV from .env if available
if [[ -f "$PROJECT_DIR/.env" ]]; then
    FIRECALC_ENV=$(grep '^FIRECALC_ENV=' "$PROJECT_DIR/.env" | tail -1 | cut -d= -f2-)
fi
FIRECALC_ENV="${FIRECALC_ENV:-}"

cd "$PROJECT_DIR"

# ---------------------------------------------------------------------------
# Check container status
# ---------------------------------------------------------------------------

echo "--- Container Status ---"
docker compose ps
echo ""

# ---------------------------------------------------------------------------
# Wait for services to become healthy
# ---------------------------------------------------------------------------

echo "--- Waiting for services ---"

# Backend is always present
wait_for_healthy "backend"

# Calc-server only in with-calc modes
if [[ "${CALC_SERVER}" = "true" ]]; then
    wait_for_healthy "calc-server"
else
    skip "calc-server (not active in no-calc mode)"
fi

wait_for_healthy "ui-server"
wait_for_healthy "nginx"
echo ""

# ---------------------------------------------------------------------------
# Run health checks
# ---------------------------------------------------------------------------

echo "--- Docker Health ---"
check_docker_health "backend" || true
if [[ "${CALC_SERVER}" = "true" ]]; then
    check_docker_health "calc-server" || true
else
    skip "Docker health for calc-server (not active)"
fi
check_docker_health "ui-server" || true
check_docker_health "nginx" || true
echo ""

# Note: HTTP checks against localhost only work when ports are published
# to the host (standalone mode). In behind-proxy mode, services are
# only reachable on the Docker network.
echo "--- HTTP Health (localhost) ---"
echo "Note: These checks require published host ports (standalone mode)."
echo "In behind-proxy mode, use Docker network to reach services."
echo ""

# Check if we're in standalone mode (ports published)
if docker compose ps nginx --format '{{.Ports}}' 2>/dev/null | grep -q "0.0.0.0:80\|0.0.0.0:443\|\[::\]:80\|\[::\]:443"; then
    # Backend health is verified via Docker healthcheck and nginx /healthcheck proxy.
    # Port 8181 is only exposed on the Docker network, not published to the host.
    # Calc-server health is verified via Docker healthcheck and nginx /calc-healthcheck proxy.
    # Port 9000 is only exposed on the Docker network, not published to the host.
    check_http_health "ui-server" "http://localhost:80/" || true
    check_http_health "nginx" "http://localhost/healthcheck" || true
    if [[ "${CALC_SERVER}" = "true" ]]; then
        check_http_health "nginx-calc" "http://localhost/calc-healthcheck" || true
        check_http_health "nginx-calc-calculate" "http://localhost/v1/calculate" "200" "POST" '{"kind":"payments","data":{}}' || true
        check_http_health "nginx-calc-kinds" "http://localhost/v1/kinds" || true
    fi
    if [[ "${CALC_SERVER}" != "true" ]]; then
        check_http_health "nginx-no-calc-calculate" "http://localhost/v1/calculate" "!200" || true
        check_http_health "nginx-no-calc-kinds" "http://localhost/v1/kinds" "!200" || true
    fi
else
    skip "HTTP health checks (ports not published — behind-proxy mode)"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo ""
echo "========================================"
echo " Smoke Test Summary"
echo "========================================"
echo "  Passed : $PASS"
echo "  Failed : $FAIL"
echo "  Skipped: $SKIP"
echo "========================================"

if [[ $FAIL -gt 0 ]]; then
    echo "SMOKE TESTS FAILED" >&2
    exit 1
else
    echo "SMOKE TESTS PASSED"
    exit 0
fi
