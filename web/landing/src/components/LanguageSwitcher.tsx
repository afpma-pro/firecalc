/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
'use client';

import {useLocale, useTranslations} from 'next-intl';
import {usePathname} from 'next/navigation';
import {locales} from '@/i18n/routing';

export default function LanguageSwitcher() {
  const t = useTranslations('language');
  const locale = useLocale();
  const pathname = usePathname();

  const switchLocale = (newLocale: string) => {
    // For static export, we need to manually navigate to the new locale path
    const currentPathWithoutLocale = pathname.replace(`/${locale}`, '');
    window.location.href = `/${newLocale}${currentPathWithoutLocale}`;
  };

  return (
    <div className="dropdown dropdown-end">
      <div tabIndex={0} role="button" className="btn btn-ghost btn-sm gap-1">
        <svg 
          className="w-5 h-5" 
          fill="none" 
          stroke="currentColor" 
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path 
            strokeLinecap="round" 
            strokeLinejoin="round" 
            strokeWidth={2} 
            d="M3 5h12M9 3v2m1.048 9.5A18.022 18.022 0 016.412 9m6.088 9h7M11 21l5-10 5 10M12.751 5C11.783 10.77 8.07 15.61 3 18.129" 
          />
        </svg>
        <span className="uppercase">{locale}</span>
      </div>
      <ul tabIndex={0} className="dropdown-content z-[1] menu p-2 shadow-lg bg-base-100 rounded-box w-32 mt-2">
        {locales.map((loc) => (
          <li key={loc}>
            <button 
              onClick={() => switchLocale(loc)}
              className={locale === loc ? 'active' : ''}
              aria-label={`Switch to ${t(loc)}`}
            >
              {t(loc)}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}