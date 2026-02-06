#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Iterative scalafix: apply scalafix file-by-file on a module,
# verify compilation periodically, and git-commit every N files.
#
# Architecture:
#   - scalafix runs via sbt (which compiles internally)
#   - sbt-compile-watch is used ONLY for verification checkpoints
#     and interactive debugging (not simultaneously with scalafix)
#   - This avoids double-compilation
#
# Usage:
#   ./scripts/scalafix-iterative.sh [module] [commit_every] [--skip-n-first N] [--batch-size P]
#
# Example:
#   ./scripts/scalafix-iterative.sh engine 10
#   ./scripts/scalafix-iterative.sh engine 10 --skip-n-first 42
#   ./scripts/scalafix-iterative.sh engine 10 --batch-size 5

set -euo pipefail

MODULE="${1:-engine}"
COMMIT_EVERY="${2:-10}"
SKIP_N=0
BATCH_SIZE=5

# Parse optional flags
shift 2 2>/dev/null || true
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-n-first)
            SKIP_N="${2:-0}"
            shift 2
            ;;
        --batch-size)
            BATCH_SIZE="${2:-5}"
            shift 2
            ;;
        *) shift ;;
    esac
done
LOG_DIR=".logs"
SCALAFIX_LOG="$LOG_DIR/scalafix-iterative.log"

mkdir -p "$LOG_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${BLUE}[scalafix]${NC} $1"; }
log_ok() { echo -e "${GREEN}[scalafix]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[scalafix]${NC} $1"; }
log_err() { echo -e "${RED}[scalafix]${NC} $1"; }

# Ensure sbt-compile-watch is NOT running (would conflict with scalafix's sbt)
if [ -f "$LOG_DIR/sbt-compile.pid" ]; then
    OLD_PID=$(cat "$LOG_DIR/sbt-compile.pid")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        log_warn "Stopping existing sbt-compile-watch (PID: $OLD_PID) to avoid conflicts..."
        ./scripts/sbt-compile-stop.sh
        sleep 2
    fi
fi

# Collect all Scala source files for the module
SRC_DIR="modules/$MODULE/src/main/scala"
if [ ! -d "$SRC_DIR" ]; then
    log_err "Source directory not found: $SRC_DIR"
    exit 1
fi

mapfile -t FILES < <(find "$SRC_DIR" -type f -name '*.scala' | sort)
TOTAL=${#FILES[@]}
log "Found $TOTAL Scala files in $SRC_DIR"

# Start sbt-compile-watch and wait for compilation result
# Returns 0 on success, 1 on failure
verify_with_watch() {
    log "Starting sbt-compile-watch for verification..."
    # sbt-compile-watch.sh clears the log file on start, but we need to
    # wait until sbt has actually started writing to the log before checking.
    ./scripts/sbt-compile-watch.sh "$MODULE"

    # Wait for sbt to actually start (log file gets content)
    local wait_count=0
    local max_start_wait=30  # max 30 seconds to start
    while [ $wait_count -lt $max_start_wait ]; do
        sleep 1
        wait_count=$((wait_count + 1))
        # Check if sbt has started writing compilation output
        if [ -f "$LOG_DIR/sbt-compile.log" ] && grep -q "\[info\]\|compiling\|\[error\]\|\[success\]" "$LOG_DIR/sbt-compile.log" 2>/dev/null; then
            log "sbt started compiling (after ${wait_count}s)"
            break
        fi
    done

    if [ $wait_count -ge $max_start_wait ]; then
        log_warn "sbt took too long to start, checking anyway..."
    fi

    ./scripts/sbt-compile-check.sh --wait
    local status=$?
    return $status
}

# Stop sbt-compile-watch
stop_watch() {
    ./scripts/sbt-compile-stop.sh 2>/dev/null || true
    sleep 1
}

# Interactive error handling with sbt-compile-watch for debugging
# The watch is already running when entering this function
handle_failure() {
    local file="$1"
    local n="$2"

    log_err "Errors from sbt:"
    ./scripts/sbt-compile-check.sh --errors 2>/dev/null || true

    while true; do
        echo ""
        log_warn "sbt-compile-watch is running for interactive debugging."
        log_warn "Options:"
        log_warn "  [f] I've fixed it — re-check compilation"
        log_warn "  [r] Revert this file and move on"
        log_warn "  [q] Quit the script"
        read -rp "Choice [f/r/q]: " choice
        case "$choice" in
            f|F)
                log "Re-checking compilation..."
                sleep 2
                if ./scripts/sbt-compile-check.sh --wait; then
                    log_ok "[$n/$TOTAL] Compilation OK after manual fix"
                    stop_watch
                    return 0  # success
                else
                    log_err "Still failing. Try again or revert."
                    ./scripts/sbt-compile-check.sh --errors 2>/dev/null || true
                fi
                ;;
            r|R)
                log_warn "Reverting $file..."
                git checkout -- "$file"
                sleep 2
                ./scripts/sbt-compile-check.sh --wait || true
                stop_watch
                return 1  # reverted
                ;;
            q|Q)
                stop_watch
                return 2  # quit
                ;;
            *)
                log_warn "Invalid choice. Enter f, r, or q."
                ;;
        esac
    done
}

