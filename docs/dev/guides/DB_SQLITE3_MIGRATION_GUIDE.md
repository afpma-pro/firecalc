<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Managing SQLite3 Database Schema with Flyway and ScalaMolecule

This guide outlines the process for managing the SQLite3 database schema for the `payments` module using `flyway`. It is crucial to follow these steps to ensure that schema changes are applied safely and consistently across all environments.

## Overview

We use [ScalaMolecule](https://www.scalamolecule.org/) to define our domain model, which in turn generates a target SQL schema optimized for SQLite3. However, Molecule does not handle schema migrations (i.e., evolving the schema from one version to another). For this, we use Flyway, **a** database migration tool.

The core workflow is:
1.  Update the domain model in Scala (`MoleculeDomain.scala`).
2.  Generate the new target schema using Molecule (`sbt payments/moleculeGen`).
3.  Compare the new schema with the old one to determine the required changes.
4.  Write a new SQL migration script with SQLite3-compatible statements.
5.  Apply the migration using Flyway.

## ⚠️ CRITICAL: SQLite3 Migration Limitations and Warnings

**READ THIS SECTION CAREFULLY** before writing any migration scripts. SQLite3 has significant limitations compared to other SQL databases that can cause migration failures if not properly understood.

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

## Developer Workflow for Schema Changes

Follow these steps whenever you need to make a change to the database schema.

### Step 1: Modify the Domain Model

Make your desired changes to the entities and attributes in `modules/payments/src/main/scala/afpma/firecalc/payments/repository/MoleculeDomain.scala`.

### Step 2: Regenerate the Target Schema

After modifying the domain model, you must regenerate the SQL schema file. Run the following command from the project root:

```bash
sbt payments/moleculeGen
```

This command updates the target schema file located at `modules/payments/target/scala-3.7.3/resource_managed/main/moleculeGen/MoleculeDomain/MoleculeDomain_Schema_sqlite.sql`.

### Step 3: Determine the Schema Difference

To create a migration, you need to know exactly what changed between the old schema and the new one. Use a `diff` tool to compare the newly generated schema with the last version from your git history.

For example, you can use `git diff` on the generated file to see the changes:

```bash
git diff modules/payments/target/scala-3.7.3/resource_managed/main/moleculeGen/MoleculeDomain/MoleculeDomain_Schema_sqlite.sql
```

This will show you the exact `CREATE TABLE` or other changes Molecule has generated.

### Step 4: Write a New Migration Script

Based on the differences you identified, create a new SQL migration file **compatible with SQLite3**.

1.  **File Naming:** Migration files must follow the pattern `V<VERSION>__<DESCRIPTION>.sql`. The version number must be unique and sequential. For example: `V2__Add_company_name_to_Customer.sql`.

2.  **Location:** Place the new file in `modules/payments/src/main/resources/db/migration/`.

3.  **Content:** Write **SQLite3-compatible** SQL statements. Examples:

    ```sql
    -- V2__Add_company_name_to_Customer.sql
    -- Simple column addition (always supported)
    ALTER TABLE Customer ADD COLUMN companyName TEXT;
    ```

    ```sql
    -- V3__Add_customer_status_with_default.sql
    -- Adding column with default value
    ALTER TABLE Customer ADD COLUMN status TEXT DEFAULT 'active';
    ```

    ```sql
    -- V4__Create_payment_log_table.sql
    -- Creating new table
    CREATE TABLE PaymentLog (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        customerId INTEGER NOT NULL,
        amount REAL NOT NULL,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (customerId) REFERENCES Customer(id)
    );
    
    CREATE INDEX idx_payment_log_customer ON PaymentLog(customerId);
    CREATE INDEX idx_payment_log_timestamp ON PaymentLog(timestamp);
    ```

**Important**: 
- The migration script should only contain the changes needed to get from the previous schema version to the new one. 
- Do not copy the entire generated schema.
- Ensure all statements are SQLite3-compatible (see warnings section above).

## Applying Migrations

Migrations are applied automatically when the application starts. The `Main.scala` file calls the `Migrations.migrate` method before initializing the database connection.

Before applying migrations, **always backup the database**:

```bash
# Create timestamped backup
cp firecalc-payments-prod.db firecalc-payments-prod.db.backup-$(date +%Y%m%d-%H%M%S)

# Verify backup was created
ls -la firecalc-payments-prod.db.backup-*
```

Then, apply migrations by running the application:

```bash
sbt "payments/run"
```

The application will log the status of the migrations to the console. Monitor for any SQLite3-specific errors.

## Database Backup and Restore (SQLite3)

SQLite3's simplicity makes backup and restore straightforward: it's just a file copy. **Always back up the database before applying migrations.**

### Backup

To create a backup of the SQLite3 database, simply copy the database file:

```bash
# Create a backup before migrating (with timestamp)
cp firecalc-payments-prod.db firecalc-payments-prod.db.backup-$(date +%Y%m%d-%H%M%S)

# Create a backup with custom name
cp firecalc-payments-prod.db firecalc-payments-prod.db.backup-before-v5-migration

# Verify database integrity after backup
sqlite3 firecalc-payments-prod.db.backup-$(date +%Y%m%d-%H%M%S) "PRAGMA integrity_check;"
```

### Restore

To restore the database from a backup, simply replace the current database file:

```bash
# Stop the application first!
# Then restore from specific backup
cp firecalc-payments-prod.db.backup-YYYYMMDD-HHMMSS firecalc-payments-prod.db

# Verify restored database integrity
sqlite3 firecalc-payments-prod.db "PRAGMA integrity_check;"
```

### Database Maintenance

Periodically optimize your SQLite3 database:

```bash
# Vacuum to reclaim space and optimize
sqlite3 firecalc-payments-prod.db "VACUUM;"

# Analyze to update query planner statistics
sqlite3 firecalc-payments-prod.db "ANALYZE;"

# Check database integrity
sqlite3 firecalc-payments-prod.db "PRAGMA integrity_check;"
```

## Best Practices for SQLite3 Migrations

-   **Always Use Table Recreation Pattern for Schema Changes**: When adding columns to existing tables, always create an "_old" table and a "_new" table as a transition, then drop "_old" and remove "_new" suffix. This ensures data consistency and proper constraint handling.

-   **Never Edit an Applied Migration**: Once a migration has been applied to any database (especially production), it must be considered immutable. If you need to make further changes, create a new migration. Flyway's validation will fail if it detects a checksum mismatch.

-   **Write Small, Atomic Migrations**: Each migration should represent a single, logical change to the schema. This makes troubleshooting easier and reduces the risk of partial application.

-   **Test Migrations Thoroughly**: Always run migrations in a local environment before applying them to production. Verify that:
    - The migration completes successfully
    - The application works as expected after the migration
    - No data is lost or corrupted
    - Performance is not significantly impacted

-   **Use SQLite3-Compatible Syntax Only**: Before writing any migration, consult the SQLite3 limitations section above. Use the table recreation pattern for complex changes.

-   **Handle Foreign Keys Carefully**: 
    ```sql
    -- Disable foreign keys during complex migrations
    PRAGMA foreign_keys = OFF;
    -- ... perform migration ...
    PRAGMA foreign_keys = ON;
    ```

-   **Coordinate with Team**: Ensure that your migration version numbers do not conflict with those created by other developers.

-   **Monitor Database Size**: SQLite3 databases can grow large. Monitor the size and consider `VACUUM` operations:
    ```bash
    # Check database size
    ls -lh firecalc-payments-prod.db
    
    # Check for unused space
    sqlite3 firecalc-payments-prod.db "PRAGMA freelist_count;"
    ```

-   **Version Control Migration Files**: Always commit migration files to version control and never modify them after they've been applied to any environment.

## Common SQLite3 Migration Patterns

### Adding Columns
```sql
-- Simple column addition
ALTER TABLE Customer ADD COLUMN phone TEXT;

-- Column with default value
ALTER TABLE Customer ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP;

-- Column with constraints (new columns can have constraints)
ALTER TABLE Customer ADD COLUMN email TEXT UNIQUE;
```

### Creating Related Tables
```sql
-- Create related table with foreign key
CREATE TABLE CustomerAddress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customerId INTEGER NOT NULL,
    street TEXT NOT NULL,
    city TEXT NOT NULL,
    FOREIGN KEY (customerId) REFERENCES Customer(id) ON DELETE CASCADE
);

-- Create index for foreign key
CREATE INDEX idx_customer_address_customer ON CustomerAddress(customerId);
```

### Data Migration with New Tables
```sql
-- Create lookup table
CREATE TABLE CustomerType (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

-- Insert reference data
INSERT INTO CustomerType (id, name) VALUES 
    (1, 'Individual'),
    (2, 'Business'),
    (3, 'Non-Profit');

-- Add foreign key column to existing table
ALTER TABLE Customer ADD COLUMN typeId INTEGER DEFAULT 1;

-- Update existing data based on business logic
UPDATE Customer SET typeId = 2 WHERE companyName IS NOT NULL;

-- Create index
CREATE INDEX idx_customer_type ON Customer(typeId);
```

## Troubleshooting SQLite3 Migrations

### Common Error Messages

1. **"table X has no column named Y"**
   - Solution: Ensure column exists before referencing it, or add it first

2. **"no such table: X"**
   - Solution: Check table name spelling and ensure table exists

3. **"cannot start a transaction within a transaction"**
   - Solution: SQLite3 auto-starts transactions; avoid explicit BEGIN/COMMIT in migrations

4. **"foreign key constraint failed"**
   - Solution: Ensure referenced data exists, or temporarily disable foreign keys

5. **"duplicate column name"**
   - Solution: Check if column already exists before adding

### Debugging Failed Migrations

```bash
# Check Flyway migration history
sqlite3 firecalc-payments-prod.db "SELECT * FROM flyway_schema_history ORDER BY installed_on;"

# Check current schema
sqlite3 firecalc-payments-prod.db ".schema"

# Check specific table structure
sqlite3 firecalc-payments-prod.db ".schema Customer"

# Check indexes
sqlite3 firecalc-payments-prod.db ".indexes Customer"
