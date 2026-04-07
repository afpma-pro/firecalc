/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import {useTranslations} from 'next-intl';
import MarkdownText from './MarkdownText';

export default function Features() {
  const t = useTranslations('features');

  const features = [
    {
      icon: (
        <svg className="w-16 h-16 text-firecalc-yellow mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
      ),
      title: 'feature1_title',
      description: 'feature1_desc'
    },
    {
      icon: (
        <svg className="w-16 h-16 text-firecalc-yellow mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4M7.835 4.697a3.42 3.42 0 001.946-.806 3.42 3.42 0 014.438 0 3.42 3.42 0 001.946.806 3.42 3.42 0 013.138 3.138 3.42 3.42 0 00.806 1.946 3.42 3.42 0 010 4.438 3.42 3.42 0 00-.806 1.946 3.42 3.42 0 01-3.138 3.138 3.42 3.42 0 00-1.946.806 3.42 3.42 0 01-4.438 0 3.42 3.42 0 00-1.946-.806 3.42 3.42 0 01-3.138-3.138 3.42 3.42 0 00-.806-1.946 3.42 3.42 0 010-4.438 3.42 3.42 0 00.806-1.946 3.42 3.42 0 013.138-3.138z" />
        </svg>
      ),
      title: 'feature2_title',
      description: 'feature2_desc'
    },
    {
      icon: (
        <svg className="w-16 h-16 text-firecalc-yellow mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
        </svg>
      ),
      title: 'feature3_title',
      description: 'feature3_desc'
    },
    {
      icon: (
        <svg className="w-16 h-16 text-firecalc-yellow mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 104 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
      title: 'feature4_title',
      description: 'feature4_desc'
    },
    {
      icon: (
        <svg className="w-16 h-16 text-firecalc-yellow mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
      title: 'feature5_title',
      description: 'feature5_desc'
    },
    {
      icon: (
        <svg className="w-16 h-16 text-firecalc-yellow mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      ),
      title: 'feature6_title',
      description: 'feature6_desc'
    },
    {
      icon: (
        <svg className="w-16 h-16 text-firecalc-yellow mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
      title: 'feature7_title',
      description: 'feature7_desc'
    }
  ];

  return (
    <section id="features" className="py-20 bg-base-100">
      <div className="container mx-auto px-4">
        {/* Section Title */}
        <h2 className="text-4xl md:text-5xl font-bold text-center mb-12 text-firecalc-yellow">
          {t('title')}
        </h2>

        {/* Features Grid - 3 Rows with Flexbox for Consistent Gaps */}
        <div className="space-y-4 mb-16">
          {/* Row 1: 2 Features */}
          <div className="flex flex-wrap justify-center gap-4">
            {features.slice(0, 2).map((feature, index) => (
              <div
                key={index}
                className="feature-card bg-firecalc-red p-6 shadow-lg rounded-none h-50 w-96 flex flex-col"
                role="article"
                aria-labelledby={`feature-${index}-title`}
              >
                <div className="flex justify-center">
                  {feature.icon}
                </div>
                <h3
                  id={`feature-${index}-title`}
                  className="text-2xl font-extrabold mb-3 text-white text-center uppercase"
                >
                  <MarkdownText text={t(feature.title)} />
                </h3>
                <p className="text-white font-normal text-center">
                  <MarkdownText text={t(feature.description)} />
                </p>
              </div>
            ))}
          </div>

          {/* Row 2: 3 Features */}
          <div className="flex flex-wrap justify-center gap-4">
            {features.slice(2, 5).map((feature, index) => (
              <div
                key={index + 2}
                className="feature-card bg-firecalc-red p-6 shadow-lg rounded-none h-50 w-96 flex flex-col"
                role="article"
                aria-labelledby={`feature-${index + 2}-title`}
              >
                <div className="flex justify-center">
                  {feature.icon}
                </div>
                <h3
                  id={`feature-${index + 2}-title`}
                  className="text-2xl font-extrabold mb-3 text-white text-center uppercase"
                >
                  <MarkdownText text={t(feature.title)} />
                </h3>
                <p className="text-white font-normal text-center">
                  <MarkdownText text={t(feature.description)} />
                </p>
              </div>
            ))}
          </div>

          {/* Row 3: 2 Features */}
          <div className="flex flex-wrap justify-center gap-4">
            {features.slice(5, 7).map((feature, index) => (
              <div
                key={index + 5}
                className="feature-card bg-firecalc-red p-6 shadow-lg rounded-none h-50 w-96 flex flex-col"
                role="article"
                aria-labelledby={`feature-${index + 5}-title`}
              >
                <div className="flex justify-center">
                  {feature.icon}
                </div>
                <h3
                  id={`feature-${index + 5}-title`}
                  className="text-2xl font-extrabold mb-3 text-white text-center uppercase"
                >
                  <MarkdownText text={t(feature.title)} />
                </h3>
                <p className="text-white font-normal text-center">
                  <MarkdownText text={t(feature.description)} />
                </p>
              </div>
            ))}
          </div>
        </div>

        {/* Screenshot Placeholder */}
        <div className="max-w-5xl mx-auto">
          <div className="relative aspect-video bg-gradient-to-br from-gray-100 to-gray-200 rounded-lg border-2 border-firecalc-orange-light overflow-hidden">
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="text-center">
                <svg className="w-24 h-24 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}