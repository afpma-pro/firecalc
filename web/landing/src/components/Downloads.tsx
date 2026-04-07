/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import {useTranslations} from 'next-intl';

export default function Downloads() {
  const t = useTranslations('downloads');

  const platforms = [
    {
      name: 'online',
      icon: '🌐',
      ariaLabel: 'Online web application'
    },
    {
      name: 'windows',
      icon: '🪟',
      ariaLabel: 'Windows desktop application'
    },
    {
      name: 'macos',
      icon: '🍎',
      ariaLabel: 'macOS desktop application'
    },
    {
      name: 'linux',
      icon: '🐧',
      ariaLabel: 'Linux desktop application'
    }
  ];

  return (
    <section id="downloads" className="py-20 bg-gradient-to-br from-firecalc-orange-light to-firecalc-yellow">
      <div className="container mx-auto px-4">
        {/* Section Title */}
        <h2 className="text-4xl md:text-5xl font-bold text-center mb-4 text-firecalc-brown-dark">
          {t('title')}
        </h2>
        <p className="text-center text-firecalc-brown-deep text-lg mb-12">
          {t('subtitle')}
        </p>

        {/* Platform Cards Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 max-w-6xl mx-auto">
          {platforms.map((platform, index) => (
            <div
              key={index}
              className="download-card"
              role="article"
              aria-label={platform.ariaLabel}
            >
              {/* Platform Icon */}
              <div className="text-6xl mb-4" aria-hidden="true">
                {platform.icon}
              </div>

              {/* Platform Name */}
              <h3 className="text-xl font-bold mb-4 text-firecalc-brown-dark">
                {t(platform.name)}
              </h3>

              {/* Status Badge */}
              {platform.name === 'online' ? (
                <div className="inline-flex items-center gap-2 bg-firecalc-orange text-white px-4 py-2 rounded-full font-semibold text-sm">
                  <span>{t('beta_available')}</span>
                </div>
              ) : (
                <div className="inline-block bg-firecalc-yellow text-firecalc-brown-dark px-4 py-2 rounded-full font-semibold text-sm">
                  {t('coming_soon')}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* GitHub Link */}
        <div className="text-center mt-12">
          <a
            href="https://github.com/afpma-pro/firecalc"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 text-firecalc-brown-dark hover:text-firecalc-red transition-colors duration-300"
            aria-label="View source code on GitHub"
          >
            <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path fillRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clipRule="evenodd" />
            </svg>
            <span className="font-semibold">{t('view_on_github')}</span>
          </a>
        </div>
      </div>
    </section>
  );
}