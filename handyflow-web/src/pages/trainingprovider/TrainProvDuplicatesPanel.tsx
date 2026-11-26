// src/pages/trainingprovider/TrainProvDuplicatesPanel.tsx
//
// People held twice, and delegates with no learner record. A match only says "look at this". A merge is proposed by one person with a
// reason and decided by a second (or by the same person, saying why alone). Nothing is deleted: the merged learner is archived.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuthStore } from "../../store/auth.store"
import {
  MERGE_STATUS, STRENGTH, linkBody, mergeDecisionBody, mergeDecisionProblem, mergeProposalBody, mergeProposalProblem, reasonText,
  type MatchView, type MergeDecisionForm, type MergeView, type PairView, type PersonView, type UnmatchedDelegate,
} from "./matchRules"
import { Badge, Notice, SubTabs } from "./tpUi"
import { card, errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

const person = (p: PersonView) => `${p.name} · ${p.number}${p.idNumberMasked ? ` · ${p.idNumberMasked}` : ""}${p.dateOfBirth ? ` · born ${p.dateOfBirth}` : ""}${p.email ? ` · ${p.email}` : ""}`

function Pairs({ canManage }: { canManage: boolean }) {
  const qc = useQueryClient()
  const { data = [], isLoading } = useQuery<PairView[]>({ queryKey: ["tp-dup-pairs"], queryFn: async () => unwrap<PairView[]>(await apiClient.get(`${LRN}/learners/duplicate-pairs`)) })
  const [openKey, setOpenKey] = useState("")
  const [keepId, setKeepId] = useState("")
  const [reason, setReason] = useState("")
  const [accepted, setAccepted] = useState(false)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const propose = async (p: PairView) => {
    const f = { keepId, reason, conflictsAccepted: accepted }
    const why = mergeProposalProblem(f, p.conflicts); if (why) { setError(why); return }
    setBusy(true); setError("")
    try {
      await apiClient.post(`${LRN}/learner-merges`, mergeProposalBody(p.first.id, p.second.id, f))
      setOpenKey(""); setReason(""); setAccepted(false)
      await Promise.all([qc.invalidateQueries({ queryKey: ["tp-dup-pairs"] }), qc.invalidateQueries({ queryKey: ["tp-merges"] })])
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (isLoading) return <div style={muted}>Looking…</div>
  if (data.length === 0) return <div style={muted}>No live learners look like the same person.</div>
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>These records share something. Only a person can say whether they are one person. Records that disagree on an identity number or birth date are marked.</div>
      {data.map(p => {
        const key = `${p.first.id}:${p.second.id}`
        return (
          <div key={key} style={{ ...card, display: "grid", gap: 6 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
              <div style={{ display: "grid", gap: 2 }}><span>{person(p.first)}</span><span>{person(p.second)}</span></div>
              <div style={{ display: "grid", justifyItems: "end", gap: 4 }}>
                <Badge text={STRENGTH[p.strength]} tone={p.strength === "STRONG" ? "warn" : "neutral"} />
                <span style={muted}>{reasonText(p.reasons)}</span>
              </div>
            </div>
            {p.conflicts.length > 0 && <div style={{ ...muted, color: "var(--hf-danger, #b00020)" }}>They disagree: {p.conflicts.join("; ")}</div>}
            {canManage && openKey !== key && <div><button style={ghost} onClick={() => { setOpenKey(key); setKeepId(p.first.id); setError("") }}>Propose a merge</button></div>}
            {openKey === key && (
              <div style={{ display: "grid", gap: 6 }}>
                <select aria-label="Record to keep" style={input} value={keepId} onChange={e => setKeepId(e.target.value)}>
                  <option value={p.first.id}>Keep {p.first.name} ({p.first.number})</option>
                  <option value={p.second.id}>Keep {p.second.name} ({p.second.number})</option>
                </select>
                <input aria-label="Merge reason" style={input} placeholder="Why are these the same person?" value={reason} onChange={e => setReason(e.target.value)} />
                {p.conflicts.length > 0 && <label style={{ fontSize: 13 }}><input type="checkbox" checked={accepted} onChange={e => setAccepted(e.target.checked)} /> I have checked the differences and they are mistakes in one record</label>}
                <Notice text={error} />
                <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => propose(p)}>Propose merge</button><button style={ghost} onClick={() => setOpenKey("")}>Cancel</button></div>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

function Unmatched({ canManage, onOpen }: { canManage: boolean; onOpen: (id: string) => void }) {
  const qc = useQueryClient()
  const { data = [], isLoading } = useQuery<UnmatchedDelegate[]>({ queryKey: ["tp-unmatched"], queryFn: async () => unwrap<UnmatchedDelegate[]>(await apiClient.get(`${LRN}/learners/unmatched-delegates`)) })
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const link = async (learnerId: string, delegateId: string) => {
    setBusy(true); setError("")
    try {
      await apiClient.put(`${LRN}/learners/${learnerId}/delegate`, linkBody(delegateId, "Linked from the unmatched delegates list"))
      await qc.invalidateQueries({ queryKey: ["tp-unmatched"] })
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (isLoading) return <div style={muted}>Looking…</div>
  if (data.length === 0) return <div style={muted}>Every delegate is linked to a learner.</div>
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>Delegates with no learner record. That is allowed: a delegate does not have to be a learner. Where a learner looks like the same person, you can link them. The two stay separate records.</div>
      <Notice text={error} />
      {data.map(u => (
        <div key={u.delegate.id} style={{ ...card, display: "grid", gap: 6 }}>
          <div><strong>{u.delegate.name}</strong> <span style={muted}>{u.delegate.number}{u.clientName ? ` · ${u.clientName}` : ""}{u.delegate.email ? ` · ${u.delegate.email}` : ""}</span></div>
          {u.candidates.length === 0 ? <div style={muted}>No learner looks like this delegate.</div> : u.candidates.map((c: MatchView) => (
            <div key={c.person.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
              <div><button style={{ ...ghost, border: "none", padding: 0, textDecoration: "underline" }} onClick={() => onOpen(c.person.id)}>{person(c.person)}</button>
                <div style={muted}>{STRENGTH[c.strength]}: {reasonText(c.reasons)}{c.conflicts.length ? ` · they disagree: ${c.conflicts.join("; ")}` : ""}</div></div>
              {canManage && c.conflicts.length === 0 && c.person.status === "ACTIVE" && <button style={ghost} disabled={busy} onClick={() => link(c.person.id, u.delegate.id)}>Link</button>}
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

function MergeCard({ m, canManage, isAdmin, me, onChanged }: { m: MergeView; canManage: boolean; isAdmin: boolean; me: string | null; onChanged: () => void }) {
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<"approve" | "reject" | "cancel">("approve")
  const [f, setF] = useState<MergeDecisionForm>({ reason: "", alone: false, aloneReason: "" })
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const mine = me !== null && me === m.proposedBy
  const send = async () => {
    const why = mode === "cancel" ? (f.reason.trim().length >= 10 ? null : "Say why, in at least 10 characters") : mergeDecisionProblem(f, m.proposedBy, me)
    if (why) { setError(why); return }
    setBusy(true); setError("")
    try {
      if (mode === "cancel") await apiClient.post(`${LRN}/learner-merges/${m.id}/cancel`, { reason: f.reason.trim() })
      else await apiClient.post(`${LRN}/learner-merges/${m.id}/${mode}`, mergeDecisionBody(f))
      setOpen(false); onChanged()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{m.mergedName} ({m.mergedNo})</strong> into <strong>{m.survivorName} ({m.survivorNo})</strong>
          <div style={muted}>Proposed {m.proposedAt.slice(0, 10)}{m.matchReasons.length ? ` · ${reasonText(m.matchReasons)}` : ""}</div></div>
        <Badge text={MERGE_STATUS[m.status] ?? m.status} tone={m.status === "COMPLETED" ? "good" : m.status === "PROPOSED" ? "warn" : "neutral"} />
      </div>
      <div style={muted}>Why: {m.reason}</div>
      {m.conflicts.length > 0 && <div style={muted}>The records disagreed ({m.conflicts.join("; ")}) and the proposer said they were checked.</div>}
      {m.status === "COMPLETED" && <div style={muted}>Moved {m.movedEnrolments} enrolment(s), {m.movedApplications} application(s), {m.movedDocuments} document(s). {m.delegateNote ?? ""} Decided: {m.decisionReason}</div>}
      {(m.status === "REJECTED" || m.status === "CANCELLED") && <div style={muted}>{m.decisionReason}</div>}
      {m.status === "PROPOSED" && canManage && !open && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {isAdmin && <button style={solid} onClick={() => { setMode("approve"); setOpen(true); setError("") }}>Approve</button>}
          {isAdmin && !mine && <button style={ghost} onClick={() => { setMode("reject"); setOpen(true); setError("") }}>Reject</button>}
          {mine && <button style={ghost} onClick={() => { setMode("cancel"); setOpen(true); setError("") }}>Cancel my proposal</button>}
        </div>
      )}
      {open && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Decision reason" style={input} placeholder={mode === "approve" ? "What did you check?" : "Why?"} value={f.reason} onChange={e => setF({ ...f, reason: e.target.value })} />
          {mode !== "cancel" && mine && <label style={{ fontSize: 13 }}><input type="checkbox" checked={f.alone} onChange={e => setF({ ...f, alone: e.target.checked })} /> No second person is available. I am deciding this alone</label>}
          {mode !== "cancel" && mine && f.alone && <input aria-label="Reason for deciding alone" style={input} placeholder="Why is no second person deciding?" value={f.aloneReason} onChange={e => setF({ ...f, aloneReason: e.target.value })} />}
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={send}>{mode === "approve" ? "Merge the learners" : mode === "reject" ? "Reject" : "Cancel the proposal"}</button><button style={ghost} onClick={() => setOpen(false)}>Back</button></div>
        </div>
      )}
    </div>
  )
}

function Merges({ canManage, isAdmin }: { canManage: boolean; isAdmin: boolean }) {
  const qc = useQueryClient()
  const me = useAuthStore(s => s.user?.userId ?? null)
  const [status, setStatus] = useState("PROPOSED")
  const { data = [], isLoading } = useQuery<MergeView[]>({
    queryKey: ["tp-merges", status],
    queryFn: async () => unwrap<MergeView[]>(await apiClient.get(`${LRN}/learner-merges`, { params: { status: status || undefined } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <select aria-label="Merge status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}>
        <option value="PROPOSED">Waiting for a decision</option><option value="COMPLETED">Merged</option><option value="REJECTED">Rejected</option><option value="CANCELLED">Cancelled</option><option value="">All</option>
      </select>
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No merges.</div> : data.map(m => (
        <MergeCard key={m.id} m={m} canManage={canManage} isAdmin={isAdmin} me={me}
          onChanged={() => { void qc.invalidateQueries({ queryKey: ["tp-merges"] }); void qc.invalidateQueries({ queryKey: ["tp-dup-pairs"] }); void qc.invalidateQueries({ queryKey: ["tp-learners"] }) }} />
      ))}
    </div>
  )
}

export default function TrainProvDuplicatesPanel({ canManage, isAdmin, onOpen }: { canManage: boolean; isAdmin: boolean; onOpen: (learnerId: string) => void }) {
  const [tab, setTab] = useState("pairs")
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <SubTabs value={tab} onChange={setTab} tabs={[["pairs", "Possible duplicates"], ["unmatched", "Delegates with no learner"], ["merges", "Merges"]]} />
      {tab === "pairs" && <Pairs canManage={canManage} />}
      {tab === "unmatched" && <Unmatched canManage={canManage} onOpen={onOpen} />}
      {tab === "merges" && <Merges canManage={canManage} isAdmin={isAdmin} />}
    </div>
  )
}
