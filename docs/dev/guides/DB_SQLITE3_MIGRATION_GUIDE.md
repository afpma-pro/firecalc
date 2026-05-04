<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Managing SQLite3 Database Schema with Molecule Auto-Migration

This guide outlines the process for managing the SQLite3 database schema for the `payments` module using Molecule's built-in Flyway-based auto-migration system (introduced in molecule v0.29.0).

## Overview

[ScalaMolecule](https://www.scalamolecule.org/) now handles schema migrations automatically. When you modify the domain model and run `sbt moleculeGen`, Molecule:

1.  Compares the current domain structure against the saved previous state (`_previous.scala`)
2.  Detects additions, renames, and removals (when marked with migration markers)
3.  Generates versioned Flyway SQL migration files in the dialect-specific directory
4.  Updates the state file for the next change cycle

Migration files are applied automatically at application startup via Flyway.

## Developer Workflow for Schema Changes

### Step 1: Modify the Domain Model with Migration Markers

Make changes to `MoleculeDomain.scala` and annotate them with migration markers:

- **Additions** are auto-detected (no marker needed)
- **Removals** require `.remove`
- **Renames** require `.rename("newName")`

### Step 2: Run moleculeGen

```bash
sbt "payments/moleculeGen"
```

Molecule will generate a new Flyway migration SQL file (e.g., `V14__molecule_1_change.sql`) in the dialect directory.

### Step 3: For Ambiguous Changes, Resolve and Re-run

If attributes or entities are removed without explicit markers, Molecule generates a resolution file (`MoleculeDomain_migration.scala`) asking you to choose `.remove` or `.rename`. Edit that file, then re-run `sbt moleculeGen`.

### Step 4: For DML Changes

Molecule auto-migration handles DDL only. For data transformations:
- Write a separate manual Flyway SQL file alongside molecule's generated one with an appropriate version number
- Or use molecule's query/update API in application code

### Step 5: Deploy

Deploy normally. Flyway applies new migrations at startup.

## Migration Markers Reference

### Entities

```scala
trait OldEntity extends Remove
trait OldEntity extends Rename("NewEntity")
```

### Attributes

```scala
trait Person {
  val email = oneString.remove
  val oldName = oneString.rename("fullName")
  val phone = oneString  // Additions are auto-detected
  val name = oneString.index     // Add or remove index
  val name = oneString.owner     // ON DELETE CASCADE
}
```

### Relationships

```scala
trait Order {
  val customer = manyToOne[Customer].remove
  val user = manyToOne[User].rename("account")
  val company = manyToOne[Company].owner  // ON DELETE CASCADE
}
```

### Segments

```scala
object analytics extends Remove
object oldSegment extends Rename("newSegment")
```

## Resolution File Workflow

When attributes disappear without `.remove` or `.rename`, Molecule generates `MoleculeDomain_migration.scala` with all ambiguous changes listed. Each attribute has two commented-out options:

```scala
trait InvoiceCounterMigrations extends InvoiceCounter {
  val testField = oneString.remove     // if removed
  val testField = oneString.becomes()  // if renamed
}
```

Uncomment the intended option (and delete the other), then re-run `sbt moleculeGen`.

## DML Strategy

For data transformations (e.g., seeding data, migrating values between columns), options include:

1. **Manual Flyway SQL file**: Write a separate SQL file alongside molecule's generated DDL file. Choose a version number that slots between molecule-generated files.
2. **Molecule query/update API**: Use the generated DSL to perform data migrations in application code during startup.
3. **Application migration code**: Run data transformations in a dedicated migration routine before normal operation begins.

## ⚠️ CRITICAL: SQLite3 Migration Limitations and Warnings

**READ THIS SECTION CAREFULLY** before writing any manual migration scripts. SQLite3 has significant limitations compared to other SQL databases that can cause migration failures if not properly understood.

### Supported ALTER TABLE Operations

SQLite3 only supports these `ALTER TABLE` operations:

✅ **SUPPORTED:**
```sql
-- Adding new columns (always added at the end)
ALTER TABLE Customer ADD COLUMN companyName TEXT;
ALTER TABLE Customer ADD COLUMN isActive INTEGER DEFAULT 1;

-- Renaming tables
ALTER TABLE OldTableName RENAME TO NewTableName;

-- Renaming columns (SQLite 3.25.0+)
ALTER TABLE Customer RENAME COLUMN old_name TO new_name;

-- Dropping columns (SQLite 3.35.0+ only, check your SQLite version!)
ALTER TABLE Customer DROP COLUMN unwanted_column;
```

### ❌ UNSUPPORTED ALTER TABLE Operations

These common operations will **FAIL** in SQLite3:

```sql
-- ❌ WILL FAIL: Changing column type
ALTER TABLE Customer ALTER COLUMN age TYPE VARCHAR(10);

-- ❌ WILL FAIL: Changing column constraints
ALTER TABLE Customer ALTER COLUMN email SET NOT NULL;
ALTER TABLE Customer ALTER COLUMN email DROP NOT NULL;

-- ❌ WILL FAIL: Adding constraints to existing tables
ALTER TABLE Customer ADD CONSTRAINT uk_email UNIQUE (email);

-- ❌ WILL FAIL: Dropping constraints
ALTER TABLE Customer DROP CONSTRAINT fk_address;

-- ❌ WILL FAIL: Changing default values
ALTER TABLE Customer ALTER COLUMN status SET DEFAULT 'active';

-- ❌ WILL FAIL: Reordering columns
ALTER TABLE Customer ADD COLUMN middle_name TEXT AFTER first_name;
```

### Workarounds for Complex Schema Changes

When you need to perform unsupported operations, use the **table recreation pattern**:

```sql
-- Example: Changing column type from INTEGER to TEXT
-- Step 1: Create new table with desired schema
CREATE TABLE Customer_new (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    age TEXT,  -- Changed from INTEGER to TEXT
    email TEXT UNIQUE
);

-- Step 2: Copy data (with type conversion if needed)
INSERT INTO Customer_new (id, name, age, email)
SELECT id, name, CAST(age AS TEXT), email FROM Customer;

-- Step 3: Drop old table
DROP TABLE Customer;

-- Step 4: Rename new table
ALTER TABLE Customer_new RENAME TO Customer;

-- Step 5: Recreate indexes if any
CREATE INDEX idx_customer_email ON Customer(email);
```

### SQLite3-Specific Considerations

1. **Transaction Behavior**: SQLite3 uses immediate transactions. Schema changes are committed immediately.

2. **FOREIGN KEY Constraints**: 
   ```sql
   -- Enable foreign key support (often disabled by default)
   PRAGMA foreign_keys = ON;
   ```

3. **Data Types**: SQLite3 has dynamic typing. These are equivalent:
   ```sql
   CREATE TABLE Example (
       text_col TEXT,      -- ✅ Preferred
       text_col VARCHAR,   -- ✅ Works (mapped to TEXT)
       text_col STRING,    -- ✅ Works (mapped to TEXT)
       int_col INTEGER,    -- ✅ Preferred
       int_col INT,        -- ✅ Works (mapped to INTEGER)
       real_col REAL,      -- ✅ For floating point
       blob_col BLOB       -- ✅ For binary data
   );
   ```

4. **NULL Handling**: SQLite3 allows NULL in PRIMARY KEY columns (unlike other databases).

### Testing Migration Scripts

Always test your migration scripts on a copy of your database:

```bash
# Create a test copy
cp firecalc-payments-prod.db firecalc-payments-prod_test.db

# Test your migration manually
sqlite3 firecalc-payments-prod_test.db < path/to/your/migration.sql

# Verify the schema
sqlite3 firecalc-payments-prod_test.db ".schema"
```

## Database Backup and Restore (SQLite3)

SQLite3's simplicity makes backup and restore straightforward: it's just a file copy. **Always back up the database before applying migrations.**

### Backup

```bash
# Create a backup before migrating (with timestamp)
cp firecalc-payments-prod.db firecalc-payments-prod.db.backup-$(date +%Y%m%d-%H%M%S)

# Create a backup with custom name
cp firecalc-payments-prod.db firecalc-payments-prod.db.backup-before-v5-migration

# Verify database integrity after backup
sqlite3 firecalc-payments-prod.db.backup-$(date +%Y%m%d-%H%M%S) "PRAGMA integrity_check;"
```

### Restore

```bash
# Stop the application first!
# Then restore from specific backup
cp firecalc-payments-prod.db.backup-YYYYMMDD-HHMMSS firecalc-payments-prod.db

# Verify restored database integrity
sqlite3 firecalc-payments-prod.db "PRAGMA integrity_check;"
```

### Database Maintenance

```bash
# Vacuum to reclaim space and optimize
sqlite3 firecalc-payments-prod.db "VACUUM;"

# Analyze to update query planner statistics
sqlite3 firecalc-payments-prod.db "ANALYZE;"

# Check database integrity
sqlite3 firecalc-payments-prod.db "PRAGMA integrity_check;"
```

## Best Practices

-   **Use Migration Markers**: Always use `.remove`, `.rename`, etc. when changing the domain to avoid ambiguous change resolution steps.

-   **Never Edit an Applied Migration**: Once a migration has been applied to any database (especially production), it must be considered immutable. Flyway's validation will fail if it detects a checksum mismatch.

-   **Write Small, Atomic Changes**: Make one logical change at a time. This simplifies the generated migration and troubleshooting.

-   **Test Migrations Thoroughly**: Run `sbt moleculeGen` and verify the generated SQL before deploying. Test on a database copy.

-   **Coordinate with Team**: Ensure your migration version numbers do not conflict with those created by other developers. Version numbers are computed from the dialect directory, so concurrent domain changes need sequencing.

-   **Version Control Migration Files**: Always commit migration files and the `_previous.scala` state file to version control.

## Common Patterns

### Adding a Field

```scala
// In MoleculeDomain.scala — no marker needed for additions
trait Product {
  val newField = oneString
}
```

Run `sbt moleculeGen` → generates `V<N>__molecule_1_change.sql` with:
```sql
ALTER TABLE Product ADD COLUMN newField TEXT;
```

### Removing a Field

```scala
trait Product {
  val oldField = oneString.remove
}
```

### Renaming a Field

```scala
trait Product {
  val oldName = oneString.rename("newName")
}
```

### Adding a New Entity

```scala
trait MoleculeDomain extends DomainStructure:
  // new entity — auto-detected
  trait NewEntity {
    val id = oneLong
    val name = oneString
  }
```

## Troubleshooting

### Common Error Messages

1. **"table X has no column named Y"**
   - Ensure column exists before referencing it, or add it first

2. **"no such table: X"**
   - Check table name spelling and ensure table exists

3. **"cannot start a transaction within a transaction"**
   - SQLite3 auto-starts transactions; avoid explicit BEGIN/COMMIT in migrations

4. **"foreign key constraint failed"**
   - Ensure referenced data exists, or temporarily disable foreign keys

5. **"duplicate column name"**
   - Check if column already exists before adding

### Debugging Failed Migrations

```bash
# Check Flyway migration history
sqlite3 firecalc-payments-prod.db "SELECT * FROM flyway_schema_history ORDER BY installed_on;"

# Check current schema
sqlite3 firecalc-payments-prod.db ".schema"

# Check specific table structure
sqlite3 firecalc-payments-prod.db ".schema InvoiceCounter"

# Check indexes
sqlite3 firecalc-payments-prod.db ".indexes InvoiceCounter"
```

### Checking Migration Status

```bash
sbt moleculeMigrationStatus
```

Displays which domains have migration handling enabled and current state.
