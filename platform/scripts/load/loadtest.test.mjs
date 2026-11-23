// Tests for loadtest.mjs:  node --test platform/scripts/load/
import { test } from "node:test"
import assert from "node:assert/strict"
import http from "node:http"
import { spawn } from "node:child_process"
import { writeFileSync, mkdtempSync, readFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join, dirname } from "node:path"
import { fileURLToPath } from "node:url"
import { percentile, pickScenario, summarise, checkBudget, configProblems, targetConfirmed, evaluate, parseArgs, run } from "./loadtest.mjs"

const here = dirname(fileURLToPath(import.meta.url))

test("percentile is nearest-rank", () => {
  const v = Array.from({ length: 100 }, (_, i) => i + 1)
  assert.equal(percentile(v, 50), 50); assert.equal(percentile(v, 95), 95); assert.equal(percentile(v, 99), 99); assert.equal(percentile(v, 100), 100)
  assert.equal(percentile([5], 99), 5); assert.equal(percentile([], 95), null)
  assert.equal(percentile([3, 1, 2], 50), 2, "unsorted input")
  assert.equal(percentile([10, 20, 30, 40], 75), 30)
  assert.equal(percentile([10, 20, 30, 40], 76), 40)
})

test("pickScenario follows the weights and never picks weight 0", () => {
  const sc = [{ name: "a", weight: 3 }, { name: "b", weight: 1 }, { name: "z", weight: 0 }]
  assert.equal(pickScenario(sc, 0).name, "a"); assert.equal(pickScenario(sc, 0.74).name, "a"); assert.equal(pickScenario(sc, 0.75).name, "b"); assert.equal(pickScenario(sc, 0.999).name, "b")
  const counts = { a: 0, b: 0, z: 0 }; for (let i = 0; i < 4000; i++) counts[pickScenario(sc, i / 4000).name]++
  assert.equal(counts.z, 0); assert.equal(counts.a, 3000); assert.equal(counts.b, 1000)
  assert.equal(pickScenario([{ name: "only" }], 0.5).name, "only", "default weight is 1")
})

test("summarise counts errors, rate and throughput", () => {
  const s = summarise([{ ms: 10, ok: true }, { ms: 20, ok: true }, { ms: 30, ok: false }, { ms: 400, ok: true }], 2)
  assert.equal(s.count, 4); assert.equal(s.errors, 1); assert.equal(s.errorRate, 0.25); assert.equal(s.rps, 2); assert.equal(s.max, 400); assert.equal(s.p95, 400)
  const e = summarise([], 5); assert.equal(e.count, 0); assert.equal(e.p95, null); assert.equal(e.errorRate, 0)
})

test("checkBudget reports each miss in words and nothing when met", () => {
  const sum = { count: 100, errors: 5, errorRate: 0.05, rps: 8, p50: 100, p95: 900, p99: 2500, max: 3000 }
  assert.equal(checkBudget("x", sum, { p95Ms: 1000, p99Ms: 3000, maxErrorRate: 0.1, minRps: 5 }).length, 0)
  assert.equal(checkBudget("x", sum, { p95Ms: 800 }).length, 1)
  assert.equal(checkBudget("x", sum, { p95Ms: 899 })[0].includes("p95 900 ms is over the budget of 899 ms"), true)
  assert.equal(checkBudget("x", sum, { p95Ms: 900 }).length, 0, "equal to the budget passes")
  assert.equal(checkBudget("x", sum, { p99Ms: 2000 }).length, 1)
  assert.equal(checkBudget("x", sum, { maxErrorRate: 0.01 }).length, 1)
  assert.equal(checkBudget("x", sum, { minRps: 10 }).length, 1)
  assert.equal(checkBudget("x", sum, { p95Ms: 1, p99Ms: 1, maxErrorRate: 0, minRps: 99 }).length, 4)
  assert.deepEqual(checkBudget("x", { ...sum, count: 0 }, { p95Ms: 1 }), ["x: no requests were made"], "silence is a failure, not a pass")
  assert.deepEqual(checkBudget("x", sum, undefined), [])
})

const good = () => ({
  baseUrl: "http://127.0.0.1:1", stages: [{ seconds: 1, users: 1 }],
  scenarios: [{ name: "a", path: "/a" }], budgets: { overall: { p95Ms: 100 } },
})

