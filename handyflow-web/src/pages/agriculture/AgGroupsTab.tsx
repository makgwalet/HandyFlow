// src/pages/agriculture/AgGroupsTab.tsx
//
// Farm-scoped batch/flock/herd groups — confirmed via AgGroupController.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Users, Plus, ChevronRight } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge } from "./constants"

export interface GroupResponse {
  id: string; farmId: string; productionAreaId: string | null; enterpriseId: string | null
  speciesId: string; batchNumber: string; breed: string | null
  initialCount: number; currentCount: number; averageWeightKg: number | null
  originDate: string; acquisitionType: string; status: string; notes: string | null
  createdAt: string; updatedAt: string
}
interface SpeciesOption { id: string; name: string }
interface Page<T> { content: T[]; totalElements: number }

const ACQUISITION_TYPES = ["BORN_ON_FARM", "PURCHASED", "TRANSFERRED_IN"]

export default function AgGroupsTab({ farmId, onSelectGroup }: { farmId: string; onSelectGroup: (g: GroupResponse) => void }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [statusFilter, setStatusFilter] = useState("ACTIVE")

  const [speciesId, setSpeciesId] = useState("")
  const [batchNumber, setBatchNumber] = useState("")
  const [breed, setBreed] = useState("")
  const [initialCount, setInitialCount] = useState("")
  const [originDate, setOriginDate] = useState(new Date().toISOString().slice(0, 10))
  const [acquisitionType, setAcquisitionType] = useState("BORN_ON_FARM")

  const { data: speciesData } = useQuery<Page<SpeciesOption>>({
    queryKey: ["ag-species", "picker"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/species", { params: { size: 200 } })).data,
  })
  const { data, isLoading } = useQuery<Page<GroupResponse>>({
    queryKey: ["ag-groups", farmId, statusFilter],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/groups`, { params: { status: statusFilter || undefined, size: 200 } })).data,
  })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/agriculture/farms/${farmId}/groups`, {
      farmId, productionAreaId: null, enterpriseId: null, speciesId, batchNumber, breed: breed || null,
      initialCount: Number(initialCount), originDate, acquisitionType,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ag-groups", farmId] })
      setShowCreate(false); setBatchNumber(""); setBreed(""); setInitialCount("")
    },
  })

  const species = speciesData?.content ?? []
  const groups = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{groups.length} group{groups.length === 1 ? "" : "s"}.</p>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={{ ...inp, width: 140 }}>
            <option value="">All statuses</option><option value="ACTIVE">ACTIVE</option><option value="CLOSED">CLOSED</option>
          </select>
        </div>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Register group</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
            <div><label style={lbl}>Species</label>
              <select value={speciesId} onChange={e => setSpeciesId(e.target.value)} style={inp}>
                <option value="">Select…</option>{species.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div><label style={lbl}>Batch number</label><input value={batchNumber} onChange={e => setBatchNumber(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Breed</label><input value={breed} onChange={e => setBreed(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 12 }}>
            <div><label style={lbl}>Initial count</label><input type="number" min={1} value={initialCount} onChange={e => setInitialCount(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Origin date</label><input type="date" value={originDate} onChange={e => setOriginDate(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Acquisition</label><select value={acquisitionType} onChange={e => setAcquisitionType(e.target.value)} style={inp}>{ACQUISITION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !speciesId || !batchNumber.trim() || !initialCount} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !speciesId || !batchNumber.trim() || !initialCount ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
        groups.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No groups registered yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {groups.map((g, i) => (
            <div key={g.id} onClick={() => onSelectGroup(g)}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9", cursor: "pointer" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <Users size={15} color={AG_ACCENT} />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{g.batchNumber}</p>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{g.breed ?? "—"} · {g.currentCount}/{g.initialCount} head{g.averageWeightKg ? ` · avg ${g.averageWeightKg} kg` : ""}</p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={statusBadge(g.status)}>{g.status}</span>
                <ChevronRight size={14} color="#94A3B8" />
              </div>
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
