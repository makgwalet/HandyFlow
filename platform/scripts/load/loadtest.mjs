#!/usr/bin/env node
// HandyFlow HTTP load test with pass/fail budgets. Node 18+, no dependencies.
//
//   node platform/scripts/load/loadtest.mjs --config platform/scripts/load/load.config.json --confirm staging.example.com
//   HF_LOAD_PASSWORD=... is read from the environment variable the config names (never put a password in the file).
//
// Exit code: 0 all budgets met, 1 a budget was missed, 2 the test could not run (bad config, cannot sign in, target not confirmed).
// Safety: you must pass --confirm <host> and it must equal the host of baseUrl. This stops a test from hitting production by accident.
import { readFileSync, writeFileSync } from "node:fs"
import { pathToFileURL } from "node:url"

// ---------- pure helpers (tested in loadtest.test.mjs) ----------

/** Nearest-rank percentile of an unsorted list of numbers. p in (0, 100]. Empty list gives null. */
export function percentile(values, p) {
  if (!values.length) return null
  const s = [...values].sort((a, b) => a - b)
  const rank = Math.max(1, Math.ceil((p / 100) * s.length))
  return s[Math.min(rank, s.length) - 1]
}

/** Picks a scenario by weight. `r` is a number in [0, 1). Scenarios with weight 0 are never picked. */
export function pickScenario(scenarios, r) {
  const total = scenarios.reduce((n, s) => n + (s.weight ?? 1), 0)
  let at = r * total
  for (const s of scenarios) {
    const w = s.weight ?? 1
    if (w <= 0) continue
    if (at < w) return s
    at -= w
  }
  return scenarios.filter(s => (s.weight ?? 1) > 0).at(-1)
}

/** Summary of one set of samples: [{ms, ok}] over `seconds` of wall time. */
export function summarise(samples, seconds) {
  const ok = samples.filter(x => x.ok).length
  const lat = samples.map(x => x.ms)
  return {
    count: samples.length, errors: samples.length - ok,
    errorRate: samples.length ? (samples.length - ok) / samples.length : 0,
    rps: seconds > 0 ? samples.length / seconds : 0,
    p50: percentile(lat, 50), p95: percentile(lat, 95), p99: percentile(lat, 99), max: lat.length ? Math.max(...lat) : null,
  }
}

/** Checks a summary against a budget { p95Ms, p99Ms, maxErrorRate, minRps }. Returns a list of plain-language failures. */
export function checkBudget(name, sum, budget) {
  const out = []
  if (!budget) return out
  if (sum.count === 0) return [`${name}: no requests were made`]
  if (budget.p95Ms != null && sum.p95 > budget.p95Ms) out.push(`${name}: p95 ${sum.p95.toFixed(0)} ms is over the budget of ${budget.p95Ms} ms`)
  if (budget.p99Ms != null && sum.p99 > budget.p99Ms) out.push(`${name}: p99 ${sum.p99.toFixed(0)} ms is over the budget of ${budget.p99Ms} ms`)
  if (budget.maxErrorRate != null && sum.errorRate > budget.maxErrorRate) out.push(`${name}: error rate ${(sum.errorRate * 100).toFixed(2)}% is over the budget of ${(budget.maxErrorRate * 100).toFixed(2)}%`)
  if (budget.minRps != null && sum.rps < budget.minRps) out.push(`${name}: ${sum.rps.toFixed(1)} requests/s is under the budget of ${budget.minRps}`)
  return out
}

