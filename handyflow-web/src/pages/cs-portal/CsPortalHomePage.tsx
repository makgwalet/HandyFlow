// src/pages/cs-portal/CsPortalHomePage.tsx
// The client's read-only view of their compliance practice: quotes, agreements and invoices.
import { useEffect, useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { csPortalApi, type CsPortalClient, type CsPortalOverview } from "../../api/csPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, space, type } from "../accountant-portal/portal-theme"
import { Chip, Title, day, empty, errText, greetingName, money, panel, sub, td, th } from "../fm-portal/portalKit"
import { FREQUENCY, agreementStatus, attention, invoiceStatus, nextBill, quoteStatus } from "./csPortalRules"

type Section = "overview" | "quotes" | "agreements" | "invoices"
const SECTIONS: [Section, string][] = [["overview", "Overview"], ["quotes", "Quotes"], ["agreements", "Agreements"], ["invoices", "Invoices"]]
const todayIso = () => new Date().toISOString().slice(0, 10)

export function CsPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)
  const [clients, setClients] = useState<CsPortalClient[] | null>(null)
  const [clientId, setClientId] = useState("")
  const [data, setData] = useState<CsPortalOverview | null>(null)
  const [section, setSection] = useState<Section>("overview")
  const [error, setError] = useState("")

  useEffect(() => {
    csPortalApi.clients().then(list => { setClients(list); if (list[0]) setClientId(list[0].id) }).catch(e => { setClients([]); setError(errText(e, "Could not load your account")) })
  }, [])
  useEffect(() => {
    if (!clientId) return
    csPortalApi.overview(clientId).then(setData).catch(e => setError(errText(e, "Could not load your information")))
  }, [clientId])

  const items = useMemo(() => (data ? attention(data.quotes, data.agreements, data.invoices, todayIso()) : []), [data])
  const upcoming = data ? nextBill(data.agreements) : null
  const signOut = () => { logout(); navigate("/compliance-services/portal/login", { replace: true }) }
  const hello = greetingName(user?.fullName)

  return (
    <div style={{ minHeight: "100vh", background: color.canvas, fontFamily: type.family }}>
      <header style={{ background: color.surface, borderBottom: `1px solid ${color.border}`, padding: `${space(3)} ${space(5)}`, display: "flex", alignItems: "center", gap: space(4), flexWrap: "wrap" }}>
        <strong style={{ color: color.ink }}>Compliance client portal</strong>
        {clients && clients.length > 1 && (
          <select aria-label="Client" value={clientId} onChange={e => { setData(null); setError(""); setClientId(e.target.value) }} style={{ padding: "6px 10px", border: `1px solid ${color.border}`, borderRadius: 8 }}>
            {clients.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        )}
        <span style={{ marginLeft: "auto", fontSize: 13, color: color.muted }}>{user?.email}</span>
        <button onClick={signOut} style={{ border: `1px solid ${color.border}`, background: color.surface, borderRadius: 8, padding: "6px 12px", cursor: "pointer", fontWeight: 600 }}>Sign out</button>
      </header>
      <nav aria-label="Sections" style={{ display: "flex", gap: space(1), padding: `0 ${space(5)}`, background: color.surface, borderBottom: `1px solid ${color.border}` }}>
        {SECTIONS.map(([id, label]) => (
          <button key={id} onClick={() => setSection(id)} aria-current={section === id ? "page" : undefined}
            style={{ border: "none", background: "none", padding: "12px 14px", cursor: "pointer", fontWeight: 700, fontSize: 13.5, color: section === id ? color.navy : color.muted, borderBottom: `2px solid ${section === id ? color.navy : "transparent"}` }}>{label}</button>
        ))}
      </nav>
      <main style={{ maxWidth: 980, margin: "0 auto", padding: space(5) }}>
        {error && <div role="alert" style={{ marginBottom: space(4), padding: space(3), background: color.redBg, color: color.redText, borderRadius: 8, fontSize: 13 }}>{error}</div>}
        {clients && clients.length === 0 && !error && <div style={panel}><div style={empty}>Your login is not linked to a client yet. Please ask your compliance practice to invite you.</div></div>}
        {!data && clientId && !error && <div style={empty}>Loading…</div>}
        {data && section === "overview" && (
          <>
            <Title text={hello ? `Hello, ${hello}` : "Hello"} note={data.client.name} />
            <div style={{ display: "flex", gap: space(3), flexWrap: "wrap", marginBottom: space(5) }}>
              <Tile label="Owing" value={money(data.outstanding)} />
              <Tile label="Next bill" value={upcoming ? money(upcoming.amount) : "—"} note={upcoming ? day(upcoming.date) : "Nothing scheduled"} />
              <Tile label="Active agreements" value={String(data.agreements.filter(a => a.status === "ACTIVE").length)} />
            </div>
            <div style={panel}>
              <div style={{ padding: `${space(3)} ${space(4)}`, fontWeight: 800, color: color.ink }}>Needs your attention</div>
              {items.length === 0 ? <div style={empty}>Nothing needs your attention.</div> : items.map((i, n) => (
                <div key={n} style={{ ...td, display: "flex", gap: space(3), alignItems: "center" }}><Chip tone={i.tone}>{i.kind}</Chip><span>{i.text}</span></div>
              ))}
            </div>
          </>
        )}
        {data && section === "quotes" && (
          <>
            <Title text="Quotes" note="Quotes your practice has sent you. To accept or decline one, please contact them." />
            {data.quotes.length === 0 ? <div style={panel}><div style={empty}>No quotes yet.</div></div> : data.quotes.map(q => (
              <div key={q.id} style={{ ...panel, marginBottom: space(4) }}>
                <div style={{ ...td, display: "flex", gap: space(3), alignItems: "center", flexWrap: "wrap" }}>
                  <strong>{q.quoteNumber}</strong><span>{q.title}</span><Chip tone={quoteStatus(q.status)[1]}>{quoteStatus(q.status)[0]}</Chip>
                  <span style={{ ...sub, marginLeft: "auto" }}>{q.validUntil ? `Stands until ${day(q.validUntil)}` : ""}</span>
                </div>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr><th style={th}>Service</th><th style={{ ...th, textAlign: "right" }}>Fee</th><th style={{ ...th, textAlign: "right" }}>Disbursements</th><th style={{ ...th, textAlign: "right" }}>VAT</th><th style={{ ...th, textAlign: "right" }}>Total</th></tr></thead>
                  <tbody>{q.lines.map((l, i) => (
                    <tr key={i}><td style={td}>{l.serviceName ?? l.serviceCode}{l.description && <div style={sub}>{l.description}</div>}</td><td style={{ ...td, textAlign: "right" }}>{money(l.professional)}</td><td style={{ ...td, textAlign: "right" }}>{money(l.disbursements)}</td><td style={{ ...td, textAlign: "right" }}>{money(l.vat)}</td><td style={{ ...td, textAlign: "right" }}>{money(l.total)}</td></tr>
                  ))}</tbody>
                </table>
                <div style={{ ...td, textAlign: "right", fontWeight: 800 }}>Total {money(q.total)}</div>
                {(q.notes || q.terms) && <div style={{ ...td, ...sub }}>{q.notes}{q.notes && q.terms ? " · " : ""}{q.terms}</div>}
              </div>
            ))}
          </>
        )}
        {data && section === "agreements" && (
          <>
            <Title text="Agreements" note="Your ongoing service agreements." />
            {data.agreements.length === 0 ? <div style={panel}><div style={empty}>No agreements yet.</div></div> : data.agreements.map(a => (
              <div key={a.id} style={{ ...panel, marginBottom: space(4) }}>
                <div style={{ ...td, display: "flex", gap: space(3), alignItems: "center", flexWrap: "wrap" }}>
                  <strong>{a.agreementNumber}</strong><span>{a.title}</span><Chip tone={agreementStatus(a.status)[1]}>{agreementStatus(a.status)[0]}</Chip>
                </div>
                <div style={td}>
                  {money(a.billingAmount)} {FREQUENCY[a.billingFrequency] ?? a.billingFrequency} · {day(a.startDate)} to {a.endDate ? day(a.endDate) : "no end date"}
                  {a.autoRenew && <div style={sub}>Renews automatically unless you give {a.noticeDays} days' notice.</div>}
                </div>
                {a.scope.length > 0 && <div style={td}><div style={sub}>Services covered</div>{a.scope.map((s, i) => <div key={i}>{s.serviceName ?? s.serviceCode}</div>)}</div>}
                {a.nextBills.length > 0 && <div style={td}><div style={sub}>Coming bills</div>{a.nextBills.map(b => <div key={b.date}>{day(b.date)} · {money(b.amount)}</div>)}</div>}
              </div>
            ))}
          </>
        )}
        {data && section === "invoices" && (
          <>
            <Title text="Invoices" note="Invoices your practice has issued." />
            <div style={panel}>
              {data.invoices.length === 0 ? <div style={empty}>No invoices yet.</div> : (
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr><th style={th}>Invoice</th><th style={th}>Date</th><th style={th}>Status</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={{ ...th, textAlign: "right" }}>Paid</th><th style={{ ...th, textAlign: "right" }}>Owing</th></tr></thead>
                  <tbody>{data.invoices.map(i => (
                    <tr key={i.invoiceNumber}><td style={td}>{i.invoiceNumber}{i.description && <div style={sub}>{i.description}</div>}</td><td style={td}>{day(i.billingDate)}</td><td style={td}><Chip tone={invoiceStatus(i.status)[1]}>{invoiceStatus(i.status)[0]}</Chip></td><td style={{ ...td, textAlign: "right" }}>{money(i.total)}</td><td style={{ ...td, textAlign: "right" }}>{money(i.paid)}</td><td style={{ ...td, textAlign: "right", fontWeight: 700 }}>{money(i.outstanding)}</td></tr>
                  ))}</tbody>
                </table>
              )}
            </div>
          </>
        )}
      </main>
    </div>
  )
}

function Tile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div style={{ ...panel, padding: space(4), minWidth: 190, flex: "1 1 190px" }}>
      <div style={sub}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: 800, color: color.ink }}>{value}</div>
      {note && <div style={sub}>{note}</div>}
    </div>
  )
}
