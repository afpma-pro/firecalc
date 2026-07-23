#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#

export SHELL := /bin/bash

## VARS AND ENVS
FIRECALC_VITE_DEV_SERVER_PORT ?= 5173
export FIRECALC_VITE_DEV_SERVER_PORT
REPO_DIR ?= $(shell pwd | xargs echo -n)
GIT_COMMIT_HASH ?= $(shell git rev-parse --short=8 HEAD)
UI_BASE_VERSION ?= $(shell grep 'lazy val ui_base_version' build.sbt | sed 's/.*= "\(.*\)".*/\1/')
ENGINE_VERSION ?= $(shell grep 'lazy val engine_version' build.sbt | sed 's/.*= "\(.*\)".*/\1/')
GITHUB_REPO_OWNER ?= $(shell grep 'lazy val githubOwner' build.sbt | sed 's/.*= "\(.*\)".*/\1/')
GITHUB_REPO_NAME ?= $(shell grep 'lazy val githubRepo' build.sbt | sed 's/.*= "\(.*\)".*/\1/')

## MAIN ##############################
.PHONY: check clean fmt ui-setup electron-setup landing-setup landing-build ui-status run-validation update-validation

## ================================
## UTILITY TARGETS
## ================================

check:
	@echo "UI_BASE_VERSION: $(UI_BASE_VERSION)"
	@echo "ENGINE_VERSION: $(ENGINE_VERSION)"
	@echo "GIT_COMMIT_HASH: $(GIT_COMMIT_HASH)"
	@echo "REPO_DIR: $(REPO_DIR)"
	@echo "GITHUB_REPO_OWNER: $(GITHUB_REPO_OWNER)"
	@echo "GITHUB_REPO_NAME: $(GITHUB_REPO_NAME)"

clean:
	@echo "Cleaning all build artifacts..."
	@sbt clean
	@rm -rf .bloop .bsp .metals \
		modules/catalog/.js/target \
		modules/catalog/.jvm/target \
		modules/domain/.js/target \
		modules/domain/.jvm/target \
		modules/dto/.js/target \
		modules/dto/.jvm/target \
		modules/engine/.js/target \
		modules/engine/.jvm/target \
		modules/engine-13384-common/.js/target \
		modules/engine-13384-common/.jvm/target \
		modules/engine-13384-strict/.js/target \
		modules/engine-13384-strict/.jvm/target \
		modules/engine-15544-common/.js/target \
		modules/engine-15544-common/.jvm/target \
		modules/engine-15544-labo/.js/target \
		modules/engine-15544-labo/.jvm/target \
		modules/engine-15544-mce/.js/target \
		modules/engine-15544-mce/.jvm/target \
		modules/engine-15544-strict/.js/target \
		modules/engine-15544-strict/.jvm/target \
		modules/engine-kernel/.js/target \
		modules/engine-kernel/.jvm/target \
		modules/engine-validation/target \
		modules/fdim/target \
		modules/graph/target \
		modules/i18n/.js/target \
		modules/i18n/.jvm/target \
		modules/i18n-utils/.js/target \
		modules/i18n-utils/.jvm/target \
		modules/invoices/target \
		modules/invoices/.bsp \
		modules/invoices/.scala-build \
		modules/invoices-i18n/target \
		modules/labo/target \
		modules/laminar-form-core/target \
		modules/laminar-form-coulomb/target \
		modules/laminar-form-daisyui/target \
		modules/laminar-form-derivation/target \
		modules/laminar-form-i18n/target \
		modules/payments/target \
		modules/payments/src/main/resources/moleculeGen \
		modules/payments-i18n/target \
		modules/payments-shared/.js/target \
		modules/payments-shared/.jvm/target \
		modules/payments-shared-i18n/.js/target \
		modules/payments-shared-i18n/.jvm/target \
		modules/reports/target \
		modules/ui/target \
		modules/ui/dist \
		modules/ui/.vite \
		modules/ui/node_modules \
		modules/ui/firecalc-ui.js \
		modules/ui-i18n/.js/target \
		modules/units/.js/target \
		modules/units/.jvm/target \
		modules/utils/.js/target \
		modules/utils/.jvm/target \
		modules/viz/target \
		modules/xlsx_catalog/target \
		web/dist \
		web/dist-app \
		web/dist-static \
		web/electron-app/dist \
		web/node_modules \
		web/.dev-build \
		web/.env.electron \
		web/generated-constants.js \
		target \
		project/.bloop \
		project/project/.bloop \
		project/target \
		project/project/target \
		project/project/project/target
	@echo "Clean complete!"

