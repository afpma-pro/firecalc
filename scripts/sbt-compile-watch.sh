#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Start sbt in watch mode with output redirection

set -e

SCOPE=${1:-}  # Optional: engine, ui, etc.
LOGS_DIR=".logs"
LOG_FILE="$LOGS_DIR/sbt-compile.log"
PID_FILE="$LOGS_DIR/sbt-compile.pid"

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

# Build sbt command
if [ -z "$SCOPE" ]; then
    SBT_CMD="~compile"
    echo "Starting sbt watch mode for full project..."
else
    SBT_CMD="~${SCOPE}/compile"
    echo "Starting sbt watch mode for module: $SCOPE"
fi

# Start sbt in background
nohup sbt "$SBT_CMD" > "$LOG_FILE" 2>&1 &
NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"

echo "Started sbt watch mode"
echo "  Command: sbt '$SBT_CMD'"
echo "  PID: $NEW_PID"
echo "  Log: $LOG_FILE"
echo ""
echo "Use ./scripts/sbt-compile-check.sh to check compilation status"
echo "Use ./scripts/sbt-compile-stop.sh to stop"
