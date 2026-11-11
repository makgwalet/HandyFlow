// src/pages/events/GuestToolsTab.tsx
//
// Bulk and door-side ways to add guests to the selected event: a walk-in at the door (event must be live; registers and checks in at once), a group of up
// to 50 registered all-or-nothing, and a CSV import that always previews first and only saves after the lawful-basis (POPIA) box is ticked.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle } from "../warehousing/whseUi"
import { PersonFields } from "./GuestOpsDialogs"
import {
  amountBody, canRunImport, emptyPerson, groupProblem, groupRows, IMPORT_TEMPLATE, importFileProblem, importSummary, MAX_GROUP, personBody, walkInProblem,
  type ImportReport, type Person,
} from "./guestOps"

interface Tier { id: string; name: string }
const field: React.CSSProperties = { ...inputStyle, width: "100%" }
const refreshGuests = (qc: ReturnType<typeof useQueryClient>, eventId: string) => {
  qc.invalidateQueries({ queryKey: ["event-guests", eventId] }); qc.invalidateQueries({ queryKey: ["event-stats", eventId] })
}

function TierSelect({ id, tiers, value, onChange, blankLabel }: { id: string; tiers: Tier[]; value: string; onChange: (v: string) => void; blankLabel: string }) {
  return (
    <select id={id} style={field} value={value} onChange={e => onChange(e.target.value)}>
      <option value="">{blankLabel}</option>{tiers.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
    </select>
  )
}

function WalkIn({ eventId, tiers }: { eventId: string; tiers: Tier[] }) {
  const qc = useQueryClient()
  const [p, setP] = useState<Person>(emptyPerson())
  const [tierId, setTierId] = useState(""); const [amount, setAmount] = useState(""); const [location, setLocation] = useState("")
  const go = useMutation({
    mutationFn: async () => (await apiClient.post(`/api/v1/events/${eventId}/guests/walk-in`, { ...personBody(p), tierId: tierId || null, amountPaid: amountBody(amount), location: location.trim() || null })).data,
    onSuccess: () => { refreshGuests(qc, eventId); setP(emptyPerson()); setAmount("") },
  })
  const problem = walkInProblem(p, amount)
  return (
    <section style={{ ...card, padding: 16 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Walk-in at the door</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Registers the person and checks them in at once. The event must be live.</p>
      <PersonFields p={p} set={(k, v) => setP(x => ({ ...x, [k]: v }))} idPrefix="wi" />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginTop: 10 }}>
        <div><label style={labelStyle} htmlFor="wi-tier">Ticket type</label><TierSelect id="wi-tier" tiers={tiers} value={tierId} onChange={setTierId} blankLabel="Default" /></div>
        <div><label style={labelStyle} htmlFor="wi-amt">Amount paid at the door (R)</label><input id="wi-amt" style={field} inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="wi-loc">Door / location</label><input id="wi-loc" style={field} maxLength={200} value={location} onChange={e => setLocation(e.target.value)} /></div>
      </div>
      <ErrorLine error={go.error} fallback="The walk-in could not be registered" />
      {go.isSuccess && <p role="status" style={{ fontSize: 12.5, color: "var(--hf-success-text-strong)", margin: "8px 0 0" }}>{go.data?.data?.guestName ?? "Guest"} is checked in.</p>}
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>{problem}</p>}
      <button type="button" style={{ ...btnPrimary, marginTop: 12, opacity: problem ? 0.5 : 1 }} disabled={!!problem || go.isPending} onClick={() => go.mutate()}>{go.isPending ? "Working…" : "Register and check in"}</button>
    </section>
  )
}

