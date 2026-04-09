#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Start sbt compilation in one of three modes.
#
# Usage:
#   ./scripts/sbt-compile-watch.sh [scope]                 # standalone (one-off)
#   ./scripts/sbt-compile-watch.sh --client [scope]        # unsupervised watch
#   ./scripts/sbt-compile-watch.sh --supervised [scope]    # supervised watch
#
# Modes:
#
#   Standalone (default):
#     Launches a dedicated sbt JVM and runs `sbt [scope/]compile` once.
#     Best for one-off compilation checks. The sbt process exits when done.
#
#   Unsupervised watch (--client):
#     Connects to an already-running sbt server (e.g. started by Scala Metals
#     in VSCode) via `sbt --client ~[scope/]compile`. Fastest option — skips
#     JVM and sbt startup entirely since the server is already warm with
#     cached classloaders and incremental state. Requires an external sbt
#     server to be running. Runs in continuous watch mode.
#
#   Supervised watch (--supervised):
#     Starts a dedicated sbt server, waits for it to be ready, then connects
#     to it via `sbt --client ~[scope/]compile`. Combines the speed of client
#     mode with full lifecycle control — no external sbt server needed.
#     If an sbt server is already reachable, reuses it instead of starting
#     a new one. Use sbt-compile-stop.sh to stop both watch and server.
#
# Scope is an optional sbt sub-project name (e.g. "engine", "ui").
# When omitted, the entire root project is compiled.
#
# Companion scripts:
#   ./scripts/sbt-compile-check.sh  — check compilation status (--wait to poll)
#   ./scripts/sbt-compile-stop.sh   — stop compilation and server

set -e

# Parse arguments
MODE="standalone"
SCOPE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --client) MODE="client"; shift ;;
        --supervised) MODE="supervised"; shift ;;
        *) SCOPE="$1"; shift ;;
    esac
done

LOGS_DIR=".logs"
LOG_FILE="$LOGS_DIR/sbt-compile.log"
PID_FILE="$LOGS_DIR/sbt-compile.pid"
SCOPE_FILE="$LOGS_DIR/sbt-compile.scope"
MODE_FILE="$LOGS_DIR/sbt-compile.mode"
SERVER_PID_FILE="$LOGS_DIR/sbt-server.pid"
SERVER_LOG="$LOGS_DIR/sbt-server.log"
SERVER_FIFO="$LOGS_DIR/sbt-server.fifo"
FIFO_PID_FILE="$LOGS_DIR/sbt-fifo.pid"
EXIT_CODE_FILE="$LOGS_DIR/sbt-compile.exitcode"

mkdir -p "$LOGS_DIR"

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

# Kill existing compile process if running
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Stopping existing compile process (PID: $OLD_PID)..."
        kill_tree "$OLD_PID"
    fi
    rm -f "$PID_FILE"
fi

# Clear log and store metadata
> "$LOG_FILE"
echo "$SCOPE" > "$SCOPE_FILE"
echo "$MODE" > "$MODE_FILE"

# Build sbt compile command
if [ "$MODE" = "standalone" ]; then
    # One-off: no ~ prefix
    if [ -z "$SCOPE" ]; then
        SBT_CMD="compile"
    else
        SBT_CMD="${SCOPE}/compile"
    fi
else
    # Watch modes: ~ prefix for continuous compilation
    if [ -z "$SCOPE" ]; then
        SBT_CMD="~compile"
    else
        SBT_CMD="~${SCOPE}/compile"
    fi
fi

