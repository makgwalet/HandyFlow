#!/usr/bin/env node
/**
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
const src = p => fs.readFileSync(path.join(root, 'src', p), 'utf8')
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
const pageText = new Map(pageFiles.map(f => [f, fs.readFileSync(f, 'utf8')]))
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

if (problems.length) { console.error(problems.join('\n')); console.error(`\n${problems.length} problem(s) in ${consts.length} section configs`); process.exit(1) }
console.log(`${consts.length} section configs OK`)
