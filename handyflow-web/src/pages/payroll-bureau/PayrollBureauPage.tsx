// src/pages/payroll-bureau/PayrollBureauPage.tsx
//
// Staff-facing single-page shell (same pattern App.tsx's own comments
// describe for ClinicPage/ProjectsPage) — client list on the left,
// selected client's detail (tabs) on the right.
//
// UPDATED: closed the full gap list found in this session's audit.
// Same-pattern gaps (backend existed, no button): client edit, client
// archive, client search, portal-access revoke, record payment on fee
// notes, mark-deadline-filed. Two more serious ones: FeeNotesTab's own
// text told users to generate invoices "from the Pay Runs tab" — a
// feature that never actually existed there; and employee documents
// (uploadDocument/getEmployeeDocuments/downloadDocument) had zero UI
// despite being fully wired on the backend. Both fixed below.
import { useEffect, useState } from "react"
import { payrollBureauApi } from "../../api/payrollBureau.api"
import type { PayClient, PayEmployee, PayRun, Payslip, PayDeadline, PayFeeNote, PortalAccessGrant } from "../../types/payrollBureau.types"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const CANVAS = "#F8FAFC"
const INK = "#0F172A"
const MUTED = "#64748B"
const FAINT = "#94A3B8"

type Tab = "employees" | "payruns" | "deadlines" | "feenotes" | "portal"

