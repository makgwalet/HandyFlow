// src/pages/trainingprovider/TrainProvLearnerPortalAccess.tsx
//
// Lets an administrator give a learner a login to look at their own records, read-only. The invite carries a written reason, and ending the access also
// needs a reason. Nothing is deleted: ended access stays in the list. The learner sees attendance as counts and a recorded outcome, never a competence result.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { endProblem, grantText, inviteProblem } from "../trainingprovider-portal/learnerPortalRules"
import { Badge, Field, Notice } from "./tpUi"
import { errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

type Grant = { id: string; email: string; status: string; reason: string; acceptedAt: string | null; endedReason: string | null; createdAt: string }

export default function TrainProvLearnerPortalAccess({ learnerId, defaultEmail, canManage, archived }: { learnerId: string; defaultEmail: string | null; canManage: boolean; archived: boolean }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<"" | "invite" | { end: string }>("")
  const [email, setEmail] = useState(defaultEmail ?? "")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const key = ["tp-learner-portal", learnerId]
  const { data } = useQuery<Grant[]>({ queryKey: key, enabled: open, queryFn: async () => unwrap<Grant[]>(await apiClient.get(`${LRN}/learners/${learnerId}/portal-access`)) })
  const grants = Array.isArray(data) ? data : []
  const live = grants.some(g => g.status !== "ENDED")
  const done = () => { setMode(""); setReason(""); setError(""); void qc.invalidateQueries({ queryKey: key }) }

  const invite = async () => {
    const p = inviteProblem(email, reason); if (p) { setError(p); return }
    try { await apiClient.post(`${LRN}/learners/${learnerId}/portal-access`, { email: email.trim(), reason: reason.trim() }); done() } catch (e) { setError(errText(e)) }
  }
  const end = async (grantId: string) => {
    const p = endProblem(reason); if (p) { setError(p); return }
    try { await apiClient.post(`${LRN}/learners/${learnerId}/portal-access/${grantId}/end`, { reason: reason.trim() }); done() } catch (e) { setError(errText(e)) }
  }

  if (!open) return <div><button style={ghost} onClick={() => setOpen(true)}>Learner portal access</button></div>
  return (
    <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
      <strong style={{ fontSize: 13 }}>Learner portal access</strong>
      <div style={muted}>A login that lets the learner see their own enrolments, upcoming sessions, attendance counts and recorded outcome. Read-only.</div>
      {grants.length === 0 ? <div style={muted}>No access has been given.</div> : grants.map(g => (
        <div key={g.id} style={{ display: "grid", gap: 2, fontSize: 13 }}>
          <div>{g.email} <Badge text={grantText(g.status)} tone={g.status === "ACCEPTED" ? "good" : "neutral"} /></div>
          <div style={muted}>Why: {g.reason}{g.endedReason ? ` · Ended: ${g.endedReason}` : ""}</div>
          {canManage && g.status !== "ENDED" && typeof mode !== "object" && <div><button style={ghost} onClick={() => { setMode({ end: g.id }); setReason(""); setError("") }}>End access</button></div>}
          {typeof mode === "object" && mode.end === g.id && (
            <div style={{ display: "grid", gap: 6 }}>
              <Field label="Why is the access ending?"><input aria-label="Reason for ending access" style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => void end(g.id)}>End access</button><button style={ghost} onClick={() => setMode("")}>Cancel</button></div>
            </div>
          )}
        </div>
      ))}
      {canManage && !archived && !live && mode === "" && <div><button style={solid} onClick={() => { setMode("invite"); setReason(""); setError("") }}>Invite the learner</button></div>}
      {mode === "invite" && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label="Learner's email"><input aria-label="Portal email" style={input} value={email} onChange={e => setEmail(e.target.value)} /></Field>
          <Field label="Why is the learner being given access?"><input aria-label="Reason for access" style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => void invite()}>Send invite</button><button style={ghost} onClick={() => setMode("")}>Cancel</button></div>
        </div>
      )}
    </div>
  )
}
