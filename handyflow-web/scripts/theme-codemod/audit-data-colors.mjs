#!/usr/bin/env node
/**
 * Finds `var(--hf-*)` strings in places where a colour is DATA rather than
 * styling, because a CSS variable name there is a bug:
 *
 *   - form/initial state:      useState({ color: 'var(--hf-…)' })
 *   - request payloads:        apiClient.post(url, { color: 'var(--hf-…)' })
 *   - persisted values:        localStorage.setItem(k, …), JSON.stringify(…)
 *   - fallbacks fed into state setters:  setForm({ color: s.color ?? 'var(--hf-…)' })
 *   - constants with data-ish names:     EMPTY_FORM, DEFAULT_*, INITIAL_*
 *
 * Why it matters: a stored colour must be a literal (the database column may
 * be VARCHAR(7); other pages may do hex maths on it; other clients cannot
 * resolve our CSS variables). Real example: bookings ServicesTab defaulted a
 * new service's colour to a CSS variable, which overflows `color VARCHAR(7)`.
 *
 *   node scripts/theme-codemod/audit-data-colors.mjs [path ...]
 *
 * Exit code 1 when anything is found, so it can gate CI. Every hit needs a
 * human decision: if the value is display-only, add `// data-color-ok: <why>`
 * on the same line or the line above.
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
import { classifyDataPosition as classify } from './data-context.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const targets = process.argv.slice(2).filter(a => !a.startsWith('--'))
const scanRoots = targets.length ? targets.map(t => path.resolve(root, t)) : [path.join(root, 'src')]

function walk(dir, out = []) {
  if (fs.statSync(dir).isFile()) { if (/\.tsx?$/.test(dir)) out.push(dir); return out }
  for (const e of fs.readdirSync(dir)) {
    if (e === 'node_modules' || e.startsWith('.')) continue
    walk(path.join(dir, e), out)
  }
  return out
}

const hits = []
for (const file of scanRoots.flatMap(r => walk(r))) {
  const text = fs.readFileSync(file, 'utf8')
  if (!text.includes('var(--hf-')) continue
  const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
  const lines = text.split('\n')
  const visit = node => {
    const isLit = ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node) ||
      ts.isTemplateHead(node) || ts.isTemplateMiddle(node) || ts.isTemplateTail(node)
    if (isLit && node.text.includes('var(--hf-')) {
      const why = classify(node, sf)
      if (why) {
        const line = sf.getLineAndCharacterOfPosition(node.getStart(sf)).line
        const ok = /data-color-ok/.test(lines[line]) || (line > 0 && /data-color-ok/.test(lines[line - 1]))
        if (!ok) hits.push({ file: path.relative(root, file), line: line + 1, why, code: lines[line].trim().slice(0, 150) })
      }
    }
    ts.forEachChild(node, visit)
  }
  visit(sf)
}

const byWhy = {}
for (const h of hits) (byWhy[h.why.split(' (')[0]] ??= []).push(h)
for (const h of hits) console.log(`${h.file}:${h.line}  [${h.why}]\n    ${h.code}`)
console.log(`\n${hits.length} data-position colour token(s) found` +
  (hits.length ? `: ${Object.entries(byWhy).map(([k, v]) => `${k} ${v.length}`).join(', ')}` : ''))
process.exit(hits.length ? 1 : 0)
