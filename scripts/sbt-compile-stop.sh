#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Stop sbt compilation and, in supervised mode, the managed sbt server.
#
# Usage:
#   ./scripts/sbt-compile-stop.sh              # stop compile process
#   ./scripts/sbt-compile-stop.sh --server     # also stop the managed sbt server
#   ./scripts/sbt-compile-stop.sh --clean      # also remove log files
#
# In supervised mode, --server is implied: stopping always cleans up
# the managed server. In client mode (unsupervised), only the compile
# process is stopped — the external sbt server is left untouched.

LOGS_DIR=".logs"
LOG_FILE="$LOGS_DIR/sbt-compile.log"
EXIT_CODE_FILE="$LOGS_DIR/sbt-compile.exitcode"
PID_FILE="$LOGS_DIR/sbt-compile.pid"
SCOPE_FILE="$LOGS_DIR/sbt-compile.scope"
MODE_FILE="$LOGS_DIR/sbt-compile.mode"
SERVER_PID_FILE="$LOGS_DIR/sbt-server.pid"
SERVER_LOG="$LOGS_DIR/sbt-server.log"
SERVER_FIFO="$LOGS_DIR/sbt-server.fifo"
FIFO_PID_FILE="$LOGS_DIR/sbt-fifo.pid"

# Kill a process and all its descendants (depth-first).
# Kills leaves first so nothing reparents to init before we reach it.
kill_tree() {
    local pid=$1
    local children
    children=$(pgrep -P "$pid" 2>/dev/null || true)
    for child in $children; do
        kill_tree "$child"
    done
    kill "$pid" 2>/dev/null || true
}

STOP_SERVER=false
CLEAN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --server) STOP_SERVER=true; shift ;;
        --clean) CLEAN=true; shift ;;
        *) shift ;;
    esac
done

# Detect mode
MODE=""
if [ -f "$MODE_FILE" ]; then
    MODE=$(cat "$MODE_FILE" | tr -d '[:space:]')
fi

# In supervised mode, always stop the server too
if [ "$MODE" = "supervised" ]; then
    STOP_SERVER=true
fi

# Stop the compile process
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        kill_tree "$PID"
        echo "Stopped compile process (PID: $PID)"
    else
        echo "Compile process already stopped (PID: $PID)"
    fi
    rm -f "$PID_FILE"
else
    echo "No compile process found"
fi

# Stop the managed sbt server (supervised mode or --server flag)
if [ "$STOP_SERVER" = true ]; then
    # Stop FIFO writer
    if [ -f "$FIFO_PID_FILE" ]; then
        FIFO_PID=$(cat "$FIFO_PID_FILE")
        kill "$FIFO_PID" 2>/dev/null || true
        rm -f "$FIFO_PID_FILE"
    fi

    # Stop sbt server
    if [ -f "$SERVER_PID_FILE" ]; then
        SERVER_PID=$(cat "$SERVER_PID_FILE")
        if kill -0 "$SERVER_PID" 2>/dev/null; then
            kill_tree "$SERVER_PID"
            echo "Stopped managed sbt server (PID: $SERVER_PID)"
        else
            echo "Managed sbt server already stopped (PID: $SERVER_PID)"
        fi
        rm -f "$SERVER_PID_FILE"
    fi

    rm -f "$SERVER_FIFO"
fi

# Clean up metadata
rm -f "$SCOPE_FILE" "$MODE_FILE"

# Remove log files if --clean
if [ "$CLEAN" = true ]; then
    rm -f "$LOG_FILE" "$EXIT_CODE_FILE" "$SERVER_LOG"
    echo "Cleaned log files"
fi
