/**
 * HandyFlow Fuel — design tokens.
 * Single source of truth for color, spacing, radius and type so every
 * screen stays visually consistent without re-declaring raw values.
 */

export const colors = {
  // Brand
  navy: '#0A1929',
  navyDeep: '#061321',
  primary: '#0891B2',
  primaryLight: '#22D3EE',
  primaryDark: '#0E7490',

  // Status
  success: '#16A34A',
  successBg: '#EAF7EE',
  warning: '#D97706',
  warningBg: '#FEF3E2',
  danger: '#DC2626',
  dangerBg: '#FDECEC',
  info: '#2563EB',
  infoBg: '#EAF0FE',

  // Neutrals
  bg: '#F5F7FA',
  surface: '#FFFFFF',
  border: '#E4E7EC',
  borderStrong: '#CBD2DC',
  textPrimary: '#101828',
  textSecondary: '#667085',
  textMuted: '#98A2B3',
  textOnDark: '#FFFFFF',
  textOnDarkMuted: 'rgba(255,255,255,0.65)',
  overlay: 'rgba(10,25,41,0.55)',
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  xxxl: 48,
} as const;

export const radii = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  pill: 999,
} as const;

export const typography = {
  h1: { fontSize: 28, fontWeight: '700' as const, color: colors.textPrimary },
  h2: { fontSize: 22, fontWeight: '700' as const, color: colors.textPrimary },
  h3: { fontSize: 18, fontWeight: '600' as const, color: colors.textPrimary },
  body: { fontSize: 15, fontWeight: '400' as const, color: colors.textPrimary },
  bodyStrong: { fontSize: 15, fontWeight: '600' as const, color: colors.textPrimary },
  caption: { fontSize: 13, fontWeight: '500' as const, color: colors.textSecondary },
  label: {
    fontSize: 12,
    fontWeight: '700' as const,
    color: colors.textSecondary,
    letterSpacing: 0.6,
  },
  metricValue: { fontSize: 32, fontWeight: '700' as const, color: colors.textPrimary },
} as const;

export const shadow = {
  card: {
    shadowColor: '#0A1929',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 2,
  },
} as const;
