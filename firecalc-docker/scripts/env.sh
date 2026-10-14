#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Deployment environment variable reader
# =============================================================================
# Sourced by deploy.sh and smoke-test-deploy.sh; not runnable standalone. The
# caller must define PROJECT_DIR as the firecalc-docker directory: .env is
# resolved relative to it. No `set -euo pipefail` here: the options are
# inherited from the sourcing script.

# Value of a deployment environment variable: a non-empty exported value wins;
# an empty or unset one counts as not set, and the value then comes from .env —
# the deployment environment file. Empty-means-unset is what makes the fallback
# work at all: firecalc-docker/Makefile does `CALC_SERVER ?=` + `export
# CALC_SERVER`, so a set-ness test would ignore .env for that key permanently.
# .env is read by sourcing it in a subshell, the way setup-env.sh does, so
# quoting, comments and `export` prefixes are handled; the subshell keeps its
# contents out of the build environment.
deployment_env() {
    local key="$1"
    if [[ -n "${!key:-}" ]]; then
        printf '%s' "${!key}"
        return
    fi
    if [[ -f "$PROJECT_DIR/.env" ]]; then
        ( set -a; source "$PROJECT_DIR/.env"; printf '%s' "${!key:-}" )
    fi
}
