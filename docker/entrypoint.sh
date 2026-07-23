#!/bin/bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Payments Backend - Entrypoint Script
# =============================================================================
# Runs as root to fix database directory permissions, then drops to appuser.
#
# Problem: When `sudo docker compose up` is used, the host-side database
# directory is owned by root. The container runs as appuser (UID 999), which
# cannot write to a root-owned directory. SQLite fails with:
#   SQLITE_READONLY_DIRECTORY: attempt to write a readonly database
#
# Solution: This script runs before the application starts. It checks if the
# database directory is writable by appuser, and fixes ownership if needed.
# Then it drops privileges and exec's into the Java process.
# =============================================================================

set -e

# Fix database directory ownership if needed
# The Dockerfile creates /app/databases with appuser ownership, but the bind
# mount from the host may override this (e.g., when host dir is root-owned).
if [ -d "/app/databases" ]; then
    chown -R appuser:appuser /app/databases
    find /app/databases -type d -exec chmod 700 {} \;
    find /app/databases -type f -exec chmod 600 {} \;
fi

# Drop privileges and run the application
# setpriv is part of util-linux (available in Ubuntu/Debian base images).
# --inh-execve drops ambient capabilities when exec happens.
command -v setpriv >/dev/null 2>&1 || { echo "FATAL: setpriv not found (util-linux required)"; exit 1; }
exec setpriv --reuid=appuser --regid=appuser --init-groups --inh-execve "$@"
