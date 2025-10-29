/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

import { app, BrowserWindow, ipcMain, dialog, Menu, session, protocol } from 'electron';
import updaterPkg from 'electron-updater';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs/promises';
import {
  readFileSync,
  existsSync,
  readdirSync,
  mkdtempSync,
  copyFileSync,
  rmSync
} from 'fs';
import { spawn, execFileSync } from 'child_process';
import os from 'os';
import crypto from 'crypto';
import {
  GITHUB_REPO_OWNER,
  GITHUB_REPO_NAME,
  BACKEND_API_DEV,
  BACKEND_API_STAGING,
  BACKEND_API_PRODUCTION
} from '../../generated-constants.js';

const { autoUpdater } = updaterPkg;

// --- Relaunch logic for Debian/GNOME/Wayland ---
function shouldRelaunchForDebianGnomeWayland() {
  if (process.platform !== 'linux' || process.env.AFPMA_RELAUNCHED) {
    return false;
  }
  try {
    const osRelease = readFileSync('/etc/os-release', 'utf-8');
    const isDebian = osRelease.includes('ID=debian');
    const is10or11 =
      osRelease.includes('VERSION_ID="11"') || osRelease.includes('VERSION_ID="10"');
    if (!isDebian || !is10or11) return false;

    const isGnome = (process.env.XDG_CURRENT_DESKTOP || '').toUpperCase().includes('GNOME');
    const isWayland = (process.env.XDG_SESSION_TYPE || '').toUpperCase().includes('WAYLAND');
    return isGnome && isWayland;
  } catch {
    return false;
  }
}
function cleanupTempSchemaDir(dir) {
  if (dir && dir.includes('firecalc-schemas-')) {
    try {
      rmSync(dir, { recursive: true, force: true });
    } catch (e) {
      console.error(`Failed to clean up temp schema directory: ${dir}`, e);
    }
  }
}
if (shouldRelaunchForDebianGnomeWayland()) {
  const newEnv = {
    ...process.env,
    AFPMA_RELAUNCHED: 'true',
    ELECTRON_OZONE_PLATFORM_HINT: 'x11',
    GDK_BACKEND: 'x11',
    XDG_SESSION_TYPE: 'x11'
  };

  const child = spawn(process.execPath, process.argv.slice(1), {
    env: newEnv,
    detached: true,
    stdio: 'inherit'
  });
  child.unref();
  app.quit();
} else {
  // NORMAL APP STARTUP
  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);

  // Enable verbose logging to diagnose startup issues
  process.env.ELECTRON_ENABLE_LOGGING = process.env.ELECTRON_ENABLE_LOGGING || 'true';
  app.commandLine.appendSwitch('enable-logging');
  app.commandLine.appendSwitch('v', '1');
  
  // Disable Autofill feature to prevent benign DevTools errors in the console
  app.commandLine.appendSwitch('disable-features', 'Autofill');

  const logDebug = (...args) => {
    try {
      console.log('[firecalc][startup]', ...args);
    } catch {}
  };

  logDebug('node/electron/chromium versions', process.versions);
  logDebug('app.isPackaged', app.isPackaged);
  if (process.env.AFPMA_RELAUNCHED) {
    logDebug('App was relaunched to force X11.');
    logDebug('XDG_SESSION_TYPE', process.env.XDG_SESSION_TYPE);
    logDebug('GDK_BACKEND', process.env.GDK_BACKEND);
    logDebug('ELECTRON_OZONE_PLATFORM_HINT', process.env.ELECTRON_OZONE_PLATFORM_HINT);
  }

  // Global crash diagnostics
  process.on('uncaughtException', (err) => {
    logDebug('uncaughtException', (err && (err.stack || err.message)) || String(err));
  });
  process.on('unhandledRejection', (reason) => {
    const msg = (reason && (reason.stack || reason.message)) || String(reason);
    logDebug('unhandledRejection', msg);
  });
  app.on('child-process-gone', (event, details) => {
    logDebug('child-process-gone', details);
  });

  // Read app version from package.json (not Electron version)
  const packageJsonPath = path.join(__dirname, '../../package.json');
  const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf-8'));
  const APP_VERSION = packageJson.version;

  // Linux runtime environment: fontconfig and display backend
  const isLinux = process.platform === 'linux';

  // Packaged resources base (works in dev and prod)
  const resourcesBase = app.isPackaged
    ? path.join(process.resourcesPath, 'app', 'resources')
    : path.join(__dirname, '../resources');

  logDebug('resourcesBase', resourcesBase);
  // 1) Force robust font rendering via bundled fontconfig if present
  const fontconfigDir = path.join(resourcesBase, 'fontconfig');
  const fontconfigFile = path.join(fontconfigDir, 'fonts.conf');

  try {
    if (isLinux && existsSync(fontconfigFile)) {
      process.env.FONTCONFIG_PATH = fontconfigDir;
      process.env.FONTCONFIG_FILE = fontconfigFile;
      logDebug('fontconfig enabled', {
        FONTCONFIG_PATH: process.env.FONTCONFIG_PATH,
        FONTCONFIG_FILE: process.env.FONTCONFIG_FILE
      });
    } else {
      logDebug('fontconfig not found', fontconfigFile);
    }
  } catch (e) {
    logDebug('fontconfig setup error', e && e.message ? e.message : e);
  }

  // 2) Set GSETTINGS_SCHEMA_DIR, compiling schemas at runtime if needed
  let tempSchemaDir = null;
  try {
    if (isLinux) {
      const gsettingsDir = path.join(resourcesBase, 'gsettings');
      const bundledCompiled = path.join(gsettingsDir, 'gschemas.compiled');

      if (existsSync(bundledCompiled)) {
        process.env.GSETTINGS_SCHEMA_DIR = gsettingsDir;
        logDebug('GSETTINGS_SCHEMA_DIR set to bundled compiled schemas', gsettingsDir);
      } else {
        logDebug('Bundled gschemas.compiled not found. Attempting runtime compilation.');
        try {
          const schemaSourceDir = path.join(gsettingsDir, 'schemas');
          if (existsSync(schemaSourceDir)) {
            const schemaFiles = readdirSync(schemaSourceDir).filter((f) =>
              f.endsWith('.gschema.xml')
            );

            if (schemaFiles.length > 0) {
              tempSchemaDir = mkdtempSync(path.join(os.tmpdir(), 'firecalc-schemas-'));
              logDebug(`Created temp schema dir: ${tempSchemaDir}`);

              for (const file of schemaFiles) {
                copyFileSync(
                  path.join(schemaSourceDir, file),
                  path.join(tempSchemaDir, file)
                );
              }
              logDebug(`Copied ${schemaFiles.length} schema files to temp dir.`);

              execFileSync('glib-compile-schemas', [tempSchemaDir]);
              logDebug('Successfully ran glib-compile-schemas.');

              if (existsSync(path.join(tempSchemaDir, 'gschemas.compiled'))) {
                process.env.GSETTINGS_SCHEMA_DIR = tempSchemaDir;
                logDebug(
                  'GSETTINGS_SCHEMA_DIR set to runtime-compiled schemas:',
                  tempSchemaDir
                );
              } else {
                logDebug('Schema compilation did not produce gschemas.compiled.');
                cleanupTempSchemaDir(tempSchemaDir);
                tempSchemaDir = null;
              }
            } else {
              logDebug('No .gschema.xml files found in resources to compile.');
            }
          } else {
            logDebug('Schema source directory not found:', schemaSourceDir);
          }
        } catch (compileError) {
          logDebug('Failed to compile schemas at runtime:', compileError.message);
          cleanupTempSchemaDir(tempSchemaDir);
          tempSchemaDir = null;
        }
      }

      // Final fallback to system schemas
      if (!process.env.GSETTINGS_SCHEMA_DIR) {
        const sysSchemasDir = '/usr/share/glib-2.0/schemas';
        if (existsSync(path.join(sysSchemasDir, 'gschemas.compiled'))) {
          process.env.GSETTINGS_SCHEMA_DIR = sysSchemasDir;
          logDebug('GSETTINGS_SCHEMA_DIR falling back to system schemas', sysSchemasDir);
        } else {
          logDebug('System schemas not found, GSETTINGS_SCHEMA_DIR remains unset.');
        }
      }
    }
  } catch (e) {
    logDebug('gsettings setup error', e && e.message ? e.message : e);
  }

  let mainWindow = null;

  // Check for environment marker files created during packaging
  const isDevBuild = (() => {
    if (!app.isPackaged) return true;
    if (process.env.ELECTRON_IS_DEV === 'true') return true;

    try {
      const markerPath = path.join(process.resourcesPath, '.dev-build');
      return readFileSync(markerPath, 'utf-8').trim() === 'true';
    } catch {
      return false;
    }
  })();

  const isDev = isDevBuild;
  const useViteDevServer = process.env.VITE_DEV_SERVER === 'true';
  
  // Determine build environment from .build-env marker file (created during packaging)
  const buildEnv = (() => {
    try {
      const buildEnvPath = app.isPackaged
        ? path.join(process.resourcesPath, '.build-env')
        : path.join(__dirname, '../../.build-env');
      
      logDebug('Looking for .build-env at:', buildEnvPath);
      logDebug('.build-env exists?', existsSync(buildEnvPath));
      
      if (existsSync(buildEnvPath)) {
        const env = readFileSync(buildEnvPath, 'utf-8').trim();
        logDebug('Build environment from .build-env:', env);
        logDebug('Build environment length:', env.length);
        return env;
      } else {
        logDebug('.build-env file not found at expected path');
      }
    } catch (e) {
      logDebug('.build-env read error:', e.message || e);
    }
    logDebug('Falling back to dev environment');
    return 'dev';
  })();
  
  const isStaging = buildEnv === 'staging';
  const isProduction = buildEnv === 'production';
  
  logDebug('Environment detection:', { buildEnv, isStaging, isProduction, isDev });
  logDebug('Backend URLs:', { BACKEND_API_DEV, BACKEND_API_STAGING, BACKEND_API_PRODUCTION });

  // Configure auto-updater for MANUAL updates only (no code signing)
  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = false;
  
  // Configure GitHub releases provider
  if (!isDev) {
    try {
      autoUpdater.setFeedURL({
        provider: 'github',
        owner: GITHUB_REPO_OWNER,
        repo: GITHUB_REPO_NAME,
        private: false
      });
    } catch (error) {
      console.error('Failed to configure auto-updater feed:', error);
    }
  }

  function createWindow() {
    mainWindow = new BrowserWindow({
      width: 1024,
      height: 768,
      minWidth: 800,
      minHeight: 600,
      webPreferences: {
        preload: path.join(__dirname, 'preload.cjs'),
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true,
        webSecurity: true
      },
      show: false
    });

    if (useViteDevServer) {
      mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL || 'http://localhost:5173');
    } else {
      const appPath = isDev
        ? path.join(__dirname, '../../dist-app/index.html')
        : path.join(process.resourcesPath, 'app/dist-app/index.html');
      mainWindow.loadFile(appPath);
    }

    mainWindow.once('ready-to-show', () => {
      mainWindow.maximize();
      mainWindow.show();
      // DevTools are available via Developer menu (View → Developer → Toggle DevTools)
      // or F12 shortcut, but do not auto-open on startup
    });

    createMenu();

    if (mainWindow && mainWindow.webContents) {
      mainWindow.webContents.on('render-process-gone', (event, details) => {
        logDebug('render-process-gone', details);
      });
    }

    mainWindow.on('closed', () => {
      mainWindow = null;
    });
  }

  function createMenu() {
    const template = [
      {
        label: 'File',
        submenu: [
          {
            label: 'Open...',
            accelerator: 'CmdOrCtrl+O',
            click: async () => {
              const result = await dialog.showOpenDialog(mainWindow, {
                properties: ['openFile'],
                filters: [
                  { name: 'FireCalc Project', extensions: ['yaml', 'firecalc.yaml'] },
                  { name: 'All Files', extensions: ['*'] }
                ]
              });
              if (!result.canceled && result.filePaths.length > 0) {
                mainWindow.webContents.send('file-opened', result.filePaths[0]);
              }
            }
          },
          {
            label: 'Save As...',
            accelerator: 'CmdOrCtrl+S',
            click: async () => {
              const result = await dialog.showSaveDialog(mainWindow, {
                defaultPath: 'project.firecalc.yaml',
                filters: [
                  { name: 'FireCalc Project', extensions: ['yaml', 'firecalc.yaml'] },
                  { name: 'All Files', extensions: ['*'] }
                ]
              });
              if (!result.canceled && result.filePath) {
                mainWindow.webContents.send('file-save-as', result.filePath);
              }
            }
          },
          { type: 'separator' },
          { role: 'quit' }
        ]
      },
      {
        label: 'Edit',
        submenu: [
          { role: 'undo' },
          { role: 'redo' },
          { type: 'separator' },
          { role: 'cut' },
          { role: 'copy' },
          { role: 'paste' },
          { role: 'selectAll' }
        ]
      },
      {
        label: 'View',
        submenu: [
          { role: 'reload' },
          { role: 'forceReload' },
          { type: 'separator' },
          { role: 'resetZoom' },
          { role: 'zoomIn' },
          { role: 'zoomOut' },
          { type: 'separator' },
          { role: 'togglefullscreen' }
        ]
      },
      {
        label: 'Help',
        submenu: [
          { label: 'Check for Updates', click: () => checkForUpdates() },
          {
            label: 'About',
            click: () => {
              dialog.showMessageBox(mainWindow, {
                type: 'info',
                title: 'About',
                message: 'FireCalc AFPMA',
                detail: `Version: ${APP_VERSION}\n\nDimensioning software for one‑off masonry heaters (EN 15544, EN 13384).`,
                buttons: ['OK']
              });
            }
          }
        ]
      }
    ];

    if (isDev) {
      template.push({
        label: 'Developer',
        submenu: [
          {
            role: 'toggleDevTools',
            accelerator: 'F12'
          },
          { type: 'separator' },
          {
            label: 'Reload',
            accelerator: 'F5',
            click: () => mainWindow.reload()
          }
        ]
      });
    }

    const menu = Menu.buildFromTemplate(template);
    Menu.setApplicationMenu(menu);
  }

  function setupAutoUpdater() {
    if (isDev) {
      console.log('Auto-updater disabled in development mode');
      return;
    }
    autoUpdater.on('checking-for-update', () => {
      console.log('Checking for updates...');
    });
    
    autoUpdater.on('update-available', (info) => {
      const releaseUrl = `https://github.com/${GITHUB_REPO_OWNER}/${GITHUB_REPO_NAME}/releases/tag/v${info.version}`;
      dialog
        .showMessageBox(mainWindow, {
          type: 'info',
          title: 'Update Available',
          message: `A new version ${info.version} is available!`,
          detail: `Current version: ${APP_VERSION}\n\n` +
                  `Please download the update from GitHub:\n${releaseUrl}\n\n` +
                  `⚠️ Note: This app is unsigned. You may see security warnings during installation.\n\n` +
                  `Platform-specific instructions:\n` +
                  `• Windows: Click "More info" → "Run anyway"\n` +
                  `• macOS: Right-click app → "Open" → Confirm\n` +
                  `• Linux: chmod +x *.AppImage`,
          buttons: ['Open Download Page', 'Remind Me Later']
        })
        .then((result) => {
          if (result.response === 0) {
            // Open GitHub release page in browser
            import('electron').then(({ shell }) => {
              shell.openExternal(releaseUrl);
            });
          }
        });
    });
    
    autoUpdater.on('update-not-available', (info) => {
      console.log('App is up to date:', info.version);
    });
    
    autoUpdater.on('error', (error) => {
      console.error('Auto-updater error:', error);
      // Don't show error dialog for update checks - just log it
    });
  }

  function checkForUpdates() {
    if (isDev) {
      dialog.showMessageBox(mainWindow, {
        type: 'info',
        title: 'Updates',
        message: 'Auto-updates are disabled in development mode',
        detail: `Current version: ${APP_VERSION}\n\nTo test updates, build a production version.`,
        buttons: ['OK']
      });
      return;
    }
    
    // Check for updates and handle the response
    autoUpdater.checkForUpdates()
      .then(result => {
        if (result && result.updateInfo && result.updateInfo.version === APP_VERSION) {
          // Show "up to date" message only when manually triggered
          dialog.showMessageBox(mainWindow, {
            type: 'info',
            title: 'No Updates Available',
            message: 'You are using the latest version',
            detail: `Current version: ${APP_VERSION}`,
            buttons: ['OK']
          });
        }
      })
      .catch(error => {
        console.error('Update check failed:', error);
        dialog.showMessageBox(mainWindow, {
          type: 'error',
          title: 'Update Check Failed',
          message: 'Failed to check for updates',
          detail: `Error: ${error.message}\n\nPlease check your internet connection or visit:\nhttps://github.com/${GITHUB_REPO_OWNER}/${GITHUB_REPO_NAME}/releases`,
          buttons: ['OK']
        });
      });
  }

  // Security: Validate file paths to allowed directories only
  const ALLOWED_DIRS = [
    app.getPath('documents'),
    app.getPath('downloads'),
    app.getPath('userData'),
    app.getPath('desktop')
  ];

  function isPathAllowed(filePath) {
    const resolved = path.resolve(filePath);
    return ALLOWED_DIRS.some(dir => resolved.startsWith(path.resolve(dir)));
  }

  ipcMain.handle('read-file', async (event, filePath) => {
    try {
      if (!isPathAllowed(filePath)) {
        return { success: false, error: 'Access denied: Path not in allowed directories' };
      }
      const content = await fs.readFile(filePath, 'utf-8');
      return { success: true, content };
    } catch (error) {
      return { success: false, error: error.message };
    }
  });
  ipcMain.handle('write-file', async (event, filePath, content) => {
    try {
      if (!isPathAllowed(filePath)) {
        return { success: false, error: 'Access denied: Path not in allowed directories' };
      }
      await fs.writeFile(filePath, content, 'utf-8');
      return { success: true };
    } catch (error) {
      return { success: false, error: error.message };
    }
  });
  ipcMain.handle('list-dir', async (event, dirPath) => {
    try {
      if (!isPathAllowed(dirPath)) {
        return { success: false, error: 'Access denied: Path not in allowed directories' };
      }
      const files = await fs.readdir(dirPath);
      return { success: true, files };
    } catch (error) {
      return { success: false, error: error.message };
    }
  });
  ipcMain.handle('create-dir', async (event, dirPath) => {
    try {
      if (!isPathAllowed(dirPath)) {
        return { success: false, error: 'Access denied: Path not in allowed directories' };
      }
      await fs.mkdir(dirPath, { recursive: true });
      return { success: true };
    } catch (error) {
      return { success: false, error: error.message };
    }
  });
  ipcMain.handle('open-file-dialog', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      properties: ['openFile'],
      filters: [
        { name: 'FireCalc Project', extensions: ['yaml', 'firecalc.yaml'] },
        { name: 'All Files', extensions: ['*'] }
      ]
    });
    if (result.canceled) return { success: false, canceled: true };
    return { success: true, path: result.filePaths[0] };
  });
  ipcMain.handle('save-file-dialog', async (event, defaultFileName) => {
    const result = await dialog.showSaveDialog(mainWindow, {
      defaultPath: defaultFileName || 'project.firecalc.yaml',
      filters: [
        { name: 'FireCalc Project', extensions: ['yaml', 'firecalc.yaml'] },
        { name: 'All Files', extensions: ['*'] }
      ]
    });
    if (result.canceled) return { success: false, canceled: true };
    return { success: true, path: result.filePath };
  });
  ipcMain.handle('get-app-version', () => APP_VERSION);
  ipcMain.handle('check-for-updates', () => {
    checkForUpdates();
    return { success: true };
  });

  app.whenReady().then(() => {
    // Security: Set explicit permission handlers (deny by default)
    session.defaultSession.setPermissionRequestHandler((webContents, permission, callback) => {
      logDebug(`Permission request: ${permission} - DENIED by default`);
      callback(false); // Deny all permissions by default
    });

    session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
      // Tightened CSP for production, relaxed for dev mode
      const cspDirectives = [
        "default-src 'self'",
        // Dev mode: Allow inline scripts and eval for Vite HMR and config
        // Production: Only allow scripts from self
        isDev
          ? "script-src 'self' 'unsafe-inline' 'unsafe-eval'"
          : "script-src 'self'",
        // Allow styles from self only (no external fonts)
        "style-src 'self' 'unsafe-inline'", // Tailwind requires inline styles
        // Allow fonts from self only
        "font-src 'self' data:",
        // Allow images from various sources
        "img-src 'self' file: data: blob:",
        // Allow connections based on build environment:
        // BUILD_ENV takes precedence over dev build marker
        // - Dev: localhost for Vite HMR and local backend from config
        // - Staging: staging API from config
        // - Production: production API from config
        // - Always: 1.1.1.1 for internet connectivity check
        (() => {
          const connectSources = ["'self'", "https://1.1.1.1"];
          
          // Check BUILD_ENV first (set during packaging), then fall back to isDev
          if (isStaging) {
            if (BACKEND_API_STAGING) connectSources.push(BACKEND_API_STAGING);
          } else if (isProduction) {
            if (BACKEND_API_PRODUCTION) connectSources.push(BACKEND_API_PRODUCTION);
          } else {
            // Dev mode or unpackaged
            connectSources.push('http://localhost:*', 'ws://localhost:*', 'wss://localhost:*');
            if (BACKEND_API_DEV) connectSources.push(BACKEND_API_DEV);
          }
          
          return `connect-src ${connectSources.join(' ')}`;
        })()
      ];

      const csp = cspDirectives.join('; ');

      callback({
        responseHeaders: {
          ...details.responseHeaders,
          'Content-Security-Policy': [csp]
        }
      });
    });
    
    // Security: Register firecalcafpma:// protocol with path validation
    protocol.handle('firecalcafpma', (request) => {
      const url = new URL(request.url);
      // Security: Validate and sanitize pathname to prevent path traversal
      const sanitizedPath = path.normalize(url.pathname).replace(/^(\.\.[\/\\])+/, '');
      const filePath = path.join(app.getAppPath(), 'dist-app', sanitizedPath);
      
      // Ensure the resolved path is still within dist-app directory
      const appPath = path.join(app.getAppPath(), 'dist-app');
      if (!filePath.startsWith(appPath)) {
        return new Response('Access denied', { status: 403 });
      }
      
      return new Response(new Blob([], { type: 'text/plain' }), {
        status: 301,
        headers: { Location: `file://${filePath}` }
      });
    });

    createWindow();
    setupAutoUpdater();

    if (!isDev) {
      setTimeout(() => autoUpdater.checkForUpdates(), 3000);
    }

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
      }
    });
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
      app.quit();
    }
  });
  app.on('quit', () => {
    cleanupTempSchemaDir(tempSchemaDir);
  });
}
