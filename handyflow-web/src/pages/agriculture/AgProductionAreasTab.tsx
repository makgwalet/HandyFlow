// src/pages/agriculture/AgProductionAreasTab.tsx
//
// Farm-scoped production areas (camps, fields, paddocks, houses, pens,
// ponds, orchards) — confirmed via AgProductionAreaController.
import type React from "react"
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { MapPin, Plus, Pencil, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, statusBadge, inp, btnPrimary, btnGhost } from "./constants"
import { agConfirm } from "./agConfirm"
import { Field, QueryError } from "./agUi"
import AgManage from "./AgManage"
import { SOIL_TYPES, withCurrent } from "./agLookups"
import { usePermission } from "../../hooks/usePermission"

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

type AreaValues = { name: string; areaType: string; sizeHectares: string; capacity: string; soilType: string; notes: string }

function AreaForm({ farmId, initial, saving, onCancel, onSave }: {
  farmId: string; initial?: Partial<ProductionAreaResponse>; saving: boolean; onCancel: () => void
  onSave: (v: AreaValues) => void
}) {
  const [name, setName] = useState(initial?.name ?? "")
  const [areaType, setAreaType] = useState(initial?.areaType ?? "CAMP")
  const [sizeHectares, setSizeHectares] = useState(initial?.sizeHectares != null ? String(initial.sizeHectares) : "")
  const [capacity, setCapacity] = useState(initial?.capacity != null ? String(initial.capacity) : "")
  const [soilType, setSoilType] = useState(initial?.soilType ?? "")
  const [notes, setNotes] = useState(initial?.notes ?? "")
  void farmId
  return (
    <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 10 }}>
        <Field label="Name"><input value={name} onChange={e => setName(e.target.value)} style={inp} /></Field>
        <Field label="Type"><select value={areaType} onChange={e => setAreaType(e.target.value)} style={inp}>{AREA_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></Field>
        <Field label="Size (ha)"><input type="number" min={0} step="0.01" value={sizeHectares} onChange={e => setSizeHectares(e.target.value)} style={inp} /></Field>
        <Field label="Capacity"><input type="number" min={0} value={capacity} onChange={e => setCapacity(e.target.value)} style={inp} /></Field>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 12 }}>
        <Field label="Soil type"><select value={soilType} onChange={e => setSoilType(e.target.value)} style={inp}><option value="">Not set</option>{withCurrent(SOIL_TYPES, soilType).map(t => <option key={t} value={t}>{t}</option>)}</select></Field>
        <Field label="Notes"><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></Field>
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
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<ProductionAreaResponse | null>(null)

  const { data, isLoading, isError, refetch } = useQuery<Page<ProductionAreaResponse>>({
    queryKey: ["ag-production-areas", farmId],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/production-areas`, { params: { size: 200 } })).data,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-production-areas", farmId] })

  const createMut = useAgToastMutation({
    mutationFn: (v: AreaValues) => apiClient.post(`/api/v1/agriculture/farms/${farmId}/production-areas`, {
      farmId, name: v.name, areaType: v.areaType,
      sizeHectares: v.sizeHectares ? Number(v.sizeHectares) : null,
      capacity: v.capacity ? Number(v.capacity) : null, soilType: v.soilType || null,
    }),
    onSuccess: () => { invalidate(); setShowCreate(false) },
  })
  const updateMut = useAgToastMutation({
    mutationFn: (v: AreaValues) => apiClient.put(`/api/v1/agriculture/production-areas/${editing!.id}`, {
      name: v.name, areaType: v.areaType, sizeHectares: v.sizeHectares ? Number(v.sizeHectares) : null,
      capacity: v.capacity ? Number(v.capacity) : null, soilType: v.soilType || null, notes: v.notes || null,
    }),
    onSuccess: () => { invalidate(); setEditing(null) },
  })
  const statusMut = useAgToastMutation({
    mutationFn: (v: { id: string; status: string }) => apiClient.patch(`/api/v1/agriculture/production-areas/${v.id}/status`, { status: v.status }),
    onSuccess: invalidate,
  })
  const deleteMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/agriculture/production-areas/${id}`), onSuccess: invalidate })

  const areas = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>{areas.length} production area{areas.length === 1 ? "" : "s"}.</p>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Add area</button></AgManage>}
      </div>
      {showCreate && <AreaForm farmId={farmId} saving={createMut.isPending} onCancel={() => setShowCreate(false)} onSave={v => createMut.mutate(v)} />}
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        areas.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No production areas yet.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {areas.map((a, i) => (
            <div key={a.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <MapPin size={15} style={{ color: AG_ACCENT_TEXT }} />
                  <div>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{a.name}</p>
                    <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{a.areaType}{a.sizeHectares ? ` · ${a.sizeHectares} ha` : ""}{a.capacity ? ` · Capacity ${a.capacity}` : ""}{a.soilType ? ` · ${a.soilType}` : ""}</p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <select value={a.status} disabled={!canManage} onChange={e => statusMut.mutate({ id: a.id, status: e.target.value })} style={{ ...statusBadge(a.status), border: "none", cursor: canManage ? "pointer" : "default" }}>
                    {AREA_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                  <AgManage>
                  <button onClick={() => setEditing(a)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
                  <button onClick={async () => { if (await agConfirm({ title: `Delete area "${a.name}"?`, confirmLabel: "Delete", danger: true })) deleteMut.mutate(a.id) }} title="Delete" style={{ ...iconBtn, color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
                  </AgManage>
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

const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer" }
