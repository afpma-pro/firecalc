<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Translation Automation Specification

## Overview

This document specifies the automated translation discovery system for the i18n workflow in the FireCalc Scala.js application. The system helps identify hardcoded strings that should be translated and provides a structured approach to managing the translation process.

## Purpose

- **Automated Discovery**: Scan Scala source files to identify hardcoded string literals
- **Smart Classification**: Automatically distinguish between technical strings and translatable user-facing text
- **Structured Output**: Generate both machine-readable (JSON) and human-readable (Markdown) inventories
- **Translation Workflow**: Provide a foundation for semi-automated translation implementation

## Architecture

### Components

1. **String Scanner** (`scripts/scan-hardcoded-strings.sh`)
   - Bash script that scans Scala files
   - Extracts and classifies string literals
   - Generates JSON and Markdown outputs

2. **Classification Engine** (embedded in scanner)
   - Rule-based system to categorize strings
   - Technical vs. translatable determination
   - Confidence scoring

3. **Output Generators** (embedded in scanner)
   - JSON format for tooling integration
   - Markdown format for human review

4. **Translation Applier** (future implementation)
   - Tool to apply translations from inventory
   - Replace hardcoded strings with i18n keys
   - Update translation files

## String Scanner Specification

### Input
- Recursively scans all `.scala` files in `modules/` directory
- Reads file line-by-line to extract context

### Processing

#### String Extraction
Regex patterns to identify string literals:
- Double-quoted strings: `"([^"\\]*(\\.[^"\\]*)*)"`
- Exclude multi-line strings and special cases

#### Context Analysis
For each string, extract:
- File path (relative to project root)
- Line number
- Surrounding code context (5 lines before/after)
- Variable/function context when available

### Classification Rules

#### TECHNICAL (Skip Translation)

Strings are classified as TECHNICAL if they match any of these patterns:

1. **CSS/Styling**
   - `cls := "..."` - CSS class assignments
   - `className := "..."` - Class name assignments
   - Strings starting with common CSS prefixes: `flex-`, `bg-`, `text-`, `p-`, `m-`, etc.

2. **HTML Attributes**
   - `idAttr := "..."` - ID attributes
   - `dataAttr(...) := "..."` - Data attributes
   - Common HTML attribute patterns

3. **XML/SVG**
   - `xmlns` namespace declarations
   - `svg.*` SVG-related attributes
   - XML tag names and attributes

4. **Imports and Paths**
   - `@JSImport` annotations
   - File extensions: `.js`, `.css`, `.png`, `.jpg`, etc.
   - Relative/absolute file paths

