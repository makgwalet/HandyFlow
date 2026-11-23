// src/pages/agriculture/AgMortalityRecordsTab.tsx
//
// Shared animal/group mortality history — list + create only (confirmed;
// no transition endpoints). Recording a death for an animal also sets its
// status to DECEASED server-side; for a group it decrements currentCount
// (and auto-closes the group at zero) — both confirmed via
// AgMortalityRecordService, so no separate frontend action is needed for
// that side-effect.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { Plus, Skull } from "lucide-react"
import AgPager from "./AgPager"
import { PAGE_SIZE, rangeLabel } from "./agPaging.logic"
import { apiClient } from "../../api/client"
import { fmtMoney, type AgTargetType, targetBasePath, inp, btnPrimary, btnGhost } from "./constants"
import { agConfirm } from "./agConfirm"
import { Field, QueryError } from "./agUi"
import AgManage from "./AgManage"

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
  // 50 a page, newest first; the server never sends more than 200 at a time
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, refetch } = useQuery<Page<MortalityRecordResponse>>({
    queryKey: [...queryKey, page],
    placeholderData: prev => prev,
    queryFn: async () => (await apiClient.get(`${base}/mortality-records`, { params: { page, size: PAGE_SIZE } })).data,
  })
  const total = data?.totalElements ?? 0

  const createMut = useAgToastMutation({
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
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>
          {targetType === "animal" ? "Recording a death sets this animal's status to DECEASED." : "Recording a death reduces this group's current count."}
        </p>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record death</button></AgManage>}
      </div>

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 8 }}>
            <Field label="Date"><input type="date" value={mortalityDate} onChange={e => setMortalityDate(e.target.value)} style={inp} /></Field>
            {targetType === "group" && <Field label="Count lost"><input type="number" min={1} value={countLost} onChange={e => setCountLost(e.target.value)} style={inp} /></Field>}
            <Field label="Cause category"><select value={causeCategory} onChange={e => setCauseCategory(e.target.value)} style={inp}>{CAUSE_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}</select></Field>
            <Field label="Estimated value loss (R)"><input type="number" min={0} step="0.01" value={estimatedValueLoss} onChange={e => setEstimatedValueLoss(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ marginBottom: 10 }}><Field label="Cause detail"><input value={causeDetail} onChange={e => setCauseDetail(e.target.value)} style={inp} /></Field></div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending} onClick={async () => { if (await agConfirm({ title: "Record this death?", message: "This cannot be undone from the screen.", confirmLabel: "Record death", danger: true })) createMut.mutate() }}
              style={{ ...btnPrimary, opacity: createMut.isPending ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        records.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>No mortalities recorded.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Skull size={13} style={{ color: 'var(--hf-danger-text)' }} />
                <div>
                  <p style={{ fontSize: 12.5, color: "var(--hf-text)", fontWeight: 600, margin: 0 }}>{r.causeCategory}{r.countLost > 1 ? ` — ${r.countLost} lost` : ""}{r.causeDetail ? ` · ${r.causeDetail}` : ""}</p>
                  <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{r.mortalityDate}{r.estimatedValueLoss ? ` · ${fmtMoney(r.estimatedValueLoss)} loss` : ""}{r.reportedByName ? ` · ${r.reportedByName}` : ""}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
      {total > PAGE_SIZE && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{rangeLabel(page, total, "mortality record")}</p>}
      <AgPager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
    </div>
  )
}

