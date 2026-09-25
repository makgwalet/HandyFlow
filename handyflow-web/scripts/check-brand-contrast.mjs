#!/usr/bin/env node
// Verifies every brand palette in src/styles/brand.ts meets WCAG AA (4.5:1):
//   white on primary / primaryHover, and primaryText on the theme surface.
// Run: node scripts/check-brand-contrast.mjs   (exits 1 on failure)
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const src = fs.readFileSync(path.join(root, 'src/styles/brand.ts'), 'utf8')
const tokens = fs.readFileSync(path.join(root, 'src/styles/tokens.css'), 'utf8')
const [lightCss, darkCss] = tokens.split("[data-theme='dark']")
const surface = {
  light: lightCss.match(/--hf-surface:\s*(#[0-9a-f]{6})/i)[1],
  dark: darkCss.match(/--hf-surface:\s*(#[0-9a-f]{6})/i)[1],
}

const lum = hex => {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map(c => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((m, n) => n - m); return (x + 0.05) / (y + 0.05) }

let failed = 0
const re = /(\w+):\s*\{\s*label:[^}]*?light:\s*\{([^}]*)\},\s*dark:\s*\{([^}]*)\}/g
for (const [, key, light, dark] of src.matchAll(re)) {
  for (const [theme, body] of [['light', light], ['dark', dark]]) {
    const v = Object.fromEntries([...body.matchAll(/(\w+):\s*'(#[0-9a-f]{6})'/gi)].map(m => [m[1], m[2]]))
    const checks = [
      ['white on primary', ratio('#ffffff', v.primary)],
      ['white on primaryHover', ratio('#ffffff', v.primaryHover)],
      [`primaryText on ${theme} surface`, ratio(v.primaryText, surface[theme])],
    ]
    for (const [name, r] of checks) {
      const ok = r >= 4.5
      if (!ok) failed++
      console.log(`${ok ? 'ok  ' : 'FAIL'} ${key.padEnd(9)} ${theme.padEnd(5)} ${name.padEnd(28)} ${r.toFixed(2)}`)
    }
  }
}
process.exit(failed ? 1 : 0)
