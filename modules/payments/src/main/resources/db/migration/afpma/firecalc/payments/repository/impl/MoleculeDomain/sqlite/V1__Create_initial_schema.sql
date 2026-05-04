-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- PRAGMA foreign_keys = 1;

CREATE TABLE IF NOT EXISTS Product (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  productId   VARCHAR(16),
  name        TEXT,
  description TEXT,
  price       TEXT,
  active      BOOLEAN
);

CREATE TABLE IF NOT EXISTS ProductOrder (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  orderId             VARCHAR(16),
  customerId          VARCHAR(16),
  productId           VARCHAR(16),
  amount              TEXT,
  status              TEXT,
  gocardlessPaymentId TEXT,
  language            TEXT,
  createdAt           NVARCHAR(100),
  updatedAt           NVARCHAR(100)
);

CREATE TABLE IF NOT EXISTS Customer (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  customerId   VARCHAR(16),
  email        TEXT,
  customerType TEXT,
  language     TEXT,
  givenName    TEXT,
  familyName   TEXT,
  companyName  TEXT,
  addressLine1 TEXT,
  addressLine2 TEXT,
  addressLine3 TEXT,
  city         TEXT,
  region       TEXT,
  postalCode   TEXT,
  countryCode  TEXT,
  phoneNumber  TEXT,
  createdAt    NVARCHAR(100),
  updatedAt    NVARCHAR(100)
);

CREATE TABLE IF NOT EXISTS PurchaseIntent (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  token     VARCHAR(16),
  productId VARCHAR(16),
  amount    TEXT,
  authCode  TEXT,
  customer  INTEGER,
  processed BOOLEAN,
  expiresAt NVARCHAR(100),
  createdAt NVARCHAR(100)
  -- CONSTRAINT _customer FOREIGN KEY (customer) REFERENCES Customer (id)
);

CREATE TABLE IF NOT EXISTS OrderStatus (
  Pending   TEXT,
  Completed TEXT,
  Failed    TEXT,
  Cancelled TEXT
);

CREATE TABLE IF NOT EXISTS CustomerType (
  Individual TEXT,
  Business   TEXT
);
