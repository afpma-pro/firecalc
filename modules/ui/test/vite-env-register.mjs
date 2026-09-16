/**
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

// Preload module for the Node.js test runtime (see `Test / jsEnv` in
// build.sbt): registers the ESM resolve hook in vite-env-loader.mjs before
// the test bundle is loaded.
import { register } from "node:module";

register(new URL("./vite-env-loader.mjs", import.meta.url));