fmt:
	@scalafmt

fmt-check:
	@scalafmt --check

update-deps:
	@echo "Updating sbt dependencies..."
	@sbt update
	@echo "Dependencies updated. Run 'make dev-ui-compile' to recompile with new dependencies."

# Sync build configuration from build.sbt to generated files
# - Updates web/package.json with version
# - Generates web/.env.electron with GitHub repository environment variables
# - Generates web/generated-constants.js with repository constants for JavaScript
sync-build-config:
	@echo "Syncing build configuration (version + repository info)..."
	@sbt "ui/syncBuildConfig"
	@echo "Generated:"
	@echo "  - web/package.json (version synced)"
	@echo "  - web/.env.electron (GitHub repo env vars)"
	@echo "  - web/generated-constants.js (GitHub repo constants)"

## ================================
## SETUP TARGETS (run these first)
## ================================

setup-all: ui-setup electron-setup landing-setup sync-build-config build-viz build-graph
	@echo "All dependencies installed successfully!"
	@echo "Run 'make dev-env-setup' to verify configuration files"

ui-setup:
	@echo "Installing UI dependencies..."
	@cd modules/ui && npm install

build-viz:
	@echo "Building viz bundle (filaire-viz.ts → filaire-viz.js)..."
	@cd modules/ui && npm run build:viz

build-graph:
	@echo "Building graph bundle (graph-viz.ts → graph-viz.js)..."
	@cd modules/ui && npm run build:graph

electron-setup:
	@echo "Installing Electron dependencies..."
	@cd web && npm install

landing-setup:
	@echo "Installing landing page dependencies..."
	@cd web/landing && npm install

landing-build:
	@echo "Building landing page (Next.js static export)..."
	@cd web/landing && npm run build

## ================================
## STATUS TARGETS
## ================================

ui-status:
	@echo "UI/JS Vite Status:"
	@ps -aef | grep vite || echo "No Vite processes running"

status-all: ui-status
	@echo ""
	@echo "Electron Status:"
	@ps -aef | grep "electron.*main.js" | grep -v grep || echo "No Electron processes running"
	@echo ""
	@echo "SBT Status:"
	@ps -aef | grep "sbt.*fastLinkJS" | grep -v grep || echo "No SBT compilation running"

## ================================
## UTILITY TARGETS
## ================================

# Generate version files for the UI
# Args: $(1) = environment suffix (e.g., "dev", "staging", or empty for prod)
define generate_ui_version
	@echo "Generating version files for environment: $(1)"
	@echo "export const UI_BASE_VERSION = '$(UI_BASE_VERSION)';" > modules/ui/firecalc-ui.js
	@echo "export const ENGINE_VERSION = '$(ENGINE_VERSION)';" >> modules/ui/firecalc-ui.js
	@echo "export const GIT_HASH = '$(GIT_COMMIT_HASH)';" >> modules/ui/firecalc-ui.js
	@if [ -z "$(1)" ]; then \
		echo "export const UI_FULL_VERSION = '$(UI_BASE_VERSION)+engine-$(ENGINE_VERSION)-$(GIT_COMMIT_HASH)';" >> modules/ui/firecalc-ui.js; \
	else \
		echo "export const UI_FULL_VERSION = '$(UI_BASE_VERSION)-$(1)+engine-$(ENGINE_VERSION)-$(GIT_COMMIT_HASH)';" >> modules/ui/firecalc-ui.js; \
	fi
endef

define copy_landing_page
	@echo "Building and copying landing page to dist-app..."
	@cd web/landing && npm run build
	@cp -r web/landing/out/en web/landing/out/fr web/landing/out/_next web/dist-app/ 2>/dev/null || true
	@cp -r web/landing/out/assets web/dist-app/ 2>/dev/null || true
	@cp web/landing/out/404.html web/dist-app/ 2>/dev/null || true
