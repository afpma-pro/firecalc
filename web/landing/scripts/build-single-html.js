/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
const fs = require('fs');
const path = require('path');
const { inlineSource } = require('inline-source');

/**
 * Build self-contained HTML files from Next.js static export
 * Uses inline-source to properly inline CSS and JavaScript
 */

async function buildSingleHTML() {
  console.log('🚀 Building self-contained HTML files...');
  
  const outDir = path.join(__dirname, '../out');
  const pages = [
    { path: 'index.html', name: 'root' },
    { path: 'en/index.html', name: 'en' },
    { path: 'fr/index.html', name: 'fr' }
  ];
  
  if (!fs.existsSync(outDir)) {
    console.error('❌ Error: out directory not found. Please run "npm run build" first.');
    process.exit(1);
  }

  for (const page of pages) {
    try {
      const htmlPath = path.join(outDir, page.path);
      
      if (!fs.existsSync(htmlPath)) {
        console.warn(`⚠️  Warning: ${page.path} not found, skipping...`);
        continue;
      }

      console.log(`\n📄 Processing ${page.path}...`);
      
      let html = fs.readFileSync(htmlPath, 'utf-8');
      
      // Add inline attributes to all link and script tags
      html = html.replace(/<link([^>]+)rel=["']stylesheet["']([^>]*)>/g, '<link$1rel="stylesheet"$2 inline>');
      html = html.replace(/<script([^>]+)src=["']([^"']+)["']([^>]*)><\/script>/g, '<script$1src="$2"$3 inline></script>');
      
      // Process with inline-source
      const inlinedHtml = await inlineSource(html, {
        rootpath: outDir,
        compress: false,
        ignore: ['png', 'jpg', 'jpeg', 'gif', 'svg'], // Don't inline images
      });
      
      // Write self-contained HTML
      fs.writeFileSync(htmlPath, inlinedHtml);
      
      console.log(`✨ Created self-contained HTML: ${page.path}`);
      
      // Calculate file size
      const stats = fs.statSync(htmlPath);
      const fileSizeInKB = (stats.size / 1024).toFixed(2);
      const fileSizeInMB = (stats.size / 1024 / 1024).toFixed(2);
      console.log(`📦 File size: ${fileSizeInKB} KB (${fileSizeInMB} MB)`);
      
    } catch (error) {
      console.error(`❌ Error processing ${page.name}:`, error.message);
      console.error(error.stack);
    }
  }
  
  console.log('\n✅ Build complete!');
}

buildSingleHTML().catch(console.error);