-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V6__Add_createdAt_to_ProductMetadata.sql
-- Add createdAt column to ProductMetadata table
-- Using table recreation pattern for SQLite3 compatibility

-- Step 1: Create new ProductMetadata table with createdAt column
CREATE TABLE ProductMetadata_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jsonString TEXT NOT NULL,
    createdAt NVARCHAR(100)
);

-- Step 2: Copy data from old ProductMetadata table with default timestamp
INSERT INTO ProductMetadata_new (id, jsonString, createdAt)
SELECT id, jsonString, datetime('now') FROM ProductMetadata;

-- Step 3: Drop old ProductMetadata table
DROP TABLE ProductMetadata;

-- Step 4: Rename new table
ALTER TABLE ProductMetadata_new RENAME TO ProductMetadata;
