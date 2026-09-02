// src/pages/agriculture/AgBreedingRecordsTab.tsx
//
// Shared animal/group breeding history — full lifecycle confirmed via
// AgAnimalController/AgBreedingRecordService: create/list/get, then
// PATCH .../confirm-pregnant, .../not-pregnant, .../record-birth,
// .../aborted, .../failed. All PATCH endpoints are addressed by the
// record's own id alone (not nested under /animals/ or /groups/) — see
// AgAnimalController's own class Javadoc for why.
//
// ⚠ UNVERIFIED: RecordBirthRequest's exact field names/order weren't seen
// in source (only that recordBirth(recordId, request) exists) — inferred
// as {actualBirthDate, outcome, offspringCount} from BreedingRecordResponse's
// own matching fields. Non-critical UI form contract; adjust field names
// here if the backend rejects this shape.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Baby, Check, X as XIcon, Ban } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge, type AgTargetType, targetBasePath } from "./constants"

interface BreedingRecordResponse {
  id: string; animalId: string | null; groupId: string | null
  breedingType: string; matingDate: string; sireId: string | null; sireDescription: string | null
  expectedDueDate: string | null; actualBirthDate: string | null
  outcome: string | null; offspringCount: number | null
  notes: string | null; createdAt: string; updatedAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const BREEDING_TYPES = ["NATURAL", "ARTIFICIAL_INSEMINATION", "EMBRYO_TRANSFER"]

export default function AgBreedingRecordsTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [breedingType, setBreedingType] = useState("NATURAL")
  const [matingDate, setMatingDate] = useState(new Date().toISOString().slice(0, 10))
  const [sireDescription, setSireDescription] = useState("")
  const [expectedDueDate, setExpectedDueDate] = useState("")

  const [birthing, setBirthing] = useState<BreedingRecordResponse | null>(null)
  const [actualBirthDate, setActualBirthDate] = useState(new Date().toISOString().slice(0, 10))
  const [outcome, setOutcome] = useState("LIVE_BIRTH")
  const [offspringCount, setOffspringCount] = useState("1")

