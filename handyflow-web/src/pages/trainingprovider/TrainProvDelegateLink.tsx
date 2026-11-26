// src/pages/trainingprovider/TrainProvDelegateLink.tsx
//
// A learner can optionally be linked to one delegate. The two stay separate records; the link says they are the same person.
// Taking a link off or moving it needs a reason, and every change is kept in the history.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { EVENT, linkBody, linkChangeProblem, type LearnerEvent } from "./matchRules"
import { Notice } from "./tpUi"
import { errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"
import type { Learner } from "./learnerRules"

type DelegateOption = { id: string; fullName: string; delegateNumber: string }

export default function TrainProvDelegateLink({ learner, canManage, onChanged }: { learner: Learner; canManage: boolean; onChanged: () => void }) {
  const [open, setOpen] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [pick, setPick] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const archived = learner.status === "ARCHIVED"
  const { data: options = [] } = useQuery<DelegateOption[]>({
    queryKey: ["tp-delegate-options", learner.clientId],
    enabled: open && !!learner.clientId,
    queryFn: async () => {
      const d = unwrap<DelegateOption[] | { content?: DelegateOption[] }>(await apiClient.get(`${LRN}/delegates`, { params: { clientId: learner.clientId, size: 200 } }))
      return Array.isArray(d) ? d : d?.content ?? []
    },
  })
  const { data: history = [] } = useQuery<LearnerEvent[]>({
    queryKey: ["tp-learner-history", learner.id, learner.delegateId],
    enabled: showHistory,
    queryFn: async () => unwrap<LearnerEvent[]>(await apiClient.get(`${LRN}/learners/${learner.id}/history`)),
  })
  const hadLink = !!learner.delegateId
  const send = async (delegateId: string) => {
    const why = linkChangeProblem(hadLink, reason, delegateId); if (why) { setError(why); return }
    setBusy(true); setError("")
    try {
      await apiClient.put(`${LRN}/learners/${learner.id}/delegate`, linkBody(delegateId, reason))
      setOpen(false); setPick(""); setReason(""); onChanged()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
      <div style={muted}>
        {learner.mergedIntoId ? "This learner was merged into another record and is kept for history. " : ""}
        {hadLink ? `Linked to delegate ${learner.delegateName ?? ""}. The delegate and the learner stay separate records.` : "Not linked to a delegate. A learner does not have to be one."}
      </div>
      {canManage && !archived && !open && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button style={ghost} onClick={() => { setOpen(true); setError("") }}>{hadLink ? "Change or remove the delegate link" : "Link a delegate"}</button>
          <button style={ghost} onClick={() => setShowHistory(!showHistory)}>{showHistory ? "Hide history" : "Link and merge history"}</button>
        </div>
      )}
      {(!canManage || archived) && <div><button style={ghost} onClick={() => setShowHistory(!showHistory)}>{showHistory ? "Hide history" : "Link and merge history"}</button></div>}
      {open && (
        <div style={{ display: "grid", gap: 6 }}>
          {!learner.clientId ? <div style={muted}>Choose the employer or sponsor on the learner first. Delegates belong to a client.</div> : (
            <select aria-label="Delegate" style={input} value={pick} onChange={e => setPick(e.target.value)}>
              <option value="">Choose a delegate</option>{options.map(o => <option key={o.id} value={o.id}>{o.fullName} ({o.delegateNumber})</option>)}
            </select>
          )}
          <input aria-label="Link reason" style={input} placeholder={hadLink ? "Why is the link changing? Required" : "Note (optional unless the identity numbers differ)"} value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button style={solid} disabled={busy || !pick} onClick={() => send(pick)}>{hadLink ? "Move the link" : "Link"}</button>
            {hadLink && <button style={ghost} disabled={busy} onClick={() => send("")}>Remove the link</button>}
            <button style={ghost} onClick={() => setOpen(false)}>Cancel</button>
          </div>
        </div>
      )}
      {showHistory && (history.length === 0 ? <div style={muted}>Nothing recorded yet.</div> : history.map(e => (
        <div key={e.id} style={muted}>{e.actedAt.slice(0, 10)} · {EVENT[e.eventType] ?? e.eventType}{e.reason ? ` · ${e.reason}` : ""}</div>
      )))}
    </div>
  )
}
