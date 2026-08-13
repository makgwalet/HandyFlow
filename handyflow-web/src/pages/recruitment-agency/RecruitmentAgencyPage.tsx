// src/pages/recruitment-agency/RecruitmentAgencyPage.tsx
//
// Two top-level sections, not the simple "client list + tabs" shape
// used for Payroll Bureau and Booking Agency — candidates are the
// agency's OWN pool (RecAgencyCandidate has no clientId), submitted
// against requisitions as needed, not scoped to one client the way
// employees/resources are in the other two modules. Forcing this into
// the same single-client-scoped shell would misrepresent the domain.
import { useEffect, useState } from "react"
import { recruitmentAgencyApi } from "../../api/recruitmentAgency.api"
import type { AgencyClient, Requisition, Candidate, Placement, AgencyInvoice, PortalAccessGrant } from "../../types/recruitmentAgency.types"
import { PLACEMENT_STAGES, TERMINAL_STAGES } from "../../types/recruitmentAgency.types"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const CANVAS = "#F8FAFC"
const INK = "#0F172A"
const MUTED = "#64748B"
const FAINT = "#94A3B8"

type Section = "clients" | "candidates"
type ClientTab = "requisitions" | "invoices" | "portal"

export function RecruitmentAgencyPage() {
  const [section, setSection] = useState<Section>("clients")

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "calc(100vh - 60px)", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ display: "flex", gap: 4, borderBottom: `1px solid ${BORDER}`, background: "#fff", padding: "0 16px" }}>
        {([["clients", "Clients & Requisitions"], ["candidates", "Candidate Pool"]] as [Section, string][]).map(([id, label]) => (
          <button key={id} onClick={() => setSection(id)} style={{
            padding: "12px 16px", background: "none", border: "none",
            borderBottom: section === id ? `2px solid ${NAVY}` : "2px solid transparent",
            color: section === id ? NAVY : MUTED, fontWeight: section === id ? 700 : 500, fontSize: 13.5, cursor: "pointer", marginBottom: -1,
          }}>{label}</button>
        ))}
      </div>
      <div style={{ flex: 1, overflow: "hidden" }}>
        {section === "clients" ? <ClientsSection /> : <CandidatePoolSection />}
      </div>
    </div>
  )
}

// ── Clients & Requisitions section ────────────────────────────────────────────

