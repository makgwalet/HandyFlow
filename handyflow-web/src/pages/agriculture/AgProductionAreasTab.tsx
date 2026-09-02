// src/pages/agriculture/AgProductionAreasTab.tsx
//
// Farm-scoped production areas (camps, fields, paddocks, houses, pens,
// ponds, orchards) — confirmed via AgProductionAreaController.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { MapPin, Plus, Pencil, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge } from "./constants"

export interface ProductionAreaResponse {
  id: string
  farmId: string
  name: string
  areaType: string
  sizeHectares: number | null
  capacity: number | null
  soilType: string | null
  status: string
  notes: string | null
  createdAt: string
  updatedAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const AREA_TYPES = ["CAMP", "FIELD", "PADDOCK", "HOUSE", "PEN", "POND", "ORCHARD", "OTHER"]
// ⚠ UNVERIFIED: the exact enum values ChangeAreaStatusRequest.status accepts
// weren't seen in source (only that the endpoint exists) — ACTIVE/RESTING/
// INACTIVE is a reasonable guess for a "camp resting between grazing
// rotations" domain; adjust if the backend rejects RESTING.
const AREA_STATUSES = ["ACTIVE", "RESTING", "INACTIVE"]

function AreaForm({ farmId, initial, saving, onCancel, onSave }: {
  farmId: string; initial?: Partial<ProductionAreaResponse>; saving: boolean; onCancel: () => void
  onSave: (v: { name: string; areaType: string; sizeHectares: string; capacity: string; soilType: string; notes: string }) => void
}) {
  const [name, setName] = useState(initial?.name ?? "")
  const [areaType, setAreaType] = useState(initial?.areaType ?? "CAMP")
  const [sizeHectares, setSizeHectares] = useState(initial?.sizeHectares != null ? String(initial.sizeHectares) : "")
  const [capacity, setCapacity] = useState(initial?.capacity != null ? String(initial.capacity) : "")
  const [soilType, setSoilType] = useState(initial?.soilType ?? "")
  const [notes, setNotes] = useState(initial?.notes ?? "")
  void farmId
  return (
    <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
        <div><label style={lbl}>Name</label><input value={name} onChange={e => setName(e.target.value)} style={inp} /></div>
        <div><label style={lbl}>Type</label>
          <select value={areaType} onChange={e => setAreaType(e.target.value)} style={inp}>{AREA_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select>
        </div>
        <div><label style={lbl}>Size (ha)</label><input type="number" min={0} step="0.01" value={sizeHectares} onChange={e => setSizeHectares(e.target.value)} style={inp} /></div>
        <div><label style={lbl}>Capacity</label><input type="number" min={0} value={capacity} onChange={e => setCapacity(e.target.value)} style={inp} /></div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 10, marginBottom: 12 }}>
        <div><label style={lbl}>Soil type</label><input value={soilType} onChange={e => setSoilType(e.target.value)} style={inp} /></div>
        <div><label style={lbl}>Notes</label><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></div>
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <button disabled={saving || !name.trim()} onClick={() => onSave({ name, areaType, sizeHectares, capacity, soilType, notes })}
          style={{ ...btnPrimary, opacity: saving || !name.trim() ? 0.6 : 1 }}>{saving ? "Saving…" : "Save"}</button>
        <button onClick={onCancel} style={btnGhost}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgProductionAreasTab({ farmId }: { farmId: string }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<ProductionAreaResponse | null>(null)

  const { data, isLoading } = useQuery<Page<ProductionAreaResponse>>({
    queryKey: ["ag-production-areas", farmId],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/production-areas`, { params: { size: 200 } })).data,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-production-areas", farmId] })

  const createMut = useMutation({
    mutationFn: (v: any) => apiClient.post(`/api/v1/agriculture/farms/${farmId}/production-areas`, {
      farmId, name: v.name, areaType: v.areaType,
      sizeHectares: v.sizeHectares ? Number(v.sizeHectares) : null,
      capacity: v.capacity ? Number(v.capacity) : null, soilType: v.soilType || null,
    }),
    onSuccess: () => { invalidate(); setShowCreate(false) },
  })
  const updateMut = useMutation({
    mutationFn: (v: any) => apiClient.put(`/api/v1/agriculture/production-areas/${editing!.id}`, {
      name: v.name, areaType: v.areaType, sizeHectares: v.sizeHectares ? Number(v.sizeHectares) : null,
      capacity: v.capacity ? Number(v.capacity) : null, soilType: v.soilType || null, notes: v.notes || null,
    }),
    onSuccess: () => { invalidate(); setEditing(null) },
  })
  const statusMut = useMutation({
    mutationFn: (v: { id: string; status: string }) => apiClient.patch(`/api/v1/agriculture/production-areas/${v.id}/status`, { status: v.status }),
    onSuccess: invalidate,
  })
  const deleteMut = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/agriculture/production-areas/${id}`), onSuccess: invalidate })

  const areas = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{areas.length} production area{areas.length === 1 ? "" : "s"}.</p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Add area</button>}
      </div>
      {showCreate && <AreaForm farmId={farmId} saving={createMut.isPending} onCancel={() => setShowCreate(false)} onSave={v => createMut.mutate(v)} />}
      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
        areas.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No production areas yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {areas.map((a, i) => (
            <div key={a.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <MapPin size={15} color={AG_ACCENT} />
                  <div>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{a.name}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{a.areaType}{a.sizeHectares ? ` · ${a.sizeHectares} ha` : ""}{a.capacity ? ` · Capacity ${a.capacity}` : ""}{a.soilType ? ` · ${a.soilType}` : ""}</p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <select value={a.status} onChange={e => statusMut.mutate({ id: a.id, status: e.target.value })} style={{ ...statusBadge(a.status), border: "none", cursor: "pointer" }}>
                    {AREA_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                  <button onClick={() => setEditing(a)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
                  <button onClick={() => { if (confirm(`Delete area "${a.name}"?`)) deleteMut.mutate(a.id) }} title="Delete" style={{ ...iconBtn, color: "#DC2626" }}><Trash2 size={13} /></button>
                </div>
              </div>
              {editing?.id === a.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <AreaForm farmId={farmId} initial={editing} saving={updateMut.isPending} onCancel={() => setEditing(null)} onSave={v => updateMut.mutate(v)} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }
const inp: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12.5, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { display: "inline-flex", alignItems: "center", padding: "8px 14px", borderRadius: 8, border: "none", background: AG_ACCENT, color: "#fff", fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnGhost: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }
const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", cursor: "pointer" }
