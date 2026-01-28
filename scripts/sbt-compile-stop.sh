#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Stop sbt watch mode

PID_FILE=".logs/sbt-compile.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "No sbt watch process found"
    exit 0
fi

PID=$(cat "$PID_FILE")

if kill -0 "$PID" 2>/dev/null; then
    if kill "$PID" 2>/dev/null; then
        echo "Stopped sbt watch process (PID: $PID)"
        rm "$PID_FILE"
    else
        echo "Failed to stop process (PID: $PID)"
        echo "You may need to run: kill $PID"
        exit 1
    fi
else
    echo "Process already stopped (PID: $PID)"
    rm "$PID_FILE"
fi
