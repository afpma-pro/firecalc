-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V7__Update_OrderStatus_enum_replace_Completed_with_Confirmed_and_PaidOut.sql
-- Update OrderStatus enum: remove "Completed", add "Confirmed" and "PaidOut"
-- Migrate existing "Completed" orders to "Confirmed"
-- Recreate OrderStatus table with enum values in correct order while preserving data

-- Step 1: Update existing ProductOrder records that have "Completed" status to "Confirmed"
-- This maintains business logic since "Confirmed" represents the new equivalent of the old "Completed"
UPDATE ProductOrder 
SET status = 'Confirmed' 
WHERE status = 'Completed';

-- Step 2: Recreate OrderStatus table with enum values in correct order
-- Using table recreation pattern for SQLite3 compatibility while preserving data

-- Step 2a: Rename existing table to _old
ALTER TABLE OrderStatus RENAME TO OrderStatus_old;

-- Step 2b: Create new OrderStatus table with updated enum values in the correct order
-- Order matches the Scala enum: Pending, Processing, Confirmed, PaidOut, Failed, Cancelled
CREATE TABLE OrderStatus_new (
  Pending    TEXT,
  Processing TEXT,
  Confirmed  TEXT,
  PaidOut    TEXT,
  Failed     TEXT,
  Cancelled  TEXT
);

-- Step 2c: Copy data from old OrderStatus table, mapping old columns to new ones
-- Handle the "Completed" -> "Confirmed" mapping in the enum table as well
INSERT INTO OrderStatus_new (Pending, Processing, Confirmed, Failed, Cancelled)
SELECT 
  Pending,
  Processing,
  Completed,  -- Map old "Completed" column to new "Confirmed" column
  Failed,
  Cancelled
FROM OrderStatus_old;

-- Step 2d: Drop old OrderStatus table
DROP TABLE OrderStatus_old;

-- Step 2e: Rename new table to final name
ALTER TABLE OrderStatus_new RENAME TO OrderStatus;

-- Verify the migration by checking updated records
-- This is a comment for verification, not executed:
-- SELECT status, COUNT(*) FROM ProductOrder GROUP BY status;
