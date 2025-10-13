/**
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

const { contextBridge, ipcRenderer } = require('electron');

// Expose protected methods that allow the renderer process to use
// ipcRenderer without exposing the entire object
contextBridge.exposeInMainWorld('electronAPI', {
  // File operations
  readFile: (filePath) => ipcRenderer.invoke('read-file', filePath),
  writeFile: (filePath, content) => ipcRenderer.invoke('write-file', filePath, content),
  
  // Directory operations
  listDir: (dirPath) => ipcRenderer.invoke('list-dir', dirPath),
  createDir: (dirPath) => ipcRenderer.invoke('create-dir', dirPath),
  
  // File dialogs
  openFileDialog: () => ipcRenderer.invoke('open-file-dialog'),
  saveFileDialog: (defaultFileName) => ipcRenderer.invoke('save-file-dialog', defaultFileName),
  
  // App information
  getVersion: () => ipcRenderer.invoke('get-app-version'),
  checkForUpdates: () => ipcRenderer.invoke('check-for-updates'),
  
  // Event listeners for menu actions
  onFileOpened: (callback) => {
    const subscription = (event, filePath) => callback(filePath);
    ipcRenderer.on('file-opened', subscription);
    return () => ipcRenderer.removeListener('file-opened', subscription);
  },
  
  onFileSaveAs: (callback) => {
    const subscription = (event, filePath) => callback(filePath);
    ipcRenderer.on('file-save-as', subscription);
    return () => ipcRenderer.removeListener('file-save-as', subscription);
  }
});
