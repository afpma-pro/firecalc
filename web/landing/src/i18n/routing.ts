/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
// Simple routing configuration for static export
export const locales = ['en', 'fr'] as const;
export const defaultLocale = 'fr' as const;

export type Locale = typeof locales[number];