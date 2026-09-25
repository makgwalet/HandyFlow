// src/styles/brand.ts
//
// Curated brand colours. Keys mirror the backend UiBrandColor enum.
// Each palette overrides only the four --hf-primary* tokens; everything else
// in tokens.css stays the same. Contrast is checked by
// scripts/check-brand-contrast.mjs:
//   - white text on `primary` and `primaryHover`: at least 4.5:1
//   - `primaryText` on the theme's surface: at least 4.5:1

export type BrandColor = 'NAVY' | 'OCEAN' | 'TEAL' | 'VIOLET' | 'EMERALD' | 'CHARCOAL'

export interface BrandPalette {
  primary: string
  primaryHover: string
  primaryText: string
  primaryBorder: string
}

export interface BrandDefinition {
  label: string
  light: BrandPalette
  dark: BrandPalette
}

export const BRANDS: Record<BrandColor, BrandDefinition> = {
  NAVY: {
    label: 'Navy',
    light: { primary: '#1b3a6b', primaryHover: '#0f2a52', primaryText: '#1b3a6b', primaryBorder: '#e8edf5' },
    dark:  { primary: '#3563ae', primaryHover: '#2b5596', primaryText: '#8fb2ec', primaryBorder: '#26395a' },
  },
  OCEAN: {
    label: 'Ocean',
    light: { primary: '#075985', primaryHover: '#0c4a6e', primaryText: '#075985', primaryBorder: '#e0f2fe' },
    dark:  { primary: '#0369a1', primaryHover: '#075985', primaryText: '#7dd3fc', primaryBorder: '#173b52' },
  },
  TEAL: {
    label: 'Teal',
    light: { primary: '#0f766e', primaryHover: '#115e59', primaryText: '#0f766e', primaryBorder: '#ccfbf1' },
    dark:  { primary: '#0f766e', primaryHover: '#115e59', primaryText: '#5eead4', primaryBorder: '#16403d' },
  },
  VIOLET: {
    label: 'Violet',
    light: { primary: '#5b21b6', primaryHover: '#4c1d95', primaryText: '#5b21b6', primaryBorder: '#ede9fe' },
    dark:  { primary: '#7c3aed', primaryHover: '#6d28d9', primaryText: '#c4b5fd', primaryBorder: '#33275a' },
  },
  EMERALD: {
    label: 'Emerald',
    light: { primary: '#047857', primaryHover: '#065f46', primaryText: '#047857', primaryBorder: '#d1fae5' },
    dark:  { primary: '#047857', primaryHover: '#065f46', primaryText: '#6ee7b7', primaryBorder: '#16433a' },
  },
  CHARCOAL: {
    label: 'Charcoal',
    light: { primary: '#334155', primaryHover: '#1e293b', primaryText: '#334155', primaryBorder: '#e2e8f0' },
    dark:  { primary: '#475569', primaryHover: '#334155', primaryText: '#cbd5e1', primaryBorder: '#2a3445' },
  },
}

export const BRAND_KEYS = Object.keys(BRANDS) as BrandColor[]

export function isBrandColor(v: unknown): v is BrandColor {
  return typeof v === 'string' && v in BRANDS
}

/** CSS custom properties for a brand in a given theme. */
export function brandVars(brand: BrandColor, theme: 'light' | 'dark'): Record<string, string> {
  const p = BRANDS[brand][theme]
  return {
    '--hf-primary': p.primary,
    '--hf-primary-hover': p.primaryHover,
    '--hf-primary-text': p.primaryText,
    '--hf-primary-border': p.primaryBorder,
  }
}
