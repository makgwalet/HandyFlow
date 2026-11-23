// src/pages/agriculture/AgWeightHistoryTab.tsx
//
// Shared between AgAnimalDetail and AgGroupDetail via the targetType prop
// — the six Livestock history sub-resources are identical in shape between
// an individually-tracked animal and a group, differing only in the
// /animals/ vs /groups/ URL prefix and which of animalId/groupId is
// populated server-side (enforced via AgTrackingTarget.requireExactlyOne).
// Confirmed via AgAnimalController/AgGroupController: POST also updates
// the animal's currentWeightKg / group's averageWeightKg server-side.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { Plus, Scale } from "lucide-react"
import AgPager from "./AgPager"
import { PAGE_SIZE, rangeLabel } from "./agPaging.logic"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, type AgTargetType, targetBasePath, inp, btnPrimary, btnGhost } from "./constants"
import { Field, QueryError } from "./agUi"
import AgManage from "./AgManage"

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
  // 50 a page, newest first; the server never sends more than 200 at a time
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, refetch } = useQuery<Page<WeightRecordResponse>>({
    queryKey: [...queryKey, page],
    placeholderData: prev => prev,
    queryFn: async () => (await apiClient.get(`${base}/weight-records`, { params: { page, size: PAGE_SIZE } })).data,
  })
  const total = data?.totalElements ?? 0

  const createMut = useAgToastMutation({
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
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>
          {targetType === "group" ? "Average/sampled weight readings for this group." : "Individual weight history for this animal."}
        </p>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record weight</button></AgManage>}
      </div>

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 10 }}>
            <Field label="Date"><input type="date" value={recordedDate} onChange={e => setRecordedDate(e.target.value)} style={inp} /></Field>
            <Field label="Weight (kg)"><input type="number" min={0} step="0.1" value={weightKg} onChange={e => setWeightKg(e.target.value)} style={inp} /></Field>
            {targetType === "group" && <Field label="Sample size"><input type="number" min={1} value={sampleSize} onChange={e => setSampleSize(e.target.value)} style={inp} /></Field>}
            <Field label="Notes"><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !weightKg} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !weightKg ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        records.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>No weight records yet.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Scale size={13} style={{ color: AG_ACCENT_TEXT }} />
                <span style={{ fontSize: 12.5, color: "var(--hf-text)", fontWeight: 600 }}>{r.weightKg} kg</span>
                {r.sampleSize ? <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>(n={r.sampleSize})</span> : null}
              </div>
              <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{r.recordedDate}{r.recordedByName ? ` · ${r.recordedByName}` : ""}</span>
            </div>
          ))}
        </div>
      )}
      {total > PAGE_SIZE && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{rangeLabel(page, total, "weight record")}</p>}
      <AgPager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
    </div>
  )
}

