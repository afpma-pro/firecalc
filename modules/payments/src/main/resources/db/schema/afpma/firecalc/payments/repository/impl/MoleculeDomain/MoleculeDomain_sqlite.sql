-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 
PRAGMA foreign_keys = 1;

CREATE TABLE IF NOT EXISTS Product (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  productId TEXT,
  sku       TEXT,
  price     TEXT,
  currency  TEXT,
  active    BOOLEAN,
  taxRate   TEXT,
  taxExempt BOOLEAN
);

CREATE TABLE IF NOT EXISTS ProductOrder (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  orderId         TEXT,
  customerId      TEXT,
  productId       TEXT,
  amount          TEXT,
  currency        TEXT,
  status          TEXT,
  paymentProvider TEXT,
  paymentId       TEXT,
  language        TEXT,
  invoiceNumber   TEXT,
  productMetadata INTEGER,
  createdAt       NVARCHAR(100),
  updatedAt       NVARCHAR(100),
  CONSTRAINT _productMetadata FOREIGN KEY (productMetadata) REFERENCES ProductMetadata (id)
);

CREATE TABLE IF NOT EXISTS Customer (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  customerId        TEXT,
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

CREATE TABLE IF NOT EXISTS PurchaseIntent (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  token           TEXT,
  productId       TEXT,
  amount          TEXT,
  currency        TEXT,
  authCode        TEXT,
  failedAttempts  INTEGER,
  customer        INTEGER,
  processed       BOOLEAN,
  productMetadata INTEGER,
  expiresAt       NVARCHAR(100),
  createdAt       NVARCHAR(100),
  CONSTRAINT _customer        FOREIGN KEY (customer       ) REFERENCES Customer        (id),
  CONSTRAINT _productMetadata FOREIGN KEY (productMetadata) REFERENCES ProductMetadata (id)
);

CREATE TABLE IF NOT EXISTS ProductMetadata (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  jsonString TEXT,
  createdAt  NVARCHAR(100)
);

CREATE TABLE IF NOT EXISTS InvoiceCounter (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  currentNumber  BIGINT,
  startingNumber BIGINT,
  updatedAt      NVARCHAR(100),
  createdAt      NVARCHAR(100)
);

CREATE TABLE IF NOT EXISTS PaymentProvider (
  GoCardless TEXT,
  Unknown    TEXT
);

CREATE TABLE IF NOT EXISTS Currency (
  EUR TEXT,
  USD TEXT
);

CREATE TABLE IF NOT EXISTS OrderStatus (
  Pending    TEXT,
  Processing TEXT,
  Confirmed  TEXT,
  PaidOut    TEXT,
  Failed     TEXT,
  Cancelled  TEXT
);

CREATE TABLE IF NOT EXISTS CustomerType (
  Individual TEXT,
  Business   TEXT
);

-- Column indexes
CREATE INDEX IF NOT EXISTS _ProductOrder_productMetadata ON ProductOrder (productMetadata);
CREATE INDEX IF NOT EXISTS _PurchaseIntent_customer ON PurchaseIntent (customer);
CREATE INDEX IF NOT EXISTS _PurchaseIntent_productMetadata ON PurchaseIntent (productMetadata);

