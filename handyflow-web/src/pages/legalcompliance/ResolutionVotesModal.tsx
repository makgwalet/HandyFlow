// src/pages/legalcompliance/ResolutionVotesModal.tsx
//
// Votes on one committee decision. Endpoints (/api/v1/legalcompliance/committees/{id}/meetings/{mid}/resolutions/{rid}/votes):
//   GET · PUT (record votes) · PUT /type (ordinary | special) · POST /close · POST /close-with-casting-vote
// Only members who were present and are not observers can vote. Votes can be changed until voting is closed; closing works out the outcome and freezes it.
// Abstentions and recusals are recorded but do not count for or against. On a tied ordinary resolution the chair's casting vote can break the tie, if the charter allows it.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { input, primaryBtn, ghostBtn, one, fmtDate, errMsg } from "./lcHelpers"
import { Modal, Err, Badge } from "./LcUi"

type Choice = "FOR" | "AGAINST" | "ABSTAIN" | "RECUSED"
interface Voter { memberId: string; name: string; role: string; eligible: boolean; choice: Choice | null; reason: string | null }
interface Tally { votesFor: number; against: number; abstained: number; recused: number }
interface View {
  resolutionId: string; description: string; type: "ORDINARY" | "SPECIAL"; requirement: string; status: "OPEN" | "CLOSED"; outcome: "CARRIED" | "NOT_CARRIED" | null
  closedOn: string | null; closedByName: string | null; tally: Tally; quorumMin: number; canVote: boolean; voters: Voter[]; problems: string[]
  tied: boolean; castingChair: string | null; castingVote: "FOR" | "AGAINST" | null; castingVoteBy: string | null; castingVoteReason: string | null
}

const CHOICES: { value: Choice; label: string }[] = [
  { value: "FOR", label: "For" }, { value: "AGAINST", label: "Against" }, { value: "ABSTAIN", label: "Abstain" }, { value: "RECUSED", label: "Recused" },
]

