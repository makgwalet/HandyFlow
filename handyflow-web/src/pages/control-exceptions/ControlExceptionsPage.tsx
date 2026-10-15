// src/pages/control-exceptions/ControlExceptionsPage.tsx
//
// The shared "needs attention" board — Stage 1 of the Financial
// Control & Assurance plan, Option C. Every open exception raised by
// any module that's adopted ControlExceptionFacade, in one place.
// First (and currently only) real source: SCM's three-way match
// dispute flow, which now raises a parallel record here at the same
// moment it already notifies internally — see ScmService's
// createSupplierInvoice() dual-write hook.
//
// (Stage 1 note: it was view-and-resolve only — no filtering/sorting UI, no
// severity breakdown, nothing fancy. This is the first real screen for
// a brand-new concept; better to prove the concept works end-to-end
// with something simple than to over-build a dashboard for a board
// that, as of this pass, has exactly one real source feeding it.
import { useEffect, useState } from "react"
import { controlExceptionsApi, type ControlExceptionDetail, type ExceptionEvidence, type ExceptionScope, type RiskRating } from "../../api/controlExceptions.api"

const fmtDT = (d: any) => (d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—")

const NAVY = "var(--hf-primary)"
const NAVY_TEXT = "var(--hf-primary-text)";
const BORDER = "var(--hf-border)"
const CANVAS = "var(--hf-surface-muted)"
const INK = "var(--hf-text)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"

const MIN_TEXT = 10
const RATINGS: RiskRating[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
const tomorrow = () => { const d = new Date(); d.setDate(d.getDate() + 1); return d.toISOString().slice(0, 10) }
const errText = (e: any, fallback: string) => e?.response?.data?.message ?? fallback

export function ControlExceptionsPage() {
  const [scope, setScope] = useState<ExceptionScope>("open")
  const [rows, setRows] = useState<ControlExceptionDetail[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState<string | null>(null)

  const refetch = () => {
    setLoading(true)
    controlExceptionsApi.detailed(scope).then(setRows).finally(() => setLoading(false))
  }
  useEffect(refetch, [scope])

  const tabs: [ExceptionScope, string][] = [["open", "Open"], ["follow-ups", "Follow-ups"], ["all", "All"]]
  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 900 }}>
      <h1 style={{ fontSize: 20, fontWeight: 800, color: INK, marginBottom: 4 }}>Needs Attention</h1>
      <p style={{ fontSize: 13, color: MUTED, marginBottom: 16 }}>
        Everything flagged across the business. Rate the risk, give it an owner, record management's response, and check later that the fix held.
      </p>
      <div role="tablist" style={{ display: "flex", gap: 6, marginBottom: 16 }}>
        {tabs.map(([k, label]) => (
          <button key={k} role="tab" aria-selected={scope === k} onClick={() => setScope(k)}
            style={scope === k ? btnPrimary : btnSecondary}>{label}</button>
        ))}
      </div>

      {loading ? (
        <div style={{ padding: 32, textAlign: "center" as const, color: FAINT, fontSize: 13 }}>Loading…</div>
      ) : rows.length === 0 ? (
        <div style={{ padding: 40, textAlign: "center" as const, color: FAINT, fontSize: 14, background: "var(--hf-surface)",
          border: `1px solid ${BORDER}`, borderRadius: 8 }}>
          {scope === "follow-ups" ? "No follow-ups waiting." : scope === "all" ? "Nothing has been flagged." : "Nothing needs attention right now."}
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {rows.map(e => (
            <ExceptionCard key={e.id} e={e} expanded={open === e.id}
              onToggle={() => setOpen(open === e.id ? null : e.id)} onChanged={refetch} />
          ))}
        </div>
      )}
    </div>
  )
}

