#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Check sbt compilation status from log file.
#
# This script is scope-aware: it reads the compilation scope from
# .logs/sbt-compile.scope (written by sbt-compile-watch.sh) and waits
# for the target module's [FIRECALC_COMPILE_DONE] marker before
# declaring success. This prevents false positives when a dependency
# module succeeds but the target module hasn't compiled yet.
#
# The detection strategy uses the "Monitoring source files" line that
# sbt prints at the end of each watch cycle as the definitive
# cycle-boundary marker. This makes the script robust regardless of
# log size or number of error lines.
#
# Exit codes:
#   0 - SUCCESS
#   1 - ERROR (compilation failed)
#   2 - IN_PROGRESS (still compiling)
#   3 - NOT_RUNNING (sbt process not found)
#   4 - TIMEOUT (exceeded max wait time)

LOGS_DIR=".logs"
LOG_FILE="$LOGS_DIR/sbt-compile.log"
PID_FILE="$LOGS_DIR/sbt-compile.pid"
SCOPE_FILE="$LOGS_DIR/sbt-compile.scope"

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

# Resolve the expected module name from the stored scope.
# Scope "engine" → expected marker "firecalc-engine"
# Scope "" (empty / full project) → expected marker "firecalc-root"
resolve_expected_module() {
    local scope=""
    if [ -f "$SCOPE_FILE" ]; then
        scope=$(cat "$SCOPE_FILE" | tr -d '[:space:]')
    fi

    if [ -z "$scope" ]; then
        echo "firecalc-root"
    else
        echo "firecalc-${scope}"
    fi
}

EXPECTED_MODULE=$(resolve_expected_module)

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

    # Find the last "Monitoring source files" line number — this marks the end
    # of a complete watch cycle.
    local monitor_line
    monitor_line=$(grep -n "Monitoring source files" "$LOG_FILE" | tail -1 | cut -d: -f1)

    if [ -z "$monitor_line" ]; then
        # No monitoring line yet — cycle not complete
        echo "STATUS: IN_PROGRESS"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "Waiting for module '${EXPECTED_MODULE}' to finish compiling..."
        fi
        return 2
    fi

    # Check if a NEW cycle has started after the last "Monitoring source files" line.
    # When sbt detects a file change, it prints lines like:
    #   [info] Build triggered by ...
    #   [info] compiling ...
    # If there's meaningful content after the last monitoring line (beyond the
    # "Press <enter>" prompt that immediately follows it), a new cycle is in progress.
    local total_lines
    total_lines=$(wc -l < "$LOG_FILE")
    # The "Press <enter>" line is typically 1 line after the monitoring line
    local lines_after_monitor=$(( total_lines - monitor_line ))
    if [ "$lines_after_monitor" -gt 2 ]; then
        # More than the "Press <enter>" line after monitoring → new cycle started
        echo "STATUS: IN_PROGRESS"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "Waiting for module '${EXPECTED_MODULE}' to finish compiling..."
        fi
        return 2
    fi

    # Cycle is complete. Extract the latest cycle text.
    # Find the previous "Monitoring source files" line (start of this cycle)
    local prev_monitor_line
    prev_monitor_line=$(grep -n "Monitoring source files" "$LOG_FILE" | tail -2 | head -1 | cut -d: -f1)

    local cycle_start
    if [ "$prev_monitor_line" = "$monitor_line" ]; then
        # Only one monitoring line — first cycle, start from line 1
        cycle_start=1
    else
        # Start from the line after the previous monitoring line
        cycle_start=$((prev_monitor_line + 1))
    fi

    local cycle_text
    cycle_text=$(sed -n "${cycle_start},${monitor_line}p" "$LOG_FILE")

    # Check if target module's FIRECALC_COMPILE_DONE marker is present in this cycle
    local has_target_marker
    has_target_marker=$(echo "$cycle_text" | grep -c "\[FIRECALC_COMPILE_DONE\] module=${EXPECTED_MODULE}$")

    # Check for [success] line in this cycle
    local has_success
    has_success=$(echo "$cycle_text" | grep -c "^\[success\] Total time:")

    # Check for [error] lines in this cycle
    local has_errors
    has_errors=$(echo "$cycle_text" | grep -c "^\[error\]")

    # Case 1: Target module marker found AND [success] present AND no errors → SUCCESS
    if [ "$has_target_marker" -gt 0 ] && [ "$has_success" -gt 0 ] && [ "$has_errors" -eq 0 ]; then
        echo "STATUS: SUCCESS"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "$cycle_text" | grep "^\[success\] Total time:" | tail -1
            local warnings
            warnings=$(echo "$cycle_text" | grep "^\[warn\]")
            if [ -n "$warnings" ]; then
                echo "---WARNINGS---"
                echo "$warnings"
            fi
        fi
        return 0
    fi

    # Case 2: Errors detected in this cycle → ERROR
    if [ "$has_errors" -gt 0 ]; then
        echo "STATUS: ERROR"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "---ERRORS---"
        fi
        echo "$cycle_text" | grep "^\[error\]"
        if [ "$ERRORS_ONLY" = false ]; then
            local warnings
            warnings=$(echo "$cycle_text" | grep "^\[warn\]")
            if [ -n "$warnings" ]; then
                echo "---WARNINGS---"
                echo "$warnings"
            fi
        fi
        return 1
    fi

    # Case 3: Success but target module marker not found
    # (nothing was recompiled in this cycle, e.g., no source changes)
    if [ "$has_success" -gt 0 ]; then
        echo "STATUS: SUCCESS"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "$cycle_text" | grep "^\[success\] Total time:" | tail -1
            echo "(Note: target module '${EXPECTED_MODULE}' was not recompiled in this cycle)"
        fi
        return 0
    fi

    # Fallback: still in progress (shouldn't normally reach here)
    echo "STATUS: IN_PROGRESS"
    if [ "$ERRORS_ONLY" = false ]; then
        echo "Waiting for module '${EXPECTED_MODULE}' to finish compiling..."
    fi
    return 2
}

if [ "$WAIT_MODE" = true ]; then
    # Exponential backoff: start at 500ms, double each iteration, max 16s per check
    # Total max wait time: 120s (2 minutes) for full project compilation
    DELAY_MS=500
    MAX_DELAY_MS=16000
    TOTAL_WAIT_MS=0
    MAX_TOTAL_WAIT_MS=380000  # 6 minutes

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
