-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V5__Add_ProductMetadata_support.sql
-- Add ProductMetadata table and productMetadata column to ProductOrder and PurchaseIntent tables
-- Using table recreation pattern for SQLite3 compatibility

-- Step 1: Create ProductMetadata table
CREATE TABLE ProductMetadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jsonString TEXT NOT NULL
);

-- Step 2: Recreate ProductOrder table with productMetadata column
CREATE TABLE ProductOrder_new (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    orderId         VARCHAR(16),
    customerId      VARCHAR(16),
    productId       VARCHAR(16),
    amount          TEXT,
    status          TEXT,
    paymentProvider TEXT,
    paymentId       TEXT,
    language        TEXT,
    productMetadata INTEGER,
    createdAt       NVARCHAR(100),
    updatedAt       NVARCHAR(100)
);

-- Copy data from old ProductOrder table
INSERT INTO ProductOrder_new (id, orderId, customerId, productId, amount, status, paymentProvider, paymentId, language, createdAt, updatedAt)
SELECT id, orderId, customerId, productId, amount, status, paymentProvider, paymentId, language, createdAt, updatedAt FROM ProductOrder;

-- Drop old ProductOrder table
DROP TABLE ProductOrder;

-- Rename new table
ALTER TABLE ProductOrder_new RENAME TO ProductOrder;

-- Step 3: Recreate PurchaseIntent table with productMetadata column
CREATE TABLE PurchaseIntent_new (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    token           VARCHAR(16),
    productId       VARCHAR(16),
    amount          TEXT,
    authCode        TEXT,
    customer        INTEGER,
    processed       BOOLEAN,
    productMetadata INTEGER,
    expiresAt       NVARCHAR(100),
    createdAt       NVARCHAR(100)
);

-- Copy data from old PurchaseIntent table
INSERT INTO PurchaseIntent_new (id, token, productId, amount, authCode, customer, processed, expiresAt, createdAt)
SELECT id, token, productId, amount, authCode, customer, processed, expiresAt, createdAt FROM PurchaseIntent;

-- Drop old PurchaseIntent table
DROP TABLE PurchaseIntent;

-- Rename new table
ALTER TABLE PurchaseIntent_new RENAME TO PurchaseIntent;
