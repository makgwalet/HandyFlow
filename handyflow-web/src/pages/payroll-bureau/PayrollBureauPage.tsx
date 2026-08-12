// src/pages/payroll-bureau/PayrollBureauPage.tsx
//
// Staff-facing single-page shell (same pattern App.tsx's own comments
// describe for ClinicPage/ProjectsPage) — client list on the left,
// selected client's detail (tabs) on the right. Lives inside
// ModuleLayout via App.tsx's existing ProtectedRoute group, so it
// inherits the staff nav bar automatically — nothing route-specific
// needed beyond adding one <Route> entry.
//
// SCOPE: covers client onboarding, employee management, and the full
// pay-run lifecycle (create → process → view payslips) as complete,
// real flows. Deadlines/Fee Notes/Portal Access tabs are simpler —
// list + the one or two actions each needs (generate, send, invite) —
// not full editable forms for every field. That's a deliberate scope
// line, not an oversight: those three are lower-frequency actions than
// "onboard a client" and "run payroll", which is why this pass focused
// there first.
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
  const [tab, setTab] = useState<Tab>("employees")

  const refetchClients = () => {
    setClientsLoading(true)
    payrollBureauApi.getClients()
     .then(res => setClients(res.content))
     .catch(err => { console.error("Failed to load payroll clients:", err); setClients([]) })
     .finally(() => setClientsLoading(false)) }

  useEffect(() => { refetchClients() }, [])

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
        <div style={{ flex: 1, overflowY: "auto" as const }}>
          {clientsLoading ? (
            <div style={{ padding: 20, color: FAINT, fontSize: 13 }}>Loading…</div>
          ) : clients.length === 0 ? (
            <div style={{ padding: 20, color: FAINT, fontSize: 13 }}>No clients yet — add your first one.</div>
          ) : (
            clients.map(c => (
              <button key={c.id} onClick={() => { setSelected(c); setTab("employees") }}
                style={{ display: "block", width: "100%", textAlign: "left" as const, padding: "12px 16px",
                  background: selected?.id === c.id ? "#EFF6FF" : "none", border: "none",
                  borderBottom: `1px solid ${BORDER}`, cursor: "pointer" }}>
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
            <h1 style={{ fontSize: 20, fontWeight: 800, color: INK, marginBottom: 4 }}>{selected.tradingName}</h1>
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
    </div>
  )
}

// ── Employees ────────────────────────────────────────────────────────────────

function EmployeesTab({ client }: { client: PayClient }) {
  const [employees, setEmployees] = useState<PayEmployee[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)

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
        <Table headers={["Employee #", "Name", "Gross Salary", "Status"]}>
          {employees.map(e => (
            <tr key={e.id} style={rowStyle}>
              <td style={cellStyle}>{e.employeeNumber}</td>
              <td style={cellStyle}>{e.fullName}</td>
              <td style={cellStyle}>{fmtR(e.grossSalary)}</td>
              <td style={cellStyle}><StatusBadge status={e.status} /></td>
            </tr>
          ))}
        </Table>
      )}
      {showNew && <NewEmployeeModal clientId={client.id} onClose={() => setShowNew(false)}
        onCreated={() => { setShowNew(false); refetch() }} />}
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

// ── Pay Runs ─────────────────────────────────────────────────────────────────

function PayRunsTab({ client }: { client: PayClient }) {
  const [payRuns, setPayRuns] = useState<PayRun[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)
  const [expandedRun, setExpandedRun] = useState<string | null>(null)
  const [payslips, setPayslips] = useState<Payslip[]>([])
  const [processingId, setProcessingId] = useState<string | null>(null)

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
    </div>
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

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={handleGenerate} disabled={generating} style={btnPrimary}>
          {generating ? "Generating…" : `Generate ${new Date().getFullYear()} Deadlines`}
        </button>
      </div>
      {loading ? <Empty text="Loading…" /> : deadlines.length === 0 ? <Empty text="No deadlines generated yet." /> : (
        <Table headers={["Type", "Period", "Due Date", "Status"]}>
          {deadlines.map(d => (
            <tr key={d.id} style={rowStyle}>
              <td style={cellStyle}>{d.deadlineType}</td>
              <td style={cellStyle}>{d.periodMonth ? `${d.periodYear}/${String(d.periodMonth).padStart(2, "0")}` : d.periodYear}</td>
              <td style={cellStyle}>{fmtD(d.adjustedDueDate)}</td>
              <td style={cellStyle}><StatusBadge status={d.status} /></td>
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
                {f.status === "DRAFT" && <button onClick={() => handleSend(f.id)} style={btnSecondary}>Send</button>}
              </td>
            </tr>
          ))}
        </Table>
      )}
    </div>
  )
}

// ── Portal Access ────────────────────────────────────────────────────────────

function PortalAccessTab({ client }: { client: PayClient }) {
  const [grants, setGrants] = useState<PortalAccessGrant[]>([])
  const [loading, setLoading] = useState(true)
  const [email, setEmail] = useState("")
  const [inviting, setInviting] = useState(false)

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
        <Table headers={["Email", "Status", "Invited"]}>
          {grants.map(g => (
            <tr key={g.id} style={rowStyle}>
              <td style={cellStyle}>{g.inviteEmail}</td>
              <td style={cellStyle}><StatusBadge status={g.status} /></td>
              <td style={cellStyle}>{fmtD(g.invitedAt)}</td>
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
      <Field label="Contact email (optional)"><input value={contactEmail} onChange={e => setContactEmail(e.target.value)} style={inputStyle} /></Field>
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
          {headers.map(h => <th key={h} style={{ textAlign: "left" as const, padding: "8px 12px", fontSize: 11, fontWeight: 700, color: MUTED, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>{h}</th>)}
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
      <div style={{ background: "#fff", borderRadius: 12, padding: 24, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}
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
