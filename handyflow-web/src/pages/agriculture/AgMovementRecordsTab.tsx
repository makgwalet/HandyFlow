// src/pages/agriculture/AgMovementRecordsTab.tsx
//
// Shared animal/group movement history — a pure append-only log (list +
// create only, confirmed via AgAnimalController/AgMovementRecordService;
// no transition endpoints exist for movement records). Production-area
// and farm UUIDs are freeform text here rather than pickers — this tab
// has no farm-scoped area list passed down to it; wiring a proper picker
// is a follow-up once the parent detail pages thread that list through.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, ArrowRightLeft } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, type AgTargetType, targetBasePath } from "./constants"

interface MovementRecordResponse {
  id: string; animalId: string | null; groupId: string | null
  movementDate: string; movementType: string
  fromProductionAreaId: string | null; toProductionAreaId: string | null
  fromFarmId: string | null; toFarmId: string | null
  countMoved: number | null; reason: string | null; notes: string | null; createdAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const MOVEMENT_TYPES = ["AREA_TRANSFER", "FARM_TRANSFER", "SALE", "TRANSFER_OUT"]

export default function AgMovementRecordsTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [movementDate, setMovementDate] = useState(new Date().toISOString().slice(0, 10))
  const [movementType, setMovementType] = useState("AREA_TRANSFER")
  const [toProductionAreaId, setToProductionAreaId] = useState("")
  const [countMoved, setCountMoved] = useState("")
  const [reason, setReason] = useState("")

  const queryKey = ["ag-movement-records", targetType, targetId]
  const { data, isLoading } = useQuery<Page<MovementRecordResponse>>({
    queryKey,
    queryFn: async () => (await apiClient.get(`${base}/movement-records`, { params: { size: 100 } })).data,
  })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`${base}/movement-records`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      movementDate, movementType, fromProductionAreaId: null,
      toProductionAreaId: toProductionAreaId || null, fromFarmId: null, toFarmId: null,
      countMoved: countMoved ? Number(countMoved) : null, reason: reason || null, notes: null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey })
      setShowCreate(false); setToProductionAreaId(""); setCountMoved(""); setReason("")
    },
  })

  const records = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "#64748B", margin: 0 }}>Transfers between production areas, farms, sales and other movements out.</p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record movement</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
            <div><label style={lbl}>Date</label><input type="date" value={movementDate} onChange={e => setMovementDate(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Type</label><select value={movementType} onChange={e => setMovementType(e.target.value)} style={inp}>{MOVEMENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></div>
            <div><label style={lbl}>To production area ID</label><input value={toProductionAreaId} onChange={e => setToProductionAreaId(e.target.value)} placeholder="optional UUID" style={inp} /></div>
            {targetType === "group" && <div><label style={lbl}>Count moved</label><input type="number" min={1} value={countMoved} onChange={e => setCountMoved(e.target.value)} style={inp} /></div>}
          </div>
          <div style={{ marginBottom: 10 }}><label style={lbl}>Reason</label><input value={reason} onChange={e => setReason(e.target.value)} style={inp} /></div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending} onClick={() => createMut.mutate()} style={{ ...btnPrimary, opacity: createMut.isPending ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>Loading…</p> :
        records.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>No movements recorded yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <ArrowRightLeft size={13} color={AG_ACCENT} />
                <div>
                  <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{r.movementType.replace(/_/g, " ")}{r.countMoved ? ` — ${r.countMoved} moved` : ""}</p>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{r.movementDate}{r.reason ? ` · ${r.reason}` : ""}</p>
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
