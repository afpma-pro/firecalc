/**
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

// Node.js ESM resolve hook for the Scala.js test runtime.
//
// `Test / jsEnv` in build.sbt launches Node with `--import
// <this dir>/vite-env-register.mjs`, which registers this file as a module
// customization hook before the test bundle is loaded. The emitted test
// bundle imports the Vite env bridge with the same specifier the browser
// build uses — "/src/main/js/vite-env.js", a URL relative to the Vite root
// — which Node cannot resolve (it would look for the file at the
// filesystem root). This hook rewrites that single specifier to the in-repo
// bridge file; every other specifier is passed through to Node's default
// resolution untouched.
//
// The hook is registered only for `sbt ui/test`: it never affects the
// browser (Vite dev server / Rollup build) or any other Node usage.
const BRIDGE_SPECIFIER = "/src/main/js/vite-env.js";
const BRIDGE_URL = new URL("../src/main/js/vite-env.js", import.meta.url).href;

export async function resolve(specifier, context, next) {
    if (specifier === BRIDGE_SPECIFIER) {
        return { url: BRIDGE_URL, shortCircuit: true };
    }
    return next(specifier, context);
}
