#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Check sbt compilation status from log file

LOGS_DIR=".logs"
LOG_FILE="$LOGS_DIR/sbt-compile.log"
PID_FILE="$LOGS_DIR/sbt-compile.pid"

WAIT_MODE=false
ERRORS_ONLY=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --wait) WAIT_MODE=true; shift ;;
        --errors) ERRORS_ONLY=true; shift ;;
        *) shift ;;
    esac
done

check_status() {
    # Check if sbt is running
    if [ ! -f "$PID_FILE" ]; then
        echo "STATUS: NOT_RUNNING"
        echo "Run ./scripts/sbt-compile-watch.sh to start"
        return 3
    fi

    PID=$(cat "$PID_FILE")
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "STATUS: NOT_RUNNING"
        echo "Process stopped unexpectedly. Check $LOG_FILE for errors."
        rm "$PID_FILE"
        return 3
    fi

    # Check if log file exists
    if [ ! -f "$LOG_FILE" ]; then
        echo "STATUS: IN_PROGRESS"
        echo "Waiting for compilation to start..."
        return 2
    fi

    # Get last N lines of log
    LAST_LINES=$(tail -150 "$LOG_FILE")

    # Check for compilation status
    # Look for success first
    if echo "$LAST_LINES" | grep -q "^\[success\] Total time:"; then
        LAST_STATUS=$(echo "$LAST_LINES" | grep -E "^\[(success|error)\]" | tail -1)
        if echo "$LAST_STATUS" | grep -q "^\[success\]"; then
            echo "STATUS: SUCCESS"
            if [ "$ERRORS_ONLY" = false ]; then
                echo "$LAST_STATUS"
            fi
            return 0
        fi
    fi

    # Check for errors
    if echo "$LAST_LINES" | grep -q "^\[error\]"; then
        echo "STATUS: ERROR"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "---ERRORS---"
        fi
        echo "$LAST_LINES" | grep -E "^\[error\]"
        return 1
    fi

    # Otherwise, still compiling
    echo "STATUS: IN_PROGRESS"
    if [ "$ERRORS_ONLY" = false ]; then
        echo "Waiting for compilation..."
    fi
    return 2
}

if [ "$WAIT_MODE" = true ]; then
    # Exponential backoff: start at 500ms, double each iteration, max 16s per check
    # Total max wait time: 120s (2 minutes) for full project compilation
    DELAY_MS=500
    MAX_DELAY_MS=16000
    TOTAL_WAIT_MS=0
    MAX_TOTAL_WAIT_MS=120000  # 2 minutes
    
    while true; do
        check_status
        STATUS=$?
        
        # Exit on success, error, or not running
        if [ $STATUS -eq 0 ] || [ $STATUS -eq 1 ] || [ $STATUS -eq 3 ]; then
            exit $STATUS
        fi
        
        # Check if we've exceeded max total wait time
        if [ $TOTAL_WAIT_MS -ge $MAX_TOTAL_WAIT_MS ]; then
            echo "STATUS: TIMEOUT"
            echo "Compilation did not complete within 2 minutes"
            exit 4
        fi
        
        # Sleep with exponential backoff
        sleep $(echo "scale=3; $DELAY_MS/1000" | bc)
        TOTAL_WAIT_MS=$((TOTAL_WAIT_MS + DELAY_MS))
        
        # Double the delay, cap at max per-check delay
        DELAY_MS=$((DELAY_MS * 2))
        if [ $DELAY_MS -gt $MAX_DELAY_MS ]; then
            DELAY_MS=$MAX_DELAY_MS
        fi
    done
else
    check_status
    exit $?
fi
