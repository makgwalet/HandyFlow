// src/pages/legalcompliance/EsgTab.tsx
//
// ESG metrics and readings. Endpoints (/api/v1/legalcompliance/esg): GET /summary · GET/POST /metrics · GET/PUT /metrics/{id} · POST /metrics/{id}/retire|readings · POST /readings/{id}/correct
// A reading is never edited: a correction records a new reading and keeps the old one, marked superseded, with the reason. "Met / missed" uses the target that applied when the reading
// was recorded, so changing a target later does not change past results.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { one, unwrap, input, primaryBtn, ghostBtn, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { Plus } from "lucide-react"

const base = "/api/v1/legalcompliance/esg"

type Category = "ENVIRONMENTAL" | "SOCIAL" | "GOVERNANCE"
type Frequency = "MONTHLY" | "QUARTERLY" | "ANNUALLY"
type Direction = "LOWER_IS_BETTER" | "HIGHER_IS_BETTER" | "TRACK_ONLY"

interface Metric {
  id: string; reference: string; name: string; description: string | null; category: Category; unit: string; frequency: Frequency; direction: Direction; targetValue: number | null
  ownerName: string; frameworkRef: string | null; status: "ACTIVE" | "RETIRED"; latestPeriodEnd: string | null; latestValue: number | null; latestTargetStatus: "MET" | "MISSED" | "NONE" | null
  readingOverdue: boolean; readingCount: number; retiredReason: string | null
}
interface Reading {
  id: string; periodStart: string; periodEnd: string; value: number; notes: string | null; targetSnapshot: number | null; directionSnapshot: Direction; targetStatus: "MET" | "MISSED" | "NONE"
  recordedByName: string; correctsId: string | null; superseded: boolean; supersededAt: string | null; supersedeReason: string | null; recordedAt: string
}
interface Detail { metric: Metric; readings: Reading[] }
interface Summary { active: number; environmental: number; social: number; governance: number; readingsOverdue: number; targetsMissed: number; noReadings: number }

const pretty = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")
const CATS: Category[] = ["ENVIRONMENTAL", "SOCIAL", "GOVERNANCE"]
const num = (v: number | null) => v === null ? "—" : String(v)
const statusBadge = (s: string | null) => s === "MET" ? <Badge text="Target met" color="#166534" bg="#DCFCE7" /> : s === "MISSED" ? <Badge text="Target missed" color="#991B1B" bg="#FEE2E2" /> : <span style={{ color: "#94A3B8" }}>—</span>