endef

kill-vite:
	@echo "Killing processes on port $(FIRECALC_VITE_DEV_SERVER_PORT)..."
	@lsof -ti:$(FIRECALC_VITE_DEV_SERVER_PORT) | xargs kill -9 2>/dev/null || echo "No processes found on port $(FIRECALC_VITE_DEV_SERVER_PORT)"

dev-open-browser:
	@echo "Opening browser to Vite dev server..."
	@xdg-open http://localhost:$(FIRECALC_VITE_DEV_SERVER_PORT) 2>/dev/null || open http://localhost:$(FIRECALC_VITE_DEV_SERVER_PORT) 2>/dev/null || echo "Please open http://localhost:$(FIRECALC_VITE_DEV_SERVER_PORT) in your browser"

## ================================
## DEVELOPMENT - UI
## ================================

dev-web-ui-compile:
	@echo "Compiling UI (Scala.js watch mode) for development..."
	$(call generate_ui_version,dev)
	@sbt ~ui/fastLinkJS

dev-web-ui-run:
	@echo "Starting UI dev server in development mode..."
	@cd modules/ui && npm run dev

dev-web-ui-build:
	@echo "Building UI for development..."
	$(call generate_ui_version,dev)
	@cd modules/ui && SINGLE_FILE=1 npm run build
	$(call copy_landing_page)

dev-web-ui-open:
	@echo "Opening browser and starting UI dev server on port $(FIRECALC_VITE_DEV_SERVER_PORT)..."
	@open http://localhost:$(FIRECALC_VITE_DEV_SERVER_PORT)
	@cd modules/ui && npm run dev

dev-web-ui-zip:
	@echo "Building and packaging UI for file:// usage..."
	@make dev-web-ui-build
	@cd web/dist-app && zip -r ../../firecalc-web-v$(UI_BASE_VERSION)-dev.zip app/
	@echo "Created firecalc-web-v$(UI_BASE_VERSION)-dev.zip"

## ================================
## DEVELOPMENT - ELECTRON
## ================================

dev-electron-app-run-vite:
	@echo "Starting Electron desktop app with Vite dev server (live reload) on port $(FIRECALC_VITE_DEV_SERVER_PORT)..."
	@echo "Make sure Vite dev server is running: make dev-web-ui-run"
	@echo "And Scala.js is compiling: make dev-web-ui-compile"
	@cd web && npm run dev:vite

dev-electron-app-run:
	@echo "Starting Electron desktop app in development mode (static build)..."
	@echo "Make sure to build the UI first with: make dev-web-ui-build"
	@cd web && npm run dev

## ================================
## DEVELOPMENT - BACKEND
## ================================

dev-backend-run:
	@echo "Starting payments backend in development mode..."
	@FIRECALC_ENV=dev sbt "payments/run"

## ================================
## DEVELOPMENT - SETUP/VERIFICATION
## ================================

dev-env-setup:
	@echo "Setting up development environment..."
	@echo "Checking development configuration files..."
	@test -f configs/dev/payments/payments-config.conf || echo "WARNING: configs/dev/payments/payments-config.conf not found"
	@test -f configs/dev/payments/gocardless-config.conf || echo "WARNING: configs/dev/payments/gocardless-config.conf not found"
	@test -f configs/dev/payments/email-config.conf || echo "WARNING: configs/dev/payments/email-config.conf not found"
	@test -f configs/dev/invoices/company-invoice.yaml || echo "WARNING: configs/dev/invoices/company-invoice.yaml not found"
	@test -f modules/ui/.env.development || echo "WARNING: modules/ui/.env.development not found"
	@echo "Development configuration check complete"

dev-env-test:
	@echo "Testing development environment detection..."
	@sbt "console" <<< 'import afpma.firecalc.config.ConfigPathResolver; println(ConfigPathResolver.debugInfo()); sys.exit(0)'

## ================================
## STAGING - UI
## ================================