function ExceptionCard({ e, expanded, onToggle, onChanged }: { e: ControlExceptionDetail; expanded: boolean; onToggle: () => void; onChanged: () => void }) {
  return (
    <div style={{ background: "var(--hf-surface)", border: `1px solid ${e.overdue || e.followUpDue ? "var(--hf-danger-border)" : BORDER}`, borderRadius: 8, padding: "14px 16px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" as const }}>
            <SeverityBadge severity={e.severity} />
            {e.riskRating ? <Pill text={`${e.riskRating} risk`} tone={e.riskRating === "HIGH" || e.riskRating === "CRITICAL" ? "danger" : "muted"} /> : <Pill text="Not rated" tone="warning" />}
            {e.overdue && <Pill text="Overdue" tone="danger" />}
            {e.followUpDue && <Pill text="Follow-up due" tone="danger" />}
            {e.timesReopened > 0 && <Pill text={`Reopened ${e.timesReopened}×`} tone="warning" />}
            {e.status !== "OPEN" && <Pill text={e.status} tone="muted" />}
            <span style={{ fontSize: 11, color: FAINT, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>
              {e.sourceModule} · {e.controlType.replace(/_/g, " ")}
            </span>
          </div>
          <div style={{ fontSize: 13.5, color: INK }}>{e.description}</div>
          <div style={{ fontSize: 11, color: FAINT, marginTop: 6 }}>
            Flagged {fmtDT(e.detectedAt)}
            {e.ownerName && <> · Owner {e.ownerName}{e.targetDate && <> by {e.targetDate}</>}</>}
            {e.evidenceCount > 0 && <> · {e.evidenceCount} file{e.evidenceCount === 1 ? "" : "s"}</>}
          </div>
        </div>
        <button onClick={onToggle} style={btnSecondary}>{expanded ? "Close" : "Manage"}</button>
      </div>
      {expanded && <ManagePanel e={e} onChanged={onChanged} />}
    </div>
  )
}

function ManagePanel({ e, onChanged }: { e: ControlExceptionDetail; onChanged: () => void }) {
  const isOpen = e.status === "OPEN"
  const serious = e.riskRating === "HIGH" || e.riskRating === "CRITICAL"
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [users, setUsers] = useState<{ id: string; name: string }[]>([])
  const [files, setFiles] = useState<ExceptionEvidence[]>([])

  const [rating, setRating] = useState<RiskRating>(e.riskRating ?? "MEDIUM")
  const [rationale, setRationale] = useState(e.riskRationale ?? "")
  const [ownerId, setOwnerId] = useState(e.ownerId ?? "")
  const [targetDate, setTargetDate] = useState(e.targetDate ?? "")
  const [response, setResponse] = useState(e.responseText ?? "")
  const [notes, setNotes] = useState("")
  const [followUpDate, setFollowUpDate] = useState("")
  const [outcome, setOutcome] = useState<"HOLDING" | "RECURRED">("HOLDING")
  const [followNote, setFollowNote] = useState("")

  const loadFiles = () => controlExceptionsApi.evidence(e.id).then(setFiles).catch(() => setFiles([]))
  useEffect(() => { loadFiles() }, [e.id])
  useEffect(() => { if (isOpen) controlExceptionsApi.assignableUsers().then(setUsers).catch(() => setUsers([])) }, [isOpen])

  const run = async (fn: () => Promise<unknown>, fallback: string) => {
    setBusy(true); setError("")
    try { await fn(); onChanged() } catch (x: any) { setError(errText(x, fallback)) } finally { setBusy(false) }
  }
  const textOk = (t: string) => t.trim().length >= MIN_TEXT

  return (
    <div style={{ marginTop: 14, paddingTop: 14, borderTop: `1px solid ${BORDER}`, display: "flex", flexDirection: "column", gap: 16 }}>
      {error && <div role="alert" style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)" }}>{error}</div>}

      {isOpen && (
        <>
          <Section title="Risk rating">
            <select aria-label="Risk rating" value={rating} onChange={ev => setRating(ev.target.value as RiskRating)} style={input}>
              {RATINGS.map(r => <option key={r} value={r}>{r}</option>)}
            </select>
            <textarea aria-label="Reason for the rating" value={rationale} onChange={ev => setRationale(ev.target.value)} style={area} placeholder="Why this rating? (at least 10 characters)" />
            <button disabled={busy || !textOk(rationale)} style={btnPrimary} onClick={() => run(() => controlExceptionsApi.rate(e.id, rating, rationale), "Failed to save the rating")}>
              {e.riskRating ? "Update rating" : "Save rating"}
            </button>
          </Section>

          <Section title="Owner">
            <select aria-label="Owner" value={ownerId} onChange={ev => setOwnerId(ev.target.value)} style={input}>
              <option value="">Choose who owns this…</option>
              {users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
            <input aria-label="Deal with by" type="date" value={targetDate} onChange={ev => setTargetDate(ev.target.value)} style={input} />
            <button disabled={busy || !ownerId || !targetDate} style={btnPrimary} onClick={() => run(() => controlExceptionsApi.assign(e.id, ownerId, targetDate), "Failed to assign")}>Save owner</button>
          </Section>

          <Section title="Management response">
            <textarea aria-label="Management response" value={response} onChange={ev => setResponse(ev.target.value)} style={area} placeholder="What is being done about it? (at least 10 characters)" />
            <button disabled={busy || !textOk(response)} style={btnPrimary} onClick={() => run(() => controlExceptionsApi.respond(e.id, response), "Failed to save the response")}>Save response</button>
            {e.responseByName && <small style={{ color: FAINT }}>Last given by {e.responseByName}, {fmtDT(e.responseAt)}</small>}
          </Section>
        </>
      )}

      <Section title={`Evidence${serious && isOpen ? " (required to resolve)" : ""}`}>
        {files.length === 0 ? <small style={{ color: FAINT }}>No files attached.</small> : (
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12.5, color: INK }}>
            {files.map(f => <li key={f.id}>{f.fileName}{f.uploadedByName ? ` · ${f.uploadedByName}` : ""}</li>)}
          </ul>
        )}
        {isOpen && (
          <input aria-label="Attach evidence" type="file" disabled={busy}
            onChange={async ev => {
              const f = ev.target.files?.[0]; if (!f) return
              setBusy(true); setError("")
              try { await controlExceptionsApi.attach(e.id, f); await loadFiles(); onChanged() }
              catch (x: any) { setError(errText(x, "Failed to attach the file")) } finally { setBusy(false); ev.target.value = "" }
            }} />
        )}
      </Section>

      {isOpen && (
        <Section title="Resolve">
          {!e.riskRating && <small style={{ color: "var(--hf-warning-text)" }}>Rate the risk first.</small>}
          {serious && <small style={{ color: MUTED }}>High and critical risks need a management response, evidence, a follow-up date, and someone other than the person who gave the response to resolve them.</small>}
          <textarea aria-label="Resolution notes" value={notes} onChange={ev => setNotes(ev.target.value)} style={area} placeholder="What was done? (at least 10 characters)" />
          <label style={{ fontSize: 12, color: MUTED }}>Follow-up check on{serious ? "" : " (optional)"}
            <input aria-label="Follow-up date" type="date" min={tomorrow()} value={followUpDate} onChange={ev => setFollowUpDate(ev.target.value)} style={{ ...input, marginLeft: 8 }} />
          </label>
          <button disabled={busy || !e.riskRating || !textOk(notes) || (serious && !followUpDate)} style={btnPrimary}
            onClick={() => run(() => controlExceptionsApi.resolve(e.id, notes, followUpDate || undefined), "Failed to resolve")}>Mark Resolved</button>
        </Section>
      )}

      {!isOpen && e.status === "RESOLVED" && (
        <Section title="Resolution">
          <div style={{ fontSize: 13, color: INK }}>{e.resolutionNotes}</div>
          <small style={{ color: FAINT }}>Resolved by {e.resolvedByName ?? "—"}, {fmtDT(e.resolvedAt)}</small>
          {e.followUpOutcome ? (
            <small style={{ color: FAINT }}>Follow-up: {e.followUpOutcome === "HOLDING" ? "fix held" : "recurred"} — {e.followUpNote} ({e.followUpByName}, {fmtDT(e.followUpAt)})</small>
          ) : e.followUpDate ? (
            <>
              <small style={{ color: e.followUpDue ? "var(--hf-danger-text)" : FAINT }}>Follow-up check {e.followUpDue ? "was due" : "due"} {e.followUpDate}</small>
              {e.followUpDue && (
                <>
                  <select aria-label="Follow-up outcome" value={outcome} onChange={ev => setOutcome(ev.target.value as any)} style={input}>
                    <option value="HOLDING">Fix has held</option>
                    <option value="RECURRED">Problem came back (reopens it)</option>
                  </select>
                  <textarea aria-label="Follow-up note" value={followNote} onChange={ev => setFollowNote(ev.target.value)} style={area} placeholder="What did you check? (at least 10 characters)" />
                  <button disabled={busy || !textOk(followNote)} style={btnPrimary} onClick={() => run(() => controlExceptionsApi.followUp(e.id, outcome, followNote), "Failed to record the follow-up")}>Record follow-up</button>
                </>
              )}
            </>
          ) : null}
        </Section>
      )}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, alignItems: "flex-start" }}>
      <div style={{ fontSize: 12, fontWeight: 700, color: INK }}>{title}</div>
      {children}
    </div>
  )
}

function Pill({ text, tone }: { text: string; tone: "danger" | "warning" | "muted" }) {
  const t = { danger: ["var(--hf-danger-text)", "var(--hf-danger-soft)"], warning: ["var(--hf-warning-text)", "var(--hf-warning-soft)"], muted: [MUTED, CANVAS] }[tone]
  return <span style={{ background: t[1], color: t[0], padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{text}</span>
}

function SeverityBadge({ severity }: { severity: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    WARNING: { c: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
    CRITICAL: { c: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  }
  const t = tones[severity] ?? tones.WARNING
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{severity}</span>
}

const input: React.CSSProperties = { padding: "6px 8px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, fontFamily: "inherit" }
const area: React.CSSProperties = { width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, minHeight: 56, resize: "vertical" as const, fontFamily: "inherit", boxSizing: "border-box" as const }
const btnPrimary: React.CSSProperties = { padding: "7px 14px", background: NAVY, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnSecondary: React.CSSProperties = { padding: "6px 12px", background: "var(--hf-surface)", color: NAVY_TEXT, border: `1px solid ${NAVY}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
