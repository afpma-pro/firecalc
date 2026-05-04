-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- Add the new column
ALTER TABLE ProductOrder ADD COLUMN paymentProvider TEXT;

-- Update the new column with the default value
UPDATE ProductOrder SET paymentProvider = 'GoCardless';

-- Rename the table
ALTER TABLE ProductOrder RENAME TO ProductOrder_old;

-- Create the new table with the renamed column
CREATE TABLE ProductOrder (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  orderId         VARCHAR(16),
  customerId      VARCHAR(16),
  productId       VARCHAR(16),
  amount          TEXT,
  status          TEXT,
  paymentProvider TEXT,
  paymentId       TEXT,
  language        TEXT,
  createdAt       NVARCHAR(100),
  updatedAt       NVARCHAR(100)
);

-- Copy the data from the old table to the new table
INSERT INTO ProductOrder (id, orderId, customerId, productId, amount, status, paymentProvider, paymentId, language, createdAt, updatedAt)
SELECT id, orderId, customerId, productId, amount, status, paymentProvider, gocardlessPaymentId, language, createdAt, updatedAt FROM ProductOrder_old;

-- Drop the old table
DROP TABLE ProductOrder_old;
