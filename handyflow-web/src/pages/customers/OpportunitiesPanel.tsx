// src/pages/customers/OpportunitiesPanel.tsx
// Opportunities: the deals with a company, each with its own stage. Used as a CRM tab (all companies) and as a tab on one company (customerId given).
// Stage, amount and lost-reason rules live on the server; this screen sends and shows.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { localToday, money, reasonLabel } from "./pipeline.logic"
import { errorMessage, fetchAllPages } from "./crmApi"
import { CompanyPicker } from "./CompanyPicker"
import {
  EMPTY_OPP, LOST_REASONS, NO_OPP_FILTER, OPP_CHANCE, OPP_LABEL, filterOpps, lostProblem, oppBody, oppCloseState, oppCloseText, oppColumns, oppMoves, oppProblem, oppStageBody, oppTotals, toOppForm,
  type OppForm, type OppStage, type Opportunity, type LostReason,
} from "./opportunities.logic"
import { Loading, ErrorNote } from "./CrmKit"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const small: React.CSSProperties = { ...ghost, padding: "3px 8px", fontSize: 12 }
const TONE = { overdue: "var(--hf-danger-text)", soon: "var(--hf-warning-text)", later: "var(--hf-text-muted)", none: "var(--hf-text-faint)" }

export default function OpportunitiesPanel({ customerId }: { customerId?: string }) {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE"), today = localToday()
  const key = ["crm", "opportunities", customerId ?? "all"]
  const [filter, setFilter] = useState(NO_OPP_FILTER), [view, setView] = useState<"board" | "list">("board"), [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<string | "new" | null>(null), [form, setForm] = useState<OppForm>(EMPTY_OPP), [losing, setLosing] = useState<string | null>(null), [deleting, setDeleting] = useState<string | null>(null)
  const base = customerId ? `/api/v1/crm/opportunities?customerId=${customerId}` : "/api/v1/crm/opportunities"
  const { data, isLoading, isError } = useQuery({ queryKey: key, queryFn: () => fetchAllPages<Opportunity>(u => apiClient.get(u), base) })
  const all = data?.items ?? []
  const done = () => { setError(null); setEditing(null); setLosing(null); setDeleting(null); qc.invalidateQueries({ queryKey: ["crm", "opportunities"] }); qc.invalidateQueries({ queryKey: ["crm", "hierarchy"] }) }
  const fail = (e: unknown) => setError(errorMessage(e, "Something went wrong. Try again."))
  const current = all.find(o => o.id === editing)
  const save = useMutation({
    mutationFn: () => editing === "new" ? apiClient.post("/api/v1/crm/opportunities", oppBody({ ...form, customerId: customerId ?? form.customerId }))
      : apiClient.put(`/api/v1/crm/opportunities/${editing}`, oppBody(form, current?.ownerId ?? null)),
    onSuccess: done, onError: fail,
  })
  const move = useMutation({ mutationFn: (v: { id: string; stage: OppStage; reason?: LostReason | ""; note?: string }) => apiClient.patch(`/api/v1/crm/opportunities/${v.id}/stage`, oppStageBody(v.stage, v.reason, v.note)), onSuccess: done, onError: fail })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/opportunities/${id}`), onSuccess: done, onError: fail })
  const busy = save.isPending || move.isPending || remove.isPending

  const shown = filterOpps(all, filter, today), totals = oppTotals(all, today), cols = oppColumns(shown)
  const problem = editing ? oppProblem(form, !customerId && editing === "new") : null
  const set = (patch: Partial<OppForm>) => setForm({ ...form, ...patch })
  const open = (id: string | "new") => { const o = all.find(x => x.id === id); setForm(o ? toOppForm(o) : { ...EMPTY_OPP, customerId: customerId ?? "" }); setEditing(id); setLosing(null); setError(null) }

  const card = (o: Opportunity) => {
    const state = oppCloseState(o, today)
    return (
      <article key={o.id} aria-label={o.name} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: 9, marginBottom: 8, fontSize: 12.5 }}>
        <strong style={{ fontSize: 13 }}>{o.name}</strong>
        {!customerId && <div style={{ color: "var(--hf-text-muted)" }}>{o.customerName ?? "Company"}</div>}
        <div style={{ marginTop: 4 }}>{money(o.amount)}{o.stage !== "WON" && o.stage !== "LOST" && <span style={{ color: "var(--hf-text-muted)" }}> · {OPP_CHANCE[o.stage]}% = {money(o.weightedAmount)}</span>}</div>
        <div style={{ color: TONE[state] }}>{oppCloseText(o, today)}</div>
        {o.stage === "LOST" && <div style={{ color: "var(--hf-danger-text)" }}>Lost: {reasonLabel(o.lostReason)}{o.lostNote ? ` — ${o.lostNote}` : ""}</div>}
        {canEdit && losing !== o.id && deleting !== o.id && (
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
            <select aria-label={`Move ${o.name}`} style={{ ...field, padding: "3px 6px", fontSize: 12 }} value="" disabled={busy} onChange={e => { const to = e.target.value as OppStage; if (!to) return; if (to === "LOST") { setLosing(o.id); setEditing(null); setError(null) } else move.mutate({ id: o.id, stage: to }) }}>
              <option value="">Move to…</option>{oppMoves(o.stage).map(s => <option key={s} value={s}>{OPP_LABEL[s]}</option>)}</select>
            <button type="button" style={small} aria-label={`Edit ${o.name}`} onClick={() => open(o.id)}>Edit</button>
            <button type="button" style={small} aria-label={`Delete ${o.name}`} onClick={() => { setDeleting(o.id); setError(null) }}>Delete</button>
          </div>)}
        {canEdit && deleting === o.id && (
          <div style={{ marginTop: 6 }}>Delete {o.name}?
            <div style={{ display: "flex", gap: 6, marginTop: 4 }}><button type="button" style={{ ...small, fontWeight: 700 }} disabled={busy} aria-label={`Confirm delete ${o.name}`} onClick={() => remove.mutate(o.id)}>Delete</button><button type="button" style={small} onClick={() => setDeleting(null)}>Cancel</button></div></div>)}
        {canEdit && losing === o.id && <LostForm opp={o} saving={move.isPending} onCancel={() => { setLosing(null); setError(null) }} onSave={(reason, note) => move.mutate({ id: o.id, stage: "LOST", reason, note })} />}
      </article>)
  }

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 10 }}>
        <input aria-label="Search opportunities" placeholder={customerId ? "Search opportunities" : "Search opportunity or company"} style={{ ...field, minWidth: 240 }} value={filter.search} onChange={e => setFilter({ ...filter, search: e.target.value })} />
        <label style={{ fontSize: 13 }}><input type="checkbox" checked={filter.overdueOnly} onChange={e => setFilter({ ...filter, overdueOnly: e.target.checked })} /> Overdue only</label>
        <div role="group" aria-label="View" style={{ display: "flex", gap: 4 }}>
          {(["board", "list"] as const).map(v => <button key={v} type="button" aria-pressed={view === v} style={{ ...ghost, fontWeight: view === v ? 700 : 500 }} onClick={() => setView(v)}>{v === "board" ? "Board" : "List"}</button>)}
        </div>
        {canEdit && <button type="button" style={{ ...ghost, fontWeight: 700 }} onClick={() => open("new")}>New opportunity</button>}
      </div>
      <div aria-label="Opportunity totals" style={{ display: "flex", gap: 18, flexWrap: "wrap", fontSize: 13, marginBottom: 10 }}>
        <span><strong>{totals.openCount}</strong> open</span>
        <span>Open value <strong>{money(totals.openValue)}</strong></span>
        <span>Weighted <strong>{money(totals.weighted)}</strong></span>
        <span>Won <strong>{money(totals.wonValue)}</strong> ({totals.wonCount})</span>
        <span>Lost <strong>{totals.lostCount}</strong></span>
        {totals.overdue > 0 && <span style={{ color: TONE.overdue }}><strong>{totals.overdue}</strong> past their close date</span>}
      </div>
      {data?.truncated && <div role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text)", marginBottom: 8 }}>Showing the first 2,000 opportunities. Narrow the list to see the rest.</div>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 8 }}>{error}</div>}
      {editing && canEdit && (
        <form aria-label={editing === "new" ? "New opportunity" : "Edit opportunity"} onSubmit={e => { e.preventDefault(); if (!problem) save.mutate() }} style={{ display: "grid", gap: 8, maxWidth: 460, padding: 10, border: "1px solid var(--hf-border)", borderRadius: 10, marginBottom: 12 }}>
          {!customerId && editing === "new" && <label>Company<CompanyPicker label="Company" value={form.customerId} onChange={id => set({ customerId: id })} /></label>}
          <label>Name <input aria-label="Opportunity name" style={{ ...field, width: "100%" }} value={form.name} onChange={e => set({ name: e.target.value })} /></label>
          <label>Amount (R) <input aria-label="Amount" inputMode="decimal" style={{ ...field, width: "100%" }} value={form.amount} onChange={e => set({ amount: e.target.value })} /></label>
          <label>Expected close date <input aria-label="Expected close date" type="date" style={{ ...field, width: "100%" }} value={form.expectedCloseDate} onChange={e => set({ expectedCloseDate: e.target.value })} /></label>
          <label>Notes <textarea aria-label="Notes" rows={2} style={{ ...field, width: "100%" }} value={form.notes} onChange={e => set({ notes: e.target.value })} /></label>
          {form.name.length > 0 && problem && <span style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{problem}</span>}
          <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!problem || busy}>{save.isPending ? "Saving…" : "Save opportunity"}</button><button type="button" style={ghost} onClick={() => { setEditing(null); setError(null) }}>Cancel</button></div>
        </form>)}
      {isLoading && <Loading />}
      {isError && <ErrorNote>Couldn't load the opportunities.</ErrorNote>}
      {!isLoading && !isError && all.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No opportunities yet.{canEdit ? " Choose New opportunity to add the first." : ""}</p>}
      {all.length > 0 && shown.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No opportunities match these filters.</p>}
      {shown.length > 0 && view === "board" && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(6, minmax(200px, 1fr))", gap: 10, overflowX: "auto", alignItems: "start" }}>
          {cols.map(col => (
            <section key={col.stage} aria-label={`${OPP_LABEL[col.stage]} column`} style={{ background: "var(--hf-surface-muted)", borderRadius: 10, padding: 8, minHeight: 80 }}>
              <h4 style={{ margin: "2px 4px 8px", fontSize: 13, display: "flex", justifyContent: "space-between" }}><span>{OPP_LABEL[col.stage]} ({col.count})</span><span style={{ fontWeight: 500, color: "var(--hf-text-muted)" }}>{money(col.value)}</span></h4>
              {col.items.map(card)}
            </section>))}
        </div>)}
      {shown.length > 0 && view === "list" && (
        <table aria-label="Opportunities" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Opportunity</th>{!customerId && <th>Company</th>}<th>Stage</th><th style={{ textAlign: "right" }}>Amount</th><th style={{ textAlign: "right" }}>Weighted</th><th>Close</th></tr></thead>
          <tbody>{[...shown].sort((a, b) => (a.expectedCloseDate ?? "9999").localeCompare(b.expectedCloseDate ?? "9999") || a.name.localeCompare(b.name)).map(o => (
            <tr key={o.id} aria-label={o.name} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={{ padding: "6px 4px" }}>{o.name}</td>{!customerId && <td>{o.customerName ?? ""}</td>}<td>{OPP_LABEL[o.stage]}</td>
              <td style={{ textAlign: "right" }}>{money(o.amount)}</td><td style={{ textAlign: "right" }}>{money(o.weightedAmount)}</td><td style={{ color: TONE[oppCloseState(o, today)] }}>{oppCloseText(o, today)}</td>
            </tr>))}</tbody>
        </table>)}
    </div>
  )
}

function LostForm({ opp, saving, onSave, onCancel }: { opp: Opportunity; saving: boolean; onSave: (reason: LostReason, note: string) => void; onCancel: () => void }) {
  const [reason, setReason] = useState<LostReason | "">(""), [note, setNote] = useState("")
  const problem = lostProblem(reason, note)
  return (
    <form aria-label={`Mark ${opp.name} lost`} onSubmit={e => { e.preventDefault(); if (!problem && reason) onSave(reason, note) }} style={{ display: "grid", gap: 6, marginTop: 6 }}>
      <label>Why was it lost? <select aria-label="Lost reason" style={{ ...field, width: "100%" }} value={reason} onChange={e => setReason(e.target.value as LostReason | "")}>
        <option value="">Choose a reason…</option>{LOST_REASONS.map(r => <option key={r.id} value={r.id}>{r.label}</option>)}</select></label>
      <label>Note <textarea aria-label="Lost note" rows={2} maxLength={500} style={{ ...field, width: "100%" }} value={note} onChange={e => setNote(e.target.value)} /></label>
      {reason && problem && <span style={{ color: "var(--hf-danger-text)" }}>{problem}</span>}
      <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!problem || saving}>{saving ? "Saving…" : "Mark lost"}</button><button type="button" style={ghost} onClick={onCancel}>Cancel</button></div>
    </form>
  )
}
