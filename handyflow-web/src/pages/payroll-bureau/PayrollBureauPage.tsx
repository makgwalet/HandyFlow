// src/pages/payroll-bureau/PayrollBureauPage.tsx
//
// UPDATED: added Bureau Profile — the tenant's OWN practice identity
// (firm name, registration, SDL number, contact details, logo URL),
// distinct from any individual client. Uses PayBureauProfile's existing
// getProfile()/upsertProfile() backend, which was already fully built
// with zero UI before this change. Uses a plain logoUrl string field —
// NOT the Evidence-backed upload system built for PayClient earlier —
// two different, coexisting mechanisms, matching what's real rather
// than silently unifying them.
import { useEffect, useState } from "react"
import { payrollBureauApi } from "../../api/payrollBureau.api"
import type { PayClient, PayEmployee, PayRun, Payslip, PayDeadline, PayFeeNote, PortalAccessGrant } from "../../types/payrollBureau.types"
import { required, validateSaId, validateTaxNumber, validateEmail, validatePhone,
  validatePositiveNumber, validateDayOfMonth, type FieldErrors } from "./validation"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const CANVAS = "#F8FAFC"
const INK = "#0F172A"
const MUTED = "#64748B"
const FAINT = "#94A3B8"

type Tab = "employees" | "payruns" | "deadlines" | "feenotes" | "portal"

// Authenticated logo image — a plain <img src=...> can't work here, the
// logo download endpoint requires a Bearer token and browsers send no
// auth header on image requests. Fetches as a blob through the
// authenticated API client instead, same pattern already proven for
// payslip PDF downloads.
function LogoImage({ clientId, maxHeight = 48, refreshKey }: { clientId: string; maxHeight?: number; refreshKey?: number }) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false
    setFailed(false)
    payrollBureauApi.downloadLogo(clientId)
      .then(blob => {
        if (cancelled) return
        objectUrl = window.URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => { if (!cancelled) setFailed(true) })
    return () => { cancelled = true; if (objectUrl) window.URL.revokeObjectURL(objectUrl) }
  }, [clientId, refreshKey])

  if (failed || !url) return null
  return <img src={url} alt="Client logo" style={{ maxHeight, maxWidth: 160, objectFit: "contain" as const }} />
}

// NEW: the bureau's own practice profile — tenant-wide, not per-client.
// getProfile()/upsertProfile() already existed fully on the backend with
// zero UI before this change.
function BureauProfileModal({ onClose }: { onClose: () => void }) {
  const [firmName, setFirmName] = useState("")
  const [registrationNumber, setRegistrationNumber] = useState("")
  const [sdlNumber, setSdlNumber] = useState("")
  const [email, setEmail] = useState("")
  const [phone, setPhone] = useState("")
  const [physicalAddress, setPhysicalAddress] = useState("")
  const [logoUrl, setLogoUrl] = useState("")
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    payrollBureauApi.getProfile()
      .then((p: any) => {
        setFirmName(p.firmName ?? "")
        setRegistrationNumber(p.registrationNumber ?? "")
        setSdlNumber(p.sdlNumber ?? "")
        setEmail(p.email ?? "")
        setPhone(p.phone ?? "")
        setPhysicalAddress(p.physicalAddress ?? "")
        setLogoUrl(p.logoUrl ?? "")
      })
      .catch(() => { /* no profile yet — start blank, matching upsertProfile()'s own create-on-first-save fallback */ })
      .finally(() => setLoading(false))
  }, [])

  const submit = async () => {
    if (!firmName.trim()) { setError("Firm name is required"); return }
    setSaving(true); setError("")
    try {
      await payrollBureauApi.upsertProfile({
        firmName, registrationNumber: registrationNumber || undefined, sdlNumber: sdlNumber || undefined,
        email: email || undefined, phone: phone || undefined,
        physicalAddress: physicalAddress || undefined, logoUrl: logoUrl || undefined,
      })
      onClose()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to save profile")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="Bureau Profile" onClose={onClose}>
      {loading ? (
        <div style={{ color: FAINT, fontSize: 13, padding: "12px 0" }}>Loading…</div>
      ) : (
        <>
          <Field label="Firm name *"><input value={firmName} onChange={e => setFirmName(e.target.value)} style={inputStyle} /></Field>
          <Field label="Registration number (optional)"><input value={registrationNumber} onChange={e => setRegistrationNumber(e.target.value)} style={inputStyle} /></Field>
          <Field label="SDL number (optional)"><input value={sdlNumber} onChange={e => setSdlNumber(e.target.value)} style={inputStyle} /></Field>
          <div style={{ display: "flex", gap: 10 }}>
            <Field label="Email"><input type="email" value={email} onChange={e => setEmail(e.target.value)} style={inputStyle} /></Field>
            <Field label="Phone"><input value={phone} onChange={e => setPhone(e.target.value)} style={inputStyle} /></Field>
          </div>
          <Field label="Physical address (optional)">
            <textarea value={physicalAddress} onChange={e => setPhysicalAddress(e.target.value)}
              style={{ ...inputStyle, minHeight: 50, resize: "vertical" as const, fontFamily: "inherit" }} />
          </Field>
          <div style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 14, marginTop: 4 }}>
            <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 8 }}>Logo</label>
            {hasLogo && <div style={{ marginBottom: 8 }}><ProfileLogoImage refreshKey={logoRefreshKey} /></div>}
            <label style={{ ...btnSecondary, display: "inline-block", opacity: uploadingLogo ? 0.6 : 1 }}>
              {uploadingLogo ? "Uploading…" : hasLogo ? "Replace Logo" : "Upload Logo"}
              <input type="file" accept="image/*" style={{ display: "none" }} disabled={uploadingLogo}
                onChange={e => { const f = e.target.files?.[0]; if (f) handleLogoUpload(f) }} />
            </label>
          </div>
          {error && <ErrorBox text={error} />}
          <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Save Profile" />
        </>
      )}
    </Modal>
  )
}