test("configProblems", () => {
  assert.deepEqual(configProblems(good()), [])
  assert.ok(configProblems(null).length)
  assert.ok(configProblems({ ...good(), baseUrl: "nope" }).some(x => x.includes("baseUrl")))
  assert.ok(configProblems({ ...good(), baseUrl: "ftp://x.com" }).some(x => x.includes("http")))
  assert.ok(configProblems({ ...good(), scenarios: [] }).some(x => x.includes("scenario")))
  assert.ok(configProblems({ ...good(), scenarios: [{ name: "a", path: "a" }] }).some(x => x.includes("path")))
  assert.ok(configProblems({ ...good(), scenarios: [{ name: "a", path: "/a", method: "PATCH" }] }).some(x => x.includes("method")))
  assert.ok(configProblems({ ...good(), scenarios: [{ name: "a", path: "/a", weight: 0 }] }).some(x => x.includes("weight 0")))
  assert.ok(configProblems({ ...good(), stages: [] }).some(x => x.includes("stage")))
  assert.ok(configProblems({ ...good(), stages: [{ seconds: 0, users: 1 }] }).some(x => x.includes("Stage 1")))
  assert.ok(configProblems({ ...good(), stages: [{ seconds: 5, users: 0 }] }).some(x => x.includes("Stage 1")))
  assert.ok(configProblems({ ...good(), budgets: undefined }).some(x => x.includes("cannot fail")))
  assert.ok(configProblems({ ...good(), budgets: {} }).some(x => x.includes("cannot fail")))
  assert.ok(configProblems({ ...good(), budgets: { scenarios: { ghost: { p95Ms: 1 } } } }).some(x => x.includes("ghost")))
})

test("the target must be confirmed by host", () => {
  assert.equal(targetConfirmed("https://staging.example.com/x", "staging.example.com"), true)
  assert.equal(targetConfirmed("https://staging.example.com", "STAGING.example.com"), true)
  assert.equal(targetConfirmed("http://127.0.0.1:8080", "127.0.0.1:8080"), true)
  assert.equal(targetConfirmed("http://127.0.0.1:8080", "127.0.0.1"), false, "the port is part of the host")
  assert.equal(targetConfirmed("https://app.example.com", "example.com"), false)
  assert.equal(targetConfirmed("https://app.example.com", null), false)
  assert.equal(targetConfirmed("not a url", "x"), false)
})

test("parseArgs", () => {
  assert.deepEqual(parseArgs(["--config", "c.json", "--confirm", "h", "--out", "o.json"]), { config: "c.json", confirm: "h", out: "o.json" })
  assert.throws(() => parseArgs(["--nope"]))
})

test("evaluate checks overall and per-scenario budgets", () => {
  const c = { scenarios: [{ name: "a" }, { name: "b" }], budgets: { overall: { p95Ms: 100 }, scenarios: { b: { p95Ms: 10 } } } }
  const s = { a: [{ ms: 5, ok: true }, { ms: 6, ok: true }], b: [{ ms: 50, ok: true }] }
  const ev = evaluate(c, s, 1)
  assert.equal(ev.failures.length, 1); assert.ok(ev.failures[0].startsWith("b:"))
  assert.equal(ev.overall.count, 3)
  const none = evaluate(c, { a: [], b: [] }, 1)
  assert.ok(none.failures.some(f => f.startsWith("overall: no requests")))
})

test("run with a fake fetch ramps users, signs in once and records failures", async () => {
  const calls = []; let logins = 0
  const fetchFn = async (url, opt) => {
    const u = String(url)
    if (u.endsWith("/login")) { logins++; return { ok: true, status: 200, json: async () => ({ data: { accessToken: "T" } }) } }
    calls.push({ u, auth: opt.headers.authorization })
    await new Promise(r => setTimeout(r, 2))
    return { status: u.endsWith("/bad") ? 500 : 200, arrayBuffer: async () => new ArrayBuffer(0) }
  }
  process.env.HF_T = "pw"
  const c = { baseUrl: "http://x.test", login: { tenantSlug: "t", email: "e", passwordEnv: "HF_T", path: "/login" }, stages: [{ seconds: 0.2, users: 3 }, { seconds: 0.2, users: 1 }],
    scenarios: [{ name: "ok", path: "/ok", weight: 1 }, { name: "bad", path: "/bad", weight: 1 }] }
  const r = await run(c, { fetchFn, rand: (() => { let i = 0; return () => (i++ % 2) / 2 })() })
  assert.equal(logins, 1); assert.ok(calls.length > 10); assert.ok(calls.every(x => x.auth === "Bearer T"))
  assert.ok(r.samples.ok.every(x => x.ok)); assert.ok(r.samples.bad.every(x => !x.ok), "a 500 is an error")
  assert.ok(r.samples.ok.length > 0 && r.samples.bad.length > 0)
})

