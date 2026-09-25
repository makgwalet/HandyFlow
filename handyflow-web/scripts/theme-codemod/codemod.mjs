#!/usr/bin/env node
/**
 * Theme codemod: replaces hard-coded hex colours with var(--hf-*) tokens.
 *
 *   node scripts/theme-codemod/codemod.mjs            # dry run, writes report only
 *   node scripts/theme-codemod/codemod.mjs --write    # apply changes
 *   node scripts/theme-codemod/codemod.mjs --write src/pages/hr   # limit to a path
 *
 * It only rewrites a string literal when ALL of these hold:
 *   1. the literal is the value of an object property (or a `.style.x =` assignment),
 *      possibly through a ternary / || / ?? / parentheses;
 *   2. the property name maps to a known role: text (fg), fill (bg) or border (bd);
 *   3. every hex inside the literal has a token for that role in token-map.json;
 *   4. the literal sits inside a JSX `style={...}` or a `.style.x =` assignment
 *      — OR it is a config/definition object in a file that is "safe" (see below).
 *
 * A file is unsafe for definition objects when it builds colours by string
 * concatenation (`${c}20`, c + '18'), passes config colours to SVG/icon props
 * (color={x.color}, fill=, stroke=), or imports recharts/leaflet. In those
 * files, CSS variables could end up somewhere that needs a literal hex, so
 * definitions are left alone and reported for manual migration.
 *
 * Everything skipped is listed in the report with a reason.
 */
import fs from 'node:fs'
import path from 'node:path'
import ts from 'typescript'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const args = process.argv.slice(2)
const WRITE = args.includes('--write')
const targets = args.filter(a => !a.startsWith('--'))
const scanRoots = targets.length ? targets.map(t => path.resolve(root, t)) : [path.join(root, 'src')]

const MAP = JSON.parse(fs.readFileSync(path.join(here, 'token-map.json'), 'utf8'))

const ROLE_BY_PROP = new Map([
  ...['color', 'fg', 'iconColor', 'c', 'textColor', 'caretColor', 'labelColor', 'valueColor', 'titleColor'].map(p => [p, 'fg']),
  ...['background', 'backgroundColor', 'bg', 'iconBg', 'selectedBg', 'hoverBg', 'activeBg', 'fill', 'stroke', 'accentColor', 'dot'].map(p => [p, 'bg']),
  ...['border', 'borderColor', 'borderTop', 'borderBottom', 'borderLeft', 'borderRight',
      'borderTopColor', 'borderBottomColor', 'borderLeftColor', 'borderRightColor',
      'outline', 'outlineColor', 'selectedBorder', 'boxShadow'].map(p => [p, 'bd']),
])

const HEX_RE = /#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b/g
const normHex = h => {
  const u = h.toUpperCase()
  return u.length === 4 ? '#' + [...u.slice(1)].map(c => c + c).join('') : u
}

