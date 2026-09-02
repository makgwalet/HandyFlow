// src/pages/agriculture/AgAnimalDetail.tsx
//
// Single animal's profile plus its six history sub-tabs (shared
// components, targetType="animal") and evidence. Move/status-change/edit/
// delete all confirmed via AgAnimalController.
//
// ⚠ UNVERIFIED: UpdateAnimalRequest's exact field set wasn't seen in
// source (only that PUT /animals/{id} exists) — inferred as
// {name, breed, notes} (identity fields like tagNumber/sex/species are
// assumed immutable after registration, matching how every other
// Increment-1 entity treats its own identity fields). Adjust if the
// backend expects more.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, PawPrint, Pencil, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, fmtMoney, statusBadge } from "./constants"
import type { AnimalResponse } from "./AgAnimalsTab"
import AgWeightHistoryTab from "./AgWeightHistoryTab"
import AgHealthEventsTab from "./AgHealthEventsTab"
import AgBreedingRecordsTab from "./AgBreedingRecordsTab"
import AgMovementRecordsTab from "./AgMovementRecordsTab"
import AgMortalityRecordsTab from "./AgMortalityRecordsTab"
import AgFeedRecordsTab from "./AgFeedRecordsTab"
import AgEvidenceTab from "./AgEvidenceTab"

type SubTab = "weight" | "health" | "breeding" | "movement" | "mortality" | "feed" | "evidence"
const SUB_TABS: { key: SubTab; label: string }[] = [
  { key: "weight", label: "Weight" }, { key: "health", label: "Health" }, { key: "breeding", label: "Breeding" },
  { key: "movement", label: "Movement" }, { key: "mortality", label: "Mortality" }, { key: "feed", label: "Feed" }, { key: "evidence", label: "Evidence" },
]
const ANIMAL_STATUSES = ["ACTIVE", "SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT"]

export default function AgAnimalDetail({ animal, onBack }: { animal: AnimalResponse; onBack: () => void }) {
  const qc = useQueryClient()
  const [sub, setSub] = useState<SubTab>("weight")
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(animal.name ?? "")
  const [breed, setBreed] = useState(animal.breed ?? "")
  const [notes, setNotes] = useState(animal.notes ?? "")

  const { data: current } = useQuery<AnimalResponse>({
    queryKey: ["ag-animal", animal.id],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/animals/${animal.id}`)).data,
    initialData: animal,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-animal", animal.id] })
  const updateMut = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/agriculture/animals/${animal.id}`, { name: name || null, breed: breed || null, notes: notes || null }),
    onSuccess: () => { invalidate(); setEditing(false) },
  })
  const statusMut = useMutation({
    mutationFn: (status: string) => apiClient.patch(`/api/v1/agriculture/animals/${animal.id}/status`, { status }),
    onSuccess: invalidate,
  })
  const deleteMut = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/agriculture/animals/${animal.id}`),
    onSuccess: onBack,
  })

  const a = current ?? animal

  return (
    <div>
      <button onClick={onBack} style={backBtn}><ArrowLeft size={13} style={{ marginRight: 5, verticalAlign: -2 }} />Back to animals</button>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16, marginTop: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: AG_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <PawPrint size={19} color="#fff" />
          </div>
          <div>
            <h2 style={{ fontSize: 17, fontWeight: 800, color: "#0F172A", margin: 0 }}>{a.tagNumber}{a.name ? ` — ${a.name}` : ""}</h2>
            <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>{a.breed ?? "Breed unknown"} · {a.sex}{a.currentWeightKg ? ` · ${a.currentWeightKg} kg` : ""}{a.acquisitionCost ? ` · Acquired ${fmtMoney(a.acquisitionCost)}` : ""}</p>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <select value={a.status} onChange={e => statusMut.mutate(e.target.value)} style={{ ...statusBadge(a.status), border: "none", cursor: "pointer" }}>
            {ANIMAL_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
          <button onClick={() => setEditing(v => !v)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
          <button onClick={() => { if (confirm(`Delete animal ${a.tagNumber}?`)) deleteMut.mutate() }} title="Delete" style={{ ...iconBtn, color: "#DC2626" }}><Trash2 size={13} /></button>
        </div>
      </div>

      {editing && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 2fr", gap: 10, marginBottom: 12 }}>
            <div><label style={lbl}>Name</label><input value={name} onChange={e => setName(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Breed</label><input value={breed} onChange={e => setBreed(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Notes</label><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={updateMut.isPending} onClick={() => updateMut.mutate()} style={{ ...btnPrimary, opacity: updateMut.isPending ? 0.6 : 1 }}>Save</button>
            <button onClick={() => setEditing(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 18, overflowX: "auto" }}>
        {SUB_TABS.map(t => {
          const active = sub === t.key
          return (
            <button key={t.key} onClick={() => setSub(t.key)}
              style={{ padding: "9px 14px", border: "none", background: "none", cursor: "pointer", fontSize: 12.5, fontWeight: 600, whiteSpace: "nowrap",
                color: active ? AG_ACCENT : "#64748B", borderBottom: active ? `2px solid ${AG_ACCENT}` : "2px solid transparent", marginBottom: -1 }}>
              {t.label}
            </button>
          )
        })}
      </div>

      {sub === "weight" && <AgWeightHistoryTab targetType="animal" targetId={a.id} />}
      {sub === "health" && <AgHealthEventsTab targetType="animal" targetId={a.id} />}
      {sub === "breeding" && <AgBreedingRecordsTab targetType="animal" targetId={a.id} />}
      {sub === "movement" && <AgMovementRecordsTab targetType="animal" targetId={a.id} />}
      {sub === "mortality" && <AgMortalityRecordsTab targetType="animal" targetId={a.id} />}
      {sub === "feed" && <AgFeedRecordsTab targetType="animal" targetId={a.id} />}
      {sub === "evidence" && <AgEvidenceTab targetType="animal" targetId={a.id} />}
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }
const inp: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12.5, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "none", background: AG_ACCENT, color: "#fff", fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnGhost: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }
const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", cursor: "pointer" }
const backBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", background: "none", border: "none", color: "#64748B", fontSize: 12.5, fontWeight: 600, cursor: "pointer", padding: 0 }
