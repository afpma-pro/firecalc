/**
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

// Vite environment variables wrapper — the single source of truth for the
// 21 env exports (20 VITE_* vars + MODE) consumed by the `ViteEnv` facade
// via `@JSImport("/src/main/js/vite-env.js")`.
//
// Browser (Vite dev server and build): `import.meta.env` is always defined
// and populated from the .env files of the current mode, so each export is
// exactly the value Vite injects; a var that is not set in that mode is
// exported as `undefined`.
//
// Node (Scala.js test bundle): `import.meta.env` does not exist, so the
// exports fall back to the Node-test-only defaults below:
//   - MODE -> "development"
//   - VITE_FIREBOX_REMOTE_KINDS -> "ecolabeled,door15a-catalog"
//   - VITE_FIREBOX_CALC_BASE_URL -> "http://test.local/v1"
// (the two remote-calc defaults keep the facade-level test assertions that
// rely on a resolved remote configuration valid). Every other var is
// absent, i.e. exported as `undefined` — never `null`. The fallback can
// never apply in the browser: Vite always defines `import.meta.env`, so the
// guard below is always true there.
const env = (typeof import.meta !== "undefined" && import.meta.env) || {
    MODE: "development",
    VITE_FIREBOX_REMOTE_KINDS: "ecolabeled,door15a-catalog",
    VITE_FIREBOX_CALC_BASE_URL: "http://test.local/v1",
};

export const VITE_BACKEND_PROTOCOL = env.VITE_BACKEND_PROTOCOL;
export const VITE_BACKEND_HOST = env.VITE_BACKEND_HOST;
export const VITE_BACKEND_PORT = env.VITE_BACKEND_PORT;
export const VITE_BACKEND_BASE_PATH = env.VITE_BACKEND_BASE_PATH;

/** URL to Conditions Générales de Vente (CGUV / Terms and Conditions of Sale) */
export const VITE_CGUV_URL = env.VITE_CGUV_URL;

// Only export the MODE string - we handle mode logic in Scala using the BuildMode enum
export const MODE = env.MODE;

// Firebox availability switches (UI controls)
export const VITE_FIREBOX_AVAIL_TRADITIONAL = env.VITE_FIREBOX_AVAIL_TRADITIONAL;
export const VITE_FIREBOX_AVAIL_ECOLABELED = env.VITE_FIREBOX_AVAIL_ECOLABELED;
export const VITE_FIREBOX_AVAIL_AFPMA_PRSE = env.VITE_FIREBOX_AVAIL_AFPMA_PRSE;
export const VITE_FIREBOX_AVAIL_SINGLE_TESTED = env.VITE_FIREBOX_AVAIL_SINGLE_TESTED;
export const VITE_FIREBOX_AVAIL_DOOR15A_CATALOG = env.VITE_FIREBOX_AVAIL_DOOR15A_CATALOG;

// Firebox availability switches (backend enforcement)
export const VITE_FIREBOX_AVAIL_TRADITIONAL_BACKEND = env.VITE_FIREBOX_AVAIL_TRADITIONAL_BACKEND;
export const VITE_FIREBOX_AVAIL_ECOLABELED_BACKEND = env.VITE_FIREBOX_AVAIL_ECOLABELED_BACKEND;
export const VITE_FIREBOX_AVAIL_AFPMA_PRSE_BACKEND = env.VITE_FIREBOX_AVAIL_AFPMA_PRSE_BACKEND;
export const VITE_FIREBOX_AVAIL_SINGLE_TESTED_BACKEND = env.VITE_FIREBOX_AVAIL_SINGLE_TESTED_BACKEND;
export const VITE_FIREBOX_AVAIL_DOOR15A_CATALOG_BACKEND = env.VITE_FIREBOX_AVAIL_DOOR15A_CATALOG_BACKEND;

// Remote firebox calculation configuration
export const VITE_FIREBOX_REMOTE_KINDS = env.VITE_FIREBOX_REMOTE_KINDS;
export const VITE_FIREBOX_CALC_BASE_URL = env.VITE_FIREBOX_CALC_BASE_URL;
export const VITE_FIREBOX_PREFETCH_MAX_RETRIES = env.VITE_FIREBOX_PREFETCH_MAX_RETRIES;
export const VITE_FIREBOX_PREFETCH_BASE_DELAY_MS = env.VITE_FIREBOX_PREFETCH_BASE_DELAY_MS;
export const VITE_FIREBOX_PREFETCH_MAX_DELAY_MS = env.VITE_FIREBOX_PREFETCH_MAX_DELAY_MS;

// Note: We no longer export DEV and PROD booleans
// These Vite flags don't support custom modes like "staging"
// Mode checking should be done in Scala using the BuildMode enum