/** Validates the config and returns a list of problems (empty when fine). */
export function configProblems(c) {
  const p = []
  if (!c || typeof c !== "object") return ["The config is not an object."]
  try { const u = new URL(c.baseUrl); if (!/^https?:$/.test(u.protocol)) p.push("baseUrl must be http or https.") } catch { p.push("baseUrl is not a valid address.") }
  if (!Array.isArray(c.scenarios) || c.scenarios.length === 0) p.push("At least one scenario is needed.")
  else c.scenarios.forEach((s, i) => {
    if (!s.name) p.push(`Scenario ${i + 1} needs a name.`)
    if (!s.path || !String(s.path).startsWith("/")) p.push(`Scenario ${s.name ?? i + 1} needs a path starting with /.`)
    if (!["GET", "POST", "PUT", "DELETE"].includes((s.method ?? "GET").toUpperCase())) p.push(`Scenario ${s.name ?? i + 1} has an unsupported method.`)
    if (s.weight != null && !(s.weight >= 0)) p.push(`Scenario ${s.name ?? i + 1} has a bad weight.`)
  })
  if (c.scenarios?.length && c.scenarios.every(s => (s.weight ?? 1) <= 0)) p.push("Every scenario has weight 0.")
  if (!Array.isArray(c.stages) || c.stages.length === 0) p.push("At least one stage is needed.")
  else c.stages.forEach((s, i) => { if (!(s.seconds > 0) || !(s.users >= 1)) p.push(`Stage ${i + 1} needs seconds > 0 and users >= 1.`) })
  if (!c.budgets || (!c.budgets.overall && !c.budgets.scenarios)) p.push("A load test without budgets cannot fail. Add budgets.overall or budgets.scenarios.")
  const names = new Set((c.scenarios ?? []).map(s => s.name))
  for (const n of Object.keys(c.budgets?.scenarios ?? {})) if (!names.has(n)) p.push(`A budget names the scenario '${n}', which does not exist.`)
  return p
}

/** The host check that stops a test being aimed at the wrong place. */
export function targetConfirmed(baseUrl, confirm) {
  try { return !!confirm && new URL(baseUrl).host.toLowerCase() === String(confirm).toLowerCase() } catch { return false }
}

/** Evaluates every budget. Returns { failures, perScenario, overall }. */
export function evaluate(config, samplesByScenario, seconds) {
  const all = Object.values(samplesByScenario).flat()
  const overall = summarise(all, seconds)
  const per = {}
  const failures = [...checkBudget("overall", overall, config.budgets?.overall)]
  for (const s of config.scenarios) {
    per[s.name] = summarise(samplesByScenario[s.name] ?? [], seconds)
    failures.push(...checkBudget(s.name, per[s.name], config.budgets?.scenarios?.[s.name]))
  }
  return { failures, perScenario: per, overall }
}

export function parseArgs(argv) {
  const a = { config: null, confirm: null, out: null }
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--config") a.config = argv[++i]
    else if (argv[i] === "--confirm") a.confirm = argv[++i]
    else if (argv[i] === "--out") a.out = argv[++i]
    else throw new Error(`Unknown argument ${argv[i]}`)
  }
  return a
}

// ---------- the run ----------

async function signIn(c, fetchFn) {
  if (!c.login) return null
  const password = process.env[c.login.passwordEnv ?? "HF_LOAD_PASSWORD"]
  if (!password) throw new Error(`Set the environment variable ${c.login.passwordEnv ?? "HF_LOAD_PASSWORD"} to the password of the test user.`)
  const r = await fetchFn(new URL(c.login.path ?? "/api/v1/auth/login", c.baseUrl), {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ tenantSlug: c.login.tenantSlug, email: c.login.email, password }),
  })
  if (!r.ok) throw new Error(`Sign-in failed with status ${r.status}.`)
  const j = await r.json()
  const token = j?.data?.accessToken ?? j?.accessToken
  if (!token) throw new Error("Sign-in worked but no accessToken came back.")
  return token
}

