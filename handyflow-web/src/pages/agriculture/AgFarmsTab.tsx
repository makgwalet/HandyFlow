// src/pages/agriculture/AgFarmsTab.tsx
//
// Top-level farm register — confirmed via AgFarmController
// (/api/v1/agriculture/farms). Manager assignment is validated server-side
// against HR (HrFacade.findEmployeeById) and the display name is
// snapshotted at write time, not live-joined on every read.
//
// ⚠ UNVERIFIED: no HR employee search/list endpoint was found in this
// research pass (only PayrollBureau's own client-scoped employee
// sub-resource, not a core HR module employee list). Manager assignment
// below takes a raw employee UUID rather than a proper picker — flagged
// as a follow-up once a confirmed HR employee endpoint is available.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Tractor, Plus, Pencil, Ban, RotateCcw, Trash2, UserCog, ChevronRight } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge } from "./constants"

export interface FarmResponse {
  id: string
  name: string
  farmType: string
  registrationNumber: string | null
  province: string | null
  region: string | null
  gpsLatitude: number | null
  gpsLongitude: number | null
  totalHectares: number | null
  managerId: string | null
  managerName: string | null
  status: string
  notes: string | null
  createdAt: string
  updatedAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const FARM_TYPES = ["MIXED", "LIVESTOCK", "CROP", "POULTRY", "DAIRY", "ORCHARD", "AQUACULTURE"]

function FarmForm({ initial, saving, onCancel, onSave }: {
  initial?: Partial<FarmResponse>; saving: boolean; onCancel: () => void
  onSave: (v: { name: string; farmType: string; registrationNumber: string; province: string; region: string; totalHectares: string; notes: string }) => void
}) {
  const [name, setName] = useState(initial?.name ?? "")
  const [farmType, setFarmType] = useState(initial?.farmType ?? "MIXED")
  const [registrationNumber, setRegistrationNumber] = useState(initial?.registrationNumber ?? "")
  const [province, setProvince] = useState(initial?.province ?? "")
  const [region, setRegion] = useState(initial?.region ?? "")
  const [totalHectares, setTotalHectares] = useState(initial?.totalHectares != null ? String(initial.totalHectares) : "")
  const [notes, setNotes] = useState(initial?.notes ?? "")

  return (
    <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
        <div><label style={lbl}>Farm name</label><input value={name} onChange={e => setName(e.target.value)} style={inp} /></div>
        <div>
          <label style={lbl}>Type</label>
          <select value={farmType} onChange={e => setFarmType(e.target.value)} style={inp}>
            {FARM_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <div><label style={lbl}>Registration no.</label><input value={registrationNumber} onChange={e => setRegistrationNumber(e.target.value)} style={inp} /></div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
        <div><label style={lbl}>Province</label><input value={province} onChange={e => setProvince(e.target.value)} style={inp} /></div>
        <div><label style={lbl}>Region</label><input value={region} onChange={e => setRegion(e.target.value)} style={inp} /></div>
        <div><label style={lbl}>Total hectares</label><input type="number" min={0} step="0.01" value={totalHectares} onChange={e => setTotalHectares(e.target.value)} style={inp} /></div>
      </div>
      <div style={{ marginBottom: 12 }}>
        <label style={lbl}>Notes</label>
        <textarea value={notes} onChange={e => setNotes(e.target.value)} rows={2} style={{ ...inp, resize: "vertical" }} />
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <button disabled={saving || !name.trim()} onClick={() => onSave({ name, farmType, registrationNumber, province, region, totalHectares, notes })}
          style={{ ...btnPrimary, opacity: saving || !name.trim() ? 0.6 : 1 }}>{saving ? "Saving…" : "Save"}</button>
        <button onClick={onCancel} style={btnGhost}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgFarmsTab({ onSelectFarm }: { onSelectFarm: (farm: FarmResponse) => void }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<FarmResponse | null>(null)
  const [assigningManager, setAssigningManager] = useState<FarmResponse | null>(null)
  const [managerId, setManagerId] = useState("")

  const { data, isLoading } = useQuery<Page<FarmResponse>>({
    queryKey: ["ag-farms"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/farms", { params: { size: 200 } })).data,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-farms"] })

  const createMut = useMutation({
    mutationFn: async (v: any) => apiClient.post("/api/v1/agriculture/farms", {
      name: v.name, farmType: v.farmType, registrationNumber: v.registrationNumber || null,
      province: v.province || null, region: v.region || null,
      totalHectares: v.totalHectares ? Number(v.totalHectares) : null,
    }),
    onSuccess: () => { invalidate(); setShowCreate(false) },
  })
  const updateMut = useMutation({
    mutationFn: async (v: any) => apiClient.put(`/api/v1/agriculture/farms/${editing!.id}`, {
      name: v.name, farmType: v.farmType, registrationNumber: v.registrationNumber || null,
      province: v.province || null, region: v.region || null,
      gpsLatitude: editing?.gpsLatitude ?? null, gpsLongitude: editing?.gpsLongitude ?? null,
      totalHectares: v.totalHectares ? Number(v.totalHectares) : null, notes: v.notes || null,
    }),
    onSuccess: () => { invalidate(); setEditing(null) },
  })
  const deactivateMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/farms/${id}/deactivate`), onSuccess: invalidate })
  const reactivateMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/farms/${id}/reactivate`), onSuccess: invalidate })
  const deleteMut = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/agriculture/farms/${id}`), onSuccess: invalidate })
  const assignManagerMut = useMutation({
    mutationFn: (v: { id: string; managerId: string | null }) => apiClient.patch(`/api/v1/agriculture/farms/${v.id}/manager`, { managerId: v.managerId }),
    onSuccess: () => { invalidate(); setAssigningManager(null); setManagerId("") },
  })

  const farms = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{data?.totalElements ?? 0} farm{(data?.totalElements ?? 0) === 1 ? "" : "s"} registered.</p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Register farm</button>}
      </div>

      {showCreate && <FarmForm saving={createMut.isPending} onCancel={() => setShowCreate(false)} onSave={v => createMut.mutate(v)} />}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
        farms.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No farms registered yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {farms.map((f, i) => (
            <div key={f.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div onClick={() => onSelectFarm(f)} style={{ display: "flex", alignItems: "center", gap: 10, cursor: "pointer", flex: 1 }}>
                  <Tractor size={17} color={AG_ACCENT} />
                  <div>
                    <p style={{ fontSize: 13.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{f.name}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>
                      {f.farmType} {f.province ? `· ${f.province}` : ""} {f.totalHectares ? `· ${f.totalHectares} ha` : ""} {f.managerName ? `· Manager: ${f.managerName}` : ""}
                    </p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <span style={statusBadge(f.status)}>{f.status}</span>
                  <button onClick={() => setAssigningManager(f)} title="Assign manager" style={iconBtn}><UserCog size={13} /></button>
                  <button onClick={() => setEditing(f)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
                  {f.status === "ACTIVE" ? (
                    <button onClick={() => deactivateMut.mutate(f.id)} title="Deactivate" style={iconBtn}><Ban size={13} /></button>
                  ) : (
                    <button onClick={() => reactivateMut.mutate(f.id)} title="Reactivate" style={iconBtn}><RotateCcw size={13} /></button>
                  )}
                  <button onClick={() => { if (confirm(`Delete farm "${f.name}"?`)) deleteMut.mutate(f.id) }} title="Delete" style={{ ...iconBtn, color: "#DC2626" }}><Trash2 size={13} /></button>
                  <button onClick={() => onSelectFarm(f)} title="Open" style={iconBtn}><ChevronRight size={13} /></button>
                </div>
              </div>

              {editing?.id === f.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <FarmForm initial={editing} saving={updateMut.isPending} onCancel={() => setEditing(null)} onSave={v => updateMut.mutate(v)} />
                </div>
              )}

              {assigningManager?.id === f.id && (
                <div style={{ padding: "0 16px 16px", display: "flex", gap: 8, alignItems: "flex-end" }}>
                  <div style={{ flex: 1 }}>
                    <label style={lbl}>Employee ID (HR) — leave blank to clear the manager</label>
                    <input value={managerId} onChange={e => setManagerId(e.target.value)} placeholder="employee UUID" style={inp} />
                  </div>
                  <button disabled={assignManagerMut.isPending}
                    onClick={() => assignManagerMut.mutate({ id: f.id, managerId: managerId.trim() || null })}
                    style={{ ...btnPrimary, opacity: assignManagerMut.isPending ? 0.6 : 1 }}>Assign</button>
                  <button onClick={() => { setAssigningManager(null); setManagerId("") }} style={btnGhost}>Cancel</button>
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
