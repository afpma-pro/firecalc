#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#

export SHELL := /bin/bash

## VARS AND ENVS
REPO_DIR ?= $(shell pwd | xargs echo -n)
GIT_COMMIT_HASH ?= $(shell git rev-parse --short=8 HEAD)
UI_BASE_VERSION ?= $(shell grep 'lazy val ui_base_version' build.sbt | sed 's/.*= "\(.*\)".*/\1/')
ENGINE_VERSION ?= $(shell grep 'lazy val engine_version' build.sbt | sed 's/.*= "\(.*\)".*/\1/')
GITHUB_REPO_OWNER ?= $(shell grep 'lazy val githubOwner' build.sbt | sed 's/.*= "\(.*\)".*/\1/')
GITHUB_REPO_NAME ?= $(shell grep 'lazy val githubRepo' build.sbt | sed 's/.*= "\(.*\)".*/\1/')

## MAIN ##############################
.PHONY: check clean fmt ui-setup electron-setup ui-status

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
		modules/dto/.js \
		modules/dto/.jvm \
		modules/engine/.js \
		modules/engine/.jvm \
		modules/fdim/target \
		modules/i18n/.js \
		modules/i18n/.jvm \
		modules/i18n-utils/.js \
		modules/i18n-utils/.jvm \
		modules/invoices/target \
		modules/invoices-i18n/target \
		modules/invoices/.bsp \
		modules/invoices/.scala-build \
		modules/labo/target \
		modules/payments/src/main/resources/moleculeGen \
		modules/payments/target \
		modules/payments-i18n/.js \
		modules/payments-i18n/.jvm \
		modules/payments-i18n/target \
		modules/payments-shared/.js \
		modules/payments-shared/.jvm \
		modules/payments-shared-i18n/.js \
		modules/payments-shared-i18n/.jvm \
		modules/reports/target \
		modules/ui/target \
		modules/ui/dist \
		modules/ui/.vite \
		modules/ui/node_modules \
		modules/ui/firecalc-ui.js \
		modules/ui-i18n/.js \
		modules/ui-i18n/.jvm \
		modules/units/.js \
		modules/units/.jvm \
		modules/utils/.js \
		modules/utils/.jvm \
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

setup-all: ui-setup electron-setup sync-build-config
	@echo "All dependencies installed successfully!"
	@echo "Run 'make dev-env-setup' to verify configuration files"

ui-setup:
	@echo "Installing UI dependencies..."
	@cd modules/ui && npm install

electron-setup:
	@echo "Installing Electron dependencies..."
	@cd web && npm install

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

kill-vite:
	@echo "Killing processes on port 5173..."
	@lsof -ti:5173 | xargs kill -9 2>/dev/null || echo "No processes found on port 5173"

dev-open-browser:
	@echo "Opening browser to Vite dev server..."
	@xdg-open http://localhost:5173 2>/dev/null || open http://localhost:5173 2>/dev/null || echo "Please open http://localhost:5173 in your browser"

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
	@cd modules/ui && npm run build

dev-web-ui-open:
	@echo "Opening browser and starting UI dev server..."
	@open http://localhost:5173
	@cd modules/ui && npm run dev

## ================================
## DEVELOPMENT - ELECTRON
## ================================

dev-electron-app-run-vite:
	@echo "Starting Electron desktop app with Vite dev server (live reload)..."
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
	@sbt "payments/run"

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

staging-web-ui-build:
	@echo "Building UI for staging environment..."
	$(call generate_ui_version,staging)
	@cd modules/ui && npm run build:staging

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

prod-web-ui-build:
	@echo "Building UI for production..."
	$(call generate_ui_version,)
	@cd modules/ui && npm run build:production

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
	@sbt ui/syncBuildConfig
	@sbt ui/fastLinkJS
	@cd modules/ui && npm run build

# Shared target for optimized staging builds
staging-electron-ui-build:
	@sbt ui/syncBuildConfig
	@sbt ui/fullLinkJS
	@cd modules/ui && npm run build:staging

# Shared target for optimized production builds
prod-electron-ui-build:
	@sbt ui/syncBuildConfig
	@sbt ui/fullLinkJS
	@cd modules/ui && npm run build:production

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
	@cd web && npm run build:electron:staging
	@rm -f web/.dev-build

staging-electron-package-mac:
	@echo "Building Electron app for macOS (staging-optimized)..."
	@make staging-electron-ui-build
	@echo "true" > web/.dev-build
	@cd web && npm run build:electron:staging -- --mac
	@rm -f web/.dev-build

staging-electron-package-win:
	@echo "Building Electron app for Windows (staging-optimized)..."
	@make staging-electron-ui-build
	@echo "true" > web/.dev-build
	@cd web && npm run build:electron:staging -- --win
	@rm -f web/.dev-build

staging-electron-package-linux:
	@echo "Building Electron app for Linux (staging-optimized)..."
	@make staging-electron-ui-build
	@echo "true" > web/.dev-build
	@cd web && npm run build:electron:staging -- --linux
	@rm -f web/.dev-build

## ================================
## PRODUCTION - ELECTRON PACKAGING
## ================================

prod-electron-package-all:
	@echo "Building Electron app for all platforms (production-optimized)..."
	@make prod-electron-ui-build
	@cd web && npm run build:electron:production

prod-electron-package-mac:
	@echo "Building Electron app for macOS (production-optimized)..."
	@make prod-electron-ui-build
	@cd web && npm run build:electron:production -- --mac

prod-electron-package-win:
	@echo "Building Electron app for Windows (production-optimized)..."
	@make prod-electron-ui-build
	@cd web && npm run build:electron:production -- --win

prod-electron-package-linux:
	@echo "Building Electron app for Linux (production-optimized)..."
	@make prod-electron-ui-build
	@cd web && npm run build:electron:production -- --linux

## ================================
## DOCKER DEPLOYMENT
## ================================

# Deploy UI to Docker Compose (production)
prod-docker-deploy-up:
	@echo "Deploying UI to Docker (production)..."
	@make prod-web-ui-build
	@cd docker && docker-compose down && docker-compose up -d --build
	@echo "✅ Deployment complete!"
	@echo "   UI:  https://\$${UI_DOMAIN}"
	@echo "   API: https://\$${API_DOMAIN}"
	@echo "   (Configured in docker/.env)"

# Deploy UI to Docker Compose (staging)
staging-docker-deploy-up:
	@echo "Deploying UI to Docker (staging)..."
	@make staging-web-ui-build
	@cd docker && docker-compose down && docker-compose up -d --build
	@echo "✅ Deployment complete!"
	@echo "   UI:  https://\$${UI_DOMAIN}"
	@echo "   API: https://\$${API_DOMAIN}"
	@echo "   (Configured in docker/.env)"

# Deploy UI to Docker Compose (development)
dev-docker-deploy-up:
	@echo "Deploying UI to Docker (development)..."
	@make dev-web-ui-build
	@cd docker && docker-compose down && docker-compose up -d --build
	@echo "✅ Deployment complete!"
	@echo "   UI:  https://\$${UI_DOMAIN}"
	@echo "   API: https://\$${API_DOMAIN}"
	@echo "   (Configured in docker/.env)"

# Stop Docker containers
docker-deploy-down:
	@echo "Stopping Docker containers..."
	@cd docker && docker-compose down
	@echo "✅ Containers stopped!"

# Restart Docker containers without rebuilding
docker-deploy-restart:
	@echo "Restarting Docker containers..."
	@cd docker && docker-compose restart
	@echo "✅ Containers restarted!"

# View Docker logs
docker-deploy-logs:
	@cd docker && docker-compose logs -f

