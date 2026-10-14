// src/pages/debtcollection/DisputesPanel.tsx
// Disputes on a collection case: record, take into review, resolve (finance F16).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  REASON_LABEL, RESOLUTION_LABEL, STATUS_LABEL, needsAmount, openFormProblem, resolveFormProblem, daysLate, isOpenDispute,
} from "./disputes.logic"

const unwrap = (r: any) => r.data?.data ?? r.data ?? []
const fmtR = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtD = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const inp: React.CSSProperties = { width: "100%", padding: "7px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 7, fontSize: 12, boxSizing: "border-box", background: "var(--hf-surface)" }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 12, fontWeight: 600, cursor: "pointer" }

export default function DisputesPanel({ caseId, invoiceIds, canManage, onChanged }: {
  caseId: string; invoiceIds: string[]; canManage: boolean; onChanged?: () => void
}) {
  const qc = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ reasonType: "", invoiceId: "", description: "", amountDisputed: "" })
  const [resolving, setResolving] = useState<any>(null)
  const [rf, setRf] = useState({ resolution: "", note: "", amountConceded: "", creditNoteRef: "" })
  const [error, setError] = useState<string | null>(null)

  const key = ["dc-disputes", caseId]
  const { data: disputes = [] } = useQuery<any[]>({
    queryKey: key,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/debtcollection/cases/${caseId}/disputes`)),
  })
  const done = () => { setError(null); qc.invalidateQueries({ queryKey: key }); onChanged?.() }
  const fail = (e: any) => setError(e?.response?.data?.message ?? "Something went wrong. Please try again.")

  const open = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/debtcollection/cases/${caseId}/disputes`, {
      reasonType: form.reasonType, invoiceId: form.invoiceId || null, description: form.description,
      amountDisputed: form.amountDisputed ? Number(form.amountDisputed) : null,
    }),
    onSuccess: () => { done(); setAdding(false); setForm({ reasonType: "", invoiceId: "", description: "", amountDisputed: "" }) },
    onError: fail,
  })
  const takeOn = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/debtcollection/disputes/${id}/take-on`),
    onSuccess: done, onError: fail,
  })
  const resolve = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/debtcollection/disputes/${resolving.id}/resolve`, {
      resolution: rf.resolution, note: rf.note,
      amountConceded: rf.amountConceded ? Number(rf.amountConceded) : null, creditNoteRef: rf.creditNoteRef || null,
    }),
    onSuccess: () => { done(); setResolving(null); setRf({ resolution: "", note: "", amountConceded: "", creditNoteRef: "" }) },
    onError: fail,
  })

  const openProblem = openFormProblem(form)
  const resolveProblem = resolving ? resolveFormProblem(rf, resolving.amountDisputed ?? null) : null
  const unresolved = disputes.filter(d => isOpenDispute(d.status)).length

  return (
    <div style={{ marginBottom: 18, paddingTop: 14, borderTop: "1px solid var(--hf-border)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <div style={{ fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em" }}>
          Disputes{unresolved > 0 ? ` (${unresolved} open, collection paused)` : ""}
        </div>
        {canManage && <button onClick={() => setAdding(a => !a)} style={{ fontSize: 11, color: "var(--hf-orange-text-strong)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>{adding ? "Cancel" : "+ Record dispute"}</button>}
      </div>

      {error && <div role="alert" style={{ padding: "7px 10px", marginBottom: 8, borderRadius: 7, fontSize: 12, color: "var(--hf-danger-text)", background: "var(--hf-danger-soft)" }}>{error}</div>}

      {adding && (
        <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12, marginBottom: 10 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
            <select aria-label="Reason" value={form.reasonType} onChange={e => setForm(f => ({ ...f, reasonType: e.target.value }))} style={inp}>
              <option value="">Reason…</option>
              {Object.entries(REASON_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <select aria-label="Invoice" value={form.invoiceId} onChange={e => setForm(f => ({ ...f, invoiceId: e.target.value }))} style={inp}>
              <option value="">Whole case</option>
              {invoiceIds.map(id => <option key={id} value={id}>Invoice {id.slice(0, 8)}</option>)}
            </select>
            <input aria-label="Amount disputed" type="number" placeholder="Amount disputed" value={form.amountDisputed} onChange={e => setForm(f => ({ ...f, amountDisputed: e.target.value }))} style={inp} />
          </div>
          <textarea aria-label="Description" rows={2} placeholder="What does the debtor say?" value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} style={{ ...inp, resize: "vertical", marginBottom: 8 }} />
          {openProblem && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginBottom: 6 }}>{openProblem}</div>}
          <button style={btn} disabled={!!openProblem || open.isPending} onClick={() => open.mutate()}>{open.isPending ? "Saving…" : "Record dispute and pause case"}</button>
        </div>
      )}

      {disputes.length === 0 ? (
        <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No disputes on this case.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {disputes.map(d => {
            const late = d.overdue ? daysLate(d.responseDue) : 0
            return (
              <div key={d.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "9px 12px", fontSize: 12 }}>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <strong>{REASON_LABEL[d.reasonType] ?? d.reasonType}{d.amountDisputed != null ? ` · ${fmtR(d.amountDisputed)}` : ""}</strong>
                  <span style={{ fontWeight: 700 }}>{STATUS_LABEL[d.status] ?? d.status}</span>
                </div>
                <div style={{ color: "var(--hf-text-secondary)", margin: "4px 0" }}>{d.description}</div>
                <div style={{ color: "var(--hf-text-muted)" }}>
                  Raised {fmtD(d.raisedOn)} · owner {d.ownerUserName ?? "none"}
                  {isOpenDispute(d.status) && <> · respond by {fmtD(d.responseDue)}</>}
                </div>
                {late > 0 && <div style={{ color: "var(--hf-danger-text)", marginTop: 2 }}>Response is {late} day(s) late.</div>}
                {d.status === "RESOLVED" && (
                  <div style={{ marginTop: 4 }}>
                    {RESOLUTION_LABEL[d.resolution] ?? d.resolution}{d.amountConceded != null ? ` · conceded ${fmtR(d.amountConceded)}` : ""}
                    {d.creditNoteRef ? ` · credit note ${d.creditNoteRef}` : ""} — {d.resolutionNote} ({d.resolvedByName}, {fmtD(d.resolvedAt)})
                  </div>
                )}
                {canManage && isOpenDispute(d.status) && (
                  <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                    {d.status === "OPEN" && <button style={btn} disabled={takeOn.isPending} onClick={() => takeOn.mutate(d.id)}>Take into review</button>}
                    <button style={btn} onClick={() => { setResolving(d); setError(null) }}>Resolve</button>
                  </div>
                )}
                {resolving?.id === d.id && (
                  <div style={{ marginTop: 8 }}>
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
                      <select aria-label="Outcome" value={rf.resolution} onChange={e => setRf(f => ({ ...f, resolution: e.target.value }))} style={inp}>
                        <option value="">Outcome…</option>
                        {Object.entries(RESOLUTION_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                      </select>
                      <input aria-label="Amount conceded" type="number" placeholder="Amount conceded" disabled={!needsAmount(rf.resolution)} value={rf.amountConceded} onChange={e => setRf(f => ({ ...f, amountConceded: e.target.value }))} style={inp} />
                      <input aria-label="Credit note" placeholder="Credit note number" value={rf.creditNoteRef} onChange={e => setRf(f => ({ ...f, creditNoteRef: e.target.value }))} style={inp} />
                    </div>
                    <textarea aria-label="Outcome note" rows={2} placeholder="Explain the outcome" value={rf.note} onChange={e => setRf(f => ({ ...f, note: e.target.value }))} style={{ ...inp, resize: "vertical", marginBottom: 6 }} />
                    {needsAmount(rf.resolution) && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginBottom: 6 }}>Raise the credit note in Invoicing; this record does not change the invoice.</div>}
                    {resolveProblem && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginBottom: 6 }}>{resolveProblem}</div>}
                    <div style={{ display: "flex", gap: 8 }}>
                      <button style={btn} disabled={!!resolveProblem || resolve.isPending} onClick={() => resolve.mutate()}>Confirm resolution</button>
                      <button style={btn} onClick={() => setResolving(null)}>Cancel</button>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