const UNSAFE_PATTERNS = [
  [/\}\s*[0-9a-fA-F]{2}`/, 'builds colours with template-literal alpha suffix'],
  [/\+\s*['"][0-9a-fA-F]{2}['"]/, 'builds colours with string-concat alpha suffix'],
  [/\b(color|fill|stroke)=\{[^}]*\.(color|fg|c|dot|accent|bg|iconColor|textColor)\b/, 'passes config colours to SVG/icon props'],
  [/from\s+['"](recharts|leaflet|react-leaflet)['"]/, 'imports recharts/leaflet (needs literal colours)'],
]

function walk(dir, out = []) {
  const st = fs.statSync(dir)
  if (st.isFile()) { if (/\.(tsx|ts)$/.test(dir)) out.push(dir); return out }
  for (const e of fs.readdirSync(dir)) {
    if (e === 'node_modules' || e.startsWith('.')) continue
    walk(path.join(dir, e), out)
  }
  return out
}

function propName(node) {
  const n = node.name
  if (ts.isIdentifier(n) || ts.isStringLiteral(n)) return n.text
  return null
}

/** Climb through ternaries, ||/??, parens, `as` casts to the owning property. */
function findOwner(lit) {
  let n = lit
  while (n.parent) {
    const p = n.parent
    if (ts.isConditionalExpression(p) && p.condition !== n) { n = p; continue }
    if (ts.isParenthesizedExpression(p) || ts.isAsExpression(p)) { n = p; continue }
    // `1px solid ${active ? '#7C3AED' : '#E2E8F0'}`: the template's owner decides the role.
    if (ts.isTemplateSpan(p) && p.expression === n) { n = p.parent; continue }
    if (ts.isBinaryExpression(p) && [ts.SyntaxKind.BarBarToken, ts.SyntaxKind.QuestionQuestionToken].includes(p.operatorToken.kind)) { n = p; continue }
    if (ts.isPropertyAssignment(p) && p.initializer === n) return { kind: 'prop', name: propName(p), node: p }
    if (ts.isBinaryExpression(p) && p.operatorToken.kind === ts.SyntaxKind.EqualsToken && p.right === n &&
        ts.isPropertyAccessExpression(p.left) && ts.isPropertyAccessExpression(p.left.expression) &&
        p.left.expression.name.text === 'style') {
      return { kind: 'style-assign', name: p.left.name.text, node: p }
    }
    if (ts.isBinaryExpression(p) && [ts.SyntaxKind.EqualsEqualsEqualsToken, ts.SyntaxKind.ExclamationEqualsEqualsToken, ts.SyntaxKind.EqualsEqualsToken].includes(p.operatorToken.kind)) {
      return { kind: 'comparison' }
    }
    if (ts.isJsxAttribute(p)) return { kind: 'jsx-attr', name: p.name.getText() }
    return { kind: 'other' }
  }
  return { kind: 'other' }
}

function insideStyleAttr(node) {
  for (let n = node; n; n = n.parent) {
    if (ts.isJsxAttribute(n)) return n.name.getText() === 'style'
    if (ts.isFunctionLike(n) || ts.isSourceFile(n)) return false
  }
  return false
}

const report = { converted: 0, convertedByToken: {}, normalised: {}, skipped: {}, files: {}, unsafeFiles: {} }
const bump = (o, k, n = 1) => { o[k] = (o[k] || 0) + n }
const lightValues = Object.fromEntries(
  [...fs.readFileSync(path.join(root, 'src/styles/tokens.css'), 'utf8').split("[data-theme='dark']")[0]
    .matchAll(/--hf-([a-z-]+):\s*(#[0-9a-f]{6})/g)].map(m => [m[1], m[2].toUpperCase()]))

for (const file of scanRoots.flatMap(r => walk(r))) {
  const text = fs.readFileSync(file, 'utf8')
  if (!HEX_RE.test(text)) continue
  HEX_RE.lastIndex = 0
  const rel = path.relative(root, file)
  const unsafe = UNSAFE_PATTERNS.filter(([re]) => re.test(text)).map(([, why]) => why)
  if (unsafe.length) report.unsafeFiles[rel] = unsafe

  const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
  const edits = []

  const visit = node => {
    if ((ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) && HEX_RE.test(node.text)) {
      HEX_RE.lastIndex = 0
      const owner = findOwner(node)
      const skip = reason => bump(report.skipped, reason, (node.text.match(HEX_RE) || []).length)
      if (owner.kind === 'comparison') return skip('used in a comparison')
      if (owner.kind === 'jsx-attr') return skip(`JSX attribute ${owner.name}= (SVG/icon prop)`)
      if (owner.kind === 'other' || !owner.name) return skip('not a property value (constant, array, argument)')
      const role = ROLE_BY_PROP.get(owner.name)
      if (!role) return skip(`unknown property "${owner.name}"`)
      const inStyle = owner.kind === 'style-assign' || insideStyleAttr(node)
      if (!inStyle && unsafe.length) return skip('definition object in unsafe file')

      let ok = true
      const replaced = node.text.replace(HEX_RE, h => {
        const hex = normHex(h)
        const tok = MAP[hex]?.[role]
        if (!tok) { ok = false; return h }
        return `var(--hf-${tok})`
      })
      if (!ok) return skip(`no token for this colour in role "${role}"`)

      for (const h of node.text.match(HEX_RE)) {
        const hex = normHex(h), tok = MAP[hex][role]
        bump(report.convertedByToken, tok)
        if (lightValues[tok] !== hex) bump(report.normalised, `${hex} -> ${tok} (${lightValues[tok]})`)
        report.converted++
      }
      const q = text[node.getStart(sf)]
      const body = q === '`' ? replaced.replace(/`/g, '\\`') : replaced.replace(new RegExp(q, 'g'), '\\' + q)
      edits.push([node.getStart(sf), node.getEnd(), q + body + q])
      bump(report.files, rel)
      return
    }
    ts.forEachChild(node, visit)
  }
  visit(sf)

  if (WRITE && edits.length) {
    let out = text
    for (const [s, e, r] of edits.sort((a, b) => b[0] - a[0])) out = out.slice(0, s) + r + out.slice(e)
    fs.writeFileSync(file, out)
  }
}

const sortObj = o => Object.fromEntries(Object.entries(o).sort((a, b) => b[1] - a[1]))
report.convertedByToken = sortObj(report.convertedByToken)
report.normalised = sortObj(report.normalised)
report.skipped = sortObj(report.skipped)
report.totalSkipped = Object.values(report.skipped).reduce((a, b) => a + b, 0)
report.filesTouched = Object.keys(report.files).length
report.files = sortObj(report.files)
fs.writeFileSync(path.join(here, 'report.json'), JSON.stringify(report, null, 2))
console.log(`${WRITE ? 'APPLIED' : 'DRY RUN'}: converted ${report.converted} colours in ${report.filesTouched} files; skipped ${report.totalSkipped}; ${Object.keys(report.unsafeFiles).length} unsafe files`)
console.log('Top skip reasons:', Object.entries(report.skipped).slice(0, 8))
