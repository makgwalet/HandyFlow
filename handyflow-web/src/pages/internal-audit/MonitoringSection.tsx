// Continuous controls monitoring: patterns in journals, bank payments and permissions for a person to review (audit backlog A22).
import { useEffect, useState } from "react"
import { auditMonitoringApi as api, type MonAlert, type MonAlerts, type MonScan } from "../../api/auditMonitoring.api"
import { Card, Tile, TileRow } from "../../components/DashboardBits"

const MUTED = "var(--hf-text-muted)"
const BORDER = "var(--hf-border)"
const MIN = 10
const msg = (e: any, f: string) => e?.response?.data?.message ?? f
const btn: React.CSSProperties = { padding: "6px 12px", fontSize: 12, fontWeight: 700, cursor: "pointer", borderRadius: 6, border: `1px solid ${BORDER}`, background: "var(--hf-surface)" }
const area: React.CSSProperties = { width: "100%", minHeight: 48, padding: 8, border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, fontFamily: "inherit", boxSizing: "border-box" }
const STATUS: Record<string, string> = { NEW: "New", IN_REVIEW: "In review", DISMISSED: "Dismissed", ESCALATED: "Escalated" }
const SEV: Record<string, string> = { HIGH: "High", MEDIUM: "Medium", LOW: "Low" }

export function MonitoringSection() {
  const [data, setData] = useState<MonAlerts | null>(null)
  const [scan, setScan] = useState<MonScan | null>(null)
  const [findings, setFindings] = useState<{ id: string; title: string }[]>([])
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [show, setShow] = useState<"open" | "closed">("open")
  const [edit, setEdit] = useState<{ id: string; status: string; note: string; findingId: string } | null>(null)

  useEffect(() => {
    api.list().then(setData).catch(e => setError(msg(e, "Failed to load the alerts")))
    api.findings().then(setFindings).catch(() => setFindings([]))
  }, [])

  const runScan = async () => {
    setBusy(true); setError("")
    try { setScan(await api.scan()); setData(await api.list()) } catch (e: any) { setError(msg(e, "The scan failed")) } finally { setBusy(false) }
  }
  const review = async () => {
    if (!edit) return
    setBusy(true); setError("")
    try { setData(await api.review(edit.id, edit.status, edit.note, edit.status === "ESCALATED" && edit.findingId ? edit.findingId : null)); setEdit(null) }
    catch (e: any) { setError(msg(e, "Failed to save")) } finally { setBusy(false) }
  }

  const c = data?.counts
  const list = (data?.alerts ?? []).filter(a => (show === "open") === (a.status === "NEW" || a.status === "IN_REVIEW"))
  return (
    <div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      {data && <div style={{ fontSize: 12.5, color: MUTED, marginBottom: 10 }}>{data.notice}</div>}
      {c && (
        <TileRow>
          <Tile label="Open alerts" value={c.open} />
          <Tile label="High" value={c.high} tone={c.high ? "warning" : undefined} />
          <Tile label="Medium" value={c.medium} />
          <Tile label="Low" value={c.low} />
        </TileRow>
      )}
      <Card title="Scan">
        <div style={{ fontSize: 13, color: MUTED, marginBottom: 6 }}>A scan also runs every night. It reads the last 90 days of journals and bank payments and everyone's permissions, and never changes any of them.</div>
        <button style={btn} disabled={busy} onClick={runScan}>Scan now</button>
        {scan && <span role="status" style={{ marginLeft: 8, fontSize: 13 }}>{scan.newAlerts} new, {scan.seenAgain} already known ({scan.journalsChecked} journals, {scan.bankPaymentsChecked} bank payments, {scan.peopleChecked} people checked).</span>}
      </Card>
      <Card title="Alerts">
        <div style={{ marginBottom: 8 }}>
          <button style={{ ...btn, marginRight: 6, fontWeight: show === "open" ? 800 : 600 }} onClick={() => setShow("open")}>Open</button>
          <button style={{ ...btn, fontWeight: show === "closed" ? 800 : 600 }} onClick={() => setShow("closed")}>Closed</button>
        </div>
        {list.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>{show === "open" ? "Nothing waiting for review." : "No closed alerts."}</span> : list.map(a => row(a))}
      </Card>
    </div>
  )

  // A plain function, not a component: a component defined here would be rebuilt on every keystroke and the note box would lose focus.
  function row(a: MonAlert) {
    const open = edit?.id === a.id
    const closed = a.status === "DISMISSED" || a.status === "ESCALATED"
    return (
      <div key={a.id} style={{ padding: "8px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
        <div><strong>{SEV[a.severity]}</strong> · {a.summary} <span style={{ color: MUTED }}>· {STATUS[a.status]}</span></div>
        {a.detail && <div style={{ color: MUTED, marginTop: 2 }}>{a.detail}</div>}
        {a.disposition && <div style={{ marginTop: 4 }}>{a.reviewedByName}: {a.disposition}</div>}
        {!closed && !open && <button style={{ ...btn, marginTop: 6 }} onClick={() => setEdit({ id: a.id, status: "IN_REVIEW", note: "", findingId: "" })}>Review</button>}
        {open && edit && (
          <div style={{ display: "grid", gap: 6, marginTop: 6 }}>
            <select aria-label="Decision" value={edit.status} onChange={e => setEdit({ ...edit, status: e.target.value })}>
              <option value="IN_REVIEW">Take it up</option><option value="DISMISSED">Dismiss: acceptable</option><option value="ESCALATED">Escalate: needs action</option>
            </select>
            {edit.status === "ESCALATED" && (
              <select aria-label="Finding" value={edit.findingId} onChange={e => setEdit({ ...edit, findingId: e.target.value })}>
                <option value="">Link a finding (optional)…</option>{findings.map(f => <option key={f.id} value={f.id}>{f.title}</option>)}
              </select>
            )}
            <textarea aria-label="Reason" style={area} placeholder={edit.status === "IN_REVIEW" ? "Notes (optional)" : "Why? (at least 10 characters)"} value={edit.note} onChange={e => setEdit({ ...edit, note: e.target.value })} />
            <div><button style={btn} disabled={busy || (edit.status !== "IN_REVIEW" && edit.note.trim().length < MIN)} onClick={review}>Save</button> <button style={btn} onClick={() => setEdit(null)}>Cancel</button></div>
          </div>
        )}
      </div>
    )
  }
}
