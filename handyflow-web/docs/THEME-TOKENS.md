# Theme tokens

All colours come from `src/styles/tokens.css`. Components reference them as
`var(--hf-*)` in inline styles, or through Tailwind names (`bg-surface`,
`text-ink-muted`, `border-line`, `bg-primary`, …) defined in `tailwind.config.js`.

The active theme is `<html data-theme="light|dark">`. `index.html` sets it
before first paint from `localStorage['hf-theme']` (`light` | `dark` | `system`).
For testing, append `?theme=dark` (or `light` / `system`) to any URL.

## Rules for new code

1. Never write a hex colour in a component. Pick a token.
2. Text uses a `-text` token; fills and borders use the base, `-soft` or `-border` token.
   They are separate because dark mode needs lighter text but not lighter fills.
3. Don't build colours with string maths (`${c}20`, `c + '18'`). CSS variables
   can't be concatenated. Use the family's `-soft` / `-border` token instead.
4. Don't pass tokens to SVG presentation attributes (`<Icon color=…>`, `fill=`,
   `stroke=`). Put the colour in `style={{ color }}` and let the icon use
   `currentColor`.
5. Recharts and Leaflet need literal colours. Chart palettes will get a
   `useThemeColors()` hook in Phase 1 that reads computed token values.

## Token families

| Family | Tokens |
|---|---|
| Neutral surfaces | `canvas`, `surface`, `surface-muted`, `surface-sunken`, `surface-strong`, `inverse-surface` |
| Neutral lines | `border`, `border-subtle`, `border-strong` |
| Neutral text | `text`, `text-secondary`, `text-tertiary`, `text-muted`, `text-faint`, `text-disabled`, `text-on-solid` |
| Brand | `primary`, `primary-hover`, `primary-text`, `primary-border`, `accent`, `accent-text`, `accent-text-strong`, `accent-soft`, `accent-border` |
| Status | `danger`, `success`, `warning`, `info`, each with `-text`, `-text-strong`, `-soft`, `-soft-strong`, `-border` |
| Categorical | `orange`, `sky`, `violet`, `indigo` (same pattern, fewer steps) |
| Shape | `radius-sm`, `radius`, `radius-lg` |

## Phase 0 migration (codemod)

`scripts/theme-codemod/` holds the migration tooling:

- `gen_map.py`: source of truth for hex → token, per role (text / fill / border). Regenerates `token-map.json`.
- `codemod.mjs`: TypeScript-AST codemod. Dry run by default; `--write` applies; optional path argument to limit scope.
- `report.json`: output of the last run (conversions, normalisations, skips with reasons, unsafe files).

Re-running it is safe and idempotent: already-converted values contain no hex.

### Verified audit numbers (commit 3361152)

| Measure | Value |
|---|---|
| `.tsx` files in `src` | 319 |
| `style={{` occurrences | 15,176 across 306 files |
| Hex literals in `.ts/.tsx` | 17,667 (153 distinct colours) |
| Tailwind colour classes | 87 in 13 files |

### Result

| | Count |
|---|---|
| Colours converted to tokens | 14,454 (~82%) in 301 files |
| Of those, merged into a neighbouring shade | 549 (e.g. Tailwind gray → slate, `#EF4444` → `#DC2626`); all listed in `report.json` → `normalised` |
| Left as hex, with reason | 3,188 |

Light theme: every converted colour keeps its exact value except the 549
merges above, which are same-hue shade adjustments. Merges that would have
been visible (light text on dark surfaces, a brown fill → orange) were
deliberately excluded and remain hex.

### What was left as hex, and why

| Reason | Colours | How to finish |
|---|---|---|
| Definition objects in 73 "unsafe" files (alpha-string maths, config colours passed to icon props, recharts/leaflet) | 1,441 | Per module: replace `${c}18` with `-soft` tokens, move icon colours to `style`, then re-run the codemod on that folder |
| Top-level constants (`const NAVY = '#1B3A6B'`), arrays, function args | 744 | Replace constant values with `var(--hf-…)` by hand, after checking every consumer |
| JSX attributes (`color="#…"` on icons and custom components) | ~680 | Icons: switch to `style={{ color }}`. Custom props (`bg=`, `border=`, `tone=`): change the component API to take a tone name |
| Status-keyed maps (`CANCELLED: '#…'`, `HIGH: '#…'`) | ~250 | Convert together with their consumers; several feed alpha maths |
| Colours with no token for that role | 57 | Mostly light text on dark navy surfaces; decide per case |

Modules with the most unsafe files: clinic (9), security (9), events, fleet, legalcompliance, trainingprovider (4 each).

### Verification performed

- `tsc -b`: identical error set to baseline (287 pre-existing errors on `main`; `npm run build` already fails on `main` because of them, mostly unused imports).
- `vite build`: passes.
- `eslint .`: identical to baseline (2,022 pre-existing problems).
- Colour comparisons (`x === '#DC2626'`): 4 sites, all fed by untouched literals; behaviour unchanged.
- Canvas (`strokeStyle`), print/`window.open` HTML: none touched.
- Three icon colour props fed by converted config values were moved to `style={{ color }}`.

Not yet verified: visual regression in a browser, and dark mode rendering. Dark
mode is expected to be partially broken until the skipped 18% is migrated;
it is not user-selectable until Phase 1.