staging-web-ui-build: build-viz build-graph
	@echo "Building UI for staging environment..."
	$(call generate_ui_version,staging)
	@cd modules/ui && npm run build:staging
	$(call copy_landing_page)

staging-web-ui-run:
	@echo "Starting UI dev server in staging mode..."
	@cd modules/ui && npm run dev:staging

## ================================
## STAGING - ELECTRON
## ================================

staging-electron-app-run:
	@echo "Starting Electron desktop app in staging mode..."
	@echo "Make sure to build the UI first with: make staging-web-ui-build"
	@cd web && npm run dev

## ================================
## STAGING - BACKEND
## ================================

staging-backend-run:
	@echo "Starting payments backend in staging mode..."
	@FIRECALC_ENV=staging sbt "payments/run"

staging-backend-build:
	@echo "Building for staging deployment..."
	@echo "Copying staging logos for PDF generation..."
	@mkdir -p modules/invoices/configs modules/reports/src/main/resources
	@cp docker/configs/staging/invoices/logo.png modules/invoices/configs/logo.png
	@cp docker/configs/staging/reports/logo.jpg modules/reports/src/main/resources/logo.jpg
	@echo "Building backend JAR..."
	@sbt "payments/assembly"
	@echo "Building UI..."
	@make staging-web-ui-build

## ================================
## STAGING - SETUP/VERIFICATION
## ================================

staging-env-setup:
	@echo "Setting up staging environment..."
	@echo "Checking staging configuration files..."
	@test -f configs/staging/payments/payments-config.conf || echo "WARNING: configs/staging/payments/payments-config.conf not found"
	@test -f configs/staging/payments/gocardless-config.conf || echo "WARNING: configs/staging/payments/gocardless-config.conf not found"
	@test -f configs/staging/payments/email-config.conf || echo "WARNING: configs/staging/payments/email-config.conf not found"
	@test -f configs/staging/invoices/company-invoice.yaml || echo "WARNING: configs/staging/invoices/company-invoice.yaml not found"
	@test -f modules/ui/.env.staging || echo "WARNING: modules/ui/.env.staging not found"
	@echo "Staging configuration check complete"

staging-env-test:
	@echo "Testing staging environment detection..."
	@FIRECALC_ENV=staging sbt "console" <<< 'import afpma.firecalc.config.ConfigPathResolver; println(ConfigPathResolver.debugInfo()); sys.exit(0)'

## ================================
## PRODUCTION - UI
## ================================

prod-web-ui-build: build-viz build-graph
	@echo "Building UI for production..."
	$(call generate_ui_version,)
	@cd modules/ui && npm run build:production
	$(call copy_landing_page)

prod-web-ui-run:
	@echo "Starting UI dev server in production mode (for testing)..."
	@cd modules/ui && npm run dev -- --mode production

## ================================
## PRODUCTION - ELECTRON
## ================================

prod-electron-app-run:
	@echo "Starting Electron desktop app in production mode..."
	@echo "Make sure to build the UI first with: make prod-web-ui-build"
	@cd web && npm run dev

## ================================
## PRODUCTION - BACKEND
## ================================

prod-backend-run:
	@echo "Starting payments backend in production mode..."
	@FIRECALC_ENV=prod sbt "payments/run"

prod-backend-build:
	@echo "Building for production deployment..."
	@echo "Copying production logos for PDF generation..."
	@mkdir -p modules/invoices/configs modules/reports/src/main/resources
	@cp docker/configs/prod/invoices/logo.png modules/invoices/configs/logo.png 2>/dev/null || cp docker/configs/staging/invoices/logo.jpg modules/invoices/configs/logo.jpg
	@cp docker/configs/prod/reports/logo.jpg modules/reports/src/main/resources/logo.jpg 2>/dev/null || cp docker/configs/staging/reports/logo.jpg modules/reports/src/main/resources/logo.jpg
	@echo "Building backend JAR..."
	@sbt "payments/assembly"
	@echo "Building UI..."
	@make prod-web-ui-build

## ================================
## PRODUCTION - SETUP/VERIFICATION
## ================================

