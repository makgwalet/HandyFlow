// src/pages/customers/PipelineBoard.tsx
// The sales pipeline: every active lead in a column for its stage, with deal value, expected close date and owner.
// Stage, deal and owner changes go to the server, which holds the rules; this screen only sends and shows.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { LOST_REASONS, NO_FILTER, PIPELINE_LIMIT, STAGE_LABEL, allowedMoves, lostProblem, reasonLabel, stageBody, closeState, closeText, columns, dealBody, dealProblem, filterLeads, localToday, money, ownerText, pipelineTotals, type LeadFilter, type LostReason, type OwnerFilter, type PipelineLead, type Stage } from "./pipeline.logic"

const unwrapList = (r: { data: unknown }): PipelineLead[] => { const o = r.data as { data?: unknown } | null; const p = o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data; return Array.isArray(p) ? (p as PipelineLead[]) : [] }
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const TONE = { overdue: "var(--hf-danger-text)", soon: "var(--hf-warning-text)", later: "var(--hf-text-muted)", none: "var(--hf-text-faint)" }

export default function PipelineBoard() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE"), me = useAuthStore(s => s.user?.userId ?? null)
  const [filter, setFilter] = useState<LeadFilter>(NO_FILTER), [error, setError] = useState<string | null>(null), [editing, setEditing] = useState<string | null>(null), [losing, setLosing] = useState<string | null>(null)
  const today = localToday()
  const { data: leads = [], isLoading, isError } = useQuery<PipelineLead[]>({ queryKey: ["crm", "pipeline"], queryFn: async () => unwrapList(await apiClient.get("/api/v1/crm/customers/pipeline")) })
  const done = () => { setError(null); qc.invalidateQueries({ queryKey: ["crm", "pipeline"] }); qc.invalidateQueries({ queryKey: ["crm", "funnel"] }) }
  const move = useMutation({ mutationFn: (v: { id: string; stage: Stage; reason?: LostReason | ""; note?: string }) => apiClient.patch(`/api/v1/crm/customers/${v.id}/stage`, stageBody(v.stage, v.reason, v.note)), onSuccess: () => { setLosing(null); done() }, onError: e => setError(errorText(e)) })
  const owner = useMutation({ mutationFn: (v: { id: string; ownerId: string | null }) => apiClient.patch(`/api/v1/crm/customers/${v.id}/owner`, { ownerId: v.ownerId }), onSuccess: done, onError: e => setError(errorText(e)) })
  const deal = useMutation({ mutationFn: (v: { id: string; value: string; date: string }) => apiClient.patch(`/api/v1/crm/customers/${v.id}/deal`, dealBody(v.value, v.date)), onSuccess: () => { setEditing(null); done() }, onError: e => setError(errorText(e)) })

  const shown = filterLeads(leads, filter, me, today), cols = columns(shown), totals = pipelineTotals(leads, today)
  const busy = move.isPending || owner.isPending || deal.isPending

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 10 }}>
        <input aria-label="Search leads" placeholder="Search name, email or phone" style={{ ...field, minWidth: 240 }} value={filter.search} onChange={e => setFilter({ ...filter, search: e.target.value })} />
        <select aria-label="Owner filter" style={field} value={filter.owner} onChange={e => setFilter({ ...filter, owner: e.target.value as OwnerFilter })}>
          <option value="all">All owners</option><option value="mine">Mine</option><option value="unassigned">Unassigned</option></select>
        <label style={{ fontSize: 13 }}><input type="checkbox" checked={filter.overdueOnly} onChange={e => setFilter({ ...filter, overdueOnly: e.target.checked })} /> Overdue only</label>
        {(filter.search || filter.owner !== "all" || filter.overdueOnly) && <button type="button" style={ghost} onClick={() => setFilter(NO_FILTER)}>Clear filters</button>}
      </div>
      <div aria-label="Pipeline totals" style={{ display: "flex", gap: 18, flexWrap: "wrap", fontSize: 13, marginBottom: 10 }}>
        <span><strong>{totals.openCount}</strong> open {totals.openCount === 1 ? "lead" : "leads"}</span>
        <span>Open value <strong>{money(totals.openValue)}</strong></span>
        <span>Won <strong>{money(totals.wonValue)}</strong></span>
        <span>Lost <strong>{money(totals.lostValue)}</strong></span>
        {totals.overdue > 0 && <span style={{ color: TONE.overdue }}><strong>{totals.overdue}</strong> past their close date</span>}
        {totals.noValue > 0 && <span style={{ color: "var(--hf-text-muted)" }}>{totals.noValue} open without a deal value</span>}
      </div>
      {leads.length >= PIPELINE_LIMIT && <div role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text)", marginBottom: 8 }}>Showing the newest {PIPELINE_LIMIT} leads. Older leads are not on this board.</div>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 8 }}>{error}</div>}
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load the pipeline.</div>}
      {!isLoading && !isError && leads.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No leads yet. Add a customer as a lead, or import a list, and it will appear here.</p>}
      {leads.length > 0 && shown.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No leads match these filters.</p>}
      {leads.length > 0 && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(5, minmax(210px, 1fr))", gap: 10, overflowX: "auto", alignItems: "start" }}>
          {cols.map(col => (
            <section key={col.stage} aria-label={`${STAGE_LABEL[col.stage]} column`} style={{ background: "var(--hf-surface-muted)", borderRadius: 10, padding: 8, minHeight: 80 }}>
              <h4 style={{ margin: "2px 4px 8px", fontSize: 13, display: "flex", justifyContent: "space-between" }}><span>{STAGE_LABEL[col.stage]} ({col.count})</span><span style={{ fontWeight: 500, color: "var(--hf-text-muted)" }}>{money(col.value)}</span></h4>
              {col.leads.map(l => {
                const state = closeState(l, today), isEditing = editing === l.id
                return (
                  <article key={l.id} aria-label={l.name} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: 9, marginBottom: 8, fontSize: 12.5 }}>
                    <strong style={{ fontSize: 13 }}>{l.name}</strong>
                    <div style={{ color: "var(--hf-text-muted)" }}>{[l.email, l.phone].filter(Boolean).join(" · ") || "No contact details"}</div>
                    <div style={{ marginTop: 4 }}>{money(l.dealValue)} · <span style={{ color: TONE[state] }}>{closeText(l, today)}</span></div>
                    <div style={{ color: "var(--hf-text-muted)" }}>Owner: {ownerText(l.ownerId, me)}</div>
                    {l.pipelineStage === "LOST" && <div style={{ color: "var(--hf-danger-text)" }}>Lost: {reasonLabel(l.lostReason)}{l.lostNote ? ` — ${l.lostNote}` : ""}</div>}
                    {canEdit && !isEditing && losing !== l.id && (
                      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
                        <select aria-label={`Move ${l.name}`} style={{ ...field, padding: "3px 6px", fontSize: 12 }} value="" disabled={busy} onChange={e => { const to = e.target.value as Stage; if (!to) return; if (to === "LOST") { setLosing(l.id); setEditing(null); setError(null) } else move.mutate({ id: l.id, stage: to }) }}>
                          <option value="">Move to…</option>{allowedMoves(l.pipelineStage ?? "NEW").map(s => <option key={s} value={s}>{STAGE_LABEL[s]}</option>)}</select>
                        <button type="button" style={{ ...ghost, padding: "3px 8px", fontSize: 12 }} aria-label={`Edit deal for ${l.name}`} onClick={() => setEditing(l.id)}>Edit deal</button>
                        {l.ownerId === me
                          ? <button type="button" style={{ ...ghost, padding: "3px 8px", fontSize: 12 }} disabled={busy} aria-label={`Unassign ${l.name}`} onClick={() => owner.mutate({ id: l.id, ownerId: null })}>Unassign</button>
                          : <button type="button" style={{ ...ghost, padding: "3px 8px", fontSize: 12 }} disabled={busy || !me} aria-label={`Assign ${l.name} to me`} onClick={() => owner.mutate({ id: l.id, ownerId: me })}>Assign to me</button>}
                      </div>)}
                    {canEdit && losing === l.id && <LostForm lead={l} saving={move.isPending} onCancel={() => { setLosing(null); setError(null) }} onSave={(reason, note) => move.mutate({ id: l.id, stage: "LOST", reason, note })} />}
                    {canEdit && isEditing && <DealForm lead={l} saving={deal.isPending} onCancel={() => { setEditing(null); setError(null) }} onSave={(value, date) => deal.mutate({ id: l.id, value, date })} />}
                  </article>)
              })}
            </section>))}
        </div>)}
    </div>
  )
}

