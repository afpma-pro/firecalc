/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

import { resolve } from 'path'
import { defineConfig, loadEnv } from "vite";
import { viteSingleFile } from 'vite-plugin-singlefile'
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
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
        sourcemap: true,
        outDir: "../../web/dist-app",
        rollupOptions: {
            external: [],
            output: {
                assetFileNames: 'assets/[name].[ext]', // Keep consistent asset paths
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
        // Only use viteSingleFile for production builds (Electron packaging)
        // In dev mode, this plugin defeats HMR and forces full page reloads
        ...(process.env.NODE_ENV === 'production' ? [viteSingleFile()] : []),
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
                
                // 2. Unsafe eval: Only in development (may be needed by Scala.js dev workflow)
                //    Remove in staging/production for better security
                //    Keep 'unsafe-inline' in all modes (required for inline <script> tags in index.html)
                const scriptSrc = mode === 'development'
                    ? `script-src 'self' 'unsafe-inline' 'unsafe-eval'`
                    : `script-src 'self' 'unsafe-inline'`;
                
                // Build the CSP policy
                const cspContent = `default-src 'self'; connect-src 'self' https://1.1.1.1 ${backendUrl}${githubSourceMaps}; ${scriptSrc}; worker-src 'self' blob:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; img-src 'self' file: data: blob:;`;
                
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
        // Fix ScalaJS inline source maps: convert to external references
        // Helps with: https://github.com/scala-js/vite-plugin-scalajs/issues/4 ???
        {
            name: 'scalajs-sourcemap-fix',
            enforce: 'post',
            apply: 'serve',
            configureServer(server) {
                server.middlewares.use((req, res, next) => {
                    // Only intercept ScalaJS fastopt .js files (not .map files)
                    if (req.url && req.url.includes('/firecalc-ui-fastopt/') &&
                        req.url.endsWith('.js') && !req.url.endsWith('.js.map')) {
                        
                        const originalWrite = res.write;
                        const originalEnd = res.end;
                        const chunks = [];
                        
                        res.write = function(chunk) {
                            chunks.push(Buffer.from(chunk));
                            return true;
                        };
                        
                        res.end = function(chunk) {
                            if (chunk) {
                                chunks.push(Buffer.from(chunk));
                            }
                            
                            const body = Buffer.concat(chunks).toString('utf8');
                            
                            // Check if has inline source map
                            if (body.includes('sourceMappingURL=data:application/json;base64,')) {
                                // Extract filename from URL, preserving special characters like $$
                                const urlParts = req.url.split('/');
                                const fileName = decodeURIComponent(urlParts[urlParts.length - 1]);
                                
                                // Replace inline sourcemap with external reference
                                // Use a function to avoid $$ being interpreted as a special replacement pattern
                                const fixed = body.replace(
                                    /\/\/# sourceMappingURL=data:application\/json;base64,[^\s]+$/m,
                                    () => `//# sourceMappingURL=${fileName}.map`
                                );
                                
                                res.setHeader('Content-Length', Buffer.byteLength(fixed));
                                originalEnd.call(res, fixed);
                            } else {
                                originalEnd.call(res, body);
                            }
                        };
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
                resolve(__dirname, './target/scala-3.7.3/firecalc-ui-fastopt'),

                // IMPORTANT: Allow the repo root so Vite can serve any source via /@fs/...
                // This matches the -scalajs-mapSourceURI added in build.sbt
                resolve(__dirname, '../../'),
            ]
        },
        watch: {
          // Watch only generated JS files and source maps for faster change detection
          include: [
            './target/scala-3.7.3/firecalc-ui-fastopt/**/*.js',
            './target/scala-3.7.3/firecalc-ui-fastopt/**/*.js.map',
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
        }
    },

    // Silence esbuild's "missing source files" sourcemap warning in dev.
    // Scala.js sourcemaps may include external stdlib sources (e.g. raw.githubusercontent.com)
    // and absolute file URIs, which esbuild cannot resolve on disk during its transform step.
    // Browser-side source fetching is handled via the middleware above and Vite's /@fs.
    esbuild: {
        logOverride: {
            'missing source files': 'silent',
        },
    },
}});
