// Who can do what in Legal Practice: every permission with how sensitive it is, the starting role templates, and a check that flags risky combinations
// (for example one role that can both request and approve a payment out of trust). Roles themselves are built in Roles; this screen only reads and checks.
import { useState } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Empty, ErrorLine, Pill, SectionTitle, card, errorText, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import { accessClipboard, accessFindingTone, accessGroups, accessLevelText, accessLevelTone, accessSummary, accessToggle, type AccessCatalogue, type AccessFinding } from "./legal.logic"

const URL = `${BASE}/access`
const unwrap = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T

export default function AccessTab() {
  const [view, setView] = useState<"templates" | "permissions" | "check">("templates")
  const q = useQuery({ queryKey: ["lp-access"], queryFn: async () => unwrap<AccessCatalogue>(await apiClient.get(`${URL}/catalogue`)) })
  const c = q.data
  return (
    <div>
      <SectionTitle>Access</SectionTitle>
      <div style={{ ...muted, marginBottom: 8 }}>Build the firm's roles in Roles. These are the Legal Practice permissions, some starting points for common roles, and a check for combinations a law firm should avoid.</div>
      <div style={{ display: "flex", gap: 6, marginBottom: 10 }}>
        <Btn small kind={view === "templates" ? "primary" : undefined} onClick={() => setView("templates")}>Role templates</Btn>
        <Btn small kind={view === "permissions" ? "primary" : undefined} onClick={() => setView("permissions")}>Permissions</Btn>
        <Btn small kind={view === "check" ? "primary" : undefined} onClick={() => setView("check")}>Check a role</Btn>
      </div>
      {q.isError ? <Empty>Could not load the permissions.</Empty> : q.isLoading || !c ? <Empty>Loading…</Empty> : view === "templates" ? <Templates c={c} /> : view === "permissions" ? <Permissions c={c} /> : <Check c={c} />}
    </div>
  )
}

function Findings({ list }: { list: AccessFinding[] }) {
  return <>{list.map(f => <div key={f.code} style={{ marginTop: 6, fontSize: 13 }}><Pill tone={accessFindingTone(f.severity)}>{f.severity === "WARNING" ? "Warning" : "Note"}</Pill> {f.message}</div>)}</>
}

function Templates({ c }: { c: AccessCatalogue }) {
  const names = new Map(c.permissions.map(p => [p.code, p]))
  const copy = async (t: AccessCatalogue["templates"][number]) => {
    try { await navigator.clipboard.writeText(accessClipboard(t)); toast.success("Copied") } catch { toast.error("Could not copy. Select the list and copy it by hand") }
  }
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 12 }}>
      {c.templates.map(t => (
        <div key={t.key} style={{ ...card, padding: 14 }}>
          <div style={{ fontWeight: 700 }}>{t.name}</div>
          <div style={{ ...muted, margin: "4px 0 8px" }}>{t.description}</div>
          <div style={{ fontSize: 12, marginBottom: 6 }}>{accessSummary(t, c.permissions)}</div>
          <div style={{ display: "flex", gap: 4, flexWrap: "wrap", marginBottom: 8 }}>
            {t.permissions.map(code => <Pill key={code} tone={accessLevelTone(names.get(code)?.level ?? "NORMAL")}>{code.replace("LEGALPRACTICE_", "").replace(/_/g, " ").toLowerCase()}</Pill>)}
          </div>
          <Findings list={t.findings} />
          <div style={{ marginTop: 8 }}><Btn small onClick={() => copy(t)}>Copy the permission list</Btn></div>
        </div>))}
    </div>
  )
}

function Permissions({ c }: { c: AccessCatalogue }) {
  return (
    <div>
      {accessGroups(c.permissions).map(g => (
        <div key={g.group} style={{ marginBottom: 12 }}>
          <div style={{ fontWeight: 700, margin: "6px 0" }}>{g.group}</div>
          <div style={{ ...card, padding: 0, overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Permission</th><th style={th}>What it allows</th><th style={th}>Sensitivity</th></tr></thead>
              <tbody>{g.items.map(p => (
                <tr key={p.code}>
                  <td style={td}><code>{p.code}</code></td>
                  <td style={td}>{p.description}</td>
                  <td style={td}><Pill tone={accessLevelTone(p.level)}>{accessLevelText(p.level)}</Pill>{p.secondPerson && <div style={muted}>Needs a second person</div>}</td>
                </tr>))}</tbody>
            </table>
          </div>
        </div>))}
    </div>
  )
}

function Check({ c }: { c: AccessCatalogue }) {
  const [picked, setPicked] = useState<string[]>([]), [result, setResult] = useState<{ held: number; findings: AccessFinding[] } | null>(null), [err, setErr] = useState("")
  const run = useMutation({
    mutationFn: async () => unwrap<{ held: number; findings: AccessFinding[] }>(await apiClient.post(`${URL}/check`, { permissions: picked })),
    onSuccess: setResult, onError: async e => setErr(await errorText(e, "Could not check the role")),
  })
  return (
    <div>
      <div style={{ ...muted, marginBottom: 8 }}>Tick what a role would hold and check it. Nothing is saved.</div>
      <div style={{ display: "flex", gap: 6, marginBottom: 10, flexWrap: "wrap" }}>
        {c.templates.map(t => <Btn key={t.key} small onClick={() => { setPicked(t.permissions); setResult(null) }}>Start from {t.name}</Btn>)}
        <Btn small onClick={() => { setPicked([]); setResult(null) }}>Clear</Btn>
      </div>
      <div style={{ ...card, padding: 12 }}>
        {accessGroups(c.permissions).map(g => (
          <div key={g.group} style={{ marginBottom: 8 }}>
            <div style={{ fontWeight: 600, fontSize: 13 }}>{g.group}</div>
            {g.items.map(p => (
              <label key={p.code} style={{ display: "flex", gap: 8, alignItems: "baseline", fontSize: 13, padding: "2px 0" }}>
                <input type="checkbox" checked={picked.includes(p.code)} onChange={() => { setPicked(accessToggle(picked, p.code)); setResult(null) }} />
                <span><code>{p.code.replace("LEGALPRACTICE_", "")}</code> <span style={muted}>{p.description}</span></span>
              </label>))}
          </div>))}
      </div>
      <div style={{ margin: "10px 0" }}><Btn kind="primary" disabled={picked.length === 0 || run.isPending} onClick={() => { setErr(""); run.mutate() }}>{run.isPending ? "Checking…" : "Check this role"}</Btn></div>
      {err && <ErrorLine text={err} />}
      {result && (result.findings.length === 0
        ? <div style={{ ...card, padding: 12 }}>Nothing to flag. This role holds {result.held} {result.held === 1 ? "permission" : "permissions"}.</div>
        : <div style={{ ...card, padding: 12 }}><div>This role holds {result.held} {result.held === 1 ? "permission" : "permissions"}.</div><Findings list={result.findings} /></div>)}
    </div>
  )
}
