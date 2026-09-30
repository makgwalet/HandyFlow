/**
 * Shared by split-constants.mjs and split-shared-constant.mjs.
 * How does a piece of code CONSUME a colour: as text, as a fill/border, or in a
 * way that cannot take a CSS variable (SVG attribute, canvas)?
 */
import fs from 'node:fs'
import path from 'node:path'
import ts from 'typescript'

export const FG = new Set(['color', 'fg', 'iconColor', 'textColor', 'caretColor', 'c', 'labelColor', 'valueColor'])
export const FILL = new Set(['background', 'backgroundColor', 'bg', 'border', 'borderColor', 'borderTop', 'borderBottom',
  'borderLeft', 'borderRight', 'borderTopColor', 'borderBottomColor', 'borderLeftColor', 'borderRightColor',
  'boxShadow', 'outline', 'outlineColor', 'accentColor', 'fill', 'stroke', 'dot', 'iconBg'])
export const JSX_FG = new Set(['color', 'textColor'])
export const JSX_FILL = new Set(['bg', 'background', 'border', 'borderColor', 'accent'])
export const JSX_SVG = new Set(['fill', 'stroke', 'stopColor', 'floodColor'])


export function walk(p, out = []) {
  if (fs.statSync(p).isFile()) { if (/\.tsx?$/.test(p)) out.push(p); return out }
  for (const e of fs.readdirSync(p)) { if (e === 'node_modules' || e.startsWith('.')) continue; walk(path.join(p, e), out) }
  return out
}

/** 'fg' | 'fill' | 'svg' | 'canvas' | 'unknown' for one identifier usage. */
export function roleOf(id) {
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

export const isReference = id => {
  const p = id.parent
  if (ts.isVariableDeclaration(p) && p.name === id) return false
  if ((ts.isPropertyAssignment(p) || ts.isPropertySignature?.(p)) && p.name === id) return false // object key
  if (ts.isPropertyAccessExpression(p) && p.name === id) return false
  if (ts.isJsxAttribute(p) && p.name === id) return false
  if (ts.isImportSpecifier(p) || ts.isExportSpecifier(p)) return false
  return true
}

