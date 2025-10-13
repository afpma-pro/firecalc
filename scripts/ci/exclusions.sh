#!/bin/bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Centralized exclusion patterns for license header checking
# Single source of truth for all license checking tools

# Directories to exclude (from .vscode/settings.json psi-header.changes-tracking.excludeGlob)
EXCLUDE_PATHS=(
    "**/node_modules/**"
    "**/target/**"
    "**/project/target/**"
    "**/project/project/**"
    "**/dist/**"
    "**/build/**"
    "**/out/**"
    "**/.git/**"
    "**/moleculeGen/**"
    "**/databases/**"
    "**/public/assets/**"
    "web/electron-app/resources/fontconfig/**"
)

# File patterns to exclude
EXCLUDE_PATTERNS=(
    "*.min.*"
    "*.lock"
    "yarn.lock"
    "pnpm-lock.yaml"
    "package-lock.json"
    "*.svg"
    "*.png"
    "*.jpg"
    "*.jpeg"
    "*.gif"
    "*.ico"
    "*.pdf"
    "modules/reports/report-*.typ"
    "modules/reports/report-*.pdf"
)

# Specific files to exclude
EXCLUDE_FILES=(
    ".vscode/settings.json"
    ".editorconfig"
    ".gitignore"
    ".gitattributes"
)

# File extensions excluded from header requirements (from .vscode/settings.json psi-header.changes-tracking.exclude)
EXCLUDED_EXTENSIONS=(
    "json"
    "jsonc"
    "yaml"
    "yml"
    "md"
    "html"
    "xml"
)

# Function to check if a file should be excluded
should_exclude() {
    local file="$1"
    
    # Check excluded paths
    for pattern in "${EXCLUDE_PATHS[@]}"; do
        # Convert glob pattern to regex-like check
        pattern_regex="${pattern//\*\*/.*}"
        pattern_regex="${pattern_regex//\*/[^/]*}"
        if [[ "$file" =~ $pattern_regex ]]; then
            return 0
        fi
    done
    
    # Check excluded file patterns
    for pattern in "${EXCLUDE_PATTERNS[@]}"; do
        if [[ "$file" == $pattern ]]; then
            return 0
        fi
    done
    
    # Check excluded specific files
    for excluded_file in "${EXCLUDE_FILES[@]}"; do
        if [[ "$file" == "$excluded_file" ]]; then
            return 0
        fi
    done
    
    # Check excluded extensions
    local extension="${file##*.}"
    for ext in "${EXCLUDED_EXTENSIONS[@]}"; do
        if [[ "$extension" == "$ext" ]]; then
            return 0
        fi
    done
    
    return 1
}

# Export function for use in other scripts
export -f should_exclude