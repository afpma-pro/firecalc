/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import type {Metadata} from 'next';
import {NextIntlClientProvider} from 'next-intl';
import {getMessages} from 'next-intl/server';
import {notFound} from 'next/navigation';
import {setRequestLocale} from 'next-intl/server';

const locales = ['en', 'fr'];

export const metadata: Metadata = {
  icons: {
    icon: [
      {url: '/favicon.ico', sizes: 'any'},
      {url: '/icon-192.png', type: 'image/png', sizes: '192x192'},
      {url: '/icon-512.png', type: 'image/png', sizes: '512x512'},
    ],
    apple: [{url: '/apple-touch-icon.png', sizes: '180x180'}],
  },
  manifest: '/manifest.webmanifest',
  themeColor: '#EF662F',
};

export function generateStaticParams() {
  return locales.map((locale) => ({locale}));
}

export default async function LocaleLayout({
  children,
  params
}: {
  children: React.ReactNode;
  params: Promise<{locale: string}>;
}) {
  // Await the params promise
  const {locale} = await params;
  
  // Ensure that the incoming `locale` is valid
  if (!locales.includes(locale as any)) {
    notFound();
  }

  // Enable static rendering
  setRequestLocale(locale);

  // Providing all messages to the client
  // side is the easiest way to get started
  const messages = await getMessages();

  return (
    <html lang={locale}>
      <body>
        <NextIntlClientProvider messages={messages}>
          {children}
        </NextIntlClientProvider>
      </body>
    </html>
  );
}