export default function ResolutionVotesModal({ committeeId, meetingId, resolutionId, canManage, onClose }: { committeeId: string; meetingId: string; resolutionId: string; canManage: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const url = `/api/v1/legalcompliance/committees/${committeeId}/meetings/${meetingId}/resolutions/${resolutionId}/votes`
  const [draft, setDraft] = useState<Record<string, { choice: Choice | null; reason: string }>>({})
  const [err, setErr] = useState("")
  const [confirmClose, setConfirmClose] = useState(false)
  const [casting, setCasting] = useState<{ choice: "FOR" | "AGAINST" | null; reason: string; charter: boolean }>({ choice: null, reason: "", charter: false })

  const { data: v, isLoading } = useQuery<View>({ queryKey: ["lc-com", "votes", resolutionId], queryFn: async () => one(await apiClient.get(url)) as View })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-com"] }); setDraft({}); setErr("") }
  const fail = (e: unknown) => setErr(errMsg(e, "That did not work."))
  const save = useMutation({
    mutationFn: (votes: { memberId: string; choice: Choice; reason: string | null }[]) => apiClient.put(url, { votes }),
    onSuccess: refresh, onError: fail,
  })
  const setType = useMutation({ mutationFn: (type: string) => apiClient.put(`${url}/type`, { type }), onSuccess: refresh, onError: fail })
  const close = useMutation({ mutationFn: () => apiClient.post(`${url}/close`), onSuccess: () => { refresh(); setConfirmClose(false) }, onError: (e: unknown) => { fail(e); setConfirmClose(false) } })

  const closeCasting = useMutation({
    mutationFn: () => apiClient.post(`${url}/close-with-casting-vote`, { choice: casting.choice, reason: casting.reason.trim(), charterPermits: casting.charter }),
    onSuccess: () => { refresh(); setCasting({ choice: null, reason: "", charter: false }) }, onError: fail,
  })

  if (isLoading || !v) return <Modal title="Votes" onClose={onClose} wide><p style={{ color: "#64748B" }}>Loading…</p></Modal>

  const open = v.status === "OPEN"
  const editable = open && v.canVote && canManage
  const current = (m: Voter) => draft[m.memberId] ?? { choice: m.choice, reason: m.reason ?? "" }
  const dirty = Object.keys(draft).length > 0
  const eligible = v.voters.filter(m => m.eligible)
  const toSend = eligible.map(m => ({ m, d: current(m) })).filter(x => x.d.choice !== null)
  const recusalWithoutReason = toSend.some(x => x.d.choice === "RECUSED" && x.d.reason.trim().length < 10)

  return (
    <Modal title="Votes on this decision" onClose={onClose} wide>
      <p style={{ fontSize: 14, color: "#0F172A", margin: "0 0 8px", whiteSpace: "pre-wrap" }}>{v.description}</p>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 10 }}>
        {open ? <Badge text="Voting open" color="#92400E" bg="#FEF3C7" /> : <Badge text={v.outcome === "CARRIED" ? "Carried" : "Not carried"} color={v.outcome === "CARRIED" ? "#166534" : "#991B1B"} bg={v.outcome === "CARRIED" ? "#DCFCE7" : "#FEE2E2"} />}
        <Badge text={v.type === "SPECIAL" ? "Special resolution" : "Ordinary resolution"} color="#3730A3" bg="#E0E7FF" />
        {editable && (
          <select style={{ ...input, width: 190 }} value={v.type} disabled={setType.isPending || dirty} title={dirty ? "Save the votes first" : "Type of resolution"} onChange={e => setType.mutate(e.target.value)}>
            <option value="ORDINARY">Ordinary resolution</option>
            <option value="SPECIAL">Special resolution</option>
          </select>
        )}
      </div>
      <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 10px" }}>{v.requirement}. Abstentions and recusals are recorded but do not count for or against.</p>
      {err && <Err m={err} />}

      <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, overflow: "hidden" }}>
        {v.voters.length === 0 && <p style={{ padding: 12, margin: 0, color: "#64748B", fontSize: 13 }}>Nobody was recorded as present.</p>}
        {v.voters.map(m => {
          const d = current(m)
          return (
            <div key={m.memberId} style={{ padding: "8px 12px", borderBottom: "1px solid #F1F5F9", opacity: m.eligible ? 1 : 0.6 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{m.name} <span style={{ fontWeight: 400, color: "#64748B" }}>· {m.role.charAt(0) + m.role.slice(1).toLowerCase()}</span></div>
                {!m.eligible ? <span style={{ fontSize: 12, color: "#64748B" }}>Cannot vote</span> : editable ? (
                  <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                    {CHOICES.map(c => (
                      <label key={c.value} style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 4, cursor: "pointer" }}>
                        <input type="radio" name={`vote-${m.memberId}`} checked={d.choice === c.value} onChange={() => setDraft(x => ({ ...x, [m.memberId]: { choice: c.value, reason: d.reason } }))} />{c.label}
                      </label>
                    ))}
                  </div>
                ) : <span style={{ fontSize: 12, fontWeight: 600, color: "#334155" }}>{m.choice ? CHOICES.find(c => c.value === m.choice)?.label : "No vote recorded"}</span>}
              </div>
              {m.eligible && (editable ? d.choice === "RECUSED" : m.choice === "RECUSED") && (editable ? (
                <input style={{ ...input, marginTop: 6 }} placeholder="Why is this member recused? (at least 10 characters)" maxLength={500} value={d.reason} onChange={e => setDraft(x => ({ ...x, [m.memberId]: { choice: d.choice, reason: e.target.value } }))} />
              ) : <div style={{ fontSize: 12, color: "#64748B", marginTop: 4 }}>{m.reason}</div>)}
            </div>
          )
        })}
      </div>

      <div style={{ display: "flex", gap: 14, margin: "10px 0", fontSize: 13, color: "#334155", flexWrap: "wrap" }}>
        <span><strong>{v.tally.votesFor}</strong> for</span><span><strong>{v.tally.against}</strong> against</span>
        <span><strong>{v.tally.abstained}</strong> abstained</span><span><strong>{v.tally.recused}</strong> recused</span>
        <span style={{ color: "#64748B" }}>Quorum {v.quorumMin}</span>
      </div>
      {!open && <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 8px" }}>Counted {fmtDate(v.closedOn)}{v.closedByName ? ` by ${v.closedByName}` : ""}. A closed vote cannot be changed.</p>}
      {!open && v.castingVote && (
        <p style={{ fontSize: 12, color: "#334155", background: "#F1F5F9", borderRadius: 8, padding: "8px 10px", margin: "0 0 10px" }}>
          The vote was tied. {v.castingVoteBy} used the chair's casting vote {v.castingVote === "FOR" ? "for" : "against"} the resolution. Basis: {v.castingVoteReason}
        </p>
      )}
      {editable && v.tied && !dirty && (v.castingChair ? (
        <div style={{ border: "1px solid #FCD34D", background: "#FFFBEB", borderRadius: 8, padding: 10, margin: "0 0 10px", fontSize: 13 }}>
          <div style={{ fontWeight: 600, color: "#92400E", marginBottom: 4 }}>The vote is tied</div>
          <div style={{ color: "#475569", marginBottom: 8 }}>Closing normally counts a tie as not carried. If your committee's charter or standing orders give the chair a casting vote, {v.castingChair} can use it here. The counts stay as voted.</div>
          <div style={{ display: "flex", gap: 14, marginBottom: 6 }}>
            {(["FOR", "AGAINST"] as const).map(c => (
              <label key={c} style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 4, cursor: "pointer" }}>
                <input type="radio" name="casting" checked={casting.choice === c} onChange={() => setCasting({ ...casting, choice: c })} />Casting vote {c === "FOR" ? "for" : "against"}
              </label>
            ))}
          </div>
          <input style={{ ...input, marginBottom: 6 }} placeholder="What allows it? e.g. the clause in the charter (at least 10 characters)" maxLength={500} value={casting.reason} onChange={e => setCasting({ ...casting, reason: e.target.value })} />
          <label style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 6, marginBottom: 8, cursor: "pointer" }}>
            <input type="checkbox" checked={casting.charter} onChange={e => setCasting({ ...casting, charter: e.target.checked })} />I confirm the charter or standing orders give the chair a casting vote
          </label>
          <button style={primaryBtn} disabled={closeCasting.isPending || !casting.choice || casting.reason.trim().length < 10 || !casting.charter} onClick={() => closeCasting.mutate()}>Close with the chair's casting vote</button>
        </div>
      ) : (
        <p style={{ fontSize: 12, color: "#92400E", background: "#FEF3C7", borderRadius: 8, padding: "8px 10px", margin: "0 0 10px" }}>The vote is tied, so it will count as not carried. There is no chair present who can use a casting vote.</p>
      ))}
      {editable && v.problems.length > 0 && !dirty && (
        <ul style={{ fontSize: 12, color: "#92400E", background: "#FEF3C7", borderRadius: 8, padding: "8px 8px 8px 24px", margin: "0 0 10px" }}>{v.problems.map(p => <li key={p}>{p}</li>)}</ul>
      )}

      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", flexWrap: "wrap" }}>
        <button style={ghostBtn} onClick={onClose}>{editable ? "Close window" : "Done"}</button>
        {editable && <button style={primaryBtn} disabled={!dirty || save.isPending || recusalWithoutReason}
          onClick={() => save.mutate(toSend.map(x => ({ memberId: x.m.memberId, choice: x.d.choice as Choice, reason: x.d.choice === "RECUSED" ? x.d.reason.trim() : null })))}>Save votes</button>}
        {editable && !confirmClose && <button style={ghostBtn} disabled={dirty || v.problems.length > 0 || close.isPending} title={dirty ? "Save the votes first" : v.problems[0]} onClick={() => setConfirmClose(true)}>Close voting…</button>}
        {editable && confirmClose && <button style={primaryBtn} disabled={close.isPending} onClick={() => close.mutate()}>Confirm: count the votes and freeze the result</button>}
      </div>
    </Modal>
  )
}
