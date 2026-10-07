// src/pages/security/GuardProfilePage.tsx
//
// Guard 360: one routed page per guard (/security/guards/:guardId) that gathers what the system already holds about
// a guard: personal and employment details, PSiRA, screening history, the guard file, shifts and incidents.
// Data arrives in one call (GET /guards/{id}/overview). Rules live in guard360.logic.ts.
import { useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertOctagon, ArrowLeft, CalendarClock, ClipboardCheck, FileText, Plus, ShieldAlert, ShieldCheck, Trash2, UserRound } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import { PageHeader } from "../../components/ui/PageHeader"
import GuardScreeningPanel, { RequestScreening } from "./GuardScreeningPanel"
import SkillsTab from "./GuardCompetencyPanel"
import ComplaintsTab from "./ComplaintsTab"
import GuardPerformancePanel from "./GuardPerformancePanel"
import type { ComplaintCounts, ComplaintSummary } from "./complaints.logic"
import {
  DOCUMENT_CATEGORIES, SEVERITY_TONE, SHIFT_TONE, categoryLabel, completionRate, expiryState,
  fileChecklist, isUpcoming, readinessState, readinessTone, screeningLabel, todayIso,
  type CompetencyItem, type DocumentItem, type Readiness, type ScreeningItem, type ShiftItem,
} from "./guard360.logic"

interface Overview {
  guard: {
    id: string; firstName: string; lastName: string; fullName: string; psiraNumber: string | null; idNumber: string | null
    phone: string | null; photoUrl: string | null; grade: string; status: string; statusNote: string | null
    psiraExpiryDate: string | null; employeeCode: string | null; emergencyContactName: string | null
    emergencyContactPhone: string | null; cpVettingTier: string | null; notes: string | null; createdAt: string
    bankName: string | null
  }
  screeningGate: string | null
  documents: DocumentItem[]
  screening: ScreeningItem[]
  readiness: Readiness
  competencies: CompetencyItem[]
  shifts: ShiftItem[]
  incidents: { id: string; siteName: string | null; title: string; severity: string; status: string; reportedAt: string }[]
  counts: { shiftsLast90Days: number; completedLast90Days: number; incidentsLast180Days: number; openIncidents: number }
  complaints?: ComplaintSummary[]
  complaintCounts?: ComplaintCounts
}

const TABS = ["Overview", "Compliance", "Skills", "Documents", "Shifts", "Incidents", "Complaints", "Performance"] as const
type Tab = typeof TABS[number]

const STATUS_TONE: Record<string, "ok" | "warn" | "bad" | "info" | "neutral"> = { ACTIVE: "ok", ON_LEAVE: "info", SUSPENDED: "bad", UNDER_INVESTIGATION: "warn", TERMINATED: "neutral" }
const fmtDate = (iso: string | null | undefined) => iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: "Africa/Johannesburg" }) : "-"
const fmtDateTime = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)" }

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: 14, color: "var(--hf-text-primary)", marginTop: 2, overflowWrap: "anywhere" }}>{value || "-"}</div>
    </div>
  )
}

