// src/pages/legalcompliance/ProvidersTab.tsx
//
// Compliance providers and their client organisations (Phase 5). Two audiences in one tab:
//  • "My clients"      — a consultancy's workspace. /api/v1/legalcompliance/provider: summary · clients (CRUD via invite) · workload · assignments · reviews ·
//                        clients/{id}/posture and clients/{id}/reports[/key] (read-only views INTO the client's workspace, logged to the client's audit trail).
//  • "Provider access" — the organisation being served. /api/v1/legalcompliance/provider-access: list · preview/accept (invitation code) · suspend/resume/revoke · activity.
// A client is its own tenant. The provider only ever sees what the client granted (summary and/or reports), only while access is active, and only for consultants assigned to it.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { one, unwrap, input, primaryBtn, ghostBtn, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { Plus, Copy } from "lucide-react"

const base = "/api/v1/legalcompliance/provider"
const accessBase = "/api/v1/legalcompliance/provider-access"

type Scope = "SUMMARY" | "REPORTS"
type Status = "INVITED" | "ACTIVE" | "SUSPENDED" | "ENDED"
type Side = "PROVIDER" | "CLIENT"
type Role = "LEAD" | "REVIEWER" | "SUPPORT"
type Outcome = "SATISFACTORY" | "ACTIONS_REQUIRED" | "ESCALATED"
type Cycle = "MONTHLY" | "QUARTERLY" | "ANNUALLY"

interface Member { id: string; userId: string; userName: string; role: Role; assignedByName: string; assignedOn: string; endedOn: string | null }
interface Review { id: string; reviewDate: string; reviewerName: string; outcome: Outcome; notes: string; metrics: Record<string, number> | null; nextReviewDue: string }
interface Client {
  id: string; reference: string; clientName: string; providerName: string; status: Status; requestedScopes: Scope[]; grantedScopes: Scope[]
  inviteExpired: boolean; inviteExpiresAt: string | null; acceptedAt: string | null; acceptedByName: string | null; suspendedBy: Side | null; statusReason: string | null
  contractStart: string | null; contractEnd: string | null; contractEnded: boolean; reviewCycle: Cycle; nextReviewDue: string | null; reviewOverdue: boolean
  team: Member[]; hasLead: boolean; notes: string | null; endedAt: string | null; endedBy: Side | null
}
interface Detail { client: Client; assignmentHistory: Member[]; reviews: Review[] }
interface Summary { active: number; invited: number; suspended: number; reviewsOverdue: number; withoutLead: number; contractsEnded: number }
interface Workload { userId: string; userName: string; clients: number; asLead: number; clientNames: string[] }
interface UserOption { id: string; firstName: string; lastName: string }
interface Indicator { key: string; label: string; formula: string; numerator: number; denominator: number; percent: number | null }
interface Attention { key: string; severity: "CRITICAL" | "WARNING"; text: string; count: number }
interface Posture { clientId: string; clientName: string; overview: { generatedAt: string; indicators: Indicator[]; attention: Attention[] } }
interface ReportInfo { key: string; category: string; title: string; description: string }
interface ReportResult { title: string; rowCount: number; truncated: boolean; columns: string[]; rows: string[][]; generatedAt: string }
interface Access { id: string; providerName: string; status: Status; requestedScopes: Scope[]; grantedScopes: Scope[]; acceptedByName: string | null; acceptedAt: string | null; suspendedBy: Side | null; endedBy: Side | null; endedAt: string | null }
interface Preview { providerName: string; requestedScopes: Scope[]; expiresAt: string }
interface AccessEvent { at: string; eventType: string; detail: string; actorName: string }

const SCOPE_TEXT: Record<Scope, string> = { SUMMARY: "Compliance summary (counts, indicators and attention list)", REPORTS: "Standard reports (viewed on screen, not downloadable)" }
const SCOPE_SHORT: Record<Scope, string> = { SUMMARY: "Summary", REPORTS: "Reports" }
const pretty = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")
const statusTone: Record<Status, { color: string; bg: string }> = {
  INVITED: { color: "#92400E", bg: "#FEF3C7" }, ACTIVE: { color: "#166534", bg: "#DCFCE7" }, SUSPENDED: { color: "#9A3412", bg: "#FFEDD5" }, ENDED: { color: "#475569", bg: "#E2E8F0" },
}
const statusBadge = (c: { status: Status; inviteExpired?: boolean; suspendedBy?: Side | null }) =>
  c.status === "INVITED" && c.inviteExpired ? <Badge text="Invitation expired" color="#991B1B" bg="#FEE2E2" />
    : c.status === "SUSPENDED" ? <Badge text={`Suspended by ${c.suspendedBy === "CLIENT" ? "client" : "provider"}`} color={statusTone.SUSPENDED.color} bg={statusTone.SUSPENDED.bg} />
    : <Badge text={pretty(c.status)} color={statusTone[c.status].color} bg={statusTone[c.status].bg} />