function ClientsSection() {
  const [clients, setClients] = useState<AgencyClient[]>([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<AgencyClient | null>(null)
  const [showNewClient, setShowNewClient] = useState(false)
  const [tab, setTab] = useState<ClientTab>("requisitions")
  const [selectedRequisition, setSelectedRequisition] = useState<Requisition | null>(null)

  const refetch = () => {
    setLoading(true)
    recruitmentAgencyApi.getClients().then(res => setClients(res.content)).finally(() => setLoading(false))
  }
  useEffect(refetch, [])

  return (
    <div style={{ display: "flex", height: "100%" }}>
      <div style={{ width: 280, borderRight: `1px solid ${BORDER}`, background: "#fff", display: "flex", flexDirection: "column" }}>
        <div style={{ padding: 14, borderBottom: `1px solid ${BORDER}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2 style={{ fontSize: 14, fontWeight: 800, color: INK, margin: 0 }}>Clients</h2>
          <button onClick={() => setShowNewClient(true)} style={btnPrimary}>+ New</button>
        </div>
        <div style={{ flex: 1, overflowY: "auto" as const }}>
          {loading ? <div style={{ padding: 16, color: FAINT, fontSize: 13 }}>Loading…</div> :
            clients.map(c => (
              <button key={c.id} onClick={() => { setSelected(c); setTab("requisitions"); setSelectedRequisition(null) }}
                style={{ display: "block", width: "100%", textAlign: "left" as const, padding: "10px 14px",
                  background: selected?.id === c.id ? "#EFF6FF" : "none", border: "none", borderBottom: `1px solid ${BORDER}`, cursor: "pointer" }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: INK }}>{c.tradingName}</div>
                <div style={{ fontSize: 11, color: FAINT, marginTop: 2 }}>{c.effectivePlacementFeePct}% placement fee</div>
              </button>
            ))}
        </div>
      </div>

      <div style={{ flex: 1, overflowY: "auto" as const, background: CANVAS, padding: "20px 28px" }}>
        {!selected ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%", color: FAINT, fontSize: 14 }}>
            Select a client, or add a new one
          </div>
        ) : selectedRequisition ? (
          <PlacementsView requisition={selectedRequisition} onBack={() => setSelectedRequisition(null)} />
        ) : (
          <>
            <h1 style={{ fontSize: 18, fontWeight: 800, color: INK, marginBottom: 4 }}>{selected.tradingName}</h1>
            <p style={{ fontSize: 12, color: MUTED, marginBottom: 16 }}>{selected.industry ?? "No industry set"}</p>
            <div style={{ display: "flex", gap: 4, borderBottom: `1px solid ${BORDER}`, marginBottom: 16 }}>
              {([["requisitions", "Requisitions"], ["invoices", "Invoices"], ["portal", "Portal Access"]] as [ClientTab, string][]).map(([id, label]) => (
                <button key={id} onClick={() => setTab(id)} style={{
                  padding: "7px 12px", background: "none", border: "none",
                  borderBottom: tab === id ? `2px solid ${NAVY}` : "2px solid transparent",
                  color: tab === id ? NAVY : MUTED, fontWeight: tab === id ? 700 : 500, fontSize: 12.5, cursor: "pointer", marginBottom: -1,
                }}>{label}</button>
              ))}
            </div>
            {tab === "requisitions" && <RequisitionsTab client={selected} onSelectRequisition={setSelectedRequisition} />}
            {tab === "invoices" && <InvoicesTab client={selected} />}
            {tab === "portal" && <PortalAccessTab client={selected} />}
          </>
        )}
      </div>

      {showNewClient && <NewClientModal onClose={() => setShowNewClient(false)} onCreated={() => { setShowNewClient(false); refetch() }} />}
    </div>
  )
}

function RequisitionsTab({ client, onSelectRequisition }: { client: AgencyClient; onSelectRequisition: (r: Requisition) => void }) {
  const [requisitions, setRequisitions] = useState<Requisition[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)

  const refetch = () => {
    setLoading(true)
    recruitmentAgencyApi.getRequisitionsForClient(client.id).then(setRequisitions).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 10 }}>
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ New Requisition</button>
      </div>
      {loading ? <Empty text="Loading…" /> : requisitions.length === 0 ? <Empty text="No requisitions yet." /> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {requisitions.map(r => (
            <button key={r.id} onClick={() => onSelectRequisition(r)}
              style={{ textAlign: "left" as const, padding: "12px 16px", background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, cursor: "pointer" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <span style={{ fontWeight: 700, fontSize: 13.5, color: INK }}>{r.title}</span>
                  <span style={{ marginLeft: 8, fontSize: 11.5, color: FAINT }}>{r.requisitionNumber}</span>
                </div>
                <StatusBadge status={r.status} />
              </div>
              <div style={{ fontSize: 11.5, color: FAINT, marginTop: 4 }}>
                {r.location ?? "No location"} · {r.candidateCount} candidate{r.candidateCount !== 1 ? "s" : ""} in pipeline
                {r.salaryMin && ` · ${fmtR(r.salaryMin)}–${fmtR(r.salaryMax)}`}
              </div>
            </button>
          ))}
        </div>
      )}
      {showNew && <NewRequisitionModal clientId={client.id} onClose={() => setShowNew(false)} onCreated={() => { setShowNew(false); refetch() }} />}
    </div>
  )
}

function NewRequisitionModal({ clientId, onClose, onCreated }: { clientId: string; onClose: () => void; onCreated: () => void }) {
  const [title, setTitle] = useState("")
  const [location, setLocation] = useState("")
  const [salaryMin, setSalaryMin] = useState("")
  const [salaryMax, setSalaryMax] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!title) { setError("Title is required"); return }
    setSaving(true); setError("")
    try {
      await recruitmentAgencyApi.createRequisition({
        clientId, title, location,
        salaryMin: salaryMin ? Number(salaryMin) : undefined,
        salaryMax: salaryMax ? Number(salaryMax) : undefined,
      })
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to create requisition")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="New Requisition" onClose={onClose}>
      <Field label="Job title"><input value={title} onChange={e => setTitle(e.target.value)} style={inputStyle} /></Field>
      <Field label="Location (optional)"><input value={location} onChange={e => setLocation(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Salary min (optional)"><input type="number" value={salaryMin} onChange={e => setSalaryMin(e.target.value)} style={inputStyle} /></Field>
        <Field label="Salary max (optional)"><input type="number" value={salaryMax} onChange={e => setSalaryMax(e.target.value)} style={inputStyle} /></Field>
      </div>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Requisition" />
    </Modal>
  )
}

function PlacementsView({ requisition, onBack }: { requisition: Requisition; onBack: () => void }) {
  const [placements, setPlacements] = useState<Placement[]>([])
  const [loading, setLoading] = useState(true)
  const [showSubmit, setShowSubmit] = useState(false)
  const [markPlacedFor, setMarkPlacedFor] = useState<Placement | null>(null)

  const refetch = () => {
    setLoading(true)
    recruitmentAgencyApi.getPlacements(requisition.id).then(setPlacements).finally(() => setLoading(false))
  }
  useEffect(refetch, [requisition.id])

  const handleAdvance = async (p: Placement, toStage: string) => {
    if (toStage === "PLACED") { setMarkPlacedFor(p); return }
    await recruitmentAgencyApi.advanceStage(p.id, toStage)
    refetch()
  }

  return (
    <div>
      <button onClick={onBack} style={{ background: "none", border: "none", color: MUTED, fontSize: 12.5, cursor: "pointer", marginBottom: 12 }}>← Back to requisitions</button>
      <h2 style={{ fontSize: 16, fontWeight: 800, color: INK, marginBottom: 2 }}>{requisition.title}</h2>
      <p style={{ fontSize: 12, color: MUTED, marginBottom: 16 }}>{requisition.requisitionNumber} · <StatusBadge status={requisition.status} /></p>

      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 10 }}>
        <button onClick={() => setShowSubmit(true)} style={btnPrimary} disabled={requisition.status !== "OPEN"}>+ Submit Candidate</button>
      </div>

      {loading ? <Empty text="Loading…" /> : placements.length === 0 ? <Empty text="No candidates submitted yet." /> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {placements.map(p => (
            <div key={p.id} style={{ padding: "12px 16px", background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span style={{ fontWeight: 700, fontSize: 13.5, color: INK }}>{p.candidateName}</span>
                <StatusBadge status={p.stage} />
              </div>
              {p.placementFeeAmount != null && (
                <div style={{ fontSize: 12, color: MUTED, marginTop: 4 }}>
                  Offered {fmtR(p.offeredSalary)} · Fee {fmtR(p.placementFeeAmount)}
                  {p.guaranteeEndsAt && ` · Guarantee ends ${fmtD(p.guaranteeEndsAt)}`}
                </div>
              )}
              {!TERMINAL_STAGES.includes(p.stage) && p.stage !== "PLACED" && (
                <div style={{ marginTop: 8 }}>
                  <select onChange={e => e.target.value && handleAdvance(p, e.target.value)} value=""
                    style={{ ...inputStyle, width: "auto", fontSize: 12 }}>
                    <option value="">Advance stage…</option>
                    {PLACEMENT_STAGES.filter(s => s !== p.stage).map(s => <option key={s} value={s}>{s.replace(/_/g, " ")}</option>)}
                    <option value="REJECTED_BY_CLIENT">Rejected by client</option>
                    <option value="WITHDRAWN">Withdrawn</option>
                    <option value="CANDIDATE_DECLINED">Candidate declined</option>
                  </select>
                </div>
              )}
              {p.stage === "PLACED" && (
                <GenerateInvoiceButton placement={p} />
              )}
            </div>
          ))}
        </div>
      )}

      {showSubmit && <SubmitCandidateModal requisitionId={requisition.id} onClose={() => setShowSubmit(false)} onSubmitted={() => { setShowSubmit(false); refetch() }} />}
      {markPlacedFor && <MarkPlacedModal placement={markPlacedFor} onClose={() => setMarkPlacedFor(null)} onConfirmed={() => { setMarkPlacedFor(null); refetch() }} />}
    </div>
  )
}

function GenerateInvoiceButton({ placement }: { placement: Placement }) {
  const [generating, setGenerating] = useState(false)
  const [done, setDone] = useState(false)

  const generate = async () => {
    setGenerating(true)
    try {
      const today = new Date().toISOString().slice(0, 10)
      const due = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10)
      await recruitmentAgencyApi.generateInvoice(placement.id, { invoiceDate: today, dueDate: due, includeVat: true })
      setDone(true)
    } catch (e: any) {
      alert(e.response?.data?.message ?? "Failed to generate invoice")
    } finally { setGenerating(false) }
  }

  if (done) return <div style={{ marginTop: 8, fontSize: 12, color: "#166534", fontWeight: 600 }}>✓ Invoice generated — see the Invoices tab</div>
  return <button onClick={generate} disabled={generating} style={{ ...btnSecondary, marginTop: 8 }}>{generating ? "Generating…" : "Generate Invoice"}</button>
}

function SubmitCandidateModal({ requisitionId, onClose, onSubmitted }: { requisitionId: string; onClose: () => void; onSubmitted: () => void }) {
  const [search, setSearch] = useState("")
  const [results, setResults] = useState<Candidate[]>([])
  const [submitting, setSubmitting] = useState<string | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    const t = setTimeout(() => {
      recruitmentAgencyApi.searchCandidates(search).then(res => setResults(res.content))
    }, 250)
    return () => clearTimeout(t)
  }, [search])

  const submit = async (candidateId: string) => {
    setSubmitting(candidateId); setError("")
    try {
      await recruitmentAgencyApi.submitCandidate(requisitionId, candidateId)
      onSubmitted()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to submit candidate")
      setSubmitting(null)
    }
  }

  return (
    <Modal title="Submit a Candidate" onClose={onClose}>
      <input placeholder="Search the candidate pool…" value={search} onChange={e => setSearch(e.target.value)} style={{ ...inputStyle, marginBottom: 10 }} autoFocus />
      {error && <ErrorBox text={error} />}
      <div style={{ maxHeight: 280, overflowY: "auto" as const }}>
        {results.map(c => (
          <div key={c.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 0", borderBottom: `1px solid ${BORDER}` }}>
            <div>
              <div style={{ fontSize: 13, fontWeight: 700, color: INK }}>{c.fullName}</div>
              <div style={{ fontSize: 11.5, color: FAINT }}>{c.currentTitle ?? "No title set"}</div>
            </div>
            <button onClick={() => submit(c.id)} disabled={submitting === c.id} style={btnPrimary}>
              {submitting === c.id ? "…" : "Submit"}
            </button>
          </div>
        ))}
        {results.length === 0 && <div style={{ padding: 16, color: FAINT, fontSize: 13, textAlign: "center" as const }}>No candidates found — try a different search, or add one in the Candidate Pool tab first.</div>}
      </div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 14 }}>
        <button onClick={onClose} style={btnSecondary}>Close</button>
      </div>
    </Modal>
  )
}

function MarkPlacedModal({ placement, onClose, onConfirmed }: { placement: Placement; onClose: () => void; onConfirmed: () => void }) {
  const [salary, setSalary] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!salary) { setError("Offered salary is required"); return }
    setSaving(true); setError("")
    try {
      await recruitmentAgencyApi.markPlaced(placement.id, Number(salary))
      onConfirmed()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to confirm placement")
    } finally { setSaving(false) }
  }

  return (
    <Modal title={`Confirm Placement — ${placement.candidateName}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: MUTED, marginBottom: 12 }}>
        This computes the placement fee, marks the requisition filled, and withdraws any other candidates still in the pipeline for this role.
      </p>
      <Field label="Offered annual salary"><input type="number" value={salary} onChange={e => setSalary(e.target.value)} style={inputStyle} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Confirm Placement" />
    </Modal>
  )
}

function InvoicesTab({ client }: { client: AgencyClient }) {
  const [invoices, setInvoices] = useState<AgencyInvoice[]>([])
  const [loading, setLoading] = useState(true)

  const refetch = () => {
    setLoading(true)
    recruitmentAgencyApi.getInvoices(client.id).then(res => setInvoices(res.content)).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const handleSend = async (id: string) => { await recruitmentAgencyApi.sendInvoice(id); refetch() }

  return (
    <div>
      <p style={{ fontSize: 12, color: MUTED, marginBottom: 10 }}>Generate invoices from a placement's row once it's PLACED (Requisitions tab).</p>
      {loading ? <Empty text="Loading…" /> : invoices.length === 0 ? <Empty text="No invoices yet." /> : (
        <Table headers={["Invoice #", "Description", "Total", "Balance", "Status", ""]}>
          {invoices.map(inv => (
            <tr key={inv.id} style={rowStyle}>
              <td style={cellStyle}>{inv.invoiceNumber}</td>
              <td style={cellStyle}>{inv.description}</td>
              <td style={cellStyle}>{fmtR(inv.total)}</td>
              <td style={cellStyle}>{fmtR(inv.balance)}</td>
              <td style={cellStyle}><StatusBadge status={inv.status} /></td>
              <td style={cellStyle}>{inv.status === "DRAFT" && <button onClick={() => handleSend(inv.id)} style={btnSecondary}>Send</button>}</td>
            </tr>
          ))}
        </Table>
      )}
    </div>
  )
}

function PortalAccessTab({ client }: { client: AgencyClient }) {
  const [grants, setGrants] = useState<PortalAccessGrant[]>([])
  const [loading, setLoading] = useState(true)
  const [email, setEmail] = useState("")
  const [inviting, setInviting] = useState(false)

  const refetch = () => {
    setLoading(true)
    recruitmentAgencyApi.getPortalAccessGrants(client.id).then(setGrants).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const handleInvite = async () => {
    if (!email) return
    setInviting(true)
    try { await recruitmentAgencyApi.invitePortalUser(client.id, email); setEmail(""); refetch() }
    catch (e: any) { alert(e.response?.data?.message ?? "Failed to send invite") }
    finally { setInviting(false) }
  }

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
        <input placeholder="client@example.com" value={email} onChange={e => setEmail(e.target.value)} style={{ ...inputStyle, flex: 1 }} />
        <button onClick={handleInvite} disabled={inviting} style={btnPrimary}>{inviting ? "Sending…" : "Invite to Portal"}</button>
      </div>
      {loading ? <Empty text="Loading…" /> : grants.length === 0 ? <Empty text="No portal invites sent yet." /> : (
        <Table headers={["Email", "Status", "Invited"]}>
          {grants.map(g => <tr key={g.id} style={rowStyle}><td style={cellStyle}>{g.inviteEmail}</td><td style={cellStyle}><StatusBadge status={g.status} /></td><td style={cellStyle}>{fmtD(g.invitedAt)}</td></tr>)}
        </Table>
      )}
    </div>
  )
}

function NewClientModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [tradingName, setTradingName] = useState("")
  const [industry, setIndustry] = useState("")
  const [placementFeePct, setPlacementFeePct] = useState("")
  const [guaranteeDays, setGuaranteeDays] = useState("60")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!tradingName) { setError("Trading name is required"); return }
    setSaving(true); setError("")
    try {
      await recruitmentAgencyApi.createClient({
        tradingName, industry,
        placementFeePct: placementFeePct ? Number(placementFeePct) : undefined,
        guaranteePeriodDays: guaranteeDays ? Number(guaranteeDays) : undefined,
      })
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to create client")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="New Agency Client" onClose={onClose}>
      <Field label="Trading name"><input value={tradingName} onChange={e => setTradingName(e.target.value)} style={inputStyle} /></Field>
      <Field label="Industry (optional)"><input value={industry} onChange={e => setIndustry(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Placement fee % (blank = agency default)"><input type="number" value={placementFeePct} onChange={e => setPlacementFeePct(e.target.value)} style={inputStyle} /></Field>
        <Field label="Guarantee period (days)"><input type="number" value={guaranteeDays} onChange={e => setGuaranteeDays(e.target.value)} style={inputStyle} /></Field>
      </div>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Client" />
    </Modal>
  )
}

// ── Candidate Pool section (agency-wide, not client-scoped) ──────────────────

function CandidatePoolSection() {
  const [search, setSearch] = useState("")
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)

  const refetch = () => {
    setLoading(true)
    recruitmentAgencyApi.searchCandidates(search).then(res => setCandidates(res.content)).finally(() => setLoading(false))
  }
  useEffect(() => { const t = setTimeout(refetch, 250); return () => clearTimeout(t) }, [search])

  const handleUploadCv = async (candidateId: string, file: File) => {
    await recruitmentAgencyApi.uploadCv(candidateId, file)
    refetch()
  }

  return (
    <div style={{ padding: "20px 28px", height: "100%", overflowY: "auto" as const, background: CANVAS }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <input placeholder="Search candidates by name or skills…" value={search} onChange={e => setSearch(e.target.value)} style={{ ...inputStyle, width: 320 }} />
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ Add Candidate</button>
      </div>
      {loading ? <Empty text="Loading…" /> : candidates.length === 0 ? <Empty text="No candidates in the pool yet." /> : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 12 }}>
          {candidates.map(c => (
            <div key={c.id} style={{ padding: 14, background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8 }}>
              <div style={{ fontWeight: 700, fontSize: 13.5, color: INK }}>{c.fullName}</div>
              <div style={{ fontSize: 12, color: MUTED, marginTop: 2 }}>{c.currentTitle ?? "No title"} {c.currentEmployer && `at ${c.currentEmployer}`}</div>
              {c.skills && <div style={{ fontSize: 11.5, color: FAINT, marginTop: 6 }}>{c.skills}</div>}
              <div style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 8 }}>
                <StatusBadge status={c.status} />
                {c.hasCv ? (
                  <span style={{ fontSize: 11, color: "#166534" }}>✓ CV on file</span>
                ) : (
                  <label style={{ fontSize: 11, color: NAVY, cursor: "pointer", fontWeight: 600 }}>
                    Upload CV
                    <input type="file" style={{ display: "none" }} accept=".pdf,.doc,.docx"
                      onChange={e => { const f = e.target.files?.[0]; if (f) handleUploadCv(c.id, f) }} />
                  </label>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
      {showNew && <NewCandidateModal onClose={() => setShowNew(false)} onCreated={() => { setShowNew(false); refetch() }} />}
    </div>
  )
}

function NewCandidateModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [fullName, setFullName] = useState("")
  const [email, setEmail] = useState("")
  const [phone, setPhone] = useState("")
  const [currentTitle, setCurrentTitle] = useState("")
  const [skills, setSkills] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!fullName) { setError("Full name is required"); return }
    setSaving(true); setError("")
    try {
      await recruitmentAgencyApi.createCandidate({ fullName, email, phone, currentTitle, skills })
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to add candidate")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="Add Candidate" onClose={onClose}>
      <Field label="Full name"><input value={fullName} onChange={e => setFullName(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Email (optional)"><input value={email} onChange={e => setEmail(e.target.value)} style={inputStyle} /></Field>
        <Field label="Phone (optional)"><input value={phone} onChange={e => setPhone(e.target.value)} style={inputStyle} /></Field>
      </div>
      <Field label="Current title (optional)"><input value={currentTitle} onChange={e => setCurrentTitle(e.target.value)} style={inputStyle} /></Field>
      <Field label="Skills (optional, comma-separated)"><input value={skills} onChange={e => setSkills(e.target.value)} style={inputStyle} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Add Candidate" />
    </Modal>
  )
}

// ── Shared small components ──────────────────────────────────────────────────

function StatusBadge({ status }: { status: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    ACTIVE: { c: "#166534", bg: "#DCFCE7" }, OPEN: { c: "#1D4ED8", bg: "#EFF6FF" },
    FILLED: { c: "#166534", bg: "#DCFCE7" }, CANCELLED: { c: "#64748B", bg: "#F1F5F9" },
    ON_HOLD: { c: "#D97706", bg: "#FFFBEB" }, SUBMITTED: { c: "#1D4ED8", bg: "#EFF6FF" },
    CLIENT_REVIEW: { c: "#1D4ED8", bg: "#EFF6FF" }, CLIENT_INTERVIEW: { c: "#7C3AED", bg: "#F3E8FF" },
    OFFERED: { c: "#D97706", bg: "#FFFBEB" }, PLACED: { c: "#166534", bg: "#DCFCE7" },
    GUARANTEE_PERIOD: { c: "#D97706", bg: "#FFFBEB" }, COMPLETED: { c: "#166534", bg: "#DCFCE7" },
    REJECTED_BY_CLIENT: { c: "#DC2626", bg: "#FEF2F2" }, WITHDRAWN: { c: "#64748B", bg: "#F1F5F9" },
    CANDIDATE_DECLINED: { c: "#64748B", bg: "#F1F5F9" }, FAILED_GUARANTEE: { c: "#DC2626", bg: "#FEF2F2" },
    PLACED_STATUS: { c: "#166534", bg: "#DCFCE7" }, DO_NOT_CONTACT: { c: "#DC2626", bg: "#FEF2F2" },
    DRAFT: { c: "#64748B", bg: "#F1F5F9" }, SENT: { c: "#1D4ED8", bg: "#EFF6FF" },
    PARTIAL: { c: "#1D4ED8", bg: "#EFF6FF" }, PAID: { c: "#166534", bg: "#DCFCE7" },
    OVERDUE: { c: "#DC2626", bg: "#FEF2F2" }, PENDING: { c: "#D97706", bg: "#FFFBEB" },
    REVOKED: { c: "#DC2626", bg: "#FEF2F2" }, INACTIVE: { c: "#64748B", bg: "#F1F5F9" },
  }
  const t = tones[status] ?? tones.INACTIVE
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700, whiteSpace: "nowrap" as const }}>{status.replace(/_/g, " ")}</span>
}

function Table({ headers, children }: { headers: string[]; children: React.ReactNode }) {
  return (
    <table style={{ width: "100%", borderCollapse: "collapse" as const, background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, overflow: "hidden" }}>
      <thead><tr style={{ background: CANVAS }}>{headers.map(h => <th key={h} style={{ textAlign: "left" as const, padding: "8px 12px", fontSize: 11, fontWeight: 700, color: MUTED, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>{h}</th>)}</tr></thead>
      <tbody>{children}</tbody>
    </table>
  )
}

function Empty({ text }: { text: string }) { return <div style={{ padding: 32, textAlign: "center" as const, color: FAINT, fontSize: 13 }}>{text}</div> }

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }} onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 12, padding: 24, width: 440, maxHeight: "80vh", overflowY: "auto" as const, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }} onClick={e => e.stopPropagation()}>
        <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 800, color: INK }}>{title}</h3>
        {children}
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div style={{ marginBottom: 12, flex: 1 }}><label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>{label}</label>{children}</div>
}

function ErrorBox({ text }: { text: string }) {
  return <div style={{ padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 6, fontSize: 12.5, color: "#DC2626", marginBottom: 12 }}>{text}</div>
}

function ModalActions({ onClose, onSubmit, saving, submitLabel }: { onClose: () => void; onSubmit: () => void; saving: boolean; submitLabel: string }) {
  return <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 4 }}><button onClick={onClose} style={btnSecondary}>Cancel</button><button onClick={onSubmit} disabled={saving} style={btnPrimary}>{saving ? "Saving…" : submitLabel}</button></div>
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { padding: "7px 14px", background: NAVY, color: "#fff", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnSecondary: React.CSSProperties = { padding: "6px 12px", background: "#fff", color: NAVY, border: `1px solid ${NAVY}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
const rowStyle: React.CSSProperties = { borderTop: `1px solid ${BORDER}` }
const cellStyle: React.CSSProperties = { padding: "8px 12px", fontSize: 12.5, color: INK }
