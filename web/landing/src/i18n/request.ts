/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import {getRequestConfig} from 'next-intl/server';

export default getRequestConfig(async ({requestLocale}) => {
  // Wait for the locale
  const locale = await requestLocale;
  
  // Validate locale
  if (!locale || !['en', 'fr'].includes(locale)) {
    return {
      locale: 'fr',
      messages: (await import(`../messages/fr.json`)).default
    };
  }

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default
  };
});