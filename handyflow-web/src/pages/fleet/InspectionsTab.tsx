// src/pages/fleet/InspectionsTab.tsx
//
// Inspections: start one from a template, answer the checklist (pass / fail / N/A with comment and photo), sign and complete.
// A failed item becomes a maintenance issue when the inspection is completed (done by the server).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { OUTCOME_LABEL, answeredCount, bySection, completionProblems, failedCount, startBody, startProblem, type Detail, type Outcome, type Result } from "./inspections.logic"

interface Summary { id: string; registration: string; templateName: string; inspectorName: string | null; status: string; verdict: string | null; failedCount: number; startedAt: string }
interface Full extends Detail { summary: Summary; signerName: string | null }
interface Template { id: string; name: string; active: boolean }
interface VehicleOption { id: string; registration: string }

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] }>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 12 }
const OUTCOMES: Outcome[] = ["PASS", "FAIL", "NOT_APPLICABLE"]

function Item({ r, id, editable, onChange }: { r: Result; id: string; editable: boolean; onChange: () => void }) {
  const [comment, setComment] = useState(r.comment ?? "")
  const [error, setError] = useState<string | null>(null)
  const answer = useMutation({
    mutationFn: (outcome: Outcome) => apiClient.put(`/api/v1/fleet/inspections/${id}/answers`, { answers: [{ resultId: r.id, outcome, comment: comment.trim() || null }] }),
    onSuccess: () => { setError(null); onChange() }, onError: e => setError(errorOf(e)),
  })
  const photo = useMutation({
    mutationFn: (file: File) => { const f = new FormData(); f.append("file", file); f.append("resultId", r.id); return apiClient.post(`/api/v1/fleet/inspections/${id}/photos`, f) },
    onSuccess: () => { setError(null); onChange() }, onError: e => setError(errorOf(e)),
  })
  return (
    <li aria-label={r.label} style={{ padding: "8px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <span style={{ flex: 1, minWidth: 160, fontSize: 13 }}>{r.label}{r.required && <span aria-label="required"> *</span>}</span>
        {OUTCOMES.map(o => (
          <button key={o} type="button" aria-pressed={r.outcome === o} disabled={!editable || answer.isPending} onClick={() => answer.mutate(o)}
            style={{ ...field, cursor: editable ? "pointer" : "default", fontWeight: r.outcome === o ? 800 : 500,
              background: r.outcome === o ? (o === "FAIL" ? "var(--hf-danger-soft)" : o === "PASS" ? "var(--hf-success-soft-strong)" : "var(--hf-surface-sunken)") : "var(--hf-surface)" }}>{OUTCOME_LABEL[o]}</button>))}
      </div>
      {editable && (
        <div style={{ display: "flex", gap: 8, marginTop: 6, flexWrap: "wrap" }}>
          <input aria-label={`Comment on ${r.label}`} placeholder="Comment" style={{ ...field, flex: 1, minWidth: 160 }} value={comment} onChange={e => setComment(e.target.value)} />
          <input aria-label={`Photo for ${r.label}`} type="file" accept="image/jpeg,image/png,image/webp" onChange={e => { const f = e.target.files?.[0]; if (f) photo.mutate(f); e.target.value = "" }} />
        </div>)}
      {r.outcome === "FAIL" && r.photoRequiredOnFail && r.photoIds.length === 0 && <div style={{ fontSize: 11.5, color: "var(--hf-danger-text)", marginTop: 4 }}>A photo is required for this failure.</div>}
      {r.photoIds.length > 0 && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", marginTop: 4 }}>{r.photoIds.length} {r.photoIds.length === 1 ? "photo" : "photos"} attached</div>}
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{error}</div>}
    </li>
  )
}

function Runner({ id, onClose }: { id: string; onClose: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const [signer, setSigner] = useState("")
  const [error, setError] = useState<string | null>(null)
  const key = ["fleet", "inspection", id]
  const { data } = useQuery<Full>({ queryKey: key, queryFn: async () => unwrap<Full>(await apiClient.get(`/api/v1/fleet/inspections/${id}`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["fleet", "inspections"] }) }
  const sign = useMutation({
    mutationFn: (file: File) => { const f = new FormData(); f.append("file", file); return apiClient.post(`/api/v1/fleet/inspections/${id}/signature?signerName=${encodeURIComponent(signer.trim())}`, f) },
    onSuccess: () => { setError(null); refresh() }, onError: e => setError(errorOf(e)),
  })
  const complete = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fleet/inspections/${id}/complete`, {}), onSuccess: () => { setError(null); refresh() }, onError: e => setError(errorOf(e)) })
  const cancel = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fleet/inspections/${id}/cancel`), onSuccess: () => { refresh(); onClose() }, onError: e => setError(errorOf(e)) })
  if (!data) return <p>Loading…</p>
  const open = data.summary.status === "IN_PROGRESS"
  const editable = open && canManage
  const problems = completionProblems(data)
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h3 style={{ margin: 0 }}>{data.summary.registration} · {data.summary.templateName}</h3>
        <button type="button" style={field} onClick={onClose}>Close</button>
      </div>
      <div style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>
        {answeredCount(data.results)} of {data.results.length} answered · {failedCount(data.results)} failed
        {data.summary.verdict && <strong style={{ marginLeft: 8, color: data.summary.verdict === "FAIL" ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>{data.summary.verdict === "FAIL" ? "Failed" : "Passed"}</strong>}
      </div>
      {bySection(data.results).map(g => (
        <section key={g.section}><h4 style={{ margin: "10px 0 2px" }}>{g.section}</h4>
          <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>{g.items.map(r => <Item key={r.id} r={r} id={id} editable={editable} onChange={refresh} />)}</ul></section>))}
      {data.signatureRequired && (
        <div style={{ marginTop: 12, fontSize: 13 }}>
          {data.signatureRecorded ? <span>Signed by <strong>{data.signerName}</strong></span> : editable && (
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <input aria-label="Signer name" placeholder="Inspector's name" style={field} value={signer} onChange={e => setSigner(e.target.value)} />
              <input aria-label="Signature image" type="file" accept="image/png,image/jpeg" disabled={!signer.trim()} onChange={e => { const f = e.target.files?.[0]; if (f) sign.mutate(f); e.target.value = "" }} />
            </div>)}
        </div>)}
      {editable && (
        <div style={{ marginTop: 12 }}>
          {problems.map(p => <div key={p} style={{ fontSize: 12.5, color: "var(--hf-warning-text)" }}>{p}</div>)}
          <button type="button" style={{ ...btn, marginTop: 8, opacity: problems.length || complete.isPending ? 0.5 : 1 }} disabled={problems.length > 0 || complete.isPending} onClick={() => complete.mutate()}>Complete inspection</button>
          <button type="button" style={{ ...field, marginLeft: 8, cursor: "pointer" }} onClick={() => cancel.mutate()}>Cancel inspection</button>
        </div>)}
      {!open && data.summary.verdict === "FAIL" && <div style={{ fontSize: 12.5, marginTop: 10 }}>A maintenance issue was logged for each failed item.</div>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 8 }}>{error}</div>}
    </div>
  )
}

