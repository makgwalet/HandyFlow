#!/usr/bin/env node
/**
 * Splits a SHARED colour constant (defined in one module's constants.ts and
 * imported across its files) into a fill/border constant and a text constant,
 * by how each usage consumes it. The same idea as split-constants.mjs, for the
 * case where the constant is imported rather than declared in the same file.
 *
 *   1. In constants.ts, define BOTH by hand, e.g.
 *        export const CA_ACCENT      = "var(--hf-violet-solid-strong)"  // fills, borders
 *        export const CA_ACCENT_TEXT = "var(--hf-violet-text-strong)"   // text, icons
 *   2. node scripts/theme-codemod/split-shared-constant.mjs [--write] <dir> CA_ACCENT
 *
 * For every file under <dir> that imports the name from a `constants` module:
 *   - usages in a text position are rewritten to NAME_TEXT;
 *   - the import gains NAME_TEXT when needed and DROPS NAME when no base usage
 *     is left (no unused-import errors to clean up afterwards);
 *   - a file with any usage in an SVG attribute, a canvas call or a data
 *     position is left alone and reported.
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
import { classifyDataPosition } from './data-context.mjs'
import { walk, roleOf, isReference } from './roles.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const args = process.argv.slice(2)
const WRITE = args.includes('--write')
const [dir, NAME] = args.filter(a => !a.startsWith('--'))
if (!dir || !NAME) { console.error('usage: split-shared-constant.mjs [--write] <dir> NAME'); process.exit(2) }
const TEXT = `${NAME}_TEXT`

const files = walk(path.resolve(root, dir))
const defs = files.filter(f => /constants\.tsx?$/.test(f) && fs.readFileSync(f, 'utf8').includes(`export const ${TEXT}`))
if (!defs.length) {
  console.error(`No constants file under ${dir} exports both ${NAME} and ${TEXT}. Define ${TEXT} there first.`)
  process.exit(1)
}

const stats = { files: 0, textUses: 0, baseUses: 0, importsDropped: 0, skipped: [] }
for (const file of files) {
  if (defs.includes(file)) continue
  const text = fs.readFileSync(file, 'utf8')
  if (!text.includes(NAME)) continue
  const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
  const rel = path.relative(root, file)

  // the import that brings NAME in
  let imp = null
  for (const st of sf.statements) {
    if (ts.isImportDeclaration(st) && ts.isStringLiteral(st.moduleSpecifier) && /constants$/.test(st.moduleSpecifier.text) &&
        st.importClause?.namedBindings && ts.isNamedImports(st.importClause.namedBindings) &&
        st.importClause.namedBindings.elements.some(e => e.name.text === NAME && !e.propertyName)) imp = st.importClause.namedBindings
  }
  if (!imp) continue

  const uses = []
  const visit = n => {
    if (ts.isIdentifier(n) && n.text === NAME && isReference(n) && !ts.isImportSpecifier(n.parent)) uses.push(n)
    ts.forEachChild(n, visit)
  }
  visit(sf)

  const bad = uses.map(u => roleOf(u)).find(r => r === 'svg' || r === 'canvas')
  const dataUse = uses.map(u => classifyDataPosition(u, sf)).find(Boolean)
  if (bad || dataUse) { stats.skipped.push(`${rel} (${bad ?? 'data position: ' + dataUse})`); continue }

  const textUses = uses.filter(u => roleOf(u) === 'fg')
  const baseUses = uses.length - textUses.length
  const edits = textUses.map(u => [u.getStart(sf), u.getEnd(), TEXT])
  // rebuild the import specifier list
  const names = imp.elements.map(e => e.getText(sf))
  const next = names.filter(n => !(n === NAME && baseUses === 0))
  if (textUses.length && !next.includes(TEXT)) next.push(TEXT)
  if (next.length !== names.length || next.some((n, i) => n !== names[i])) {
    edits.push([imp.getStart(sf), imp.getEnd(), `{ ${next.join(', ')} }`])
    if (baseUses === 0) stats.importsDropped++
  }
  stats.files++; stats.textUses += textUses.length; stats.baseUses += baseUses
  if (WRITE && edits.length) {
    let out = text
    for (const [s, e, r] of edits.sort((a, b) => b[0] - a[0])) out = out.slice(0, s) + r + out.slice(e)
    fs.writeFileSync(file, out)
  }
}
console.log(`${WRITE ? 'APPLIED' : 'DRY RUN'} ${NAME}: ${stats.files} files, ${stats.textUses} text usages -> ${TEXT}, ${stats.baseUses} kept as base, ${stats.importsDropped} unused imports dropped`)
if (stats.skipped.length) console.log('Left alone:\n  ' + stats.skipped.join('\n  '))
