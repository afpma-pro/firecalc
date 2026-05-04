-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V10__Add_currency_support.sql
-- Add currency support to Product, ProductOrder, and PurchaseIntent tables
-- Using table recreation pattern for SQLite3 compatibility

-- Step 1: Create Currency lookup table
CREATE TABLE Currency (
    EUR TEXT,
    USD TEXT
);

-- Step 2: Recreate Product table with currency column
CREATE TABLE Product_new (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    productId   VARCHAR(16),
    name        TEXT,
    description TEXT,
    price       TEXT,
    currency    TEXT DEFAULT 'EUR',
    active      BOOLEAN
);

-- Copy data from old Product table (set default currency to EUR)
INSERT INTO Product_new (id, productId, name, description, price, currency, active)
SELECT id, productId, name, description, price, 'EUR', active FROM Product;

-- Drop old Product table
DROP TABLE Product;

-- Rename new table
ALTER TABLE Product_new RENAME TO Product;

-- Step 3: Recreate ProductOrder table with currency column
CREATE TABLE ProductOrder_new (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    orderId         VARCHAR(16),
    customerId      VARCHAR(16),
    productId       VARCHAR(16),
    amount          TEXT,
    currency        TEXT DEFAULT 'EUR',
    status          TEXT,
    paymentProvider TEXT,
    paymentId       TEXT,
    language        TEXT,
    invoiceNumber   TEXT,
    productMetadata INTEGER,
    createdAt       NVARCHAR(100),
    updatedAt       NVARCHAR(100)
);

-- Copy data from old ProductOrder table (set default currency to EUR)
INSERT INTO ProductOrder_new (id, orderId, customerId, productId, amount, currency, status, paymentProvider, paymentId, language, invoiceNumber, productMetadata, createdAt, updatedAt)
SELECT id, orderId, customerId, productId, amount, 'EUR', status, paymentProvider, paymentId, language, invoiceNumber, productMetadata, createdAt, updatedAt FROM ProductOrder;

-- Drop old ProductOrder table
DROP TABLE ProductOrder;

-- Rename new table
ALTER TABLE ProductOrder_new RENAME TO ProductOrder;

-- Step 4: Recreate PurchaseIntent table with currency column
CREATE TABLE PurchaseIntent_new (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    token           VARCHAR(16),
    productId       VARCHAR(16),
    amount          TEXT,
    currency        TEXT DEFAULT 'EUR',
    authCode        TEXT,
    customer        INTEGER,
    processed       BOOLEAN,
    productMetadata INTEGER,
    expiresAt       NVARCHAR(100),
    createdAt       NVARCHAR(100)
);

-- Copy data from old PurchaseIntent table (set default currency to EUR)
INSERT INTO PurchaseIntent_new (id, token, productId, amount, currency, authCode, customer, processed, productMetadata, expiresAt, createdAt)
SELECT id, token, productId, amount, 'EUR', authCode, customer, processed, productMetadata, expiresAt, createdAt FROM PurchaseIntent;

-- Drop old PurchaseIntent table
DROP TABLE PurchaseIntent;

-- Rename new table
ALTER TABLE PurchaseIntent_new RENAME TO PurchaseIntent;
