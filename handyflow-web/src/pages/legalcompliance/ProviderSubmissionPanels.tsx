// src/pages/legalcompliance/ProviderSubmissionPanels.tsx
//
// Provider submissions (chunk 32). Three pieces:
//  • ClientSubmissions — inside a provider's client window: send observations, recommendations or information requests to the client, see their answers, withdraw, and ask for the permission.
//  • SubmissionInbox   — the organisation being served: what providers sent, with accept / decline / complete.
//  • GrantsEditor      — the organisation being served: choose which of the things a provider asked for it may do (reading, reports, sending).
// Nothing here edits the client's registers. A client's reply is visible to the provider.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { one, unwrap, input, primaryBtn, ghostBtn, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import {
  AREAS, KINDS, PRIORITIES, STATUS_TONE, canSend, dueText, formProblem, grantsChanged, label, replyValid, toggleGrant,
  type Area, type Kind, type Priority, type Scope, type SubStatus,
} from "./submissionLogic"

const base = "/api/v1/legalcompliance/provider/clients"
const inboxBase = "/api/v1/legalcompliance/provider-submissions"
const accessBase = "/api/v1/legalcompliance/provider-access"

interface Submission {
  id: string; reference: string; relationshipId: string; providerName: string; kind: Kind; area: Area; priority: Priority; title: string; body: string; respondBy: string | null
  submittedBy: string | null; status: SubStatus; response: string | null; respondedBy: string | null; respondedAt: string | null; sentAt: string; overdue: boolean
}
interface Inbox { open: number; items: Submission[] }

const todayIso = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }

function StatusBadge({ s }: { s: Submission }) {
  const t = STATUS_TONE[s.status]
  return (
    <>
      <Badge text={label(s.status)} color={t.color} bg={t.bg} />
      {s.overdue && <Badge text="Reply overdue" color="#B91C1C" bg="#FEE2E2" />}
    </>
  )
}

function SubmissionCard({ s, actions }: { s: Submission; actions?: React.ReactNode }) {
  const due = dueText(s.respondBy, s.overdue)
  return (
    <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: 12, marginBottom: 10 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <b style={{ fontSize: 14 }}>{s.title}</b><StatusBadge s={s} />
        <Badge text={label(s.kind)} color="#3730A3" bg="#E0E7FF" /><Badge text={label(s.area)} color="#475569" bg="#E2E8F0" />
        {s.priority === "HIGH" && <Badge text="High priority" color="#92400E" bg="#FEF3C7" />}
      </div>
      <div style={{ fontSize: 12, color: "#64748B", margin: "4px 0 6px" }}>{s.reference} · sent {fmtDateTime(s.sentAt)}{s.submittedBy ? ` by ${s.submittedBy}` : ""}{due ? ` · ${due}` : ""}</div>
      <p style={{ fontSize: 13, color: "#0F172A", whiteSpace: "pre-wrap", margin: "0 0 6px" }}>{s.body}</p>
      {s.response && <div style={{ background: "#F8FAFC", borderRadius: 8, padding: 8, fontSize: 13, marginBottom: 6 }}><b>Reply{s.respondedBy ? ` from ${s.respondedBy}` : ""}{s.respondedAt ? `, ${fmtDate(s.respondedAt)}` : ""}:</b> {s.response}</div>}
      {actions}
    </div>
  )
}

// ------------------------------------------------------------------------------------------------------------------------ provider side

