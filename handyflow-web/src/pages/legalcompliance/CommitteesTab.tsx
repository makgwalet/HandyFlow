// src/pages/legalcompliance/CommitteesTab.tsx
//
// Governance committees. Endpoints (/api/v1/legalcompliance/committees):
//   GET / (status) · /summary · /actions?open= · /{id} · /{id}/meetings/{mid} · POST / · PUT /{id} · POST /{id}/charter-review|dissolve · GET|POST /{id}/charter (rules: see CharterRulesPanel)
//   POST /{id}/members · POST /{id}/members/{memberId}/end · POST /{id}/meetings · PUT /{id}/meetings/{mid} · POST .../cancel|hold|minutes|minutes/approve
//   POST|PUT|DELETE /{id}/meetings/{mid}/resolutions[/{rid}] · POST .../resolutions/{rid}/complete
//   GET /{id}/meetings/{mid}/ballots · votes on a decision: see ResolutionVotesModal
// Quorum is worked out from who attended (observers do not count). Decisions and actions can be recorded only for a meeting held with a quorum, and not after the
// minutes are approved. The person who wrote the minutes cannot approve them.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"
import ResolutionVotesModal from "./ResolutionVotesModal"
import { MeetingProxies } from "./ProxyPanels"
import { CharterRulesPanel } from "./CharterRulesPanel"

const base = "/api/v1/legalcompliance/committees"
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()
const MIN = 15
const TYPES = ["BOARD", "AUDIT", "RISK", "SOCIAL_ETHICS", "COMPLIANCE", "OTHER"]
const ROLES = ["CHAIR", "MEMBER", "SECRETARY", "OBSERVER"]
const today = () => new Date().toISOString().slice(0, 10)
const small = { ...ghostBtn, padding: "4px 10px", fontSize: 12 }

interface Com {
  id: string; reference: string; name: string; committeeType: string; charter: string | null; meetingFrequencyMonths: number; quorumMin: number; charterReviewMonths: number; lastCharterReview: string | null
  status: string; dissolvedReason: string | null; activeMembers: number; qualifyingMembers: number; hasChair: boolean; lastHeld: string | null; nextScheduled: string | null; meetingOverdue: boolean
  charterReviewOverdue: boolean; openActions: number; overdueActions: number; minutesPending: number
}
interface Member { id: string; memberName: string; memberRole: string; appointedOn: string; endedOn: string | null; active: boolean }
interface Meeting {
  id: string; committeeId: string; meetingDate: string; title: string; agenda: string | null; status: string; attendees: string[]; presentCount: number | null; quorumMet: boolean | null; cancelReason: string | null
  minutes: string | null; minutesStatus: string; minutesByName: string | null; minutesApprovedByName: string | null; minutesApprovedOn: string | null; canRecordResolutions: boolean
}
interface Res { id: string; kind: string; description: string; ownerName: string | null; dueDate: string | null; status: string; completedOn: string | null; note: string | null; overdue: boolean; committeeName: string | null; meetingDate: string | null }
interface Ballot { resolutionId: string; type: string; status: string; outcome: string | null; tally: { votesFor: number; against: number; abstained: number; recused: number } }
interface Detail { committee: Com; members: Member[]; meetings: Meeting[] }
interface MeetingDetail { meeting: Meeting; resolutions: Res[] }
interface Summary { committees: number; meetingsOverdue: number; charterReviewOverdue: number; minutesPending: number; actionsOpen: number; actionsOverdue: number; withoutQuorumCapacity: number }

const blank = { id: "", name: "", committeeType: "RISK", charter: "", meetingFrequencyMonths: "3", quorumMin: "2", charterReviewMonths: "12" }
type Reason = { kind: "dissolve" | "cancel"; c: Com; m?: Meeting; a: string }

