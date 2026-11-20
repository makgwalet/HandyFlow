// src/pages/legalcompliance/WrittenResolutionsTab.tsx
//
// Committee decisions taken without a meeting. Two views: "My written resolutions" (anyone on a committee who was sent one) and "All" (committee managers and readers).
// Endpoints (/api/v1/legalcompliance/written-resolutions): GET /mine · POST /{id}/respond · GET "" · GET /{id} · POST "" · POST /{id}/members/{memberId}/record · POST /{id}/close · POST /{id}/cancel
//
// While a resolution is open, managers see who has answered but not how. It closes when everyone has answered or the deadline passes (automatically overnight).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

type Choice = "FOR" | "AGAINST" | "ABSTAIN"
type Status = "OPEN" | "CARRIED" | "NOT_CARRIED" | "CANCELLED"
interface Mine {
  resolutionId: string; reference: string; title: string; text: string; thresholdText: string; deadline: string; status: Status; open: boolean
  myChoice: Choice | null; myReason: string | null; respondedAt: string | null
}
interface View {
  id: string; committeeId: string; committeeName: string; reference: string; title: string; text: string; threshold: string; thresholdText: string; deadline: string; status: Status
  circulatedOn: string; closedOn: string | null; closedAutomatically: boolean; cancelReason: string | null; memberCount: number; answered: number
  votesFor: number | null; against: number | null; abstained: number | null; noResponse: number | null; canClose: boolean; closeProblem: string | null; pastDeadline: boolean
}
interface Row { memberId: string; name: string; hasAccount: boolean; answered: boolean; respondedAt: string | null; choice: Choice | null; reason: string | null; recordedByName: string | null; howReceived: string | null }
interface Detail { resolution: View; members: Row[] }
interface CommitteeOpt { id: string; name: string; status: string }

const STATUS: Record<Status, { label: string; color: string; bg: string }> = {
  OPEN: { label: "Open", color: "#1D4ED8", bg: "#DBEAFE" }, CARRIED: { label: "Carried", color: "#166534", bg: "#DCFCE7" },
  NOT_CARRIED: { label: "Not carried", color: "#991B1B", bg: "#FEE2E2" }, CANCELLED: { label: "Cancelled", color: "#475569", bg: "#E2E8F0" },
}
const CHOICES: { value: Choice; label: string }[] = [{ value: "FOR", label: "For" }, { value: "AGAINST", label: "Against" }, { value: "ABSTAIN", label: "Abstain" }]
const label = (c: Choice | null) => CHOICES.find(x => x.value === c)?.label ?? "—"
const base = "/api/v1/legalcompliance/written-resolutions"

