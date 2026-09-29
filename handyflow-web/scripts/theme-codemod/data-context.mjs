/**
 * Shared by codemod.mjs, jsx-colors.mjs and audit-data-colors.mjs.
 *
 * A colour is DATA, not styling, when it is:
 *   - form / initial state:   useState({ color: ... })
 *   - a request payload:      apiClient.post(url, { color: ... }), mutate({ ... })
 *   - persisted:              localStorage.setItem, JSON.stringify
 *   - fed into a state setter: setForm({ color: s.color ?? ... })
 *   - a data-named constant:  EMPTY_FORM, DEFAULT_*, initial*, ...
 * In those places a colour must stay a literal: the database column may be
 * VARCHAR(7), other code may do hex maths on it, and other clients cannot
 * resolve our CSS variables. (Real bug this prevents: a new bookable service
 * defaulted to a CSS variable name, which overflows `color VARCHAR(7)`.)
 */
import ts from 'typescript'

const STATE_HOOKS = new Set(['useState', 'useReducer'])
const WRITE_VERBS = new Set(['post', 'put', 'patch', 'delete'])
// Any identifier that looks like form/default state: EMPTY_FORM, blankForm, initial, defaults, ...
const DATA_NAME = /empty|blank|initial|default|form|draft|template|preset|seed/i

const calleeName = call => {
  const e = call.expression
  if (ts.isIdentifier(e)) return e.text
  if (ts.isPropertyAccessExpression(e)) return e.name.text
  return ''
}
const calleeObject = call => {
  const e = call.expression
  return ts.isPropertyAccessExpression(e) ? e.expression.getText() : ''
}

/** Why this node is in a data position, or null if it is (probably) styling. */
export function classifyDataPosition(node, sf) {
  for (let n = node; n.parent; n = n.parent) {
    const p = n.parent
    // A JSX style attribute or `.style.x =` is styling. Stop: everything above is display.
    if (ts.isJsxAttribute(p) && p.name.getText(sf) === 'style') return null
    if (ts.isBinaryExpression(p) && p.operatorToken.kind === ts.SyntaxKind.EqualsToken &&
        ts.isPropertyAccessExpression(p.left) && ts.isPropertyAccessExpression(p.left.expression) &&
        p.left.expression.name.text === 'style') return null

    if (ts.isCallExpression(p) && p.arguments.some(a => a === n || (a.pos <= n.pos && a.end >= n.end))) {
      const name = calleeName(p)
      if (STATE_HOOKS.has(name)) return `initial state (${name})`
      if (WRITE_VERBS.has(name) && /api|client|axios|http/i.test(calleeObject(p))) return `request payload (${calleeObject(p)}.${name})`
      if (name === 'mutate' || name === 'mutateAsync') return `request payload (${name})`
      if (name === 'fetch') return 'request payload (fetch)'
      if (name === 'setItem' && /(local|session)Storage/.test(calleeObject(p))) return 'persisted (Storage.setItem)'
      if (name === 'stringify' && calleeObject(p) === 'JSON') return 'serialised (JSON.stringify)'
      if (/^set[A-Z]/.test(name) && !/^set(Open|Show|Hide|Loading|Tab|Page|Sort|Search|Filter|Expanded|Selected|Error|Toast|Menu|Modal|Drawer|Hover|Active|Confirm|Edit|Delete)/.test(name))
        return `state setter (${name})`
    }
    if (ts.isVariableDeclaration(p) && p.initializer && (n === p.initializer || p.initializer.pos <= n.pos && p.initializer.end >= n.end) &&
        ts.isIdentifier(p.name) && DATA_NAME.test(p.name.text)) return `data-named constant (${p.name.text})`
    if (ts.isSourceFile(p)) return null
  }
  return null
}