export default function CommitteesTab() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_GOVERNANCE_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const canRecordCharter = usePermission("LEGALCOMPLIANCE_CHARTER_MANAGE") || hasAdmin

  const [view, setView] = useState<"committees" | "actions">("committees")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [openMeeting, setOpenMeeting] = useState<string | null>(null)
  const [form, setForm] = useState<typeof blank | null>(null)
  const [member, setMember] = useState<{ c: Com; name: string; role: string; on: string } | null>(null)
  const [endM, setEndM] = useState<{ c: Com; m: Member; on: string } | null>(null)
  const [meet, setMeet] = useState<{ c: Com; id: string; date: string; title: string; agenda: string } | null>(null)
  const [hold, setHold] = useState<{ c: Com; m: Meeting; ids: string[] } | null>(null)
  const [minutes, setMinutes] = useState<{ c: Com; m: Meeting; text: string } | null>(null)
  const [res, setRes] = useState<{ c: Com; m: Meeting; id: string; kind: string; description: string; owner: string; due: string } | null>(null)
  const [done, setDone] = useState<{ c: Com; m: Meeting; r: Res; on: string; note: string } | null>(null)
  const [charter, setCharter] = useState<{ c: Com; on: string } | null>(null)
  const [reason, setReason] = useState<Reason | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")
  const [voting, setVoting] = useState<{ c: Com; m: Meeting; r: Res } | null>(null)

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-com", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: list = [], isLoading } = useQuery<Com[]>({ queryKey: ["lc-com", "list"], queryFn: async () => unwrap(await apiClient.get(base)) })
  const { data: actions = [] } = useQuery<Res[]>({ queryKey: ["lc-com", "actions"], enabled: view === "actions", queryFn: async () => unwrap(await apiClient.get(`${base}/actions`, { params: { open: true } })) })
  const { data: detail } = useQuery<Detail>({ queryKey: ["lc-com", "detail", expanded], enabled: !!expanded, queryFn: async () => one(await apiClient.get(`${base}/${expanded}`)) })
  const { data: md } = useQuery<MeetingDetail>({ queryKey: ["lc-com", "meeting", expanded, openMeeting], enabled: !!expanded && !!openMeeting, queryFn: async () => one(await apiClient.get(`${base}/${expanded}/meetings/${openMeeting}`)) })
  const { data: ballots = [] } = useQuery<Ballot[]>({ queryKey: ["lc-com", "ballots", expanded, openMeeting], enabled: !!expanded && !!openMeeting, queryFn: async () => unwrap(await apiClient.get(`${base}/${expanded}/meetings/${openMeeting}/ballots`)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-com"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }
  const post = (url: string, body?: unknown) => apiClient.post(`${base}/${url}`, body)

  const save = useMutation({
    mutationFn: (f: typeof blank) => { const b = { name: f.name.trim(), committeeType: f.committeeType, charter: f.charter.trim() || null, meetingFrequencyMonths: +f.meetingFrequencyMonths || 0, quorumMin: +f.quorumMin || 0, charterReviewMonths: +f.charterReviewMonths || 0 }; return f.id ? apiClient.put(`${base}/${f.id}`, b) : apiClient.post(base, b) },
    onSuccess: ok("Saved", () => setForm(null)), onError: fail("Failed to save committee"),
  })
  const addMember = useMutation({ mutationFn: (x: NonNullable<typeof member>) => post(`${x.c.id}/members`, { memberName: x.name.trim(), memberRole: x.role, appointedOn: x.on }), onSuccess: ok("Member added", () => setMember(null)), onError: fail("Failed to add member") })
  const endMember = useMutation({ mutationFn: (x: NonNullable<typeof endM>) => post(`${x.c.id}/members/${x.m.id}/end`, { endedOn: x.on }), onSuccess: ok("Membership ended", () => setEndM(null)), onError: fail("Failed to end membership") })
  const saveMeet = useMutation({
    mutationFn: (x: NonNullable<typeof meet>) => { const b = { meetingDate: x.date, title: x.title.trim(), agenda: x.agenda.trim() || null }; return x.id ? apiClient.put(`${base}/${x.c.id}/meetings/${x.id}`, b) : post(`${x.c.id}/meetings`, b) },
    onSuccess: ok("Meeting saved", () => setMeet(null)), onError: fail("Failed to save meeting"),
  })
  const doHold = useMutation({ mutationFn: (x: NonNullable<typeof hold>) => post(`${x.c.id}/meetings/${x.m.id}/hold`, { attendeeIds: x.ids }), onSuccess: ok("Meeting recorded", () => setHold(null)), onError: fail("Failed to record meeting") })
  const saveMinutes = useMutation({ mutationFn: (x: NonNullable<typeof minutes>) => post(`${x.c.id}/meetings/${x.m.id}/minutes`, { minutes: x.text.trim() }), onSuccess: ok("Minutes saved", () => setMinutes(null)), onError: fail("Failed to save minutes") })
  const approve = useMutation({ mutationFn: ({ c, m }: { c: Com; m: Meeting }) => post(`${c.id}/meetings/${m.id}/minutes/approve`), onSuccess: ok("Minutes approved"), onError: fail("Cannot approve minutes") })
  const saveRes = useMutation({
    mutationFn: (x: NonNullable<typeof res>) => { const b = { kind: x.kind, description: x.description.trim(), ownerName: x.kind === "ACTION" ? x.owner.trim() : null, dueDate: x.kind === "ACTION" ? x.due : null }; return x.id ? apiClient.put(`${base}/${x.c.id}/meetings/${x.m.id}/resolutions/${x.id}`, b) : post(`${x.c.id}/meetings/${x.m.id}/resolutions`, b) },
    onSuccess: ok("Saved", () => setRes(null)), onError: fail("Failed to save"),
  })
  const removeRes = useMutation({ mutationFn: ({ c, m, r }: { c: Com; m: Meeting; r: Res }) => apiClient.delete(`${base}/${c.id}/meetings/${m.id}/resolutions/${r.id}`), onSuccess: ok("Removed"), onError: fail("Failed to remove") })
  const complete = useMutation({ mutationFn: (x: NonNullable<typeof done>) => post(`${x.c.id}/meetings/${x.m.id}/resolutions/${x.r.id}/complete`, { completedOn: x.on, note: x.note.trim() || null }), onSuccess: ok("Action completed", () => setDone(null)), onError: fail("Failed to complete action") })
  const reviewCharter = useMutation({ mutationFn: (x: NonNullable<typeof charter>) => post(`${x.c.id}/charter-review`, { reviewedOn: x.on }), onSuccess: ok("Charter review recorded", () => setCharter(null)), onError: fail("Failed to record review") })
  const doReason = useMutation({ mutationFn: (x: Reason) => x.kind === "dissolve" ? post(`${x.c.id}/dissolve`, { reason: x.a.trim() }) : post(`${x.c.id}/meetings/${x.m!.id}/cancel`, { reason: x.a.trim() }), onSuccess: ok("Saved", () => setReason(null)), onError: fail("Action failed") })

  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 120, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const bad = (n?: number) => (n ? "#991B1B" : undefined)
  const modalOpen = form || member || endM || meet || hold || minutes || res || done || charter || reason
  const actionRow = (r: Res, extra?: React.ReactNode) => (
    <div key={r.id} style={{ border: "1px solid #F1F5F9", borderRadius: 8, padding: "8px 10px", marginBottom: 6 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <div style={{ flex: 1, minWidth: 160 }}><Badge text={r.kind === "ACTION" ? "Action" : "Decision"} color="#4338CA" bg="#E0E7FF" /> {r.description}
          <span style={{ fontSize: 12, color: "#64748B" }}>{r.ownerName ? ` · ${r.ownerName}` : ""}{r.dueDate ? ` · due ${fmtDate(r.dueDate)}` : ""}{r.committeeName ? ` · ${r.committeeName}` : ""}{r.completedOn ? ` · done ${fmtDate(r.completedOn)}` : ""}</span></div>
        {r.overdue && <Badge text="Overdue" color="#991B1B" bg="#FEE2E2" />}{r.kind === "ACTION" && <Badge text={pretty(r.status)} color={r.status === "DONE" ? "#166534" : "#475569"} bg={r.status === "DONE" ? "#DCFCE7" : "#F1F5F9"} />}
      </div>
      {r.note && <div style={{ fontSize: 12, color: "#64748B" }}>{r.note}</div>}{extra}
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Active committees", summary?.committees)}{card("Overdue to meet", summary?.meetingsOverdue, bad(summary?.meetingsOverdue))}{card("Charter review overdue", summary?.charterReviewOverdue, bad(summary?.charterReviewOverdue))}
        {card("Minutes awaiting approval", summary?.minutesPending, "#92400E")}{card("Open actions", summary?.actionsOpen)}{card("Actions overdue", summary?.actionsOverdue, bad(summary?.actionsOverdue))}{card("Cannot reach quorum", summary?.withoutQuorumCapacity, bad(summary?.withoutQuorumCapacity))}
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12 }}>
        {canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ ...blank }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Add committee</button>}
        <button style={view === "committees" ? primaryBtn : ghostBtn} onClick={() => setView("committees")}>Committees</button>
        <button style={view === "actions" ? primaryBtn : ghostBtn} onClick={() => setView("actions")}>Open actions</button>
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !modalOpen && <Err m={apiError} />}

      {view === "actions" ? (actions.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No open actions.</p> : actions.map(a => actionRow(a))) :
        isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : list.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No committees recorded.</p> : list.map(c => {
        const open = expanded === c.id
        const d = open && detail?.committee.id === c.id ? detail : null
        const live = c.status === "ACTIVE"
        const flagged = c.meetingOverdue || c.charterReviewOverdue || c.overdueActions > 0 || c.qualifyingMembers < c.quorumMin
        return (
          <div key={c.id} style={{ border: `1px solid ${flagged && live ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : c.id); setOpenMeeting(null); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.reference} — {c.name}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{pretty(c.committeeType)} · meets every {c.meetingFrequencyMonths} month(s) · quorum {c.quorumMin} · {c.activeMembers} member(s){c.lastHeld ? ` · last met ${fmtDate(c.lastHeld)}` : " · has not met"}{c.nextScheduled ? ` · next ${fmtDate(c.nextScheduled)}` : ""}</div></div>
              {live && c.meetingOverdue && <Badge text="Overdue to meet" color="#991B1B" bg="#FEE2E2" />}{live && c.qualifyingMembers < c.quorumMin && <Badge text="Too few members for quorum" color="#991B1B" bg="#FEE2E2" />}
              {live && c.overdueActions > 0 && <Badge text={`${c.overdueActions} action(s) overdue`} color="#991B1B" bg="#FEE2E2" />}{live && c.minutesPending > 0 && <Badge text="Minutes to approve" color="#92400E" bg="#FEF3C7" />}
              <Badge text={live ? "Active" : "Dissolved"} color={live ? "#166534" : "#475569"} bg={live ? "#DCFCE7" : "#E2E8F0"} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {c.charter && <p style={{ marginTop: 0, whiteSpace: "pre-wrap" }}>{c.charter}</p>}
                <p style={{ fontSize: 12 }}>Charter reviewed {c.lastCharterReview ? fmtDate(c.lastCharterReview) : "never"} · reviewed every {c.charterReviewMonths} months{c.charterReviewOverdue ? " · overdue" : ""}{!c.hasChair && live ? " · no chair appointed" : ""}{c.dissolvedReason ? ` · dissolved: ${c.dissolvedReason}` : ""}</p>
                <CharterRulesPanel committeeId={c.id} canRecord={canRecordCharter} live={live} />

                <div style={{ fontWeight: 700, margin: "8px 0 4px" }}>Members</div>
                {!d ? <p style={{ fontSize: 12 }}>Loading…</p> : d.members.length === 0 ? <p style={{ fontSize: 12 }}>No members yet.</p> : d.members.map(m => (
                  <div key={m.id} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, padding: "3px 0", opacity: m.active ? 1 : 0.6 }}>
                    <span style={{ flex: 1 }}>{m.memberName} <span style={{ fontSize: 12, color: "#64748B" }}>· {pretty(m.memberRole)} · since {fmtDate(m.appointedOn)}{m.endedOn ? ` · left ${fmtDate(m.endedOn)}` : ""}</span></span>
                    {canManage && live && m.active && <button style={small} onClick={() => { setApiError(""); setEndM({ c, m, on: today() }) }}>End</button>}
                  </div>
                ))}
                {canManage && live && <button style={{ ...small, marginTop: 4 }} onClick={() => { setApiError(""); setMember({ c, name: "", role: "MEMBER", on: today() }) }}>Add member</button>}

                <div style={{ fontWeight: 700, margin: "14px 0 4px" }}>Meetings</div>
                {!d ? null : d.meetings.length === 0 ? <p style={{ fontSize: 12 }}>No meetings yet.</p> : d.meetings.map(m => {
                  const mo = openMeeting === m.id
                  const x = mo && md?.meeting.id === m.id ? md : null
                  const mm = x?.meeting ?? m
                  return (
                    <div key={m.id} style={{ border: "1px solid #F1F5F9", borderRadius: 8, marginBottom: 6 }}>
                      <button onClick={() => { setOpenMeeting(mo ? null : m.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 8, alignItems: "center", padding: "8px 10px", width: "calc(100% - 20px)" }}>
                        <span style={{ flex: 1 }}><b>{m.title}</b> <span style={{ fontSize: 12, color: "#64748B" }}>· {fmtDate(m.meetingDate)}</span></span>
                        {m.status === "HELD" && <Badge text={m.quorumMet ? "Quorum met" : "No quorum"} color={m.quorumMet ? "#166534" : "#991B1B"} bg={m.quorumMet ? "#DCFCE7" : "#FEE2E2"} />}
                        {m.minutesStatus !== "NONE" && <Badge text={`Minutes ${pretty(m.minutesStatus)}`} color={m.minutesStatus === "APPROVED" ? "#166534" : "#92400E"} bg={m.minutesStatus === "APPROVED" ? "#DCFCE7" : "#FEF3C7"} />}
                        <Badge text={pretty(m.status)} color="#475569" bg="#F1F5F9" />
                      </button>
                      {mo && (
                        <div style={{ padding: "0 10px 10px", fontSize: 13 }}>
                          {mm.agenda && <p style={{ whiteSpace: "pre-wrap", margin: "0 0 6px" }}>{mm.agenda}</p>}
                          {d && (mm.status === "SCHEDULED" || mm.status === "HELD") && <MeetingProxies committeeId={c.id} meeting={{ id: mm.id, meetingDate: mm.meetingDate, status: mm.status }} members={d.members} canManage={canManage && live} />}
                          {mm.cancelReason && <p style={{ color: "#64748B", margin: "0 0 6px" }}>Cancelled: {mm.cancelReason}</p>}
                          {mm.status === "HELD" && <p style={{ fontSize: 12, margin: "0 0 6px" }}>Present ({mm.presentCount} counting toward quorum of {c.quorumMin}): {mm.attendees.join(", ")}</p>}
                          {mm.status === "HELD" && !mm.quorumMet && <p style={{ fontSize: 12, color: "#991B1B", margin: "0 0 6px" }}>There was no quorum, so no decisions or actions can be recorded for this meeting.</p>}
                          {mm.minutes && <div style={{ background: "#F8FAFC", borderRadius: 8, padding: 8, whiteSpace: "pre-wrap", marginBottom: 6 }}>{mm.minutes}<div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>Written by {mm.minutesByName}{mm.minutesApprovedByName ? ` · approved by ${mm.minutesApprovedByName} on ${fmtDate(mm.minutesApprovedOn)}` : ""}</div></div>}
                          {x?.resolutions.map(r => actionRow(r, (
                            <div style={{ display: "flex", gap: 6, marginTop: 4 }}>
                              {r.kind === "DECISION" && <button style={small} onClick={() => setVoting({ c, m: mm, r })}>{(() => { const b = ballots.find(x => x.resolutionId === r.id); return !b ? "Votes" : b.status === "CLOSED" ? (b.outcome === "CARRIED" ? "Votes · carried" : "Votes · not carried") : "Votes · open" })()}</button>}
                              {canManage && r.kind === "ACTION" && r.status === "OPEN" && <button style={small} onClick={() => { setApiError(""); setDone({ c, m: mm, r, on: today(), note: "" }) }}>Mark done</button>}
                              {canManage && mm.canRecordResolutions && r.status === "OPEN" && <button style={small} onClick={() => { setApiError(""); setRes({ c, m: mm, id: r.id, kind: r.kind, description: r.description, owner: r.ownerName ?? "", due: r.dueDate ?? "" }) }}>Edit</button>}
                              {canManage && mm.canRecordResolutions && r.status === "OPEN" && <button style={small} onClick={() => { if (confirm("Remove this entry?")) removeRes.mutate({ c, m: mm, r }) }}>Remove</button>}
                            </div>))) }
                          {apiError && <Err m={apiError} />}
                          {canManage && live && (
                            <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
                              {mm.status === "SCHEDULED" && <button style={primaryBtn} onClick={() => { setApiError(""); setHold({ c, m: mm, ids: [] }) }}>Record meeting held</button>}
                              {mm.status === "SCHEDULED" && <button style={ghostBtn} onClick={() => { setApiError(""); setMeet({ c, id: mm.id, date: mm.meetingDate, title: mm.title, agenda: mm.agenda ?? "" }) }}>Edit</button>}
                              {mm.status === "SCHEDULED" && <button style={ghostBtn} onClick={() => { setApiError(""); setReason({ kind: "cancel", c, m: mm, a: "" }) }}>Cancel meeting</button>}
                              {mm.status === "HELD" && mm.minutesStatus !== "APPROVED" && <button style={ghostBtn} onClick={() => { setApiError(""); setMinutes({ c, m: mm, text: mm.minutes ?? "" }) }}>{mm.minutes ? "Edit minutes" : "Write minutes"}</button>}
                              {mm.minutesStatus === "DRAFT" && <button style={primaryBtn} disabled={approve.isPending} onClick={() => approve.mutate({ c, m: mm })}>Approve minutes</button>}
                              {mm.canRecordResolutions && <button style={ghostBtn} onClick={() => { setApiError(""); setRes({ c, m: mm, id: "", kind: "DECISION", description: "", owner: "", due: "" }) }}>Add decision or action</button>}
                            </div>
                          )}
                          {mm.minutesStatus === "DRAFT" && <p style={{ fontSize: 12, color: "#64748B", margin: "6px 0 0" }}>The person who wrote the minutes cannot approve them.</p>}
                        </div>
                      )}
                    </div>
                  )
                })}
                {canManage && live && (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 10 }}>
                    <button style={ghostBtn} onClick={() => { setApiError(""); setMeet({ c, id: "", date: today(), title: "", agenda: "" }) }}>Schedule meeting</button>
                    <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: c.id, name: c.name, committeeType: c.committeeType, charter: c.charter ?? "", meetingFrequencyMonths: String(c.meetingFrequencyMonths), quorumMin: String(c.quorumMin), charterReviewMonths: String(c.charterReviewMonths) }) }}>Edit details</button>
                    <button style={ghostBtn} onClick={() => { setApiError(""); setCharter({ c, on: today() }) }}>Record charter review</button>
                    <button style={ghostBtn} onClick={() => { setApiError(""); setReason({ kind: "dissolve", c, a: "" }) }}>Dissolve</button>
                  </div>
                )}
              </div>
            )}
          </div>
        )
      })}

      {form && (
        <Modal title={form.id ? "Edit committee" : "Add committee"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 2 }}><Field l="Name *"><input style={input} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={form.committeeType} onChange={e => setForm({ ...form, committeeType: e.target.value })}>{TYPES.map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field></div>
          </div>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Meets every (months)"><input type="number" min={1} max={12} style={input} value={form.meetingFrequencyMonths} onChange={e => setForm({ ...form, meetingFrequencyMonths: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Quorum (members present)"><input type="number" min={1} style={input} value={form.quorumMin} onChange={e => setForm({ ...form, quorumMin: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Charter review (months)"><input type="number" min={1} max={60} style={input} value={form.charterReviewMonths} onChange={e => setForm({ ...form, charterReviewMonths: e.target.value })} /></Field></div>
          </div>
          <Field l="Charter / terms of reference"><textarea style={{ ...input, minHeight: 80 }} value={form.charter} onChange={e => setForm({ ...form, charter: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button><button style={primaryBtn} disabled={save.isPending || !form.name.trim()} onClick={() => save.mutate(form)}>Save</button></div>
        </Modal>
      )}
      {member && (
        <Modal title={`Add member — ${member.c.name}`} onClose={() => setMember(null)}>
          <Err m={apiError} />
          <Field l="Name *"><input style={input} value={member.name} onChange={e => setMember({ ...member, name: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Role *"><select style={input} value={member.role} onChange={e => setMember({ ...member, role: e.target.value })}>{ROLES.map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Appointed *"><input type="date" max={today()} style={input} value={member.on} onChange={e => setMember({ ...member, on: e.target.value })} /></Field></div>
          </div>
          <p style={{ fontSize: 12, color: "#64748B" }}>Observers do not count toward quorum. A committee can have only one chair at a time.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setMember(null)}>Cancel</button><button style={primaryBtn} disabled={addMember.isPending || !member.name.trim() || !member.on} onClick={() => addMember.mutate(member)}>Add</button></div>
        </Modal>
      )}
      {endM && (
        <Modal title={`End membership — ${endM.m.memberName}`} onClose={() => setEndM(null)}>
          <Err m={apiError} />
          <Field l="Left on *"><input type="date" max={today()} style={input} value={endM.on} onChange={e => setEndM({ ...endM, on: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setEndM(null)}>Cancel</button><button style={primaryBtn} disabled={endMember.isPending || !endM.on} onClick={() => endMember.mutate(endM)}>End membership</button></div>
        </Modal>
      )}
      {meet && (
        <Modal title={`${meet.id ? "Edit" : "Schedule"} meeting — ${meet.c.name}`} onClose={() => setMeet(null)}>
          <Err m={apiError} />
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Date *"><input type="date" style={input} value={meet.date} onChange={e => setMeet({ ...meet, date: e.target.value })} /></Field></div>
            <div style={{ flex: 2 }}><Field l="Title *"><input style={input} value={meet.title} onChange={e => setMeet({ ...meet, title: e.target.value })} /></Field></div>
          </div>
          <Field l="Agenda"><textarea style={{ ...input, minHeight: 70 }} value={meet.agenda} onChange={e => setMeet({ ...meet, agenda: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setMeet(null)}>Cancel</button><button style={primaryBtn} disabled={saveMeet.isPending || !meet.date || !meet.title.trim()} onClick={() => saveMeet.mutate(meet)}>Save</button></div>
        </Modal>
      )}
      {hold && (() => {
        const eligible = (detail?.members ?? []).filter(m => m.active && m.appointedOn <= hold.m.meetingDate)
        const counted = eligible.filter(m => hold.ids.includes(m.id) && m.memberRole !== "OBSERVER").length
        return (
          <Modal title={`Record meeting held — ${hold.m.title}`} onClose={() => setHold(null)}>
            <Err m={apiError} />
            <p style={{ fontSize: 13, marginTop: 0 }}>Tick everyone who attended.</p>
            {eligible.map(m => <label key={m.id} style={{ display: "flex", gap: 6, fontSize: 13, marginBottom: 4 }}><input type="checkbox" checked={hold.ids.includes(m.id)} onChange={e => setHold({ ...hold, ids: e.target.checked ? [...hold.ids, m.id] : hold.ids.filter(i => i !== m.id) })} />{m.memberName} <span style={{ color: "#64748B" }}>· {pretty(m.memberRole)}{m.memberRole === "OBSERVER" ? " (does not count toward quorum)" : ""}</span></label>)}
            <p style={{ fontSize: 13, fontWeight: 600, color: counted >= hold.c.quorumMin ? "#166534" : "#991B1B" }}>{counted} of {hold.c.quorumMin} needed for a quorum — {counted >= hold.c.quorumMin ? "quorum met" : "no quorum"}</p>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setHold(null)}>Cancel</button><button style={primaryBtn} disabled={doHold.isPending || hold.ids.length === 0} onClick={() => doHold.mutate(hold)}>Record</button></div>
          </Modal>
        )
      })()}
      {minutes && (
        <Modal title={`Minutes — ${minutes.m.title}`} onClose={() => setMinutes(null)}>
          <Err m={apiError} />
          <Field l="Minutes * (at least 30 characters)"><textarea style={{ ...input, minHeight: 200 }} value={minutes.text} onChange={e => setMinutes({ ...minutes, text: e.target.value })} /></Field>
          <p style={{ fontSize: 12, color: "#64748B" }}>Saved as a draft under your name. Someone else must approve them; approved minutes cannot be changed.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setMinutes(null)}>Cancel</button><button style={primaryBtn} disabled={saveMinutes.isPending || minutes.text.trim().length < 30} onClick={() => saveMinutes.mutate(minutes)}>Save draft</button></div>
        </Modal>
      )}
      {res && (
        <Modal title={`${res.id ? "Edit" : "Add"} decision or action — ${res.m.title}`} onClose={() => setRes(null)}>
          <Err m={apiError} />
          <Field l="Type *"><select style={input} value={res.kind} onChange={e => setRes({ ...res, kind: e.target.value })}><option value="DECISION">Decision</option><option value="ACTION">Action</option></select></Field>
          <Field l={`Description * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={res.description} onChange={e => setRes({ ...res, description: e.target.value })} /></Field>
          {res.kind === "ACTION" && (
            <div style={{ display: "flex", gap: 12 }}>
              <div style={{ flex: 2 }}><Field l="Owner *"><input style={input} value={res.owner} onChange={e => setRes({ ...res, owner: e.target.value })} /></Field></div>
              <div style={{ flex: 1 }}><Field l="Due *"><input type="date" min={res.m.meetingDate} style={input} value={res.due} onChange={e => setRes({ ...res, due: e.target.value })} /></Field></div>
            </div>
          )}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setRes(null)}>Cancel</button>
            <button style={primaryBtn} disabled={saveRes.isPending || res.description.trim().length < MIN || (res.kind === "ACTION" && (!res.owner.trim() || !res.due))} onClick={() => saveRes.mutate(res)}>Save</button></div>
        </Modal>
      )}
      {voting && <ResolutionVotesModal committeeId={voting.c.id} meetingId={voting.m.id} resolutionId={voting.r.id} canManage={canManage} onClose={() => { setVoting(null); refresh() }} />}
      {done && (
        <Modal title="Mark action done" onClose={() => setDone(null)}>
          <Err m={apiError} />
          <p style={{ fontSize: 13, color: "#475569", marginTop: 0 }}>{done.r.description}</p>
          <Field l="Completed on *"><input type="date" max={today()} style={input} value={done.on} onChange={e => setDone({ ...done, on: e.target.value })} /></Field>
          <Field l="Note"><textarea style={{ ...input, minHeight: 50 }} value={done.note} onChange={e => setDone({ ...done, note: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setDone(null)}>Cancel</button><button style={primaryBtn} disabled={complete.isPending || !done.on} onClick={() => complete.mutate(done)}>Save</button></div>
        </Modal>
      )}
      {charter && (
        <Modal title={`Charter review — ${charter.c.name}`} onClose={() => setCharter(null)}>
          <Err m={apiError} />
          <Field l="Reviewed on *"><input type="date" max={today()} style={input} value={charter.on} onChange={e => setCharter({ ...charter, on: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setCharter(null)}>Cancel</button><button style={primaryBtn} disabled={reviewCharter.isPending || !charter.on} onClick={() => reviewCharter.mutate(charter)}>Record</button></div>
        </Modal>
      )}
      {reason && (
        <Modal title={reason.kind === "dissolve" ? `Dissolve — ${reason.c.name}` : `Cancel meeting — ${reason.m?.title}`} onClose={() => setReason(null)}>
          <Err m={apiError} />
          <Field l={`Reason * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={reason.a} onChange={e => setReason({ ...reason, a: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setReason(null)}>Back</button><button style={primaryBtn} disabled={doReason.isPending || reason.a.trim().length < MIN} onClick={() => doReason.mutate(reason)}>Confirm</button></div>
        </Modal>
      )}
    </div>
  )
}
