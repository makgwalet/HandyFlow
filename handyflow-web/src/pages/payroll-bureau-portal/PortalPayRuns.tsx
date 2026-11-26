// The client's view of its pay runs: approve or decline a run the bureau sent, and see finalised ones.
import { useEffect, useState } from "react"
import { payrollBureauPortalApi } from "../../api/payrollBureauPortal.api"
import type { PortalPayRun, PortalPayRunDetail } from "../../types/payrollBureauPortal.types"
import { color, radius, space } from "../accountant-portal/portal-theme"

const fmtR = (n: unknown) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: unknown) => (d ? new Date(d as string).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")
const message = (e: unknown, fallback: string) => (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback

const STATUS_LABEL: Record<string, string> = {
  AWAITING_CLIENT: "Waiting for your approval", CLIENT_APPROVED: "Approved by you", FINALISED: "Final", REVERSED: "Reversed",
}

export function PortalPayRuns({ clientId }: { clientId: string }) {
  const [runs, setRuns] = useState<PortalPayRun[]>([])
  const [loading, setLoading] = useState(true)
  const [openId, setOpenId] = useState<string | null>(null)

  const refetch = () => {
    payrollBureauPortalApi.getMyPayRuns(clientId).then(setRuns).catch(() => setRuns([])).finally(() => setLoading(false))
  }
  useEffect(refetch, [clientId])

  if (loading) return <div style={{ color: color.muted, fontSize: 13 }}>Loading…</div>
  if (runs.length === 0) return <div style={{ color: color.muted, fontSize: 13 }}>No pay runs to show yet. Runs appear here when your bureau sends them for approval.</div>
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
      {runs.map(r => (
        <div key={r.id} style={{ background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md }}>
          <button onClick={() => setOpenId(openId === r.id ? null : r.id)}
            style={{ width: "100%", textAlign: "left" as const, padding: `${space(4)} ${space(5)}`, background: "none", border: "none", cursor: "pointer", display: "flex", justifyContent: "space-between" }}>
            <span>
              <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{r.payRunNumber}</span>
              <span style={{ display: "block", fontSize: 12.5, color: color.faint }}>
                {fmtD(r.periodStart)} to {fmtD(r.periodEnd)} · pay date {fmtD(r.payDate)}
              </span>
            </span>
            <span style={{ textAlign: "right" as const }}>
              <span style={{ display: "block", fontWeight: 700, color: color.ink }}>{fmtR(r.totalNet)}</span>
              <span style={{ fontSize: 11.5, fontWeight: 700, color: r.status === "AWAITING_CLIENT" ? color.navyText : color.muted }}>{STATUS_LABEL[r.status] ?? r.status}</span>
            </span>
          </button>
          {openId === r.id && <RunDetail clientId={clientId} runId={r.id} onChanged={() => { setOpenId(null); refetch() }} />}
        </div>
      ))}
    </div>
  )
}

function RunDetail({ clientId, runId, onChanged }: { clientId: string; runId: string; onChanged: () => void }) {
  const [detail, setDetail] = useState<PortalPayRunDetail | null>(null)
  const [error, setError] = useState("")
  const [declining, setDeclining] = useState(false)
  const [reason, setReason] = useState("")
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    payrollBureauPortalApi.getMyPayRun(clientId, runId).then(setDetail).catch(e => setError(message(e, "Failed to load this pay run")))
  }, [clientId, runId])

  const act = async (fn: () => Promise<unknown>, failure: string) => {
    setBusy(true); setError("")
    try { await fn(); onChanged() }
    catch (e) { setError(message(e, failure)); setBusy(false) }
  }

  if (error && !detail) return <div style={{ padding: space(4), color: color.redText, fontSize: 13 }}>{error}</div>
  if (!detail) return <div style={{ padding: space(4), color: color.muted, fontSize: 13 }}>Loading…</div>
  const { run, payslips, review } = detail
  return (
    <div style={{ padding: `0 ${space(5)} ${space(5)}`, fontSize: 13 }}>
      <div style={{ display: "flex", gap: space(5), flexWrap: "wrap" as const, margin: `${space(2)} 0 ${space(4)}` }}>
        {([["Gross", run.totalGross], ["PAYE", run.totalPaye], ["UIF", run.totalUif], ["SDL", run.totalSdl], ["Net pay", run.totalNet]] as [string, number | null][]).map(([k, v]) => (
          <div key={k}><div style={{ color: color.muted, fontSize: 11 }}>{k}</div><div style={{ fontWeight: 700 }}>{fmtR(v)}</div></div>
        ))}
      </div>
      {review.findings.length > 0 && (
        <div style={{ marginBottom: space(4) }}>
          <div style={{ fontWeight: 700, marginBottom: 4 }}>Things to look at{review.previousRunNumber ? ` (compared with ${review.previousRunNumber})` : ""}</div>
          <ul style={{ margin: 0, paddingLeft: 18 }}>{review.findings.map((f, i) => <li key={i}>{f.message}</li>)}</ul>
        </div>
      )}
      <table style={{ width: "100%", borderCollapse: "collapse" as const, marginBottom: space(4) }}>
        <thead><tr style={{ textAlign: "left" as const, color: color.muted, fontSize: 11 }}>
          <th>Employee</th><th>Earnings</th><th>PAYE</th><th>UIF</th><th>Deductions</th><th>Net pay</th>
        </tr></thead>
        <tbody>
          {payslips.map(p => (
            <tr key={p.id} style={{ borderTop: `1px solid ${color.border}` }}>
              <td style={{ padding: "6px 0" }}>{p.employeeName}</td><td>{fmtR(p.totalEarnings)}</td><td>{fmtR(p.payeAmount)}</td>
              <td>{fmtR(p.uifEmployee)}</td><td>{fmtR(p.totalDeductions)}</td><td style={{ fontWeight: 700 }}>{fmtR(p.netPay)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {error && <div style={{ color: color.redText, marginBottom: space(3) }}>{error}</div>}
      {run.status === "AWAITING_CLIENT" && !declining && (
        <div style={{ display: "flex", gap: space(2) }}>
          <button disabled={busy} onClick={() => act(() => payrollBureauPortalApi.approvePayRun(clientId, runId), "Failed to approve")}
            style={{ padding: "8px 16px", background: color.navy, color: "#fff", border: "none", borderRadius: radius.md, fontWeight: 700, cursor: "pointer" }}>Approve this pay run</button>
          <button disabled={busy} onClick={() => setDeclining(true)}
            style={{ padding: "8px 16px", background: "none", border: `1px solid ${color.border}`, borderRadius: radius.md, fontWeight: 600, cursor: "pointer" }}>Decline…</button>
        </div>
      )}
      {run.status === "AWAITING_CLIENT" && declining && (
        <div>
          <textarea aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} rows={3} maxLength={500} placeholder="What needs to change?"
            style={{ width: "100%", padding: 8, border: `1px solid ${color.border}`, borderRadius: radius.md, boxSizing: "border-box" as const }} />
          <div style={{ display: "flex", gap: space(2), marginTop: space(2) }}>
            <button disabled={busy || !reason.trim()} onClick={() => act(() => payrollBureauPortalApi.declinePayRun(clientId, runId, reason), "Failed to decline")}
              style={{ padding: "8px 16px", background: color.navy, color: "#fff", border: "none", borderRadius: radius.md, fontWeight: 700, cursor: "pointer" }}>Send back to the bureau</button>
            <button disabled={busy} onClick={() => setDeclining(false)} style={{ padding: "8px 16px", background: "none", border: "none", cursor: "pointer" }}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}
