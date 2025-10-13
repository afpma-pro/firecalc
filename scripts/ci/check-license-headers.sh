#!/bin/bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# License header checking script for CI
# Supports both "changed files" and "full repository" modes

set -e

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source exclusion patterns
source "$SCRIPT_DIR/exclusions.sh"

# Configuration
REQUIRED_SPDX="SPDX-License-Identifier: AGPL-3.0-or-later"
REQUIRED_COPYRIGHT="Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal"

# Parse arguments
MODE="changed"
BASE_REF="origin/main"
GITHUB_OUTPUT="${GITHUB_OUTPUT:-}"
GITHUB_STEP_SUMMARY="${GITHUB_STEP_SUMMARY:-}"

while [[ $# -gt 0 ]]; do
    case $1 in
        --changed)
            MODE="changed"
            shift
            ;;
        --all)
            MODE="all"
            shift
            ;;
        --base-ref)
            BASE_REF="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [--changed|--all] [--base-ref REF]"
            echo ""
            echo "Options:"
            echo "  --changed       Check only changed files (default)"
            echo "  --all           Check all files in repository"
            echo "  --base-ref REF  Base reference for diff (default: origin/main)"
            echo ""
            echo "Examples:"
            echo "  $0 --changed --base-ref HEAD~1"
            echo "  $0 --all"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 2
            ;;
    esac
done

# Header check functions (extracted from pre-commit hook)
check_c_style_header() {
    local file="$1"
    if ! head -n 5 "$file" | grep -q "$REQUIRED_SPDX"; then
        return 1
    fi
    return 0
}

check_hash_header() {
    local file="$1"
    if ! head -n 5 "$file" | grep -q "$REQUIRED_SPDX"; then
        return 1
    fi
    return 0
}

check_sql_header() {
    local file="$1"
    if ! head -n 5 "$file" | grep -q "$REQUIRED_SPDX"; then
        return 1
    fi
    return 0
}

check_html_header() {
    local file="$1"
    if ! head -n 5 "$file" | grep -q "$REQUIRED_SPDX"; then
        return 1
    fi
    return 0
}

check_double_slash_header() {
    local file="$1"
    if ! head -n 5 "$file" | grep -q "$REQUIRED_SPDX"; then
        return 1
    fi
    return 0
}

# Get file list based on mode
get_file_list() {
    if [ "$MODE" == "changed" ]; then
        # Get changed files
        git diff --name-only --diff-filter=ACM "$BASE_REF" HEAD 2>/dev/null || \
        git diff --name-only --diff-filter=ACM HEAD 2>/dev/null || \
        echo ""
    else
        # Get all tracked files
        git ls-files
    fi
}

# Check file header based on extension
check_file_header() {
    local file="$1"
    local extension="${file##*.}"
    
    case "$extension" in
        # Excluded extensions (from psi-header.changes-tracking.exclude)
        json|jsonc|yaml|yml|md|html|xml)
            return 0  # Skip these file types
            ;;
            
        # C-style comments: Scala, JavaScript, TypeScript, SBT, CSS
        scala|js|ts|jsx|tsx|sbt|css)
            check_c_style_header "$file"
            return $?
            ;;
            
        # Hash comments: Shell scripts, HOCON, Properties
        sh|conf|hocon|properties|mk)
            check_hash_header "$file"
            return $?
            ;;
            
        # SQL comments
        sql)
            check_sql_header "$file"
            return $?
            ;;
            
        # Double-slash comments: Typst
        typ)
            check_double_slash_header "$file"
            return $?
            ;;
            
        # Special case: Makefile (no extension)
        *)
            if [[ "$file" == *"Makefile"* ]] || [[ "$file" == *"makefile"* ]]; then
                check_hash_header "$file"
                return $?
            fi
            # Unknown extension - skip
            return 0
            ;;
    esac
}

