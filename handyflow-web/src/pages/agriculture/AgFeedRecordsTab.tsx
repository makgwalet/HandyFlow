// src/pages/agriculture/AgFeedRecordsTab.tsx
//
// Shared animal/group feed history — list + create only. When an
// inventory item is referenced, recording feed also issues a matching
// AgStockMovement against it server-side (confirmed via
// AgFeedRecordService) — no separate frontend action needed for that
// side-effect. inventoryItemId is a freeform UUID here since this tab
// isn't threaded the farm's inventory list; a proper picker is a
// follow-up (same simplification as AgMovementRecordsTab's area picker).
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Wheat } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, fmtMoney, type AgTargetType, targetBasePath } from "./constants"

interface FeedRecordResponse {
  id: string; animalId: string | null; groupId: string | null
  feedDate: string; inventoryItemId: string | null; feedType: string
  quantityKg: number; costPerKg: number | null; totalCost: number | null
  notes: string | null; createdAt: string
}
interface Page<T> { content: T[]; totalElements: number }

export default function AgFeedRecordsTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [feedDate, setFeedDate] = useState(new Date().toISOString().slice(0, 10))
  const [inventoryItemId, setInventoryItemId] = useState("")
  const [feedType, setFeedType] = useState("")
  const [quantityKg, setQuantityKg] = useState("")
  const [costPerKg, setCostPerKg] = useState("")

  const queryKey = ["ag-feed-records", targetType, targetId]
  const { data, isLoading } = useQuery<Page<FeedRecordResponse>>({
    queryKey,
    queryFn: async () => (await apiClient.get(`${base}/feed-records`, { params: { size: 100 } })).data,
  })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`${base}/feed-records`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      feedDate, inventoryItemId: inventoryItemId || null, feedType,
      quantityKg: Number(quantityKg), costPerKg: costPerKg ? Number(costPerKg) : null, notes: null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey })
      setShowCreate(false); setInventoryItemId(""); setFeedType(""); setQuantityKg(""); setCostPerKg("")
    },
  })

  const records = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "#64748B", margin: 0 }}>Feed given — linking an inventory item also issues stock against it.</p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record feed</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr 1fr", gap: 8, marginBottom: 10 }}>
            <div><label style={lbl}>Date</label><input type="date" value={feedDate} onChange={e => setFeedDate(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Feed type</label><input value={feedType} onChange={e => setFeedType(e.target.value)} placeholder="e.g. Broiler starter" style={inp} /></div>
            <div><label style={lbl}>Quantity (kg)</label><input type="number" min={0} step="0.1" value={quantityKg} onChange={e => setQuantityKg(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Cost/kg (R)</label><input type="number" min={0} step="0.01" value={costPerKg} onChange={e => setCostPerKg(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Inventory item ID</label><input value={inventoryItemId} onChange={e => setInventoryItemId(e.target.value)} placeholder="optional UUID" style={inp} /></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !feedType.trim() || !quantityKg} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !feedType.trim() || !quantityKg ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>Loading…</p> :
        records.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>No feed records yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Wheat size={13} color={AG_ACCENT} />
                <div>
                  <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{r.feedType} — {r.quantityKg} kg</p>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{r.feedDate}{r.totalCost != null ? ` · ${fmtMoney(r.totalCost)}` : ""}</p>
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
