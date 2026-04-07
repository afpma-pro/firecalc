/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import {useTranslations} from 'next-intl';
import LanguageSwitcher from './LanguageSwitcher';
import Image from 'next/image';

export default function Hero() {
  const t = useTranslations('hero');

  return (
    <section className="relative min-h-screen bg-gradient-to-br from-firecalc-brown-dark to-firecalc-brown-deep text-white flex items-center">
      {/* Language Switcher */}
      <div className="absolute top-4 right-4 z-10">
        <LanguageSwitcher />
      </div>

      <div className="container mx-auto px-4 py-16 text-center">
        {/* Logo */}
        <div className="mb-8 flex justify-center">
          <Image
            src="/assets/images/logo.png"
            alt="AFPMA Logo"
            width={300}
            height={80}
            className="animate-fade-in"
            priority
          />
        </div>

        {/* Main Heading */}
        <h1 className="text-5xl md:text-6xl lg:text-7xl font-bold mb-4 animate-slide-up">
          {t('title')}
        </h1>

        {/* Subtitle */}
        <h2 className="text-2xl md:text-3xl lg:text-4xl font-semibold mb-6 text-firecalc-orange-light">
          {t('subtitle')}
        </h2>

        {/* Description */}
        <p className="text-lg md:text-xl max-w-3xl mx-auto mb-8 text-gray-200">
          {t('description')}
        </p>

        {/* CTA Buttons */}
        <div className="flex flex-col sm:flex-row gap-4 justify-center items-center mb-8">
          <a
            href="#"
            className="btn-primary-custom w-full sm:w-auto relative"
            aria-label={t('cta_online')}
          >
            {t('cta_online')}
                      <span className="absolute -top-2 -right-2 bg-firecalc-yellow text-firecalc-brown-dark text-xs px-2 py-1 rounded-full font-bold">
              BETA
            </span>
          </a>
          <a
            href="#downloads"
            className="btn-secondary-custom w-full sm:w-auto"
            aria-label={t('cta_download')}
          >
            {t('cta_download')}
          </a>
        </div>

        {/* Version 1 Release Info */}
        <div className="inline-flex items-center gap-2 bg-firecalc-yellow text-firecalc-brown-dark px-6 py-3 rounded-full font-semibold text-sm md:text-base">
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" />
          </svg>
          <span>{t('version_1_release')}</span>
        </div>

        {/* Scroll Indicator */}
        <div className="mt-16 animate-bounce">
          <svg className="w-6 h-6 mx-auto text-firecalc-orange-light" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 14l-7 7m0 0l-7-7m7 7V3" />
          </svg>
        </div>
      </div>
    </section>
  );
}