#!/usr/bin/env node
/**
 * Converts top-level colour constants:   const NAVY = "#1B3A6B"
 * into theme tokens, splitting them by how each USAGE consumes the colour.
 *
 * Why: one constant is often used for text, fills and borders. A single token
 * cannot serve all three in dark mode (text must get lighter, fills must stay
 * dark enough for white labels). So:
 *
 *   const NAVY = "var(--hf-primary)"              // fills, borders (the base)
 *   const NAVY_TEXT = "var(--hf-primary-text)"    // added only if some usage is text
 *
 * and every usage whose owning property is a text colour (color, iconColor,
 * a JSX `color=` prop, ...) is rewritten to NAVY_TEXT. Unrecognised usages
 * keep the base constant.
 *
 * A constant is LEFT ALONE (and reported) when any usage is:
 *   - an SVG presentation attribute (fill=, stroke=, stopColor=): needs style;
 *   - a canvas call (ctx.fillStyle = ...);
 *   - in a data position (form state, API payload, storage): see data-context.mjs.
 *
 *   node scripts/theme-codemod/split-constants.mjs [--write] path [path ...]
 *
 * The hex -> tokens table is CONSTS below; extend it when a new constant
 * value turns up (the report lists any it did not recognise).
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
import { classifyDataPosition } from './data-context.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const args = process.argv.slice(2)
const WRITE = args.includes('--write')
const targets = args.filter(a => !a.startsWith('--'))
if (!targets.length) { console.error('usage: split-constants.mjs [--write] path ...'); process.exit(2) }

// hex -> { fill: token for fills/borders/unknown usage, text: token for text usage }
const CONSTS = {
  '#1B3A6B': { fill: 'primary',              text: 'primary-text' },
  '#0D9488': { fill: 'accent',               text: 'accent-text' },
  '#DC2626': { fill: 'danger',               text: 'danger-text' },
  '#166534': { fill: 'success-solid-strong', text: 'success-text-strong' },
  '#065F46': { fill: 'success-solid-strong', text: 'success-text-strong' }, // merged into green-800
  '#D97706': { fill: 'warning',              text: 'warning-text' },
  '#7C3AED': { fill: 'violet',               text: 'violet-text' },
  '#64748B': { fill: 'neutral-solid',        text: 'text-muted' },
  '#0284C7': { fill: 'sky',                  text: 'sky-text' },
  '#1D4ED8': { fill: 'info',                 text: 'info-text' },
  '#E2E8F0': { fill: 'border',               text: 'border' },
  '#F8FAFC': { fill: 'surface-muted',        text: 'surface-muted' },
  '#F1F5F9': { fill: 'surface-sunken',       text: 'surface-sunken' },
  '#0F172A': { fill: 'text',                 text: 'text' },
  '#94A3B8': { fill: 'text-faint',           text: 'text-faint' },
}
const FG = new Set(['color', 'fg', 'iconColor', 'textColor', 'caretColor', 'c', 'labelColor', 'valueColor'])
const FILL = new Set(['background', 'backgroundColor', 'bg', 'border', 'borderColor', 'borderTop', 'borderBottom',
  'borderLeft', 'borderRight', 'borderTopColor', 'borderBottomColor', 'borderLeftColor', 'borderRightColor',
  'boxShadow', 'outline', 'outlineColor', 'accentColor', 'fill', 'stroke', 'dot', 'iconBg'])
const JSX_FG = new Set(['color', 'textColor'])
const JSX_FILL = new Set(['bg', 'background', 'border', 'borderColor', 'accent'])
const JSX_SVG = new Set(['fill', 'stroke', 'stopColor', 'floodColor'])

function walk(p, out = []) {
  if (fs.statSync(p).isFile()) { if (/\.tsx?$/.test(p)) out.push(p); return out }
  for (const e of fs.readdirSync(p)) { if (e === 'node_modules' || e.startsWith('.')) continue; walk(path.join(p, e), out) }
  return out
}

/** 'fg' | 'fill' | 'svg' | 'canvas' | 'unknown' for one identifier usage. */
function roleOf(id) {
  let n = id
  while (n.parent) {
    const p = n.parent
    if (ts.isConditionalExpression(p) && p.condition !== n) { n = p; continue }
    if (ts.isParenthesizedExpression(p) || ts.isAsExpression(p) || ts.isTemplateSpan(p) ||
        ts.isTemplateExpression(p) || ts.isNonNullExpression(p)) { n = p; continue }
    if (ts.isBinaryExpression(p) && [ts.SyntaxKind.BarBarToken, ts.SyntaxKind.QuestionQuestionToken, ts.SyntaxKind.PlusToken].includes(p.operatorToken.kind)) { n = p; continue }
    if (ts.isPropertyAssignment(p) && p.initializer === n) {
      const name = p.name.getText()
      return FG.has(name) ? 'fg' : FILL.has(name) ? 'fill' : 'unknown'
    }
    if (ts.isJsxExpression(p) && ts.isJsxAttribute(p.parent)) {
      const an = p.parent.name.getText()
      return JSX_SVG.has(an) ? 'svg' : JSX_FG.has(an) ? 'fg' : JSX_FILL.has(an) ? 'fill' : 'unknown'
    }
    if (ts.isBinaryExpression(p) && p.operatorToken.kind === ts.SyntaxKind.EqualsToken && p.right === n) {
      const l = p.left.getText()
      if (/(fill|stroke)Style|shadowColor/.test(l)) return 'canvas'
      const m = /\.style\.(\w+)$/.exec(l)
      if (m) return FG.has(m[1]) ? 'fg' : 'fill'
    }
    return 'unknown'
  }
  return 'unknown'
}