function ScopePicker({ value, onChange, allowed }: { value: Scope[]; onChange: (s: Scope[]) => void; allowed: Scope[] }) {
  return (
    <div>
      {allowed.map(s => (
        <label key={s} style={{ display: "flex", gap: 8, alignItems: "flex-start", fontSize: 13, marginBottom: 6, color: "#0F172A" }}>
          <input type="checkbox" checked={value.includes(s)} onChange={e => onChange(e.target.checked ? [...value, s] : value.filter(x => x !== s))} style={{ marginTop: 3 }} />
          {SCOPE_TEXT[s]}
        </label>
      ))}
    </div>
  )
}

// ------------------------------------------------------------------------------------------------------------------------ provider side

function InviteModal({ onClose, onCreated }: { onClose: () => void; onCreated: (code: string) => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ providerName: "", clientName: "", contractStart: "", contractEnd: "", reviewCycle: "QUARTERLY" as Cycle, notes: "" })
  const [scopes, setScopes] = useState<Scope[]>(["SUMMARY"])
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: async () => one(await apiClient.post(`${base}/clients`, { ...f, scopes, contractStart: f.contractStart || null, contractEnd: f.contractEnd || null, notes: f.notes || null })) as { code: string },
    onSuccess: r => { qc.invalidateQueries({ queryKey: ["lc-provider"] }); onCreated(r.code) },
    onError: (e: unknown) => setErr(errMsg(e, "Could not create the invitation")),
  })
  return (
    <Modal title="Add a client organisation" onClose={onClose}>
      <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>The client organisation must accept before you can see anything. You will get a one-time code to give them.</p>
      <Err m={err} />
      <Field l="Your organisation, as the client should see it *"><input style={input} value={f.providerName} onChange={e => setF({ ...f, providerName: e.target.value })} /></Field>
      <Field l="Client name (your label) *"><input style={input} value={f.clientName} onChange={e => setF({ ...f, clientName: e.target.value })} /></Field>
      <Field l="What you are asking to see *"><ScopePicker value={scopes} onChange={setScopes} allowed={["SUMMARY", "REPORTS"]} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <div style={{ flex: 1 }}><Field l="Contract start"><input type="date" style={input} value={f.contractStart} onChange={e => setF({ ...f, contractStart: e.target.value })} /></Field></div>
        <div style={{ flex: 1 }}><Field l="Contract end"><input type="date" style={input} value={f.contractEnd} onChange={e => setF({ ...f, contractEnd: e.target.value })} /></Field></div>
      </div>
      <Field l="Review cycle *">
        <select style={input} value={f.reviewCycle} onChange={e => setF({ ...f, reviewCycle: e.target.value as Cycle })}>
          {(["MONTHLY", "QUARTERLY", "ANNUALLY"] as Cycle[]).map(c => <option key={c} value={c}>{pretty(c)}</option>)}
        </select>
      </Field>
      <Field l="Notes (private to you)"><textarea style={{ ...input, minHeight: 60 }} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} /></Field>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
        <button style={ghostBtn} onClick={onClose}>Cancel</button>
        <button style={primaryBtn} disabled={save.isPending || scopes.length === 0} onClick={() => save.mutate()}>Create invitation</button>
      </div>
    </Modal>
  )
}

function CodeModal({ code, onClose }: { code: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false)
  return (
    <Modal title="Invitation code" onClose={onClose}>
      <p style={{ fontSize: 13, color: "#0F172A", marginTop: 0 }}>Give this code to an administrator of the client organisation, by phone or a secure message. It works once, expires in 14 days and <b>is not shown again</b>.</p>
      <div style={{ fontFamily: "monospace", fontSize: 24, letterSpacing: 2, textAlign: "center", padding: 16, background: "#F1F5F9", borderRadius: 10, marginBottom: 12 }}>{code}</div>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
        <button style={{ ...ghostBtn, display: "flex", gap: 6, alignItems: "center" }} onClick={() => { navigator.clipboard?.writeText(code).then(() => setCopied(true), () => setCopied(false)) }}><Copy size={13} />{copied ? "Copied" : "Copy"}</button>
        <button style={primaryBtn} onClick={onClose}>Done</button>
      </div>
    </Modal>
  )
}