function Group({ eventId, tiers }: { eventId: string; tiers: Tier[] }) {
  const qc = useQueryClient()
  const [rows, setRows] = useState<Person[]>([emptyPerson(), emptyPerson()])
  const [tierId, setTierId] = useState(""); const [each, setEach] = useState("")
  const go = useMutation({
    mutationFn: async () => (await apiClient.post(`/api/v1/events/${eventId}/guests/group`, {
      tierId: tierId || null, amountPaidEach: amountBody(each), members: groupRows(rows).map(personBody),
    })).data,
    onSuccess: () => { refreshGuests(qc, eventId); setRows([emptyPerson(), emptyPerson()]); setEach("") },
  })
  const problem = groupProblem(rows, each)
  const set = (i: number, k: keyof Person, v: string) => setRows(rs => rs.map((r, j) => (j === i ? { ...r, [k]: v } : r)))
  return (
    <section style={{ ...card, padding: 16 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Group registration</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Up to {MAX_GROUP} people. Either everyone is registered or nobody is, for example when the event does not have room for all of them.</p>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 10 }}>
        <div><label style={labelStyle} htmlFor="gr-tier">Ticket type</label><TierSelect id="gr-tier" tiers={tiers} value={tierId} onChange={setTierId} blankLabel="Default" /></div>
        <div><label style={labelStyle} htmlFor="gr-each">Amount paid per person (R)</label><input id="gr-each" style={field} inputMode="decimal" value={each} onChange={e => setEach(e.target.value)} /></div>
      </div>
      {rows.map((r, i) => (
        <div key={i} style={{ display: "grid", gridTemplateColumns: "2fr 2fr 1.4fr auto", gap: 6, marginBottom: 6 }}>
          <input aria-label={`Person ${i + 1} name`} placeholder="Full name" style={field} maxLength={200} value={r.fullName} onChange={e => set(i, "fullName", e.target.value)} />
          <input aria-label={`Person ${i + 1} email`} placeholder="Email" style={field} maxLength={254} value={r.email} onChange={e => set(i, "email", e.target.value)} />
          <input aria-label={`Person ${i + 1} phone`} placeholder="Phone" style={field} maxLength={50} value={r.phone} onChange={e => set(i, "phone", e.target.value)} />
          <button type="button" style={btnGhost} aria-label={`Remove person ${i + 1}`} onClick={() => setRows(rs => rs.length > 1 ? rs.filter((_, j) => j !== i) : [emptyPerson()])}>×</button>
        </div>
      ))}
      <button type="button" style={btnGhost} disabled={rows.length >= MAX_GROUP} onClick={() => setRows(rs => [...rs, emptyPerson()])}>Add person</button>
      <ErrorLine error={go.error} fallback="The group could not be registered" />
      {go.isSuccess && <p role="status" style={{ fontSize: 12.5, color: "var(--hf-success-text-strong)", margin: "8px 0 0" }}>{go.data?.data?.count ?? 0} people registered.</p>}
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>{problem}</p>}
      <div><button type="button" style={{ ...btnPrimary, marginTop: 12, opacity: problem ? 0.5 : 1 }} disabled={!!problem || go.isPending} onClick={() => go.mutate()}>{go.isPending ? "Working…" : `Register ${groupRows(rows).length || ""} people`.replace("  ", " ")}</button></div>
    </section>
  )
}

