#!/usr/bin/env node
/**
 * Splits a shared palette OBJECT (e.g. `color` exported from portal-theme.ts and
 * used as `color.navy`) into fill and text variants, by how each usage consumes it.
 * The object-member counterpart of split-shared-constant.mjs.
 *
 *   1. In the definition file, give each key you want to split a text sibling:
 *        navy: "var(--hf-primary)",  navyText: "var(--hf-primary-text)",
 *   2. node scripts/theme-codemod/split-object-members.mjs [--write] \
 *          --object color --from portal-theme --split navy,red,blue <dir> [<dir> ...]
 *
 * For every file under the dirs that imports `color` from a module ending in
 * `portal-theme`: `color.navy` in a text position becomes `color.navyText`.
 * A key with any usage in an SVG attribute, canvas call or data position is
 * reported and left as is (the definition would then be wrong for that usage).
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
import { classifyDataPosition } from './data-context.mjs'
import { walk, roleOf } from './roles.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const argv = process.argv.slice(2)
const WRITE = argv.includes('--write')
const opt = n => { const i = argv.indexOf(`--${n}`); return i >= 0 ? argv[i + 1] : null }
const OBJ = opt('object'), FROM = opt('from'), SPLIT = new Set((opt('split') ?? '').split(',').filter(Boolean))
const skipVals = new Set(['--object', '--from', '--split', OBJ, FROM, opt('split')])
const dirs = argv.filter(a => !a.startsWith('--') && !skipVals.has(a))
if (!OBJ || !FROM || !SPLIT.size || !dirs.length) {
  console.error('usage: split-object-members.mjs [--write] --object NAME --from MODULE --split k1,k2 <dir> ...'); process.exit(2)
}

const files = dirs.flatMap(d => walk(path.resolve(root, d)))
const defFile = files.find(f => new RegExp(`${FROM}\\.tsx?$`).test(f))
const defText = defFile ? fs.readFileSync(defFile, 'utf8') : fs.readFileSync(
  walk(path.join(root, 'src')).find(f => new RegExp(`${FROM}\\.tsx?$`).test(f)), 'utf8')
for (const k of SPLIT) if (!new RegExp(`\\b${k}Text\\s*:`).test(defText)) { console.error(`${FROM} does not define ${k}Text`); process.exit(1) }

const stats = { files: 0, text: 0, base: 0, skipped: [] }
const perKey = {}
for (const file of files) {
  if (/portal-theme\.tsx?$/.test(file) && new RegExp(`${FROM}\\.tsx?$`).test(file)) continue
  const text = fs.readFileSync(file, 'utf8')
  if (!new RegExp(`\\b${OBJ}\\.`).test(text)) continue
  const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
  const rel = path.relative(root, file)
  let imported = false
  for (const st of sf.statements) {
    if (ts.isImportDeclaration(st) && ts.isStringLiteral(st.moduleSpecifier) && new RegExp(`${FROM}$`).test(st.moduleSpecifier.text) &&
        st.importClause?.namedBindings && ts.isNamedImports(st.importClause.namedBindings) &&
        st.importClause.namedBindings.elements.some(e => e.name.text === OBJ && !e.propertyName)) imported = true
  }
  if (!imported) continue
  const edits = []
  const visit = n => {
    if (ts.isPropertyAccessExpression(n) && ts.isIdentifier(n.expression) && n.expression.text === OBJ && SPLIT.has(n.name.text)) {
      const key = n.name.text, role = roleOf(n), data = classifyDataPosition(n, sf)
      if (role === 'svg' || role === 'canvas' || data) stats.skipped.push(`${rel}: ${OBJ}.${key} (${role === 'svg' || role === 'canvas' ? role : 'data position'})`)
      else if (role === 'fg') { edits.push([n.name.getStart(sf), n.name.getEnd(), `${key}Text`]); stats.text++; perKey[key] = (perKey[key] || 0) + 1 }
      else stats.base++
    }
    ts.forEachChild(n, visit)
  }
  visit(sf)
  if (!edits.length) continue
  stats.files++
  if (WRITE) {
    let out = text
    for (const [s, e, r] of edits.sort((a, b) => b[0] - a[0])) out = out.slice(0, s) + r + out.slice(e)
    fs.writeFileSync(file, out)
  }
}
console.log(`${WRITE ? 'APPLIED' : 'DRY RUN'}: ${stats.files} files; ${stats.text} text usages -> <key>Text, ${stats.base} left as fill/border`)
console.log('text usages by key:', perKey)
if (stats.skipped.length) console.log('Left alone:\n  ' + stats.skipped.join('\n  '))