prod-env-setup:
	@echo "Setting up production environment..."
	@echo "Checking production configuration files..."
	@test -f configs/prod/payments/payments-config.conf || echo "WARNING: configs/prod/payments/payments-config.conf not found"
	@test -f configs/prod/payments/gocardless-config.conf || echo "WARNING: configs/prod/payments/gocardless-config.conf not found"
	@test -f configs/prod/payments/email-config.conf || echo "WARNING: configs/prod/payments/email-config.conf not found"
	@test -f configs/prod/invoices/company-invoice.yaml || echo "WARNING: configs/prod/invoices/company-invoice.yaml not found"
	@test -f modules/ui/.env.production || echo "WARNING: modules/ui/.env.production not found"
	@echo "Production configuration check complete"

prod-env-test:
	@echo "Testing production environment detection..."
	@FIRECALC_ENV=prod sbt "console" <<< 'import afpma.firecalc.config.ConfigPathResolver; println(ConfigPathResolver.debugInfo()); sys.exit(0)'

## ================================
## SHARED ELECTRON BUILD TARGETS
## ================================

# Shared target for fast development builds
dev-electron-ui-build:
	$(call generate_ui_version,dev)
	@sbt -Dsbt.coursier=true -Dsbt.coursier.parallel-downloads=1 -Dsbt.supershell=false "update; ui/update; ui/syncBuildConfig; ui/fastLinkJS"
	@cd modules/ui && npm run build
	$(call copy_landing_page)

# Shared target for optimized staging builds
staging-electron-ui-build: build-viz build-graph
	$(call generate_ui_version,staging)
	@sbt -Dsbt.coursier=true -Dsbt.coursier.parallel-downloads=1 -Dsbt.supershell=false "update; ui/update; ui/syncBuildConfig; ui/fullLinkJS"
	@cd modules/ui && npm run build:staging
	$(call copy_landing_page)

# Shared target for optimized production builds
prod-electron-ui-build: build-viz build-graph
	$(call generate_ui_version,)
	@sbt -Dsbt.coursier=true -Dsbt.coursier.parallel-downloads=1 -Dsbt.supershell=false "update; ui/update; ui/syncBuildConfig; ui/fullLinkJS"
	@cd modules/ui && npm run build:production
	$(call copy_landing_page)

## ================================
## DEVELOPMENT - ELECTRON PACKAGING
## ================================

dev-electron-package-all:
	@echo "Building Electron app for all platforms (development - fast)..."
	@make dev-electron-ui-build
	@echo "true" > web/.dev-build
	@cd web && npm run build:electron:dev
	@rm -f web/.dev-build

dev-electron-package-mac:
	@echo "Building Electron app for macOS (development - fast)..."
	@make dev-electron-ui-build
	@echo "true" > web/.dev-build
	@cd web && npm run build:electron:dev -- --mac
	@rm -f web/.dev-build

dev-electron-package-win:
	@echo "Building Electron app for Windows (development - fast)..."
	@make dev-electron-ui-build
	@echo "true" > web/.dev-build
	@cd web && npm run build:electron:dev -- --win
	@rm -f web/.dev-build

dev-electron-package-linux:
	@echo "Building Electron app for Linux (development - fast)..."
	@make dev-electron-ui-build
	@echo "true" > web/.dev-build
	@cd web && npm run build:electron:dev -- --linux
	@rm -f web/.dev-build

## ================================
## STAGING - ELECTRON PACKAGING
## ================================

staging-electron-package-all:
	@echo "Building Electron app for all platforms (staging-optimized)..."
	@make staging-electron-ui-build
	@echo "true" > web/.dev-build
	@echo "staging" > web/.build-env
	@cd web && npm run build:electron:staging
	@rm -f web/.dev-build web/.build-env

staging-electron-package-mac:
	@echo "Building Electron app for macOS (staging-optimized)..."
	@make staging-electron-ui-build
	@echo "true" > web/.dev-build
	@echo "staging" > web/.build-env
	@cd web && npm run build:electron:staging -- --mac
	@rm -f web/.dev-build web/.build-env

