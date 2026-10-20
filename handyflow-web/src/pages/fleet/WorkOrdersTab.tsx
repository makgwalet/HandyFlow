// src/pages/fleet/WorkOrdersTab.tsx
//
// The workshop: work orders from open to completed (quotation and approval, actual cost lines, downtime) and the technicians who do them.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import {
  LINE_LABEL, LINE_TYPES, PRIORITIES, STATUS_LABEL, TYPE_LABEL, canEditLines, completeBody, completeProblem, emptyComplete, emptyLine, emptyOrder, emptyQuote, emptyTech,
  lineBody, lineProblem, orderBody, orderProblem, quoteBody, quoteProblem, stepsFor, techBody, techProblem, varianceText, type StepOption,
} from "./workOrders.logic"

interface Order { id: string; referenceNumber: string; vehicleId: string; registration: string; type: string; title: string; priority: string; status: string; assignedTechnicianName: string | null; quotedAmount: number | null; actualTotal: number | null; scheduledFor: string | null }
interface Line { id: string; lineType: string; description: string; quantity: number; unitCost: number; lineTotal: number; technicianName: string | null }
interface Detail { summary: Order; description: string | null; rejectionReason: string | null; holdReason: string | null; lines: Line[]; labourTotal: number; partsTotal: number; subletTotal: number; otherTotal: number; varianceToQuote: number | null; overQuote: boolean; downtimeHours: number | null; downtimeOngoing: boolean }
interface Tech { id: string; name: string; phone: string | null; hourlyRate: number | null; active: boolean }
interface VehicleOption { id: string; registration: string }

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] }>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const R = (n: number | null | undefined) => (n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 12 }

function useTechnicians() { return useQuery<Tech[]>({ queryKey: ["fleet", "technicians"], queryFn: async () => list<Tech>(await apiClient.get("/api/v1/fleet/technicians")) }) }

