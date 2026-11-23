// src/pages/agriculture/AgGroupDetail.tsx
//
// Single group's profile plus its six shared history sub-tabs
// (targetType="group") and evidence. Edit (update) and count/close/reopen
// actions confirmed via AgGroupService (updateGroup/reduceCount/
// increaseCount/close/reopen all exist there) —
//
// Every head-count change asks for a reason and is kept in the group's count history (GET /groups/{id}/count-log).
import type React from "react"
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { ArrowLeft, Users, Pencil, Lock, Unlock, MinusCircle, PlusCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge, inp, btnPrimary, btnGhost } from "./constants"
import { Field } from "./agUi"
import AgProduceNotice from "./AgProduceNotice"
import { AgTabs } from "./agUi"
import type { GroupResponse } from "./AgGroupsTab"
import AgWeightHistoryTab from "./AgWeightHistoryTab"
import AgHealthEventsTab from "./AgHealthEventsTab"
import AgBreedingRecordsTab from "./AgBreedingRecordsTab"
import AgMovementRecordsTab from "./AgMovementRecordsTab"
import AgMortalityRecordsTab from "./AgMortalityRecordsTab"
import AgFeedRecordsTab from "./AgFeedRecordsTab"
import AgEvidenceTab from "./AgEvidenceTab"
import { buildTextPatch } from "./agPatch"
import AgManage from "./AgManage"
import { breedSuggestions } from "./agLookups"
import { useSpeciesNames } from "./agLookups.api"
import { countAdjustError, countLogLabel } from "./agLifecycle.logic"

interface CountLogEntry { id: string; changeType: string; delta: number; countBefore: number; countAfter: number; reason: string; createdByName: string | null; createdAt: string }

type SubTab = "weight" | "health" | "breeding" | "movement" | "mortality" | "feed" | "evidence"
const SUB_TABS: { key: SubTab; label: string }[] = [
  { key: "weight", label: "Weight" }, { key: "health", label: "Health" }, { key: "breeding", label: "Breeding" },
  { key: "movement", label: "Movement" }, { key: "mortality", label: "Mortality" }, { key: "feed", label: "Feed" }, { key: "evidence", label: "Evidence" },
]

