# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

# =============================================================================
# FireCalc Docker — Makefile
# =============================================================================
# Deployment orchestration: clone source repos at pinned refs, build artifacts,
# build Docker images, and manage the container lifecycle.
#
# Environment Variables (set before invoking make, or in your shell profile):
#   CALC_SERVER  — Set to "true" to deploy with calc-server (Mode B). This is
#                  the only calculation seam for Ecolabeled and Door15aCatalog:
#                  no Austrian engine source ships in this repository.
#                  Unset or empty selects without calc-server (Mode A) — no
#                  calculation seam at all, NOT local computation: the two
#                  Austrian kinds are then unavailable (payments PDFs for them
#                  fail) and should be disabled via firebox-availability and
#                  VITE_FIREBOX_AVAIL_*; see DEPLOYMENT.md.
#   MODE         — "standalone" (default) or "behind-proxy".
#                  Controls TLS termination strategy.
#
# Usage:
#   make deploy          # Full deployment (clone, build, up) — requires .env and configs/<FIRECALC_ENV>/… first ('make setup' creates both)
#   make build           # Clone repos, build artifacts, build images
#   make up              # Start containers (images must already exist)
#   make down            # Stop and remove containers
#   make restart         # Restart containers without rebuilding
#   make logs            # Stream logs from all services
#   make smoke           # Run smoke tests against running services
#   make fix-db-perms    # Fix host-side databases/ permissions (UID 999)
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
#              Mode A is not "calculations run locally": Ecolabeled and
#              Door15aCatalog have no local engine, so they are unavailable.
# MODE: "standalone" (default) or "behind-proxy".
CALC_SERVER ?=
export CALC_SERVER
MODE       ?= standalone

# Resolve the active compose file from CALC_SERVER and MODE.
# CALC_SERVER="true" → with calc-server; anything else → no calc-server.
# The calc-server assembly JAR is built in the private (non-AGPL) repository,
# pinned in VERSIONS and staged by scripts/deploy.sh.
# MODE=standalone (default) or MODE=behind-proxy.
COMPOSE_FILE := $(shell \
  calc_suffix=""; \
  if [ "$(CALC_SERVER)" = "true" ]; then :; else calc_suffix=".no-calc"; fi; \
  deploy_mode="$(MODE)"; \
  if [ -z "$$deploy_mode" ]; then deploy_mode="standalone"; fi; \
  echo "docker-compose.$$deploy_mode$$calc_suffix.yml")

.PHONY: deploy build up down restart logs smoke clean setup fix-db-perms .require-configs .require-variant

