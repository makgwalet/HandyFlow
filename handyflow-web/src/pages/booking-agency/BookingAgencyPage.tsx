// src/pages/booking-agency/BookingAgencyPage.tsx
//
// Staff-facing single-page shell, same pattern as PayrollBureauPage.tsx
// (client list + tabbed detail panel) — client list on the left,
// selected client's detail (tabs) on the right.
//
// SCOPE: covers client onboarding, resource/offering management, and
// the full booking lifecycle (create -> cancel/complete/no-show) as
// complete, real flows. DELIBERATELY NO INVOICES/BILLING TAB — this
// module's billing model (flat retainer vs. per-booking fee) is still
// an open business decision, never resolved. Showing a billing tab
// backed by nothing real would be worse than not having one.
import { useEffect, useState } from "react"
import { bookingAgencyApi } from "../../api/bookingAgency.api"
import type { BookAgencyClient, BookAgencyResource, BookAgencyOffering, BookAgencyBooking, PortalAccessGrant, BookAgencyInvoice } from "../../types/bookingAgency.types"

const fmtDT = (d: any) => (d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—")

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const CANVAS = "#F8FAFC"
const INK = "#0F172A"
const MUTED = "#64748B"
const FAINT = "#94A3B8"

type Tab = "resources" | "offerings" | "bookings" | "invoices" | "portal"

export function BookingAgencyPage() {
  const [clients, setClients] = useState<BookAgencyClient[]>([])
  const [clientsLoading, setClientsLoading] = useState(true)
  const [selected, setSelected] = useState<BookAgencyClient | null>(null)
  const [showNewClient, setShowNewClient] = useState(false)
  const [tab, setTab] = useState<Tab>("resources")

  const refetchClients = () => {
    setClientsLoading(true)
    bookingAgencyApi.getClients().then(res => setClients(res.content)).finally(() => setClientsLoading(false))
  }

  useEffect(() => { refetchClients() }, [])

  return (
    <div style={{ display: "flex", height: "calc(100vh - 60px)", fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Client list */}
      <div style={{ width: 300, borderRight: `1px solid ${BORDER}`, background: "#fff", display: "flex", flexDirection: "column" }}>
        <div style={{ padding: 16, borderBottom: `1px solid ${BORDER}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2 style={{ fontSize: 15, fontWeight: 800, color: INK, margin: 0 }}>Booking Clients</h2>
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
              <button key={c.id} onClick={() => { setSelected(c); setTab("resources") }}
                style={{ display: "block", width: "100%", textAlign: "left" as const, padding: "12px 16px",
                  background: selected?.id === c.id ? "#EFF6FF" : "none", border: "none",
                  borderBottom: `1px solid ${BORDER}`, cursor: "pointer" }}>
                <div style={{ fontSize: 13.5, fontWeight: 700, color: INK }}>{c.tradingName}</div>
                <div style={{ fontSize: 11.5, color: FAINT, marginTop: 2 }}>
                  {c.status === "INACTIVE" ? "Inactive" : (c.businessType ?? "No business type set")}
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
              {selected.businessType ?? "No business type set"} · {selected.timezone}
              {selected.contactName && ` · ${selected.contactName}`}
            </p>

            <div style={{ display: "flex", gap: 4, borderBottom: `1px solid ${BORDER}`, marginBottom: 20 }}>
              {([
                ["resources", "Resources"], ["offerings", "Offerings"],
                ["bookings", "Bookings"], ["invoices", "Invoices"], ["portal", "Portal Access"],
              ] as [Tab, string][]).map(([id, label]) => (
                <button key={id} onClick={() => setTab(id)} style={{
                  padding: "8px 14px", background: "none", border: "none",
                  borderBottom: tab === id ? `2px solid ${NAVY}` : "2px solid transparent",
                  color: tab === id ? NAVY : MUTED, fontWeight: tab === id ? 700 : 500, fontSize: 13, cursor: "pointer", marginBottom: -1,
                }}>{label}</button>
              ))}
            </div>

            {tab === "resources" && <ResourcesTab client={selected} />}
            {tab === "offerings" && <OfferingsTab client={selected} />}
            {tab === "bookings" && <BookingsTab client={selected} />}
            {tab === "invoices" && <InvoicesTab client={selected} />}
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

// ── Resources ────────────────────────────────────────────────────────────────

function ResourcesTab({ client }: { client: BookAgencyClient }) {
  const [resources, setResources] = useState<BookAgencyResource[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)

  const refetch = () => {
    setLoading(true)
    bookingAgencyApi.getResources(client.id).then(setResources).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const toggleActive = async (r: BookAgencyResource) => {
    if (r.active) await bookingAgencyApi.deactivateResource(r.id)
    else await bookingAgencyApi.reactivateResource(r.id)
    refetch()
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ Add Resource</button>
      </div>
      {loading ? <Empty text="Loading…" /> : resources.length === 0 ? <Empty text="No resources added yet." /> : (
        <Table headers={["Name", "Role", "Working Hours", "Status", ""]}>
          {resources.map(r => (
            <tr key={r.id} style={rowStyle}>
              <td style={cellStyle}>{r.name}</td>
              <td style={cellStyle}>{r.roleDescription ?? "—"}</td>
              <td style={cellStyle}>{r.workingHoursStart?.slice(0, 5) ?? "—"}–{r.workingHoursEnd?.slice(0, 5) ?? "—"}</td>
              <td style={cellStyle}><StatusBadge status={r.active ? "ACTIVE" : "INACTIVE"} /></td>
              <td style={cellStyle}>
                <button onClick={() => toggleActive(r)} style={btnSecondary}>{r.active ? "Deactivate" : "Reactivate"}</button>
              </td>
            </tr>
          ))}
        </Table>
      )}
      {showNew && <NewResourceModal clientId={client.id} onClose={() => setShowNew(false)}
        onCreated={() => { setShowNew(false); refetch() }} />}
    </div>
  )
}

function NewResourceModal({ clientId, onClose, onCreated }: { clientId: string; onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState("")
  const [roleDescription, setRoleDescription] = useState("")
  const [start, setStart] = useState("09:00")
  const [end, setEnd] = useState("17:00")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!name) { setError("Name is required"); return }
    setSaving(true); setError("")
    try {
      await bookingAgencyApi.createResource({
        clientId, name, roleDescription,
        workingHoursStart: start + ":00", workingHoursEnd: end + ":00",
      } as any)
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to add resource")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="Add Resource" onClose={onClose}>
      <Field label="Name"><input value={name} onChange={e => setName(e.target.value)} style={inputStyle} /></Field>
      <Field label="Role (optional)"><input value={roleDescription} onChange={e => setRoleDescription(e.target.value)} style={inputStyle} placeholder="e.g. Stylist, Consulting Room 2" /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Start time"><input type="time" value={start} onChange={e => setStart(e.target.value)} style={inputStyle} /></Field>
        <Field label="End time"><input type="time" value={end} onChange={e => setEnd(e.target.value)} style={inputStyle} /></Field>
      </div>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Add Resource" />
    </Modal>
  )
}


// ── Invoices ────────────────────────────────────────────────────────────────

function InvoicesTab({ client }: { client: BookAgencyClient }) {
      const [invoices, setInvoices] = useState<BookAgencyInvoice[]>([])
      const [loading, setLoading] = useState(true)
      const [showGenerate, setShowGenerate] = useState(false)
 
      const refetch = () => {
        setLoading(true)
        bookingAgencyApi.getInvoices(client.id).then(res => setInvoices(res.content)).finally(() => setLoading(false))
      }
      useEffect(refetch, [client.id])
 
      const handleSend = async (id: string) => { await bookingAgencyApi.sendInvoice(id); refetch() }
 
      return (
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
            <p style={{ fontSize: 12, color: MUTED, margin: 0 }}>
              {client.monthlyRetainerAmount != null
                ? `Monthly retainer: R ${client.monthlyRetainerAmount}`
                : "No retainer amount set — add one when editing this client before generating an invoice."}
            </p>
            <button onClick={() => setShowGenerate(true)} style={btnPrimary} disabled={client.monthlyRetainerAmount == null}>
              + Generate Invoice
            </button>
          </div>
          {loading ? <Empty text="Loading…" /> : invoices.length === 0 ? <Empty text="No invoices yet." /> : (
            <Table headers={["Invoice #", "Period", "Total", "Balance", "Status", ""]}>
              {invoices.map(inv => (
                <tr key={inv.id} style={rowStyle}>
                  <td style={cellStyle}>{inv.invoiceNumber}</td>
                  <td style={cellStyle}>{fmtD(inv.periodStart)} – {fmtD(inv.periodEnd)}</td>
                  <td style={cellStyle}>R {inv.total.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}</td>
                  <td style={cellStyle}>R {inv.balance.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}</td>
                  <td style={cellStyle}><StatusBadge status={inv.status} /></td>
                  <td style={cellStyle}>{inv.status === "DRAFT" && <button onClick={() => handleSend(inv.id)} style={btnSecondary}>Send</button>}</td>
                </tr>
              ))}
            </Table>
          )}
          {showGenerate && <GenerateInvoiceModal client={client} onClose={() => setShowGenerate(false)} onGenerated={() => { setShowGenerate(false); refetch() }} />}
        </div>
      )
    }
 
    function GenerateInvoiceModal({ client, onClose, onGenerated }: { client: BookAgencyClient; onClose: () => void; onGenerated: () => void }) {
      const now = new Date()
      const periodStart = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10)
      const periodEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().slice(0, 10)
      const [saving, setSaving] = useState(false)
      const [error, setError] = useState("")
 
      const submit = async () => {
        setSaving(true); setError("")
        try {
          await bookingAgencyApi.generateInvoice(client.id, {
            periodStart, periodEnd, invoiceDate: periodEnd, dueDate: periodEnd, includeVat: true,
          })
          onGenerated()
        } catch (e: any) {
          setError(e.response?.data?.message ?? "Failed to generate invoice")
        } finally { setSaving(false) }
      }
 
      return (
        <Modal title="Generate Invoice" onClose={onClose}>
          <p style={{ fontSize: 13, color: MUTED, marginBottom: 14 }}>
            Generates the retainer invoice for {new Date().toLocaleDateString("en-ZA", { month: "long", year: "numeric" })}
            {" "}— R {client.monthlyRetainerAmount} + VAT.
          </p>
          {error && <ErrorBox text={error} />}
          <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Generate" />
        </Modal>
      )
    }

// ── Offerings ────────────────────────────────────────────────────────────────

function OfferingsTab({ client }: { client: BookAgencyClient }) {
  const [offerings, setOfferings] = useState<BookAgencyOffering[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)

  const refetch = () => {
    setLoading(true)
    bookingAgencyApi.getOfferings(client.id).then(setOfferings).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ Add Offering</button>
      </div>
      {loading ? <Empty text="Loading…" /> : offerings.length === 0 ? <Empty text="No offerings added yet." /> : (
        <Table headers={["Name", "Duration", "Buffer", "Price", "Status"]}>
          {offerings.map(o => (
            <tr key={o.id} style={rowStyle}>
              <td style={cellStyle}>{o.name}</td>
              <td style={cellStyle}>{o.durationMinutes} min</td>
              <td style={cellStyle}>{o.bufferMinutes} min</td>
              <td style={cellStyle}>{o.price != null ? `R ${o.price}` : "—"}</td>
              <td style={cellStyle}><StatusBadge status={o.active ? "ACTIVE" : "INACTIVE"} /></td>
            </tr>
          ))}
        </Table>
      )}
      {showNew && <NewOfferingModal clientId={client.id} onClose={() => setShowNew(false)}
        onCreated={() => { setShowNew(false); refetch() }} />}
    </div>
  )
}

function NewOfferingModal({ clientId, onClose, onCreated }: { clientId: string; onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState("")
  const [duration, setDuration] = useState("30")
  const [buffer, setBuffer] = useState("0")
  const [price, setPrice] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!name || !duration) { setError("Name and duration are required"); return }
    setSaving(true); setError("")
    try {
      await bookingAgencyApi.createOffering({
        clientId, name, durationMinutes: Number(duration), bufferMinutes: Number(buffer),
        price: price ? Number(price) : undefined,
      } as any)
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to add offering")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="Add Offering" onClose={onClose}>
      <Field label="Name"><input value={name} onChange={e => setName(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Duration (min)"><input type="number" value={duration} onChange={e => setDuration(e.target.value)} style={inputStyle} /></Field>
        <Field label="Buffer (min)"><input type="number" value={buffer} onChange={e => setBuffer(e.target.value)} style={inputStyle} /></Field>
      </div>
      <Field label="Price (optional)"><input type="number" value={price} onChange={e => setPrice(e.target.value)} style={inputStyle} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Add Offering" />
    </Modal>
  )
}

// ── Bookings ─────────────────────────────────────────────────────────────────

function BookingsTab({ client }: { client: BookAgencyClient }) {
  const [bookings, setBookings] = useState<BookAgencyBooking[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)

  const refetch = () => {
    setLoading(true)
    bookingAgencyApi.getBookings(client.id).then(res => setBookings(res.content)).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const handleAction = async (id: string, action: "cancel" | "complete" | "no-show") => {
    if (action === "cancel") await bookingAgencyApi.cancelBooking(id)
    else if (action === "complete") await bookingAgencyApi.completeBooking(id)
    else await bookingAgencyApi.markNoShow(id)
    refetch()
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ New Booking</button>
      </div>
      {loading ? <Empty text="Loading…" /> : bookings.length === 0 ? <Empty text="No bookings yet." /> : (
        <Table headers={["#", "Customer", "Resource", "Offering", "When", "Status", ""]}>
          {bookings.map(b => (
            <tr key={b.id} style={rowStyle}>
              <td style={cellStyle}>{b.bookingNumber}</td>
              <td style={cellStyle}>{b.customerName}</td>
              <td style={cellStyle}>{b.resourceName}</td>
              <td style={cellStyle}>{b.offeringName}</td>
              <td style={cellStyle}>{fmtDT(b.startDatetime)}</td>
              <td style={cellStyle}><StatusBadge status={b.status} /></td>
              <td style={cellStyle}>
                {b.status === "CONFIRMED" && (
                  <div style={{ display: "flex", gap: 6 }}>
                    <button onClick={() => handleAction(b.id, "complete")} style={btnSecondary}>Complete</button>
                    <button onClick={() => handleAction(b.id, "no-show")} style={btnSecondary}>No-show</button>
                    <button onClick={() => handleAction(b.id, "cancel")} style={{ ...btnSecondary, color: "#DC2626", borderColor: "#DC2626" }}>Cancel</button>
                  </div>
                )}
              </td>
            </tr>
          ))}
        </Table>
      )}
      {showNew && <NewBookingModal client={client} onClose={() => setShowNew(false)}
        onCreated={() => { setShowNew(false); refetch() }} />}
    </div>
  )
}

function NewBookingModal({ client, onClose, onCreated }: { client: BookAgencyClient; onClose: () => void; onCreated: () => void }) {
  const [resources, setResources] = useState<BookAgencyResource[]>([])
  const [offerings, setOfferings] = useState<BookAgencyOffering[]>([])
  const [resourceId, setResourceId] = useState("")
  const [offeringId, setOfferingId] = useState("")
  const [customerName, setCustomerName] = useState("")
  const [customerPhone, setCustomerPhone] = useState("")
  const [customerEmail, setCustomerEmail] = useState("")
  const [datetime, setDatetime] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    bookingAgencyApi.getResources(client.id).then(setResources)
    bookingAgencyApi.getOfferings(client.id).then(setOfferings)
  }, [client.id])

  const submit = async () => {
    if (!resourceId || !offeringId || !customerName || !datetime) {
      setError("Resource, offering, customer name, and time are all required")
      return
    }
    setSaving(true); setError("")
    try {
      await bookingAgencyApi.createBooking(client.id, {
        resourceId, offeringId, customerName, customerPhone, customerEmail,
        startDatetime: datetime,
      })
      onCreated()
    } catch (e: any) {
      // SLOT_CONFLICT surfaces here with a clear message from the backend's
      // own overlap check — shown as-is rather than a generic error.
      setError(e.response?.data?.message ?? "Failed to create booking")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="New Booking" onClose={onClose}>
      <Field label="Resource">
        <select value={resourceId} onChange={e => setResourceId(e.target.value)} style={inputStyle}>
          <option value="">Select…</option>
          {resources.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
        </select>
      </Field>
      <Field label="Offering">
        <select value={offeringId} onChange={e => setOfferingId(e.target.value)} style={inputStyle}>
          <option value="">Select…</option>
          {offerings.map(o => <option key={o.id} value={o.id}>{o.name} ({o.durationMinutes} min)</option>)}
        </select>
      </Field>
      <Field label="Customer name"><input value={customerName} onChange={e => setCustomerName(e.target.value)} style={inputStyle} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <Field label="Phone (optional)"><input value={customerPhone} onChange={e => setCustomerPhone(e.target.value)} style={inputStyle} /></Field>
        <Field label="Email (optional)"><input value={customerEmail} onChange={e => setCustomerEmail(e.target.value)} style={inputStyle} /></Field>
      </div>
      <Field label="Date & time"><input type="datetime-local" value={datetime} onChange={e => setDatetime(e.target.value)} style={inputStyle} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Booking" />
    </Modal>
  )
}

// ── Portal Access ────────────────────────────────────────────────────────────

function PortalAccessTab({ client }: { client: BookAgencyClient }) {
  const [grants, setGrants] = useState<PortalAccessGrant[]>([])
  const [loading, setLoading] = useState(true)
  const [email, setEmail] = useState("")
  const [inviting, setInviting] = useState(false)

  const refetch = () => {
    setLoading(true)
    bookingAgencyApi.getPortalAccessGrants(client.id).then(setGrants).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const handleInvite = async () => {
    if (!email) return
    setInviting(true)
    try {
      await bookingAgencyApi.invitePortalUser(client.id, email)
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
              <td style={cellStyle}>{fmtDT(g.invitedAt)}</td>
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
  const [businessType, setBusinessType] = useState("")
  const [contactName, setContactName] = useState("")
  const [contactEmail, setContactEmail] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    if (!tradingName) { setError("Trading name is required"); return }
    setSaving(true); setError("")
    try {
      await bookingAgencyApi.createClient({ tradingName, businessType, contactName, contactEmail } as any)
      onCreated()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to create client")
    } finally { setSaving(false) }
  }

  return (
    <Modal title="New Booking Client" onClose={onClose}>
      <Field label="Trading name"><input value={tradingName} onChange={e => setTradingName(e.target.value)} style={inputStyle} /></Field>
      <Field label="Business type (optional)"><input value={businessType} onChange={e => setBusinessType(e.target.value)} style={inputStyle} placeholder="e.g. hair salon, plumber" /></Field>
      <Field label="Contact name (optional)"><input value={contactName} onChange={e => setContactName(e.target.value)} style={inputStyle} /></Field>
      <Field label="Contact email (optional)"><input value={contactEmail} onChange={e => setContactEmail(e.target.value)} style={inputStyle} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Client" />
    </Modal>
  )
}

// ── Shared small components ──────────────────────────────────────────────────

function StatusBadge({ status }: { status: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    ACTIVE: { c: "#166534", bg: "#DCFCE7" }, CONFIRMED: { c: "#1D4ED8", bg: "#EFF6FF" },
    COMPLETED: { c: "#166534", bg: "#DCFCE7" }, INACTIVE: { c: "#64748B", bg: "#F1F5F9" },
    CANCELLED: { c: "#64748B", bg: "#F1F5F9" }, NO_SHOW: { c: "#DC2626", bg: "#FEF2F2" },
    PENDING: { c: "#D97706", bg: "#FFFBEB" }, REVOKED: { c: "#DC2626", bg: "#FEF2F2" },
  }
  const t = tones[status] ?? tones.INACTIVE
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
    <div style={{ marginBottom: 12, flex: 1 }}>
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
const btnSecondary: React.CSSProperties = { padding: "6px 12px", background: "#fff", color: NAVY, border: `1px solid ${NAVY}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
const rowStyle: React.CSSProperties = { borderTop: `1px solid ${BORDER}` }
const cellStyle: React.CSSProperties = { padding: "8px 12px", fontSize: 12.5, color: INK }
