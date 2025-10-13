#!/bin/bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal


# Translation String Scanner
# Scans Scala files for hardcoded strings and classifies them for i18n translation

# Configuration
MODULES_DIR="modules"
OUTPUT_JSON="translation-inventory.json"
OUTPUT_MD="TRANSLATION_INVENTORY.md"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
SCANNER_VERSION="1.1.0"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
total_files=0
total_strings=0
technical_count=0
translatable_count=0

# Temporary file
TEMP_STRINGS=$(mktemp)

# Cleanup
cleanup() {
    rm -f "$TEMP_STRINGS"
}
trap cleanup EXIT

# Logging
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if technical
is_technical() {
    local str="$1"
    local ctx="$2"
    
    # Short
    [ ${#str} -lt 3 ] && echo "Too short" && return 0
    
    # CSS
    [[ "$ctx" =~ cls.*:= ]] && echo "CSS" && return 0
    [[ "$str" =~ ^(flex|bg|text|border|p-|m-|w-|h-) ]] && echo "CSS" && return 0
    
    # HTML/attrs
    [[ "$ctx" =~ (idAttr|dataAttr) ]] && echo "HTML attr" && return 0
    
    # Imports
    [[ "$ctx" =~ @JSImport ]] && echo "Import" && return 0
    [[ "$str" =~ \.(js|css|png|jpg|svg|json|ya?ml)$ ]] && echo "File ext" && return 0
    
    # URLs
    [[ "$str" =~ :// ]] && echo "URL" && return 0
    
    # Debug
    [[ "$ctx" =~ (console\.|println) ]] && echo "Debug" && return 0
    
    # i18n
    [[ "$ctx" =~ I18[Nn] ]] && echo "Already i18n" && return 0
    
    # Values
    [[ "$str" =~ ^(true|false|null|[0-9]+)$ ]] && echo "Value" && return 0
    
    # Show instances (debugging/logging output)
    [[ "$ctx" =~ (Show\[|given Show|derives Show|show_) ]] && echo "Show instance" && return 0
    [[ "$str" =~ ^\$ ]] && echo "Show template var" && return 0
    [[ "$str" =~ \$\{[^}]+\} ]] && echo "Show interpolation" && return 0
    
    # Decoders (internal error messages)
    [[ "$ctx" =~ (decoder|Decoder|decode) ]] && echo "Decoder" && return 0
    [[ "$str" =~ could\ not\ be\ decoded ]] && echo "Decoder error" && return 0
    
    # Technical documentation/comments (French formula sections)
    [[ "$str" =~ ^(Section|Tableau|Annexe) ]] && echo "Technical doc" && return 0
    [[ "$str" =~ (Température|Pression|Débit|Masse|Résistance|Coefficient|Capacité).*\( ]] && echo "Formula doc" && return 0
    [[ "$ctx" =~ ^[[:space:]]*//.* ]] && echo "Comment" && return 0
    
    # Shape symbols (geometric output)
    [[ "$str" =~ ^(◯|□|▭|▯|✓|~) ]] && echo "Shape symbol" && return 0
    
    # Technical identifiers
    [[ "$str" =~ ^[A-Z][a-z]+[A-Z] ]] && echo "CamelCase ID" && return 0
    [[ "$str" =~ ^[a-z_]+[A-Z] ]] && echo "snake_CamelCase" && return 0
    
    return 1
}

# Check if translatable  
is_translatable() {
    local str="$1"
    local ctx="$2"
    
    [ ${#str} -lt 3 ] && return 1
    [[ "$str" =~ [[:space:]] ]] && return 0
    [[ "$str" =~ [A-Z][a-z]+ ]] && return 0
    [[ "$ctx" =~ (button|label|span|div)\( ]] && return 0
    
    return 1
}

# Confidence
calc_confidence() {
    local cat="$1"
    local ctx="$2"
    
    if [ "$cat" = "technical" ]; then
        echo "high"
    else
        [[ "$ctx" =~ button ]] && echo "high" || echo "medium"
    fi
}

# Suggest key
suggest_key() {
    local str="$1"
    local ctx="$2"
    local cat="common"
    
    [[ "$ctx" =~ button ]] && cat="buttons"
    [[ "$ctx" =~ label ]] && cat="labels"
    [[ "$str" =~ (error|Error) ]] && cat="messages"
    
    local key=$(echo "$str" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9 ]//g' | tr ' ' '_' | cut -c1-40)
    echo "${cat}.${key}"
}

# Extract strings from file
extract_from_file() {
    local file="$1"
    local module=$(echo "$file" | cut -d'/' -f2)
    
    total_files=$((total_files + 1))
    
    # Extract strings with grep
    local matches=$(grep -on '"[^"]*"' "$file" 2>/dev/null || true)
    
    if [ -z "$matches" ]; then
        return 0
    fi
    
    echo "$matches" | while IFS=: read -r lnum match; do
        local str="${match:1:-1}"
        [ -z "$str" ] && continue
        
        total_strings=$((total_strings + 1))
        
        local ctx=$(sed -n "${lnum}p" "$file" 2>/dev/null || echo "")
        local cat="unknown"
        local reason=""
        
        if reason=$(is_technical "$str" "$ctx"); then
            cat="technical"
            technical_count=$((technical_count + 1))
        elif is_translatable "$str" "$ctx"; then
            cat="translatable"
            reason="User-facing"
            translatable_count=$((translatable_count + 1))
        else
            cat="technical"
            reason="No match"
            technical_count=$((technical_count + 1))
        fi
        
        local conf=$(calc_confidence "$cat" "$ctx")
        local key=""
        [ "$cat" = "translatable" ] && key=$(suggest_key "$str" "$ctx")
        
        echo "$module|$file|$lnum|$str|$cat|$conf|$key|$reason" >> "$TEMP_STRINGS"
    done
}

# Generate JSON
gen_json() {
    log_info "Generating JSON..."
    
    cat > "$OUTPUT_JSON" << EOF
{
  "scan_metadata": {
    "timestamp": "$TIMESTAMP",
    "version": "$SCANNER_VERSION",
    "files": $total_files,
    "strings": $total_strings,
    "technical": $technical_count,
    "translatable": $translatable_count
  },
  "strings": [
EOF
    
    local first=true
    while IFS='|' read -r mod file lnum str cat conf key reason; do
        str=$(echo "$str" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')
        key=$(echo "$key" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')
        
        [ "$first" = "false" ] && echo "    ," >> "$OUTPUT_JSON"
        first=false
        
        cat >> "$OUTPUT_JSON" << EOF
    {"module":"$mod","file":"$file","line":$lnum,"content":"$str","category":"$cat","confidence":"$conf","key":"$key","reason":"$reason"}
EOF
    done < "$TEMP_STRINGS"
    
    echo "  ]" >> "$OUTPUT_JSON"
    echo "}" >> "$OUTPUT_JSON"
    
    log_success "JSON: $OUTPUT_JSON"
}

# Generate MD
gen_md() {
    log_info "Generating Markdown..."
    
    cat > "$OUTPUT_MD" << EOF
# Translation Inventory

**Generated**: $TIMESTAMP  
**Files**: $total_files | **Strings**: $total_strings | **Translatable**: $translatable_count | **Technical**: $technical_count

## Translatable Strings

EOF
    
    local count=0
    local curr_mod=""
    
    grep "|translatable|" "$TEMP_STRINGS" | while IFS='|' read -r mod file lnum str cat conf key reason; do
        if [ "$mod" != "$curr_mod" ]; then
            curr_mod="$mod"
            echo -e "\n### Module: $mod\n" >> "$OUTPUT_MD"
        fi
        
        echo "- **$file:$lnum** (\`$conf\`): \`\"$str\"\` → \`$key\`" >> "$OUTPUT_MD"
        
        count=$((count + 1))
        [ $count -gt 50 ] && echo -e "\n*...and more*\n" >> "$OUTPUT_MD" && break
    done
    
    log_success "Markdown: $OUTPUT_MD"
}

# Main
main() {
    log_info "Starting scanner..."
    log_info "Directory: $MODULES_DIR"
    
    [ ! -d "$MODULES_DIR" ] && log_error "Directory not found" && exit 1
    
    # Scan files
    find "$MODULES_DIR" -type f -name "*.scala" 2>/dev/null | sort | while read -r f; do
        log_info "Scanning: $f"
        extract_from_file "$f"
    done
    
    # Count results
    total_files=$(find "$MODULES_DIR" -type f -name "*.scala" 2>/dev/null | wc -l)
    total_strings=$(wc -l < "$TEMP_STRINGS" 2>/dev/null || echo 0)
    translatable_count=$(grep -c "|translatable|" "$TEMP_STRINGS" 2>/dev/null || echo 0)
    technical_count=$(grep -c "|technical|" "$TEMP_STRINGS" 2>/dev/null || echo 0)
    
    log_info "Complete: $total_files files, $total_strings strings ($translatable_count translatable)"
    
    gen_json
    gen_md
    
    log_success "Done!"
}

main "$@"