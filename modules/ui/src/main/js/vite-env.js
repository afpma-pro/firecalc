/**
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

// Vite environment variables wrapper
// This file is processed by Vite and provides access to import.meta.env

export const VITE_BACKEND_PROTOCOL = import.meta.env.VITE_BACKEND_PROTOCOL;
export const VITE_BACKEND_HOST = import.meta.env.VITE_BACKEND_HOST;
export const VITE_BACKEND_PORT = import.meta.env.VITE_BACKEND_PORT;
export const VITE_BACKEND_BASE_PATH = import.meta.env.VITE_BACKEND_BASE_PATH;

/** URL to Conditions Générales de Vente (CGUV / Terms and Conditions of Sale) */
export const VITE_CGUV_URL = import.meta.env.VITE_CGUV_URL;

// Only export the MODE string - we handle mode logic in Scala using the BuildMode enum
export const MODE = import.meta.env.MODE;

// Firebox availability switches (UI controls)
export const VITE_FIREBOX_AVAIL_TRADITIONAL = import.meta.env.VITE_FIREBOX_AVAIL_TRADITIONAL;
export const VITE_FIREBOX_AVAIL_ECOLABELED = import.meta.env.VITE_FIREBOX_AVAIL_ECOLABELED;
export const VITE_FIREBOX_AVAIL_AFPMA_PRSE = import.meta.env.VITE_FIREBOX_AVAIL_AFPMA_PRSE;
export const VITE_FIREBOX_AVAIL_SINGLE_TESTED = import.meta.env.VITE_FIREBOX_AVAIL_SINGLE_TESTED;
export const VITE_FIREBOX_AVAIL_DOOR15A_CATALOG = import.meta.env.VITE_FIREBOX_AVAIL_DOOR15A_CATALOG;

// Firebox availability switches (backend enforcement)
export const VITE_FIREBOX_AVAIL_TRADITIONAL_BACKEND = import.meta.env.VITE_FIREBOX_AVAIL_TRADITIONAL_BACKEND;
export const VITE_FIREBOX_AVAIL_ECOLABELED_BACKEND = import.meta.env.VITE_FIREBOX_AVAIL_ECOLABELED_BACKEND;
export const VITE_FIREBOX_AVAIL_AFPMA_PRSE_BACKEND = import.meta.env.VITE_FIREBOX_AVAIL_AFPMA_PRSE_BACKEND;
export const VITE_FIREBOX_AVAIL_SINGLE_TESTED_BACKEND = import.meta.env.VITE_FIREBOX_AVAIL_SINGLE_TESTED_BACKEND;
export const VITE_FIREBOX_AVAIL_DOOR15A_CATALOG_BACKEND = import.meta.env.VITE_FIREBOX_AVAIL_DOOR15A_CATALOG_BACKEND;

// Note: We no longer export DEV and PROD booleans
// These Vite flags don't support custom modes like "staging"
// Mode checking should be done in Scala using the BuildMode enum