export function PayrollBureauPage() {
  const [clients, setClients] = useState<PayClient[]>([])
  const [clientsLoading, setClientsLoading] = useState(true)
  const [selected, setSelected] = useState<PayClient | null>(null)
  const [showNewClient, setShowNewClient] = useState(false)
  const [showEditClient, setShowEditClient] = useState(false)
  const [showBureauProfile, setShowBureauProfile] = useState(false)
  const [archiving, setArchiving] = useState(false)
  const [tab, setTab] = useState<Tab>("employees")
  const [search, setSearch] = useState("")

  const refetchClients = () => {
    setClientsLoading(true)
    payrollBureauApi.getClients().then(res => setClients(res.content)).finally(() => setClientsLoading(false))
  }

  useEffect(() => { refetchClients() }, [])

  const visibleClients = clients.filter(c =>
    !search || c.tradingName.toLowerCase().includes(search.toLowerCase()))

  const toggleArchive = async () => {
    if (!selected) return
    setArchiving(true)
    try {
      if (selected.status === "OFFBOARDED") await payrollBureauApi.reactivateClient(selected.id)
      else await payrollBureauApi.offboardClient(selected.id)
      const res = await payrollBureauApi.getClients()
      setClients(res.content)
      setSelected(res.content.find(c => c.id === selected.id) ?? null)
    } finally { setArchiving(false) }
  }

  return (
    <div style={{ display: "flex", height: "calc(100vh - 60px)", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ width: 300, borderRight: `1px solid ${BORDER}`, background: "#fff", display: "flex", flexDirection: "column" }}>
        <div style={{ padding: 16, borderBottom: `1px solid ${BORDER}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2 style={{ fontSize: 15, fontWeight: 800, color: INK, margin: 0 }}>Payroll Clients</h2>
          <div style={{ display: "flex", gap: 6 }}>
            <button onClick={() => setShowBureauProfile(true)} style={btnSecondary}>Bureau Profile</button>
            <button onClick={() => setShowNewClient(true)}
              style={{ padding: "5px 10px", background: NAVY, color: "#fff", border: "none", borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
              + New
            </button>
          </div>
        </div>
        <div style={{ padding: "10px 16px", borderBottom: `1px solid ${BORDER}` }}>
          <input placeholder="Search clients…" value={search} onChange={e => setSearch(e.target.value)}
            style={{ ...inputStyle, fontSize: 12.5 }} />
        </div>
        <div style={{ flex: 1, overflowY: "auto" as const }}>
          {clientsLoading ? (
            <div style={{ padding: 20, color: FAINT, fontSize: 13 }}>Loading…</div>
          ) : visibleClients.length === 0 ? (
            <div style={{ padding: 20, color: FAINT, fontSize: 13 }}>
              {clients.length === 0 ? "No clients yet — add your first one." : `No clients match "${search}"`}
            </div>
          ) : (
            visibleClients.map(c => (
              <button key={c.id} onClick={() => { setSelected(c); setTab("employees") }}
                style={{ display: "block", width: "100%", textAlign: "left" as const, padding: "12px 16px",
                  background: selected?.id === c.id ? "#EFF6FF" : "none", border: "none",
                  borderBottom: `1px solid ${BORDER}`, cursor: "pointer",
                  opacity: c.status === "OFFBOARDED" ? 0.55 : 1 }}>
                <div style={{ fontSize: 13.5, fontWeight: 700, color: INK }}>{c.tradingName}</div>
                <div style={{ fontSize: 11.5, color: FAINT, marginTop: 2 }}>
                  {c.status === "OFFBOARDED" ? "Offboarded" : `R${c.perEmployeeFee}/employee · ${c.payFrequency}`}
                </div>
              </button>
            ))
          )}
        </div>
      </div>

      <div style={{ flex: 1, overflowY: "auto" as const, background: CANVAS }}>
        {!selected ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%", color: FAINT, fontSize: 14 }}>
            Select a client, or add a new one
          </div>
        ) : (
          <div style={{ padding: "24px 32px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 4 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                {(selected as any).hasLogo && <LogoImage clientId={selected.id} maxHeight={36} />}
                <h1 style={{ fontSize: 20, fontWeight: 800, color: INK, margin: 0 }}>{selected.tradingName}</h1>
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <button onClick={() => setShowEditClient(true)} style={btnSecondary}>Edit</button>
                <button onClick={toggleArchive} disabled={archiving}
                  style={{ ...btnSecondary, color: selected.status === "OFFBOARDED" ? "#166534" : "#DC2626",
                    borderColor: selected.status === "OFFBOARDED" ? "#166534" : "#DC2626" }}>
                  {archiving ? "…" : selected.status === "OFFBOARDED" ? "Reactivate" : "Offboard"}
                </button>
              </div>
            </div>
            <p style={{ fontSize: 12.5, color: MUTED, marginBottom: 4 }}>
              PAYE {selected.payeReference ?? "—"} · UIF {selected.uifReference ?? "—"} · SDL {selected.sdlReference ?? "exempt"}
            </p>
            <p style={{ fontSize: 12.5, color: MUTED, marginBottom: 4 }}>
              {(selected as any).contactName && `${(selected as any).contactName} · `}
              {selected.contactEmail}
              {(selected as any).contactPhone && ` · ${(selected as any).contactPhone}`}
              {(selected as any).payDay && ` · Pays on day ${(selected as any).payDay} of the month`}
            </p>
            {(selected as any).address && (
              <p style={{ fontSize: 12.5, color: MUTED, marginBottom: 20, whiteSpace: "pre-wrap" as const }}>
                {(selected as any).address}
              </p>
            )}
            {!(selected as any).address && <div style={{ marginBottom: 20 }} />}

            <div style={{ display: "flex", gap: 4, borderBottom: `1px solid ${BORDER}`, marginBottom: 20 }}>
              {([
                ["employees", "Employees"], ["payruns", "Pay Runs"], ["deadlines", "Deadlines"],
                ["feenotes", "Invoices"], ["portal", "Portal Access"],
              ] as [Tab, string][]).map(([id, label]) => (
                <button key={id} onClick={() => setTab(id)} style={{
                  padding: "8px 14px", background: "none", border: "none",
                  borderBottom: tab === id ? `2px solid ${NAVY}` : "2px solid transparent",
                  color: tab === id ? NAVY : MUTED, fontWeight: tab === id ? 700 : 500, fontSize: 13, cursor: "pointer", marginBottom: -1,
                }}>{label}</button>
              ))}
            </div>

            {tab === "employees" && <EmployeesTab client={selected} />}
            {tab === "payruns" && <PayRunsTab client={selected} />}
            {tab === "deadlines" && <DeadlinesTab client={selected} />}
            {tab === "feenotes" && <FeeNotesTab client={selected} />}
            {tab === "portal" && <PortalAccessTab client={selected} />}
          </div>
        )}
      </div>

      {showNewClient && (
        <NewClientModal onClose={() => setShowNewClient(false)}
          onCreated={() => { setShowNewClient(false); refetchClients() }} />
      )}
      {showEditClient && selected && (
        <EditClientModal client={selected} onClose={() => setShowEditClient(false)}
          onSaved={updated => { setShowEditClient(false); setSelected(updated); refetchClients() }} />
      )}
      {showBureauProfile && (
        <BureauProfileModal onClose={() => setShowBureauProfile(false)} />
      )}
    </div>
  )
}

// ── Client form (shared state + fields between New/Edit) ─────────────────────

function useClientFormState(initial?: Partial<PayClient> & {
  registrationNumber?: string; sdlReference?: string; payFrequency?: string;
  payDay?: number; contactName?: string; contactPhone?: string; notes?: string; address?: string
}) {
  const [tradingName, setTradingName] = useState(initial?.tradingName ?? "")
  const [registrationNumber, setRegistrationNumber] = useState((initial as any)?.registrationNumber ?? "")
  const [payeReference, setPayeReference] = useState((initial as any)?.payeReference ?? "")
  const [uifReference, setUifReference] = useState((initial as any)?.uifReference ?? "")
  const [sdlReference, setSdlReference] = useState((initial as any)?.sdlReference ?? "")
  const [payFrequency, setPayFrequency] = useState((initial as any)?.payFrequency ?? "MONTHLY")
  const [payDay, setPayDay] = useState((initial as any)?.payDay ? String((initial as any).payDay) : "")
  const [contactName, setContactName] = useState((initial as any)?.contactName ?? "")
  const [contactEmail, setContactEmail] = useState((initial as any)?.contactEmail ?? "")
  const [contactPhone, setContactPhone] = useState((initial as any)?.contactPhone ?? "")
  const [notes, setNotes] = useState((initial as any)?.notes ?? "")
  const [address, setAddress] = useState((initial as any)?.address ?? "")
  const [errors, setErrors] = useState<FieldErrors>({})

  const validate = (): boolean => {
    const e: FieldErrors = {}
    const nameErr = required(tradingName, "Trading name"); if (nameErr) e.tradingName = nameErr
    const emailErr = required(contactEmail, "Contact email") || validateEmail(contactEmail)
    if (emailErr) e.contactEmail = emailErr
    const phoneErr = validatePhone(contactPhone); if (phoneErr) e.contactPhone = phoneErr
    const dayErr = validateDayOfMonth(payDay); if (dayErr) e.payDay = dayErr
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const toPayload = () => ({
    tradingName, registrationNumber: registrationNumber || undefined,
    payeReference: payeReference || undefined, uifReference: uifReference || undefined,
    sdlReference: sdlReference || undefined, payFrequency, payDay: payDay ? Number(payDay) : undefined,
    contactName: contactName || undefined, contactEmail, contactPhone: contactPhone || undefined,
    notes: notes || undefined, address: address || undefined,
  })

  return { tradingName, setTradingName, registrationNumber, setRegistrationNumber,
    payeReference, setPayeReference, uifReference, setUifReference, sdlReference, setSdlReference,
    payFrequency, setPayFrequency, payDay, setPayDay, contactName, setContactName,
    contactEmail, setContactEmail, contactPhone, setContactPhone, notes, setNotes,
    address, setAddress,
    errors, validate, toPayload }
}

function ClientFormFields({ state }: { state: ReturnType<typeof useClientFormState> }) {
  return (
    <>
      <Field label="Trading name *">
        <input value={state.tradingName} onChange={e => state.setTradingName(e.target.value)}
          style={{ ...inputStyle, ...(state.errors.tradingName ? errorInputStyle : {}) }} />
        <FieldError text={state.errors.tradingName} />
      </Field>
      <Field label="Registration number (optional)">
        <input value={state.registrationNumber} onChange={e => state.setRegistrationNumber(e.target.value)} style={inputStyle} />
      </Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="PAYE reference (optional)"><input value={state.payeReference} onChange={e => state.setPayeReference(e.target.value)} style={inputStyle} /></Field>
        <Field label="UIF reference (optional)"><input value={state.uifReference} onChange={e => state.setUifReference(e.target.value)} style={inputStyle} /></Field>
      </div>
      <Field label="SDL reference (optional — leave blank if exempt)">
        <input value={state.sdlReference} onChange={e => state.setSdlReference(e.target.value)} style={inputStyle} />
      </Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Pay frequency">
          <select value={state.payFrequency} onChange={e => state.setPayFrequency(e.target.value)} style={inputStyle}>
            <option value="MONTHLY">Monthly</option>
            <option value="FORTNIGHTLY">Fortnightly</option>
            <option value="WEEKLY">Weekly</option>
          </select>
        </Field>
        <Field label="Pay day (day of month)">
          <input type="number" min={1} max={31} value={state.payDay} onChange={e => state.setPayDay(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.payDay ? errorInputStyle : {}) }} placeholder="e.g. 25" />
          <FieldError text={state.errors.payDay} />
        </Field>
      </div>
      <p style={{ fontSize: 11.5, fontWeight: 700, color: MUTED, marginTop: 4, marginBottom: 6, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>Contact Person</p>
      <Field label="Contact name (optional)"><input value={state.contactName} onChange={e => state.setContactName(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Contact email *">
          <input value={state.contactEmail} onChange={e => state.setContactEmail(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.contactEmail ? errorInputStyle : {}) }} />
          <FieldError text={state.errors.contactEmail} />
        </Field>
        <Field label="Contact phone (optional)">
          <input value={state.contactPhone} onChange={e => state.setContactPhone(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.contactPhone ? errorInputStyle : {}) }} />
          <FieldError text={state.errors.contactPhone} />
        </Field>
      </div>
      <Field label="Notes (optional)">
        <textarea value={state.notes} onChange={e => state.setNotes(e.target.value)}
          style={{ ...inputStyle, minHeight: 60, resize: "vertical" as const, fontFamily: "inherit" }} />
      </Field>
      <Field label="Address (optional — appears on payslips)">
        <textarea value={state.address} onChange={e => state.setAddress(e.target.value)}
          style={{ ...inputStyle, minHeight: 50, resize: "vertical" as const, fontFamily: "inherit" }} />
      </Field>
    </>
  )
}

function NewClientModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const state = useClientFormState()
  const [saving, setSaving] = useState(false)
  const [submitError, setSubmitError] = useState("")

  const submit = async () => {
    if (!state.validate()) return
    setSaving(true); setSubmitError("")
    try {
      await payrollBureauApi.createClient(state.toPayload() as any)
      onCreated()
    } catch (e: any) {
      setSubmitError(e.response?.data?.message ?? "Failed to create client")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="New Payroll Client" onClose={onClose}>
      <ClientFormFields state={state} />
      <p style={{ fontSize: 11.5, color: FAINT, marginTop: -4, marginBottom: 12 }}>
        Logo can be added once the client is created — see Edit after saving.
      </p>
      {submitError && <ErrorBox text={submitError} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Client" />
    </Modal>
  )
}

function EditClientModal({ client, onClose, onSaved }: { client: PayClient; onClose: () => void; onSaved: (c: PayClient) => void }) {
  const state = useClientFormState(client as any)
  const [saving, setSaving] = useState(false)
  const [submitError, setSubmitError] = useState("")
  const [uploadingLogo, setUploadingLogo] = useState(false)
  const [logoRefreshKey, setLogoRefreshKey] = useState(0)
  const [hasLogo, setHasLogo] = useState((client as any).hasLogo)

  const submit = async () => {
    if (!state.validate()) return
    setSaving(true); setSubmitError("")
    try {
      const updated = await payrollBureauApi.updateClient(client.id, state.toPayload() as any)
      onSaved(updated)
    } catch (e: any) {
      setSubmitError(e.response?.data?.message ?? "Failed to save client")
    } finally { setSaving(false) }
  }

  const handleLogoUpload = async (file: File) => {
    setUploadingLogo(true)
    try {
      await payrollBureauApi.attachLogo(client.id, file)
      setHasLogo(true)
      setLogoRefreshKey(k => k + 1) // forces LogoImage to refetch rather than show a stale cached blob
    } catch (e: any) {
      alert(e.response?.data?.message ?? "Failed to upload logo")
    } finally { setUploadingLogo(false) }
  }

  return (
    <Modal title="Edit Payroll Client" onClose={onClose}>
      <ClientFormFields state={state} />

      <div style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 14, marginTop: 4, marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 8 }}>
          Logo (appears on payslips)
        </label>
        {hasLogo && (
          <div style={{ marginBottom: 8 }}>
            <LogoImage clientId={client.id} refreshKey={logoRefreshKey} />
          </div>
        )}
        <label style={{ ...btnSecondary, display: "inline-block", opacity: uploadingLogo ? 0.6 : 1 }}>
          {uploadingLogo ? "Uploading…" : hasLogo ? "Replace Logo" : "Upload Logo"}
          <input type="file" accept="image/*" style={{ display: "none" }} disabled={uploadingLogo}
            onChange={e => { const f = e.target.files?.[0]; if (f) handleLogoUpload(f) }} />
        </label>
      </div>

      {submitError && <ErrorBox text={submitError} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Save Changes" />
    </Modal>
  )
}

// ── Employees ────────────────────────────────────────────────────────────────

function EmployeesTab({ client }: { client: PayClient }) {
  const [employees, setEmployees] = useState<PayEmployee[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)
  const [editing, setEditing] = useState<PayEmployee | null>(null)
  const [docsFor, setDocsFor] = useState<PayEmployee | null>(null)

  const refetch = () => {
    setLoading(true)
    payrollBureauApi.getEmployees(client.id).then(setEmployees).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ Add Employee</button>
      </div>
      {loading ? <Empty text="Loading…" /> : employees.length === 0 ? <Empty text="No employees added yet." /> : (
        <Table headers={["Employee #", "Name", "Email", "Gross Salary", "Status", ""]}>
          {employees.map(e => (
            <tr key={e.id} style={rowStyle}>
              <td style={cellStyle}>{e.employeeNumber}</td>
              <td style={cellStyle}>{e.fullName}</td>
              <td style={cellStyle}>{(e as any).email || <span style={{ color: FAINT }}>—</span>}</td>
              <td style={cellStyle}>{fmtR(e.grossSalary)}</td>
              <td style={cellStyle}><StatusBadge status={e.status} /></td>
              <td style={cellStyle}>
                <div style={{ display: "flex", gap: 6 }}>
                  <button onClick={() => setEditing(e)} style={btnSecondary}>Edit</button>
                  <button onClick={() => setDocsFor(e)} style={btnSecondary}>Documents</button>
                </div>
              </td>
            </tr>
          ))}
        </Table>
      )}
      {showNew && <NewEmployeeModal clientId={client.id} onClose={() => setShowNew(false)}
        onCreated={() => { setShowNew(false); refetch() }} />}
      {editing && <EditEmployeeModal clientId={client.id} employee={editing} onClose={() => setEditing(null)}
        onSaved={() => { setEditing(null); refetch() }} />}
      {docsFor && <EmployeeDocumentsModal employee={docsFor} onClose={() => setDocsFor(null)} />}
    </div>
  )
}

function useEmployeeFormState(initial?: Partial<PayEmployee> & { email?: string; taxNumber?: string; phone?: string }) {
  const [firstName, setFirstName] = useState(initial?.firstName ?? "")
  const [lastName, setLastName] = useState(initial?.lastName ?? "")
  const [email, setEmail] = useState(initial?.email ?? "")
  const [idNumber, setIdNumber] = useState((initial as any)?.idNumber ?? "")
  const [taxNumber, setTaxNumber] = useState(initial?.taxNumber ?? "")
  const [phone, setPhone] = useState(initial?.phone ?? "")
  const [dateOfBirth, setDateOfBirth] = useState((initial as any)?.dateOfBirth ?? "")
  const [grossSalary, setGrossSalary] = useState(initial?.grossSalary ? String(initial.grossSalary) : "")
  const [travelAllowance, setTravelAllowance] = useState((initial as any)?.travelAllowance ? String((initial as any).travelAllowance) : "0")
  const [pensionContribution, setPensionContribution] = useState((initial as any)?.pensionContribution ? String((initial as any).pensionContribution) : "0")
  const [medicalAidContribution, setMedicalAidContribution] = useState((initial as any)?.medicalAidContribution ? String((initial as any).medicalAidContribution) : "0")
  const [bankName, setBankName] = useState((initial as any)?.bankName ?? "")
  const [bankAccountNumber, setBankAccountNumber] = useState((initial as any)?.bankAccountNumber ?? "")
  const [bankBranchCode, setBankBranchCode] = useState((initial as any)?.bankBranchCode ?? "")
  const [errors, setErrors] = useState<FieldErrors>({})

  const validate = (): boolean => {
    const e: FieldErrors = {}
    const firstErr = required(firstName, "First name"); if (firstErr) e.firstName = firstErr
    const lastErr = required(lastName, "Last name"); if (lastErr) e.lastName = lastErr
    const salaryErr = validatePositiveNumber(grossSalary, "Gross salary"); if (salaryErr) e.grossSalary = salaryErr
    const emailErr = validateEmail(email); if (emailErr) e.email = emailErr
    const phoneErr = validatePhone(phone); if (phoneErr) e.phone = phoneErr
    const idErr = validateSaId(idNumber); if (idErr) e.idNumber = idErr
    const taxErr = validateTaxNumber(taxNumber); if (taxErr) e.taxNumber = taxErr
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const toPayload = () => ({
    firstName, lastName, email: email || undefined, idNumber: idNumber || undefined,
    taxNumber: taxNumber || undefined, phone: phone || undefined,
    dateOfBirth: dateOfBirth || undefined,
    grossSalary: Number(grossSalary), travelAllowance: Number(travelAllowance || 0),
    pensionContribution: Number(pensionContribution || 0), medicalAidContribution: Number(medicalAidContribution || 0),
    bankName: bankName || undefined, bankAccountNumber: bankAccountNumber || undefined, bankBranchCode: bankBranchCode || undefined,
  })

  return { firstName, setFirstName, lastName, setLastName, email, setEmail, idNumber, setIdNumber,
    taxNumber, setTaxNumber, phone, setPhone, dateOfBirth, setDateOfBirth,
    grossSalary, setGrossSalary, travelAllowance, setTravelAllowance,
    pensionContribution, setPensionContribution, medicalAidContribution, setMedicalAidContribution,
    bankName, setBankName, bankAccountNumber, setBankAccountNumber, bankBranchCode, setBankBranchCode,
    errors, validate, toPayload }
}

function EmployeeFormFields({ state }: { state: ReturnType<typeof useEmployeeFormState> }) {
  return (
    <>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="First name *">
          <input value={state.firstName} onChange={e => state.setFirstName(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.firstName ? errorInputStyle : {}) }} />
          <FieldError text={state.errors.firstName} />
        </Field>
        <Field label="Last name *">
          <input value={state.lastName} onChange={e => state.setLastName(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.lastName ? errorInputStyle : {}) }} />
          <FieldError text={state.errors.lastName} />
        </Field>
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Email (optional — for digital payslips)">
          <input type="email" value={state.email} onChange={e => state.setEmail(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.email ? errorInputStyle : {}) }} />
          <FieldError text={state.errors.email} />
        </Field>
        <Field label="Phone (optional)">
          <input value={state.phone} onChange={e => state.setPhone(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.phone ? errorInputStyle : {}) }} />
          <FieldError text={state.errors.phone} />
        </Field>
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="ID number">
          <input value={state.idNumber} onChange={e => state.setIdNumber(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.idNumber ? errorInputStyle : {}) }} placeholder="13 digits" />
          <FieldError text={state.errors.idNumber} />
        </Field>
        <Field label="Tax number">
          <input value={state.taxNumber} onChange={e => state.setTaxNumber(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.taxNumber ? errorInputStyle : {}) }} placeholder="10 digits" />
          <FieldError text={state.errors.taxNumber} />
        </Field>
      </div>
      <Field label="Date of birth"><input type="date" value={state.dateOfBirth} onChange={e => state.setDateOfBirth(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Gross salary (monthly) *">
          <input type="number" value={state.grossSalary} onChange={e => state.setGrossSalary(e.target.value)}
            style={{ ...inputStyle, ...(state.errors.grossSalary ? errorInputStyle : {}) }} />
          <FieldError text={state.errors.grossSalary} />
        </Field>
        <Field label="Travel allowance"><input type="number" value={state.travelAllowance} onChange={e => state.setTravelAllowance(e.target.value)} style={inputStyle} /></Field>
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Pension contribution"><input type="number" value={state.pensionContribution} onChange={e => state.setPensionContribution(e.target.value)} style={inputStyle} /></Field>
        <Field label="Medical aid contribution"><input type="number" value={state.medicalAidContribution} onChange={e => state.setMedicalAidContribution(e.target.value)} style={inputStyle} /></Field>
      </div>
      <p style={{ fontSize: 11.5, fontWeight: 700, color: MUTED, marginTop: 4, marginBottom: 6, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>Banking Details</p>
      <Field label="Bank name"><input value={state.bankName} onChange={e => state.setBankName(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Account number"><input value={state.bankAccountNumber} onChange={e => state.setBankAccountNumber(e.target.value)} style={inputStyle} /></Field>
        <Field label="Branch code"><input value={state.bankBranchCode} onChange={e => state.setBankBranchCode(e.target.value)} style={inputStyle} /></Field>
      </div>
    </>
  )
}

function NewEmployeeModal({ clientId, onClose, onCreated }: { clientId: string; onClose: () => void; onCreated: () => void }) {
  const state = useEmployeeFormState()
  const [startDate, setStartDate] = useState(new Date().toISOString().slice(0, 10))
  const [saving, setSaving] = useState(false)
  const [submitError, setSubmitError] = useState("")

  const submit = async () => {
    if (!state.validate()) return
    setSaving(true); setSubmitError("")
    try {
      await payrollBureauApi.createEmployee(clientId, { ...state.toPayload(), startDate } as any)
      onCreated()
    } catch (e: any) {
      setSubmitError(e.response?.data?.message ?? "Failed to add employee")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="Add Employee" onClose={onClose}>
      <EmployeeFormFields state={state} />
      <Field label="Start date"><input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} style={inputStyle} /></Field>
      {submitError && <ErrorBox text={submitError} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Add Employee" />
    </Modal>
  )
}

function EditEmployeeModal({ clientId, employee, onClose, onSaved }: { clientId: string; employee: PayEmployee; onClose: () => void; onSaved: () => void }) {
  const state = useEmployeeFormState(employee as any)
  const [saving, setSaving] = useState(false)
  const [submitError, setSubmitError] = useState("")

  const submit = async () => {
    if (!state.validate()) return
    setSaving(true); setSubmitError("")
    try {
      await payrollBureauApi.updateEmployee(clientId, employee.id, state.toPayload() as any)
      onSaved()
    } catch (e: any) {
      setSubmitError(e.response?.data?.message ?? "Failed to save employee")
    } finally { setSaving(false) }
  }

  return (
    <Modal title={`Edit Employee — ${employee.fullName}`} onClose={onClose}>
      <EmployeeFormFields state={state} />
      {submitError && <ErrorBox text={submitError} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Save Changes" />
    </Modal>
  )
}

function EmployeeDocumentsModal({ employee, onClose }: { employee: PayEmployee; onClose: () => void }) {
  const [docs, setDocs] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [docType, setDocType] = useState("ID_DOCUMENT")
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState("")
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const refetch = () => {
    setLoading(true)
    payrollBureauApi.getEmployeeDocuments(employee.id).then(setDocs).finally(() => setLoading(false))
  }
  useEffect(refetch, [employee.id])

  const handleUpload = async (file: File) => {
    setUploading(true); setError("")
    try {
      await payrollBureauApi.uploadDocument(employee.id, file, docType)
      refetch()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to upload document")
    } finally { setUploading(false) }
  }

  const handleDownload = async (documentId: string, fileName: string) => {
    setDownloadingId(documentId)
    try {
      const blob = await payrollBureauApi.downloadDocument(documentId)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url; a.download = fileName
      document.body.appendChild(a); a.click(); a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      alert("Failed to download document")
    } finally { setDownloadingId(null) }
  }

  return (
    <Modal title={`Documents — ${employee.fullName}`} onClose={onClose}>
      {loading ? (
        <div style={{ color: FAINT, fontSize: 13, padding: "12px 0" }}>Loading…</div>
      ) : docs.length === 0 ? (
        <div style={{ color: FAINT, fontSize: 13, padding: "12px 0" }}>No documents on file yet.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 16 }}>
          {docs.map((d: any) => (
            <div key={d.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center",
              padding: "8px 10px", background: CANVAS, borderRadius: 6 }}>
              <div>
                <div style={{ fontSize: 12.5, fontWeight: 600, color: INK }}>{d.fileName}</div>
                <div style={{ fontSize: 11, color: FAINT }}>{d.docType?.replace(/_/g, " ")} · {fmtD(d.uploadedAt)}</div>
              </div>
              <button onClick={() => handleDownload(d.id, d.fileName)} disabled={downloadingId === d.id}
                style={{ ...btnSecondary, padding: "4px 10px", fontSize: 11 }}>
                {downloadingId === d.id ? "…" : "Download"}
              </button>
            </div>
          ))}
        </div>
      )}
      <div style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 14 }}>
        <Field label="Document type">
          <select value={docType} onChange={e => setDocType(e.target.value)} style={inputStyle}>
            <option value="ID_DOCUMENT">ID document</option>
            <option value="TAX_CERTIFICATE">Tax certificate</option>
            <option value="EMPLOYMENT_CONTRACT">Employment contract</option>
            <option value="BANKING_DETAILS">Banking details</option>
            <option value="OTHER">Other</option>
          </select>
        </Field>
        {error && <ErrorBox text={error} />}
        <label style={{ ...btnPrimary, display: "inline-block", opacity: uploading ? 0.6 : 1 }}>
          {uploading ? "Uploading…" : "Upload File"}
          <input type="file" style={{ display: "none" }} disabled={uploading}
            onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f) }} />
        </label>
      </div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 14 }}>
        <button onClick={onClose} style={btnSecondary}>Close</button>
      </div>
    </Modal>
  )
}

// ── Pay Runs ─────────────────────────────────────────────────────────────────

function PayRunsTab({ client }: { client: PayClient }) {
  const [payRuns, setPayRuns] = useState<PayRun[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)
  const [expandedRun, setExpandedRun] = useState<string | null>(null)
  const [payslips, setPayslips] = useState<Payslip[]>([])
  const [processingId, setProcessingId] = useState<string | null>(null)
  const [generatingFeeNoteFor, setGeneratingFeeNoteFor] = useState<PayRun | null>(null)
  const [emailingRunId, setEmailingRunId] = useState<string | null>(null)
  const [emailResult, setEmailResult] = useState<{ sent: number; skippedNoEmail: number; skippedEmployeeNames: string[] } | null>(null)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const refetch = () => {
    setLoading(true)
    payrollBureauApi.getPayRuns(client.id).then(res => setPayRuns(res.content)).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const handleProcess = async (id: string) => {
    setProcessingId(id)
    try {
      await payrollBureauApi.processPayRun(id)
      refetch()
    } catch (e: any) {
      alert(e.response?.data?.message ?? "Failed to process pay run")
    } finally { setProcessingId(null) }
  }

  const handleExpand = async (run: PayRun) => {
    if (expandedRun === run.id) { setExpandedRun(null); return }
    setExpandedRun(run.id)
    if (run.status === "PROCESSED") {
      setPayslips(await payrollBureauApi.getPayslips(run.id))
    }
  }

  const handleEmailAll = async (runId: string) => {
    setEmailingRunId(runId)
    try {
      const result = await payrollBureauApi.emailPayslips(runId)
      setEmailResult(result)
    } catch (e: any) {
      alert(e.response?.data?.message ?? "Failed to send payslips")
    } finally { setEmailingRunId(null) }
  }

  const handleDownloadPdf = async (runId: string, payslipId: string, employeeName: string) => {
    setDownloadingId(payslipId)
    try {
      const blob = await payrollBureauApi.downloadPayslipPdf(runId, payslipId)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url; a.download = `Payslip - ${employeeName}.pdf`
      document.body.appendChild(a); a.click(); a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      alert("Failed to download payslip")
    } finally { setDownloadingId(null) }
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ New Pay Run</button>
      </div>
      {loading ? <Empty text="Loading…" /> : payRuns.length === 0 ? <Empty text="No pay runs yet." /> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {payRuns.map(r => (
            <div key={r.id} style={{ background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", cursor: "pointer" }}
                onClick={() => handleExpand(r)}>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 13.5, color: INK }}>
                    {r.payRunNumber} — {fmtD(r.periodStart)} to {fmtD(r.periodEnd)}
                  </div>
                  <div style={{ fontSize: 11.5, color: FAINT, marginTop: 2 }}>
                    Pay date {fmtD(r.payDate)}{r.status === "PROCESSED" && ` · ${r.employeeCount} employees · Net ${fmtR(r.totalNet)}`}
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <StatusBadge status={r.status} />
                  {r.status === "DRAFT" && (
                    <button onClick={e => { e.stopPropagation(); handleProcess(r.id) }} disabled={processingId === r.id}
                      style={btnPrimarySmall}>{processingId === r.id ? "Processing…" : "Process"}</button>
                  )}
                  {r.status === "PROCESSED" && (
                    <>
                      <button onClick={e => { e.stopPropagation(); handleEmailAll(r.id) }} disabled={emailingRunId === r.id}
                        style={btnPrimarySmall}>{emailingRunId === r.id ? "Sending…" : "Email All Payslips"}</button>
                      <button onClick={e => { e.stopPropagation(); setGeneratingFeeNoteFor(r) }} style={btnPrimarySmall}>
                        Generate Invoice
                      </button>
                    </>
                  )}
                </div>
              </div>
              {expandedRun === r.id && r.status === "PROCESSED" && (
                <div style={{ borderTop: `1px solid ${BORDER}`, padding: 12 }}>
                  <Table headers={["Employee", "Gross", "PAYE", "UIF", "Net Pay", ""]}>
                    {payslips.map(p => (
                      <tr key={p.id} style={rowStyle}>
                        <td style={cellStyle}>{p.employeeName}</td>
                        <td style={cellStyle}>{fmtR(p.grossSalary)}</td>
                        <td style={cellStyle}>{fmtR(p.payeAmount)}</td>
                        <td style={cellStyle}>{fmtR(p.uifEmployee)}</td>
                        <td style={cellStyle}><strong>{fmtR(p.netPay)}</strong></td>
                        <td style={cellStyle}>
                          <button onClick={() => handleDownloadPdf(r.id, p.id, p.employeeName)} disabled={downloadingId === p.id}
                            style={{ ...btnSecondary, padding: "4px 10px", fontSize: 11 }}>
                            {downloadingId === p.id ? "…" : "Download / Print"}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </Table>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {showNew && <NewPayRunModal clientId={client.id} onClose={() => setShowNew(false)}
        onCreated={() => { setShowNew(false); refetch() }} />}
      {generatingFeeNoteFor && (
        <GenerateFeeNoteModal client={client} payRun={generatingFeeNoteFor}
          onClose={() => setGeneratingFeeNoteFor(null)}
          onGenerated={() => setGeneratingFeeNoteFor(null)} />
      )}
      {emailResult && (
        <Modal title="Payslips Sent" onClose={() => setEmailResult(null)}>
          <p style={{ fontSize: 13.5, color: INK, marginBottom: 10 }}>
            <strong>{emailResult.sent}</strong> payslip{emailResult.sent !== 1 ? "s" : ""} emailed successfully.
          </p>
          {emailResult.skippedNoEmail > 0 && (
            <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 6, padding: 12, fontSize: 12.5, color: "#92400E" }}>
              <strong>{emailResult.skippedNoEmail}</strong> employee{emailResult.skippedNoEmail !== 1 ? "s have" : " has"} no email on file
              and {emailResult.skippedNoEmail !== 1 ? "were" : "was"} skipped — use "Download / Print" for{" "}
              {emailResult.skippedEmployeeNames.join(", ")}.
            </div>
          )}
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 14 }}>
            <button onClick={() => setEmailResult(null)} style={btnPrimary}>Close</button>
          </div>
        </Modal>
      )}
    </div>
  )
}

function GenerateFeeNoteModal({ client, payRun, onClose, onGenerated }: { client: PayClient; payRun: PayRun; onClose: () => void; onGenerated: () => void }) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    setSaving(true); setError("")
    try {
      const today = new Date().toISOString().slice(0, 10)
      const due = new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10)
      await payrollBureauApi.generateFeeNote(client.id, {
        payRunId: payRun.id, invoiceDate: today, dueDate: due, includeVat: true,
      })
      onGenerated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to generate invoice")
    } finally { setSaving(false) }
  }

  return (
    <Modal title={`Generate Invoice — ${payRun.payRunNumber}`} onClose={onClose}>
      <p style={{ fontSize: 13, color: MUTED, marginBottom: 14 }}>
        Generates your bureau fee invoice for this processed pay run ({payRun.employeeCount} employees). See it in the Invoices tab once created.
      </p>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Generate" />
    </Modal>
  )
}

function NewPayRunModal({ clientId, onClose, onCreated }: { clientId: string; onClose: () => void; onCreated: () => void }) {
  const today = new Date()
  const firstOfMonth = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().slice(0, 10)
  const lastOfMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0).toISOString().slice(0, 10)
  const [periodStart, setPeriodStart] = useState(firstOfMonth)
  const [periodEnd, setPeriodEnd] = useState(lastOfMonth)
  const [payDate, setPayDate] = useState(lastOfMonth)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    setSaving(true); setError("")
    try {
      await payrollBureauApi.createPayRun(clientId, { periodStart, periodEnd, payDate })
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to create pay run")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="New Pay Run" onClose={onClose}>
      <Field label="Period start"><input type="date" value={periodStart} onChange={e => setPeriodStart(e.target.value)} style={inputStyle} /></Field>
      <Field label="Period end"><input type="date" value={periodEnd} min={periodStart} onChange={e => setPeriodEnd(e.target.value)} style={inputStyle} /></Field>
      <Field label="Pay date"><input type="date" value={payDate} onChange={e => setPayDate(e.target.value)} style={inputStyle} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Pay Run" />
    </Modal>
  )
}

// ── Deadlines ────────────────────────────────────────────────────────────────

function DeadlinesTab({ client }: { client: PayClient }) {
  const [deadlines, setDeadlines] = useState<PayDeadline[]>([])
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [filingId, setFilingId] = useState<string | null>(null)

  const refetch = () => {
    setLoading(true)
    payrollBureauApi.getDeadlines(client.id).then(setDeadlines).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const handleGenerate = async () => {
    setGenerating(true)
    try {
      await payrollBureauApi.generateDeadlines(client.id, new Date().getFullYear())
      refetch()
    } finally { setGenerating(false) }
  }

  const handleMarkFiled = async (id: string) => {
    setFilingId(id)
    try { await payrollBureauApi.markDeadlineFiled(id); refetch() }
    finally { setFilingId(null) }
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={handleGenerate} disabled={generating} style={btnPrimary}>
          {generating ? "Generating…" : `Generate ${new Date().getFullYear()} Deadlines`}
        </button>
      </div>
      {loading ? <Empty text="Loading…" /> : deadlines.length === 0 ? <Empty text="No deadlines generated yet." /> : (
        <Table headers={["Type", "Period", "Due Date", "Status", ""]}>
          {deadlines.map(d => (
            <tr key={d.id} style={rowStyle}>
              <td style={cellStyle}>{d.deadlineType}</td>
              <td style={cellStyle}>{d.periodMonth ? `${d.periodYear}/${String(d.periodMonth).padStart(2, "0")}` : d.periodYear}</td>
              <td style={cellStyle}>{fmtD(d.adjustedDueDate)}</td>
              <td style={cellStyle}><StatusBadge status={d.status} /></td>
              <td style={cellStyle}>
                {d.status === "PENDING" && (
                  <button onClick={() => handleMarkFiled(d.id)} disabled={filingId === d.id} style={btnSecondary}>
                    {filingId === d.id ? "…" : "Mark Filed"}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </Table>
      )}
    </div>
  )
}

// ── Fee Notes ────────────────────────────────────────────────────────────────

function FeeNotesTab({ client }: { client: PayClient }) {
  const [feeNotes, setFeeNotes] = useState<PayFeeNote[]>([])
  const [loading, setLoading] = useState(true)
  const [payingNote, setPayingNote] = useState<PayFeeNote | null>(null)

  const refetch = () => {
    setLoading(true)
    payrollBureauApi.getFeeNotes(client.id).then(res => setFeeNotes(res.content)).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const handleSend = async (id: string) => {
    await payrollBureauApi.sendFeeNote(id)
    refetch()
  }

  return (
    <div>
      <p style={{ fontSize: 12.5, color: MUTED, marginBottom: 12 }}>
        Generate invoices from the Pay Runs tab once a run is processed.
      </p>
      {loading ? <Empty text="Loading…" /> : feeNotes.length === 0 ? <Empty text="No invoices yet." /> : (
        <Table headers={["Invoice #", "Date", "Total", "Balance", "Status", ""]}>
          {feeNotes.map(f => (
            <tr key={f.id} style={rowStyle}>
              <td style={cellStyle}>{f.invoiceNumber}</td>
              <td style={cellStyle}>{fmtD(f.invoiceDate)}</td>
              <td style={cellStyle}>{fmtR(f.total)}</td>
              <td style={cellStyle}>{fmtR(f.balance)}</td>
              <td style={cellStyle}><StatusBadge status={f.status} /></td>
              <td style={cellStyle}>
                <div style={{ display: "flex", gap: 6 }}>
                  {f.status === "DRAFT" && <button onClick={() => handleSend(f.id)} style={btnSecondary}>Send</button>}
                  {f.status !== "DRAFT" && f.balance > 0 && (
                    <button onClick={() => setPayingNote(f)} style={btnSecondary}>Record Payment</button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </Table>
      )}
      {payingNote && (
        <RecordPaymentModal feeNote={payingNote} onClose={() => setPayingNote(null)}
          onRecorded={() => { setPayingNote(null); refetch() }} />
      )}
    </div>
  )
}

function RecordPaymentModal({ feeNote, onClose, onRecorded }: { feeNote: PayFeeNote; onClose: () => void; onRecorded: () => void }) {
  const [amount, setAmount] = useState(String(feeNote.balance))
  const [paidDate, setPaidDate] = useState(new Date().toISOString().slice(0, 10))
  const [method, setMethod] = useState("EFT")
  const [reference, setReference] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!amount || Number(amount) <= 0) { setError("Enter a payment amount"); return }
    setSaving(true); setError("")
    try {
      await payrollBureauApi.recordPayment(feeNote.id, {
        amount: Number(amount), paidDate, method: method || undefined, reference: reference || undefined,
      })
      onRecorded()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to record payment")
    } finally { setSaving(false) }
  }

  return (
    <Modal title={`Record Payment — ${feeNote.invoiceNumber}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: MUTED, marginBottom: 12 }}>Outstanding balance: {fmtR(feeNote.balance)}</p>
      <Field label="Amount"><input type="number" value={amount} onChange={e => setAmount(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Date paid"><input type="date" value={paidDate} onChange={e => setPaidDate(e.target.value)} style={inputStyle} /></Field>
        <Field label="Method">
          <select value={method} onChange={e => setMethod(e.target.value)} style={inputStyle}>
            <option value="EFT">EFT</option>
            <option value="CARD">Card</option>
            <option value="CASH">Cash</option>
            <option value="OTHER">Other</option>
          </select>
        </Field>
      </div>
      <Field label="Reference (optional)"><input value={reference} onChange={e => setReference(e.target.value)} style={inputStyle} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Record Payment" />
    </Modal>
  )
}

// ── Portal Access ────────────────────────────────────────────────────────────

function PortalAccessTab({ client }: { client: PayClient }) {
  const [grants, setGrants] = useState<PortalAccessGrant[]>([])
  const [loading, setLoading] = useState(true)
  const [email, setEmail] = useState("")
  const [inviting, setInviting] = useState(false)
  const [revokingId, setRevokingId] = useState<string | null>(null)

  const refetch = () => {
    setLoading(true)
    payrollBureauApi.getPortalAccessGrants(client.id).then(setGrants).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const handleInvite = async () => {
    if (!email) return
    setInviting(true)
    try {
      await payrollBureauApi.invitePortalUser(client.id, email)
      setEmail("")
      refetch()
    } catch (e: any) {
      alert(e.response?.data?.message ?? "Failed to send invite")
    } finally { setInviting(false) }
  }

  const handleRevoke = async (grantId: string) => {
    if (!confirm("Revoke this client's portal access?")) return
    setRevokingId(grantId)
    try { await payrollBureauApi.revokePortalAccess(client.id, grantId); refetch() }
    finally { setRevokingId(null) }
  }

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
        <input placeholder="client@example.com" value={email} onChange={e => setEmail(e.target.value)}
          style={{ ...inputStyle, flex: 1 }} />
        <button onClick={handleInvite} disabled={inviting} style={btnPrimary}>
          {inviting ? "Sending…" : "Invite to Portal"}
        </button>
      </div>
      {loading ? <Empty text="Loading…" /> : grants.length === 0 ? <Empty text="No portal invites sent yet." /> : (
        <Table headers={["Email", "Status", "Invited", ""]}>
          {grants.map(g => (
            <tr key={g.id} style={rowStyle}>
              <td style={cellStyle}>{g.inviteEmail}</td>
              <td style={cellStyle}><StatusBadge status={g.status} /></td>
              <td style={cellStyle}>{fmtD(g.invitedAt)}</td>
              <td style={cellStyle}>
                {g.status !== "REVOKED" && (
                  <button onClick={() => handleRevoke(g.id)} disabled={revokingId === g.id}
                    style={{ ...btnSecondary, color: "#DC2626", borderColor: "#DC2626" }}>
                    {revokingId === g.id ? "…" : "Revoke"}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </Table>
      )}
    </div>
  )
}

// ── Shared small components ──────────────────────────────────────────────────

function StatusBadge({ status }: { status: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    ACTIVE: { c: "#166534", bg: "#DCFCE7" }, PROCESSED: { c: "#166534", bg: "#DCFCE7" },
    PAID: { c: "#166534", bg: "#DCFCE7" }, FILED: { c: "#166534", bg: "#DCFCE7" },
    DRAFT: { c: "#64748B", bg: "#F1F5F9" }, PENDING: { c: "#D97706", bg: "#FFFBEB" },
    SENT: { c: "#1D4ED8", bg: "#EFF6FF" }, PARTIAL: { c: "#1D4ED8", bg: "#EFF6FF" },
    OVERDUE: { c: "#DC2626", bg: "#FEF2F2" }, OFFBOARDED: { c: "#64748B", bg: "#F1F5F9" },
    TERMINATED: { c: "#64748B", bg: "#F1F5F9" }, REVOKED: { c: "#DC2626", bg: "#FEF2F2" },
  }
  const t = tones[status] ?? tones.DRAFT
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10.5, fontWeight: 700 }}>{status}</span>
}

function Table({ headers, children }: { headers: string[]; children: React.ReactNode }) {
  return (
    <table style={{ width: "100%", borderCollapse: "collapse" as const, background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, overflow: "hidden" }}>
      <thead>
        <tr style={{ background: CANVAS }}>
          {headers.map(h => <th key={h} style={{ textAlign: "left" as const, padding: "8px 12px", fontSize: 11, fontWeight: 600, color: MUTED, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>{h}</th>)}
        </tr>
      </thead>
      <tbody>{children}</tbody>
    </table>
  )
}

function Empty({ text }: { text: string }) {
  return <div style={{ padding: 32, textAlign: "center" as const, color: FAINT, fontSize: 13 }}>{text}</div>
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}
      onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 12, padding: 24, width: 460, maxHeight: "85vh", overflowY: "auto" as const, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}
        onClick={e => e.stopPropagation()}>
        <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 800, color: INK }}>{title}</h3>
        {children}
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 12, flex: 1 }}>
      <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>{label}</label>
      {children}
    </div>
  )
}