export function ClientSubmissions({ id, status, granted, requested, canManage }: { id: string; status: string; granted: Scope[]; requested: Scope[]; canManage: boolean }) {
  const qc = useQueryClient()
  const allowed = canSend(status, granted)
  const [open, setOpen] = useState(false)
  const [f, setF] = useState({ kind: "OBSERVATION" as Kind, area: "OTHER" as Area, priority: "NORMAL" as Priority, title: "", body: "", respondBy: "" })
  const [err, setErr] = useState("")
  const [asked, setAsked] = useState(false)
  const key = ["lc-provider", "submissions", id]
  const { data: list = [], isLoading } = useQuery<Submission[]>({ queryKey: key, enabled: allowed, queryFn: async () => unwrap(await apiClient.get(`${base}/${id}/submissions`)), retry: false })
  const fail = (e: unknown) => setErr(errMsg(e, "That did not work"))
  const send = useMutation({
    mutationFn: () => apiClient.post(`${base}/${id}/submissions`, { ...f, respondBy: f.respondBy || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setOpen(false); setErr(""); setF({ ...f, title: "", body: "", respondBy: "" }) }, onError: fail,
  })
  const withdraw = useMutation({ mutationFn: (sid: string) => apiClient.post(`${base}/${id}/submissions/${sid}/withdraw`), onSuccess: () => qc.invalidateQueries({ queryKey: key }), onError: fail })
  const ask = useMutation({ mutationFn: () => apiClient.post(`${base}/${id}/submissions/scopes`, { scopes: ["SUBMIT"] }), onSuccess: () => { setAsked(true); setErr("") }, onError: fail })

  if (!allowed) {
    const already = requested.includes("SUBMIT")
    return (
      <div style={{ fontSize: 13, color: "#475569" }}>
        <p style={{ marginTop: 0 }}>{already ? "You have asked this client for permission to send submissions. It is waiting for the client to grant it." : "This client has not given you permission to send submissions."}</p>
        {!already && canManage && status !== "ENDED" && status !== "INVITED" && !asked && (
          <button style={ghostBtn} disabled={ask.isPending} onClick={() => ask.mutate()}>Ask the client for permission to send submissions</button>
        )}
        {asked && <p style={{ color: "#166534" }}>Request recorded. The client decides whether to grant it.</p>}
        <Err m={err} />
      </div>
    )
  }
  const problem = formProblem(f, todayIso())
  return (
    <div>
      <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Submissions go to the client's inbox. They cannot change the client's records; the client decides what to do with each one. Do not put personal information in a submission, and remember the client's reply is visible to you.</p>
      <div style={{ marginBottom: 10 }}><button style={primaryBtn} onClick={() => setOpen(true)}>New submission</button></div>
      <Err m={err} />
      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && list.length === 0 && <p style={{ color: "#64748B", fontSize: 13 }}>Nothing sent to this client yet.</p>}
      {list.map(s => <SubmissionCard key={s.id} s={s} actions={s.status === "SUBMITTED" ? <button style={ghostBtn} disabled={withdraw.isPending} onClick={() => withdraw.mutate(s.id)}>Withdraw</button> : undefined} />)}
      {open && (
        <Modal title="New submission" onClose={() => setOpen(false)} wide>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10 }}>
            <Field l="Kind"><select style={input} value={f.kind} onChange={e => setF({ ...f, kind: e.target.value as Kind })}>{KINDS.map(k => <option key={k} value={k}>{label(k)}</option>)}</select></Field>
            <Field l="Area"><select style={input} value={f.area} onChange={e => setF({ ...f, area: e.target.value as Area })}>{AREAS.map(k => <option key={k} value={k}>{label(k)}</option>)}</select></Field>
            <Field l="Priority"><select style={input} value={f.priority} onChange={e => setF({ ...f, priority: e.target.value as Priority })}>{PRIORITIES.map(k => <option key={k} value={k}>{label(k)}</option>)}</select></Field>
          </div>
          <Field l="Title *"><input style={input} value={f.title} maxLength={200} onChange={e => setF({ ...f, title: e.target.value })} /></Field>
          <Field l="Description * (at least 20 characters)"><textarea style={{ ...input, minHeight: 120 }} value={f.body} maxLength={4000} onChange={e => setF({ ...f, body: e.target.value })} /></Field>
          <Field l="Reply wanted by (optional)"><input style={input} type="date" value={f.respondBy} onChange={e => setF({ ...f, respondBy: e.target.value })} /></Field>
          {problem && f.title + f.body !== "" && <p style={{ color: "#B45309", fontSize: 12 }}>{problem}</p>}
          <Err m={err} />
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setOpen(false)}>Cancel</button><button style={primaryBtn} disabled={!!problem || send.isPending} onClick={() => send.mutate()}>Send to the client</button></div>
        </Modal>
      )}
    </div>
  )
}

// ------------------------------------------------------------------------------------------------------------------------ client side