function MetricForm({ initial, onClose, onSaved }: { initial: Metric | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({
    name: initial?.name ?? "", description: initial?.description ?? "", category: (initial?.category ?? "ENVIRONMENTAL") as Category, unit: initial?.unit ?? "", frequency: (initial?.frequency ?? "QUARTERLY") as Frequency,
    direction: (initial?.direction ?? "LOWER_IS_BETTER") as Direction, targetValue: initial?.targetValue === null || initial?.targetValue === undefined ? "" : String(initial.targetValue),
    ownerName: initial?.ownerName ?? "", frameworkRef: initial?.frameworkRef ?? "",
  })
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, description: f.description || null, frameworkRef: f.frameworkRef || null, targetValue: f.direction === "TRACK_ONLY" || f.targetValue === "" ? null : Number(f.targetValue) }
      return initial ? apiClient.put(`${base}/metrics/${initial.id}`, body) : apiClient.post(`${base}/metrics`, body)
    },
    onSuccess: onSaved, onError: (e: unknown) => setErr(errMsg(e, "Could not save the metric")),
  })
  const set = (k: keyof typeof f, v: string) => setF({ ...f, [k]: v })
  return (
    <Modal title={initial ? "Edit metric" : "Add an ESG metric"} onClose={onClose}>
      <Err m={err} />
      {initial && <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Changing the target applies to future readings only. Past readings keep the target they were measured against.</p>}
      <Field l="Metric *"><input style={input} value={f.name} onChange={e => set("name", e.target.value)} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <div style={{ flex: 1 }}><Field l="Category *"><select style={input} value={f.category} onChange={e => set("category", e.target.value)}>{CATS.map(c => <option key={c} value={c}>{pretty(c)}</option>)}</select></Field></div>
        <div style={{ flex: 1 }}><Field l="Unit *"><input style={input} placeholder="e.g. tCO2e, %, count" value={f.unit} onChange={e => set("unit", e.target.value)} /></Field></div>
        <div style={{ flex: 1 }}><Field l="Reported *"><select style={input} value={f.frequency} onChange={e => set("frequency", e.target.value)}>{(["MONTHLY", "QUARTERLY", "ANNUALLY"] as Frequency[]).map(c => <option key={c} value={c}>{pretty(c)}</option>)}</select></Field></div>
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <div style={{ flex: 2 }}><Field l="Good is *"><select style={input} value={f.direction} onChange={e => set("direction", e.target.value)}>{(["LOWER_IS_BETTER", "HIGHER_IS_BETTER", "TRACK_ONLY"] as Direction[]).map(c => <option key={c} value={c}>{pretty(c)}</option>)}</select></Field></div>
        <div style={{ flex: 1 }}><Field l={f.direction === "TRACK_ONLY" ? "Target (none)" : "Target *"}><input type="number" step="any" style={input} disabled={f.direction === "TRACK_ONLY"} value={f.direction === "TRACK_ONLY" ? "" : f.targetValue} onChange={e => set("targetValue", e.target.value)} /></Field></div>
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <div style={{ flex: 1 }}><Field l="Owner *"><input style={input} value={f.ownerName} onChange={e => set("ownerName", e.target.value)} /></Field></div>
        <div style={{ flex: 1 }}><Field l="Framework reference"><input style={input} placeholder="e.g. GRI 305-2" value={f.frameworkRef} onChange={e => set("frameworkRef", e.target.value)} /></Field></div>
      </div>
      <Field l="Description"><textarea style={{ ...input, minHeight: 50 }} value={f.description} onChange={e => set("description", e.target.value)} /></Field>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={onClose}>Cancel</button><button style={primaryBtn} disabled={save.isPending} onClick={() => save.mutate()}>Save</button></div>
    </Modal>
  )
}

