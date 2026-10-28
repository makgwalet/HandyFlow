// An employee's career timeline: joined, title, department and manager changes, salary changes (for those who may see pay), earlier jobs.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate } from "./er.logic"
import { card, errorText, input, label, plain, primary, unwrap } from "./attendance.shared"
import { Notice } from "./ErParts"
import { CAREER_KINDS, careerBody, careerProblem, careerSummary, emptyCareer, tenureText, type CareerForm, type CareerView } from "./profile.logic"

interface Props {
  listPath: string
  cacheKey: string
  /** Where HR adds a line; leave out when the viewer may not add. */
  addPath?: string
  /** Day the person joined, to check dates against. */
  startDate: string | null
  /** Shown above the timeline, for example the person's time with the company. */
  showTenure?: boolean
}

export default function CareerPanel({ listPath, cacheKey, addPath, startDate, showTenure = true }: Props) {
  const qc = useQueryClient()
  const key = ["career", cacheKey]
  const [form, setForm] = useState<CareerForm | null>(null)
  const [error, setError] = useState<string | null>(null)
  const q = useQuery<CareerView[]>({ queryKey: key, queryFn: async () => unwrap<CareerView[]>(await apiClient.get(listPath)) ?? [] })
  const rows = q.data ?? []

  const add = useMutation({
    mutationFn: async (f: CareerForm) => unwrap<CareerView>(await apiClient.post(addPath!, careerBody(f))),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setForm(null); setError(null) },
    onError: e => setError(errorText(e, "That could not be saved.")),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => { await apiClient.delete(`/api/v1/hr/career/${id}`) },
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setError(null) },
    onError: e => setError(errorText(e, "That could not be removed.")),
  })
  const submit = () => {
    if (!form) return
    const problem = careerProblem(form, startDate)
    if (problem) { setError(problem); return }
    setError(null)
    add.mutate(form)
  }
  const set = (patch: Partial<CareerForm>) => setForm(f => (f ? { ...f, ...patch } : f))
  const left = rows.find(r => r.kind === "TERMINATED")

  return (
    <div>
      {showTenure && startDate && <p style={{ margin: "0 0 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        {left ? "Was with the company for " : "With the company for "}<strong>{tenureText(startDate, left?.eventDate)}</strong>, since {fmtDate(startDate)}.
      </p>}
      {q.isError && <Notice kind="error">{errorText(q.error, "The career history could not be loaded.")}</Notice>}
      {!form && error && <Notice kind="error">{error}</Notice>}
      {addPath && !form && <div style={{ marginBottom: 12 }}><button onClick={() => { setForm(emptyCareer()); setError(null) }} style={plain}>Add earlier job, promotion or note</button></div>}
      {form && (
        <div role="dialog" aria-label="Add to career history" style={{ ...card, padding: 16 }}>
          {error && <Notice kind="error">{error}</Notice>}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 10 }}>
            <div><label htmlFor="car-kind" style={label}>What *</label>
              <select id="car-kind" value={form.kind} onChange={e => set({ kind: e.target.value })} style={input}>
                {CAREER_KINDS.map(k => <option key={k.value} value={k.value}>{k.label}</option>)}
              </select></div>
            <div><label htmlFor="car-date" style={label}>{form.kind === "PRIOR_EMPLOYMENT" ? "Started *" : "Date *"}</label><input id="car-date" type="date" value={form.eventDate} onChange={e => set({ eventDate: e.target.value })} style={input} /></div>
            {form.kind === "PRIOR_EMPLOYMENT" && <>
              <div><label htmlFor="car-end" style={label}>Left *</label><input id="car-end" type="date" value={form.endDate} onChange={e => set({ endDate: e.target.value })} style={input} /></div>
              <div><label htmlFor="car-employer" style={label}>Employer *</label><input id="car-employer" value={form.employer} onChange={e => set({ employer: e.target.value })} style={input} /></div>
            </>}
            {(form.kind === "PRIOR_EMPLOYMENT" || form.kind === "PROMOTION") && (
              <div><label htmlFor="car-title" style={label}>Job title *</label><input id="car-title" value={form.jobTitle} onChange={e => set({ jobTitle: e.target.value })} style={input} /></div>
            )}
            {form.kind === "TRANSFER" && (
              <div><label htmlFor="car-dept" style={label}>Moved to department *</label><input id="car-dept" value={form.department} onChange={e => set({ department: e.target.value })} style={input} /></div>
            )}
            <div style={{ gridColumn: "1 / -1" }}><label htmlFor="car-note" style={label}>Note{form.kind === "OTHER" ? " *" : ""}</label><input id="car-note" value={form.note} onChange={e => set({ note: e.target.value })} style={input} /></div>
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <button onClick={submit} disabled={add.isPending} style={primary}>{add.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => { setForm(null); setError(null) }} style={plain}>Cancel</button>
          </div>
        </div>
      )}
      {q.isLoading && <div style={{ color: "var(--hf-text-faint)" }}>Loading…</div>}
      {!q.isLoading && rows.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing is recorded yet.</p>}
      <ol style={{ listStyle: "none", margin: 0, padding: 0 }}>
        {rows.map(e => (
          <li key={e.id} style={{ display: "flex", gap: 12, padding: "10px 0", borderTop: "1px solid var(--hf-border-subtle)" }}>
            <div style={{ width: 130, flexShrink: 0, fontSize: 12, color: "var(--hf-text-muted)" }}>
              {fmtDate(e.eventDate)}{e.endDate && <> to {fmtDate(e.endDate)}</>}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700 }}>{e.kindLabel}</div>
              {careerSummary(e) && <div style={{ fontSize: 13 }}>{careerSummary(e)}</div>}
              {e.note && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{e.note}</div>}
            </div>
            {addPath && !e.auto && (
              <button onClick={() => { if (window.confirm("Remove this line?")) remove.mutate(e.id) }} style={plain} disabled={remove.isPending}>Remove</button>
            )}
          </li>
        ))}
      </ol>
    </div>
  )
}