export function SubmissionInbox({ canAnswer }: { canAnswer: boolean }) {
  const qc = useQueryClient()
  const [filter, setFilter] = useState<"" | SubStatus>("")
  const [reply, setReply] = useState<{ id: string; action: "accept" | "decline" | "complete"; note: string } | null>(null)
  const [err, setErr] = useState("")
  const { data, isLoading } = useQuery<Inbox>({ queryKey: ["lc-provider-inbox", filter], queryFn: async () => one(await apiClient.get(inboxBase, { params: filter ? { status: filter } : {} })) })
  const answer = useMutation({
    mutationFn: ({ id, action, note }: { id: string; action: string; note: string }) => apiClient.post(`${inboxBase}/${id}/${action}`, { note: note.trim() || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lc-provider-inbox"] }); setReply(null); setErr("") }, onError: (e: unknown) => setErr(errMsg(e, "That did not work")),
  })
  const items = data?.items ?? []
  return (
    <div>
      <p style={{ fontSize: 13, color: "#475569", marginTop: 0 }}>What your compliance providers have sent you. Answering one does not change any of your records; if you act on it, you do that in the normal registers. <b>Your reply is visible to the provider.</b></p>
      <div style={{ display: "flex", gap: 6, marginBottom: 12, alignItems: "center", flexWrap: "wrap" }}>
        {(["", "SUBMITTED", "ACCEPTED", "COMPLETED", "DECLINED", "WITHDRAWN"] as const).map(s => (
          <button key={s || "all"} onClick={() => setFilter(s)} style={{ ...ghostBtn, background: filter === s ? "#EEF2FF" : "#fff", color: filter === s ? "#3730A3" : "#475569", borderColor: filter === s ? "#C7D2FE" : "#E2E8F0" }}>{s ? label(s) : "All"}</button>
        ))}
        <span style={{ fontSize: 12, color: "#64748B" }}>{data ? `${data.open} waiting for you` : ""}</span>
      </div>
      <Err m={err} />
      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && items.length === 0 && <p style={{ color: "#64748B", fontSize: 13 }}>Nothing here.</p>}
      {items.map(s => (
        <SubmissionCard key={s.id} s={s} actions={
          canAnswer && (s.status === "SUBMITTED" || s.status === "ACCEPTED") ? (
            reply?.id === s.id ? (
              <div style={{ marginTop: 8 }}>
                <Field l={reply.action === "accept" ? "Note for the provider (optional)" : "Reply for the provider (at least 10 characters)"}><textarea style={{ ...input, minHeight: 60 }} value={reply.note} onChange={e => setReply({ ...reply, note: e.target.value })} /></Field>
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setReply(null)}>Cancel</button>
                  <button style={primaryBtn} disabled={!replyValid(reply.action, reply.note) || answer.isPending} onClick={() => answer.mutate(reply)}>{label(reply.action)}</button></div>
              </div>
            ) : (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                {s.status === "SUBMITTED" && <button style={ghostBtn} onClick={() => setReply({ id: s.id, action: "accept", note: "" })}>Accept</button>}
                <button style={ghostBtn} onClick={() => setReply({ id: s.id, action: "complete", note: "" })}>Mark done</button>
                <button style={{ ...ghostBtn, color: "#B91C1C" }} onClick={() => setReply({ id: s.id, action: "decline", note: "" })}>Decline</button>
              </div>
            )
          ) : undefined} />
      ))}
    </div>
  )
}

/** Lets the client choose which of the things a provider asked for it may do. Mounted under each provider in the access list. */
export function GrantsEditor({ id, requested, granted }: { id: string; requested: Scope[]; granted: Scope[] }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [value, setValue] = useState<Scope[]>(granted)
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.put(`${accessBase}/${id}/grants`, { scopes: value }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lc-provider-access"] }); setOpen(false); setErr("") }, onError: (e: unknown) => setErr(errMsg(e, "That did not work")),
  })
  const text: Record<Scope, string> = { SUMMARY: "See the compliance summary", REPORTS: "View standard reports", SUBMIT: "Send submissions to your inbox (cannot change your records)" }
  const pending = requested.filter(s => !granted.includes(s))
  return (
    <>
      <button style={ghostBtn} onClick={() => { setValue(granted); setOpen(true) }}>{pending.length ? `What they may do (${pending.length} more asked)` : "What they may do"}</button>
      {open && (
        <Modal title="What this provider may do" onClose={() => setOpen(false)}>
          {requested.map(s => (
            <label key={s} style={{ display: "flex", gap: 8, fontSize: 13, marginBottom: 6 }}>
              <input type="checkbox" checked={value.includes(s)} onChange={e => setValue(toggleGrant(value, s, e.target.checked))} style={{ marginTop: 3 }} />{text[s]}
            </label>
          ))}
          <p style={{ fontSize: 12, color: "#64748B" }}>To take everything away, end the access instead.</p>
          <Err m={err} />
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setOpen(false)}>Cancel</button>
            <button style={primaryBtn} disabled={value.length === 0 || !grantsChanged(value, granted) || save.isPending} onClick={() => save.mutate()}>Save</button></div>
        </Modal>
      )}
    </>
  )
}