# ---------------------------------------------------------------------------
# .require-configs — internal guard: the runtime configs the backend mounts
# ---------------------------------------------------------------------------
# The backend mounts ./configs read-only at /app/configs and selects the
# environment from FIRECALC_ENV (an exported value wins, otherwise .env — the
# same resolution scripts/env.sh implements), so a missing or renamed runtime
# config first surfaces as a crash-looping container, after the whole image
# build. This guard runs before any of that: it names the missing config
# directory or file and where it should come from. It also checks the two things
# a copied template gets wrong in practice: the invoice path the backend actually
# opens (invoice-generation.config-file-path in the payments config — BackendMain
# resolves paymentsConfig.invoiceConfig.configFilePath, and the shipped template
# default names configs/development/invoices/company-invoice.yaml, which does not
# exist inside the container), and the in-file environment selector, which must
# equal the directory name because nothing in the loader cross-checks them
# (ConfigLoader falls back to "development" when the key is absent). The PDF
# logos are checked by scripts/deploy.sh, which fails naming the path before the
# image build can fail at its unconditional COPY of artifacts/logo.png (and logo.jpg).
.require-configs:
	@PROJECT_DIR="$(CURDIR)"; . scripts/env.sh; \
	env_name="$$(deployment_env FIRECALC_ENV || true)"; \
	if [ -z "$$env_name" ]; then \
		echo "ERROR: FIRECALC_ENV is not set — neither in the environment nor in $(CURDIR)/.env."; \
		echo "       Create .env with 'make setup' (it copies .env.defaults) and set FIRECALC_ENV to an environment that has a configs/<env>/ directory."; \
		exit 1; \
	fi; \
	if [ ! -d "configs/$$env_name" ]; then \
		available="$$(for d in configs/*/; do if [ -d "$$d" ]; then basename "$$d"; fi; done | tr '\n' ' ')"; \
		echo "ERROR: FIRECALC_ENV=$$env_name names no deployment: configs/$$env_name/ does not exist."; \
		echo "       Set FIRECALC_ENV in .env (or the environment) to an environment that has a configs/<env>/ directory — currently: $${available:-none}."; \
		exit 1; \
	fi; \
	echo "Checking runtime configuration for FIRECALC_ENV=$$env_name..."; \
	missing=0; \
	for spec in \
		"configs/$$env_name/payments/payments-config.conf|copy configs/templates/payments/payments-config.conf.template from the repository root, drop .template, set environment/section to $$env_name, and set invoice-generation.config-file-path to configs/$$env_name/invoices/invoice-config.yaml" \
		"configs/$$env_name/payments/gocardless-config.conf|copy configs/templates/payments/gocardless-config.conf.template from the repository root, drop .template, and set gocardless.environment to live for production (sandbox elsewhere)" \
		"configs/$$env_name/payments/email-config.conf|copy configs/templates/payments/email-config.conf.template from the repository root, drop .template" \
		"configs/$$env_name/invoices/invoice-config.yaml|copy the tracked configs/staging/invoices/invoice-config.yaml.example, drop .example, and fill in the company values"; do \
		path="$${spec%%|*}"; hint="$${spec#*|}"; \
		if [ ! -f "$$path" ]; then \
			echo "ERROR: missing $$path"; \
			echo "       should come from: $$hint"; \
			missing=1; \
		fi; \
	done; \
	invoice_path="$$(sed -n 's/^[[:space:]]*config-file-path[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "configs/$$env_name/payments/payments-config.conf" | head -n1)"; \
	invoice_env="$$(printf '%s\n' "$$invoice_path" | sed -n 's|^configs/\([a-z]*\)/.*|\1|p')"; \
	if [ -z "$$invoice_path" ]; then \
		echo "ERROR: configs/$$env_name/payments/payments-config.conf names no invoice-generation.config-file-path"; \
		echo "       set it to configs/$$env_name/invoices/invoice-config.yaml"; \
		missing=1; \
	elif [ -n "$$invoice_env" ] && [ "$$invoice_env" != "$$env_name" ]; then \
		echo "ERROR: invoice-generation.config-file-path points into configs/$$invoice_env/ while FIRECALC_ENV=$$env_name"; \
		echo "       each environment's payments config must open its own invoice config: configs/$$env_name/invoices/invoice-config.yaml"; \
		missing=1; \
	elif [ ! -f "$$invoice_path" ]; then \
		echo "ERROR: the payments config opens no invoice config: $$invoice_path"; \
		echo "       invoice-generation.config-file-path must name a file inside the mounted configs/ tree"; \
		missing=1; \
	fi; \
	for cfg in payments/payments-config.conf payments/email-config.conf; do \
		selector="$$(sed -n 's/^[[:space:]]*environment[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "configs/$$env_name/$$cfg" | head -n1)"; \
		if [ "$$selector" != "$$env_name" ]; then \
			echo "ERROR: configs/$$env_name/$$cfg selects environment=\"$$selector\" but FIRECALC_ENV=$$env_name"; \
			echo "       the directory name, the environment selector and the section it names must be the same string"; \
			missing=1; \
		elif ! grep -qE "^[[:space:]]*$$env_name[[:space:]]*[{]" "configs/$$env_name/$$cfg"; then \
			echo "ERROR: configs/$$env_name/$$cfg selects $$env_name but defines no $$env_name { } section"; \
			missing=1; \
		fi; \
	done; \
	if [ -f "configs/$$env_name/payments/gocardless-config.conf" ]; then \
		gc_env="$$(sed -n 's/^[[:space:]]*environment[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "configs/$$env_name/payments/gocardless-config.conf" | head -n1)"; \
		case "$$gc_env" in sandbox|live) ;; *) \
			echo "ERROR: configs/$$env_name/payments/gocardless-config.conf selects environment=\"$$gc_env\"; ConfigLoader reads only gocardless.sandbox and gocardless.live"; \
			echo "       set gocardless.environment to live for production, sandbox elsewhere"; \
			missing=1; ;; \
		esac; \
		if ! grep -qE "^[[:space:]]*$$gc_env[[:space:]]*[{]" "configs/$$env_name/payments/gocardless-config.conf"; then \
			echo "ERROR: the gocardless config selects $$gc_env but defines no $$gc_env { } section"; \
			missing=1; \
		elif sed -n "/^[[:space:]]*$$gc_env[[:space:]]*{/,/^[[:space:]]*}/p" "configs/$$env_name/payments/gocardless-config.conf" | grep -qE '(access-token|webhook-secret)[[:space:]]*=[[:space:]]*[$$][{]'; then \
			echo "ERROR: gocardless.$$gc_env reads its credentials from a GOCARDLESS_* env hook"; \
			echo "       no GOCARDLESS_* variable reaches the container, so those keys resolve absent and startup fails — set them literally"; \
			missing=1; \
		fi; \
	fi; \
	if [ "$$missing" -ne 0 ]; then \
		echo "Refusing to deploy: the backend would fail at startup."; \
		exit 1; \
	fi; \
	echo "Runtime configuration present."

# ---------------------------------------------------------------------------
# deploy — Full deployment pipeline
# ---------------------------------------------------------------------------
deploy: .require-configs
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
# .require-variant — internal guard: an active compose variant must exist
# ---------------------------------------------------------------------------
.require-variant:
	@test -L docker-compose.yml || { echo "ERROR: No active compose variant. Run 'make build' or 'make deploy' first."; exit 1; }

# ---------------------------------------------------------------------------
# up — Start containers (requires prior build or existing images)
# ---------------------------------------------------------------------------
up: .require-variant
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
logs: .require-variant
	docker compose logs -f

# ---------------------------------------------------------------------------
# restart — Restart containers without rebuilding
# ---------------------------------------------------------------------------
restart: .require-variant
	docker compose restart
	@echo "Containers restarted."

# ---------------------------------------------------------------------------
# smoke — Run smoke tests against running services
# ---------------------------------------------------------------------------
smoke:
	bash scripts/smoke-test-deploy.sh

# ---------------------------------------------------------------------------
# fix-db-perms — Pre-flight host-side permissions for the databases/ mount
# ---------------------------------------------------------------------------
# The backend entrypoint fixes permissions inside the container on every start.
# This target fixes the host side before deploying (e.g. after manually
# creating databases/ or switching users). The container user is appuser,
# UID/GID 999 (see Dockerfile.backend).
fix-db-perms:
	@if [ ! -d databases ]; then \
		echo "WARNING: databases/ does not exist. Create it first: mkdir -p databases"; \
	fi
	find databases -type d -exec chmod 700 {} \; 2>/dev/null || true
	find databases -type f -exec chmod 600 {} \; 2>/dev/null || true
	chown -R 999:999 databases 2>/dev/null \
		|| echo "WARNING: Could not change ownership (may need sudo). Run: sudo chown -R 999:999 databases"
	@echo "Database permissions fixed on host."

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