function FieldError({ text }: { text?: string }) {
  if (!text) return null
  return <div style={{ fontSize: 11, color: "#DC2626", marginTop: 3 }}>{text}</div>
}

function ErrorBox({ text }: { text: string }) {
  return <div style={{ padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 6, fontSize: 12.5, color: "#DC2626", marginBottom: 12 }}>{text}</div>
}

function ModalActions({ onClose, onSubmit, saving, submitLabel }: { onClose: () => void; onSubmit: () => void; saving: boolean; submitLabel: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 4 }}>
      <button onClick={onClose} style={btnSecondary}>Cancel</button>
      <button onClick={onSubmit} disabled={saving} style={btnPrimary}>{saving ? "Saving…" : submitLabel}</button>
    </div>
  )
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" }
const errorInputStyle: React.CSSProperties = { borderColor: "#DC2626" }
const btnPrimary: React.CSSProperties = { padding: "7px 14px", background: NAVY, color: "#fff", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnPrimarySmall: React.CSSProperties = { ...btnPrimary, padding: "4px 10px", fontSize: 11.5 }
const btnSecondary: React.CSSProperties = { padding: "6px 12px", background: "#fff", color: NAVY, border: `1px solid ${NAVY}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
const rowStyle: React.CSSProperties = { borderTop: `1px solid ${BORDER}` }
const cellStyle: React.CSSProperties = { padding: "8px 12px", fontSize: 12.5, color: INK }