5. **URLs and Protocols**
   - Contains `://` (http://, https://, etc.)
   - Domain patterns: `.com`, `.org`, `.net`, etc.
   - Query parameters: `?`, `&`, `=`

6. **Debug/Logging**
   - `console.log` statements
   - Debug markers
   - Stack trace patterns

7. **Technical Identifiers**
   - Version patterns: `v1.2.3`, `1.0.0`
   - Git hashes: 40-character hex strings
   - API endpoints with technical paths

8. **Already Internationalized**
   - Contains `I18N` or `I18n` references
   - Uses translation function calls

9. **Short Strings**
   - Length < 3 characters
   - Single characters (separators, punctuation)

10. **Configuration Values**
    - Boolean strings: `"true"`, `"false"`
    - Numeric strings that are identifiers
    - Enum-like technical values

#### TRANSLATABLE (Needs Translation)

Strings are classified as TRANSLATABLE if they:

1. **User-Facing Text Indicators**
   - Length >= 3 characters
   - Contains spaces (likely phrases)
   - Contains capitalized words (titles, labels)
   - Contains common UI words: "button", "label", "error", "warning", "success", etc.

2. **Content Patterns**
   - Sentences with punctuation
   - Question marks (user prompts)
   - Exclamation marks (alerts, notifications)
   - Colons followed by text (form labels)

3. **Common UI Elements**
   - Button text
   - Form labels
   - Error/success messages
   - Tooltips and help text
   - Navigation items
   - Dialog titles and messages

4. **Context Clues**
   - Near UI component declarations: `button`, `label`, `div`, `span`
   - In component props: `title=`, `label=`, `placeholder=`
   - In user-facing function calls

### Confidence Scoring

Each classification receives a confidence level:

- **HIGH** (90-100%): Clear indicators, unambiguous classification
- **MEDIUM** (70-89%): Most indicators present, minor ambiguity
- **LOW** (50-69%): Conflicting signals, manual review recommended

Confidence factors:
- Pattern match strength
- Context clarity
- String characteristics (length, content)
- Multiple matching rules vs. single rule

### Suggested Key Generation

For translatable strings, generate i18n keys following convention:

**Pattern**: `{category}.{subcategory}.{descriptive_name}`

**Categories**:
- `common` - Shared across modules
- `buttons` - Button text
- `labels` - Form labels and field names
- `messages` - User messages (errors, success, info)
- `titles` - Page and section titles
- `tooltips` - Help text and tooltips
- `validation` - Form validation messages
- `navigation` - Menu and navigation items

**Key Generation Rules**:
1. Convert to lowercase
2. Replace spaces with underscores
3. Remove special characters
4. Limit to 50 characters
5. Ensure uniqueness by adding context suffix if needed

**Examples**:
- `"Save Changes"` → `buttons.save_changes`
- `"Email Address"` → `labels.email_address`
- `"Invalid input"` → `messages.invalid_input`
- `"Settings"` → `navigation.settings`

## Output Format Specifications

### JSON Format: `translation-inventory.json`

```json
{
  "scan_metadata": {
    "timestamp": "2025-10-09T13:54:00Z",
    "scanner_version": "1.0.0",
    "total_files_scanned": 150,
    "total_strings_found": 342,
    "technical_count": 186,
    "translatable_count": 156,
    "scan_duration_ms": 1234
  },
  "modules": {
    "module_name": {
      "files": {
        "relative/path/to/file.scala": {
          "total_strings": 8,
          "translatable_count": 3,
          "technical_count": 5,
          "strings": [
            {
              "line": 61,
              "content": "Save Changes",
              "category": "translatable",
              "confidence": "high",
              "suggested_key": "buttons.save_changes",
              "context": {
                "before": "  val submitButton = button(",
                "after": "    onClick --> ..."
              },
              "classification_reasons": [
                "Contains spaces",
                "Capitalized words",
                "Near button component",
                "Common UI pattern"
              ]
            },
            {
              "line": 45,
              "content": "flex-col gap-4",
              "category": "technical",
              "confidence": "high",
              "classification_reasons": [
                "CSS class pattern",
                "In cls assignment"
              ]
            }
          ]
        }
      }
    }
  },
  "statistics": {
    "by_module": {
      "ui": {
        "total": 120,
        "translatable": 85,
        "technical": 35
      },
      "payments": {
        "total": 80,
        "translatable": 40,
        "technical": 40
      }
    },
    "by_confidence": {
      "high": 280,
      "medium": 50,
      "low": 12
    },
    "by_category": {
      "buttons": 45,
      "labels": 38,
      "messages": 32,
      "titles": 18,
      "other": 23
    }
  }
}
```

### Markdown Format: `TRANSLATION_INVENTORY.md`

```markdown
# Translation Inventory

**Generated**: 2025-10-09T13:54:00Z  
**Total Strings**: 342 (156 translatable, 186 technical)  
**Files Scanned**: 150

## Summary by Module

| Module | Total | Translatable | Technical |
|--------|-------|--------------|-----------|
| ui | 120 | 85 | 35 |
| payments | 80 | 40 | 40 |
| ... | ... | ... | ... |

## Translatable Strings (156)

### Module: ui

#### File: `modules/ui/src/main/scala/pages/Settings.scala`

##### High Confidence (3)

- **Line 61**: `"Save Changes"`
  - **Suggested Key**: `buttons.save_changes`
  - **Reasons**: Contains spaces, Capitalized words, Near button component
  - **Context**:
    ```scala
    val submitButton = button(
      "Save Changes",  // ← HERE
      onClick --> ...
    ```

- **Line 89**: `"Email Address"`
  - **Suggested Key**: `labels.email_address`
  - **Reasons**: Form label pattern, Capitalized
  - **Context**:
    ```scala
    label(
      "Email Address",  // ← HERE
      input(...)
    ```

##### Medium Confidence (1)

- **Line 102**: `"Required"`
  - **Suggested Key**: `validation.required`
  - **Reasons**: Short but likely validation message
  - **Context**:
    ```scala
    span(
      cls := "error-text",
      "Required"  // ← HERE
    ```

### Module: payments

#### File: `modules/payments/src/main/scala/service/PurchaseService.scala`

##### High Confidence (2)

- **Line 145**: `"Payment completed successfully"`
  - **Suggested Key**: `messages.payment_success`
  - **Reasons**: User-facing message, Sentence structure
  - **Context**:
    ```scala
    case Success(_) =>
      EmailContent(
        "Payment completed successfully"  // ← HERE
      )
    ```

## Technical Strings (186)

### By Category

- **CSS Classes**: 78
- **HTML IDs**: 23
- **Imports/Paths**: 42
- **URLs**: 15
- **Already i18n**: 28

### Sample Technical Strings

- `"flex-col gap-4"` (CSS class)
- `"payment-form-id"` (HTML ID)
- `"@mui/material"` (Import)
- `"https://api.example.com"` (URL)

## Action Items

1. **Review Medium/Low Confidence** (62 strings)
   - Manual classification needed
   - Check context for ambiguous cases

2. **Create Translation Keys** (156 strings)
   - Generate i18n key structure
   - Add to en.conf and fr.conf

3. **Apply Translations**
   - Replace hardcoded strings with i18n calls
   - Test translated UI

## Notes

- Strings already using I18N: 28 (marked as technical)
- Potential duplicates: Check suggested keys for conflicts
- Custom review needed: Strings with low confidence scores
```

## Translation Applier Specification (Future)

### Purpose
Automate the process of replacing hardcoded strings with i18n function calls.

### Workflow

1. **Input**: JSON inventory with approved translations
2. **Process**:
   - For each translatable string:
     - Locate exact occurrence in source file
     - Generate i18n function call: `I18N.get("suggested.key")`
     - Replace hardcoded string with function call
     - Add key to translation files if not exists
3. **Output**:
   - Modified Scala source files
   - Updated en.conf / fr.conf files
   - Change report (diff summary)

### Safety Features

- **Dry-run mode**: Show changes without applying
- **Backup creation**: Save original files before modification
- **Validation**: Ensure syntax correctness after replacement
- **Rollback**: Undo changes if validation fails

### Example Transformation

**Before**:
```scala
button(
  "Save Changes",
  onClick --> observer
)
```

**After**:
```scala
button(
  I18N.get("buttons.save_changes"),
  onClick --> observer
)
```

**Translation files updated**:
```conf
# en.conf
buttons.save_changes = "Save Changes"

# fr.conf
buttons.save_changes = "Enregistrer les modifications"
```

## Usage Guide

### Running the Scanner

```bash
# Make script executable (first time only)
chmod +x scripts/scan-hardcoded-strings.sh

# Run full scan
./scripts/scan-hardcoded-strings.sh

# Output files generated:
# - translation-inventory.json (machine-readable)
# - TRANSLATION_INVENTORY.md (human-readable)
```

### Review Process

1. **Review JSON/Markdown output**
   - Check translatable strings for accuracy
   - Verify suggested keys make sense
   - Flag any misclassifications

2. **Manual adjustments**
   - Edit JSON to reclassify if needed
   - Modify suggested keys for clarity
   - Add custom notes for translators

3. **Apply translations** (manual for now)
   - Use JSON as reference
   - Update source files
   - Add keys to translation files

### Best Practices

1. **Regular Scans**: Run after significant UI changes
2. **Version Control**: Commit inventory files for tracking
3. **Review Sessions**: Periodic manual review of classifications
4. **Key Conventions**: Follow established naming patterns
5. **Translation Notes**: Add context for translators when needed

## Maintenance

### Scanner Updates

The scanner should be enhanced to:
- Support additional file types (if needed)
- Improve classification rules based on false positives
- Add new pattern recognition for framework-specific constructs
- Optimize performance for large codebases

### Rule Refinement

Continuously improve classification by:
- Analyzing misclassified strings
- Adding new technical patterns
- Refining confidence scoring
- Updating based on team feedback

## Integration with Existing i18n

### Current i18n Structure

The project uses:
- Module-specific i18n packages (e.g., `ui-i18n`, `payments-i18n`)
- HOCON config files (`en.conf`, `fr.conf`)
- Type-safe i18n data objects
- Implicit conversion for easy access

### Scanner Alignment

The scanner:
- Respects existing i18n structure
- Suggests keys following current conventions
- Identifies already-translated strings
- Integrates with module boundaries

### Future Integration

Potential enhancements:
- Auto-generate i18n key additions to conf files
- Validate against existing keys to prevent duplicates
- Suggest translation improvements
- Integration with translation management platforms

## Limitations and Considerations

### Current Limitations

1. **Pattern-based**: May miss context-dependent strings
2. **No semantic analysis**: Cannot understand string meaning
3. **Manual review needed**: Especially for low-confidence strings
4. **No runtime detection**: Only scans source code

### Edge Cases

- **Dynamic strings**: String interpolation not detected
- **Computed strings**: String concatenation may be missed
- **External strings**: From libraries or configs not scanned
- **Comments**: Documentation strings not considered

### Recommendations

1. Always manually review the output
2. Start with high-confidence strings
3. Gradually refine classification rules
4. Maintain a feedback loop for improvements
5. Consider semantic analysis tools for future versions

## Version History

- **v1.0.0** (2025-10-09): Initial specification
  - Basic string scanning
  - Rule-based classification
  - JSON/Markdown output
  - Foundation for automation
