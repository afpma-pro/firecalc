#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Stop sbt watch mode

PID_FILE=".logs/sbt-compile.pid"
SCOPE_FILE=".logs/sbt-compile.scope"
CLIENT_FILE=".logs/sbt-compile.client"

if [ ! -f "$PID_FILE" ]; then
    echo "No sbt watch process found"
    exit 0
fi

PID=$(cat "$PID_FILE")

# Detect client mode
CLIENT_MODE=false
if [ -f "$CLIENT_FILE" ]; then
    CLIENT_MODE=$(cat "$CLIENT_FILE" | tr -d '[:space:]')
fi

if [ "$CLIENT_MODE" = "true" ]; then
    PROCESS_LABEL="sbt client process"
else
    PROCESS_LABEL="sbt watch process"
fi

if kill -0 "$PID" 2>/dev/null; then
    if kill "$PID" 2>/dev/null; then
        echo "Stopped $PROCESS_LABEL (PID: $PID)"
        rm -f "$PID_FILE" "$SCOPE_FILE" "$CLIENT_FILE"
    else
        echo "Failed to stop process (PID: $PID)"
        echo "You may need to run: kill $PID"
        exit 1
    fi
else
    echo "Process already stopped (PID: $PID)"
    rm -f "$PID_FILE" "$SCOPE_FILE" "$CLIENT_FILE"
fi