staging-electron-package-win:
	@echo "Building Electron app for Windows (staging-optimized)..."
	@make staging-electron-ui-build
	@echo "true" > web/.dev-build
	@echo "staging" > web/.build-env
	@cd web && npm run build:electron:staging -- --win
	@rm -f web/.dev-build web/.build-env

staging-electron-package-linux:
	@echo "Building Electron app for Linux (staging-optimized)..."
	@make staging-electron-ui-build
	@echo "true" > web/.dev-build
	@echo "staging" > web/.build-env
	@cd web && npm run build:electron:staging -- --linux
	@rm -f web/.dev-build web/.build-env

## ================================
## PRODUCTION - ELECTRON PACKAGING
## ================================

prod-electron-package-all:
	@echo "Building Electron app for all platforms (production-optimized)..."
	@make prod-electron-ui-build
	@echo "production" > web/.build-env
	@cd web && npm run build:electron:production
	@rm -f web/.build-env

prod-electron-package-mac:
	@echo "Building Electron app for macOS (production-optimized)..."
	@make prod-electron-ui-build
	@echo "production" > web/.build-env
	@cd web && npm run build:electron:production -- --mac
	@rm -f web/.build-env

prod-electron-package-win:
	@echo "Building Electron app for Windows (production-optimized)..."
	@make prod-electron-ui-build
	@echo "production" > web/.build-env
	@cd web && npm run build:electron:production -- --win
	@rm -f web/.build-env

prod-electron-package-linux:
	@echo "Building Electron app for Linux (production-optimized)..."
	@make prod-electron-ui-build
	@echo "production" > web/.build-env
	@cd web && npm run build:electron:production -- --linux
	@rm -f web/.build-env

## ================================
## DOCKER DEPLOYMENT — MODE SETUP
## ================================

# Internal: validate that docker-compose.yml symlink exists
.docker-check-symlink:
	@test -L docker/docker-compose.yml || (echo "ERROR: docker/docker-compose.yml symlink not found." && echo "Run 'make docker-setup-standalone' or 'make docker-setup-behind-proxy' first." && exit 1)

docker-setup-standalone:
	@echo "Setting up Docker for standalone mode..."
	@cd docker && rm -f docker-compose.yml && ln -s docker-compose.standalone.yml docker-compose.yml
	@echo "✅ Symlink: docker-compose.yml → docker-compose.standalone.yml"

docker-setup-behind-proxy:
	@echo "Setting up Docker for behind-proxy mode..."
	@cd docker && rm -f docker-compose.yml && ln -s docker-compose.behind-proxy.yml docker-compose.yml
	@echo "✅ Symlink: docker-compose.yml → docker-compose.behind-proxy.yml"
# Pre-flight: fix database directory permissions on the host for container user (UID 999).
# The entrypoint.sh script handles this automatically inside the container on startup.
# This target is for operators who want to verify/fix host-side permissions before deploying
# (e.g., after manually creating the database directory or switching users).
docker-fix-db-perms:
	@echo "Fixing database directory permissions on host (UID 999)..."
	@if [ ! -d "docker/databases" ]; then \
		echo "WARNING: docker/databases does not exist. Create it first: mkdir -p docker/databases"; \
	fi
	@find docker/databases -type d -exec chmod 700 {} \; 2>/dev/null || true
	@find docker/databases -type f -exec chmod 600 {} \; 2>/dev/null || true
	@chown -R 999:999 docker/databases 2>/dev/null || \
		echo "WARNING: Could not change ownership (may need sudo). Run: sudo chown -R 999:999 docker/databases"
	@echo "✅ Database permissions fixed on host!"


## ================================
## DOCKER DEPLOYMENT
## ================================

# Deploy UI to Docker Compose (production)
prod-docker-deploy-up: .docker-check-symlink
	@echo "Deploying to Docker (production)..."
	@make prod-backend-build
	@cd docker && docker compose down && docker compose up -d --build
	@MODE=$$(readlink docker/docker-compose.yml 2>/dev/null | grep -o 'standalone\|behind-proxy' || echo 'unknown'); \
	if [ "$$MODE" = "standalone" ]; then \
		echo "✅ Deployment complete!"; \
		echo "   UI:  https://$$${UI_DOMAIN}"; \
		echo "   API: https://$$${API_DOMAIN}"; \
		echo "   (Configured in docker/.env)"; \
	elif [ "$$MODE" = "behind-proxy" ]; then \
		echo "✅ Deployment complete!"; \
		echo "   (Behind-proxy mode — URLs managed by outer proxy)"; \
	else \
		echo "✅ Deployment complete!"; \
	fi

