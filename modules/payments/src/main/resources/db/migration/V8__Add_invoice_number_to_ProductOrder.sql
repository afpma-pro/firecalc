-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V8__Add_invoice_number_to_ProductOrder.sql
-- Add optional invoice number field to ProductOrder table using table recreation pattern

-- Step 1: Rename existing table to _old
ALTER TABLE ProductOrder RENAME TO ProductOrder_old;

-- Step 2: Create new table with invoiceNumber column
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
  invoiceNumber   TEXT,
  productMetadata INTEGER,
  createdAt       NVARCHAR(100),
  updatedAt       NVARCHAR(100)
  -- CONSTRAINT _productMetadata FOREIGN KEY (productMetadata) REFERENCES ProductMetadata (id)
);

-- Step 3: Copy data from old table to new table
INSERT INTO ProductOrder_new (
    id, orderId, customerId, productId, amount, status, 
    paymentProvider, paymentId, language, productMetadata, 
    createdAt, updatedAt
)
SELECT 
    id, orderId, customerId, productId, amount, status, 
    paymentProvider, paymentId, language, productMetadata, 
    createdAt, updatedAt 
FROM ProductOrder_old;

-- Step 4: Drop old table
DROP TABLE ProductOrder_old;

-- Step 5: Rename new table to final name
ALTER TABLE ProductOrder_new RENAME TO ProductOrder;
