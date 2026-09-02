// src/pages/agriculture/AgMortalityRecordsTab.tsx
//
// Shared animal/group mortality history — list + create only (confirmed;
// no transition endpoints). Recording a death for an animal also sets its
// status to DECEASED server-side; for a group it decrements currentCount
// (and auto-closes the group at zero) — both confirmed via
// AgMortalityRecordService, so no separate frontend action is needed for
// that side-effect.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Skull } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, fmtMoney, type AgTargetType, targetBasePath } from "./constants"

interface MortalityRecordResponse {
  id: string; animalId: string | null; groupId: string | null
  mortalityDate: string; countLost: number; causeCategory: string; causeDetail: string | null
  estimatedValueLoss: number | null; reportedBy: string | null; reportedByName: string | null
  notes: string | null; createdAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const CAUSE_CATEGORIES = ["DISEASE", "PREDATION", "ACCIDENT", "WEATHER", "UNKNOWN", "OTHER"]

export default function AgMortalityRecordsTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [mortalityDate, setMortalityDate] = useState(new Date().toISOString().slice(0, 10))
  const [countLost, setCountLost] = useState("1")
  const [causeCategory, setCauseCategory] = useState("UNKNOWN")
  const [causeDetail, setCauseDetail] = useState("")
  const [estimatedValueLoss, setEstimatedValueLoss] = useState("")

  const queryKey = ["ag-mortality-records", targetType, targetId]
  const { data, isLoading } = useQuery<Page<MortalityRecordResponse>>({
    queryKey,
    queryFn: async () => (await apiClient.get(`${base}/mortality-records`, { params: { size: 100 } })).data,
  })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`${base}/mortality-records`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      mortalityDate, countLost: Number(countLost), causeCategory, causeDetail: causeDetail || null,
      estimatedValueLoss: estimatedValueLoss ? Number(estimatedValueLoss) : null, reportedBy: null, notes: null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey })
      qc.invalidateQueries({ queryKey: [targetType === "animal" ? "ag-animal" : "ag-group", targetId] })
      setShowCreate(false); setCountLost("1"); setCauseDetail(""); setEstimatedValueLoss("")
    },
  })

  const records = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "#64748B", margin: 0 }}>
          {targetType === "animal" ? "Recording a death sets this animal's status to DECEASED." : "Recording a death reduces this group's current count."}
        </p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record death</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
            <div><label style={lbl}>Date</label><input type="date" value={mortalityDate} onChange={e => setMortalityDate(e.target.value)} style={inp} /></div>
            {targetType === "group" && <div><label style={lbl}>Count lost</label><input type="number" min={1} value={countLost} onChange={e => setCountLost(e.target.value)} style={inp} /></div>}
            <div><label style={lbl}>Cause category</label><select value={causeCategory} onChange={e => setCauseCategory(e.target.value)} style={inp}>{CAUSE_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}</select></div>
            <div><label style={lbl}>Estimated value loss (R)</label><input type="number" min={0} step="0.01" value={estimatedValueLoss} onChange={e => setEstimatedValueLoss(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ marginBottom: 10 }}><label style={lbl}>Cause detail</label><input value={causeDetail} onChange={e => setCauseDetail(e.target.value)} style={inp} /></div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending} onClick={() => { if (confirm("Record this death? This cannot be undone from the UI.")) createMut.mutate() }}
              style={{ ...btnPrimary, opacity: createMut.isPending ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>Loading…</p> :
        records.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>No mortalities recorded.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Skull size={13} color="#DC2626" />
                <div>
                  <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{r.causeCategory}{r.countLost > 1 ? ` — ${r.countLost} lost` : ""}{r.causeDetail ? ` · ${r.causeDetail}` : ""}</p>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{r.mortalityDate}{r.estimatedValueLoss ? ` · ${fmtMoney(r.estimatedValueLoss)} loss` : ""}{r.reportedByName ? ` · ${r.reportedByName}` : ""}</p>
                </div>
              </div>
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