# Deploy UI to Docker Compose (staging)
staging-docker-deploy-up: .docker-check-symlink
	@echo "Deploying to Docker (staging)..."
	@make staging-backend-build
	@cd docker && docker compose down && docker compose up -d --build
	@MODE=$$(readlink docker/docker-compose.yml 2>/dev/null | grep -o 'standalone\|behind-proxy' || echo 'unknown'); \
	if [ "$$MODE" = "standalone" ]; then \
		echo "✅ Deployment complete!"; \
		echo "   UI:  https://$$${UI_DOMAIN}"; \
		echo "   API: https://$$${API_DOMAIN}"; \
		echo "   (Configured in docker/.env)"; \
	elif [ "$$MODE" = "behind-proxy" ]; then \
		echo "✅ Deployment complete!"; \
		echo "   (Behind-proxy mode — URLs managed by outer proxy)"; \
	else \
		echo "✅ Deployment complete!"; \
	fi

# Deploy UI to Docker Compose (development)
dev-docker-deploy-up: .docker-check-symlink
	@echo "Deploying UI to Docker (development)..."
	@make dev-web-ui-build
	@cd docker && docker compose down && docker compose up -d --build
	@MODE=$$(readlink docker/docker-compose.yml 2>/dev/null | grep -o 'standalone\|behind-proxy' || echo 'unknown'); \
	if [ "$$MODE" = "standalone" ]; then \
		echo "✅ Deployment complete!"; \
		echo "   UI:  https://$$${UI_DOMAIN}"; \
		echo "   API: https://$$${API_DOMAIN}"; \
		echo "   (Configured in docker/.env)"; \
	elif [ "$$MODE" = "behind-proxy" ]; then \
		echo "✅ Deployment complete!"; \
		echo "   (Behind-proxy mode — URLs managed by outer proxy)"; \
	else \
		echo "✅ Deployment complete!"; \
	fi

# Stop Docker containers
docker-deploy-down: .docker-check-symlink
	@echo "Stopping Docker containers..."
	@cd docker && docker compose down
	@echo "✅ Containers stopped!"

# Restart Docker containers without rebuilding
docker-deploy-restart: .docker-check-symlink
	@echo "Restarting Docker containers..."
	@cd docker && docker compose restart
	@echo "✅ Containers restarted!"

# View Docker logs
docker-deploy-logs: .docker-check-symlink
	@cd docker && docker compose logs -f


## ================================
## ENGINE VALIDATION
## ================================

# Run engine golden-file validation tests
run-validation:
	sbt --client "engineValidation/test"

# Update golden reference files from current output.
# Review changes with 'git diff' before committing.
update-validation:
	@cp "modules/engine/validation/cas_types_13384/current/C2.afpma.txt" \
		"modules/engine-validation/src/test/resources/validation/cas_types_13384/C2.afpma.txt"
	@cp "modules/engine/validation/cas_types_13384/current/C16.afpma.txt" \
		"modules/engine-validation/src/test/resources/validation/cas_types_13384/C16.afpma.txt"
	@cp "modules/engine/validation/cas_types_15544/current/01 - Colonne ascendante.afpma.txt" \
		"modules/engine-validation/src/test/resources/validation/cas_types_15544/01 - Colonne ascendante.afpma.txt"
	@cp "modules/engine/validation/cas_types_15544/current/02 - Kachelofen.afpma.txt" \
		"modules/engine-validation/src/test/resources/validation/cas_types_15544/02 - Kachelofen.afpma.txt"
	@cp "modules/engine/validation/cas_types_15544/current/03 - Cas pratique.afpma.txt" \
		"modules/engine-validation/src/test/resources/validation/cas_types_15544/03 - Cas pratique.afpma.txt"
	@echo "Golden files updated. Review with 'git diff' before committing."
