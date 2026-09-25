/** @type {import('tailwindcss').Config} */
// Colours point at the CSS variables in src/styles/tokens.css so Tailwind
// classes follow the active theme. Note: opacity modifiers (bg-surface/50)
// do not work with plain var() colours; use a dedicated token instead.
const token = name => `var(--hf-${name})`

export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          navy: token('primary'),
          teal: token('accent'),
        },
        canvas: token('canvas'),
        surface: {
          DEFAULT: token('surface'),
          muted: token('surface-muted'),
          sunken: token('surface-sunken'),
          strong: token('surface-strong'),
        },
        line: {
          DEFAULT: token('border'),
          subtle: token('border-subtle'),
          strong: token('border-strong'),
        },
        ink: {
          DEFAULT: token('text'),
          secondary: token('text-secondary'),
          tertiary: token('text-tertiary'),
          muted: token('text-muted'),
          faint: token('text-faint'),
          disabled: token('text-disabled'),
          'on-solid': token('text-on-solid'),
        },
        primary: { DEFAULT: token('primary'), hover: token('primary-hover'), text: token('primary-text') },
        accent: { DEFAULT: token('accent'), text: token('accent-text'), soft: token('accent-soft') },
        danger: { DEFAULT: token('danger'), text: token('danger-text'), soft: token('danger-soft'), border: token('danger-border') },
        success: { DEFAULT: token('success'), text: token('success-text'), soft: token('success-soft'), border: token('success-border') },
        warning: { DEFAULT: token('warning'), text: token('warning-text'), soft: token('warning-soft'), border: token('warning-border') },
        info: { DEFAULT: token('info'), text: token('info-text'), soft: token('info-soft'), border: token('info-border') },
      },
      borderRadius: {
        token: token('radius'),
        'token-sm': token('radius-sm'),
        'token-lg': token('radius-lg'),
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
