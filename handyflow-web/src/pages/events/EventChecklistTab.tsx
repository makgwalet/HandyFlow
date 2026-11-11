// src/pages/events/EventChecklistTab.tsx
//
// Readiness and the checklist of the selected event: "are we ready?" with each failed check explained, then the checklist by phase (pre-event, setup,
// event day, breakdown, post-event) and the contingency plans, where "done" means the plan has been reviewed. A starter list can be added; it never
// duplicates or overwrites. Reading needs EVENTS_READ; changes need EVENTS_OPS_MANAGE.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { CHECKLIST_PHASES, checklistBody, checklistProblem, doneWord, groupByPhase, phaseText, progressText, templateText, type ChecklistItem } from "./opsView"

interface Readiness { ready: boolean; checks: { code: string; label: string; passed: boolean; detail: string | null }[]; checklistProgress: { phase: string; total: number; done: number; requiredOpen: number }[] }
const field: React.CSSProperties = { ...inputStyle, width: "100%" }

export default function EventChecklistTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["EVENTS_OPS_MANAGE", "EVENTS_MANAGE"])
  const [dlg, setDlg] = useState<{ item?: ChecklistItem } | null>(null)
  const [f, setF] = useState<Record<string, any>>({})
  const set = (k: string, v: any) => setF(p => ({ ...p, [k]: v }))
  const base = `/api/v1/events/${eventId}`
  const { data: ready } = useQuery<Readiness>({ queryKey: ["event-readiness", eventId], enabled: !!eventId, queryFn: async () => { const r = await apiClient.get(`${base}/readiness`); return r.data?.data ?? r.data } })
  const { data: items = [], isLoading, error } = useQuery<ChecklistItem[]>({ queryKey: ["event-checklist", eventId], enabled: !!eventId, queryFn: async () => { const r = await apiClient.get(`${base}/checklist`); return r.data?.data ?? r.data ?? [] } })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["event-checklist"] }); qc.invalidateQueries({ queryKey: ["event-readiness"] }) }
  const toggle = useMutation({ mutationFn: ({ i }: { i: ChecklistItem }) => apiClient.post(`${base}/checklist/${i.id}/${i.done ? "reopen" : "complete"}`), onSuccess: refresh })
  const del = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/checklist/${id}`), onSuccess: refresh })
  const tpl = useMutation({ mutationFn: async () => (await apiClient.post(`${base}/checklist/from-template`)).data?.data as { added: number; skipped: number }, onSuccess: refresh })
  const save = useMutation({
    mutationFn: () => { const b = checklistBody(f as any); return dlg?.item ? apiClient.put(`${base}/checklist/${dlg.item.id}`, b) : apiClient.post(`${base}/checklist`, b) },
    onSuccess: () => { refresh(); setDlg(null) },
  })
  const open = (item?: ChecklistItem) => { setDlg({ item }); save.reset(); setF(item ? { phase: item.phase, title: item.title, description: item.description ?? "", assignedTo: item.assignedTo ?? "", dueAt: item.dueAt?.slice(0, 16) ?? "", required: item.required } : { phase: "PRE_EVENT", title: "", description: "", assignedTo: "", dueAt: "", required: true }) }

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  const groups = groupByPhase(items)
  const progress = new Map((ready?.checklistProgress ?? []).map(p => [p.phase, p]))
  const problem = checklistProblem({ phase: f.phase ?? "", title: f.title ?? "" })
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Readiness and checklist for {eventTitle}</p>
        {canManage && <div style={{ display: "flex", gap: 6 }}>
          <button type="button" style={btnGhost} disabled={tpl.isPending} onClick={() => tpl.mutate()}>Add starter checklist</button>
          <button type="button" style={btnPrimary} onClick={() => open()}>Add item</button>
        </div>}
      </div>
      {tpl.isSuccess && tpl.data && <p role="status" style={{ fontSize: 12.5 }}>{templateText(tpl.data)}</p>}
      <ErrorLine error={error ?? toggle.error ?? del.error ?? tpl.error} fallback="That could not be completed" />
      {ready && (
        <section style={{ ...card, padding: 14, marginBottom: 14 }}>
          <h3 style={{ fontSize: 14, margin: "0 0 8px" }}>{ready.ready ? "Ready to run" : "Not ready yet"}</h3>
          {ready.checks.map(c => (
            <div key={c.code} style={{ fontSize: 13, padding: "3px 0", color: c.passed ? "var(--hf-text-muted)" : "var(--hf-text)" }}>
              <span aria-hidden>{c.passed ? "✓" : "✗"}</span> <span style={{ fontWeight: c.passed ? 400 : 700 }}>{c.label}</span>{!c.passed && c.detail ? ` — ${c.detail}` : ""}
            </div>
          ))}
          <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>This is advice only; it never stops you going live.</p>
        </section>
      )}
      {isLoading ? <p>Loading…</p> : groups.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No checklist items yet.{canManage ? " Add a starter checklist to begin." : ""}</p> : groups.map(g => (
        <section key={g.phase} style={{ marginBottom: 14 }}>
          <h3 style={{ fontSize: 14, margin: "0 0 2px" }}>{phaseText(g.phase)}</h3>
          <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: "0 0 6px" }}>{progress.get(g.phase) ? progressText(progress.get(g.phase)!) : ""}</p>
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {g.items.map(i => (
              <div key={i.id} style={{ ...card, padding: "8px 14px", display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center", borderLeft: i.overdue ? "3px solid var(--hf-danger)" : undefined }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, textDecoration: i.done && i.phase !== "CONTINGENCY" ? "line-through" : "none" }}>{i.title}{!i.required && <span style={{ fontWeight: 400, fontSize: 11, color: "var(--hf-text-muted)" }}> (optional)</span>}</div>
                  {i.description && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{i.description}</div>}
                  <div style={{ fontSize: 11.5, color: i.overdue ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>
                    {doneWord(i.phase, i.done)}{i.done && i.doneByName ? ` by ${i.doneByName}` : ""}{i.assignedTo ? ` · ${i.assignedTo}` : ""}{i.dueAt ? ` · due ${i.dueAt.slice(0, 16).replace("T", " ")}` : ""}{i.overdue ? " · overdue" : ""}
                  </div>
                </div>
                {canManage && <div style={{ display: "flex", gap: 6 }}>
                  <button type="button" style={i.done ? btnGhost : btnPrimary} disabled={toggle.isPending} onClick={() => toggle.mutate({ i })}>{i.done ? "Undo" : i.phase === "CONTINGENCY" ? "Mark reviewed" : "Tick off"}</button>
                  <button type="button" style={btnGhost} onClick={() => open(i)}>Edit</button>
                  <button type="button" style={btnGhost} disabled={del.isPending} onClick={() => del.mutate(i.id)}>Remove</button>
                </div>}
              </div>
            ))}
          </div>
        </section>
      ))}
      {dlg && (
        <Modal title={dlg.item ? "Edit item" : "Add item"} onClose={() => setDlg(null)} width={500}>
          <label style={labelStyle} htmlFor="ck-phase">Phase *</label><select id="ck-phase" style={field} value={f.phase ?? ""} onChange={e => set("phase", e.target.value)}>{CHECKLIST_PHASES.map(p => <option key={p} value={p}>{phaseText(p)}</option>)}</select>
          <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="ck-title">{f.phase === "CONTINGENCY" ? "Scenario *" : "Title *"}</label><input id="ck-title" style={field} maxLength={200} value={f.title ?? ""} onChange={e => set("title", e.target.value)} />
          <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="ck-desc">{f.phase === "CONTINGENCY" ? "Response" : "Description"}</label><input id="ck-desc" style={field} maxLength={1000} value={f.description ?? ""} onChange={e => set("description", e.target.value)} />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 10 }}>
            <div><label style={labelStyle} htmlFor="ck-who">Owner</label><input id="ck-who" style={field} maxLength={200} value={f.assignedTo ?? ""} onChange={e => set("assignedTo", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="ck-due">Due</label><input id="ck-due" type="datetime-local" style={field} value={f.dueAt ?? ""} onChange={e => set("dueAt", e.target.value)} /></div>
          </div>
          <label style={{ display: "block", fontSize: 13, marginTop: 10 }}><input type="checkbox" checked={f.required !== false} onChange={e => set("required", e.target.checked)} /> Required</label>
          <ErrorLine error={save.error} fallback="The item could not be saved" />
          {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
          <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
            <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={() => setDlg(null)}>Back</button>
            <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Working…" : "Save item"}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
