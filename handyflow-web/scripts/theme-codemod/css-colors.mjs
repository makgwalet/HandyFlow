#!/usr/bin/env node
/**
 * Converts hard-coded colours in .css files (including CSS modules) to theme
 * tokens. The TypeScript codemods only read .ts/.tsx, so stylesheets were a
 * blind spot until this tool.
 *
 *   node scripts/theme-codemod/css-colors.mjs [--write] path [path ...]
 *
 * What it does, per declaration, by the ROLE of the property it is in:
 *   fg  (color, caret-color, fill, stroke, ...)        -> text token
 *   bg  (background, background-color)                 -> fill token
 *   bd  (border*, outline*, box-shadow, column-rule)   -> border token
 * using the same hex -> token table as the TypeScript codemod (token-map.json).
 *
 * Handles three colour forms:
 *   1. literal hex:                    background: #FEF2F2
 *   2. the named colour `white`:       color: white      (never `white-space`)
 *   3. a custom property that holds a hex, e.g. `--navy: #1B3A6B` defined once
 *      and used as var(--navy) for text, fills and borders alike. Each USAGE is
 *      resolved to the token for its own property's role, and the definition is
 *      removed once nothing references it.
 *
 * Anything it cannot map (unknown property role, or no token for that colour in
 * that role) is left untouched and reported. rgba()/hsla() are not touched.
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const args = process.argv.slice(2)
const WRITE = args.includes('--write')
const targets = args.filter(a => !a.startsWith('--'))
if (!targets.length) { console.error('usage: css-colors.mjs [--write] path ...'); process.exit(2) }
const MAP = JSON.parse(fs.readFileSync(path.join(here, 'token-map.json'), 'utf8'))

const norm = h => {
  if (h.toLowerCase() === 'white') return '#FFFFFF'
  const u = h.toUpperCase()
  return u.length === 4 ? '#' + [...u.slice(1)].map(c => c + c).join('') : u
}
const roleOf = prop => {
  const p = prop.toLowerCase()
  if (p === 'color' || p === 'caret-color' || p === 'fill' || p === 'stroke' || p === 'text-decoration-color' ||
      p === '-webkit-text-fill-color') return 'fg'
  if (p === 'background' || p === 'background-color') return 'bg'
  if (p === 'border' || p.startsWith('border-') || p === 'outline' || p.startsWith('outline-') ||
      p === 'box-shadow' || p === 'column-rule') return 'bd'
  return null
}
const tokenFor = (hex, role) => {
  const t = MAP[norm(hex)]?.[role]
  return t ? `var(--hf-${t})` : null
}

function walk(p, out = []) {
  if (fs.statSync(p).isFile()) { if (/\.css$/.test(p) && !p.endsWith('tokens.css')) out.push(p); return out }
  for (const e of fs.readdirSync(p)) { if (e === 'node_modules' || e.startsWith('.')) continue; walk(path.join(p, e), out) }
  return out
}

const DECL = /([\w-]+)(\s*:\s*)([^;{}]+?)(\s*!important)?(\s*(?=[;}]))/g
const HEX_OR_WHITE = /#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b|(?<![\w-])white(?![\w-])/g
const report = { converted: 0, viaVar: 0, removedDefs: 0, left: [] }

for (const file of targets.flatMap(t => walk(path.resolve(root, t)))) {
  let text = fs.readFileSync(file, 'utf8')
  const rel = path.relative(root, file)

  // 1. custom properties whose value is exactly a hex
  const defs = {}
  for (const m of text.matchAll(/(--[\w-]+)\s*:\s*(#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3}))\s*;/g)) defs[m[1]] = m[2]

  // 2. declarations
  text = text.replace(DECL, (whole, prop, colon, value, imp = '', tail = '') => {
    if (prop.startsWith('--')) return whole                      // definitions handled in step 3
    const role = roleOf(prop)
    let out = value, changed = false
    // var(--name) that resolves to a known hex
    out = out.replace(/var\((--[\w-]+)\)/g, (v, name) => {
      if (!defs[name]) return v
      if (!role) return v
      const t = tokenFor(defs[name], role)
      if (!t) { report.left.push(`${rel}: ${prop}: var(${name}) = ${defs[name]} (no ${role} token)`); return v }
      changed = true; report.viaVar++; return t
    })
    // literal hex / white
    if (HEX_OR_WHITE.test(out)) {
      HEX_OR_WHITE.lastIndex = 0
      out = out.replace(HEX_OR_WHITE, h => {
        if (!role) { report.left.push(`${rel}: ${prop}: ${h} (unrecognised property role)`); return h }
        const t = tokenFor(h, role)
        if (!t) { report.left.push(`${rel}: ${prop}: ${h} (no ${role} token)`); return h }
        changed = true; report.converted++; return t
      })
    }
    HEX_OR_WHITE.lastIndex = 0
    return changed ? `${prop}${colon}${out}${imp}${tail}` : whole
  })

  // 3. drop definitions nothing references any more; re-point the rest at a token
  for (const [name, hex] of Object.entries(defs)) {
    const refs = (text.match(new RegExp(`var\\(${name}\\)`, 'g')) || []).length
    const defRe = new RegExp(`^[ \\t]*${name}\\s*:\\s*${hex}\\s*;[ \\t]*\\r?\\n`, 'm')
    if (refs === 0) { text = text.replace(defRe, ''); report.removedDefs++ }
    else {
      const t = tokenFor(hex, 'bg') || tokenFor(hex, 'fg') || tokenFor(hex, 'bd')
      if (t) text = text.replace(new RegExp(`(${name}\\s*:\\s*)${hex}`), `$1${t}`)
    }
  }
  if (WRITE) fs.writeFileSync(file, text)
}
console.log(`${WRITE ? 'APPLIED' : 'DRY RUN'}: ${report.converted} literals + ${report.viaVar} var() usages converted; ${report.removedDefs} palette variables removed`)
if (report.left.length) console.log('Left alone:\n  ' + report.left.join('\n  '))
