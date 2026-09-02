// src/pages/agriculture/AgWeightHistoryTab.tsx
//
// Shared between AgAnimalDetail and AgGroupDetail via the targetType prop
// — the six Livestock history sub-resources are identical in shape between
// an individually-tracked animal and a group, differing only in the
// /animals/ vs /groups/ URL prefix and which of animalId/groupId is
// populated server-side (enforced via AgTrackingTarget.requireExactlyOne).
// Confirmed via AgAnimalController/AgGroupController: POST also updates
// the animal's currentWeightKg / group's averageWeightKg server-side.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Scale } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, type AgTargetType, targetBasePath } from "./constants"

interface WeightRecordResponse {
  id: string; animalId: string | null; groupId: string | null
  recordedDate: string; weightKg: number; sampleSize: number | null
  recordedBy: string | null; recordedByName: string | null; notes: string | null; createdAt: string
}
interface Page<T> { content: T[]; totalElements: number }

export default function AgWeightHistoryTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [recordedDate, setRecordedDate] = useState(new Date().toISOString().slice(0, 10))
  const [weightKg, setWeightKg] = useState("")
  const [sampleSize, setSampleSize] = useState("")
  const [notes, setNotes] = useState("")

  const queryKey = ["ag-weight-records", targetType, targetId]
  const { data, isLoading } = useQuery<Page<WeightRecordResponse>>({
    queryKey,
    queryFn: async () => (await apiClient.get(`${base}/weight-records`, { params: { size: 100 } })).data,
  })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`${base}/weight-records`, {
      recordedDate, weightKg: Number(weightKg), sampleSize: sampleSize ? Number(sampleSize) : null,
      recordedBy: null, notes: notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey })
      setShowCreate(false); setWeightKg(""); setSampleSize(""); setNotes("")
    },
  })

  const records = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "#64748B", margin: 0 }}>
          {targetType === "group" ? "Average/sampled weight readings for this group." : "Individual weight history for this animal."}
        </p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record weight</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 2fr", gap: 8, marginBottom: 10 }}>
            <div><label style={lbl}>Date</label><input type="date" value={recordedDate} onChange={e => setRecordedDate(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Weight (kg)</label><input type="number" min={0} step="0.1" value={weightKg} onChange={e => setWeightKg(e.target.value)} style={inp} /></div>
            {targetType === "group" && <div><label style={lbl}>Sample size</label><input type="number" min={1} value={sampleSize} onChange={e => setSampleSize(e.target.value)} style={inp} /></div>}
            <div><label style={lbl}>Notes</label><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !weightKg} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !weightKg ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>Loading…</p> :
        records.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>No weight records yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Scale size={13} color={AG_ACCENT} />
                <span style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600 }}>{r.weightKg} kg</span>
                {r.sampleSize ? <span style={{ fontSize: 11, color: "#94A3B8" }}>(n={r.sampleSize})</span> : null}
              </div>
              <span style={{ fontSize: 11.5, color: "#64748B" }}>{r.recordedDate}{r.recordedByName ? ` · ${r.recordedByName}` : ""}</span>
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