function MetricModal({ id, canManage, onClose }: { id: string; canManage: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [err, setErr] = useState("")
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<{ correcting: Reading | null; periodStart: string; periodEnd: string; value: string; notes: string; reason: string } | null>(null)
  const [retire, setRetire] = useState<string | null>(null)
  const { data: d, isLoading } = useQuery<Detail>({ queryKey: ["lc-esg", "metric", id], queryFn: async () => one(await apiClient.get(`${base}/metrics/${id}`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-esg"] }); setErr("") }
  const fail = (e: unknown) => setErr(errMsg(e, "That did not work"))
  const save = useMutation({
    mutationFn: () => {
      if (!form) return Promise.resolve(null)
      const body = { periodStart: form.periodStart, periodEnd: form.periodEnd, value: Number(form.value), notes: form.notes || null }
      return form.correcting ? apiClient.post(`${base}/readings/${form.correcting.id}/correct`, { ...body, reason: form.reason }) : apiClient.post(`${base}/metrics/${id}/readings`, body)
    },
    onSuccess: () => { refresh(); setForm(null) }, onError: fail,
  })
  const doRetire = useMutation({ mutationFn: () => apiClient.post(`${base}/metrics/${id}/retire`, { reason: retire }), onSuccess: () => { refresh(); setRetire(null) }, onError: fail })

  if (isLoading || !d) return <Modal title="Metric" onClose={onClose} wide><p style={{ color: "#64748B" }}>Loading…</p></Modal>
  const m = d.metric
  const retired = m.status === "RETIRED"
  const today = new Date().toISOString().slice(0, 10)

  return (
    <Modal title={`${m.reference} · ${m.name}`} onClose={onClose} wide>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
        <Badge text={pretty(m.category)} color="#3730A3" bg="#E0E7FF" /><Badge text={`${pretty(m.frequency)}, ${m.unit}`} color="#475569" bg="#E2E8F0" />
        {m.direction !== "TRACK_ONLY" ? <Badge text={`Target ${m.targetValue} (${pretty(m.direction).toLowerCase()})`} color="#475569" bg="#E2E8F0" /> : <Badge text="Track only" color="#475569" bg="#E2E8F0" />}
        {retired && <Badge text="Retired" color="#475569" bg="#E2E8F0" />}{m.readingOverdue && <Badge text="Reading overdue" color="#991B1B" bg="#FEE2E2" />}
      </div>
      <div style={{ fontSize: 12, color: "#64748B", marginBottom: 10 }}>Owner {m.ownerName}{m.frameworkRef ? ` · ${m.frameworkRef}` : ""}.{m.description ? ` ${m.description}` : ""}{m.retiredReason ? ` Retired: ${m.retiredReason}` : ""}</div>
      <Err m={err} />
      {canManage && !retired && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
          <button style={primaryBtn} onClick={() => setForm({ correcting: null, periodStart: "", periodEnd: "", value: "", notes: "", reason: "" })}>Record a reading</button>
          <button style={ghostBtn} onClick={() => setEditing(true)}>Edit metric</button>
          <button style={{ ...ghostBtn, color: "#B91C1C" }} onClick={() => setRetire("")}>Retire</button>
        </div>
      )}
      {retire !== null && (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 12 }}>
          <Field l="Reason (min. 10 characters)"><textarea style={{ ...input, minHeight: 50 }} value={retire} onChange={e => setRetire(e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setRetire(null)}>Cancel</button><button style={primaryBtn} disabled={retire.trim().length < 10 || doRetire.isPending} onClick={() => doRetire.mutate()}>Retire</button></div>
        </div>
      )}
      {form && (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 12 }}>
          <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 8 }}>{form.correcting ? `Correct the reading for the period ending ${fmtDate(form.correcting.periodEnd)}` : "New reading"}</div>
          {form.correcting && <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>The earlier value stays on record, marked as superseded, with your reason.</p>}
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 130 }}><Field l="Period start *"><input type="date" style={input} max={today} value={form.periodStart} onChange={e => setForm({ ...form, periodStart: e.target.value })} /></Field></div>
            <div style={{ flex: 1, minWidth: 130 }}><Field l="Period end *"><input type="date" style={input} max={today} value={form.periodEnd} onChange={e => setForm({ ...form, periodEnd: e.target.value })} /></Field></div>
            <div style={{ flex: 1, minWidth: 130 }}><Field l={`Value (${m.unit}) *`}><input type="number" step="any" style={input} value={form.value} onChange={e => setForm({ ...form, value: e.target.value })} /></Field></div>
          </div>
          <Field l="Source or notes"><textarea style={{ ...input, minHeight: 44 }} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></Field>
          {form.correcting && <Field l="Why is it being corrected? * (min. 10 characters)"><textarea style={{ ...input, minHeight: 44 }} value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} /></Field>}
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={save.isPending || !form.periodStart || !form.periodEnd || form.value === "" || (!!form.correcting && form.reason.trim().length < 10)} onClick={() => save.mutate()}>Save</button></div>
        </div>
      )}
      <div style={{ fontSize: 13, fontWeight: 700, margin: "8px 0 6px" }}>Readings</div>
      {d.readings.length === 0 && <p style={{ fontSize: 13, color: "#64748B" }}>No readings yet.</p>}
      {d.readings.map(r => (
        <div key={r.id} style={{ borderTop: "1px solid #F1F5F9", padding: "7px 0", fontSize: 13, opacity: r.superseded ? 0.55 : 1 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <b style={{ textDecoration: r.superseded ? "line-through" : "none" }}>{num(r.value)} {m.unit}</b>
            <span style={{ color: "#64748B" }}>{fmtDate(r.periodStart)} – {fmtDate(r.periodEnd)}</span>
            {!r.superseded && statusBadge(r.targetStatus)}
            {r.targetSnapshot !== null && <span style={{ fontSize: 12, color: "#64748B" }}>against target {r.targetSnapshot}</span>}
            {r.superseded && <Badge text="Superseded" color="#475569" bg="#E2E8F0" />}
            {r.correctsId && <Badge text="Correction" color="#3730A3" bg="#E0E7FF" />}
            {canManage && !retired && !r.superseded && <button style={{ ...ghostBtn, padding: "2px 8px", fontSize: 12 }} onClick={() => setForm({ correcting: r, periodStart: r.periodStart, periodEnd: r.periodEnd, value: String(r.value), notes: r.notes ?? "", reason: "" })}>Correct</button>}
          </div>
          <div style={{ fontSize: 12, color: "#64748B" }}>Recorded by {r.recordedByName} on {fmtDateTime(r.recordedAt)}{r.notes ? ` · ${r.notes}` : ""}{r.supersedeReason ? ` · Superseded: ${r.supersedeReason}` : ""}</div>
        </div>
      ))}
      {editing && <MetricForm initial={m} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); refresh() }} />}
    </Modal>
  )
}

