/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import {useTranslations} from 'next-intl';
import Image from 'next/image';

export default function Sponsors() {
  const t = useTranslations('sponsors');

  return (
    <div className="py-12">
      <h3 className="text-3xl font-bold text-center mb-3 text-firecalc-orange-red">
        {t('title')}
      </h3>
      <p className="text-center text-white mb-8">
        {t('description')}
      </p>

      <div className="max-w-6xl mx-auto">
        <Image
          src="/assets/images/sponsors.jpg"
          alt={t('title')}
          width={1200}
          height={300}
          className="w-full h-auto"
          priority={false}
        />
      </div>
    </div>
  );
}