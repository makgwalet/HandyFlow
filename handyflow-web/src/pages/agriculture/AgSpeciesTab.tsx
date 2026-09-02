// src/pages/agriculture/AgSpeciesTab.tsx
//
// Tenant-wide species catalogue — confirmed via AgSpeciesController (base
// path /api/v1/agriculture/species, inferred from every sibling
// controller's own @RequestMapping convention — the class-level annotation
// itself was truncated in the source read, so this ONE base path is
// UNVERIFIED; every method mapping under it (POST/PUT/{id}/deactivate/
// {id}/reactivate/DELETE) is directly confirmed).
//
// No separate AgBreed catalogue entity exists — breed is a free-text field
// directly on AgAnimal/AgGroup (see AgSpecies's own Javadoc: breed names
// vary too freely across species/regions for a pre-populated table to earn
// its keep in Increment 1).
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { PawPrint, Plus, Pencil, Ban, RotateCcw, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge } from "./constants"

export interface SpeciesResponse {
  id: string
  name: string
  category: string
  defaultUnitOfMeasure: string
  trackingMode: "INDIVIDUAL" | "GROUP" | "BOTH"
  gestationDays: number | null
  maturityWeightKg: number | null
  status: string
  createdAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const CATEGORIES = ["LIVESTOCK", "POULTRY", "AQUACULTURE"]
const TRACKING_MODES = ["INDIVIDUAL", "GROUP", "BOTH"]

function SpeciesForm({ initial, onCancel, onSave, saving }: {
  initial?: Partial<SpeciesResponse>; onCancel: () => void; saving: boolean
  onSave: (v: { name: string; category: string; defaultUnitOfMeasure: string; trackingMode: string; gestationDays: string; maturityWeightKg: string }) => void
}) {
  const [name, setName] = useState(initial?.name ?? "")
  const [category, setCategory] = useState(initial?.category ?? "LIVESTOCK")
  const [uom, setUom] = useState(initial?.defaultUnitOfMeasure ?? "head")
  const [trackingMode, setTrackingMode] = useState(initial?.trackingMode ?? "BOTH")
  const [gestationDays, setGestationDays] = useState(initial?.gestationDays != null ? String(initial.gestationDays) : "")
  const [maturityWeightKg, setMaturityWeightKg] = useState(initial?.maturityWeightKg != null ? String(initial.maturityWeightKg) : "")

  return (
    <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
        <div>
          <label style={lbl}>Name</label>
          <input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Cattle" style={inp} />
        </div>
        {!initial?.id && (
          <div>
            <label style={lbl}>Category</label>
            <select value={category} onChange={e => setCategory(e.target.value)} style={inp}>
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
        )}
        <div>
          <label style={lbl}>Default unit</label>
          <input value={uom} onChange={e => setUom(e.target.value)} placeholder="head / kg / crate" style={inp} />
        </div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 12 }}>
        <div>
          <label style={lbl}>Tracking mode</label>
          <select value={trackingMode} onChange={e => setTrackingMode(e.target.value)} style={inp}>
            {TRACKING_MODES.map(m => <option key={m} value={m}>{m}</option>)}
          </select>
        </div>
        <div>
          <label style={lbl}>Gestation (days)</label>
          <input type="number" min={0} value={gestationDays} onChange={e => setGestationDays(e.target.value)} style={inp} />
        </div>
        <div>
          <label style={lbl}>Maturity weight (kg)</label>
          <input type="number" min={0} step="0.1" value={maturityWeightKg} onChange={e => setMaturityWeightKg(e.target.value)} style={inp} />
        </div>
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <button disabled={saving || !name.trim()}
          onClick={() => onSave({ name, category, defaultUnitOfMeasure: uom, trackingMode, gestationDays, maturityWeightKg })}
          style={{ ...btnPrimary, opacity: saving || !name.trim() ? 0.6 : 1 }}>
          {saving ? "Saving…" : "Save"}
        </button>
        <button onClick={onCancel} style={btnGhost}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgSpeciesTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<SpeciesResponse | null>(null)

  const { data, isLoading } = useQuery<Page<SpeciesResponse>>({
    queryKey: ["ag-species"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/species", { params: { size: 200 } })).data,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-species"] })

  const createMut = useMutation({
    mutationFn: async (v: any) => apiClient.post("/api/v1/agriculture/species", {
      name: v.name, category: v.category, defaultUnitOfMeasure: v.defaultUnitOfMeasure || "head",
      trackingMode: v.trackingMode, gestationDays: v.gestationDays ? Number(v.gestationDays) : null,
      maturityWeightKg: v.maturityWeightKg ? Number(v.maturityWeightKg) : null,
    }),
    onSuccess: () => { invalidate(); setShowCreate(false) },
  })
  const updateMut = useMutation({
    mutationFn: async (v: any) => apiClient.put(`/api/v1/agriculture/species/${editing!.id}`, {
      name: v.name, defaultUnitOfMeasure: v.defaultUnitOfMeasure || "head", trackingMode: v.trackingMode,
      gestationDays: v.gestationDays ? Number(v.gestationDays) : null,
      maturityWeightKg: v.maturityWeightKg ? Number(v.maturityWeightKg) : null,
    }),
    onSuccess: () => { invalidate(); setEditing(null) },
  })
  const deactivateMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/species/${id}/deactivate`), onSuccess: invalidate })
  const reactivateMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/species/${id}/reactivate`), onSuccess: invalidate })
  const deleteMut = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/agriculture/species/${id}`), onSuccess: invalidate })

  const species = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>Tenant-wide catalogue used when registering animals and groups on any farm.</p>
        {!showCreate && (
          <button onClick={() => setShowCreate(true)} style={btnPrimary}>
            <Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Add species
          </button>
        )}
      </div>

      {showCreate && (
        <SpeciesForm saving={createMut.isPending} onCancel={() => setShowCreate(false)} onSave={v => createMut.mutate(v)} />
      )}

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : species.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No species in the catalogue yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {species.map((s, i) => (
            <div key={s.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <PawPrint size={16} color={AG_ACCENT} />
                  <div>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{s.name}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{s.category} · {s.defaultUnitOfMeasure} · Tracking: {s.trackingMode}{s.gestationDays ? ` · Gestation ${s.gestationDays}d` : ""}</p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <span style={statusBadge(s.status)}>{s.status}</span>
                  <button onClick={() => setEditing(s)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
                  {s.status === "ACTIVE" ? (
                    <button onClick={() => deactivateMut.mutate(s.id)} title="Deactivate" style={iconBtn}><Ban size={13} /></button>
                  ) : (
                    <button onClick={() => reactivateMut.mutate(s.id)} title="Reactivate" style={iconBtn}><RotateCcw size={13} /></button>
                  )}
                  <button onClick={() => { if (confirm(`Delete species "${s.name}"?`)) deleteMut.mutate(s.id) }} title="Delete" style={{ ...iconBtn, color: "#DC2626" }}><Trash2 size={13} /></button>
                </div>
              </div>
              {editing?.id === s.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <SpeciesForm initial={editing} saving={updateMut.isPending} onCancel={() => setEditing(null)} onSave={v => updateMut.mutate(v)} />
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
