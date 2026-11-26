// src/pages/trainingprovider/TrainProvProgrammeNeeds.tsx
//
// The resources one programme version lists as needed, each with where the need comes from. The provider's own list; it holds no standard.
// Changed only while the version is a draft. Withdrawn with a reason, never deleted.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { TYPES } from "./resourceRules"
import { NEEDS_NOTE, NONE_NOTE, emptyNeed, needBody, needLine, needProblem, withdrawProblem, type NeedForm, type VersionNeeds } from "./programmeNeedsRules"
import { Field, Notice } from "./tpUi"
import { errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { CAT } from "./tpApi"

export default function TrainProvProgrammeNeeds({ versionId, canManage }: { versionId: string; canManage: boolean }) {
  const qc = useQueryClient()
  const url = `${CAT}/programme-versions/${versionId}/resource-needs`
  const { data } = useQuery<VersionNeeds>({ queryKey: ["tp-programme-needs", versionId], queryFn: async () => unwrap<VersionNeeds>(await apiClient.get(url)) })
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState<NeedForm>(emptyNeed())
  const [withdrawing, setWithdrawing] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  if (!data) return null
  const run = async (fn: () => Promise<unknown>, done: () => void) => {
    setBusy(true); setError("")
    try { await fn(); done(); await qc.invalidateQueries({ queryKey: ["tp-programme-needs", versionId] }) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const live = data.needs.filter(n => n.status === "ACTIVE")
  const earlier = data.needs.filter(n => n.status === "WITHDRAWN")
  return (
    <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
      <strong style={{ fontSize: 13 }}>Resources needed (optional)</strong>
      {live.length === 0 ? <div style={muted}>{NONE_NOTE}</div> : live.map(n => (
        <div key={n.id} style={{ fontSize: 13 }}>
          <div>{needLine(n)}</div>
          <div style={muted}>Source: {n.sourceNote}</div>
          {canManage && data.editable && withdrawing !== n.id && <button style={{ ...ghost, marginTop: 4 }} onClick={() => { setWithdrawing(n.id); setReason(""); setError("") }}>Withdraw</button>}
          {withdrawing === n.id && (
            <div style={{ display: "grid", gap: 6, marginTop: 4 }}>
              <Field label="Why is it withdrawn?"><input style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}>
                <button style={solid} disabled={busy} onClick={() => { const p = withdrawProblem(reason); if (p) { setError(p); return } void run(() => apiClient.post(`${CAT}/resource-needs/${n.id}/withdraw`, { reason: reason.trim() }), () => setWithdrawing(null)) }}>Withdraw need</button>
                <button style={ghost} onClick={() => setWithdrawing(null)}>Cancel</button>
              </div>
            </div>
          )}
        </div>
      ))}
      {earlier.map(n => <div key={n.id} style={muted}>{needLine(n)}. Withdrawn: {n.withdrawnReason}</div>)}
      {!data.editable && <div style={muted}>This version is approved, so its list is frozen. Start a new version to change it.</div>}
      {canManage && data.editable && !adding && <button style={{ ...ghost, justifySelf: "start" }} onClick={() => { setAdding(true); setF(emptyNeed()); setError("") }}>Add a resource need</button>}
      {adding && (
        <div style={{ display: "grid", gap: 8 }}>
          <div style={muted}>{NEEDS_NOTE}</div>
          <Field label="Type"><select aria-label="Need type" style={input} value={f.resourceType} onChange={e => setF({ ...f, resourceType: e.target.value })}><option value="">Choose…</option>{Object.entries(TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
          <Field label="What is needed"><input style={input} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} /></Field>
          <Field label="Words to look for in the resource name (optional)" hint="Leave blank to count every resource of this type at the site."><input style={input} value={f.matchText} onChange={e => setF({ ...f, matchText: e.target.value })} /></Field>
          <Field label="Quantity"><input type="number" min={1} style={input} value={f.quantity} onChange={e => setF({ ...f, quantity: e.target.value })} /></Field>
          <Field label="For every how many learners (optional)" hint="Leave blank if the quantity is for the whole cohort."><input type="number" min={1} style={input} value={f.perLearners} onChange={e => setF({ ...f, perLearners: e.target.value })} /></Field>
          <Field label="Where does the need come from?"><textarea style={input} rows={2} value={f.sourceNote} onChange={e => setF({ ...f, sourceNote: e.target.value })} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = needProblem(f); if (p) { setError(p); return } void run(() => apiClient.post(url, needBody(f)), () => setAdding(false)) }}>Save need</button>
            <button style={ghost} onClick={() => setAdding(false)}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}