function Import({ eventId, tiers }: { eventId: string; tiers: Tier[] }) {
  const qc = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [tierId, setTierId] = useState(""); const [send, setSend] = useState(false); const [consent, setConsent] = useState(false)
  const [preview, setPreview] = useState<ImportReport | null>(null)
  const [done, setDone] = useState<ImportReport | null>(null)
  const run = useMutation({
    mutationFn: async (dryRun: boolean) => {
      const fd = new FormData()
      fd.append("file", file as File)
      if (tierId) fd.append("tierId", tierId)
      fd.append("sendTickets", String(send)); fd.append("dryRun", String(dryRun)); fd.append("consentConfirmed", String(consent))
      return ((await apiClient.post(`/api/v1/events/${eventId}/guests/import`, fd)).data?.data ?? null) as ImportReport
    },
    onSuccess: (r, dryRun) => {
      if (dryRun) { setPreview(r); setDone(null) } else { setDone(r); setPreview(null); setFile(null); setConsent(false); refreshGuests(qc, eventId) }
    },
  })
  const fileProblem = importFileProblem(file ? { name: file.name, size: file.size } : null)
  const report = done ?? preview
  const issues = (report as any)?.rows?.filter((r: any) => r.outcome !== "IMPORTED") ?? []
  return (
    <section style={{ ...card, padding: 16 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Import guests from CSV</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Up to 500 rows. The columns are <code>full_name</code> (required), email, phone, company, dietary_requirements and tier. You always see a preview first; nothing is saved until you confirm.</p>
      <a download="guest-import-template.csv" href={`data:text/csv;charset=utf-8,${encodeURIComponent(IMPORT_TEMPLATE)}`} style={{ fontSize: 12 }}>Download a blank template</a>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, margin: "10px 0" }}>
        <div><label style={labelStyle} htmlFor="im-file">CSV file</label>
          <input id="im-file" type="file" accept=".csv,text/csv" onChange={e => { setFile(e.target.files?.[0] ?? null); setPreview(null); setDone(null) }} /></div>
        <div><label style={labelStyle} htmlFor="im-tier">Ticket type for rows without one</label><TierSelect id="im-tier" tiers={tiers} value={tierId} onChange={v => { setTierId(v); setPreview(null) }} blankLabel="Default" /></div>
      </div>
      <label style={{ display: "block", fontSize: 13 }}><input type="checkbox" checked={send} onChange={e => setSend(e.target.checked)} /> Email each person their ticket</label>
      <button type="button" style={{ ...btnGhost, marginTop: 10 }} disabled={!!fileProblem || run.isPending} onClick={() => run.mutate(true)}>{run.isPending && run.variables === true ? "Checking…" : "Preview import"}</button>
      {file && fileProblem && <p style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "8px 0 0" }}>{fileProblem}</p>}
      <ErrorLine error={run.error} fallback="The import could not be run" />
      {report && (
        <div style={{ marginTop: 12 }}>
          <p role="status" style={{ fontSize: 13, fontWeight: 700, margin: "0 0 6px" }}>{done ? "Import complete. " : "Preview: "}{importSummary(report)}</p>
          {issues.length > 0 && (
            <ul style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px", paddingLeft: 18, maxHeight: 160, overflowY: "auto" }}>
              {issues.slice(0, 50).map((r: any) => <li key={r.row}>Row {r.row}: {r.fullName ?? r.email ?? "(blank)"} — {r.message}</li>)}
            </ul>
          )}
          {preview && preview.imported > 0 && (
            <>
              <label style={{ display: "block", fontSize: 13, margin: "8px 0" }}>
                <input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} /> I confirm I have a lawful basis (POPIA) to register these people for this event.
              </label>
              <button type="button" style={{ ...btnPrimary, opacity: canRunImport(preview, consent) ? 1 : 0.5 }} disabled={!canRunImport(preview, consent) || run.isPending} onClick={() => run.mutate(false)}>
                {run.isPending && run.variables === false ? "Importing…" : `Import ${preview.imported} guests`}
              </button>
            </>
          )}
        </div>
      )}
    </section>
  )
}

export default function GuestToolsTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const allowed = usePermissions(["EVENTS_REGISTRATION_MANAGE", "EVENTS_CHECKIN", "EVENTS_MANAGE"])
  const canRegister = usePermissions(["EVENTS_REGISTRATION_MANAGE", "EVENTS_MANAGE"])
  const canWalkIn = usePermissions(["EVENTS_CHECKIN", "EVENTS_REGISTRATION_MANAGE", "EVENTS_MANAGE"])
  const { data: tiers = [] } = useQuery<Tier[]>({
    queryKey: ["event-tiers", eventId], enabled: !!eventId && allowed,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/events/${eventId}/tiers`); return r.data?.data ?? r.data ?? [] },
  })
  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  if (!allowed) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to add guests to events.</p>
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Adding guests to {eventTitle}</p>
      {canWalkIn && <WalkIn eventId={eventId} tiers={tiers} />}
      {canRegister && <Group eventId={eventId} tiers={tiers} />}
      {canRegister && <Import eventId={eventId} tiers={tiers} />}
    </div>
  )
}
