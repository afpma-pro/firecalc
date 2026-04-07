/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import { redirect } from 'next/navigation';
import { defaultLocale } from '@/i18n/routing';

export default function RootPage() {
  redirect(`/${defaultLocale}/`);
}