function DealForm({ lead, saving, onSave, onCancel }: { lead: PipelineLead; saving: boolean; onSave: (value: string, date: string) => void; onCancel: () => void }) {
  const [value, setValue] = useState(lead.dealValue != null ? String(lead.dealValue) : ""), [date, setDate] = useState(lead.expectedCloseDate ?? "")
  const problem = dealProblem(value, date)
  return (
    <form aria-label={`Deal for ${lead.name}`} onSubmit={e => { e.preventDefault(); if (!problem) onSave(value, date) }} style={{ display: "grid", gap: 6, marginTop: 6 }}>
      <label>Deal value (R) <input aria-label="Deal value" inputMode="decimal" style={{ ...field, width: "100%" }} value={value} onChange={e => setValue(e.target.value)} /></label>
      <label>Expected close date <input aria-label="Expected close date" type="date" style={{ ...field, width: "100%" }} value={date} onChange={e => setDate(e.target.value)} /></label>
      {problem && <span style={{ color: "var(--hf-danger-text)" }}>{problem}</span>}
      <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!problem || saving}>{saving ? "Saving…" : "Save deal"}</button><button type="button" style={ghost} onClick={onCancel}>Cancel</button></div>
    </form>
  )
}

function LostForm({ lead, saving, onSave, onCancel }: { lead: PipelineLead; saving: boolean; onSave: (reason: LostReason, note: string) => void; onCancel: () => void }) {
  const [reason, setReason] = useState<LostReason | "">(""), [note, setNote] = useState("")
  const problem = lostProblem(reason, note)
  return (
    <form aria-label={`Mark ${lead.name} lost`} onSubmit={e => { e.preventDefault(); if (!problem && reason) onSave(reason, note) }} style={{ display: "grid", gap: 6, marginTop: 6 }}>
      <label>Why was it lost? <select aria-label="Lost reason" style={{ ...field, width: "100%" }} value={reason} onChange={e => setReason(e.target.value as LostReason | "")}>
        <option value="">Choose a reason…</option>{LOST_REASONS.map(r => <option key={r.id} value={r.id}>{r.label}</option>)}</select></label>
      <label>Note <textarea aria-label="Lost note" rows={2} maxLength={500} style={{ ...field, width: "100%" }} value={note} onChange={e => setNote(e.target.value)} /></label>
      {reason && problem && <span style={{ color: "var(--hf-danger-text)" }}>{problem}</span>}
      <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!problem || saving}>{saving ? "Saving…" : "Mark lost"}</button><button type="button" style={ghost} onClick={onCancel}>Cancel</button></div>
    </form>
  )
}