function Posture({ id }: { id: string }) {
  const { data, error, isLoading } = useQuery<Posture>({ queryKey: ["lc-provider", "posture", id], queryFn: async () => one(await apiClient.get(`${base}/clients/${id}/posture`)), retry: false })
  if (isLoading) return <p style={{ color: "#64748B" }}>Loading…</p>
  if (error || !data) return <Err m={errMsg(error, "Could not load the summary")} />
  return (
    <div>
      <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 8px" }}>Counts as at {fmtDateTime(data.overview.generatedAt)}. Viewing this was recorded in {data.clientName}'s audit trail.</p>
      {data.overview.attention.length > 0 && (
        <div style={{ marginBottom: 12 }}>
          {data.overview.attention.map(a => <div key={a.key} style={{ display: "flex", gap: 8, padding: "6px 0", fontSize: 13 }}>
            <Badge text={a.severity === "CRITICAL" ? "Critical" : "Warning"} color={a.severity === "CRITICAL" ? "#991B1B" : "#92400E"} bg={a.severity === "CRITICAL" ? "#FEE2E2" : "#FEF3C7"} />{a.text}</div>)}
        </div>
      )}
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
        <tbody>
          {data.overview.indicators.map(i => (
            <tr key={i.key} style={{ borderTop: "1px solid #F1F5F9" }}>
              <td style={{ padding: "6px 4px" }}>{i.label}</td>
              <td style={{ padding: "6px 4px", textAlign: "right", color: "#64748B" }}>{i.numerator} of {i.denominator}</td>
              <td style={{ padding: "6px 4px", textAlign: "right", fontWeight: 700 }}>{i.percent === null ? "—" : `${i.percent}%`}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ClientReports({ id }: { id: string }) {
  const [result, setResult] = useState<ReportResult | null>(null)
  const [err, setErr] = useState("")
  const { data: catalogue = [] } = useQuery<ReportInfo[]>({ queryKey: ["lc-provider", "reports", id], queryFn: async () => one(await apiClient.get(`${base}/clients/${id}/reports`)) ?? [], retry: false })
  const run = useMutation({
    mutationFn: async (key: string) => one(await apiClient.get(`${base}/clients/${id}/reports/${key}`)) as ReportResult,
    onSuccess: r => { setErr(""); setResult(r) }, onError: (e: unknown) => setErr(errMsg(e, "Could not run the report")),
  })
  return (
    <div>
      <Err m={err} />
      <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
        {catalogue.map(c => <button key={c.key} style={{ ...ghostBtn, padding: "5px 10px", fontSize: 12 }} title={c.description} disabled={run.isPending} onClick={() => run.mutate(c.key)}>{c.title}</button>)}
      </div>
      {result && (
        <div>
          <div style={{ fontWeight: 700, fontSize: 14 }}>{result.title} <span style={{ fontWeight: 400, color: "#64748B", fontSize: 12 }}>· {result.rowCount} rows · each run is recorded in the client's audit trail</span></div>
          {result.truncated && <p style={{ fontSize: 12, color: "#92400E" }}>Only the first {result.rows.length} rows are shown.</p>}
          <div style={{ overflowX: "auto", border: "1px solid #E2E8F0", borderRadius: 8, marginTop: 6 }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
              <thead><tr>{result.columns.map(c => <th key={c} style={{ textAlign: "left", padding: "6px 8px", background: "#F8FAFC", whiteSpace: "nowrap" }}>{c}</th>)}</tr></thead>
              <tbody>{result.rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j} style={{ padding: "5px 8px", borderTop: "1px solid #F1F5F9" }}>{c}</td>)}</tr>)}</tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}

function ClientModal({ id, canManage, onClose, onCode }: { id: string; canManage: boolean; onClose: () => void; onCode: (code: string) => void }) {
  const qc = useQueryClient()
  const [err, setErr] = useState("")
  const [view, setView] = useState<"team" | "summary" | "reports">("team")
  const [reason, setReason] = useState<{ kind: "suspend" | "end"; text: string } | null>(null)
  const [assign, setAssign] = useState({ userId: "", role: "SUPPORT" as Role })
  const [review, setReview] = useState({ reviewDate: new Date().toISOString().slice(0, 10), outcome: "SATISFACTORY" as Outcome, notes: "" })
  const [showReview, setShowReview] = useState(false)
  const [rescopes, setRescopes] = useState<Scope[] | null>(null)

  const { data: d, isLoading } = useQuery<Detail>({ queryKey: ["lc-provider", "client", id], queryFn: async () => one(await apiClient.get(`${base}/clients/${id}`)) })
  const { data: usersRaw } = useQuery<UserOption[] | { content: UserOption[] }>({
    queryKey: ["lc-provider", "users"], enabled: canManage, queryFn: async () => (await apiClient.get("/api/v1/identity/users?size=200")).data,
  })
  const users: UserOption[] = Array.isArray(usersRaw) ? usersRaw : usersRaw?.content ?? []

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-provider"] }); setErr("") }
  const fail = (e: unknown) => setErr(errMsg(e, "That did not work"))
  const post = useMutation({ mutationFn: ({ path, body }: { path: string; body?: unknown }) => apiClient.post(`${base}/clients/${id}/${path}`, body), onSuccess: () => { refresh(); setReason(null); setShowReview(false) }, onError: fail })
  const reissue = useMutation({
    mutationFn: async (scopes: Scope[]) => one(await apiClient.post(`${base}/clients/${id}/reissue`, { scopes })) as { code: string },
    onSuccess: r => { refresh(); setRescopes(null); onCode(r.code) }, onError: fail,
  })

  if (isLoading || !d) return <Modal title="Client" onClose={onClose} wide><p style={{ color: "#64748B" }}>Loading…</p></Modal>
  const c = d.client
  const accepted = c.status === "ACTIVE" || c.status === "SUSPENDED"
  const pickedUser = users.find(u => u.id === assign.userId)

  return (
    <Modal title={`${c.reference} · ${c.clientName}`} onClose={onClose} wide>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 8 }}>
        {statusBadge(c)}
        {c.grantedScopes.map(s => <Badge key={s} text={`${SCOPE_SHORT[s]} granted`} color="#3730A3" bg="#E0E7FF" />)}
        {c.status === "INVITED" && c.requestedScopes.map(s => <Badge key={s} text={`${SCOPE_SHORT[s]} requested`} color="#475569" bg="#E2E8F0" />)}
        {c.reviewOverdue && <Badge text="Review overdue" color="#991B1B" bg="#FEE2E2" />}
        {c.contractEnded && c.status !== "ENDED" && <Badge text="Contract end date passed" color="#92400E" bg="#FEF3C7" />}
      </div>
      <div style={{ fontSize: 12, color: "#64748B", marginBottom: 12 }}>
        {accepted && c.acceptedByName ? `Accepted by ${c.acceptedByName} on ${fmtDate(c.acceptedAt)}. ` : ""}
        Review {pretty(c.reviewCycle).toLowerCase()}{c.nextReviewDue ? `, next due ${fmtDate(c.nextReviewDue)}` : ""}.
        {c.contractStart || c.contractEnd ? ` Contract ${fmtDate(c.contractStart)} – ${fmtDate(c.contractEnd)}.` : ""}
        {c.statusReason && c.status !== "ACTIVE" ? ` Reason: ${c.statusReason}` : ""}
      </div>
      <Err m={err} />

      {canManage && c.status !== "ENDED" && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 14 }}>
          {c.status === "INVITED" && <button style={ghostBtn} onClick={() => setRescopes(c.requestedScopes)}>Issue a new code</button>}
          {c.status === "ACTIVE" && <button style={ghostBtn} onClick={() => setReason({ kind: "suspend", text: "" })}>Suspend access</button>}
          {c.status === "SUSPENDED" && c.suspendedBy === "PROVIDER" && <button style={ghostBtn} onClick={() => post.mutate({ path: "resume" })}>Resume access</button>}
          {c.status === "SUSPENDED" && c.suspendedBy === "CLIENT" && <span style={{ fontSize: 12, color: "#64748B", alignSelf: "center" }}>Only the client can lift its own suspension.</span>}
          <button style={{ ...ghostBtn, color: "#B91C1C" }} onClick={() => setReason({ kind: "end", text: "" })}>{c.status === "INVITED" ? "Withdraw invitation" : "End relationship"}</button>
        </div>
      )}

      {rescopes && (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 12 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>Issue a new code (the old one stops working)</div>
          <ScopePicker value={rescopes} onChange={setRescopes} allowed={["SUMMARY", "REPORTS"]} />
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setRescopes(null)}>Cancel</button><button style={primaryBtn} disabled={rescopes.length === 0 || reissue.isPending} onClick={() => reissue.mutate(rescopes)}>Issue code</button></div>
        </div>
      )}

      {reason && (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 12 }}>
          <Field l={reason.kind === "suspend" ? "Why are you suspending access? (min. 10 characters)" : "Why is this ending? (min. 10 characters)"}>
            <textarea style={{ ...input, minHeight: 50 }} value={reason.text} onChange={e => setReason({ ...reason, text: e.target.value })} />
          </Field>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <button style={ghostBtn} onClick={() => setReason(null)}>Cancel</button>
            <button style={primaryBtn} disabled={reason.text.trim().length < 10 || post.isPending} onClick={() => post.mutate({ path: reason.kind, body: { reason: reason.text } })}>Confirm</button>
          </div>
        </div>
      )}

      <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 12 }}>
        {(["team", "summary", "reports"] as const).map(v => (
          <button key={v} onClick={() => setView(v)} style={{ background: "none", border: "none", padding: "8px 12px", cursor: "pointer", fontSize: 13, fontWeight: view === v ? 600 : 400, color: view === v ? "#4338CA" : "#64748B", borderBottom: view === v ? "2px solid #4338CA" : "2px solid transparent", marginBottom: -1 }}>
            {v === "team" ? "Team and reviews" : v === "summary" ? "Compliance summary" : "Reports"}
          </button>
        ))}
      </div>

      {view === "team" && (
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Consultants</div>
          {d.assignmentHistory.length === 0 && <p style={{ fontSize: 13, color: "#64748B" }}>No one is assigned yet. Consultants can only see clients they are assigned to.</p>}
          {d.assignmentHistory.map(a => (
            <div key={a.id} style={{ display: "flex", alignItems: "center", gap: 8, padding: "6px 0", borderTop: "1px solid #F1F5F9", fontSize: 13, opacity: a.endedOn ? 0.55 : 1 }}>
              <span style={{ flex: 1 }}>{a.userName} <span style={{ color: "#64748B" }}>· {pretty(a.role)} · since {fmtDate(a.assignedOn)}{a.endedOn ? `, ended ${fmtDate(a.endedOn)}` : ""}</span></span>
              {canManage && !a.endedOn && <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => post.mutate({ path: `assignments/${a.id}/end` })}>End</button>}
            </div>
          ))}
          {canManage && c.status !== "ENDED" && (
            <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
              <select style={{ ...input, flex: 2, minWidth: 160 }} value={assign.userId} onChange={e => setAssign({ ...assign, userId: e.target.value })}>
                <option value="">Choose a consultant…</option>
                {users.map(u => <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>)}
              </select>
              <select style={{ ...input, flex: 1, minWidth: 110 }} value={assign.role} onChange={e => setAssign({ ...assign, role: e.target.value as Role })}>
                {(["LEAD", "REVIEWER", "SUPPORT"] as Role[]).map(r => <option key={r} value={r}>{pretty(r)}</option>)}
              </select>
              <button style={primaryBtn} disabled={!pickedUser || post.isPending}
                onClick={() => pickedUser && post.mutate({ path: "assignments", body: { userId: pickedUser.id, userName: `${pickedUser.firstName} ${pickedUser.lastName}`, role: assign.role } })}>Assign</button>
            </div>
          )}

          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "18px 0 6px" }}>
            <div style={{ fontSize: 13, fontWeight: 700 }}>Reviews</div>
            {accepted && <button style={{ ...ghostBtn, padding: "4px 10px" }} onClick={() => setShowReview(s => !s)}>Record a review</button>}
          </div>
          {showReview && (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 10 }}>
              <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>{c.status === "ACTIVE" && c.grantedScopes.includes("SUMMARY") ? "The client's current compliance counts will be saved with this review, and the read is recorded in their audit trail." : "No client figures will be saved with this review (summary access is not active)."}</p>
              <div style={{ display: "flex", gap: 10 }}>
                <div style={{ flex: 1 }}><Field l="Review date"><input type="date" style={input} value={review.reviewDate} max={new Date().toISOString().slice(0, 10)} onChange={e => setReview({ ...review, reviewDate: e.target.value })} /></Field></div>
                <div style={{ flex: 1 }}><Field l="Outcome"><select style={input} value={review.outcome} onChange={e => setReview({ ...review, outcome: e.target.value as Outcome })}>
                  {(["SATISFACTORY", "ACTIONS_REQUIRED", "ESCALATED"] as Outcome[]).map(o => <option key={o} value={o}>{pretty(o)}</option>)}</select></Field></div>
              </div>
              <Field l="Notes * (min. 15 characters)"><textarea style={{ ...input, minHeight: 60 }} value={review.notes} onChange={e => setReview({ ...review, notes: e.target.value })} /></Field>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setShowReview(false)}>Cancel</button>
                <button style={primaryBtn} disabled={review.notes.trim().length < 15 || post.isPending} onClick={() => post.mutate({ path: "reviews", body: review })}>Save review</button></div>
            </div>
          )}
          {d.reviews.length === 0 && <p style={{ fontSize: 13, color: "#64748B" }}>No reviews recorded yet.</p>}
          {d.reviews.map(r => (
            <div key={r.id} style={{ borderTop: "1px solid #F1F5F9", padding: "8px 0", fontSize: 13 }}>
              <div><b>{fmtDate(r.reviewDate)}</b> · {r.reviewerName} · {pretty(r.outcome)} <span style={{ color: "#64748B" }}>· next {fmtDate(r.nextReviewDue)}</span></div>
              <div style={{ color: "#334155", whiteSpace: "pre-wrap" }}>{r.notes}</div>
              {r.metrics && <div style={{ fontSize: 11, color: "#64748B" }}>{Object.keys(r.metrics).length} counts from the client's summary were saved with this review.</div>}
            </div>
          ))}
        </div>
      )}
      {view === "summary" && (c.status === "ACTIVE" && c.grantedScopes.includes("SUMMARY") ? <Posture id={id} /> : <p style={{ color: "#64748B", fontSize: 13 }}>Summary access is not currently available for this client.</p>)}
      {view === "reports" && (c.status === "ACTIVE" && c.grantedScopes.includes("REPORTS") ? <ClientReports id={id} /> : <p style={{ color: "#64748B", fontSize: 13 }}>Report access is not currently available for this client.</p>)}
    </Modal>
  )
}

