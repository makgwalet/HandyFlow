/**
 * HandyFlow Agriculture — Design Tokens
 *
 * Semantic tokens, not raw colour values, are used throughout the app so that
 * light/dark mode is a first-class concept rather than a simple inversion.
 *
 * Visual direction (see /docs/agriculture/00-OVERVIEW.md):
 * clean, premium, modern, agricultural, data-rich. Avoid "farm cartoon" cliches.
 */

export type ColorScheme = 'light' | 'dark';

export interface SemanticColors {
  background: string;
  surface: string;
  surfaceElevated: string;
  border: string;
  textPrimary: string;
  textSecondary: string;
  textInverse: string;

  // Status
  success: string;
  warning: string;
  danger: string;
  info: string;

  // Domain accents — used sparingly (icons, badges, category chips)
  livestock: string;
  poultry: string;
  crop: string;
  production: string;

  // Interactive
  accent: string;
  accentSurface: string;
}

const base = {
  green700: '#1F5B3F',
  green600: '#2A7A54',
  green500: '#3E9A6D',
  green100: '#E4F2EA',
  amber500: '#C9862B',
  amber100: '#FBEDD8',
  red500: '#C1443A',
  red100: '#F8E3E1',
  blue500: '#3068A8',
  blue100: '#E2EBF5',
  clay500: '#A8623E',
  wheat500: '#B98B2E',
  sky500: '#2E7FA0',
  neutral900: '#15181A',
  neutral800: '#20242A',
  neutral700: '#2C3138',
  neutral500: '#6B7280',
  neutral300: '#C3C9CE',
  neutral100: '#F1F3F1',
  neutral50: '#FAFAF8',
  white: '#FFFFFF',
};

export const lightColors: SemanticColors = {
  background: base.neutral50,
  surface: base.white,
  surfaceElevated: base.white,
  border: base.neutral300,
  textPrimary: base.neutral900,
  textSecondary: base.neutral500,
  textInverse: base.white,

  success: base.green600,
  warning: base.amber500,
  danger: base.red500,
  info: base.blue500,

  livestock: base.clay500,
  poultry: base.wheat500,
  crop: base.green600,
  production: base.sky500,

  accent: base.green600,
  accentSurface: base.green100,
};

export const darkColors: SemanticColors = {
  // Tuned for early-morning rounds, vehicles, low light — not a raw invert.
  background: base.neutral900,
  surface: base.neutral800,
  surfaceElevated: base.neutral700,
  border: '#3A3F46',
  textPrimary: '#F4F5F3',
  textSecondary: '#A7ADB3',
  textInverse: base.neutral900,

  success: base.green500,
  warning: '#E0A34C',
  danger: '#E07068',
  info: '#5B96D6',

  livestock: '#C98259',
  poultry: '#D6A94C',
  crop: base.green500,
  production: '#5AA6C7',

  accent: base.green500,
  accentSurface: '#22362B',
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  xxxl: 48,
};

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  pill: 999,
};

export const typography = {
  display: { fontSize: 30, fontWeight: '700' as const, lineHeight: 36 },
  h1: { fontSize: 24, fontWeight: '700' as const, lineHeight: 30 },
  h2: { fontSize: 20, fontWeight: '600' as const, lineHeight: 26 },
  h3: { fontSize: 17, fontWeight: '600' as const, lineHeight: 22 },
  body: { fontSize: 15, fontWeight: '400' as const, lineHeight: 21 },
  bodyStrong: { fontSize: 15, fontWeight: '600' as const, lineHeight: 21 },
  caption: { fontSize: 13, fontWeight: '400' as const, lineHeight: 18 },
  label: { fontSize: 12, fontWeight: '600' as const, lineHeight: 16, letterSpacing: 0.4 },
};

// Minimum touch target per accessibility guidance in the product spec.
export const minTouchTarget = 44;
