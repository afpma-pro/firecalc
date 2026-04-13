/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import {setRequestLocale} from 'next-intl/server';
import Hero from '@/components/Hero';
import Features from '@/components/Features';
import Downloads from '@/components/Downloads';
import Footer from '@/components/Footer';

const locales = ['en', 'fr'];

export function generateStaticParams() {
  return locales.map((locale) => ({locale}));
}

type Props = {
  params: Promise<{locale: string}>;
};

export default async function Home({params}: Props) {
  // Await the params promise
  const {locale} = await params;
  
  // Enable static rendering
  setRequestLocale(locale);
  
  return (
    <main className="min-h-screen">
      <Hero />
      <Features />
      <Downloads />
      <Footer />
    </main>
  );
}