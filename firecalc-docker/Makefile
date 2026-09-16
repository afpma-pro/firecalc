# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

# =============================================================================
# FireCalc Docker — Makefile
# =============================================================================
# Deployment orchestration: clone source repos at pinned refs, build artifacts,
# build Docker images, and manage the container lifecycle.
#
# Environment Variables (set before invoking make, or in your shell profile):
#   CALC_SERVER  — Set to "true" to deploy with calc-server (Mode B).
#                  Unset or empty for without calc-server (Mode A).
#   MODE         — "standalone" (default) or "behind-proxy".
#                  Controls TLS termination strategy.
#
# Usage:
#   make deploy          # Full deployment (clone, build, up)
#   make build           # Clone repos, build artifacts, build images
#   make up              # Start containers (images must already exist)
#   make down            # Stop and remove containers
#   make logs            # Stream logs from all services
#   make smoke           # Run smoke tests against running services
#   make clean           # Remove build artifacts and generated files
# =============================================================================

SHELL := /usr/bin/bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := deploy
.DELETE_ON_ERROR:

# Defaults for compose variant selection (override via environment or make args).
# CALC_SERVER: set to "true" to include calc-server (Mode B).
#              Any other value (false, empty, unset) excludes calc-server (Mode A).
# MODE: "standalone" (default) or "behind-proxy".
CALC_SERVER ?=
export CALC_SERVER
MODE       ?= standalone

# Resolve the active compose file from CALC_SERVER and MODE.
# CALC_SERVER="true" → with calc-server; anything else → no calc-server.
# MODE=standalone (default) or MODE=behind-proxy.
COMPOSE_FILE := $(shell \
  calc_suffix=""; \
  if [ "$(CALC_SERVER)" = "true" ]; then :; else calc_suffix=".no-calc"; fi; \
  deploy_mode="$(MODE)"; \
  if [ -z "$$deploy_mode" ]; then deploy_mode="standalone"; fi; \
  echo "docker-compose.$$deploy_mode$$calc_suffix.yml")

.PHONY: deploy build up down logs smoke clean setup

# ---------------------------------------------------------------------------
# deploy — Full deployment pipeline
# ---------------------------------------------------------------------------
deploy:
	@echo "========================================"
	@echo " FireCalc Docker Deploy"
	@echo "========================================"
	@echo ""
	@echo "Compose file : $(COMPOSE_FILE)"
	@echo "CALC_SERVER  : $${CALC_SERVER:-<unset>}"
	@echo "MODE         : $${MODE:-standalone}"
	@echo ""
	@# Step 1: Setup environment
	@echo "[1/6] Setting up environment..."
	bash scripts/setup-env.sh
	@# Step 2: Clone repos and build artifacts
	@echo "[2/6] Cloning repos and building artifacts..."
	bash scripts/deploy.sh
	@# Step 3: Activate compose variant
	@echo "[3/6] Activating compose variant..."
	rm -f docker-compose.yml
	ln -s "$(COMPOSE_FILE)" docker-compose.yml
	@# Step 4: Build Docker images
	@echo "[4/6] Building Docker images..."
	docker compose build
	@# Step 5: Generate self-signed SSL certificates (if missing)
	@echo "[5/6] Generating SSL certificates..."
	bash scripts/generate-certs.sh
	@# Step 6: Start containers
	@echo "[6/6] Starting containers..."
	docker compose up -d
	@echo ""
	@echo "Deployment complete."
	@echo "Run 'make smoke' to verify."
# ---------------------------------------------------------------------------
# build — Clone, build artifacts, build images (no up)
# ---------------------------------------------------------------------------
build:
	@echo "Building artifacts and Docker images..."
	bash scripts/setup-env.sh
	bash scripts/deploy.sh
	rm -f docker-compose.yml
	ln -s "$(COMPOSE_FILE)" docker-compose.yml
	docker compose build
	@echo "Build complete."

# ---------------------------------------------------------------------------
# up — Start containers (requires prior build or existing images)
# ---------------------------------------------------------------------------
up:
	@test -L docker-compose.yml || { echo "ERROR: No active compose variant. Run 'make build' or 'make deploy' first."; exit 1; }
	bash scripts/generate-certs.sh
	docker compose up -d
	@echo "Containers started."
# ---------------------------------------------------------------------------
# down — Stop and remove containers
# ---------------------------------------------------------------------------
down:
	@test -L docker-compose.yml || { echo "No active compose variant found."; exit 0; }
	docker compose down
	@echo "Containers stopped."

# ---------------------------------------------------------------------------
# logs — Stream logs from all services
# ---------------------------------------------------------------------------
logs:
	@test -L docker-compose.yml || { echo "ERROR: No active compose variant. Run 'make build' or 'make deploy' first."; exit 1; }
	docker compose logs -f

# ---------------------------------------------------------------------------
# smoke — Run smoke tests against running services
# ---------------------------------------------------------------------------
smoke:
	bash scripts/smoke-test-deploy.sh

# ---------------------------------------------------------------------------
# setup — Setup environment and activate compose variant
# ---------------------------------------------------------------------------
setup:
	bash scripts/setup-env.sh
	rm -f docker-compose.yml
	ln -s "$(COMPOSE_FILE)" docker-compose.yml
	@echo "Environment setup complete. Active compose: $(COMPOSE_FILE)"

# ---------------------------------------------------------------------------
# clean — Remove build artifacts and generated files
# ---------------------------------------------------------------------------
clean:
	@echo "Cleaning build artifacts..."
	rm -rf .build/
	rm -rf artifacts/
	rm -rf .certs/
	rm -rf certbot-webroot/
	rm -f docker-compose.yml
	@echo "Clean complete."