function Runner({ id, onClose }: { id: string; onClose: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE"), canAdmin = usePermission("FLEET_ADMIN")
  const key = ["fleet", "work-order", id]
  const base = `/api/v1/fleet/work-orders/${id}`
  const { data } = useQuery<Detail>({ queryKey: key, queryFn: async () => unwrap<Detail>(await apiClient.get(base)) })
  const { data: techs = [] } = useTechnicians()
  const [step, setStep] = useState<StepOption | null>(null)
  const [text, setText] = useState("")
  const [quote, setQuote] = useState(emptyQuote()), [done, setDone] = useState(emptyComplete()), [line, setLine] = useState(emptyLine())
  const [error, setError] = useState<string | null>(null)
  const refresh = () => { qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["fleet", "work-orders"] }) }
  const ok = () => { setError(null); setStep(null); setText(""); refresh() }
  const run = useMutation({
    mutationFn: (s: StepOption) => {
      switch (s.step) {
        case "QUOTE": return apiClient.put(`${base}/quotation`, quoteBody(quote))
        case "APPROVE": return apiClient.post(`${base}/approve`, {})
        case "REJECT": return apiClient.post(`${base}/reject`, { reason: text.trim() })
        case "START": return apiClient.post(`${base}/start`, {})
        case "HOLD": return apiClient.post(`${base}/hold`, { reason: text.trim() })
        case "RESUME": return apiClient.post(`${base}/resume`, {})
        case "CANCEL": return apiClient.post(`${base}/cancel`, { reason: text.trim() })
        case "COMPLETE": return apiClient.post(`${base}/complete`, completeBody(done))
      }
    },
    onSuccess: ok, onError: e => setError(errorOf(e)),
  })
  const addLine = useMutation({ mutationFn: () => apiClient.post(`${base}/lines`, lineBody(line)), onSuccess: () => { setLine(emptyLine()); ok() }, onError: e => setError(errorOf(e)) })
  const removeLine = useMutation({ mutationFn: (lineId: string) => apiClient.delete(`${base}/lines/${lineId}`), onSuccess: ok, onError: e => setError(errorOf(e)) })
  if (!data) return <p>Loading…</p>
  const o = data.summary
  const steps = stepsFor(o.status).filter(s => (s.admin ? canAdmin : canManage))
  const stepProblem = !step ? null : step.step === "QUOTE" ? quoteProblem(quote) : step.step === "COMPLETE" ? completeProblem(o.type, done) : step.needsText && !text.trim() ? "A reason is needed." : null
  const var_ = varianceText(data.varianceToQuote)
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between" }}><h3 style={{ margin: 0 }}>{o.referenceNumber} · {o.registration} · {o.title}</h3><button type="button" style={field} onClick={onClose}>Close</button></div>
      <div style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>
        {TYPE_LABEL[o.type]} · {o.priority.toLowerCase()} · <strong>{STATUS_LABEL[o.status]}</strong>{o.assignedTechnicianName ? ` · ${o.assignedTechnicianName}` : ""}
        {data.downtimeHours != null && ` · off the road ${data.downtimeHours.toFixed(1)} h${data.downtimeOngoing ? " so far" : ""}`}
      </div>
      {data.rejectionReason && <div style={{ fontSize: 12.5, color: "var(--hf-danger-text)" }}>Quotation rejected: {data.rejectionReason}</div>}
      {data.holdReason && o.status === "ON_HOLD" && <div style={{ fontSize: 12.5, color: "var(--hf-warning-text)" }}>On hold: {data.holdReason}</div>}
      <div style={{ fontSize: 13, margin: "8px 0" }}>Quoted <strong>{R(o.quotedAmount)}</strong> · Actual <strong>{R(o.actualTotal)}</strong>
        {var_ && <span style={{ marginLeft: 8, fontWeight: 700, color: data.overQuote ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>{var_}</span>}</div>
      <table style={{ width: "100%", fontSize: 12.5, borderCollapse: "collapse" }}>
        <thead><tr>{["Type", "Description", "Qty", "Unit", "Total", ""].map(h => <th key={h} style={{ textAlign: "left", padding: "4px 6px", fontSize: 11, color: "var(--hf-text-faint)" }}>{h}</th>)}</tr></thead>
        <tbody>{data.lines.map(l => (
          <tr key={l.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
            <td style={{ padding: "5px 6px" }}>{LINE_LABEL[l.lineType]}</td><td style={{ padding: "5px 6px" }}>{l.description}{l.technicianName ? ` (${l.technicianName})` : ""}</td>
            <td style={{ padding: "5px 6px" }}>{l.quantity}</td><td style={{ padding: "5px 6px" }}>{R(l.unitCost)}</td><td style={{ padding: "5px 6px" }}>{R(l.lineTotal)}</td>
            <td>{canManage && canEditLines(o.status) && <button type="button" aria-label={`Remove ${l.description}`} style={field} onClick={() => removeLine.mutate(l.id)}>Remove</button>}</td>
          </tr>))}</tbody>
      </table>
      {data.lines.length === 0 && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>No costs recorded yet.</p>}
      {canManage && canEditLines(o.status) && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "8px 0" }}>
          <select aria-label="Cost type" style={field} value={line.lineType} onChange={e => setLine({ ...line, lineType: e.target.value })}>{LINE_TYPES.map(t => <option key={t} value={t}>{LINE_LABEL[t]}</option>)}</select>
          <input aria-label="Cost description" placeholder="Description" style={{ ...field, minWidth: 180 }} value={line.description} onChange={e => setLine({ ...line, description: e.target.value })} />
          <input aria-label={line.lineType === "LABOUR" ? "Hours" : "Quantity"} type="number" min="0" style={{ ...field, width: 80 }} value={line.quantity} onChange={e => setLine({ ...line, quantity: e.target.value })} />
          <input aria-label="Unit cost" type="number" min="0" placeholder={line.lineType === "LABOUR" ? "Rate (optional)" : "Unit cost"} style={{ ...field, width: 120 }} value={line.unitCost} onChange={e => setLine({ ...line, unitCost: e.target.value })} />
          {line.lineType === "LABOUR" && <select aria-label="Technician" style={field} value={line.technicianId} onChange={e => setLine({ ...line, technicianId: e.target.value })}><option value="">Technician…</option>{techs.filter(t => t.active).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select>}
          <button type="button" style={{ ...btn, opacity: lineProblem(line) || addLine.isPending ? 0.5 : 1 }} disabled={!!lineProblem(line) || addLine.isPending} onClick={() => addLine.mutate()}>Add cost</button>
        </div>)}
      {steps.length > 0 && !step && <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>{steps.map(s => <button key={s.step} type="button" style={{ ...btn, background: s.step === "CANCEL" || s.step === "REJECT" ? "var(--hf-danger-text)" : undefined }} onClick={() => { setStep(s); setError(null) }}>{s.label}</button>)}</div>}
      {step && (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10, marginTop: 8 }}>
          <strong>{step.label}</strong>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>
            {step.needsText && <input aria-label="Reason" placeholder="Reason" style={{ ...field, minWidth: 280 }} value={text} onChange={e => setText(e.target.value)} />}
            {step.step === "QUOTE" && <>
              <input aria-label="Quoted amount" type="number" min="0" placeholder="Amount (R)" style={field} value={quote.amount} onChange={e => setQuote({ ...quote, amount: e.target.value })} />
              <input aria-label="Supplier" placeholder="Supplier" style={field} value={quote.supplier} onChange={e => setQuote({ ...quote, supplier: e.target.value })} />
              <input aria-label="Quotation reference" placeholder="Quote ref" style={field} value={quote.quotationRef} onChange={e => setQuote({ ...quote, quotationRef: e.target.value })} />
              <input aria-label="Valid until" type="date" style={field} value={quote.validUntil} onChange={e => setQuote({ ...quote, validUntil: e.target.value })} /></>}
            {step.step === "COMPLETE" && <>
              <input aria-label="Odometer" type="number" min="0" placeholder={o.type === "SERVICE" ? "Odometer (required)" : "Odometer (km)"} style={field} value={done.odometer} onChange={e => setDone({ ...done, odometer: e.target.value })} />
              <input aria-label="Invoice reference" placeholder="Invoice ref" style={field} value={done.invoiceRef} onChange={e => setDone({ ...done, invoiceRef: e.target.value })} />
              <input aria-label="Next service at" type="number" min="0" placeholder="Next service (km)" style={field} value={done.nextServiceKm} onChange={e => setDone({ ...done, nextServiceKm: e.target.value })} />
              <input aria-label="Completion notes" placeholder="Notes" style={{ ...field, minWidth: 220 }} value={done.completionNotes} onChange={e => setDone({ ...done, completionNotes: e.target.value })} /></>}
          </div>
          {stepProblem && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>{stepProblem}</div>}
          <div style={{ marginTop: 8, display: "flex", gap: 8 }}>
            <button type="button" style={{ ...btn, opacity: stepProblem || run.isPending ? 0.5 : 1 }} disabled={!!stepProblem || run.isPending} onClick={() => run.mutate(step)}>Confirm</button>
            <button type="button" style={field} onClick={() => setStep(null)}>Back</button>
          </div>
        </div>)}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 8 }}>{error}</div>}
    </div>
  )
}

