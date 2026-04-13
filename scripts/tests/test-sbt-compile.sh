#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Smoke tests for sbt-compile-watch.sh, sbt-compile-check.sh, and
# sbt-compile-stop.sh.
#
# Each test creates a temp directory with a mock `sbt` on PATH, runs
# the real scripts, and asserts expected STATUS output and exit codes.
#
# Usage:
#   ./scripts/tests/test-sbt-compile.sh
#
# Exit code: 0 if all tests pass, 1 if any fail.

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0
ORIGINAL_PATH="$PATH"

# ─── Test infrastructure ──────────────────────────────────────────

setup_test() {
    TEST_DIR=$(mktemp -d)
    mkdir -p "$TEST_DIR/.logs"
    mkdir -p "$TEST_DIR/mock-bin"
    # Prepend mock-bin to PATH so our mock sbt is found first
    export PATH="$TEST_DIR/mock-bin:$ORIGINAL_PATH"
}

teardown_test() {
    export PATH="$ORIGINAL_PATH"
    rm -rf "$TEST_DIR"
}

assert_status() {
    local output="$1"
    local expected="$2"
    local test_name="$3"
    if echo "$output" | grep -q "STATUS: $expected"; then
        return 0
    else
        echo "  ASSERTION FAILED: expected STATUS: $expected"
        echo "  Got: $output"
        return 1
    fi
}

assert_exit_code() {
    local actual="$1"
    local expected="$2"
    local test_name="$3"
    if [ "$actual" -eq "$expected" ]; then
        return 0
    else
        echo "  ASSERTION FAILED: expected exit code $expected, got $actual"
        return 1
    fi
}

collect_descendants() {
    local p=$1
    local kids
    kids=$(pgrep -P "$p" 2>/dev/null || true)
    for k in $kids; do
        echo "$k"
        collect_descendants "$k"
    done
}

run_test() {
    local test_name="$1"
    local test_fn="$2"
    echo -n "Test: $test_name ... "
    setup_test
    local result=0
    $test_fn || result=1
    teardown_test
    if [ "$result" -eq 0 ]; then
        echo "PASS"
        PASS=$((PASS + 1))
    else
        echo "FAIL"
        FAIL=$((FAIL + 1))
    fi
}

# ─── Test 1: Standalone successful compile ─────────────────────────

test_standalone_success() {
    # Mock sbt: writes success output, exits 0
    cat > "$TEST_DIR/mock-bin/sbt" << 'MOCK'
#!/bin/bash
echo "[info] compiling 3 Scala sources..."
echo "[success] Total time: 5s, completed Apr 09, 2026"
exit 0
MOCK
    chmod +x "$TEST_DIR/mock-bin/sbt"

    # Run watch in standalone mode
    cd "$TEST_DIR"
    "$SCRIPT_DIR/sbt-compile-watch.sh" > /dev/null 2>&1

    # Wait for background process to finish (mock sbt is instant)
    sleep 2

    # Check status
    local output
    output=$("$SCRIPT_DIR/sbt-compile-check.sh" 2>&1)
    local exit_code=$?

    assert_status "$output" "SUCCESS" "standalone_success" || return 1
    assert_exit_code "$exit_code" 0 "standalone_success" || return 1
}

# ─── Test 2: Standalone launcher failure (sbt not on PATH) ─────────

test_standalone_launcher_failure() {
    # No mock sbt — remove mock-bin from PATH entirely
    rm -rf "$TEST_DIR/mock-bin"
    export PATH="$TEST_DIR/empty-bin:$ORIGINAL_PATH"
    mkdir -p "$TEST_DIR/empty-bin"

    # Ensure no sbt exists anywhere we'll find it
    # (We rely on the original PATH not having a working sbt in CI/test)
    # Create a mock that immediately fails (simulates missing launcher)
    cat > "$TEST_DIR/empty-bin/sbt" << 'MOCK'
#!/bin/bash
echo "Error: sbt launcher not found" >&2
exit 127
MOCK
    chmod +x "$TEST_DIR/empty-bin/sbt"

    cd "$TEST_DIR"
    "$SCRIPT_DIR/sbt-compile-watch.sh" > /dev/null 2>&1

    sleep 2

    local output
    output=$("$SCRIPT_DIR/sbt-compile-check.sh" 2>&1)
    local exit_code=$?

    assert_status "$output" "ERROR" "launcher_failure" || return 1
    assert_exit_code "$exit_code" 1 "launcher_failure" || return 1
}