export default function GuardProfilePage() {
  const { guardId } = useParams<{ guardId: string }>()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>("Overview")
  const [openCompetency, setOpenCompetency] = useState<string | null>(null)
  const canManage = usePermission("SECURITY_MANAGE") || usePermission("SECURITY_ADMIN")
  const { data, isLoading, error } = useQuery<Overview>({
    queryKey: ["guard-overview", guardId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/guards/${guardId}/overview`); return r.data?.data ?? r.data },
    enabled: !!guardId,
  })

  const header = (title: string) => (
    <PageHeader title={title} icon={UserRound} breadcrumbs={[{ label: "Security", to: "/security/dashboard" }, { label: "Guards", to: "/security/guards" }, { label: title }]} />
  )
  if (isLoading) return <div>{header("Guard")}<div style={{ color: "var(--hf-text-muted)" }}>Loading guard...</div></div>
  if (error || !data) return (
    <div>{header("Guard")}
      <div style={{ ...card, color: "var(--hf-danger-text)" }}>This guard could not be loaded. They may have been removed, or you may not have access.</div>
      <button onClick={() => navigate("/security/guards")} style={{ marginTop: 12, ...linkBtn }}><ArrowLeft size={14} /> Back to guards</button>
    </div>
  )

  const { guard } = data
  const today = todayIso()
  const psira = expiryState(guard.psiraExpiryDate, today)
  const readiness = data.readiness
  const rate = completionRate(data.counts.shiftsLast90Days, data.counts.completedLast90Days)
  const upcoming = data.shifts.filter(s => isUpcoming(s, new Date()))
  const hasPhoto = guard.photoUrl && guard.photoUrl !== "PENDING_UPLOAD"

  return (
    <div>
      {header(guard.fullName)}
      <div style={{ ...card, display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap", marginBottom: 16 }}>
        {hasPhoto
          ? <img src={guard.photoUrl!} alt={guard.fullName} style={{ width: 64, height: 64, borderRadius: "50%", objectFit: "cover" }} />
          : <div style={{ width: 64, height: 64, borderRadius: "50%", background: "var(--hf-info-soft)", color: "var(--hf-info-text)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 22 }}>{guard.firstName?.[0]}{guard.lastName?.[0]}</div>}
        <div style={{ flex: "1 1 220px", minWidth: 0 }}>
          <div style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-text-primary)" }}>{guard.fullName}</div>
          <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 2 }}>Guard ID: {guard.employeeCode ?? "not set"} · Grade {guard.grade}</div>
          <div style={{ display: "flex", gap: 6, marginTop: 8, flexWrap: "wrap" }}>
            <Chip tone={STATUS_TONE[guard.status] ?? "neutral"}>{guard.status.replace(/_/g, " ").toLowerCase()}</Chip>
            {guard.psiraNumber && <Chip tone={psira.tone} title={psira.label}>PSiRA {psira.tone === "ok" ? "valid" : psira.label.toLowerCase()}</Chip>}
            {guard.cpVettingTier && <Chip tone="accent">Close protection {guard.cpVettingTier.toLowerCase()}</Chip>}
            <Chip tone={readinessTone(readiness)} icon={readiness.ready ? <ShieldCheck size={12} /> : <ShieldAlert size={12} />}>
              {readiness.ready ? `Ready to deploy, ${readiness.percent}%` : `Not ready, ${readiness.percent}%`}
            </Chip>
          </div>
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <Link to="/security/shifts" style={linkBtn}><CalendarClock size={14} /> Shifts</Link>
          <Link to="/security/incidents" style={linkBtn}><ShieldAlert size={14} /> Incidents</Link>
          <Link to="/security/guards" style={linkBtn}><ArrowLeft size={14} /> All guards</Link>
        </div>
      </div>

      <div role="tablist" style={{ display: "flex", gap: 4, flexWrap: "wrap", borderBottom: "1px solid var(--hf-border)", marginBottom: 16 }}>
        {TABS.map(t => (
          <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
            style={{ padding: "9px 14px", border: "none", background: "none", cursor: "pointer", fontSize: 13, fontWeight: 700,
              color: tab === t ? "var(--hf-accent-text)" : "var(--hf-text-muted)", borderBottom: tab === t ? "2px solid var(--hf-accent)" : "2px solid transparent" }}>
            {t}{t === "Incidents" && data.counts.openIncidents > 0 ? ` (${data.counts.openIncidents} open)` : ""}{t === "Complaints" && (data.complaintCounts?.open ?? 0) > 0 ? ` (${data.complaintCounts!.open} open)` : ""}
          </button>
        ))}
      </div>

      {tab === "Overview" && (
        <div style={{ display: "grid", gap: 16 }}>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <StatTile label="Shifts, last 90 days" value={data.counts.shiftsLast90Days} icon={<CalendarClock size={18} />} />
            <StatTile label="Shifts completed" value={rate === null ? "-" : `${rate}%`} hint={`${data.counts.completedLast90Days} of ${data.counts.shiftsLast90Days}`} tone={rate !== null && rate < 80 ? "warn" : "neutral"} icon={<ClipboardCheck size={18} />} />
            <StatTile label="Incidents, last 180 days" value={data.counts.incidentsLast180Days} hint={`${data.counts.openIncidents} open`} tone={data.counts.openIncidents > 0 ? "warn" : "neutral"} icon={<ShieldAlert size={18} />} />
            <StatTile label="Complaints, last 90 days" value={data.complaintCounts?.last90Days ?? 0} hint={`${data.complaintCounts?.open ?? 0} open`} tone={(data.complaintCounts?.open ?? 0) > 0 ? "warn" : "neutral"} icon={<AlertOctagon size={18} />} />
            <StatTile label="Documents on file" value={data.documents.length} icon={<FileText size={18} />} />
          </div>
          <div style={{ ...card, display: "flex", gap: 20, alignItems: "center", flexWrap: "wrap" }}>
            <ReadinessRing readiness={readiness} />
            <div style={{ flex: "1 1 260px", minWidth: 0 }}>
              <div style={{ fontWeight: 800, marginBottom: 6 }}>Deployment readiness</div>
              {readiness.reasons.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Every required check is met. Open the Compliance tab for the detail.</div> :
                <ul style={{ margin: 0, padding: 0, listStyle: "none", display: "grid", gap: 6 }}>
                  {readiness.reasons.map((r, i) => <li key={i} style={{ fontSize: 13 }}>{r}</li>)}
                </ul>}
              <button onClick={() => setTab("Compliance")} style={{ ...linkBtn, marginTop: 10 }}>Open compliance</button>
            </div>
          </div>
          <div style={{ display: "grid", gap: 16, gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))" }}>
            <div style={card}>
              <div style={{ fontWeight: 800, marginBottom: 10 }}>Personal</div>
              <div style={{ display: "grid", gap: 10, gridTemplateColumns: "1fr 1fr" }}>
                <Field label="ID number" value={guard.idNumber} />
                <Field label="Phone" value={guard.phone} />
                <Field label="Emergency contact" value={guard.emergencyContactName} />
                <Field label="Emergency phone" value={guard.emergencyContactPhone} />
              </div>
            </div>
            <div style={card}>
              <div style={{ fontWeight: 800, marginBottom: 10 }}>Employment and PSiRA</div>
              <div style={{ display: "grid", gap: 10, gridTemplateColumns: "1fr 1fr" }}>
                <Field label="Guard ID" value={guard.employeeCode} />
                <Field label="Grade" value={guard.grade} />
                <Field label="PSiRA number" value={guard.psiraNumber} />
                <Field label="PSiRA expiry" value={<>{fmtDate(guard.psiraExpiryDate)} <Chip tone={psira.tone}>{psira.label}</Chip></>} />
                <Field label="Bank" value={guard.bankName} />
                <Field label="Added" value={fmtDate(guard.createdAt)} />
              </div>
              {guard.statusNote && <div style={{ marginTop: 10, fontSize: 12, color: "var(--hf-text-muted)" }}>Status note: {guard.statusNote}</div>}
            </div>
          </div>
          <div style={card}>
            <div style={{ fontWeight: 800, marginBottom: 8 }}>Upcoming shifts</div>
            {upcoming.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No shifts scheduled in the next 14 days.</div> :
              upcoming.slice(0, 5).map(s => <div key={s.id} style={{ fontSize: 13, padding: "4px 0" }}>{fmtDateTime(s.startAt)} to {fmtDateTime(s.endAt)} · {s.siteName ?? "Unknown site"}</div>)}
          </div>
        </div>
      )}

      {tab === "Compliance" && <ComplianceTab guardId={guard.id} readiness={readiness} screening={data.screening} onOpenCompetency={id => { setOpenCompetency(id); setTab("Skills") }} />}
      {tab === "Skills" && <SkillsTab guardId={guard.id} competencies={data.competencies ?? []} canManage={canManage} initialOpenId={openCompetency} />}

      {tab === "Documents" && <DocumentsTab guardId={guard.id} docs={data.documents} />}

      {tab === "Shifts" && (
        <div style={{ ...card, overflowX: "auto" }}>
          {data.shifts.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No shifts in the last 90 days or the next 14.</div> :
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Start</th><th style={th}>End</th><th style={th}>Site</th><th style={th}>Status</th></tr></thead>
              <tbody>{data.shifts.map(s => (
                <tr key={s.id}><td style={td}>{fmtDateTime(s.startAt)}</td><td style={td}>{fmtDateTime(s.endAt)}</td><td style={td}>{s.siteName ?? "-"}</td>
                  <td style={td}><Chip tone={SHIFT_TONE[s.status] ?? "neutral"}>{s.status.toLowerCase()}</Chip></td></tr>
              ))}</tbody>
            </table>}
          {data.counts.shiftsLast90Days > data.shifts.length && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>Showing the newest {data.shifts.length}.</div>}
        </div>
      )}

      {tab === "Performance" && <GuardPerformancePanel guardId={guard.id} canManage={canManage} />}

      {tab === "Complaints" && <ComplaintsTab guardId={guard.id} />}

      {tab === "Incidents" && (
        <div style={{ ...card, overflowX: "auto" }}>
          {data.incidents.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No incidents involving this guard in the last 180 days.</div> :
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Reported</th><th style={th}>Incident</th><th style={th}>Site</th><th style={th}>Severity</th><th style={th}>Status</th></tr></thead>
              <tbody>{data.incidents.map(i => (
                <tr key={i.id}><td style={td}>{fmtDateTime(i.reportedAt)}</td><td style={td}>{i.title}</td><td style={td}>{i.siteName ?? "-"}</td>
                  <td style={td}><Chip tone={SEVERITY_TONE[i.severity] ?? "neutral"}>{i.severity.toLowerCase()}</Chip></td>
                  <td style={td}>{i.status.toLowerCase()}</td></tr>
              ))}</tbody>
            </table>}
        </div>
      )}
    </div>
  )
}

const linkBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer", textDecoration: "none" }

function ReadinessRing({ readiness }: { readiness: Readiness }) {
  const tone = readinessTone(readiness)
  const color = tone === "ok" ? "var(--hf-success-text-strong)" : tone === "warn" ? "var(--hf-warning-text)" : "var(--hf-danger-text)"
  const r = 44, c = 2 * Math.PI * r
  return (
    <div role="img" aria-label={`Deployment readiness ${readiness.percent} percent, ${readiness.ready ? "ready" : "not ready"}`} style={{ position: "relative", width: 112, height: 112, flexShrink: 0 }}>
      <svg width="112" height="112" viewBox="0 0 112 112">
        <circle cx="56" cy="56" r={r} fill="none" stroke="var(--hf-surface-sunken)" strokeWidth="10" />
        <circle cx="56" cy="56" r={r} fill="none" stroke={color} strokeWidth="10" strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c * (1 - readiness.percent / 100)} transform="rotate(-90 56 56)" />
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
        <div style={{ fontSize: 24, fontWeight: 800, color }}>{readiness.percent}%</div>
        <div style={{ fontSize: 11, fontWeight: 700, color }}>{readiness.ready ? "READY" : "NOT READY"}</div>
      </div>
    </div>
  )
}

function ComplianceTab({ guardId, readiness, screening, onOpenCompetency }: { guardId: string; readiness: Readiness; screening: ScreeningItem[]; onOpenCompetency: (id: string) => void }) {
  const canManage = usePermission("SECURITY_MANAGE") || usePermission("SECURITY_ADMIN")
  const [openId, setOpenId] = useState<string | null>(null)
  const [requesting, setRequesting] = useState(false)
  const open = screening.find(s => s.id === openId) ?? null
  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
          <div style={{ fontWeight: 800 }}>Compliance matrix</div>
          {canManage && <button onClick={() => setRequesting(true)} style={{ ...linkBtn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }}><Plus size={14} /> Request screening</button>}
        </div>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Check</th><th style={th}>Evidence</th><th style={th}>Valid until</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {readiness.items.map(i => {
                const st = readinessState(i.state)
                return (
                  <tr key={i.key}>
                    <td style={td}>{i.label}{i.required ? "" : <span style={{ color: "var(--hf-text-muted)", fontSize: 11 }}> (not required)</span>}</td>
                    <td style={td}>{i.screeningId || i.competencyId ? (i.evidenceCount > 0 ? `${i.evidenceCount} file${i.evidenceCount === 1 ? "" : "s"}` : "None") : "-"}</td>
                    <td style={td}>{fmtDate(i.validUntil)}</td>
                    <td style={td}><Chip tone={st.tone}>{st.label}</Chip> <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{i.detail}</span></td>
                    <td style={td}>
                      {i.screeningId && <button onClick={() => setOpenId(i.screeningId)} style={{ ...linkBtn, padding: "4px 10px" }}>Open</button>}
                      {i.competencyId && <button onClick={() => onOpenCompetency(i.competencyId!)} style={{ ...linkBtn, padding: "4px 10px" }}>Open</button>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>
          Required for readiness: PSiRA registration, ID copy, criminal record check, reference check and drug test, each passed, with an evidence file, and in date. Others are shown, and only a failed or expired one blocks readiness.
        </div>
      </div>

      {requesting && <RequestScreening guardId={guardId} onClose={() => setRequesting(false)} />}
      {open && <GuardScreeningPanel key={open.id} guardId={guardId} record={open} canManage={canManage} onClose={() => setOpenId(null)} />}

      <div style={card}>
        <div style={{ fontWeight: 800, marginBottom: 8 }}>Screening history ({screening.length})</div>
        {screening.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No screening records yet.</div> :
          screening.map(s => (
            <div key={s.id} style={{ fontSize: 13, padding: "5px 0", display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <span>{fmtDate(s.createdAt)} · {screeningLabel(s.screeningType)} · {s.reason.replace(/_/g, " ").toLowerCase()} · {s.result.toLowerCase()}{s.decision ? ` · ${s.decision === "CLEARED" ? "cleared" : "not cleared"}` : ""}</span>
              <button onClick={() => setOpenId(s.id)} style={{ ...linkBtn, padding: "3px 9px" }}>Open</button>
            </div>
          ))}
      </div>
    </div>
  )
}

function DocumentsTab({ guardId, docs }: { guardId: string; docs: DocumentItem[] }) {
  const qc = useQueryClient()
  const canManage = usePermission("SECURITY_MANAGE") || usePermission("SECURITY_ADMIN")
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ category: "ID_COPY", fileUrl: "", fileName: "", notes: "" })
  const [removing, setRemoving] = useState<DocumentItem | null>(null)
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["guard-overview", guardId] })
  const msg = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/guards/${guardId}/documents`, { category: form.category, fileUrl: form.fileUrl.trim(), fileName: form.fileName.trim() || null, notes: form.notes.trim() || null }),
    onSuccess: () => { setAdding(false); setForm({ category: "ID_COPY", fileUrl: "", fileName: "", notes: "" }); setErr(""); refresh() },
    onError: e => setErr(msg(e)),
  })
  const remove = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/security/guards/${guardId}/documents/${removing!.id}`, { data: { reason: reason.trim() } }),
    onSuccess: () => { setRemoving(null); setReason(""); setErr(""); refresh() },
    onError: e => setErr(msg(e)),
  })
  const checklist = fileChecklist(docs)
  const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div style={card}>
        <div style={{ fontWeight: 800, marginBottom: 8 }}>Guard file checklist</div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {checklist.map(c => <Chip key={c.category} tone={c.present ? "ok" : "warn"}>{c.label}{c.present ? "" : " missing"}</Chip>)}
        </div>
      </div>
      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
          <div style={{ fontWeight: 800 }}>Documents ({docs.length})</div>
          {canManage && <button onClick={() => { setAdding(true); setErr("") }} style={{ ...linkBtn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }}><Plus size={14} /> Add document</button>}
        </div>
        {docs.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No documents on this guard's file yet.</div> :
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Type</th><th style={th}>File</th><th style={th}>Added</th><th style={th}>Notes</th><th style={th}></th></tr></thead>
            <tbody>{docs.map(d => (
              <tr key={d.id}>
                <td style={td}>{categoryLabel(d.category)}</td>
                <td style={td}>{d.fileUrl && d.fileUrl !== "PENDING_UPLOAD" ? <a href={d.fileUrl} target="_blank" rel="noreferrer" style={{ color: "var(--hf-accent-text)" }}>{d.fileName ?? "Open"}</a> : <span title="The file itself was not stored">{d.fileName ?? "No file stored"}</span>}</td>
                <td style={td}>{fmtDate(d.createdAt)}</td>
                <td style={td}>{d.notes ?? "-"}</td>
                <td style={td}>{canManage && <button aria-label={`Remove ${categoryLabel(d.category)}`} onClick={() => { setRemoving(d); setReason(""); setErr("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={14} /></button>}</td>
              </tr>
            ))}</tbody>
          </table>}
      </div>

      {adding && (
        <div style={{ ...card, display: "grid", gap: 10 }}>
          <div style={{ fontWeight: 800 }}>Add a document</div>
          <label style={{ fontSize: 12 }}>Type
            <select value={form.category} onChange={e => setForm({ ...form, category: e.target.value })} style={input}>
              {DOCUMENT_CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select></label>
          <label style={{ fontSize: 12 }}>Link to the file
            <input value={form.fileUrl} onChange={e => setForm({ ...form, fileUrl: e.target.value })} placeholder="https://..." style={input} /></label>
          <label style={{ fontSize: 12 }}>File name (optional)
            <input value={form.fileName} onChange={e => setForm({ ...form, fileName: e.target.value })} style={input} /></label>
          <label style={{ fontSize: 12 }}>Notes (optional)
            <input value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} style={input} /></label>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>For now a document is a link to where the file is kept. Uploading the file itself arrives with the evidence vault.</div>
          {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={!form.fileUrl.trim() || add.isPending} onClick={() => add.mutate()} style={{ ...linkBtn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }}>Save document</button>
            <button onClick={() => setAdding(false)} style={linkBtn}>Cancel</button>
          </div>
        </div>
      )}

      {removing && (
        <div style={{ ...card, display: "grid", gap: 10 }}>
          <div style={{ fontWeight: 800 }}>Remove {categoryLabel(removing.category)}?</div>
          <label style={{ fontSize: 12 }}>Reason (required, kept in the audit trail)
            <input value={reason} onChange={e => setReason(e.target.value)} style={input} /></label>
          {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={!reason.trim() || remove.isPending} onClick={() => remove.mutate()} style={{ ...linkBtn, background: "var(--hf-danger-text)", color: "var(--hf-text-on-solid)", border: "none" }}>Remove document</button>
            <button onClick={() => setRemoving(null)} style={linkBtn}>Keep it</button>
          </div>
        </div>
      )}
    </div>
  )
}
