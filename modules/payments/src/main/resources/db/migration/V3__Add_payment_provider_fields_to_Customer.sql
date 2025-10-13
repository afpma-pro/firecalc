-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- Add payment provider fields to Customer table to support multiple payment providers
-- SQLite has limited ALTER TABLE support, so we use the rename-recreate-copy-drop pattern

-- Rename the existing table
ALTER TABLE Customer RENAME TO Customer_old;

-- Create the new table with the additional columns
CREATE TABLE Customer (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  customerId        VARCHAR(16),
  email             TEXT,
  customerType      TEXT,
  language          TEXT,
  givenName         TEXT,
  familyName        TEXT,
  companyName       TEXT,
  addressLine1      TEXT,
  addressLine2      TEXT,
  addressLine3      TEXT,
  city              TEXT,
  region            TEXT,
  postalCode        TEXT,
  countryCode       TEXT,
  phoneNumber       TEXT,
  paymentProviderId TEXT,
  paymentProvider   TEXT,
  createdAt         NVARCHAR(100),
  updatedAt         NVARCHAR(100)
);

-- Copy data from the old table to the new table
INSERT INTO Customer (id, customerId, email, customerType, language, givenName, familyName, companyName, addressLine1, addressLine2, addressLine3, city, region, postalCode, countryCode, phoneNumber, createdAt, updatedAt)
SELECT id, customerId, email, customerType, language, givenName, familyName, companyName, addressLine1, addressLine2, addressLine3, city, region, postalCode, countryCode, phoneNumber, createdAt, updatedAt FROM Customer_old;

-- Drop the old table
DROP TABLE Customer_old;
