#!/usr/bin/env node
/**
 * Prepares files for the main token codemod by removing the two patterns
 * that make CSS variables unsafe:
 *
 *  A. Alpha suffixes in templates:  `${c}18`, `1px solid ${c}30`
 *     -> `color-mix(in srgb, ${c} 9%, transparent)` (same visual result, and
 *     works whether `c` is a hex value or a var(--hf-*) token).
 *     Only inside values of known colour properties (background, border,
 *     color, boxShadow, ...), or `.style.x =` assignments.
 *
 *  B. Colour props on lucide-react icons:  <Pin color="#94A3B8" />
 *     -> <Pin style={{ color: 'var(--hf-text-faint)' }} />
 *     Lucide draws with currentColor, so this is visually identical, and
 *     unlike an SVG attribute it resolves CSS variables. Only for components
 *     imported from 'lucide-react' that have no style or spread props.
 *
 *   node scripts/theme-codemod/jsx-colors.mjs [--write] [path ...]
 * Then run codemod.mjs on the same paths.
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const args = process.argv.slice(2)
const WRITE = args.includes('--write')
const targets = args.filter(a => !a.startsWith('--'))
const scanRoots = targets.length ? targets.map(t => path.resolve(root, t)) : [path.join(root, 'src')]
const MAP = JSON.parse(fs.readFileSync(path.join(here, 'token-map.json'), 'utf8'))

const COLOUR_PROPS = new Set([
  'color', 'background', 'backgroundColor', 'border', 'borderColor', 'borderTop', 'borderBottom',
  'borderLeft', 'borderRight', 'borderTopColor', 'borderBottomColor', 'borderLeftColor',
  'borderRightColor', 'outline', 'outlineColor', 'boxShadow', 'fill', 'stroke', 'bg', 'fg',
])
const HEX_ONLY = /^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})$/

function walk(dir, out = []) {
  if (fs.statSync(dir).isFile()) { if (/\.tsx?$/.test(dir)) out.push(dir); return out }
  for (const e of fs.readdirSync(dir)) {
    if (e === 'node_modules' || e.startsWith('.')) continue
    walk(path.join(dir, e), out)
  }
  return out
}

/** Property name that owns this expression, climbing through ternaries etc. */
function owningProp(node) {
  let n = node
  while (n.parent) {
    const p = n.parent
    if (ts.isConditionalExpression(p) || ts.isParenthesizedExpression(p) || ts.isAsExpression(p) ||
        (ts.isBinaryExpression(p) && [ts.SyntaxKind.BarBarToken, ts.SyntaxKind.QuestionQuestionToken].includes(p.operatorToken.kind))) {
      n = p; continue
    }
    if (ts.isPropertyAssignment(p) && p.initializer === n) {
      return ts.isIdentifier(p.name) || ts.isStringLiteral(p.name) ? p.name.text : null
    }
    if (ts.isBinaryExpression(p) && p.operatorToken.kind === ts.SyntaxKind.EqualsToken && p.right === n &&
        ts.isPropertyAccessExpression(p.left) && ts.isPropertyAccessExpression(p.left.expression) &&
        p.left.expression.name.text === 'style') {
      return p.left.name.text
    }
    return null
  }
  return null
}

const report = { alpha: 0, iconLiteral: 0, iconExpression: 0, skippedIcons: {}, files: {} }
const bump = (o, k) => { o[k] = (o[k] || 0) + 1 }

