-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

-- V4__Add_Processing_status_to_OrderStatus.sql
-- Add Processing status to OrderStatus enum table

ALTER TABLE OrderStatus ADD COLUMN Processing TEXT;
