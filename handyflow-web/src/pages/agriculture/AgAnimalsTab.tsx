// src/pages/agriculture/AgAnimalsTab.tsx
//
// Farm-scoped, individually-tracked animals — confirmed via
// AgAnimalController. Species must exist in the tenant catalogue first
// (AgSpeciesTab); this tab fetches the active species list for the
// create-form picker.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { PawPrint, Plus, ChevronRight } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge } from "./constants"

export interface AnimalResponse {
  id: string; farmId: string; productionAreaId: string | null; enterpriseId: string | null
  speciesId: string; tagNumber: string; name: string | null; breed: string | null
  sex: string; dateOfBirth: string | null; estimatedAge: boolean
  sireId: string | null; damId: string | null
  acquisitionType: string; acquisitionDate: string; acquisitionCost: number | null
  currentWeightKg: number | null; status: string; notes: string | null
  createdAt: string; updatedAt: string
}
interface SpeciesOption { id: string; name: string }
interface Page<T> { content: T[]; totalElements: number }

const SEXES = ["MALE", "FEMALE", "CASTRATED"]
const ACQUISITION_TYPES = ["BORN_ON_FARM", "PURCHASED", "TRANSFERRED_IN"]

export default function AgAnimalsTab({ farmId, onSelectAnimal }: { farmId: string; onSelectAnimal: (a: AnimalResponse) => void }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [statusFilter, setStatusFilter] = useState("ACTIVE")

  const [speciesId, setSpeciesId] = useState("")
  const [tagNumber, setTagNumber] = useState("")
  const [name, setName] = useState("")
  const [breed, setBreed] = useState("")
  const [sex, setSex] = useState("FEMALE")
  const [dateOfBirth, setDateOfBirth] = useState("")
  const [acquisitionType, setAcquisitionType] = useState("BORN_ON_FARM")
  const [acquisitionDate, setAcquisitionDate] = useState(new Date().toISOString().slice(0, 10))
  const [acquisitionCost, setAcquisitionCost] = useState("")

  const { data: speciesData } = useQuery<Page<SpeciesOption>>({
    queryKey: ["ag-species", "picker"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/species", { params: { size: 200 } })).data,
  })
  const { data, isLoading } = useQuery<Page<AnimalResponse>>({
    queryKey: ["ag-animals", farmId, statusFilter],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/animals`, { params: { status: statusFilter || undefined, size: 200 } })).data,
  })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/agriculture/farms/${farmId}/animals`, {
      farmId, productionAreaId: null, enterpriseId: null, speciesId, tagNumber, name: name || null,
      breed: breed || null, sex, dateOfBirth: dateOfBirth || null, estimatedAge: !dateOfBirth,
      sireId: null, damId: null, acquisitionType, acquisitionDate,
      acquisitionCost: acquisitionCost ? Number(acquisitionCost) : null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ag-animals", farmId] })
      setShowCreate(false); setTagNumber(""); setName(""); setBreed(""); setDateOfBirth(""); setAcquisitionCost("")
    },
  })

  const species = speciesData?.content ?? []
  const animals = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{animals.length} animal{animals.length === 1 ? "" : "s"}.</p>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={{ ...inp, width: 140 }}>
            <option value="">All statuses</option>
            <option value="ACTIVE">ACTIVE</option><option value="SOLD">SOLD</option>
            <option value="DECEASED">DECEASED</option><option value="CULLED">CULLED</option>
          </select>
        </div>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Register animal</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
            <div><label style={lbl}>Species</label>
              <select value={speciesId} onChange={e => setSpeciesId(e.target.value)} style={inp}>
                <option value="">Select…</option>{species.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div><label style={lbl}>Tag number</label><input value={tagNumber} onChange={e => setTagNumber(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Name (optional)</label><input value={name} onChange={e => setName(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Breed</label><input value={breed} onChange={e => setBreed(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr 1fr", gap: 10, marginBottom: 12 }}>
            <div><label style={lbl}>Sex</label><select value={sex} onChange={e => setSex(e.target.value)} style={inp}>{SEXES.map(s => <option key={s} value={s}>{s}</option>)}</select></div>
            <div><label style={lbl}>Date of birth</label><input type="date" value={dateOfBirth} onChange={e => setDateOfBirth(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Acquisition</label><select value={acquisitionType} onChange={e => setAcquisitionType(e.target.value)} style={inp}>{ACQUISITION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></div>
            <div><label style={lbl}>Acquisition date</label><input type="date" value={acquisitionDate} onChange={e => setAcquisitionDate(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Cost (R)</label><input type="number" min={0} step="0.01" value={acquisitionCost} onChange={e => setAcquisitionCost(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !speciesId || !tagNumber.trim()} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !speciesId || !tagNumber.trim() ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
        animals.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No animals registered yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {animals.map((a, i) => (
            <div key={a.id} onClick={() => onSelectAnimal(a)}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9", cursor: "pointer" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <PawPrint size={15} color={AG_ACCENT} />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{a.tagNumber}{a.name ? ` — ${a.name}` : ""}</p>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{a.breed ?? "—"} · {a.sex}{a.currentWeightKg ? ` · ${a.currentWeightKg} kg` : ""}</p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={statusBadge(a.status)}>{a.status}</span>
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
