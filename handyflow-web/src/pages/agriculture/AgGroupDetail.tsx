// src/pages/agriculture/AgGroupDetail.tsx
//
// Single group's profile plus its six shared history sub-tabs
// (targetType="group") and evidence. Edit (update) and count/close/reopen
// actions confirmed via AgGroupService (updateGroup/reduceCount/
// increaseCount/close/reopen all exist there) —
//
// ⚠ UNVERIFIED PATHS: the exact controller route mappings for
// move/reduce-count/increase-count/close/reopen were not directly seen in
// source (AgGroupController's fragment read stopped before that section);
// the paths below (PATCH /groups/{id}/move|reduce-count|increase-count|
// close|reopen) are inferred from this module's own naming convention
// used everywhere else (farms' /deactivate·/reactivate, health-events'
// /complete·/acknowledge). Adjust the path strings below if these 404.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, Users, Pencil, Lock, Unlock, MinusCircle, PlusCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge } from "./constants"
import type { GroupResponse } from "./AgGroupsTab"
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

export default function AgGroupDetail({ group, onBack }: { group: GroupResponse; onBack: () => void }) {
  const qc = useQueryClient()
  const [sub, setSub] = useState<SubTab>("weight")
  const [editing, setEditing] = useState(false)
  const [breed, setBreed] = useState(group.breed ?? "")
  const [notes, setNotes] = useState(group.notes ?? "")
  const [adjusting, setAdjusting] = useState<"increase" | "decrease" | null>(null)
  const [adjustCount, setAdjustCount] = useState("")

  const { data: current } = useQuery<GroupResponse>({
    queryKey: ["ag-group", group.id],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/groups/${group.id}`)).data,
    initialData: group,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-group", group.id] })

  const updateMut = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/agriculture/groups/${group.id}`, { productionAreaId: null, enterpriseId: null, breed: breed || null, notes: notes || null }),
    onSuccess: () => { invalidate(); setEditing(false) },
  })
  const closeMut = useMutation({ mutationFn: () => apiClient.patch(`/api/v1/agriculture/groups/${group.id}/close`), onSuccess: invalidate })
  const reopenMut = useMutation({ mutationFn: () => apiClient.patch(`/api/v1/agriculture/groups/${group.id}/reopen`), onSuccess: invalidate })
  const adjustMut = useMutation({
    mutationFn: () => apiClient.patch(`/api/v1/agriculture/groups/${group.id}/${adjusting === "increase" ? "increase-count" : "reduce-count"}`, { count: Number(adjustCount) }),
    onSuccess: () => { invalidate(); setAdjusting(null); setAdjustCount("") },
  })

  const g = current ?? group

  return (
    <div>
      <button onClick={onBack} style={backBtn}><ArrowLeft size={13} style={{ marginRight: 5, verticalAlign: -2 }} />Back to groups</button>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16, marginTop: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: AG_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Users size={19} color="#fff" />
          </div>
          <div>
            <h2 style={{ fontSize: 17, fontWeight: 800, color: "#0F172A", margin: 0 }}>{g.batchNumber}</h2>
            <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>{g.breed ?? "Breed unknown"} · {g.currentCount}/{g.initialCount} head{g.averageWeightKg ? ` · avg ${g.averageWeightKg} kg` : ""}</p>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={statusBadge(g.status)}>{g.status}</span>
          <button onClick={() => setAdjusting("increase")} title="Increase count" style={iconBtn}><PlusCircle size={13} /></button>
          <button onClick={() => setAdjusting("decrease")} title="Reduce count" style={iconBtn}><MinusCircle size={13} /></button>
          {g.status === "ACTIVE" ? (
            <button onClick={() => closeMut.mutate()} title="Close group" style={iconBtn}><Lock size={13} /></button>
          ) : (
            <button onClick={() => reopenMut.mutate()} title="Reopen group" style={iconBtn}><Unlock size={13} /></button>
          )}
          <button onClick={() => setEditing(v => !v)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
        </div>
      </div>

      {adjusting && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 14, marginBottom: 16, display: "flex", gap: 8, alignItems: "flex-end" }}>
          <div>
            <label style={lbl}>{adjusting === "increase" ? "Increase count by" : "Reduce count by"}</label>
            <input type="number" min={1} value={adjustCount} onChange={e => setAdjustCount(e.target.value)} style={{ ...inp, width: 120 }} />
          </div>
          <button disabled={adjustMut.isPending || !adjustCount} onClick={() => adjustMut.mutate()} style={{ ...btnPrimary, opacity: adjustMut.isPending || !adjustCount ? 0.6 : 1 }}>Apply</button>
          <button onClick={() => { setAdjusting(null); setAdjustCount("") }} style={btnGhost}>Cancel</button>
        </div>
      )}

      {editing && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 10, marginBottom: 12 }}>
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

      {sub === "weight" && <AgWeightHistoryTab targetType="group" targetId={g.id} />}
      {sub === "health" && <AgHealthEventsTab targetType="group" targetId={g.id} />}
      {sub === "breeding" && <AgBreedingRecordsTab targetType="group" targetId={g.id} />}
      {sub === "movement" && <AgMovementRecordsTab targetType="group" targetId={g.id} />}
      {sub === "mortality" && <AgMortalityRecordsTab targetType="group" targetId={g.id} />}
      {sub === "feed" && <AgFeedRecordsTab targetType="group" targetId={g.id} />}
      {sub === "evidence" && <AgEvidenceTab targetType="group" targetId={g.id} />}
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }
const inp: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12.5, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "none", background: AG_ACCENT, color: "#fff", fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnGhost: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }
const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", cursor: "pointer" }
const backBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", background: "none", border: "none", color: "#64748B", fontSize: 12.5, fontWeight: 600, cursor: "pointer", padding: 0 }