for (const file of scanRoots.flatMap(r => walk(r))) {
  const text = fs.readFileSync(file, 'utf8')
  const rel = path.relative(root, file)
  const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true,
    file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)

  // Names imported from lucide-react in this file.
  const lucide = new Set()
  for (const st of sf.statements) {
    if (ts.isImportDeclaration(st) && ts.isStringLiteral(st.moduleSpecifier) &&
        st.moduleSpecifier.text === 'lucide-react' && st.importClause?.namedBindings &&
        ts.isNamedImports(st.importClause.namedBindings)) {
      for (const el of st.importClause.namedBindings.elements) lucide.add(el.name.text)
    }
  }

  const edits = [] // [start, end, replacement]

  const visit = node => {
    // A. alpha suffix inside a template feeding a colour property
    if (ts.isTemplateExpression(node) && COLOUR_PROPS.has(owningProp(node) ?? '')) {
      for (const span of node.templateSpans) {
        const lit = span.literal
        const raw = text.slice(lit.getStart(sf), lit.getEnd()) // starts with "}"
        const m = /^\}([0-9a-fA-F]{2})(?![0-9a-zA-Z])/.exec(raw)
        if (!m) continue
        const dollar = span.expression.getStart(sf) - 2
        if (text.slice(dollar, dollar + 2) !== '${') continue
        const pct = Math.round((parseInt(m[1], 16) / 255) * 100)
        edits.push([dollar, dollar, 'color-mix(in srgb, '])
        edits.push([lit.getStart(sf), lit.getStart(sf) + 3, `} ${pct}%, transparent)`])
        report.alpha++; bump(report.files, rel)
      }
    }

    // B. color= on lucide icons
    if ((ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node))) {
      const tag = node.tagName.getText(sf)
      const attrs = node.attributes.properties
      const colorAttr = attrs.find(a => ts.isJsxAttribute(a) && a.name.getText(sf) === 'color')
      if (colorAttr) {
        const styleAttr = attrs.find(a => ts.isJsxAttribute(a) && a.name.getText(sf) === 'style')
        // An existing style={{ ... }} object literal can take the colour too.
        const styleObj = styleAttr?.initializer && ts.isJsxExpression(styleAttr.initializer) &&
          styleAttr.initializer.expression && ts.isObjectLiteralExpression(styleAttr.initializer.expression)
          ? styleAttr.initializer.expression : null
        const styleHasColor = styleObj?.properties.some(pr => pr.name && pr.name.getText(sf) === 'color')
        const why = !lucide.has(tag) ? 'not a lucide-react icon'
          : attrs.some(a => ts.isJsxSpreadAttribute(a)) ? 'has spread props'
          : styleAttr && (!styleObj || styleHasColor) ? 'style is not a plain object literal (or sets color)'
          : null
        if (why) { bump(report.skippedIcons, why) }
        else {
          const init = colorAttr.initializer
          let expr = null
          if (init && ts.isStringLiteral(init)) {
            if (HEX_ONLY.test(init.text)) {
              const h = init.text.toUpperCase()
              const hex = h.length === 4 ? '#' + [...h.slice(1)].map(c => c + c).join('') : h
              const tok = MAP[hex]?.fg
              if (tok) { expr = `'var(--hf-${tok})'`; report.iconLiteral++ } else bump(report.skippedIcons, 'no fg token for ' + hex)
            } else if (init.text === 'currentColor') {
              bump(report.skippedIcons, 'currentColor (fine as is)')
            } else {
              expr = JSON.stringify(init.text); report.iconLiteral++
            }
          } else if (init && ts.isJsxExpression(init) && init.expression) {
            expr = init.expression.getText(sf); report.iconExpression++
          }
          if (expr && styleObj) {
            // Remove the color attribute (and the space before it), then add
            // `color: ...` as the first property of the existing style object.
            let s = colorAttr.getStart(sf)
            while (s > 0 && /[ \t]/.test(text[s - 1])) s--
            edits.push([s, colorAttr.getEnd(), ''])
            const open = styleObj.getStart(sf) + 1
            edits.push([open, open, ` color: ${expr},`])
            bump(report.files, rel)
          } else if (expr) {
            edits.push([colorAttr.getStart(sf), colorAttr.getEnd(), `style={{ color: ${expr} }}`])
            bump(report.files, rel)
          }
        }
      }
    }
    ts.forEachChild(node, visit)
  }
  visit(sf)

  if (WRITE && edits.length) {
    let out = text
    for (const [s, e, r] of edits.sort((a, b) => b[0] - a[0] || b[1] - a[1])) out = out.slice(0, s) + r + out.slice(e)
    fs.writeFileSync(file, out)
  }
}

console.log(`${WRITE ? 'APPLIED' : 'DRY RUN'}: alpha ${report.alpha}, icon literals ${report.iconLiteral}, icon expressions ${report.iconExpression}, files ${Object.keys(report.files).length}`)
console.log('Icon props left alone:', report.skippedIcons)