  const queryKey = ["ag-breeding-records", targetType, targetId]
  const { data, isLoading } = useQuery<Page<BreedingRecordResponse>>({
    queryKey,
    queryFn: async () => (await apiClient.get(`${base}/breeding-records`, { params: { size: 100 } })).data,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`${base}/breeding-records`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      breedingType, matingDate, sireId: null, sireDescription: sireDescription || null,
      expectedDueDate: expectedDueDate || null, notes: null,
    }),
    onSuccess: () => { invalidate(); setShowCreate(false); setSireDescription(""); setExpectedDueDate("") },
  })
  const confirmPregnantMut = useMutation({
    mutationFn: (v: { id: string; expectedDueDate: string | null }) =>
      apiClient.patch(`/api/v1/agriculture/breeding-records/${v.id}/confirm-pregnant`, { expectedDueDate: v.expectedDueDate }),
    onSuccess: invalidate,
  })
  const notPregnantMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/breeding-records/${id}/not-pregnant`), onSuccess: invalidate })
  const abortedMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/breeding-records/${id}/aborted`), onSuccess: invalidate })
  const failedMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/breeding-records/${id}/failed`), onSuccess: invalidate })
  const recordBirthMut = useMutation({
    mutationFn: () => apiClient.patch(`/api/v1/agriculture/breeding-records/${birthing!.id}/record-birth`, {
      actualBirthDate, outcome, offspringCount: Number(offspringCount),
    }),
    onSuccess: () => { invalidate(); setBirthing(null) },
  })

  const records = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "#64748B", margin: 0 }}>Mating → pregnancy confirmation → birth outcome.</p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record mating</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 8, marginBottom: 10 }}>
            <div><label style={lbl}>Type</label><select value={breedingType} onChange={e => setBreedingType(e.target.value)} style={inp}>{BREEDING_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></div>
            <div><label style={lbl}>Mating date</label><input type="date" value={matingDate} onChange={e => setMatingDate(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Sire</label><input value={sireDescription} onChange={e => setSireDescription(e.target.value)} placeholder="description or tag" style={inp} /></div>
            <div><label style={lbl}>Expected due date</label><input type="date" value={expectedDueDate} onChange={e => setExpectedDueDate(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending} onClick={() => createMut.mutate()} style={{ ...btnPrimary, opacity: createMut.isPending ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>Loading…</p> :
        records.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>No breeding records yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <Baby size={13} color={AG_ACCENT} />
                  <div>
                    <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{r.breedingType.replace(/_/g, " ")}{r.sireDescription ? ` · ${r.sireDescription}` : ""}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>
                      Mated {r.matingDate}{r.expectedDueDate ? ` · Due ${r.expectedDueDate}` : ""}
                      {r.actualBirthDate ? ` · Born ${r.actualBirthDate} (${r.offspringCount ?? "?"})` : ""}
                    </p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <span style={statusBadge(r.outcome ?? "PREGNANT_UNCONFIRMED")}>{(r.outcome ?? "UNCONFIRMED").replace(/_/g, " ")}</span>
                  {!r.actualBirthDate && !r.outcome && (
                    <>
                      <button onClick={() => confirmPregnantMut.mutate({ id: r.id, expectedDueDate: r.expectedDueDate })} title="Confirm pregnant" style={iconBtn}><Check size={12} /></button>
                      <button onClick={() => notPregnantMut.mutate(r.id)} title="Not pregnant" style={iconBtn}><XIcon size={12} /></button>
                    </>
                  )}
                  {!r.actualBirthDate && (
                    <>
                      <button onClick={() => { setBirthing(r); setOutcome("LIVE_BIRTH") }} title="Record birth" style={iconBtn}><Baby size={12} /></button>
                      <button onClick={() => abortedMut.mutate(r.id)} title="Mark aborted" style={iconBtn}><Ban size={12} /></button>
                      <button onClick={() => failedMut.mutate(r.id)} title="Mark failed" style={{ ...iconBtn, color: "#DC2626" }}><XIcon size={12} /></button>
                    </>
                  )}
                </div>
              </div>
              {birthing?.id === r.id && (
                <div style={{ padding: "0 14px 14px", display: "flex", gap: 8, alignItems: "flex-end" }}>
                  <div><label style={lbl}>Birth date</label><input type="date" value={actualBirthDate} onChange={e => setActualBirthDate(e.target.value)} style={inp} /></div>
                  <div><label style={lbl}>Outcome</label>
                    <select value={outcome} onChange={e => setOutcome(e.target.value)} style={inp}>
                      <option value="LIVE_BIRTH">LIVE_BIRTH</option><option value="STILLBIRTH">STILLBIRTH</option>
                    </select>
                  </div>
                  <div><label style={lbl}>Offspring count</label><input type="number" min={0} value={offspringCount} onChange={e => setOffspringCount(e.target.value)} style={{ ...inp, width: 90 }} /></div>
                  <button disabled={recordBirthMut.isPending} onClick={() => recordBirthMut.mutate()} style={{ ...btnPrimary, opacity: recordBirthMut.isPending ? 0.6 : 1 }}>Save</button>
                  <button onClick={() => setBirthing(null)} style={btnGhost}>Cancel</button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 10.5, fontWeight: 600, color: "#374151", marginBottom: 3, display: "block" }
const inp: React.CSSProperties = { width: "100%", padding: "7px 9px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 12, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { display: "inline-flex", alignItems: "center", padding: "7px 12px", borderRadius: 7, border: "none", background: AG_ACCENT, color: "#fff", fontSize: 12, fontWeight: 700, cursor: "pointer" }
const btnGhost: React.CSSProperties = { padding: "7px 12px", borderRadius: 7, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 24, height: 24, borderRadius: 6, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", cursor: "pointer" }
