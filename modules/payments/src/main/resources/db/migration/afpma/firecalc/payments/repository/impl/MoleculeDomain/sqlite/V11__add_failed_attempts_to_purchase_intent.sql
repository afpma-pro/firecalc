-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V11__add_failed_attempts_to_purchase_intent.sql
-- SEC-003: Add failed attempt counter for auth code brute-force protection
-- Using table recreation pattern for SQLite3 compatibility

-- Step 1: Create new PurchaseIntent table with failedAttempts column
CREATE TABLE PurchaseIntent_new (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    token           VARCHAR(16),
    productId       VARCHAR(16),
    amount          TEXT,
    currency        TEXT DEFAULT 'EUR',
    authCode        TEXT,
    failedAttempts  INTEGER NOT NULL DEFAULT 0,
    customer        INTEGER,
    processed       BOOLEAN,
    productMetadata INTEGER,
    expiresAt       NVARCHAR(100),
    createdAt       NVARCHAR(100)
);

-- Step 2: Copy data from old PurchaseIntent table (failedAttempts defaults to 0)
INSERT INTO PurchaseIntent_new (id, token, productId, amount, currency, authCode, failedAttempts, customer, processed, productMetadata, expiresAt, createdAt)
SELECT id, token, productId, amount, currency, authCode, 0, customer, processed, productMetadata, expiresAt, createdAt FROM PurchaseIntent;

-- Step 3: Drop old PurchaseIntent table
DROP TABLE PurchaseIntent;

-- Step 4: Rename new table
ALTER TABLE PurchaseIntent_new RENAME TO PurchaseIntent;