export default function EsgTab() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_ESG_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const [adding, setAdding] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null)
  const [showRetired, setShowRetired] = useState(false)
  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-esg", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: list = [], isLoading } = useQuery<Metric[]>({ queryKey: ["lc-esg", "list", showRetired], queryFn: async () => unwrap(await apiClient.get(`${base}/metrics`, { params: { includeRetired: showRetired } })) })

  const tile = (label: string, v: number | undefined, bad?: boolean) => (
    <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: "10px 14px", minWidth: 120 }}>
      <div style={{ fontSize: 22, fontWeight: 800, color: bad && (v ?? 0) > 0 ? "#B91C1C" : "#0F172A" }}>{v ?? "—"}</div><div style={{ fontSize: 12, color: "#64748B" }}>{label}</div>
    </div>
  )
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        {tile("Active metrics", summary?.active)}{tile("Environmental", summary?.environmental)}{tile("Social", summary?.social)}{tile("Governance", summary?.governance)}
        {tile("Readings overdue", summary?.readingsOverdue, true)}{tile("Latest target missed", summary?.targetsMissed, true)}{tile("No readings yet", summary?.noReadings)}
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <label style={{ fontSize: 13, color: "#475569" }}><input type="checkbox" checked={showRetired} onChange={e => setShowRetired(e.target.checked)} /> Show retired</label>
        {canManage && <button style={{ ...primaryBtn, display: "flex", gap: 6, alignItems: "center" }} onClick={() => setAdding(true)}><Plus size={14} />Add metric</button>}
      </div>
      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && list.length === 0 && <p style={{ color: "#64748B" }}>No ESG metrics yet.</p>}
      {CATS.map(cat => {
        const rows = list.filter(m => m.category === cat)
        if (rows.length === 0) return null
        return (
          <div key={cat} style={{ marginBottom: 18 }}>
            <h3 style={{ fontSize: 13, fontWeight: 700, color: "#334155", margin: "0 0 6px", textTransform: "uppercase", letterSpacing: 0.4 }}>{pretty(cat)}</h3>
            <div style={{ overflowX: "auto", border: "1px solid #E2E8F0", borderRadius: 10 }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                <thead><tr>{["Ref", "Metric", "Reported", "Target", "Latest", "Status", "Owner"].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 10px", background: "#F8FAFC", color: "#475569" }}>{h}</th>)}</tr></thead>
                <tbody>
                  {rows.map(m => (
                    <tr key={m.id} onClick={() => setOpenId(m.id)} style={{ cursor: "pointer", borderTop: "1px solid #F1F5F9", opacity: m.status === "RETIRED" ? 0.55 : 1 }}>
                      <td style={{ padding: "8px 10px", color: "#64748B" }}>{m.reference}</td>
                      <td style={{ padding: "8px 10px", fontWeight: 600 }}>{m.name} <span style={{ color: "#94A3B8", fontWeight: 400 }}>({m.unit})</span></td>
                      <td style={{ padding: "8px 10px" }}>{pretty(m.frequency)}</td>
                      <td style={{ padding: "8px 10px" }}>{m.direction === "TRACK_ONLY" ? "Track only" : `${num(m.targetValue)}${m.direction === "LOWER_IS_BETTER" ? " or less" : " or more"}`}</td>
                      <td style={{ padding: "8px 10px" }}>{m.latestValue === null ? "—" : `${num(m.latestValue)} (to ${fmtDate(m.latestPeriodEnd)})`}</td>
                      <td style={{ padding: "8px 10px" }}>{statusBadge(m.latestTargetStatus)} {m.readingOverdue && <Badge text="Overdue" color="#991B1B" bg="#FEE2E2" />}</td>
                      <td style={{ padding: "8px 10px", color: "#475569" }}>{m.ownerName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )
      })}
      {adding && <MetricForm initial={null} onClose={() => setAdding(false)} onSaved={() => { setAdding(false); qc.invalidateQueries({ queryKey: ["lc-esg"] }) }} />}
      {openId && <MetricModal id={openId} canManage={canManage} onClose={() => setOpenId(null)} />}
    </div>
  )
}
