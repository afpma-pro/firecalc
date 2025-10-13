-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V9__Create_invoice_counter_table.sql
-- Create table to track invoice counter (single row per database)

CREATE TABLE InvoiceCounter (
    id             INTEGER PRIMARY KEY CHECK (id = 1), -- Single row constraint for SQLite
    currentNumber  BIGINT,
    startingNumber BIGINT,
    updatedAt      NVARCHAR(100),
    createdAt      NVARCHAR(100)
);