/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

-- V13: Drop name and description columns from Product (moved to YAML-sourced config).
-- Add sku column (stable, operator-facing SKU identifier).
--
-- Uses the SQLite "rebuild table" pattern because SQLite cannot drop multiple
-- columns in a way that also lets us insert a new column at the correct
-- position to match the Molecule-generated target schema. Column order and
-- (crucially) lax/nullable types must match
--   modules/payments/src/main/resources/moleculeGen/afpma/firecalc/payments/
--     repository/impl/MoleculeDomain/MoleculeDomain_sqlite.sql
-- exactly, otherwise Molecule's generated DB access will not find the
-- expected columns at the expected offsets.

-- Note on types: Molecule emits lax types with no NOT NULL. SQLite uses
-- dynamic typing — `BOOLEAN` / `VARCHAR(16)` are affinity hints, not
-- enforced constraints. Do not add NOT NULL here; it is not in the target.

CREATE TABLE Product_new (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  productId VARCHAR(16),
  sku       TEXT,
  price     TEXT,
  currency  TEXT,
  active    BOOLEAN,
  taxRate   TEXT,
  taxExempt BOOLEAN
);

-- Copy existing data. Only one SKU exists today, so we seed `sku` with a
-- constant mapping. `name` and `description` are dropped.
INSERT INTO Product_new (id, productId, sku, price, currency, active, taxRate, taxExempt)
SELECT id,
       productId,
       'pdf_report_EN_15544_2023' AS sku,
       price,
       currency,
       active,
       taxRate,
       taxExempt
  FROM Product;

DROP TABLE Product;
ALTER TABLE Product_new RENAME TO Product;