function NewOrder({ onCreated }: { onCreated: (id: string) => void }) {
  const [f, setF] = useState(emptyOrder())
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof typeof f, v: string) => { setF(p => ({ ...p, [k]: v })); setError(null) }
  const { data: vehicles = [] } = useQuery<VehicleOption[]>({ queryKey: ["fleet", "vehicle-options"], queryFn: async () => list<VehicleOption>(await apiClient.get("/api/v1/fleet/vehicles?size=200")) })
  const { data: techs = [] } = useTechnicians()
  const create = useMutation({ mutationFn: () => apiClient.post("/api/v1/fleet/work-orders", orderBody(f)), onSuccess: r => onCreated(unwrap<Detail>(r).summary.id), onError: e => setError(errorOf(e)) })
  const problem = orderProblem(f)
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>New work order</h3>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        <select aria-label="Vehicle" style={field} value={f.vehicleId} onChange={e => set("vehicleId", e.target.value)}><option value="">Vehicle…</option>{vehicles.map(v => <option key={v.id} value={v.id}>{v.registration}</option>)}</select>
        <select aria-label="Type" style={field} value={f.type} onChange={e => set("type", e.target.value)}>{Object.entries(TYPE_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <input aria-label="Title" placeholder="What needs doing" style={{ ...field, minWidth: 240 }} value={f.title} onChange={e => set("title", e.target.value)} />
        <select aria-label="Priority" style={field} value={f.priority} onChange={e => set("priority", e.target.value)}>{PRIORITIES.map(p => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}</select>
        <select aria-label="Assign technician" style={field} value={f.technicianId} onChange={e => set("technicianId", e.target.value)}><option value="">Technician…</option>{techs.filter(t => t.active).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select>
        <input aria-label="Supplier" placeholder="Supplier" style={field} value={f.supplier} onChange={e => set("supplier", e.target.value)} />
        <input aria-label="Scheduled for" type="date" style={field} value={f.scheduledFor} onChange={e => set("scheduledFor", e.target.value)} />
        <button type="button" style={{ ...btn, opacity: problem || create.isPending ? 0.5 : 1 }} disabled={!!problem || create.isPending} onClick={() => create.mutate()}>Create</button>
      </div>
      {problem && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>{problem}</div>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </div>
  )
}

function Technicians() {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const { data: techs = [] } = useTechnicians()
  const [f, setF] = useState(emptyTech())
  const [error, setError] = useState<string | null>(null)
  const add = useMutation({ mutationFn: () => apiClient.post("/api/v1/fleet/technicians", techBody(f)), onSuccess: () => { setF(emptyTech()); setError(null); qc.invalidateQueries({ queryKey: ["fleet", "technicians"] }) }, onError: e => setError(errorOf(e)) })
  const problem = techProblem(f)
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>Technicians</h3>
      {techs.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No technicians yet.</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 13 }}>{techs.map(t => <li key={t.id} style={{ padding: "4px 0" }}>{t.name}{t.phone ? ` · ${t.phone}` : ""}{t.hourlyRate != null ? ` · ${R(t.hourlyRate)}/h` : ""}{!t.active && " · inactive"}</li>)}</ul>
      {canManage && <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>
        <input aria-label="Technician name" placeholder="Name" style={field} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} />
        <input aria-label="Technician phone" placeholder="Phone" style={field} value={f.phone} onChange={e => setF({ ...f, phone: e.target.value })} />
        <input aria-label="Hourly rate" type="number" min="0" placeholder="Rate per hour (R)" style={field} value={f.hourlyRate} onChange={e => setF({ ...f, hourlyRate: e.target.value })} />
        <button type="button" style={{ ...btn, opacity: problem || add.isPending ? 0.5 : 1 }} disabled={!!problem || add.isPending} onClick={() => add.mutate()}>Add technician</button>
      </div>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </div>
  )
}

export default function WorkOrdersTab() {
  const canManage = usePermission("FLEET_MANAGE")
  const qc = useQueryClient()
  const [status, setStatus] = useState("")
  const [openId, setOpenId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false), [showTechs, setShowTechs] = useState(false)
  const { data: rows = [], isLoading, isError } = useQuery<Order[]>({ queryKey: ["fleet", "work-orders", status], queryFn: async () => list<Order>(await apiClient.get(`/api/v1/fleet/work-orders${status ? `?status=${status}` : ""}`)) })
  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 12, alignItems: "center", flexWrap: "wrap" }}>
        <select aria-label="Filter by status" style={field} value={status} onChange={e => setStatus(e.target.value)}><option value="">All work orders</option>{Object.entries(STATUS_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        {canManage && <button type="button" style={btn} onClick={() => setCreating(c => !c)}>{creating ? "Cancel" : "New work order"}</button>}
        <button type="button" style={field} onClick={() => setShowTechs(s => !s)}>{showTechs ? "Hide technicians" : "Technicians"}</button>
      </div>
      {showTechs && <Technicians />}
      {creating && <NewOrder onCreated={id => { setCreating(false); setOpenId(id); qc.invalidateQueries({ queryKey: ["fleet", "work-orders"] }) }} />}
      {openId && <Runner id={openId} onClose={() => setOpenId(null)} />}
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load work orders.</div>}
      {!isLoading && !isError && rows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No work orders yet.</p>}
      {rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
          <thead><tr>{["Order", "Vehicle", "Work", "Priority", "Status", "Quoted", "Actual", ""].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 10px", fontSize: 11, textTransform: "uppercase", color: "var(--hf-text-faint)" }}>{h}</th>)}</tr></thead>
          <tbody>{rows.map(o => (
            <tr key={o.id} aria-label={o.referenceNumber} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={{ padding: "9px 10px" }}><strong>{o.referenceNumber}</strong></td><td style={{ padding: "9px 10px" }}>{o.registration}</td>
              <td style={{ padding: "9px 10px" }}>{o.title}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{TYPE_LABEL[o.type]}{o.assignedTechnicianName ? ` · ${o.assignedTechnicianName}` : ""}</div></td>
              <td style={{ padding: "9px 10px" }}>{o.priority.charAt(0) + o.priority.slice(1).toLowerCase()}</td><td style={{ padding: "9px 10px" }}>{STATUS_LABEL[o.status]}</td>
              <td style={{ padding: "9px 10px" }}>{R(o.quotedAmount)}</td><td style={{ padding: "9px 10px" }}>{R(o.actualTotal)}</td>
              <td style={{ padding: "9px 10px" }}><button type="button" style={field} onClick={() => setOpenId(o.id)}>Open</button></td>
            </tr>))}</tbody>
        </table>)}
    </div>
  )
}
