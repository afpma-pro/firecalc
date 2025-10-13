/**
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

const setScrollbarWidth = () => {
    const html = document.documentElement;
    const inner = document.createElement('div');
    inner.style.width = '100%';
    inner.style.height = '200px';
    inner.style.position = 'absolute';
    inner.style.top = '-9999px';
    document.body.appendChild(inner);
    const scrollbarWidth = inner.offsetWidth - inner.clientWidth;
    document.body.removeChild(inner);
    html.style.setProperty('--scrollbar-width', scrollbarWidth);
};

setScrollbarWidth();

import './main.css'

import 'scalajs:main.js'