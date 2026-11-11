// src/pages/events/EventReportsTab.tsx
//
// Reports hub and close-out for one event. The close-out view (attendance reconciliation, final cost position, supplier balances and the checklist of what is
// still open) includes money, so it needs EVENTS_BUDGET_READ like the server. Guest CSVs carry personal information and need EVENTS_EXPORT; the server logs each download.
import { useState } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, card, ErrorLine } from "../warehousing/whseUi"
import { rand } from "./paymentOps"
import { closeOutHeadline, failedChecks, overBudget, owedSuppliers, ratePercent, visibleReports, type CloseOut } from "./reportOps"

const blobText = (b: Blob) => (typeof b.text === "function" ? b.text() : new Promise<string>((res, rej) => { const r = new FileReader(); r.onload = () => res(String(r.result)); r.onerror = () => rej(r.error); r.readAsText(b) }))

/** A failed blob download carries its JSON error as a Blob; turn it back into the server's message. */
async function downloadError(e: any): Promise<unknown> {
  try { const d = e?.response?.data; const t = d instanceof Blob ? await blobText(d) : null; const m = t ? JSON.parse(t)?.message : null; if (m) return { response: { data: { message: m } } } } catch { /* fall through */ }
  return e
}

export default function EventReportsTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const canMoney = usePermissions(["EVENTS_BUDGET_READ", "EVENTS_BUDGET_MANAGE", "EVENTS_SUPPLIER_MANAGE", "EVENTS_MANAGE"])
  const canPii = usePermissions(["EVENTS_EXPORT", "EVENTS_MANAGE"])
  const [busy, setBusy] = useState<string | null>(null)
  const base = `/api/v1/events/${eventId}`
  const { data: co, error } = useQuery<CloseOut>({ queryKey: ["event-close-out", eventId], enabled: !!eventId && canMoney, queryFn: async () => { const r = await apiClient.get(`${base}/dashboard/close-out`); return r.data?.data ?? r.data } })
  const dl = useMutation({
    mutationFn: async (r: { id: string; file: string }) => {
      setBusy(r.id)
      try {
        const res = await apiClient.get(`${base}/reports/${r.file}`, { responseType: "blob" })
        const url = URL.createObjectURL(res.data as Blob)
        const a = document.createElement("a"); a.href = url; a.download = `${r.id}-${eventId?.slice(0, 8)}.${r.file.split(".").pop()}`; a.click(); URL.revokeObjectURL(url)
      } catch (e) { throw await downloadError(e) } finally { setBusy(null) }
    },
  })

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={{ ...btnGhost }} onClick={onChangeEvent}>Choose event</button></div>
  const reports = visibleReports(canPii, canMoney)
  if (reports.length === 0) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to reports for events.</p>
  const failed = failedChecks(co), owed = owedSuppliers(co)

  return (
    <div>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>Reports and close-out for {eventTitle}</p>
      {canMoney && (<>
        <ErrorLine error={error} fallback="The close-out could not be loaded" />
        {co && (
          <section style={{ ...card, padding: 14, marginBottom: 14 }}>
            <h3 style={{ fontSize: 15, margin: "0 0 4px" }}>Close-out</h3>
            <p role="status" style={{ margin: "0 0 10px", fontSize: 13, fontWeight: 700, color: co.readyToClose ? "var(--hf-success-text)" : "var(--hf-warning-text)" }}>{closeOutHeadline(co)}</p>
            <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 10 }}>
              {([["Registered", co.attendance.registered], ["Checked in", co.attendance.checkedIn], ["Walk-ins", co.attendance.walkIns], ["Did not attend", co.attendance.notCheckedIn], ["Attendance", ratePercent(co.attendance.attendanceRatePercent)]] as [string, string | number][]).map(([k, v]) =>
                <div key={k} style={{ ...card, padding: "8px 14px", minWidth: 100 }}><div style={{ fontSize: 18, fontWeight: 800 }}>{v}</div><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{k}</div></div>)}
            </div>
            {co.budget && co.budget.budgetStatus !== "NOT_STARTED" ? (
              <div style={{ fontSize: 13, marginBottom: 10 }}>
                <div>Planned {rand(co.budget.planned)} · Final cost forecast {rand(co.budget.forecast)} · Paid {rand(co.budget.paid)} · Still to pay {rand(co.budget.outstanding)}</div>
                <div style={{ fontWeight: 700, color: overBudget(co) ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{overBudget(co) ? `Over budget by ${rand(Math.abs(co.budget.variance))}` : `Within budget, ${rand(co.budget.variance)} to spare`}</div>
                {co.budget.costPerCheckedIn != null && <div style={{ color: "var(--hf-text-muted)" }}>Cost per guest who came: {rand(co.budget.costPerCheckedIn)}</div>}
              </div>
            ) : <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No budget was set up for this event.</p>}
            {failed.length > 0 && <ul aria-label="Still open" style={{ margin: "0 0 10px", paddingLeft: 18, fontSize: 13 }}>{failed.map(c => <li key={c.code}><strong>{c.label}</strong>{c.detail ? `: ${c.detail}` : ""}</li>)}</ul>}
            {owed.length > 0 && <div style={{ fontSize: 13 }}><strong>Suppliers still owed</strong>{owed.map(s => <div key={s.vendorId}>{s.vendorName}: {rand(s.outstanding)}</div>)}</div>}
          </section>
        )}
      </>)}
      <ErrorLine error={dl.error} fallback="The download failed" />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(260px,1fr))", gap: 10 }}>
        {reports.map(r => (
          <div key={r.id} style={{ ...card, padding: 12, display: "flex", flexDirection: "column", gap: 6 }}>
            <div style={{ fontSize: 13.5, fontWeight: 700 }}>{r.name}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)", flex: 1 }}>{r.blurb}</div>
            <button type="button" style={btnGhost} disabled={busy !== null} aria-label={`Download ${r.name}`} onClick={() => dl.mutate(r)}>{busy === r.id ? "Preparing…" : "Download"}</button>
          </div>
        ))}
      </div>
    </div>
  )
}