export function PayrollBureauPage() {
  const [clients, setClients] = useState<PayClient[]>([])
  const [clientsLoading, setClientsLoading] = useState(true)
  const [selected, setSelected] = useState<PayClient | null>(null)
  const [showNewClient, setShowNewClient] = useState(false)
  const [showEditClient, setShowEditClient] = useState(false)
  const [archiving, setArchiving] = useState(false)
  const [tab, setTab] = useState<Tab>("employees")
  // Client-side filter only — same caveat as the other two agency
  // modules: getClients() has no server-side search param.
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
      {/* Client list */}
      <div style={{ width: 300, borderRight: `1px solid ${BORDER}`, background: "#fff", display: "flex", flexDirection: "column" }}>
        <div style={{ padding: 16, borderBottom: `1px solid ${BORDER}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2 style={{ fontSize: 15, fontWeight: 800, color: INK, margin: 0 }}>Payroll Clients</h2>
          <button onClick={() => setShowNewClient(true)}
            style={{ padding: "5px 10px", background: NAVY, color: "#fff", border: "none", borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
            + New
          </button>
        </div>
        {/* NEW: client search */}
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

      {/* Detail panel */}
      <div style={{ flex: 1, overflowY: "auto" as const, background: CANVAS }}>
        {!selected ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%", color: FAINT, fontSize: 14 }}>
            Select a client, or add a new one
          </div>
        ) : (
          <div style={{ padding: "24px 32px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 4 }}>
              <h1 style={{ fontSize: 20, fontWeight: 800, color: INK, margin: 0 }}>{selected.tradingName}</h1>
              {/* NEW: edit + offboard/reactivate */}
              <div style={{ display: "flex", gap: 8 }}>
                <button onClick={() => setShowEditClient(true)} style={btnSecondary}>Edit</button>
                <button onClick={toggleArchive} disabled={archiving}
                  style={{ ...btnSecondary, color: selected.status === "OFFBOARDED" ? "#166534" : "#DC2626",
                    borderColor: selected.status === "OFFBOARDED" ? "#166534" : "#DC2626" }}>
                  {archiving ? "…" : selected.status === "OFFBOARDED" ? "Reactivate" : "Offboard"}
                </button>
              </div>
            </div>
            <p style={{ fontSize: 12.5, color: MUTED, marginBottom: 20 }}>
              PAYE {selected.payeReference ?? "—"} · UIF {selected.uifReference ?? "—"} · SDL {selected.sdlReference ?? "exempt"}
            </p>

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
    </div>
  )
}

// NEW: reuses NewClientModal's field set, pre-filled, calling updateClient.
function EditClientModal({ client, onClose, onSaved }: { client: PayClient; onClose: () => void; onSaved: (c: PayClient) => void }) {
  const [tradingName, setTradingName] = useState(client.tradingName)
  const [payeReference, setPayeReference] = useState(client.payeReference ?? "")
  const [uifReference, setUifReference] = useState(client.uifReference ?? "")
  const [contactEmail, setContactEmail] = useState(client.contactEmail ?? "")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!tradingName) { setError("Trading name is required"); return }
    if (!contactEmail) { setError("Contact email is required — needed to send this client their invoices"); return }
    setSaving(true); setError("")
    try {
      const updated = await payrollBureauApi.updateClient(client.id, { tradingName, payeReference, uifReference, contactEmail } as any)
      onSaved(updated)
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to save client")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="Edit Payroll Client" onClose={onClose}>
      <Field label="Trading name"><input value={tradingName} onChange={e => setTradingName(e.target.value)} style={inputStyle} /></Field>
      <Field label="PAYE reference (optional)"><input value={payeReference} onChange={e => setPayeReference(e.target.value)} style={inputStyle} /></Field>
      <Field label="UIF reference (optional)"><input value={uifReference} onChange={e => setUifReference(e.target.value)} style={inputStyle} /></Field>
      <Field label="Contact email"><input value={contactEmail} onChange={e => setContactEmail(e.target.value)} style={inputStyle} required /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Save Changes" />
    </Modal>
  )
}

// ── Employees ────────────────────────────────────────────────────────────────

function EmployeesTab({ client }: { client: PayClient }) {
  const [employees, setEmployees] = useState<PayEmployee[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)
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
        <Table headers={["Employee #", "Name", "Gross Salary", "Status", ""]}>
          {employees.map(e => (
            <tr key={e.id} style={rowStyle}>
              <td style={cellStyle}>{e.employeeNumber}</td>
              <td style={cellStyle}>{e.fullName}</td>
              <td style={cellStyle}>{fmtR(e.grossSalary)}</td>
              <td style={cellStyle}><StatusBadge status={e.status} /></td>
              {/* NEW: employee documents — was fully backed with zero UI */}
              <td style={cellStyle}>
                <button onClick={() => setDocsFor(e)} style={btnSecondary}>Documents</button>
              </td>
            </tr>
          ))}
        </Table>
      )}
      {showNew && <NewEmployeeModal clientId={client.id} onClose={() => setShowNew(false)}
        onCreated={() => { setShowNew(false); refetch() }} />}
      {docsFor && <EmployeeDocumentsModal employee={docsFor} onClose={() => setDocsFor(null)} />}
    </div>
  )
}

function NewEmployeeModal({ clientId, onClose, onCreated }: { clientId: string; onClose: () => void; onCreated: () => void }) {
  const [firstName, setFirstName] = useState("")
  const [lastName, setLastName] = useState("")
  const [grossSalary, setGrossSalary] = useState("")
  const [startDate, setStartDate] = useState(new Date().toISOString().slice(0, 10))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!firstName || !lastName || !grossSalary) { setError("First name, last name, and gross salary are required"); return }
    setSaving(true); setError("")
    try {
      await payrollBureauApi.createEmployee(clientId, {
        firstName, lastName, grossSalary: Number(grossSalary), startDate,
      } as any)
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to add employee")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="Add Employee" onClose={onClose}>
      <Field label="First name"><input value={firstName} onChange={e => setFirstName(e.target.value)} style={inputStyle} /></Field>
      <Field label="Last name"><input value={lastName} onChange={e => setLastName(e.target.value)} style={inputStyle} /></Field>
      <Field label="Gross salary (monthly)"><input type="number" value={grossSalary} onChange={e => setGrossSalary(e.target.value)} style={inputStyle} /></Field>
      <Field label="Start date"><input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} style={inputStyle} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Add Employee" />
    </Modal>
  )
}

// NEW: closes the employee-documents gap entirely — list existing docs
// (view/download, already-working backend methods) plus upload a new
// one. UNVERIFIED: the docType select's options are a reasonable guess
// at what a payroll bureau would file (ID, tax certificate, contract,
// banking details) — check against the real allowed values if the
// upload 400s on an unexpected docType string.
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
                  {/* NEW: this is the fix for FeeNotesTab's broken promise
                      — "generate from the Pay Runs tab" now actually
                      exists here, on a processed run. */}
                  {r.status === "PROCESSED" && (
                    <button onClick={e => { e.stopPropagation(); setGeneratingFeeNoteFor(r) }} style={btnPrimarySmall}>
                      Generate Invoice
                    </button>
                  )}
                </div>
              </div>
              {expandedRun === r.id && r.status === "PROCESSED" && (
                <div style={{ borderTop: `1px solid ${BORDER}`, padding: 12 }}>
                  <Table headers={["Employee", "Gross", "PAYE", "UIF", "Net Pay"]}>
                    {payslips.map(p => (
                      <tr key={p.id} style={rowStyle}>
                        <td style={cellStyle}>{p.employeeName}</td>
                        <td style={cellStyle}>{fmtR(p.grossSalary)}</td>
                        <td style={cellStyle}>{fmtR(p.payeAmount)}</td>
                        <td style={cellStyle}>{fmtR(p.uifEmployee)}</td>
                        <td style={cellStyle}><strong>{fmtR(p.netPay)}</strong></td>
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
    </div>
  )
}

// NEW: the actual missing piece — generateFeeNote() existed in the API
// client, called from nowhere. This is what FeeNotesTab's own text
// always claimed existed.
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
      <Field label="Period end"><input type="date" value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} style={inputStyle} /></Field>
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

  // NEW: markDeadlineFiled existed with no button — every deadline sat
  // PENDING forever regardless of whether it was actually filed with SARS.
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
      {/* FIX: this used to point at a feature that didn't exist. It now
          does — see PayRunsTab's "Generate Invoice" button. */}
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
                  {/* NEW: recordPayment existed with no button */}
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

  // NEW: revokePortalAccess existed with no button
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

// ── New Client Modal ──────────────────────────────────────────────────────────

function NewClientModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [tradingName, setTradingName] = useState("")
  const [payeReference, setPayeReference] = useState("")
  const [uifReference, setUifReference] = useState("")
  const [contactEmail, setContactEmail] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!tradingName) { setError("Trading name is required"); return }
    if (!contactEmail) { setError("Contact email is required — needed to send this client their invoices"); return }
      setSaving(true); setError("")
    try {
      await payrollBureauApi.createClient({ tradingName, payeReference, uifReference, contactEmail } as any)
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to create client")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="New Payroll Client" onClose={onClose}>
      <Field label="Trading name"><input value={tradingName} onChange={e => setTradingName(e.target.value)} style={inputStyle} /></Field>
      <Field label="PAYE reference (optional)"><input value={payeReference} onChange={e => setPayeReference(e.target.value)} style={inputStyle} /></Field>
      <Field label="UIF reference (optional)"><input value={uifReference} onChange={e => setUifReference(e.target.value)} style={inputStyle} /></Field>
      <Field label="Contact email"><input value={contactEmail} onChange={e => setContactEmail(e.target.value)} style={inputStyle} required /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Client" />
    </Modal>
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
      <div style={{ background: "#fff", borderRadius: 12, padding: 24, width: 420, maxHeight: "80vh", overflowY: "auto" as const, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}
        onClick={e => e.stopPropagation()}>
        <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 800, color: INK }}>{title}</h3>
        {children}
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>{label}</label>
      {children}
    </div>
  )
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
const btnPrimary: React.CSSProperties = { padding: "7px 14px", background: NAVY, color: "#fff", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnPrimarySmall: React.CSSProperties = { ...btnPrimary, padding: "4px 10px", fontSize: 11.5 }
const btnSecondary: React.CSSProperties = { padding: "6px 12px", background: "#fff", color: NAVY, border: `1px solid ${NAVY}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
const rowStyle: React.CSSProperties = { borderTop: `1px solid ${BORDER}` }
const cellStyle: React.CSSProperties = { padding: "8px 12px", fontSize: 12.5, color: INK }
