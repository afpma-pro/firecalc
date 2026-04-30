-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V12__add_tax_fields_to_product.sql
-- Add tax rate and tax exemption flag to Product table

ALTER TABLE Product ADD COLUMN taxRate   TEXT    NOT NULL DEFAULT '20.0';
ALTER TABLE Product ADD COLUMN taxExempt INTEGER NOT NULL DEFAULT 0;