function ProviderWorkspace() {
  const hasManage = usePermission("LEGALCOMPLIANCE_PROVIDER_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const [adding, setAdding] = useState(false)
  const [code, setCode] = useState<string | null>(null)
  const [openId, setOpenId] = useState<string | null>(null)
  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-provider", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: clients = [], isLoading } = useQuery<Client[]>({ queryKey: ["lc-provider", "clients"], queryFn: async () => unwrap(await apiClient.get(`${base}/clients`)) })
  const { data: workload = [] } = useQuery<Workload[]>({ queryKey: ["lc-provider", "workload"], enabled: canManage, queryFn: async () => unwrap(await apiClient.get(`${base}/workload`)) })

  const tile = (label: string, v: number | undefined, warn?: boolean) => (
    <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: "10px 14px", minWidth: 120 }}>
      <div style={{ fontSize: 22, fontWeight: 800, color: warn && (v ?? 0) > 0 ? "#B45309" : "#0F172A" }}>{v ?? "—"}</div>
      <div style={{ fontSize: 12, color: "#64748B" }}>{label}</div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        {tile("Active clients", summary?.active)}{tile("Invitations open", summary?.invited)}{tile("Suspended", summary?.suspended, true)}
        {tile("Reviews overdue", summary?.reviewsOverdue, true)}{tile("No lead consultant", summary?.withoutLead, true)}{tile("Contract date passed", summary?.contractsEnded, true)}
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <div style={{ fontSize: 12, color: "#64748B" }}>{canManage ? "All your client organisations." : "Clients you are assigned to."} You see a client's records only while it has granted access.</div>
        {canManage && <button style={{ ...primaryBtn, display: "flex", gap: 6, alignItems: "center" }} onClick={() => setAdding(true)}><Plus size={14} />Add client</button>}
      </div>
      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && clients.length === 0 && <p style={{ color: "#64748B" }}>No client organisations yet.</p>}
      {clients.length > 0 && (
        <div style={{ overflowX: "auto", border: "1px solid #E2E8F0", borderRadius: 10 }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr>{["Ref", "Client", "Status", "Access", "Team", "Next review"].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 10px", background: "#F8FAFC", color: "#475569" }}>{h}</th>)}</tr></thead>
            <tbody>
              {clients.map(c => (
                <tr key={c.id} onClick={() => setOpenId(c.id)} style={{ cursor: "pointer", borderTop: "1px solid #F1F5F9" }}>
                  <td style={{ padding: "8px 10px", color: "#64748B" }}>{c.reference}</td>
                  <td style={{ padding: "8px 10px", fontWeight: 600 }}>{c.clientName}</td>
                  <td style={{ padding: "8px 10px" }}>{statusBadge(c)}</td>
                  <td style={{ padding: "8px 10px", color: "#475569" }}>{c.grantedScopes.length ? c.grantedScopes.map(s => SCOPE_SHORT[s]).join(", ") : "—"}</td>
                  <td style={{ padding: "8px 10px", color: "#475569" }}>{c.team.length ? c.team.map(m => `${m.userName}${m.role === "LEAD" ? " (lead)" : ""}`).join(", ") : "—"}</td>
                  <td style={{ padding: "8px 10px" }}>{c.nextReviewDue ? fmtDate(c.nextReviewDue) : "—"} {c.reviewOverdue && <Badge text="Overdue" color="#991B1B" bg="#FEE2E2" />}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {canManage && workload.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Consultant workload</div>
          {workload.map(w => <div key={w.userId} style={{ fontSize: 13, padding: "5px 0", borderTop: "1px solid #F1F5F9" }}><b>{w.userName}</b> — {w.clients} client{w.clients === 1 ? "" : "s"}{w.asLead ? `, lead on ${w.asLead}` : ""} <span style={{ color: "#64748B" }}>({w.clientNames.join(", ")})</span></div>)}
        </div>
      )}
      {adding && <InviteModal onClose={() => setAdding(false)} onCreated={c => { setAdding(false); setCode(c) }} />}
      {code && <CodeModal code={code} onClose={() => setCode(null)} />}
      {openId && <ClientModal id={openId} canManage={canManage} onClose={() => setOpenId(null)} onCode={setCode} />}
    </div>
  )
}

// ------------------------------------------------------------------------------------------------------------------------ client side

function ProviderAccessPanel() {
  const qc = useQueryClient()
  const [code, setCode] = useState("")
  const [preview, setPreview] = useState<Preview | null>(null)
  const [granted, setGranted] = useState<Scope[]>([])
  const [err, setErr] = useState("")
  const [reason, setReason] = useState<{ id: string; kind: "suspend" | "revoke"; text: string } | null>(null)
  const [activityFor, setActivityFor] = useState<Access | null>(null)
  const { data: list = [], isLoading } = useQuery<Access[]>({ queryKey: ["lc-provider-access"], queryFn: async () => unwrap(await apiClient.get(accessBase)) })
  const { data: events = [] } = useQuery<AccessEvent[]>({ queryKey: ["lc-provider-access", "activity", activityFor?.id], enabled: !!activityFor, queryFn: async () => unwrap(await apiClient.get(`${accessBase}/${activityFor?.id}/activity`)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-provider-access"] }); setErr("") }
  const check = useMutation({
    mutationFn: async () => one(await apiClient.post(`${accessBase}/preview`, { code })) as Preview,
    onSuccess: p => { setErr(""); setPreview(p); setGranted(p.requestedScopes) }, onError: (e: unknown) => { setPreview(null); setErr(errMsg(e, "That code is not valid")) },
  })
  const accept = useMutation({
    mutationFn: () => apiClient.post(`${accessBase}/accept`, { code, scopes: granted }),
    onSuccess: () => { refresh(); setPreview(null); setCode("") }, onError: (e: unknown) => setErr(errMsg(e, "Could not accept")),
  })
  const act = useMutation({
    mutationFn: ({ id, path, body }: { id: string; path: string; body?: unknown }) => apiClient.post(`${accessBase}/${id}/${path}`, body),
    onSuccess: () => { refresh(); setReason(null) }, onError: (e: unknown) => setErr(errMsg(e, "That did not work")),
  })

  return (
    <div>
      <p style={{ fontSize: 13, color: "#334155", marginTop: 0 }}>A compliance provider can only see this organisation's records if you accept its invitation, and only what you choose to grant. Access is read-only. You can pause or end it at any time, and everything they view is recorded below.</p>
      <Err m={err} />
      <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: 14, marginBottom: 18 }}>
        <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 8 }}>Accept an invitation</div>
        <div style={{ display: "flex", gap: 8 }}>
          <input style={{ ...input, fontFamily: "monospace", maxWidth: 260 }} placeholder="XXXX-XXXX-XXXX" value={code} onChange={e => { setCode(e.target.value); setPreview(null) }} />
          <button style={ghostBtn} disabled={code.trim().length < 12 || check.isPending} onClick={() => check.mutate()}>Check code</button>
        </div>
        {preview && (
          <div style={{ marginTop: 12 }}>
            <p style={{ fontSize: 13, margin: "0 0 8px" }}><b>{preview.providerName}</b> is asking for read-only access. Valid until {fmtDateTime(preview.expiresAt)}. Choose what to grant:</p>
            <ScopePicker value={granted} onChange={setGranted} allowed={preview.requestedScopes} />
            <button style={primaryBtn} disabled={granted.length === 0 || accept.isPending} onClick={() => accept.mutate()}>Grant access</button>
          </div>
        )}
      </div>

      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && list.length === 0 && <p style={{ color: "#64748B" }}>No provider has access to this organisation.</p>}
      {list.map(a => (
        <div key={a.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: 12, marginBottom: 10 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <b style={{ fontSize: 14 }}>{a.providerName}</b>{statusBadge(a)}
            {a.grantedScopes.map(s => <Badge key={s} text={SCOPE_SHORT[s]} color="#3730A3" bg="#E0E7FF" />)}
          </div>
          <div style={{ fontSize: 12, color: "#64748B", margin: "4px 0 8px" }}>{a.acceptedAt ? `Granted by ${a.acceptedByName} on ${fmtDate(a.acceptedAt)}.` : "Not accepted."}{a.endedAt ? ` Ended ${fmtDate(a.endedAt)} by the ${a.endedBy === "CLIENT" ? "client" : "provider"}.` : ""}</div>
          {a.status !== "ENDED" && a.status !== "INVITED" && (
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {a.status === "ACTIVE" && <button style={ghostBtn} onClick={() => setReason({ id: a.id, kind: "suspend", text: "" })}>Pause access</button>}
              {a.status === "SUSPENDED" && a.suspendedBy === "CLIENT" && <button style={ghostBtn} onClick={() => act.mutate({ id: a.id, path: "resume" })}>Resume access</button>}
              {a.status === "SUSPENDED" && a.suspendedBy === "PROVIDER" && <span style={{ fontSize: 12, color: "#64748B", alignSelf: "center" }}>Paused by the provider.</span>}
              <button style={{ ...ghostBtn, color: "#B91C1C" }} onClick={() => setReason({ id: a.id, kind: "revoke", text: "" })}>End access</button>
              <button style={ghostBtn} onClick={() => setActivityFor(a)}>What they viewed</button>
            </div>
          )}
          {a.status === "ENDED" && <button style={ghostBtn} onClick={() => setActivityFor(a)}>What they viewed</button>}
          {reason?.id === a.id && (
            <div style={{ marginTop: 10 }}>
              <Field l="Reason (min. 10 characters; the provider can see it)"><textarea style={{ ...input, minHeight: 50 }} value={reason.text} onChange={e => setReason({ ...reason, text: e.target.value })} /></Field>
              <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setReason(null)}>Cancel</button>
                <button style={primaryBtn} disabled={reason.text.trim().length < 10 || act.isPending} onClick={() => act.mutate({ id: a.id, path: reason.kind, body: { reason: reason.text } })}>Confirm</button></div>
            </div>
          )}
        </div>
      ))}

      {activityFor && (
        <Modal title={`Activity — ${activityFor.providerName}`} onClose={() => setActivityFor(null)} wide>
          {events.length === 0 && <p style={{ color: "#64748B", fontSize: 13 }}>Nothing recorded yet.</p>}
          {events.map((e, i) => <div key={i} style={{ padding: "6px 0", borderTop: "1px solid #F1F5F9", fontSize: 13 }}><span style={{ color: "#64748B" }}>{fmtDateTime(e.at)}</span> · {e.actorName ?? "—"} · {e.detail}</div>)}
        </Modal>
      )}
    </div>
  )
}

// ------------------------------------------------------------------------------------------------------------------------ shell

export default function ProvidersTab() {
  const hasManage = usePermission("LEGALCOMPLIANCE_PROVIDER_MANAGE")
  const hasConsult = usePermission("LEGALCOMPLIANCE_PROVIDER_CONSULT")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const isProvider = hasManage || hasConsult || hasAdmin
  const [view, setView] = useState<"clients" | "access">(isProvider ? "clients" : "access")

  const pills: { id: "clients" | "access"; label: string; show: boolean }[] = [
    { id: "clients", label: "My clients", show: isProvider },
    { id: "access", label: "Provider access to this organisation", show: hasAdmin },
  ]
  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
        {pills.filter(p => p.show).map(p => (
          <button key={p.id} onClick={() => setView(p.id)} style={{ ...ghostBtn, background: view === p.id ? "#EEF2FF" : "#fff", color: view === p.id ? "#3730A3" : "#475569", borderColor: view === p.id ? "#C7D2FE" : "#E2E8F0" }}>{p.label}</button>
        ))}
      </div>
      {view === "clients" && isProvider && <ProviderWorkspace />}
      {view === "access" && hasAdmin && <ProviderAccessPanel />}
    </div>
  )
}