# ─── Test 3: Standalone compilation errors ─────────────────────────

test_standalone_compile_errors() {
    cat > "$TEST_DIR/mock-bin/sbt" << 'MOCK'
#!/bin/bash
echo "[info] compiling 3 Scala sources..."
echo "[error] /src/Main.scala:10:5: not found: value foo"
echo "[error]     foo.bar()"
echo "[error]     ^"
echo "[error] one error found"
exit 1
MOCK
    chmod +x "$TEST_DIR/mock-bin/sbt"

    cd "$TEST_DIR"
    "$SCRIPT_DIR/sbt-compile-watch.sh" > /dev/null 2>&1

    sleep 2

    local output
    output=$("$SCRIPT_DIR/sbt-compile-check.sh" 2>&1)
    local exit_code=$?

    assert_status "$output" "ERROR" "compile_errors" || return 1
    assert_exit_code "$exit_code" 1 "compile_errors" || return 1
    # Verify error lines are shown
    if ! echo "$output" | grep -q "not found: value foo"; then
        echo "  ASSERTION FAILED: expected error details in output"
        return 1
    fi
}

# ─── Test 4: Client mode — no sbt server ──────────────────────────

test_client_no_server() {
    # Mock sbt --client: prints server-not-detected, exits 1
    cat > "$TEST_DIR/mock-bin/sbt" << 'MOCK'
#!/bin/bash
if [ "$1" = "--client" ]; then
    echo "[error] server was not detected"
    exit 1
fi
exit 1
MOCK
    chmod +x "$TEST_DIR/mock-bin/sbt"

    cd "$TEST_DIR"
    "$SCRIPT_DIR/sbt-compile-watch.sh" --client > /dev/null 2>&1

    sleep 2

    local output
    output=$("$SCRIPT_DIR/sbt-compile-check.sh" 2>&1)
    local exit_code=$?

    assert_status "$output" "NOT_RUNNING" "client_no_server" || return 1
    assert_exit_code "$exit_code" 3 "client_no_server" || return 1
}

# ─── Test 5: Stop + clean removes all metadata ────────────────────

test_stop_clean() {
    # Mock sbt: long-running process (simulates server or watch)
    cat > "$TEST_DIR/mock-bin/sbt" << 'MOCK'
#!/bin/bash
echo "[info] waiting..."
sleep 300
MOCK
    chmod +x "$TEST_DIR/mock-bin/sbt"

    cd "$TEST_DIR"
    "$SCRIPT_DIR/sbt-compile-watch.sh" > /dev/null 2>&1

    sleep 1

    # Verify process is running
    if [ ! -f "$TEST_DIR/.logs/sbt-compile.pid" ]; then
        echo "  ASSERTION FAILED: PID file not created"
        return 1
    fi

    local pid
    pid=$(cat "$TEST_DIR/.logs/sbt-compile.pid")
    if ! kill -0 "$pid" 2>/dev/null; then
        echo "  ASSERTION FAILED: compile process not running"
        return 1
    fi

    # Capture all descendant PIDs before stopping
    local descendants
    descendants=$(collect_descendants "$pid")

    # Create extra files that --clean should remove
    touch "$TEST_DIR/.logs/sbt-server.log"

    # Stop with --clean
    "$SCRIPT_DIR/sbt-compile-stop.sh" --clean > /dev/null 2>&1

    sleep 1

    # Verify wrapper process is stopped
    if kill -0 "$pid" 2>/dev/null; then
        echo "  ASSERTION FAILED: wrapper process still running after stop"
        kill "$pid" 2>/dev/null || true
        return 1
    fi

    # Verify all descendant processes are stopped — not reparented and leaked
    local leftover=0
    if [ -n "$descendants" ]; then
        for desc in $descendants; do
            if kill -0 "$desc" 2>/dev/null; then
                echo "  ASSERTION FAILED: descendant process $desc still running after stop (leaked)"
                kill "$desc" 2>/dev/null || true
                leftover=1
            fi
        done
        [ "$leftover" -eq 0 ] || return 1
    fi

    # Verify metadata files are cleaned up
    leftover=0
    for f in sbt-compile.pid sbt-compile.scope sbt-compile.mode sbt-compile.log sbt-compile.exitcode sbt-server.log; do
        if [ -f "$TEST_DIR/.logs/$f" ]; then
            echo "  ASSERTION FAILED: $f not cleaned up"
            leftover=1
        fi
    done
    [ "$leftover" -eq 0 ] || return 1
}

