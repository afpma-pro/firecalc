/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
'use client';

import {useTranslations} from 'next-intl';
import {useRef} from 'react';

/**
 * Newsletter Component - MailPoet Embedded Form
 *
 * This component uses MailPoet's embedded form feature for static site compatibility.
 * No API keys or serverless functions required.
 *
 * Configuration:
 * - Set MAILPOET_FORM_URL in your environment or update the constant below
 * - The form is managed entirely by MailPoet on your WordPress site
 *
 * Benefits:
 * - Works with GitHub Pages (no server-side code needed)
 * - GDPR compliant (managed by MailPoet)
 * - Easy to set up and maintain
 * - Fully managed subscription handling
 */

// Configuration: Update this URL with your actual MailPoet form URL
// To get this URL:
// 1. Go to MailPoet → Forms in your WordPress admin
// 2. Edit or create a form
// 3. Click "Use form on my page" → Get the iframe URL

// <iframe width="100%" height="100%" scrolling="no" frameborder="0" 
// src="https://www.afpma.pro/wordpress/?mailpoet_form_iframe=4" class="mailpoet_form_iframe" 
// id="mailpoet_form_iframe" vspace="0" tabindex="0" onload="var _this = this; window.addEventListener('message', function(e) {if(e.data.MailPoetIframeHeight){_this.style.height = e.data.MailPoetIframeHeight;}})" 
// marginwidth="0" marginheight="0" hspace="0" allowtransparency="true"></iframe>

const MAILPOET_FORM_URL = process.env.NEXT_PUBLIC_MAILPOET_FORM_URL ||
    'https://www.afpma.pro/wordpress/?mailpoet_form_iframe=4';
                          


export default function Newsletter() {
  const t = useTranslations('newsletter');
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const handleIframeLoad = () => {
    const iframe = iframeRef.current;
    if (!iframe) return;

    window.addEventListener('message', (e) => {
      if (e.data.MailPoetIframeHeight && iframe) {
        iframe.style.height = e.data.MailPoetIframeHeight;
      }
    });
  };

  return (
    <div className="bg-firecalc-brown-dark text-white py-6 px-6 rounded-lg">
      <h3 className="text-3xl font-bold mb-4 text-center">
        {t('title')}
      </h3>
      <p className="text-gray-300 text-center mb-6 max-w-2xl mx-auto">
        {t('description')}
      </p>

      {/* MailPoet Embedded Form */}
      <div className="max-w-md mx-auto">
        <div
          className="mailpoet-form-container rounded-lg overflow-hidden border-2 border-firecalc-orange-light bg-white"
          style={{ minHeight: '150px' }}
              >
                  
                  <iframe
                      ref={iframeRef}
                      width="100%"
                      height="100%"
                      scrolling="no"
                      frameBorder="0"
                      src={MAILPOET_FORM_URL}
                      className="mailpoet_form_iframe mailpoet-iframe"
                      id="mailpoet_form_iframe"
                      onLoad={handleIframeLoad}
                      aria-label="Newsletter subscription form"
                      tabIndex={0}>
                      </iframe>
        </div>

        {/* Fallback message if iframe doesn't load */}
        <noscript>
          <div className="mt-4 p-4 bg-yellow-100 text-yellow-800 rounded-lg text-center">
            Please enable JavaScript to subscribe to our newsletter.
          </div>
        </noscript>
      </div>

      <p className="text-center mt-4 text-sm text-gray-400">
        <a
          href="#"
          className="text-firecalc-yellow hover:underline"
          aria-label={t('privacy')}
        >
          {t('privacy')}
        </a>
      </p>
    </div>
  );
}