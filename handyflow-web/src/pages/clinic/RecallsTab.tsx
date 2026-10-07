// Recalls: patients who are due a follow-up and have not booked one. This is a worklist, not a report:
// search it, filter it, and work each row (call, book, snooze, dismiss) so it shrinks as the front desk goes.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient, keepPreviousData } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { CalendarClock, CalendarPlus, Phone, PhoneCall, AlarmClock, XCircle, RotateCcw, Search, AlertTriangle, Stethoscope, ChevronLeft, ChevronRight } from "lucide-react"
import ModalShell from "./ModalShell"
import { useDialogs } from "./dialogs"
import {
  FILTERS, OUTCOMES, PAGE_SIZE, contactSummary, dateInDays, pageCount, recallsUrl, snoozeProblem,
  type ContactOutcome, type Recall, type RecallFilter, type RecallPage,
} from "./recalls"

export interface RecallBookRequest { patientId: string; patientName: string; practitionerId?: string | null; reason: string }
interface Practitioner { id: string; fullName: string }

const fmtDay = (iso?: string | null) => iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const unwrap = (r: any) => r.data?.data ?? r.data
const errMsg = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."

const inp: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }
const btn = (primary = false): React.CSSProperties => ({ display: "inline-flex", alignItems: "center", gap: 5, padding: "6px 11px", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)", background: primary ? "var(--hf-accent)" : "var(--hf-surface)", color: primary ? "var(--hf-on-accent, #fff)" : "var(--hf-text)" })

export default function RecallsTab({ onBook }: { onBook?: (r: RecallBookRequest) => void }) {
  const qc = useQueryClient()
  const { confirm, prompt, dialogs } = useDialogs()
  const [filter, setFilter] = useState<RecallFilter>("ALL")
  const [search, setSearch] = useState("")
  const [q, setQ] = useState("")
  const [doctor, setDoctor] = useState("")
  const [page, setPage] = useState(0)
  const [logging, setLogging] = useState<Recall | null>(null)
  const [snoozing, setSnoozing] = useState<Recall | null>(null)
  const [error, setError] = useState("")

  useEffect(() => { const t = setTimeout(() => { setQ(search); setPage(0) }, 300); return () => clearTimeout(t) }, [search])

  const { data: practitioners = [] } = useQuery<Practitioner[]>({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => { const p = unwrap(await apiClient.get("/api/v1/clinic/practitioners/list")); return Array.isArray(p) ? p : (p?.content ?? []) },
  })
  const { data, isLoading, isError, isFetching } = useQuery<RecallPage>({
    queryKey: ["clinic-recalls", filter, q, doctor, page],
    queryFn: async () => unwrap(await apiClient.get(recallsUrl({ q, filter, practitionerId: doctor, page }))),
    placeholderData: keepPreviousData,
  })

  const act = useMutation({
    mutationFn: (v: { id: string; body: { type: string; outcome?: ContactOutcome; note?: string; snoozeUntil?: string } }) =>
      apiClient.post(`/api/v1/clinic/recalls/${v.id}/actions`, v.body),
    onSuccess: () => { setError(""); setLogging(null); setSnoozing(null); qc.invalidateQueries({ queryKey: ["clinic-recalls"] }) },
    onError: (e: any) => setError(errMsg(e)),
  })

  const rows = data?.content ?? []
  const counts = data?.counts
  const tabCount = (f: RecallFilter) => !counts ? null : ({ ALL: counts.open, OVERDUE: counts.overdue, TODAY: counts.dueToday, NOT_CONTACTED: counts.notContacted, SNOOZED: counts.snoozed, DISMISSED: null } as const)[f]

  const dismiss = async (r: Recall) => {
    const reason = await prompt({ title: `Dismiss recall for ${r.patientName}`, label: "Reason", placeholder: "e.g. moved away, followed up elsewhere, declined",
      body: "The recall disappears from the list. You can find it under Dismissed and reopen it.", confirmLabel: "Dismiss recall", danger: true, multiline: true })
    if (reason) act.mutate({ id: r.consultationId, body: { type: "DISMISS", note: reason } })
  }
  const reopen = async (r: Recall) => {
    if (await confirm({ title: `Bring ${r.patientName} back to the list?`, confirmLabel: "Reopen" })) act.mutate({ id: r.consultationId, body: { type: "REOPEN" } })
  }

  return (
    <div>
      {dialogs}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 14 }}>
        {FILTERS.map(f => {
          const n = tabCount(f.id), on = filter === f.id
          return (
            <button key={f.id} onClick={() => { setFilter(f.id); setPage(0) }} aria-pressed={on}
              style={{ ...btn(on), padding: "7px 14px", borderRadius: 20, background: on ? "var(--hf-primary-text)" : "var(--hf-surface)", color: on ? "var(--hf-surface)" : "var(--hf-text)" }}>
              {f.label}{n != null && <span style={{ opacity: 0.8 }}> · {n}</span>}
            </button>
          )
        })}
      </div>

      <div style={{ display: "flex", flexWrap: "wrap", gap: 10, marginBottom: 16, alignItems: "center" }}>
        <div style={{ position: "relative", flex: "1 1 260px", maxWidth: 380 }}>
          <Search size={14} style={{ position: "absolute", left: 10, top: 11, color: "var(--hf-text-muted)" }} />
          <input aria-label="Search recalls" placeholder="Search name, phone or diagnosis" value={search} onChange={e => setSearch(e.target.value)} style={{ ...inp, width: "100%", paddingLeft: 30, boxSizing: "border-box" }} />
        </div>
        <select aria-label="Doctor" value={doctor} onChange={e => { setDoctor(e.target.value); setPage(0) }} style={inp}>
          <option value="">All doctors</option>
          {practitioners.map(p => <option key={p.id} value={p.id}>Dr. {p.fullName}</option>)}
        </select>
        {isFetching && !isLoading && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Updating…</span>}
      </div>

      {error && <div role="alert" style={{ marginBottom: 12, padding: "9px 12px", borderRadius: 8, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-muted)" }}>Loading recalls…</div>
      ) : isError ? (
        <div role="alert" style={{ textAlign: "center", padding: 40, color: "var(--hf-danger-text)" }}>Could not load recalls.</div>
      ) : rows.length === 0 ? (
        <div style={{ textAlign: "center", padding: "56px 20px", color: "var(--hf-text-muted)", border: "1px dashed var(--hf-border)", borderRadius: 12 }}>
          <CalendarClock size={34} style={{ marginBottom: 10, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)", fontSize: 15 }}>{q || doctor ? "No recalls match" : filter === "ALL" ? "Nothing to chase" : "Nothing here"}</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>{q || doctor ? "Try a different name or doctor." : "Patients appear when a visit's follow-up date passes without a new booking."}</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {rows.map(r => <RecallRow key={r.consultationId} r={r} busy={act.isPending}
            onLog={() => setLogging(r)} onSnooze={() => setSnoozing(r)} onDismiss={() => dismiss(r)} onReopen={() => reopen(r)}
            onBook={() => onBook?.({ patientId: r.patientId, patientName: r.patientName, practitionerId: r.practitionerId, reason: "Follow-up" })} canBook={!!onBook} />)}
        </div>
      )}

      {data && data.total > PAGE_SIZE && (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: 12, marginTop: 16 }}>
          <button style={btn()} disabled={page === 0} onClick={() => setPage(p => p - 1)} aria-label="Previous page"><ChevronLeft size={14} /> Previous</button>
          <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Page {page + 1} of {pageCount(data.total)} · {data.total} patients</span>
          <button style={btn()} disabled={page + 1 >= pageCount(data.total)} onClick={() => setPage(p => p + 1)} aria-label="Next page">Next <ChevronRight size={14} /></button>
        </div>
      )}

      {logging && <LogCallModal r={logging} busy={act.isPending} onClose={() => setLogging(null)}
        onSave={(outcome, note) => act.mutate({ id: logging.consultationId, body: { type: "CONTACT", outcome, note: note || undefined } })} />}
      {snoozing && <SnoozeModal r={snoozing} busy={act.isPending} onClose={() => setSnoozing(null)}
        onSave={until => act.mutate({ id: snoozing.consultationId, body: { type: "SNOOZE", snoozeUntil: until } })} />}
    </div>
  )
}