export default function AgGroupDetail({ group, onBack }: { group: GroupResponse; onBack: () => void }) {
  const qc = useQueryClient()
  const speciesNames = useSpeciesNames()
  const [sub, setSub] = useState<SubTab>("weight")
  const [editing, setEditing] = useState(false)
  const [breed, setBreed] = useState(group.breed ?? "")
  const [notes, setNotes] = useState(group.notes ?? "")
  const [adjusting, setAdjusting] = useState<"increase" | "decrease" | null>(null)
  const [adjustCount, setAdjustCount] = useState("")
  const [adjustReason, setAdjustReason] = useState("")
  const [adjustError, setAdjustError] = useState("")

  const { data: current } = useQuery<GroupResponse>({
    queryKey: ["ag-group", group.id],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/groups/${group.id}`)).data,
    initialData: group,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-group", group.id] })

  const updateMut = useAgToastMutation({
    mutationFn: async () => {
      const patch = buildTextPatch({ breed: current?.breed, notes: current?.notes }, { breed, notes })
      if (patch) await apiClient.put(`/api/v1/agriculture/groups/${group.id}`, patch)
    },
    onSuccess: () => { invalidate(); setEditing(false) },
  })
  const closeMut = useAgToastMutation({ mutationFn: () => apiClient.patch(`/api/v1/agriculture/groups/${group.id}/close`), onSuccess: invalidate })
  const reopenMut = useAgToastMutation({ mutationFn: () => apiClient.patch(`/api/v1/agriculture/groups/${group.id}/reopen`), onSuccess: invalidate })
  const adjustMut = useAgToastMutation({
    mutationFn: () => apiClient.patch(`/api/v1/agriculture/groups/${group.id}/${adjusting === "increase" ? "increase-count" : "reduce-count"}`, { count: Number(adjustCount), reason: adjustReason.trim() }),
    onSuccess: () => { invalidate(); qc.invalidateQueries({ queryKey: ["ag-group-count-log", group.id] }); qc.invalidateQueries({ queryKey: ["ag-groups"] }); setAdjusting(null); setAdjustCount(""); setAdjustReason(""); setAdjustError("") },
  })

  const g = current ?? group
  const { data: countLog = [] } = useQuery<CountLogEntry[]>({
    queryKey: ["ag-group-count-log", group.id],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/groups/${group.id}/count-log`, { params: { size: 20 } })).data.content ?? [],
  })
  const applyAdjust = () => {
    const problem = countAdjustError(adjusting ?? "increase", adjustCount, adjustReason, g.currentCount)
    if (problem) { setAdjustError(problem); return }
    setAdjustError(""); adjustMut.mutate()
  }

  return (
    <div>
      <button onClick={onBack} style={backBtn}><ArrowLeft size={13} style={{ marginRight: 5, verticalAlign: -2 }} />Back to groups</button>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16, marginTop: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: AG_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Users size={19} style={{ color: 'var(--hf-text-on-solid)' }} />
          </div>
          <div>
            <h2 style={{ fontSize: 17, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{g.batchNumber}</h2>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>{g.breed ?? "Breed unknown"} · {g.currentCount}/{g.initialCount} head{g.averageWeightKg ? ` · avg ${g.averageWeightKg} kg` : ""}</p>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={statusBadge(g.status)}>{g.status}</span>
          <AgManage>
          <button onClick={() => setAdjusting("increase")} title="Increase count" style={iconBtn}><PlusCircle size={13} /></button>
          <button onClick={() => setAdjusting("decrease")} title="Reduce count" style={iconBtn}><MinusCircle size={13} /></button>
          {g.status === "ACTIVE" ? (
            <button onClick={() => closeMut.mutate()} title="Close group" style={iconBtn}><Lock size={13} /></button>
          ) : (
            <button onClick={() => reopenMut.mutate()} title="Reopen group" style={iconBtn}><Unlock size={13} /></button>
          )}
          <button onClick={() => setEditing(v => !v)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
          </AgManage>
        </div>
      </div>

      {adjusting && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 16 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginBottom: 8 }}>
            <Field label={adjusting === "increase" ? "Increase count by" : "Reduce count by"}><input type="number" min={1} value={adjustCount} onChange={e => setAdjustCount(e.target.value)} style={{ ...inp, width: 120 }} /></Field>
            <div style={{ flex: 1 }}>
              <Field label="Reason (required)"><input value={adjustReason} onChange={e => setAdjustReason(e.target.value)} placeholder={adjusting === "increase" ? "e.g. Recount found 4 more" : "e.g. Recount, 3 missing"} style={inp} /></Field>
            </div>
          </div>
          <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>For sales, transfers out and deaths use the Movement and Mortality tabs, so they are recorded properly.</p>
          {adjustError && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "0 0 8px" }}>{adjustError}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={adjustMut.isPending} onClick={applyAdjust} style={{ ...btnPrimary, opacity: adjustMut.isPending ? 0.6 : 1 }}>Apply</button>
            <button onClick={() => { setAdjusting(null); setAdjustCount(""); setAdjustReason(""); setAdjustError("") }} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {editing && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 12 }}>
            <><Field label="Breed"><input list="ag-breed-options" value={breed} onChange={e => setBreed(e.target.value)} style={inp} /></Field><datalist id="ag-breed-options">{breedSuggestions(speciesNames[g.speciesId]).map(b => <option key={b} value={b} />)}</datalist></>
            <Field label="Notes"><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={updateMut.isPending} onClick={() => updateMut.mutate()} style={{ ...btnPrimary, opacity: updateMut.isPending ? 0.6 : 1 }}>Save</button>
            <button onClick={() => setEditing(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {countLog.length > 0 && (
        <details style={{ marginBottom: 16 }}>
          <summary style={{ fontSize: 12.5, fontWeight: 600, color: "var(--hf-text-muted)", cursor: "pointer" }}>Head-count history ({countLog.length})</summary>
          <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden", marginTop: 8 }}>
            {countLog.map((l, i) => (
              <div key={l.id} style={{ padding: "8px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <p style={{ fontSize: 12.5, color: "var(--hf-text)", fontWeight: 600, margin: 0 }}>{countLogLabel(l)}</p>
                <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{l.createdAt.slice(0, 10)}{l.createdByName ? ` · ${l.createdByName}` : ""} · {l.reason}</p>
              </div>
            ))}
          </div>
        </details>
      )}

      <AgProduceNotice kind="group" id={group.id} />
      <AgTabs label="Group records" tabs={SUB_TABS} active={sub} onChange={setSub} size="sm" spacing={18} />

      {sub === "weight" && <AgWeightHistoryTab targetType="group" targetId={g.id} />}
      {sub === "health" && <AgHealthEventsTab targetType="group" targetId={g.id} />}
      {sub === "breeding" && <AgBreedingRecordsTab targetType="group" targetId={g.id} />}
      {sub === "movement" && <AgMovementRecordsTab targetType="group" targetId={g.id} farmId={g.farmId} currentAreaId={g.productionAreaId} currentCount={g.currentCount} />}
      {sub === "mortality" && <AgMortalityRecordsTab targetType="group" targetId={g.id} />}
      {sub === "feed" && <AgFeedRecordsTab targetType="group" targetId={g.id} farmId={g.farmId} speciesName={speciesNames[g.speciesId]} />}
      {sub === "evidence" && <AgEvidenceTab targetType="group" targetId={g.id} />}
    </div>
  )
}

const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer" }
const backBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", background: "none", border: "none", color: "var(--hf-text-muted)", fontSize: 12.5, fontWeight: 600, cursor: "pointer", padding: 0 }