function StartForm({ onStarted }: { onStarted: (id: string) => void }) {
  const [f, setF] = useState({ vehicleId: "", templateId: "", odometer: "", inspectorName: "" })
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof typeof f, v: string) => { setF(p => ({ ...p, [k]: v })); setError(null) }
  const { data: vehicles = [] } = useQuery<VehicleOption[]>({ queryKey: ["fleet", "vehicle-options"], queryFn: async () => list<VehicleOption>(await apiClient.get("/api/v1/fleet/vehicles?size=200")) })
  const { data: templates = [] } = useQuery<Template[]>({ queryKey: ["fleet", "inspection-templates"], queryFn: async () => list<Template>(await apiClient.get("/api/v1/fleet/inspection-templates")) })
  const qc = useQueryClient()
  const starter = useMutation({
    mutationFn: () => apiClient.post("/api/v1/fleet/inspection-templates/starter"),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fleet", "inspection-templates"] }), onError: e => setError(errorOf(e)),
  })
  const start = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fleet/vehicles/${f.vehicleId}/inspections`, startBody(f)),
    onSuccess: r => onStarted(unwrap<Full>(r).summary.id), onError: e => setError(errorOf(e)),
  })
  const active = templates.filter(t => t.active)
  const problem = startProblem(f)
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>Start an inspection</h3>
      {active.length === 0 ? (
        <div style={{ fontSize: 13 }}>No inspection templates yet. <button type="button" style={btn} disabled={starter.isPending} onClick={() => starter.mutate()}>Create the daily vehicle inspection</button></div>
      ) : (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          <select aria-label="Vehicle" style={field} value={f.vehicleId} onChange={e => set("vehicleId", e.target.value)}><option value="">Vehicle…</option>{vehicles.map(v => <option key={v.id} value={v.id}>{v.registration}</option>)}</select>
          <select aria-label="Inspection" style={field} value={f.templateId} onChange={e => set("templateId", e.target.value)}><option value="">Inspection…</option>{active.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select>
          <input aria-label="Odometer" type="number" min="0" placeholder="Odometer (km)" style={{ ...field, width: 130 }} value={f.odometer} onChange={e => set("odometer", e.target.value)} />
          <input aria-label="Inspector" placeholder="Inspector" style={field} value={f.inspectorName} onChange={e => set("inspectorName", e.target.value)} />
          <button type="button" style={{ ...btn, opacity: problem || start.isPending ? 0.5 : 1 }} disabled={!!problem || start.isPending} onClick={() => start.mutate()}>Start</button>
        </div>)}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 8 }}>{error}</div>}
    </div>
  )
}

export default function InspectionsTab() {
  const canManage = usePermission("FLEET_MANAGE")
  const [status, setStatus] = useState("")
  const [openId, setOpenId] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const qc = useQueryClient()
  const { data: rows = [], isLoading, isError } = useQuery<Summary[]>({
    queryKey: ["fleet", "inspections", status],
    queryFn: async () => list<Summary>(await apiClient.get(`/api/v1/fleet/inspections${status ? `?status=${status}` : ""}`)),
  })
  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 12, alignItems: "center" }}>
        <select aria-label="Filter by status" style={field} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All inspections</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option></select>
        {canManage && <button type="button" style={btn} onClick={() => setStarting(s => !s)}>{starting ? "Cancel" : "Start an inspection"}</button>}
      </div>
      {starting && <StartForm onStarted={id => { setStarting(false); setOpenId(id); qc.invalidateQueries({ queryKey: ["fleet", "inspections"] }) }} />}
      {openId && <Runner id={openId} onClose={() => setOpenId(null)} />}
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load inspections.</div>}
      {!isLoading && !isError && rows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No inspections yet.</p>}
      {rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
          <thead><tr>{["Vehicle", "Inspection", "Started", "Inspector", "Status", "Result", ""].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 10px", fontSize: 11, textTransform: "uppercase", color: "var(--hf-text-faint)" }}>{h}</th>)}</tr></thead>
          <tbody>{rows.map(i => (
            <tr key={i.id} aria-label={`${i.registration} ${i.templateName}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={{ padding: "9px 10px" }}><strong>{i.registration}</strong></td><td style={{ padding: "9px 10px" }}>{i.templateName}</td>
              <td style={{ padding: "9px 10px" }}>{new Date(i.startedAt).toLocaleDateString("en-ZA")}</td><td style={{ padding: "9px 10px" }}>{i.inspectorName ?? "—"}</td>
              <td style={{ padding: "9px 10px" }}>{i.status === "IN_PROGRESS" ? "In progress" : i.status === "COMPLETED" ? "Completed" : "Cancelled"}</td>
              <td style={{ padding: "9px 10px", color: i.verdict === "FAIL" ? "var(--hf-danger-text)" : undefined }}>{i.verdict === "FAIL" ? `Failed (${i.failedCount})` : i.verdict === "PASS" ? "Passed" : "—"}</td>
              <td style={{ padding: "9px 10px" }}><button type="button" style={field} onClick={() => setOpenId(i.id)}>{i.status === "IN_PROGRESS" && canManage ? "Continue" : "View"}</button></td>
            </tr>))}</tbody>
        </table>)}
    </div>
  )
}