# Get header format for file type
get_header_format() {
    local file="$1"
    local extension="${file##*.}"
    
    case "$extension" in
        scala|js|ts|jsx|tsx|sbt|css)
            echo "C-style /* */"
            ;;
        sh|conf|hocon|properties|mk)
            echo "Hash #"
            ;;
        sql)
            echo "SQL --"
            ;;
        typ)
            echo "Double-slash //"
            ;;
        *)
            if [[ "$file" == *"Makefile"* ]]; then
                echo "Hash #"
            else
                echo "Unknown"
            fi
            ;;
    esac
}

# GitHub Actions annotation
github_error() {
    local file="$1"
    local message="$2"
    
    if [ -n "$GITHUB_OUTPUT" ]; then
        echo "::error file=${file},line=1,title=Missing License Header::${message}"
    fi
}

# Arrays to track results
declare -a MISSING_HEADERS=()
declare -a CHECKED_FILES=()

# Main checking loop
echo "Running license header check in '$MODE' mode..."
if [ "$MODE" == "changed" ]; then
    echo "Base reference: $BASE_REF"
fi
echo ""

FILE_LIST=$(get_file_list)

if [ -z "$FILE_LIST" ]; then
    echo "⚠ No files to check"
    exit 0
fi

while IFS= read -r file; do
    # Skip if file doesn't exist
    [ ! -f "$file" ] && continue
    
    # Skip excluded files
    if should_exclude "$file"; then
        continue
    fi
    
    CHECKED_FILES+=("$file")
    
    if ! check_file_header "$file"; then
        MISSING_HEADERS+=("$file")
        
        # Add GitHub Actions annotation
        header_format=$(get_header_format "$file")
        github_error "$file" "Missing SPDX-License-Identifier: AGPL-3.0-or-later (expected format: $header_format)"
    fi
done <<< "$FILE_LIST"

# Generate results
VIOLATIONS=${#MISSING_HEADERS[@]}
CHECKED=${#CHECKED_FILES[@]}

# Create summary
if [ $VIOLATIONS -gt 0 ]; then
    echo "❌ License header check FAILED"
    echo ""
    echo "Missing license headers in $VIOLATIONS file(s):"
    printf '  - %s\n' "${MISSING_HEADERS[@]}"
    echo ""
    echo "Files checked: $CHECKED"
    
    # Generate GitHub Actions job summary
    if [ -n "$GITHUB_STEP_SUMMARY" ]; then
        cat >> "$GITHUB_STEP_SUMMARY" << EOF
## ❌ License Header Check Failed

Missing license headers in **$VIOLATIONS file(s)** out of $CHECKED checked:

| File | Required Format |
|------|-----------------|
EOF
        for file in "${MISSING_HEADERS[@]}"; do
            format=$(get_header_format "$file")
            echo "| \`$file\` | $format |" >> "$GITHUB_STEP_SUMMARY"
        done
        
        cat >> "$GITHUB_STEP_SUMMARY" << 'EOF'

### How to Fix

Add the appropriate header from [LICENSE-HEADER-TEMPLATE.txt](../LICENSE-HEADER-TEMPLATE.txt)

**For Scala/JS/TS/CSS files:**
```
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
```

**For Shell/Config files:**
```
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
```

**For SQL files:**
```
-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
```

**For Typst files:**
```
// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
```

### Next Steps

1. Add headers using the VSCode psi-header extension (auto-adds on save)
2. Or manually add headers using LICENSE-HEADER-TEMPLATE.txt
3. Commit and push again

See [docs/LICENSE.md](../docs/LICENSE.md) for details.
EOF
    fi
    
    exit 1
else
    echo "✅ All $CHECKED file(s) have valid license headers"
    
    # Generate success summary for GitHub Actions
    if [ -n "$GITHUB_STEP_SUMMARY" ]; then
        cat >> "$GITHUB_STEP_SUMMARY" << EOF
## ✅ License Header Check Passed

All **$CHECKED file(s)** have valid AGPL-3.0-or-later license headers.

**Mode:** $MODE
EOF
        if [ "$MODE" == "changed" ]; then
            echo "**Base:** $BASE_REF" >> "$GITHUB_STEP_SUMMARY"
        fi
    fi
    
    exit 0
fi