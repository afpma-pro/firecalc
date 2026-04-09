#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Check sbt compilation status from log file.
#
# Usage:
#   ./scripts/sbt-compile-check.sh              # one-shot status check
#   ./scripts/sbt-compile-check.sh --wait       # poll every 2s until done
#   ./scripts/sbt-compile-check.sh --errors     # only show [error] lines (skip success/warnings)
#   ./scripts/sbt-compile-check.sh --wait --errors  # poll + minimal output
#
# This script is scope-aware: it reads the compilation scope from
# .logs/sbt-compile.scope (written by sbt-compile-watch.sh) and waits
# for the target module's [FIRECALC_COMPILE_DONE] marker before
# declaring success. This prevents false positives when a dependency
# module succeeds but the target module hasn't compiled yet.
#
# The detection strategy uses the "Monitoring source files" line that
# sbt prints at the end of each watch cycle as the definitive
# cycle-boundary marker.
#
# In --wait mode, polls every 2 seconds printing a "." for each check
# to minimize output. Final status is printed when compilation completes.
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
MODE_FILE="$LOGS_DIR/sbt-compile.mode"

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

# Detect mode (standalone does not use watch markers)
COMPILE_MODE="standalone"
if [ -f "$MODE_FILE" ]; then
    COMPILE_MODE=$(cat "$MODE_FILE" | tr -d '[:space:]')
fi

check_status() {
    # Check if sbt is running
    if [ ! -f "$PID_FILE" ]; then
        echo "STATUS: NOT_RUNNING"
        echo "Run ./scripts/sbt-compile-watch.sh to start"
        return 3
    fi

    PID=$(cat "$PID_FILE")

    # For standalone mode, check if process exited (= compilation done)
    if [ "$COMPILE_MODE" = "standalone" ]; then
        if ! kill -0 "$PID" 2>/dev/null; then
            # Process finished — check exit status from log
            if [ -f "$LOG_FILE" ]; then
                local has_errors
                has_errors=$(grep -c "^\[error\]" "$LOG_FILE" 2>/dev/null || true)
                if [ "$has_errors" -gt 0 ]; then
                    echo "STATUS: ERROR"
                    if [ "$ERRORS_ONLY" = false ]; then
                        echo "---ERRORS---"
                    fi
                    grep "^\[error\]" "$LOG_FILE"
                    rm -f "$PID_FILE" "$SCOPE_FILE" "$MODE_FILE"
                    return 1
                else
                    echo "STATUS: SUCCESS"
                    if [ "$ERRORS_ONLY" = false ]; then
                        grep "^\[success\] Total time:" "$LOG_FILE" | tail -1 2>/dev/null || true
                    fi
                    rm -f "$PID_FILE" "$SCOPE_FILE" "$MODE_FILE"
                    return 0
                fi
            fi
            echo "STATUS: NOT_RUNNING"
            rm -f "$PID_FILE" "$SCOPE_FILE" "$MODE_FILE"
            return 3
        fi

        # Still running
        echo "STATUS: IN_PROGRESS"
        return 2
    fi

    # Watch modes (client / supervised): process should stay alive
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "STATUS: NOT_RUNNING"
        echo "Process stopped unexpectedly. Check $LOG_FILE"
        rm -f "$PID_FILE"
        return 3
    fi

    # Check if log file exists
    if [ ! -f "$LOG_FILE" ]; then
        echo "STATUS: IN_PROGRESS"
        return 2
    fi

    # Find the last "Monitoring source files" line number — marks end of a cycle
    local monitor_line
    monitor_line=$(grep -n "Monitoring source files" "$LOG_FILE" | tail -1 | cut -d: -f1)

    if [ -z "$monitor_line" ]; then
        echo "STATUS: IN_PROGRESS"
        return 2
    fi

    # Check if a new cycle started after the last monitoring line
    local total_lines
    total_lines=$(wc -l < "$LOG_FILE")
    local lines_after_monitor=$(( total_lines - monitor_line ))
    if [ "$lines_after_monitor" -gt 2 ]; then
        echo "STATUS: IN_PROGRESS"
        return 2
    fi

    # Cycle is complete. Extract the latest cycle text.
    local prev_monitor_line
    prev_monitor_line=$(grep -n "Monitoring source files" "$LOG_FILE" | tail -2 | head -1 | cut -d: -f1)

    local cycle_start
    if [ "$prev_monitor_line" = "$monitor_line" ]; then
        cycle_start=1
    else
        cycle_start=$((prev_monitor_line + 1))
    fi

    local cycle_text
    cycle_text=$(sed -n "${cycle_start},${monitor_line}p" "$LOG_FILE")

    local has_target_marker
    has_target_marker=$(echo "$cycle_text" | grep -c "\[FIRECALC_COMPILE_DONE\] module=${EXPECTED_MODULE}$" || true)

    local has_success
    has_success=$(echo "$cycle_text" | grep -c "^\[success\] Total time:" || true)

    local has_errors
    has_errors=$(echo "$cycle_text" | grep -c "^\[error\]" || true)

    # SUCCESS: target marker + success + no errors
    if [ "$has_target_marker" -gt 0 ] && [ "$has_success" -gt 0 ] && [ "$has_errors" -eq 0 ]; then
        echo "STATUS: SUCCESS"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "$cycle_text" | grep "^\[success\] Total time:" | tail -1
            local warnings
            warnings=$(echo "$cycle_text" | grep "^\[warn\]" || true)
            if [ -n "$warnings" ]; then
                echo "---WARNINGS---"
                echo "$warnings"
            fi
        fi
        return 0
    fi

    # ERROR
    if [ "$has_errors" -gt 0 ]; then
        echo "STATUS: ERROR"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "---ERRORS---"
        fi
        echo "$cycle_text" | grep "^\[error\]"
        if [ "$ERRORS_ONLY" = false ]; then
            local warnings
            warnings=$(echo "$cycle_text" | grep "^\[warn\]" || true)
            if [ -n "$warnings" ]; then
                echo "---WARNINGS---"
                echo "$warnings"
            fi
        fi
        return 1
    fi

    # SUCCESS without recompilation
    if [ "$has_success" -gt 0 ]; then
        echo "STATUS: SUCCESS"
        if [ "$ERRORS_ONLY" = false ]; then
            echo "$cycle_text" | grep "^\[success\] Total time:" | tail -1
            echo "(target '${EXPECTED_MODULE}' was not recompiled in this cycle)"
        fi
        return 0
    fi

    echo "STATUS: IN_PROGRESS"
    return 2
}

if [ "$WAIT_MODE" = true ]; then
    MAX_WAIT_S=360
    ELAPSED=0
    echo -n "Polling every 2s "

    while true; do
        OUTPUT=$(check_status 2>&1)
        STATUS=$?

        # Done: success, error, or not running
        if [ $STATUS -eq 0 ] || [ $STATUS -eq 1 ] || [ $STATUS -eq 3 ]; then
            echo ""
            echo "$OUTPUT"
            exit $STATUS
        fi

        # Timeout
        if [ $ELAPSED -ge $MAX_WAIT_S ]; then
            echo ""
            echo "STATUS: TIMEOUT"
            echo "Compilation did not complete within ${MAX_WAIT_S}s"
            exit 4
        fi

        echo -n "."
        sleep 2
        ELAPSED=$((ELAPSED + 2))
    done
else
    check_status
    exit $?
fi
