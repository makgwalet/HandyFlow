import React, { createContext, useContext, useMemo, useState } from 'react';
import { useColorScheme } from 'react-native';
import { ColorScheme, SemanticColors, darkColors, lightColors, spacing, radius, typography } from './tokens';

interface ThemeContextValue {
  scheme: ColorScheme;
  colors: SemanticColors;
  spacing: typeof spacing;
  radius: typeof radius;
  typography: typeof typography;
  setScheme: (scheme: ColorScheme | 'system') => void;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const systemScheme = useColorScheme();
  const [override, setOverride] = useState<ColorScheme | 'system'>('system');

  const scheme: ColorScheme = override === 'system' ? (systemScheme === 'dark' ? 'dark' : 'light') : override;

  const value = useMemo<ThemeContextValue>(
    () => ({
      scheme,
      colors: scheme === 'dark' ? darkColors : lightColors,
      spacing,
      radius,
      typography,
      setScheme: setOverride,
    }),
    [scheme]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within a ThemeProvider');
  return ctx;
}