export default function WrittenResolutionsTab() {
  const qc = useQueryClient()
  const admin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const governance = usePermission("LEGALCOMPLIANCE_GOVERNANCE_MANAGE")
  const readPerm = usePermission("LEGALCOMPLIANCE_READ")
  const managePerm = usePermission("LEGALCOMPLIANCE_MANAGE")
  const manage = governance || admin
  const canRead = readPerm || managePerm || manage

  const [view, setView] = useState<"mine" | "all">("mine")
  const [openMine, setOpenMine] = useState<string | null>(null)
  const [openAll, setOpenAll] = useState<string | null>(null)
  const [answer, setAnswer] = useState<{ choice: Choice | null; reason: string }>({ choice: null, reason: "" })
  const [circulate, setCirculate] = useState<{ committeeId: string; title: string; text: string; threshold: string; deadline: string } | null>(null)
  const [recording, setRecording] = useState<{ res: View; row: Row; choice: Choice | null; reason: string; how: string } | null>(null)
  const [cancelling, setCancelling] = useState<{ res: View; reason: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: mine = [], isLoading } = useQuery<Mine[]>({ queryKey: ["lc-written", "mine"], queryFn: async () => unwrap(await apiClient.get(`${base}/mine`)) as Mine[] })
  const { data: all = [] } = useQuery<View[]>({ queryKey: ["lc-written", "all"], enabled: canRead && view === "all", queryFn: async () => unwrap(await apiClient.get(base)) as View[] })
  const { data: detail } = useQuery<Detail>({ queryKey: ["lc-written", "detail", openAll], enabled: !!openAll, queryFn: async () => one(await apiClient.get(`${base}/${openAll}`)) as Detail })
  const { data: committees = [] } = useQuery<CommitteeOpt[]>({ queryKey: ["lc-com", "options"], enabled: !!circulate, queryFn: async () => (unwrap(await apiClient.get("/api/v1/legalcompliance/committees")) as CommitteeOpt[]).filter(c => c.status === "ACTIVE") })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-written"] }); setApiError("") }
  const fail = (fallback: string) => (e: unknown) => setApiError(errMsg(e, fallback))
  const respond = useMutation({
    mutationFn: ({ id }: { id: string }) => apiClient.post(`${base}/${id}/respond`, { choice: answer.choice, reason: answer.reason.trim() || null }),
    onSuccess: () => { refresh(); setNotice("Your answer was recorded. You can change it until the deadline.") }, onError: fail("Failed to record your answer"),
  })
  const doCirculate = useMutation({
    mutationFn: (c: NonNullable<typeof circulate>) => apiClient.post(base, { committeeId: c.committeeId, title: c.title.trim(), text: c.text.trim(), threshold: c.threshold, deadline: c.deadline }),
    onSuccess: () => { refresh(); setCirculate(null); setNotice("Written resolution circulated to the committee") }, onError: fail("Failed to circulate"),
  })
  const doRecord = useMutation({
    mutationFn: (r: NonNullable<typeof recording>) => apiClient.post(`${base}/${r.res.id}/members/${r.row.memberId}/record`, { choice: r.choice, reason: r.reason.trim() || null, howReceived: r.how.trim() }),
    onSuccess: () => { refresh(); setRecording(null); setNotice("Answer recorded") }, onError: fail("Failed to record the answer"),
  })
  const doClose = useMutation({
    mutationFn: (id: string) => apiClient.post(`${base}/${id}/close`),
    onSuccess: () => { refresh(); setNotice("Written resolution closed") }, onError: fail("Failed to close"),
  })
  const doCancel = useMutation({
    mutationFn: (c: { id: string; reason: string }) => apiClient.post(`${base}/${c.id}/cancel`, { reason: c.reason.trim() }),
    onSuccess: () => { refresh(); setCancelling(null); setNotice("Written resolution cancelled") }, onError: fail("Failed to cancel"),
  })

  const waiting = mine.filter(m => m.open && m.myChoice === null).length
  const tab = (id: "mine" | "all", text: string) => (
    <button onClick={() => { setView(id); setApiError(""); setNotice("") }} style={{ ...(view === id ? primaryBtn : ghostBtn), padding: "6px 14px" }}>{text}</button>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 14, alignItems: "center" }}>
        {tab("mine", `My written resolutions${waiting ? ` (${waiting})` : ""}`)}{canRead && tab("all", "All")}
        {manage && view === "all" && <button style={{ ...primaryBtn, marginLeft: "auto" }} onClick={() => { setApiError(""); setCirculate({ committeeId: "", title: "", text: "", threshold: "MAJORITY_OF_ALL", deadline: "" }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Circulate a resolution</button>}
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !circulate && !recording && !cancelling && <Err m={apiError} />}

      {view === "mine" && (isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : mine.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No written resolutions have been sent to you.</p> : mine.map(m => {
        const open = openMine === m.resolutionId, st = STATUS[m.status]
        return (
          <div key={m.resolutionId} style={{ border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setOpenMine(open ? null : m.resolutionId); setAnswer({ choice: m.myChoice, reason: m.myReason ?? "" }); setApiError(""); setNotice("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{m.reference} — {m.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>answer by {fmtDate(m.deadline)}{m.myChoice ? ` · you answered: ${label(m.myChoice)}` : m.open ? " · waiting for your answer" : ""}</div>
              </div>
              <Badge text={st.label} color={st.color} bg={st.bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <div style={{ whiteSpace: "pre-wrap", border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, background: "#F8FAFC", maxHeight: 280, overflow: "auto", color: "#0F172A" }}>{m.text}</div>
                <p style={{ fontSize: 12 }}>{m.thresholdText}.</p>
                {m.respondedAt && <p style={{ fontSize: 12, marginTop: 0 }}>You answered {label(m.myChoice)} on {fmtDateTime(m.respondedAt)}.</p>}
                {m.open ? (
                  <>
                    <div style={{ display: "flex", gap: 14, marginBottom: 8 }}>
                      {CHOICES.map(c => (
                        <label key={c.value} style={{ fontSize: 13, display: "flex", alignItems: "center", gap: 4, cursor: "pointer" }}>
                          <input type="radio" name={`ans-${m.resolutionId}`} checked={answer.choice === c.value} onChange={() => setAnswer({ ...answer, choice: c.value })} />{c.label}
                        </label>
                      ))}
                    </div>
                    <Field l="Comment (optional)"><input style={input} maxLength={500} value={answer.reason} onChange={e => setAnswer({ ...answer, reason: e.target.value })} /></Field>
                    <button style={primaryBtn} disabled={!answer.choice || respond.isPending} onClick={() => respond.mutate({ id: m.resolutionId })}>{m.myChoice ? "Change my answer" : "Send my answer"}</button>
                  </>
                ) : <p style={{ fontSize: 12, marginBottom: 0 }}>This written resolution is closed.</p>}
              </div>
            )}
          </div>
        )
      }))}

      {view === "all" && (all.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No written resolutions yet.</p> : all.map(w => {
        const open = openAll === w.id, st = STATUS[w.status]
        const d = open && detail?.resolution.id === w.id ? detail : null
        return (
          <div key={w.id} style={{ border: `1px solid ${w.pastDeadline ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setOpenAll(open ? null : w.id); setApiError(""); setNotice("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{w.reference} — {w.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{w.committeeName} · answers by {fmtDate(w.deadline)} · {w.answered} of {w.memberCount} answered{w.status === "CARRIED" || w.status === "NOT_CARRIED" ? ` · ${w.votesFor} for, ${w.against} against, ${w.abstained} abstained, ${w.noResponse} no response` : ""}</div>
              </div>
              <Badge text={st.label} color={st.color} bg={st.bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <div style={{ whiteSpace: "pre-wrap", border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, background: "#F8FAFC", maxHeight: 200, overflow: "auto", color: "#0F172A" }}>{w.text}</div>
                <p style={{ fontSize: 12 }}>{w.thresholdText}.{w.closedAutomatically ? " Closed automatically at the deadline." : ""}{w.cancelReason ? ` Cancelled: ${w.cancelReason}` : ""}</p>
                {w.status === "OPEN" && (
                  <p style={{ fontSize: 12, marginTop: 0 }}>You can see who has answered, not how, until it is closed.{w.closeProblem ? ` ${w.closeProblem}.` : ""}</p>
                )}
                {manage && w.status === "OPEN" && (
                  <div style={{ display: "flex", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
                    <button style={primaryBtn} disabled={!w.canClose || doClose.isPending} title={w.closeProblem ?? "Count the answers and fix the result"} onClick={() => { setApiError(""); doClose.mutate(w.id) }}>Close and count</button>
                    <button style={{ ...ghostBtn, color: "#991B1B" }} onClick={() => { setApiError(""); setCancelling({ res: w, reason: "" }) }}>Cancel</button>
                  </div>
                )}
                {apiError && !recording && !cancelling && <Err m={apiError} />}
                {d && (
                  <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                    <thead><tr style={{ textAlign: "left", color: "#64748B" }}><th style={{ padding: 4 }}>Member</th><th>Answered</th><th>Answer</th><th></th></tr></thead>
                    <tbody>{d.members.map(r => (
                      <tr key={r.memberId} style={{ borderTop: "1px solid #F1F5F9" }}>
                        <td style={{ padding: 4 }}>{r.name}{!r.hasAccount ? " (no account)" : ""}</td>
                        <td>{r.answered ? fmtDateTime(r.respondedAt) : "Not yet"}{r.recordedByName ? ` · recorded by ${r.recordedByName}: ${r.howReceived}` : ""}</td>
                        <td>{w.status === "OPEN" ? (r.answered ? "Hidden until closed" : "—") : `${label(r.choice)}${r.reason ? ` — ${r.reason}` : ""}`}</td>
                        <td>{manage && w.status === "OPEN" && !r.hasAccount && <button style={{ ...ghostBtn, padding: "2px 8px", fontSize: 12 }} onClick={() => { setApiError(""); setRecording({ res: w, row: r, choice: null, reason: "", how: "" }) }}>{r.answered ? "Re-record" : "Record answer"}</button>}</td>
                      </tr>
                    ))}</tbody>
                  </table>
                )}
              </div>
            )}
          </div>
        )
      }))}

      {circulate && (
        <Modal title="Circulate a written resolution" onClose={() => setCirculate(null)} wide>
          <Err m={apiError} />
          <Field l="Committee *"><select style={input} value={circulate.committeeId} onChange={e => setCirculate({ ...circulate, committeeId: e.target.value })}><option value="">Choose…</option>{committees.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
          <Field l="Title *"><input style={input} maxLength={255} value={circulate.title} onChange={e => setCirculate({ ...circulate, title: e.target.value })} /></Field>
          <Field l="The resolution, written out in full *"><textarea style={{ ...input, minHeight: 120 }} value={circulate.text} onChange={e => setCirculate({ ...circulate, text: e.target.value })} /></Field>
          <Field l="How it is approved *">
            <select style={input} value={circulate.threshold} onChange={e => setCirculate({ ...circulate, threshold: e.target.value })}>
              <option value="MAJORITY_OF_ALL">Majority of all members (more than half of everyone must be for)</option>
              <option value="UNANIMOUS">Unanimous (every member must be for)</option>
            </select>
          </Field>
          <Field l="Members must answer by *"><input type="date" style={input} value={circulate.deadline} onChange={e => setCirculate({ ...circulate, deadline: e.target.value })} /></Field>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>It goes to the committee's voting members as they are today (observers and former members are not included). Silence, abstentions and a tie all count against it. Check your charter allows decisions without a meeting, and by this rule, before relying on the result.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setCirculate(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doCirculate.isPending || !circulate.committeeId || !circulate.title.trim() || circulate.text.trim().length < 15 || !circulate.deadline} onClick={() => doCirculate.mutate(circulate)}>Circulate</button>
          </div>
        </Modal>
      )}

      {recording && (
        <Modal title={`Record ${recording.row.name}'s answer`} onClose={() => setRecording(null)}>
          <Err m={apiError} />
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>{recording.row.name} has no user account, so you record what they sent you and how it reached you.</p>
          <div style={{ display: "flex", gap: 14, marginBottom: 8 }}>
            {CHOICES.map(c => (
              <label key={c.value} style={{ fontSize: 13, display: "flex", alignItems: "center", gap: 4, cursor: "pointer" }}>
                <input type="radio" name="rec" checked={recording.choice === c.value} onChange={() => setRecording({ ...recording, choice: c.value })} />{c.label}
              </label>
            ))}
          </div>
          <Field l="How was it received? * (at least 10 characters)"><input style={input} maxLength={500} value={recording.how} onChange={e => setRecording({ ...recording, how: e.target.value })} placeholder="e.g. Email from the member, 18 Nov 2026" /></Field>
          <Field l="Their comment (optional)"><input style={input} maxLength={500} value={recording.reason} onChange={e => setRecording({ ...recording, reason: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setRecording(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doRecord.isPending || !recording.choice || recording.how.trim().length < 10} onClick={() => doRecord.mutate(recording)}>Record</button>
          </div>
        </Modal>
      )}

      {cancelling && (
        <Modal title={`Cancel ${cancelling.res.reference}`} onClose={() => setCancelling(null)}>
          <Err m={apiError} />
          <Field l="Reason * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={cancelling.reason} onChange={e => setCancelling({ ...cancelling, reason: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setCancelling(null)}>Keep it open</button>
            <button style={{ ...primaryBtn, background: "#B91C1C" }} disabled={doCancel.isPending || cancelling.reason.trim().length < 15} onClick={() => doCancel.mutate({ id: cancelling.res.id, reason: cancelling.reason })}>Cancel resolution</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