function RecallRow({ r, busy, canBook, onLog, onSnooze, onDismiss, onReopen, onBook }:
  { r: Recall; busy: boolean; canBook: boolean; onLog: () => void; onSnooze: () => void; onDismiss: () => void; onReopen: () => void; onBook: () => void }) {
  const overdue = r.overdueDays > 0
  const calls = contactSummary(r)
  return (
    <div style={{ border: "1px solid var(--hf-border)", borderLeft: `4px solid ${r.status !== "OPEN" ? "var(--hf-text-muted)" : overdue ? "var(--hf-danger)" : "var(--hf-warning)"}`,
      borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
      <div style={{ minWidth: 0, flex: "1 1 280px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
          <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{r.patientName}</span>
          {r.status === "SNOOZED" ? <Pill bg="var(--hf-surface-muted)" fg="var(--hf-text-muted)">Snoozed until {fmtDay(r.snoozedUntil)}</Pill>
            : r.status === "DISMISSED" ? <Pill bg="var(--hf-surface-muted)" fg="var(--hf-text-muted)">Dismissed</Pill>
            : overdue ? <Pill bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)"><AlertTriangle size={10} /> {r.overdueDays}d overdue</Pill>
            : <Pill bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">Due today</Pill>}
          {calls && <Pill bg="var(--hf-accent-soft)" fg="var(--hf-accent-text)"><PhoneCall size={10} /> {calls}</Pill>}
        </div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", display: "flex", gap: 10, flexWrap: "wrap" }}>
          <span>Seen {fmtDay(r.consultedAt)} · follow-up due {fmtDay(r.dueDate)}</span>
          {r.practitionerName && <span style={{ display: "inline-flex", alignItems: "center", gap: 3 }}><Stethoscope size={11} /> Dr. {r.practitionerName}</span>}
          {r.diagnosis && <span>· {r.diagnosis}</span>}
        </div>
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
        {r.patientPhone && <a href={`tel:${r.patientPhone}`} style={{ ...btn(), textDecoration: "none" }}><Phone size={12} /> {r.patientPhone}</a>}
        {r.status === "OPEN" ? (<>
          <button style={btn()} disabled={busy} onClick={onLog}><PhoneCall size={12} /> Log call</button>
          {canBook && <button style={btn(true)} onClick={onBook}><CalendarPlus size={12} /> Book follow-up</button>}
          <button style={btn()} disabled={busy} onClick={onSnooze}><AlarmClock size={12} /> Snooze</button>
          <button style={btn()} disabled={busy} onClick={onDismiss}><XCircle size={12} /> Dismiss</button>
        </>) : (
          <button style={btn()} disabled={busy} onClick={onReopen}><RotateCcw size={12} /> Reopen</button>
        )}
      </div>
    </div>
  )
}

const Pill = ({ bg, fg, children }: { bg: string; fg: string; children: React.ReactNode }) =>
  <span style={{ display: "inline-flex", alignItems: "center", gap: 3, background: bg, color: fg, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{children}</span>

function LogCallModal({ r, busy, onClose, onSave }: { r: Recall; busy: boolean; onClose: () => void; onSave: (o: ContactOutcome, note: string) => void }) {
  const [outcome, setOutcome] = useState<ContactOutcome | "">("")
  const [note, setNote] = useState("")
  return (
    <ModalShell title={`Log call · ${r.patientName}`} onClose={onClose} width={440}
      footer={<><button style={btn()} onClick={onClose}>Cancel</button>
        <button style={btn(true)} disabled={!outcome || busy} onClick={() => outcome && onSave(outcome, note.trim())}>Save</button></>}>
      <fieldset style={{ border: "none", padding: 0, margin: "0 0 14px" }}>
        <legend style={{ fontSize: 12, fontWeight: 700, marginBottom: 8 }}>What happened?</legend>
        {OUTCOMES.map(o => (
          <label key={o.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "6px 0", fontSize: 13, cursor: "pointer" }}>
            <input type="radio" name="outcome" checked={outcome === o.id} onChange={() => setOutcome(o.id)} /> {o.label}
          </label>
        ))}
      </fieldset>
      <label style={{ fontSize: 12, fontWeight: 700 }}>Note (optional)
        <textarea value={note} maxLength={500} onChange={e => setNote(e.target.value)} rows={3} style={{ ...inp, width: "100%", boxSizing: "border-box", marginTop: 6, fontWeight: 400 }} />
      </label>
    </ModalShell>
  )
}

function SnoozeModal({ r, busy, onClose, onSave }: { r: Recall; busy: boolean; onClose: () => void; onSave: (until: string) => void }) {
  const [until, setUntil] = useState(dateInDays(7))
  const problem = snoozeProblem(until)
  return (
    <ModalShell title={`Snooze · ${r.patientName}`} onClose={onClose} width={420}
      footer={<><button style={btn()} onClick={onClose}>Cancel</button>
        <button style={btn(true)} disabled={!!problem || busy} onClick={() => onSave(until)}>Snooze</button></>}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 12 }}>Hide this recall until:</div>
      <div style={{ display: "flex", gap: 6, marginBottom: 12, flexWrap: "wrap" }}>
        {[3, 7, 14, 30].map(d => <button key={d} style={btn(until === dateInDays(d))} onClick={() => setUntil(dateInDays(d))}>{d} days</button>)}
      </div>
      <input type="date" aria-label="Snooze until" value={until} min={dateInDays(1)} onChange={e => setUntil(e.target.value)} style={inp} />
      {problem && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 6 }}>{problem}</div>}
    </ModalShell>
  )
}