/** Runs the stages and returns { samples (by scenario), seconds }. `fetchFn` and `rand` can be replaced in tests. */
export async function run(c, { fetchFn = fetch, rand = Math.random, now = () => performance.now(), sleep = ms => new Promise(r => setTimeout(r, ms)) } = {}) {
  const token = await signIn(c, fetchFn)
  const samples = Object.fromEntries(c.scenarios.map(s => [s.name, []]))
  const timeout = c.timeoutMs ?? 10000, think = c.thinkTimeMs ?? 0
  let stop = false, target = 0
  const alive = []
  // a virtual user stops itself when a later stage asks for fewer users than its number
  const user = async idx => {
    while (!stop && idx < target) {
      const s = pickScenario(c.scenarios, rand())
      const ctl = new AbortController(), timer = setTimeout(() => ctl.abort(), timeout)
      const t0 = now()
      let ok = false
      try {
        const r = await fetchFn(new URL(s.path, c.baseUrl), {
          method: (s.method ?? "GET").toUpperCase(), signal: ctl.signal,
          headers: { ...(token ? { authorization: `Bearer ${token}` } : {}), ...(s.body ? { "content-type": "application/json" } : {}) },
          body: s.body ? JSON.stringify(s.body) : undefined,
        })
        await r.arrayBuffer()
        ok = (s.expect ?? [200]).includes(r.status)
      } catch { ok = false }
      clearTimeout(timer)
      samples[s.name].push({ ms: now() - t0, ok })
      if (think) await sleep(think)
      else await new Promise(r => setImmediate(r))   // let timers (the end of a stage) run even when every response is instant
    }
  }
  const t0 = now()
  const workers = []
  for (const st of c.stages) {
    // step to this stage's number of users (up or down), then hold
    target = st.users
    for (let i = 0; i < target; i++) if (!alive[i]) { alive[i] = true; workers.push(user(i).finally(() => { alive[i] = false })) }
    await sleep(st.seconds * 1000)
  }
  stop = true
  await Promise.all(workers)
  return { samples, seconds: (now() - t0) / 1000 }
}

export function report(result, ev, c) {
  const rows = [["scenario", "requests", "errors", "p50", "p95", "p99", "max", "req/s"]]
  const line = (n, s) => [n, s.count, s.errors, f(s.p50), f(s.p95), f(s.p99), f(s.max), s.rps.toFixed(1)]
  const f = v => v == null ? "-" : v.toFixed(0)
  rows.push(line("ALL", ev.overall)); for (const s of c.scenarios) rows.push(line(s.name, ev.perScenario[s.name]))
  const w = rows[0].map((_, i) => Math.max(...rows.map(r => String(r[i]).length)))
  return rows.map(r => r.map((x, i) => i === 0 ? String(x).padEnd(w[i]) : String(x).padStart(w[i])).join("  ")).join("\n")
}

async function main() {
  let args
  try { args = parseArgs(process.argv.slice(2)) } catch (e) { console.error(e.message); process.exit(2) }
  if (!args.config) { console.error("Usage: loadtest.mjs --config <file> --confirm <host> [--out report.json]"); process.exit(2) }
  let c
  try { c = JSON.parse(readFileSync(args.config, "utf8")) } catch (e) { console.error("Cannot read the config: " + e.message); process.exit(2) }
  const problems = configProblems(c)
  if (problems.length) { console.error("The config has problems:\n - " + problems.join("\n - ")); process.exit(2) }
  if (!targetConfirmed(c.baseUrl, args.confirm)) { console.error(`Refusing to run: pass --confirm ${new URL(c.baseUrl).host} to confirm this is the system to test.`); process.exit(2) }
  let result
  try { result = await run(c) } catch (e) { console.error(e.message); process.exit(2) }
  const ev = evaluate(c, result.samples, result.seconds)
  console.log(report(result, ev, c))
  if (args.out) writeFileSync(args.out, JSON.stringify({ seconds: result.seconds, overall: ev.overall, scenarios: ev.perScenario, failures: ev.failures }, null, 2))
  if (ev.failures.length) { console.error("\nOVER BUDGET:\n - " + ev.failures.join("\n - ")); process.exit(1) }
  console.log("\nAll budgets met.")
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) main()
