#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Start sbt in watch mode with output redirection

set -e

# Parse arguments
CLIENT_MODE=false
SCOPE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --client) CLIENT_MODE=true; shift ;;
        *) SCOPE="$1"; shift ;;
    esac
done

LOGS_DIR=".logs"
LOG_FILE="$LOGS_DIR/sbt-compile.log"
PID_FILE="$LOGS_DIR/sbt-compile.pid"
SCOPE_FILE="$LOGS_DIR/sbt-compile.scope"
CLIENT_FILE="$LOGS_DIR/sbt-compile.client"

# Create logs directory if needed
mkdir -p "$LOGS_DIR"

# Kill existing sbt watch if running
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Stopping existing sbt watch process (PID: $OLD_PID)..."
        kill "$OLD_PID" 2>/dev/null || true
        sleep 1
    fi
    rm "$PID_FILE"
fi

# Clear log file
> "$LOG_FILE"

# Store scope and client mode for the check/stop scripts
# Empty scope = full project compilation (root)
echo "$SCOPE" > "$SCOPE_FILE"
echo "$CLIENT_MODE" > "$CLIENT_FILE"

# Build sbt command
if [ -z "$SCOPE" ]; then
    SBT_CMD="~compile"
else
    SBT_CMD="~${SCOPE}/compile"
fi

# Build sbt invocation
if [ "$CLIENT_MODE" = true ]; then
    SBT_INVOKE="sbt --client"
    MODE_LABEL="client"
else
    SBT_INVOKE="sbt"
    MODE_LABEL="standalone"
fi

echo "Starting sbt watch mode (${MODE_LABEL}) for ${SCOPE:-full project}..."

# Start sbt in background
nohup $SBT_INVOKE "$SBT_CMD" > "$LOG_FILE" 2>&1 &
NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"

echo "Started sbt watch mode"
echo "  Command: $SBT_INVOKE '$SBT_CMD'"
echo "  Mode: $MODE_LABEL"
echo "  PID: $NEW_PID"
echo "  Log: $LOG_FILE"
echo "  Scope: ${SCOPE:-full project}"
echo ""
echo "Use ./scripts/sbt-compile-check.sh to check compilation status"
echo "Use ./scripts/sbt-compile-stop.sh to stop"
