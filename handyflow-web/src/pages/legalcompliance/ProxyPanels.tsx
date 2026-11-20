// src/pages/legalcompliance/ProxyPanels.tsx
//
// Committee proxies (chunk 33). Endpoints under /api/v1/legalcompliance/committees/{id}/meetings/{mid}:
//   GET /proxies · POST /proxies · POST /proxies/{pid}/revoke · GET|PUT /resolutions/{rid}/proxy-votes
// A proxy is for one meeting: an absent voting member lets a present one vote for them. It is made before the meeting is held, needs the charter clause that allows it, and never counts toward quorum.
// If the member who gave it turns up, it lapses. Proxy votes are added to the tally when voting closes.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, errMsg } from "./lcHelpers"
import { Err, Badge } from "./LcUi"
import { PROXY_CHOICES, formProblem, grantorChoices, holderChoices, revokeValid, type PMember } from "./proxyLogic"

type Choice = "FOR" | "AGAINST" | "ABSTAIN"
interface Proxy {
  id: string; grantorMemberId: string; grantorName: string; holderMemberId: string; holderName: string; charterClause: string; instructions: string | null; status: "ACTIVE" | "REVOKED"
  usable: boolean; unusableReason: string | null; appointedBy: string | null; revokedReason: string | null; revokedBy: string | null; revokedAt: string | null; appointedAt: string
}
interface ProxyVoter { proxyId: string; grantorName: string; holderName: string; usable: boolean; unusableReason: string | null; choice: Choice | null; reason: string | null }
interface ProxyVoting { resolutionId: string; open: boolean; canVote: boolean; voters: ProxyVoter[] }

const small = { ...ghostBtn, padding: "4px 10px", fontSize: 12 }

