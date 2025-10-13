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

// Only export the MODE string - we handle mode logic in Scala using the BuildMode enum
export const MODE = import.meta.env.MODE;

// Note: We no longer export DEV and PROD booleans
// These Vite flags don't support custom modes like "staging"
// Mode checking should be done in Scala using the BuildMode enum