# ─── Test 6: Supervised startup + teardown kills entire tree ───────

test_supervised_teardown() {
    # Mock sbt that:
    # - In server mode: touches a ready flag, then sleeps (with a child)
    # - --client version: succeeds only if server ready flag exists
    # - --client ~compile: long-running watch (with a child)
    cat > "$TEST_DIR/mock-bin/sbt" << 'MOCK'
#!/bin/bash
if [ "$1" = "--client" ]; then
    if [ "$2" = "version" ]; then
        if [ -f ".logs/mock-server-ready" ]; then
            echo "[info] 1.0.0"
            exit 0
        else
            exit 1
        fi
    else
        echo "[info] watching..."
        sleep 300
    fi
else
    echo "[info] sbt server started"
    touch ".logs/mock-server-ready"
    sleep 300
fi
MOCK
    chmod +x "$TEST_DIR/mock-bin/sbt"

    cd "$TEST_DIR"
    "$SCRIPT_DIR/sbt-compile-watch.sh" --supervised > /dev/null 2>&1

    sleep 2

    # Verify both PID files exist
    if [ ! -f "$TEST_DIR/.logs/sbt-compile.pid" ]; then
        echo "  ASSERTION FAILED: compile PID file not created"
        return 1
    fi
    if [ ! -f "$TEST_DIR/.logs/sbt-server.pid" ]; then
        echo "  ASSERTION FAILED: server PID file not created"
        return 1
    fi

    local compile_pid server_pid
    compile_pid=$(cat "$TEST_DIR/.logs/sbt-compile.pid")
    server_pid=$(cat "$TEST_DIR/.logs/sbt-server.pid")

    # Collect all descendants of both trees before stopping
    local all_pids="$compile_pid $server_pid"
    local all_descendants
    all_descendants=$(
        for p in $all_pids; do
            collect_descendants "$p"
        done
    )

    # Stop with --clean (supervised mode auto-stops server)
    "$SCRIPT_DIR/sbt-compile-stop.sh" --clean > /dev/null 2>&1

    sleep 1

    # Verify both root processes are stopped
    local leftover=0
    if kill -0 "$compile_pid" 2>/dev/null; then
        echo "  ASSERTION FAILED: compile process $compile_pid still running"
        kill "$compile_pid" 2>/dev/null || true
        leftover=1
    fi
    if kill -0 "$server_pid" 2>/dev/null; then
        echo "  ASSERTION FAILED: server process $server_pid still running"
        kill "$server_pid" 2>/dev/null || true
        leftover=1
    fi

    # Verify all descendants are stopped
    if [ -n "$all_descendants" ]; then
        for desc in $all_descendants; do
            if kill -0 "$desc" 2>/dev/null; then
                echo "  ASSERTION FAILED: descendant process $desc still running (leaked)"
                kill "$desc" 2>/dev/null || true
                leftover=1
            fi
        done
    fi
    [ "$leftover" -eq 0 ] || return 1

    # Verify metadata files are cleaned up
    for f in sbt-compile.pid sbt-compile.scope sbt-compile.mode sbt-compile.log sbt-compile.exitcode sbt-server.pid sbt-server.log; do
        if [ -f "$TEST_DIR/.logs/$f" ]; then
            echo "  ASSERTION FAILED: $f not cleaned up"
            leftover=1
        fi
    done
    [ "$leftover" -eq 0 ] || return 1
}

# ─── Run all tests ────────────────────────────────────────────────

echo "=== sbt-compile smoke tests ==="
echo ""

run_test "Standalone: successful compile"   test_standalone_success
run_test "Standalone: launcher failure"      test_standalone_launcher_failure
run_test "Standalone: compilation errors"    test_standalone_compile_errors
run_test "Client: no sbt server"            test_client_no_server
run_test "Stop + clean removes metadata"    test_stop_clean
run_test "Supervised: teardown kills tree"  test_supervised_teardown

echo ""
echo "─────────────────────────────────"
echo "Results: $PASS passed, $FAIL failed"
echo "─────────────────────────────────"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
