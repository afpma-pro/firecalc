/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        firecalc: {
          'brown-dark': '#3B2416',
          'brown-deep': '#531E00',
          'red': '#BC2132',
          'orange-red': '#EF662F',
          'orange': '#F59331',
          'orange-light': '#FAAF4C',
          'yellow': '#FFDC38',
        },
      },
    },
  },
  plugins: [require('daisyui')],
  daisyui: {
    themes: [
      {
        light: {
          "primary": "#BC2132",
          "secondary": "#EF662F",
          "accent": "#F59331",
          "neutral": "#3B2416",
          "base-100": "#FFFFFF",
          "info": "#3ABFF8",
          "success": "#36D399",
          "warning": "#FBBD23",
          "error": "#F87272",
        },
        dark: {
          "primary": "#BC2132",
          "secondary": "#EF662F",
          "accent": "#F59331",
          "neutral": "#3B2416",
          "base-100": "#1a1a1a",
          "info": "#3ABFF8",
          "success": "#36D399",
          "warning": "#FBBD23",
          "error": "#F87272",
        },
      },
    ],
  },
}
export default config