test("a status that is not in expect counts as an error, and expect can allow others", async () => {
  const fetchFn = async () => ({ status: 404, arrayBuffer: async () => new ArrayBuffer(0) })
  const mk = expect => ({ baseUrl: "http://x.test", stages: [{ seconds: 0.05, users: 1 }], scenarios: [{ name: "n", path: "/n", expect }] })
  const a = await run(mk(undefined), { fetchFn }); assert.ok(a.samples.n.every(x => !x.ok))
  const b = await run(mk([404]), { fetchFn }); assert.ok(b.samples.n.every(x => x.ok))
})

test("a network failure and a timeout are errors, not crashes", async () => {
  const boom = async () => { throw new Error("down") }
  const a = await run({ baseUrl: "http://x.test", stages: [{ seconds: 0.05, users: 1 }], scenarios: [{ name: "n", path: "/n" }], thinkTimeMs: 5 }, { fetchFn: boom })
  assert.ok(a.samples.n.length > 0 && a.samples.n.every(x => !x.ok))
  const hang = (url, opt) => new Promise((_, rej) => opt.signal.addEventListener("abort", () => rej(new Error("aborted"))))
  const b = await run({ baseUrl: "http://x.test", timeoutMs: 20, stages: [{ seconds: 0.1, users: 1 }], scenarios: [{ name: "n", path: "/n" }] }, { fetchFn: hang })
  assert.ok(b.samples.n.length >= 1 && b.samples.n.every(x => !x.ok && x.ms >= 15))
})

test("sign-in problems stop the run with a clear message", async () => {
  delete process.env.HF_MISSING
  const c = { baseUrl: "http://x.test", login: { tenantSlug: "t", email: "e", passwordEnv: "HF_MISSING" }, stages: [{ seconds: 0.05, users: 1 }], scenarios: [{ name: "n", path: "/n" }] }
  await assert.rejects(run(c, { fetchFn: async () => ({}) }), /HF_MISSING/)
  process.env.HF_MISSING = "pw"
  await assert.rejects(run(c, { fetchFn: async () => ({ ok: false, status: 401 }) }), /status 401/)
  await assert.rejects(run(c, { fetchFn: async () => ({ ok: true, json: async () => ({ data: {} }) }) }), /no accessToken/)
})

// ---- end to end through the command line, against a real local server ----

function serve(handler) {
  return new Promise(res => { const s = http.createServer(handler); s.listen(0, "127.0.0.1", () => res(s)) })
}
function cli(args, env = {}) {
  return new Promise(res => {
    const p = spawn(process.execPath, [join(here, "loadtest.mjs"), ...args], { env: { ...process.env, ...env } })
    let out = "", err = ""; p.stdout.on("data", d => out += d); p.stderr.on("data", d => err += d)
    p.on("close", code => res({ code, out, err }))
  })
}
function write(c) { const d = mkdtempSync(join(tmpdir(), "lt-")); const f = join(d, "c.json"); writeFileSync(f, JSON.stringify(c)); return { f, d } }
const login = (req, res) => { res.setHeader("content-type", "application/json"); res.end(JSON.stringify({ data: { accessToken: "T" } })) }