case "$MODE" in
    standalone)
        echo "Starting one-off compilation for ${SCOPE:-full project}..."
        rm -f "$EXIT_CODE_FILE"
        nohup bash -c '
            sbt "$1" > "$2" 2>&1 &
            CHILD=$!
            trap "kill $CHILD 2>/dev/null; wait $CHILD 2>/dev/null; echo \$? > \"$3\"; exit" TERM
            wait $CHILD
            echo $? > "$3"
        ' _ "$SBT_CMD" "$LOG_FILE" "$EXIT_CODE_FILE" &
        NEW_PID=$!
        echo "$NEW_PID" > "$PID_FILE"
        echo "  Mode: standalone (one-off)"
        echo "  Command: sbt '$SBT_CMD'"
        echo "  PID: $NEW_PID"
        ;;

    client)
        echo "Starting unsupervised watch for ${SCOPE:-full project}..."
        nohup sbt --client "$SBT_CMD" > "$LOG_FILE" 2>&1 &
        NEW_PID=$!
        echo "$NEW_PID" > "$PID_FILE"
        echo "  Mode: client (unsupervised — requires external sbt server)"
        echo "  Command: sbt --client '$SBT_CMD'"
        echo "  PID: $NEW_PID"
        ;;

    supervised)
        # Check if an sbt server is already reachable
        NEED_SERVER=true
        if [ -f "$SERVER_PID_FILE" ]; then
            EXISTING_PID=$(cat "$SERVER_PID_FILE")
            if kill -0 "$EXISTING_PID" 2>/dev/null; then
                echo "Reusing existing managed sbt server (PID: $EXISTING_PID)"
                NEED_SERVER=false
            else
                echo "Stale server PID found, cleaning up..."
                rm -f "$SERVER_PID_FILE" "$FIFO_PID_FILE" "$SERVER_FIFO"
            fi
        fi

        if [ "$NEED_SERVER" = true ]; then
            # Check if an external sbt server (e.g. Metals) is already reachable
            if timeout 5 sbt --client "version" > /dev/null 2>&1; then
                echo "Found existing external sbt server, reusing it"
                echo "client" > "$MODE_FILE"
                NEED_SERVER=false
            fi
        fi

        if [ "$NEED_SERVER" = true ]; then
            # Start our own sbt server using a FIFO to keep stdin open
            rm -f "$SERVER_FIFO"
            mkfifo "$SERVER_FIFO"

            nohup sbt < "$SERVER_FIFO" > "$SERVER_LOG" 2>&1 &
            SERVER_PID=$!
            echo "$SERVER_PID" > "$SERVER_PID_FILE"

            # Keep FIFO writer open so sbt doesn't get EOF on stdin
            ( while kill -0 "$SERVER_PID" 2>/dev/null; do sleep 10; done ) > "$SERVER_FIFO" 2>/dev/null &
            FIFO_PID=$!
            echo "$FIFO_PID" > "$FIFO_PID_FILE"

            echo "Started sbt server (PID: $SERVER_PID)"
            echo "Waiting for server to accept connections..."

            MAX_TRIES=30
            for i in $(seq 1 $MAX_TRIES); do
                if timeout 5 sbt --client "version" > /dev/null 2>&1; then
                    echo "Server ready (${i}x2s)"
                    break
                fi
                if [ "$i" -eq "$MAX_TRIES" ]; then
                    echo "ERROR: sbt server failed to start within 60s"
                    kill "$SERVER_PID" "$FIFO_PID" 2>/dev/null || true
                    rm -f "$SERVER_PID_FILE" "$FIFO_PID_FILE" "$SERVER_FIFO"
                    exit 1
                fi
                sleep 2
            done
        fi

        # Start compile watch via client
        nohup sbt --client "$SBT_CMD" > "$LOG_FILE" 2>&1 &
        NEW_PID=$!
        echo "$NEW_PID" > "$PID_FILE"
        echo "  Mode: supervised (managed sbt server + client)"
        echo "  Command: sbt --client '$SBT_CMD'"
        echo "  PID: $NEW_PID"
        if [ -f "$SERVER_PID_FILE" ]; then
            echo "  Server PID: $(cat "$SERVER_PID_FILE")"
        fi
        ;;
esac

echo "  Scope: ${SCOPE:-full project}"
echo "  Log: $LOG_FILE"
echo ""
echo "Use ./scripts/sbt-compile-check.sh [--wait] to check status"
echo "Use ./scripts/sbt-compile-stop.sh to stop"
