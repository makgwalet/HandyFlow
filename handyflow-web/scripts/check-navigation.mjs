#!/usr/bin/env node
/**
 * Registry coverage (runs after the section checks):
 *   - the sidebar registry (navigation/modules.ts) and the dashboard registry (pages/dashboard/
 *     DashboardPage.tsx) list the same module keys. The dashboard silently drops a subscribed
 *     module it has no tile for, and so does the sidebar, so a mismatch hides a module;
 *   - every top-level route in App.tsx is owned by a registry entry, one of its aliases, or a
 *     workspace link, or is listed in NON_MODULE_ROUTES below with a reason.
 *
 * Integrity check for the sidebar section navigation.
 *
 *   npm run check:navigation      (exit 1 if anything is inconsistent)
 *
 * For every `export const X_SECTIONS: ModuleSections` in
 * src/navigation/moduleSections.ts it verifies that:
 *   - exactly one page imports it, and that page renders content for every
 *     section id (case "id" / "id": <Tab /> / section.id === "id") and for
 *     nothing that is not a section;
 *   - the default section exists;
 *   - the module key is in the sidebar registry (src/navigation/modules.ts),
 *     otherwise the sidebar never switches to the module's sections;
 *   - App.tsx routes `${basePath}/:section?`;
 *   - every literal passed to onNavigate("x") / goTo("x") / { tab: "x" } inside
 *     the module's folder is a real section. (Two dashboards once navigated to
 *     a tab that did not exist, and those buttons opened an empty panel.)
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
// Normalise Windows line endings on read: the parsing below looks for '\n}\n' to find where a block ends, which a CRLF checkout
// (git autocrlf on Windows) never matches, so every block silently ran to the end of the file and reported dozens of false problems.
const readText = f => fs.readFileSync(f, 'utf8').replace(/\r\n?/g, '\n')
const src = p => readText(path.join(root, 'src', p))
const nav = src('navigation/moduleSections.ts')
const registry = src('navigation/modules.ts')
const app = src('App.tsx')

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, out)
    else if (/\.tsx$/.test(e.name)) out.push(p)
  }
  return out
}
const stripComments = t => t.replace(/\/\*[\s\S]*?\*\//g, '').split('\n').filter(l => !l.trim().startsWith('//')).join('\n')

const pageFiles = walk(path.join(root, 'src/pages'))
const pageText = new Map(pageFiles.map(f => [f, readText(f)]))
const problems = []
const consts = [...nav.matchAll(/export const (\w+_SECTIONS): ModuleSections/g)].map(m => m[1])

for (const c of consts) {
  const start = nav.indexOf(`export const ${c}`)
  const blk = nav.slice(start, nav.indexOf('\n}\n', start))
  const ids = new Set([...blk.matchAll(/id: '([a-z-]+)'/g)].map(m => m[1]))
  const key = /moduleKey: '([a-z_-]+)'/.exec(blk)?.[1]
  const base = /basePath: '([^']+)'/.exec(blk)?.[1]
  const def = /defaultSection: '([^']+)'/.exec(blk)?.[1]
  const fail = msg => problems.push(`${c}: ${msg}`)

  const users = pageFiles.filter(f => new RegExp(`\\b${c}\\b`).test(pageText.get(f)))
  if (users.length !== 1) { fail(`imported by ${users.length} pages (expected 1)`); continue }
  const page = users[0], text = pageText.get(page), dir = path.dirname(page)

  const cases = new Set([
    ...[...text.matchAll(/case ["']([a-z-]+)["']/g)].map(m => m[1]),
    ...[...text.matchAll(/^\s+"([a-z-]+)":\s+</gm)].map(m => m[1]),
    ...[...text.matchAll(/section\.id === "([a-z-]+)"/g)].map(m => m[1]),
  ])
  const missing = [...ids].filter(i => !cases.has(i)), extra = [...cases].filter(i => !ids.has(i))
  if (missing.length) fail(`no content for ${missing.join(', ')}`)
  if (extra.length) fail(`content for non-sections ${extra.join(', ')}`)
  if (!ids.has(def)) fail(`default section '${def}' is not a section`)
  if (!new RegExp(`^\\s*'?${key}'?:\\s*\\{\\s*icon`, 'm').test(registry)) fail(`module key '${key}' is not in the sidebar registry`)
  if (!app.includes(`path="${base}/:section?"`)) fail(`App.tsx has no route ${base}/:section?`)

  const targets = new Set()
  for (const [f, t] of pageText) {
    if (!f.startsWith(dir + path.sep) && path.dirname(f) !== dir) continue
    const clean = stripComments(t)
    for (const m of clean.matchAll(/\b(?:onNavigate|onNav|goTo|goToSection)\(\s*["']([a-z][a-z-]*)["']/g)) targets.add(m[1])
    if (/Dashboard|Page/.test(path.basename(f))) for (const m of clean.matchAll(/\btab:\s*["']([a-z][a-z-]*)["']/g)) targets.add(m[1])
  }
  const badTargets = [...targets].filter(t => !ids.has(t))
  if (badTargets.length) fail(`navigation targets that are not sections: ${badTargets.join(', ')}`)
}

// ---- Registry coverage -------------------------------------------------------------------
const dashboard = src('pages/dashboard/DashboardPage.tsx')
const between = (text, startRe, end = '\n}\n') => { const m = startRe.exec(text); return m ? text.slice(m.index, text.indexOf(end, m.index)) : '' }
const navBlock = between(registry, /export const MODULE_REGISTRY/)
const dashBlock = between(dashboard, /const MODULE_REGISTRY: Record<string, AppTile> = \{/)
const keysOf = (blk, first) => new Set([...blk.matchAll(new RegExp(`^\\s*'?([a-z_-]+)'?:\\s*\\{\\s*${first}`, 'gm'))].map(m => m[1]))
const navKeys = keysOf(navBlock, 'icon'), dashKeys = keysOf(dashBlock, 'key')
if (!navKeys.size || !dashKeys.size) problems.push('registry: could not read module keys (did the registry format change?)')
for (const k of navKeys) if (!dashKeys.has(k)) problems.push(`registry: module '${k}' is in the sidebar registry but has no dashboard tile`)
for (const k of dashKeys) if (!navKeys.has(k)) problems.push(`registry: module '${k}' has a dashboard tile but is not in the sidebar registry`)

// Routes that are deliberately not modules: auth screens, public and external-user surfaces, home, profile.
const NON_MODULE_ROUTES = new Set([
  '/login', '/register', '/forgot-password', '/reset-password', '/verify-email', '/account-locked', // sign-in flow
  '/dashboard',                                                                                      // "All modules" home
  '/portal', '/auditor', '/careers', '/sign', '/unsubscribe', '/invite',                            // client, auditor and public pages
  '/profile',                                                                                        // user menu
])
const owned = new Set([...registry.matchAll(/route:\s*'(\/[^']*)'/g)].map(m => m[1]))
for (const m of registry.matchAll(/aliases:\s*\[([^\]]*)\]/g)) for (const a of m[1].matchAll(/'(\/[^']*)'/g)) owned.add(a[1])
const appBases = new Set([...app.matchAll(/<Route\s+path="(\/[^"]*)"/g)].map(m => '/' + m[1].split('/')[1]).filter(b => b !== '/'))
for (const b of [...appBases].sort()) {
  if (!owned.has(b) && !NON_MODULE_ROUTES.has(b)) problems.push(`routes: ${b} is routed in App.tsx but no registry entry, alias or workspace link owns it (add it to navigation/modules.ts, or to NON_MODULE_ROUTES in this script with a reason)`)
}

if (problems.length) { console.error(problems.join('\n')); console.error(`\n${problems.length} problem(s) in ${consts.length} section configs`); process.exit(1) }
console.log(`${consts.length} section configs OK; ${navKeys.size} modules and ${appBases.size} route bases covered`)
