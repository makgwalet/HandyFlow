// Investigation case files: visible only to the people named on a case, with a tamper-evident record (audit backlog A20).
import { useEffect, useState } from "react"
import { auditInvestigationsApi as api, type InvSummary, type InvView, type InvVerification } from "../../api/auditInvestigations.api"
import { Card } from "../../components/DashboardBits"
import { useAuthStore } from "../../store/auth.store"

const MUTED = "var(--hf-text-muted)"
const BORDER = "var(--hf-border)"
const MIN = 10
const msg = (e: any, f: string) => e?.response?.data?.message ?? f
const btn: React.CSSProperties = { padding: "6px 12px", fontSize: 12, fontWeight: 700, cursor: "pointer", borderRadius: 6, border: `1px solid ${BORDER}`, background: "var(--hf-surface)" }
const area: React.CSSProperties = { width: "100%", minHeight: 48, padding: 8, border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, fontFamily: "inherit", boxSizing: "border-box" }
const STATUS: Record<string, string> = { OPEN: "Open", UNDER_REVIEW: "Under review", CLOSED: "Closed" }
const ROLE: Record<string, string> = { LEAD: "Lead", INVESTIGATOR: "Investigator", OBSERVER: "Observer" }
const ACTION: Record<string, string> = { CASE_OPENED: "Case opened", VIEWED: "Opened the case", MEMBER_ADDED: "Person added", MEMBER_REMOVED: "Person removed", NOTE: "Note", EVIDENCE_COLLECTED: "Evidence collected", EVIDENCE_TRANSFERRED: "Evidence handed over", STATUS_CHANGED: "Status changed", CASE_CLOSED: "Case closed" }

export function InvestigationsSection() {
  const [list, setList] = useState<InvSummary[]>([])
  const [openId, setOpenId] = useState<string | null>(null)
  const [title, setTitle] = useState("")
  const [summary, setSummary] = useState("")
  const [error, setError] = useState("")

  const load = () => api.list().then(setList).catch(e => { setList([]); setError(msg(e, "Failed to load your cases")) })
  useEffect(() => { load() }, [])

  const open = async () => {
    setError("")
    try { const v = await api.open(title, summary); setTitle(""); setSummary(""); load(); setOpenId(v.summary.id) }
    catch (e: any) { setError(msg(e, "Failed to open the case")) }
  }

  if (openId) return <CaseDetail id={openId} onBack={() => { setOpenId(null); load() }} />
  return (
    <div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      <div style={{ fontSize: 12.5, color: MUTED, marginBottom: 10 }}>You see only the cases you are named on. Other people, including administrators, cannot see that a case exists unless they are named on it.</div>
      <Card title="Open a case">
        <input aria-label="Case title" style={{ ...area, minHeight: 0, marginBottom: 6 }} placeholder="Title (keep names and details out of it)" value={title} onChange={e => setTitle(e.target.value)} />
        <textarea aria-label="Case summary" style={area} placeholder="Summary (optional)" value={summary} onChange={e => setSummary(e.target.value)} />
        <button style={{ ...btn, marginTop: 6 }} disabled={!title.trim()} onClick={open}>Open case</button>
      </Card>
      <Card title="Your cases">
        {list.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>You are not named on any case.</span> : list.map(c => (
          <div key={c.id} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
            <span><strong>{c.number}</strong> {c.title} <span style={{ color: MUTED }}>· {STATUS[c.status] ?? c.status} · you are {ROLE[c.myRole ?? ""]?.toLowerCase() ?? "a member"} · {c.members} on the case</span></span>
            <button style={btn} onClick={() => setOpenId(c.id)}>Open</button>
          </div>
        ))}
      </Card>
    </div>
  )
}

function CaseDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const me = useAuthStore(s => s.user?.userId)
  const [v, setV] = useState<InvView | null>(null)
  const [users, setUsers] = useState<{ id: string; name: string }[]>([])
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [check, setCheck] = useState<InvVerification | null>(null)
  const [note, setNote] = useState("")
  const [evDesc, setEvDesc] = useState("")
  const [evSource, setEvSource] = useState("")
  const [person, setPerson] = useState("")
  const [role, setRole] = useState("INVESTIGATOR")
  const [closing, setClosing] = useState("")
  const [transfer, setTransfer] = useState<{ evidenceId: string; to: string; reason: string } | null>(null)

  useEffect(() => {
    api.get(id).then(setV).catch(e => setError(msg(e, "That case could not be found")))
    api.users().then(setUsers).catch(() => setUsers([]))
  }, [id])

  const run = async (fn: () => Promise<InvView | null>, fallback: string, after?: () => void) => {
    setBusy(true); setError("")
    try {
      const next = await fn()
      if (next === null) { onBack(); return }
      setV(next); setCheck(null); after?.()
    } catch (e: any) { setError(msg(e, fallback)) } finally { setBusy(false) }
  }

  if (!v) return error ? <div><div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div><button style={btn} onClick={onBack}>← Back</button></div> : <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>

  const s = v.summary
  const closed = s.status === "CLOSED"
  const lead = s.myRole === "LEAD"
  const writer = !closed && (lead || s.myRole === "INVESTIGATOR")
  const writers = v.members.filter(m => m.role !== "OBSERVER")
  const onCase = new Set(v.members.map(m => m.userId))
  return (
    <div>
      <button style={btn} onClick={onBack}>← Back</button>
      <h2 style={{ fontSize: 16, margin: "12px 0 2px" }}>{s.number} · {s.title}</h2>
      <div style={{ fontSize: 12.5, color: MUTED, marginBottom: 12 }}>{STATUS[s.status] ?? s.status} · you are {ROLE[s.myRole ?? ""]?.toLowerCase() ?? "a member"}. Everything you do here is written to this case's record.</div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      {v.summaryText && <Card title="Summary"><div style={{ fontSize: 13 }}>{v.summaryText}</div></Card>}
      {v.closeReason && <Card title="Closed"><div style={{ fontSize: 13 }}>{v.closeReason}</div></Card>}

      <Card title="People on this case">
        {v.members.map(m => (
          <div key={m.userId} style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", fontSize: 13 }}>
            <span>{m.name} <span style={{ color: MUTED }}>· {ROLE[m.role] ?? m.role}</span></span>
            {!closed && (lead || m.userId === me) && <button style={btn} disabled={busy} onClick={() => run(() => api.removeMember(id, m.userId), "Failed to remove")}>{m.userId === me ? "Leave" : "Remove"}</button>}
          </div>
        ))}
        {lead && !closed && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>
            <select aria-label="Person to add" value={person} onChange={e => setPerson(e.target.value)}>
              <option value="">Add a person…</option>{users.filter(u => !onCase.has(u.id)).map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
            <select aria-label="Role" value={role} onChange={e => setRole(e.target.value)}>{Object.entries(ROLE).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
            <button style={btn} disabled={busy || !person} onClick={() => run(() => api.addMember(id, person, role), "Failed to add", () => setPerson(""))}>Add</button>
          </div>
        )}
        <div style={{ fontSize: 12, color: MUTED, marginTop: 6 }}>A person added here also needs the Investigate permission to reach the case.</div>
      </Card>

      <Card title="Evidence">
        {v.evidence.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No evidence registered.</span> : v.evidence.map(e => (
          <div key={e.id} style={{ padding: "6px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
            <div><strong>{e.ref}</strong> {e.description}{e.source ? <span style={{ color: MUTED }}> · from {e.source}</span> : null}</div>
            <div style={{ color: MUTED }}>Held by {e.holderName}</div>
            {writer && (transfer?.evidenceId === e.id ? (
              <div style={{ display: "grid", gap: 6, marginTop: 4 }}>
                <select aria-label={`Hand ${e.ref} to`} value={transfer.to} onChange={ev => setTransfer({ ...transfer, to: ev.target.value })}>
                  <option value="">Hand to…</option>{writers.filter(w => w.userId !== e.holderId).map(w => <option key={w.userId} value={w.userId}>{w.name}</option>)}
                </select>
                <textarea aria-label={`Reason for handing over ${e.ref}`} style={area} placeholder="Why? (at least 10 characters)" value={transfer.reason} onChange={ev => setTransfer({ ...transfer, reason: ev.target.value })} />
                <div><button style={btn} disabled={busy || !transfer.to || transfer.reason.trim().length < MIN} onClick={() => run(() => api.transfer(id, e.id, transfer.to, transfer.reason), "Failed to hand over", () => setTransfer(null))}>Hand over</button></div>
              </div>
            ) : <button style={{ ...btn, marginTop: 4 }} onClick={() => setTransfer({ evidenceId: e.id, to: "", reason: "" })}>Hand over {e.ref}</button>)}
          </div>
        ))}
        {writer && (
          <div style={{ marginTop: 8, display: "grid", gap: 6 }}>
            <textarea aria-label="Evidence description" style={area} placeholder="What is it? (at least 10 characters)" value={evDesc} onChange={e => setEvDesc(e.target.value)} />
            <input aria-label="Evidence source" style={{ ...area, minHeight: 0 }} placeholder="Where it came from (optional)" value={evSource} onChange={e => setEvSource(e.target.value)} />
            <div><button style={btn} disabled={busy || evDesc.trim().length < MIN} onClick={() => run(() => api.collect(id, evDesc, evSource), "Failed to register", () => { setEvDesc(""); setEvSource("") })}>Register evidence</button></div>
          </div>
        )}
        <div style={{ fontSize: 12, color: MUTED, marginTop: 6 }}>This is a register of who holds what. The files themselves are not stored here.</div>
      </Card>

      {writer && (
        <Card title="Add a note">
          <textarea aria-label="Note" style={area} placeholder="At least 10 characters. Notes cannot be edited or deleted." value={note} onChange={e => setNote(e.target.value)} />
          <button style={{ ...btn, marginTop: 6 }} disabled={busy || note.trim().length < MIN} onClick={() => run(() => api.note(id, note), "Failed to add the note", () => setNote(""))}>Add note</button>
        </Card>
      )}

      <Card title="Case record">
        <div style={{ marginBottom: 8 }}>
          <button style={btn} onClick={() => api.verify(id).then(setCheck).catch(e => setError(msg(e, "Failed to check the record")))}>Check the record has not been altered</button>
          {check && <span role="status" style={{ marginLeft: 8, fontSize: 13, color: check.ok ? "var(--hf-success-text)" : "var(--hf-danger-text)" }}>{check.ok ? `Record intact (${check.entries} entries).` : `The record was altered at entry ${check.brokenAtSeq}.`}</span>}
        </div>
        {v.log.map(l => (
          <div key={l.seq} style={{ padding: "4px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 12.5 }}>
            <span style={{ color: MUTED }}>{l.seq}. {new Date(l.at).toLocaleString()} · {l.actorName} ·</span> <strong>{ACTION[l.action] ?? l.action}</strong>{l.detail ? `: ${l.detail}` : ""}
          </div>
        ))}
      </Card>

      {lead && !closed && (
        <Card title="Run the case">
          <button style={btn} disabled={busy} onClick={() => run(() => api.setReview(id, s.status !== "UNDER_REVIEW"), "Failed to change the status")}>{s.status === "UNDER_REVIEW" ? "Back to open" : "Put under review"}</button>
          <textarea aria-label="Reason for closing" style={{ ...area, marginTop: 8 }} placeholder="Reason for closing (at least 10 characters). A closed case cannot be reopened." value={closing} onChange={e => setClosing(e.target.value)} />
          <button style={{ ...btn, marginTop: 6 }} disabled={busy || closing.trim().length < MIN} onClick={() => run(() => api.close(id, closing), "Failed to close")}>Close case</button>
        </Card>
      )}
    </div>
  )
}