const isReference = id => {
  const p = id.parent
  if (ts.isVariableDeclaration(p) && p.name === id) return false
  if ((ts.isPropertyAssignment(p) || ts.isPropertySignature?.(p)) && p.name === id) return false // object key
  if (ts.isPropertyAccessExpression(p) && p.name === id) return false
  if (ts.isJsxAttribute(p) && p.name === id) return false
  if (ts.isImportSpecifier(p) || ts.isExportSpecifier(p)) return false
  return true
}

const report = { files: 0, converted: 0, textSplits: 0, skipped: [], unknownHex: {} }
for (const file of targets.flatMap(t => walk(path.resolve(root, t)))) {
  const text = fs.readFileSync(file, 'utf8')
  if (!/const\s+\w+\s*=\s*["']#[0-9A-Fa-f]{3,6}["']/.test(text)) continue
  const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
  const rel = path.relative(root, file)

  // 1. candidate constants (top-level, const, string-literal initialiser)
  const decls = []
  for (const st of sf.statements) {
    if (!ts.isVariableStatement(st) || !(st.declarationList.flags & ts.NodeFlags.Const)) continue
    for (const d of st.declarationList.declarations) {
      if (!ts.isIdentifier(d.name) || !d.initializer || !ts.isStringLiteral(d.initializer)) continue
      if (!/^#[0-9A-Fa-f]{3,6}$/.test(d.initializer.text)) continue
      let hex = d.initializer.text.toUpperCase()
      if (hex.length === 4) hex = '#' + [...hex.slice(1)].map(c => c + c).join('')
      if (!CONSTS[hex]) { report.unknownHex[`${rel}: ${d.name.text} = ${hex}`] = 1; continue }
      decls.push({ name: d.name.text, hex, stmt: st, decl: d, multi: st.declarationList.declarations.length > 1 })
    }
  }
  if (!decls.length) continue

  // 2. usages
  const uses = new Map(decls.map(d => [d.name, []]))
  const visit = n => {
    if (ts.isIdentifier(n) && uses.has(n.text) && isReference(n)) uses.get(n.text).push(n)
    ts.forEachChild(n, visit)
  }
  visit(sf)

  const edits = [] // [start, end, replacement]
  for (const d of decls) {
    const us = uses.get(d.name)
    const bad = us.map(u => roleOf(u)).find(r => r === 'svg' || r === 'canvas')
    const dataUse = us.map(u => classifyDataPosition(u, sf)).find(Boolean)
    if (bad || dataUse) { report.skipped.push(`${rel}: ${d.name} (${bad ?? 'data position: ' + dataUse})`); continue }
    if (d.multi) { report.skipped.push(`${rel}: ${d.name} (declared in a multi-declarator statement)`); continue }

    const cfg = CONSTS[d.hex]
    const textUses = cfg.text !== cfg.fill ? us.filter(u => roleOf(u) === 'fg') : []
    const baseUses = us.length - textUses.length
    const fillTok = `"var(--hf-${cfg.fill})"`
    if (us.length > 0 && baseUses === 0) {
      // every usage is text: the base constant would be unused, so declare only the text one
      edits.push([d.decl.initializer.getStart(sf), d.decl.initializer.getEnd(), `"var(--hf-${cfg.text})"`])
      report.converted++
      continue
    }
    edits.push([d.decl.initializer.getStart(sf), d.decl.initializer.getEnd(), fillTok])
    report.converted++
    if (textUses.length) {
      edits.push([d.stmt.getEnd(), d.stmt.getEnd(), `\nconst ${d.name}_TEXT = "var(--hf-${cfg.text})";`])
      for (const u of textUses) edits.push([u.getStart(sf), u.getEnd(), `${d.name}_TEXT`])
      report.textSplits++
    }
  }
  if (!edits.length) continue
  report.files++
  if (WRITE) {
    let out = text
    for (const [s, e, r] of edits.sort((a, b) => b[0] - a[0] || b[1] - a[1])) out = out.slice(0, s) + r + out.slice(e)
    fs.writeFileSync(file, out)
  }
}
console.log(`${WRITE ? 'APPLIED' : 'DRY RUN'}: ${report.converted} constants in ${report.files} files (${report.textSplits} needed a _TEXT variant)`)
if (report.skipped.length) console.log('Left alone:\n  ' + report.skipped.join('\n  '))
const unk = Object.keys(report.unknownHex)
if (unk.length) console.log('Unrecognised colour constants (add to CONSTS):\n  ' + unk.join('\n  '))