test("cli: a fast healthy server passes with exit 0 and writes a report", async () => {
  const srv = await serve((req, res) => req.url === "/api/v1/auth/login" ? login(req, res) : (res.end("{}")))
  const base = `http://127.0.0.1:${srv.address().port}`
  const { f, d } = write({ baseUrl: base, login: { tenantSlug: "t", email: "e" }, stages: [{ seconds: 1, users: 4 }], scenarios: [{ name: "a", path: "/a" }],
    budgets: { overall: { p95Ms: 500, maxErrorRate: 0, minRps: 20 } } })
  const out = join(d, "r.json")
  const r = await cli(["--config", f, "--confirm", `127.0.0.1:${srv.address().port}`, "--out", out], { HF_LOAD_PASSWORD: "pw" })
  srv.close()
  assert.equal(r.code, 0, r.err + r.out); assert.match(r.out, /All budgets met/)
  const rep = JSON.parse(readFileSync(out, "utf8")); assert.ok(rep.overall.count > 20); assert.deepEqual(rep.failures, [])
})

test("cli: a slow server fails the p95 budget with exit 1 and says which", async () => {
  const srv = await serve((req, res) => req.url === "/api/v1/auth/login" ? login(req, res) : setTimeout(() => res.end("{}"), 120))
  const { f } = write({ baseUrl: `http://127.0.0.1:${srv.address().port}`, login: { tenantSlug: "t", email: "e" }, stages: [{ seconds: 1, users: 2 }],
    scenarios: [{ name: "slow", path: "/slow" }], budgets: { scenarios: { slow: { p95Ms: 50 } } } })
  const r = await cli(["--config", f, "--confirm", `127.0.0.1:${srv.address().port}`], { HF_LOAD_PASSWORD: "pw" })
  srv.close()
  assert.equal(r.code, 1); assert.match(r.err, /OVER BUDGET/); assert.match(r.err, /slow: p95 \d+ ms is over the budget of 50 ms/)
})

test("cli: a server that fails requests fails the error-rate budget", async () => {
  const srv = await serve((req, res) => { if (req.url === "/api/v1/auth/login") return login(req, res); res.statusCode = 503; res.end("") })
  const { f } = write({ baseUrl: `http://127.0.0.1:${srv.address().port}`, login: { tenantSlug: "t", email: "e" }, stages: [{ seconds: 1, users: 2 }],
    scenarios: [{ name: "a", path: "/a" }], budgets: { overall: { maxErrorRate: 0.01 } } })
  const r = await cli(["--config", f, "--confirm", `127.0.0.1:${srv.address().port}`], { HF_LOAD_PASSWORD: "pw" })
  srv.close()
  assert.equal(r.code, 1); assert.match(r.err, /error rate 100\.00%/)
})

test("cli: refuses to run without the right --confirm, and sends nothing", async () => {
  let hits = 0
  const srv = await serve((req, res) => { hits++; res.end("{}") })
  const { f } = write({ ...good(), baseUrl: `http://127.0.0.1:${srv.address().port}` })
  const none = await cli(["--config", f]); const wrong = await cli(["--config", f, "--confirm", "prod.example.com"])
  srv.close()
  assert.equal(none.code, 2); assert.equal(wrong.code, 2); assert.match(wrong.err, /Refusing to run/); assert.equal(hits, 0)
})

test("cli: a bad config or a failed sign-in exits 2, not 0", async () => {
  const bad = write({ baseUrl: "http://127.0.0.1:1" })
  const r1 = await cli(["--config", bad.f, "--confirm", "127.0.0.1:1"]); assert.equal(r1.code, 2); assert.match(r1.err, /problems/)
  const srv = await serve((req, res) => { res.statusCode = 401; res.end("") })
  const { f } = write({ baseUrl: `http://127.0.0.1:${srv.address().port}`, login: { tenantSlug: "t", email: "e" }, stages: [{ seconds: 1, users: 1 }], scenarios: [{ name: "a", path: "/a" }], budgets: { overall: { p95Ms: 1 } } })
  const r2 = await cli(["--config", f, "--confirm", `127.0.0.1:${srv.address().port}`], { HF_LOAD_PASSWORD: "pw" })
  const r3 = await cli(["--config", f, "--confirm", `127.0.0.1:${srv.address().port}`])
  srv.close()
  assert.equal(r2.code, 2); assert.match(r2.err, /Sign-in failed/); assert.equal(r3.code, 2); assert.match(r3.err, /HF_LOAD_PASSWORD/)
  const r4 = await cli(["--config", "/nonexistent.json", "--confirm", "x"]); assert.equal(r4.code, 2)
  const r5 = await cli([]); assert.equal(r5.code, 2)
})