# Counters
success_count=0
skip_count=0
fail_count=0
since_last_commit=0

# Track files changed since last commit for git add
declare -a changed_files=()

echo "" > "$SCALAFIX_LOG"

# Use index-based iteration to support batch advancement
idx=0
while [ "$idx" -lt "$TOTAL" ]; do
    # Skip first N files if requested
    file_n=$((idx + 1))
    if [ "$file_n" -le "$SKIP_N" ]; then
        idx=$((idx + 1))
        continue
    fi

    # Collect a batch of up to BATCH_SIZE files
    batch_files=()
    batch_start=$file_n
    for ((j = 0; j < BATCH_SIZE && (idx + j) < TOTAL; j++)); do
        candidate_n=$((idx + j + 1))
        if [ "$candidate_n" -le "$SKIP_N" ]; then
            continue
        fi
        batch_files+=("${FILES[$((idx + j))]}")
    done
    batch_end=$((idx + ${#batch_files[@]}))
    batch_display="${batch_start}-$((batch_end))"

    # Advance index past this batch
    idx=$batch_end

    if [ ${#batch_files[@]} -eq 0 ]; then
        continue
    fi

    # Build --files arguments (one --files= per file for scalafix)
    files_arg=""
    for bf in "${batch_files[@]}"; do
        files_arg="${files_arg} --files=$bf"
    done

    log "[$batch_display/$TOTAL] Processing batch of ${#batch_files[@]} files"

    # Get hashes before scalafix
    declare -A hashes_before=()
    for bf in "${batch_files[@]}"; do
        hashes_before["$bf"]=$(md5sum "$bf" | cut -d' ' -f1)
    done

    # Run scalafix on batch
    log "Running: sbt --batch \"${MODULE}/scalafix${files_arg}\""
    sbt_ok=true
    if ! sbt --batch "${MODULE}/scalafix${files_arg}" >> "$SCALAFIX_LOG" 2>&1; then
        sbt_ok=false
    fi

    # Check which files were modified
    declare -a modified_files=()
    for bf in "${batch_files[@]}"; do
        hash_after=$(md5sum "$bf" | cut -d' ' -f1)
        if [ "${hashes_before[$bf]}" != "$hash_after" ]; then
            modified_files+=("$bf")
        fi
    done

    if [ "$sbt_ok" = false ]; then
        log_err "[$batch_display/$TOTAL] scalafix reported issues"
        log_err "Last scalafix output:"
        tail -30 "$SCALAFIX_LOG" | grep -E "^\[error\]|error:|warning:" || true
        echo ""

        if [ ${#modified_files[@]} -gt 0 ]; then
            log_warn "Modified files:"
            for mf in "${modified_files[@]}"; do
                log_warn "  $mf"
            done
        else
            log_warn "No files were modified."
        fi

        # Always ask the user what to do
        while true; do
            echo ""
            log_warn "Options:"
            log_warn "  [f] I've fixed it — retry scalafix on this batch"
            log_warn "  [r] Revert all modified files and skip this batch"
            log_warn "  [s] Skip — keep modifications as-is"
            log_warn "  [q] Quit the script"
            read -rp "Choice [f/r/s/q]: " choice
            case "$choice" in
                f|F)
                    log "Retrying scalafix on batch..."
                    # Re-hash
                    for bf in "${batch_files[@]}"; do
                        hashes_before["$bf"]=$(md5sum "$bf" | cut -d' ' -f1)
                    done
                    if sbt --batch "${MODULE}/scalafix${files_arg}" >> "$SCALAFIX_LOG" 2>&1; then
                        # Count modified files
                        modified_files=()
                        for bf in "${batch_files[@]}"; do
                            hash_after=$(md5sum "$bf" | cut -d' ' -f1)
                            if [ "${hashes_before[$bf]}" != "$hash_after" ]; then
                                modified_files+=("$bf")
                            fi
                        done
                        log_ok "[$batch_display/$TOTAL] scalafix succeeded after fix"
                        for mf in "${modified_files[@]}"; do
                            success_count=$((success_count + 1))
                            since_last_commit=$((since_last_commit + 1))
                            changed_files+=("$mf")
                            echo "FIXED $mf" >> "$SCALAFIX_LOG"
                        done
                        break
                    else
                        log_err "Still failing. Try again, revert, or skip."
                        tail -20 "$SCALAFIX_LOG" | grep -E "^\[error\]|error:|warning:" || true
                    fi
                    ;;
                r|R)
                    for mf in "${modified_files[@]}"; do
                        log_warn "Reverting $mf..."
                        git checkout -- "$mf"
                        echo "REVERTED $mf" >> "$SCALAFIX_LOG"
                    done
                    fail_count=$((fail_count + ${#batch_files[@]}))
                    break
                    ;;
                s|S)
                    for mf in "${modified_files[@]}"; do
                        success_count=$((success_count + 1))
                        since_last_commit=$((since_last_commit + 1))
                        changed_files+=("$mf")
                        echo "KEPT_AS_IS $mf" >> "$SCALAFIX_LOG"
                    done
                    skip_count=$((skip_count + ${#batch_files[@]} - ${#modified_files[@]}))
                    break
                    ;;
                q|Q)
                    log_warn "Quitting. Committing any pending changes first..."
                    if [ "$since_last_commit" -gt 0 ]; then
                        git add "${changed_files[@]}"
                        git commit -m "refactor($MODULE): apply scalafix to ${since_last_commit} files (partial)"
                        log_ok "Committed ${since_last_commit} files before exit"
                    fi
                    exit 0
                    ;;
                *)
                    log_warn "Invalid choice. Enter f, r, s, or q."
                    ;;
            esac
        done
        continue
    fi

    # scalafix succeeded
    if [ ${#modified_files[@]} -eq 0 ]; then
        log "[$batch_display/$TOTAL] No changes in batch"
        continue
    fi

    log_ok "[$batch_display/$TOTAL] scalafix applied to ${#modified_files[@]} files"
    for mf in "${modified_files[@]}"; do
        success_count=$((success_count + 1))
        since_last_commit=$((since_last_commit + 1))
        changed_files+=("$mf")
        echo "OK $mf" >> "$SCALAFIX_LOG"
    done

    # Commit every N successful files (with full verification via watch)
    if [ "$since_last_commit" -ge "$COMMIT_EVERY" ]; then
        log "Verification checkpoint: verifying full compilation before commit..."
        if verify_with_watch; then
            log_ok "Full compilation OK"
            stop_watch
            git add "${changed_files[@]}"
            git commit -m "refactor($MODULE): apply scalafix to ${since_last_commit} files (batch)"
            log_ok "Committed ${since_last_commit} files"
            since_last_commit=0
            changed_files=()
        else
            log_err "Full compilation failed at checkpoint!"
            handle_failure "<batch of $since_last_commit files>" "$batch_display"
            handle_result=$?
            if [ $handle_result -eq 0 ]; then
                git add "${changed_files[@]}"
                git commit -m "refactor($MODULE): apply scalafix to ${since_last_commit} files (batch, fixed)"
                log_ok "Committed ${since_last_commit} files after fix"
                since_last_commit=0
                changed_files=()
            elif [ $handle_result -eq 2 ]; then
                exit 0
            fi
        fi
    fi
done

# Final commit for remaining files
if [ "$since_last_commit" -gt 0 ]; then
    log "Final verification before last commit..."
    if verify_with_watch; then
        stop_watch
        git add "${changed_files[@]}"
        git commit -m "refactor($MODULE): apply scalafix to ${since_last_commit} files (final batch)"
        log_ok "Committed remaining ${since_last_commit} files"
    else
        log_err "Final compilation failed!"
        handle_failure "<final batch>" "$TOTAL"
        handle_result=$?
        if [ $handle_result -eq 0 ]; then
            git add "${changed_files[@]}"
            git commit -m "refactor($MODULE): apply scalafix to ${since_last_commit} files (final batch, fixed)"
            log_ok "Committed remaining ${since_last_commit} files after fix"
        fi
    fi
fi

echo ""
log "========== SUMMARY =========="
log "Total files:    $TOTAL"
log_ok "Modified+OK:    $success_count"
log_warn "Skipped:        $skip_count"
log_err "Failed+reverted: $fail_count"
log "Unchanged:      $((TOTAL - success_count - skip_count - fail_count))"
log "Log: $SCALAFIX_LOG"
