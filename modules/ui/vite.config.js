/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

import { resolve } from 'path'
import { defineConfig, loadEnv } from "vite";

import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
import basicSsl from '@vitejs/plugin-basic-ssl';
import tailwindcss from '@tailwindcss/vite';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
    // Load environment variables for the current mode
    const env = loadEnv(mode, process.cwd(), '');
    
    // Build the backend URL from environment variables
    const backendProtocol = env.VITE_BACKEND_PROTOCOL || 'http';
    const backendHost = env.VITE_BACKEND_HOST || 'localhost';
    const backendPort = env.VITE_BACKEND_PORT || '8181';
    
    // Construct the full backend URL for CSP
    const backendUrl = `${backendProtocol}://${backendHost}${backendPort && backendPort !== '443' && backendPort !== '80' ? ':' + backendPort : ''}`;
    
    console.log(`[Vite Config] Mode: ${mode}, Backend URL for CSP: ${backendUrl}`);
    
    return {
    base: "./",
    publicDir: "../../public",
    build: {
        cssCodeSplit: false,
        assetsInlineLimit: 0, // Don't inline assets, keep them as separate files
        // target: ["node*"], // see https://electron-vite.org/config/
        sourcemap: mode === 'development',
        outDir: "../../web/dist-app/app",
        rollupOptions: {
            external: [],
            output: {
                assetFileNames: 'assets/[name]-[hash].[ext]',
                // Optimize for large chunks (fullLinkJS output can be large)
                manualChunks: undefined,
                inlineDynamicImports: true,
            },
            // Increase memory limit for rollup when processing large Scala.js bundles
            maxParallelFileOps: 20,
        },
        // Disable minification terser optimizations that consume too much memory
        minify: 'esbuild',
        // Increase chunk size warning limit (fullLinkJS can produce large files)
        chunkSizeWarningLimit: 10000,
    },
    optimizeDeps: {
        // Prevent Vite dep-scan from trying to resolve the virtual Scala.js entry
        exclude: ['scalajs:main.js'],
    },
    plugins: [
        // Resolve 'three' and 'three/addons/*' imports originating from the aliased
        // filaire-viz.js (which lives outside this project root at modules/viz/...).
        // Without this, vite:import-analysis cannot find 'three' because node_modules
        // is only at modules/ui/, not on the ancestor path of modules/viz/.
        {
            name: 'resolve-filaire-three',
            resolveId(id, importer) {
                if (importer && importer.includes('filaire-viz') &&
                    (id === 'three' || id.startsWith('three/'))) {
                    return this.resolve(id, resolve(__dirname, './package.json'));
                }
            }
        },
        {
            name: 'resolve-graph-chartjs',
            resolveId(id, importer) {
                if (importer && importer.includes('graph-viz') &&
                    (id === 'chart.js' || id.startsWith('chart.js/'))) {
                    return this.resolve(id, resolve(__dirname, './package.json'));
                }
            }
        },
        // Enable HTTPS + HTTP/2 in dev for faster loading of many small Scala.js modules
        ...(mode === 'development' ? [basicSsl()] : []),
        scalaJSPlugin({
            cwd: '../../',
            projectID: 'ui',
        }),
        tailwindcss(),
        // Transform index.html to inject environment-specific CSP
        {
            name: 'inject-csp',
            transformIndexHtml(html) {
                // Environment-specific CSP directives
                
                // 1. GitHub source maps: Only in development for Scala.js debugging
                const githubSourceMaps = mode === 'development' ? ' https://raw.githubusercontent.com' : '';
                
                // 2. Unsafe eval/inline: Only in development (needed by Vite HMR + Scala.js dev workflow)
                //    Production has no inline <script> tags, so 'unsafe-inline' is not needed.
                const scriptSrc = mode === 'development'
                    ? `script-src 'self' 'unsafe-inline' 'unsafe-eval'`
                    : `script-src 'self'`;
                
                // 3. Additional hardening directives (production only)
                const hardeningDirectives = mode === 'development'
                    ? ''
                    : " frame-ancestors 'none'; base-uri 'self'; form-action 'self';";

                const cspContent = `default-src 'self'; connect-src 'self' https://1.1.1.1 ${backendUrl}${githubSourceMaps}; ${scriptSrc}; worker-src 'self' blob:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; img-src 'self' file: data: blob:;${hardeningDirectives}`;
                
                console.log(`[CSP] ${mode} mode - Backend: ${backendUrl}`);
                console.log(`[CSP] ${mode} mode - GitHub: ${githubSourceMaps ? 'enabled' : 'disabled'}`);
                console.log(`[CSP] ${mode} mode - Script: ${scriptSrc}`);
                
                return html.replace(
                    /<meta http-equiv="Content-Security-Policy"[^>]*content="[^"]*"[^>]*>/,
                    `<meta http-equiv="Content-Security-Policy" content="${cspContent}">`
                );
            }
        },
        // Dev-only middleware: rewrite absolute FS paths to Vite's /@fs so DevTools can fetch .scala sources.
        // Avoid Node's require in ESM Vite config to prevent "Dynamic require is not supported" errors.
        {
            name: 'rewrite-abs-path-sources',
            enforce: 'pre',
            apply: 'serve',
            configureServer(server) {
                const rewrites = [
                    { match: (url) => url.startsWith('/file:///'), to: (url) => '/@fs/' + url.slice('/file:///'.length) }, // /file:///home/... -> /@fs/home/...
                    { match: (url) => url.startsWith('/home/'),    to: (url) => '/@fs' + url },                             // /home/... -> /@fs/home/...
                    { match: (url) => url.startsWith('/Users/'),   to: (url) => '/@fs' + url },                             // macOS: /Users/... -> /@fs/Users/...
                ];
                server.middlewares.use((req, _res, next) => {
                    if (req.url) {
                        for (const { match, to } of rewrites) {
                            if (match(req.url)) {
                                req.url = to(req.url);
                                break;
                            }
                        }
                    }
                    next();
                });
            },
        },
    ],
    server: {
        port: 5173,
        strictPort: true,
        fs: {
            allow: [
                // Existing UI module paths
                resolve(__dirname, '.'),
                resolve(__dirname, './src/main/scala'),
                resolve(__dirname, './target/scala-3.8.2/firecalc-ui-fastopt'),

                // IMPORTANT: Allow the repo root so Vite can serve any source via /@fs/...
                // This matches the -scalajs-mapSourceURI added in build.sbt
                resolve(__dirname, '../../'),
            ]
        },
        watch: {
          // Watch only generated JS files and source maps for faster change detection
          include: [
            './target/scala-3.8.2/firecalc-ui-fastopt/**/*.js',
            './target/scala-3.8.2/firecalc-ui-fastopt/**/*.js.map',
          ],
          ignored: [
            '**/*.scala',              // Ignore source files (handled by sbt)
            '**/node_modules/**',      // Ignore dependencies
            '**/.git/**',              // Ignore git
            '**/target/classes/**',    // Ignore compiled classes
            '**/target/meta/**',       // Ignore sbt metadata
            '**/target/zinc/**',       // Ignore zinc cache
            '**/target/sync/**',       // Ignore sync artifacts
          ]
        }
      },
    resolve: {
        alias: {
            'firecalc-ui': resolve(__dirname, './firecalc-ui.js'),
            // In dev, Vite compiles TypeScript natively — point directly at the source.
            // For production (fullLinkJS), run `npm run build:viz` to generate the .js resource.
            '/afpma/firecalc/filaire/filaire-viz.js': resolve(
                __dirname, '../viz/src/ts/filaire-viz.ts'
            ),
            '/afpma/firecalc/graph/graph-viz.js': resolve(
                __dirname, '../graph/src/ts/graph-viz.ts'
            ),
        },
    },
}});