/** Inside an expanded meeting. Shown for SCHEDULED (where proxies are made) and HELD meetings (where they are read). */
export function MeetingProxies({ committeeId, meeting, members, canManage }: { committeeId: string; meeting: { id: string; meetingDate: string; status: string }; members: PMember[]; canManage: boolean }) {
  const qc = useQueryClient()
  const base = `/api/v1/legalcompliance/committees/${committeeId}/meetings/${meeting.id}/proxies`
  const key = ["lc-com", "proxies", meeting.id]
  const { data: list = [] } = useQuery<Proxy[]>({ queryKey: key, queryFn: async () => unwrap(await apiClient.get(base)) })
  const [form, setForm] = useState<{ grantor: string; holder: string; clause: string; charter: boolean; instructions: string } | null>(null)
  const [revoke, setRevoke] = useState<{ id: string; reason: string } | null>(null)
  const [err, setErr] = useState("")
  const done = () => { qc.invalidateQueries({ queryKey: key }); setForm(null); setRevoke(null); setErr("") }
  const fail = (e: unknown) => setErr(errMsg(e, "That did not work."))
  const appoint = useMutation({
    mutationFn: () => apiClient.post(base, { grantorMemberId: form?.grantor, holderMemberId: form?.holder, charterClause: form?.clause.trim(), charterPermits: form?.charter, instructions: form?.instructions.trim() || null }),
    onSuccess: done, onError: fail,
  })
  const doRevoke = useMutation({ mutationFn: () => apiClient.post(`${base}/${revoke?.id}/revoke`, { reason: revoke?.reason.trim() }), onSuccess: done, onError: fail })

  const scheduled = meeting.status === "SCHEDULED"
  if (!scheduled && list.length === 0) return null
  const grantors = grantorChoices(members, meeting.meetingDate, list)
  const holders = form ? holderChoices(members, meeting.meetingDate, list, form.grantor) : []
  const problem = form ? formProblem(form) : null
  return (
    <div style={{ margin: "8px 0", padding: 8, background: "#F8FAFC", borderRadius: 8 }}>
      <div style={{ fontWeight: 700, fontSize: 12, marginBottom: 4 }}>Proxies for this meeting</div>
      {list.length === 0 && <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 4px" }}>None. Proxies do not count toward quorum.</p>}
      {list.map(p => (
        <div key={p.id} style={{ fontSize: 12, padding: "4px 0", borderTop: "1px solid #E2E8F0" }}>
          <b>{p.grantorName}</b> → <b>{p.holderName}</b> <span style={{ color: "#64748B" }}>· {p.charterClause}</span>{" "}
          {p.status === "REVOKED" ? <Badge text="Revoked" color="#475569" bg="#E2E8F0" /> : meeting.status === "HELD" ? (p.usable ? <Badge text="In use" color="#166534" bg="#DCFCE7" /> : <Badge text="Lapsed" color="#92400E" bg="#FEF3C7" />) : <Badge text="Active" color="#3730A3" bg="#E0E7FF" />}
          {p.instructions && <div style={{ color: "#475569" }}>Instructions: {p.instructions}</div>}
          {p.status === "ACTIVE" && p.unusableReason && <div style={{ color: "#92400E" }}>{p.unusableReason}</div>}
          {p.status === "REVOKED" && <div style={{ color: "#64748B" }}>Revoked {fmtDate(p.revokedAt)}{p.revokedBy ? ` by ${p.revokedBy}` : ""}: {p.revokedReason}</div>}
          {canManage && p.status === "ACTIVE" && (revoke?.id === p.id ? (
            <div style={{ marginTop: 4 }}>
              <input style={input} placeholder="Why is it revoked? (at least 10 characters)" maxLength={500} value={revoke.reason} onChange={e => setRevoke({ id: p.id, reason: e.target.value })} />
              <div style={{ display: "flex", gap: 6, marginTop: 4 }}><button style={small} onClick={() => setRevoke(null)}>Cancel</button><button style={{ ...small, color: "#B91C1C" }} disabled={!revokeValid(revoke.reason) || doRevoke.isPending} onClick={() => doRevoke.mutate()}>Revoke</button></div>
            </div>
          ) : <button style={{ ...small, marginTop: 4 }} onClick={() => { setErr(""); setRevoke({ id: p.id, reason: "" }) }}>Revoke</button>)}
        </div>
      ))}
      <Err m={err} />
      {canManage && scheduled && !form && grantors.length > 0 && <button style={{ ...small, marginTop: 4 }} onClick={() => { setErr(""); setForm({ grantor: "", holder: "", clause: "", charter: false, instructions: "" }) }}>Record a proxy</button>}
      {form && (
        <div style={{ marginTop: 6 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <select style={input} value={form.grantor} onChange={e => setForm({ ...form, grantor: e.target.value, holder: "" })}>
              <option value="">Who is giving the proxy?</option>{grantors.map(m => <option key={m.id} value={m.id}>{m.memberName}</option>)}
            </select>
            <select style={input} value={form.holder} disabled={!form.grantor} onChange={e => setForm({ ...form, holder: e.target.value })}>
              <option value="">Who will vote for them?</option>{holders.map(m => <option key={m.id} value={m.id}>{m.memberName}</option>)}
            </select>
          </div>
          <input style={{ ...input, marginTop: 6 }} placeholder="Which charter clause allows proxies? e.g. Charter 7.2" maxLength={200} value={form.clause} onChange={e => setForm({ ...form, clause: e.target.value })} />
          <input style={{ ...input, marginTop: 6 }} placeholder="Instructions from the member (optional, for the record)" maxLength={500} value={form.instructions} onChange={e => setForm({ ...form, instructions: e.target.value })} />
          <label style={{ fontSize: 12, display: "flex", gap: 6, margin: "6px 0", cursor: "pointer" }}><input type="checkbox" checked={form.charter} onChange={e => setForm({ ...form, charter: e.target.checked })} />I confirm the charter or standing orders allow proxies</label>
          {problem && form.grantor && <p style={{ fontSize: 12, color: "#B45309", margin: "0 0 6px" }}>{problem}</p>}
          <div style={{ display: "flex", gap: 6 }}><button style={small} onClick={() => setForm(null)}>Cancel</button><button style={{ ...primaryBtn, padding: "4px 10px", fontSize: 12 }} disabled={!!problem || appoint.isPending} onClick={() => appoint.mutate()}>Save proxy</button></div>
        </div>
      )}
      <p style={{ fontSize: 11, color: "#64748B", margin: "6px 0 0" }}>A proxy lapses if the member who gave it attends. It cannot be revoked once it has been used to vote.</p>
    </div>
  )
}

/** Inside the votes window: one row per live proxy, with the holder's vote on this decision. Same save-and-count rules as members' votes. */
export function ProxyVotesSection({ base, canManage, onChanged }: { base: string; canManage: boolean; onChanged: () => void }) {
  const qc = useQueryClient()
  const url = `${base}/proxy-votes`
  const key = ["lc-com", "proxy-votes", base]
  const { data } = useQuery<ProxyVoting>({ queryKey: key, queryFn: async () => one(await apiClient.get(url)) as ProxyVoting })
  const [draft, setDraft] = useState<Record<string, Choice>>({})
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.put(url, { votes: Object.entries(draft).map(([proxyId, choice]) => ({ proxyId, choice })) }),
    onSuccess: () => { setDraft({}); setErr(""); qc.invalidateQueries({ queryKey: key }); onChanged() }, onError: (e: unknown) => setErr(errMsg(e, "That did not work.")),
  })
  if (!data || data.voters.length === 0) return null
  const editable = data.open && data.canVote && canManage
  return (
    <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, margin: "10px 0", overflow: "hidden" }}>
      <div style={{ padding: "6px 12px", background: "#F8FAFC", fontSize: 12, fontWeight: 700 }}>Votes by proxy</div>
      {data.voters.map(v => {
        const current = draft[v.proxyId] ?? v.choice
        return (
          <div key={v.proxyId} style={{ padding: "8px 12px", borderTop: "1px solid #F1F5F9", opacity: v.usable ? 1 : 0.6 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
              <div style={{ fontSize: 13 }}><b>{v.holderName}</b> <span style={{ color: "#64748B" }}>for {v.grantorName}</span></div>
              {!v.usable ? <span style={{ fontSize: 12, color: "#64748B" }}>{v.unusableReason}</span> : editable ? (
                <div style={{ display: "flex", gap: 10 }}>
                  {PROXY_CHOICES.map(c => (
                    <label key={c.value} style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 4, cursor: "pointer" }}>
                      <input type="radio" name={`proxy-${v.proxyId}`} checked={current === c.value} onChange={() => setDraft(d => ({ ...d, [v.proxyId]: c.value }))} />{c.label}
                    </label>
                  ))}
                </div>
              ) : <span style={{ fontSize: 12, fontWeight: 600, color: "#334155" }}>{v.choice ? PROXY_CHOICES.find(c => c.value === v.choice)?.label : "No vote recorded"}</span>}
            </div>
          </div>
        )
      })}
      {err && <div style={{ padding: "0 12px 8px" }}><Err m={err} /></div>}
      {editable && Object.keys(draft).length > 0 && <div style={{ padding: "6px 12px", display: "flex", justifyContent: "flex-end" }}><button style={primaryBtn} disabled={save.isPending} onClick={() => save.mutate()}>Save proxy votes</button></div>}
    </div>
  )
}
