// src/pages/accountant/ServicesTab.tsx
// Recurring services (finance F22): what the practice does for each client on a schedule, and the runs that have come due.
// A run is only a reminder to bill. A person makes the fee note (which posts revenue) or skips the run with a reason.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { emptySchedule, fmtR, FREQ_LABEL, scheduleProblem, scheduleRequest, SERVICE_LABEL, type ScheduleForm } from "./practice.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const today = () => new Date().toISOString().slice(0, 10)
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14 }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const inp: React.CSSProperties = { padding: "7px 9px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function ServicesTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("ACCOUNTANT_WRITE")
  const base = "/api/v1/accountant"
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState<ScheduleForm>(emptySchedule(today()))
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const { data: runs = [] } = useQuery<any[]>({ queryKey: ["acc-runs"], queryFn: async () => unwrap(await apiClient.get(`${base}/service-runs?status=DUE`)) ?? [] })
  const { data: schedules = [] } = useQuery<any[]>({ queryKey: ["acc-schedules"], queryFn: async () => unwrap(await apiClient.get(`${base}/service-schedules`)) ?? [] })
  const { data: clients = [] } = useQuery<any[]>({
    queryKey: ["acc-clients-pick"], enabled: adding,
    queryFn: async () => { const d = unwrap(await apiClient.get(`${base}/clients?size=200`)); return d?.content ?? d ?? [] },
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["acc-runs"] }); qc.invalidateQueries({ queryKey: ["acc-schedules"] }) }
  const fail = (e: any) => { setNotice(null); setError(errorOf(e)) }
  const create = useMutation({
    mutationFn: () => apiClient.post(`${base}/clients/${form.clientId}/service-schedules`, scheduleRequest(form)),
    onSuccess: () => { setError(null); setAdding(false); setForm(emptySchedule(today())); refresh() }, onError: fail,
  })
  const act = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: any }) => apiClient.post(`${base}/${path}`, body ?? {}),
    onSuccess: (_r, v) => { setError(null); setNotice(v.path.endsWith("/bill") ? "Fee note made. Send it from Billing." : null); refresh() }, onError: fail,
  })
  const check = useMutation({
    mutationFn: () => apiClient.post(`${base}/service-runs/generate`),
    onSuccess: r => { setError(null); const n = unwrap(r)?.made ?? 0; setNotice(n ? `${n} run(s) now due.` : "Nothing new is due."); refresh() }, onError: fail,
  })
  const problem = scheduleProblem(form)
  const set = (p: Partial<ScheduleForm>) => setForm(f => ({ ...f, ...p }))

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Recurring services</h2>
      {notice && <div role="status" style={{ ...card, fontSize: 13 }}>{notice}</div>}
      {error && <div role="alert" style={{ ...card, fontSize: 13, color: "var(--hf-danger-text, #b91c1c)" }}>{error}</div>}

      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h3 style={{ margin: 0 }}>Due to bill</h3>
          {canWrite && <button style={btn} onClick={() => check.mutate()}>Check now</button>}
        </div>
        {runs.length === 0 && <p style={{ fontSize: 13 }}>Nothing is due.</p>}
        {runs.map(r => (
          <div key={r.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, padding: "8px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13, flexWrap: "wrap" }}>
            <span><b>{r.clientName}</b> · {SERVICE_LABEL[r.serviceType] ?? r.serviceType}{r.description ? ` (${r.description})` : ""} · {r.runDate}</span>
            <span>{r.feeAmount != null ? fmtR(r.feeAmount) : "no fee"}
              {canWrite && <>
                {r.feeAmount != null && <button style={{ ...btn, marginLeft: 8 }} onClick={() => { if (window.confirm(`Make a fee note for ${fmtR(r.feeAmount)} to ${r.clientName}? This posts revenue.`)) act.mutate({ path: `service-runs/${r.id}/bill` }) }}>Make fee note</button>}
                <button style={{ ...btn, marginLeft: 6 }} onClick={() => { const note = window.prompt("Why is this run skipped?"); if (note && note.trim()) act.mutate({ path: `service-runs/${r.id}/skip`, body: { note } }) }}>Skip</button>
              </>}
            </span>
          </div>
        ))}
      </div>

      <div style={card}>
        <h3 style={{ marginTop: 0 }}>Schedules</h3>
        {schedules.length === 0 && <p style={{ fontSize: 13 }}>No recurring services yet.</p>}
        {schedules.map(s => (
          <div key={s.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, padding: "8px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13, flexWrap: "wrap" }}>
            <span><b>{s.clientName}</b> · {SERVICE_LABEL[s.serviceType] ?? s.serviceType} · {FREQ_LABEL[s.frequency]}
              {s.feeAmount != null ? ` · ${fmtR(s.feeAmount)}` : ""}{s.ownerName ? ` · ${s.ownerName}` : ""}</span>
            <span>{s.status === "ACTIVE" ? (s.nextRun ? `next ${s.nextRun}` : "finished") : s.status === "PAUSED" ? "Paused" : "Ended"}
              {canWrite && s.status === "ACTIVE" && <button style={{ ...btn, marginLeft: 8 }} onClick={() => act.mutate({ path: `service-schedules/${s.id}/pause` })}>Pause</button>}
              {canWrite && s.status === "PAUSED" && <button style={{ ...btn, marginLeft: 8 }} onClick={() => act.mutate({ path: `service-schedules/${s.id}/resume` })}>Resume</button>}
              {canWrite && s.status !== "ENDED" && <button style={{ ...btn, marginLeft: 6 }} onClick={() => { if (window.confirm("End this schedule?")) act.mutate({ path: `service-schedules/${s.id}/end` }) }}>End</button>}
            </span>
          </div>
        ))}
        {canWrite && !adding && <button style={{ ...btn, marginTop: 10 }} onClick={() => setAdding(true)}>Add a recurring service</button>}
        {adding && (
          <div style={{ marginTop: 12, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "flex-end" }}>
            <label style={{ fontSize: 11 }}>Client<br /><select aria-label="Client" value={form.clientId} onChange={e => set({ clientId: e.target.value })} style={{ ...inp, width: 200 }}>
              <option value="">Choose…</option>{clients.map((c: any) => <option key={c.id} value={c.id}>{c.tradingName ?? c.name}</option>)}</select></label>
            <label style={{ fontSize: 11 }}>Service<br /><select aria-label="Service" value={form.serviceType} onChange={e => set({ serviceType: e.target.value })} style={inp}>
              {Object.entries(SERVICE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
            <label style={{ fontSize: 11 }}>How often<br /><select aria-label="How often" value={form.frequency} onChange={e => set({ frequency: e.target.value })} style={inp}>
              {Object.entries(FREQ_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
            <label style={{ fontSize: 11 }}>First date<br /><input aria-label="First date" type="date" value={form.firstDate} onChange={e => set({ firstDate: e.target.value })} style={inp} /></label>
            <label style={{ fontSize: 11 }}>Fee per run (R, excl. VAT)<br /><input aria-label="Fee per run" value={form.feeAmount} onChange={e => set({ feeAmount: e.target.value })} style={{ ...inp, width: 110 }} /></label>
            <label style={{ fontSize: 11 }}>Hours budgeted<br /><input aria-label="Hours budgeted" value={form.budgetHours} onChange={e => set({ budgetHours: e.target.value })} style={{ ...inp, width: 80 }} /></label>
            <label style={{ fontSize: 11 }}><input type="checkbox" checked={form.includeVat} onChange={e => set({ includeVat: e.target.checked })} /> Add VAT</label>
            <button style={btn} disabled={!!problem || create.isPending} title={problem ?? ""} onClick={() => create.mutate()}>Save</button>
            <button style={btn} onClick={() => setAdding(false)}>Cancel</button>
          </div>
        )}
      </div>
    </div>
  )
}
