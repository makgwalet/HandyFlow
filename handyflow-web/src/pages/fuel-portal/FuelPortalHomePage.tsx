// Fuel customer portal home: the customer's own orders, deliveries, and a request-fuel form.
// The server decides what the user may see (active grant per account); this page only renders what it is given.
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { fuelPortalApiClient } from "../../api/fuelPortal.client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { PORTAL_FUEL_TYPES, fmtL, fmtRand, statusLabel, validateRequest, type PortalAccount, type PortalOrder, type PortalDelivery, type PortalSite } from "./portalOps"

const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const msg = (e: any, fallback: string) => e?.response?.data?.message ?? fallback
const statusColors = (s: string): [string, string] =>
  s === "QUOTE" ? ["var(--hf-warning-soft)", "var(--hf-warning-text)"]
  : s === "CANCELLED" ? ["var(--hf-danger-soft)", "var(--hf-danger-text)"]
  : s === "DELIVERED" ? ["var(--hf-success-soft)", "var(--hf-success-text)"]
  : ["var(--hf-info-soft)", "var(--hf-info-text)"]

export function FuelPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)
  const [picked, setPicked] = useState<string | null>(null)

  const accounts = useQuery<PortalAccount[]>({ queryKey: ["fuel-portal-accounts"], queryFn: async () => (await fuelPortalApiClient.get("/api/v1/fuel/portal/accounts")).data })
  const list = accounts.data ?? []
  const accountId = picked ?? list[0]?.accountId ?? null
  const account = list.find(a => a.accountId === accountId) ?? null

  const signOut = () => { logout(); navigate("/fuel/portal/login") }

  return (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ maxWidth: 980, margin: "0 auto", padding: "24px 16px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, gap: 12, flexWrap: "wrap" }}>
          <div>
            <h1 style={{ fontSize: 20, fontWeight: 800, margin: 0, color: "var(--hf-text)" }}>Fuel customer portal</h1>
            <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>{user?.fullName ?? ""}{user?.email ? ` · ${user.email}` : ""}</p>
          </div>
          <button style={btnGhost} onClick={signOut}>Sign out</button>
        </div>

        {accounts.isLoading && <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Loading…</p>}
        {accounts.isError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{msg(accounts.error, "Could not load your accounts")}</p>}
        {!accounts.isLoading && !accounts.isError && list.length === 0 && (
          <div style={{ ...card, padding: 24 }}><p style={{ margin: 0, fontSize: 13.5, color: "var(--hf-text-muted)" }}>Your access to a fuel account is no longer active. Please contact your supplier.</p></div>
        )}

        {list.length > 1 && (
          <div style={{ marginBottom: 14, maxWidth: 320 }}>
            <label htmlFor="fp-account" style={labelStyle}>Account</label>
            <select id="fp-account" style={inputStyle} value={accountId ?? ""} onChange={e => setPicked(e.target.value)}>
              {list.map(a => <option key={a.accountId} value={a.accountId}>{a.customerName}</option>)}
            </select>
          </div>
        )}

        {account && <AccountView key={account.accountId} account={account} />}
      </div>
    </div>
  )
}

function AccountView({ account }: { account: PortalAccount }) {
  const qc = useQueryClient()
  const base = `/api/v1/fuel/portal/accounts/${account.accountId}`
  const orders = useQuery<PortalOrder[]>({ queryKey: ["fuel-portal-orders", account.accountId], queryFn: async () => (await fuelPortalApiClient.get(`${base}/orders`)).data })
  const deliveries = useQuery<PortalDelivery[]>({ queryKey: ["fuel-portal-deliveries", account.accountId], queryFn: async () => (await fuelPortalApiClient.get(`${base}/deliveries`)).data })
  const sites = useQuery<PortalSite[]>({ queryKey: ["fuel-portal-sites", account.accountId], queryFn: async () => (await fuelPortalApiClient.get(`${base}/sites`)).data })
  const [showForm, setShowForm] = useState(false)
  const [done, setDone] = useState("")

  return (
    <>
      <div style={{ ...card, padding: 18, marginBottom: 16, display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <div>
          <p style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>{account.customerName}</p>
          <p style={{ margin: "2px 0 0", fontSize: 12.5, color: "var(--hf-text-muted)" }}>Payment terms: {account.paymentTermsDays} days</p>
        </div>
        <button style={btnPrimary} onClick={() => { setDone(""); setShowForm(s => !s) }}>{showForm ? "Close" : "Request fuel"}</button>
      </div>

      {done && <p role="status" style={{ fontSize: 13, color: "var(--hf-success-text)", marginBottom: 12 }}>{done}</p>}
      {showForm && (
        <RequestForm accountId={account.accountId} sites={sites.data ?? []}
          onDone={(m) => { setDone(m); setShowForm(false); qc.invalidateQueries({ queryKey: ["fuel-portal-orders", account.accountId] }) }} />
      )}

      <h2 style={{ fontSize: 14, fontWeight: 700, margin: "18px 0 8px", color: "var(--hf-text)" }}>Orders</h2>
      <div style={{ ...card, overflowX: "auto" }}>
        {orders.isError ? <p role="alert" style={{ padding: 16, color: "var(--hf-danger-text)", fontSize: 13 }}>{msg(orders.error, "Could not load orders")}</p>
          : (orders.data ?? []).length === 0 ? <p style={{ padding: 16, margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>{orders.isLoading ? "Loading…" : "No orders yet."}</p>
          : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Order</th><th style={th}>Site</th><th style={th}>Fuel</th><th style={th}>Ordered</th><th style={th}>Delivered</th><th style={th}>Value</th><th style={th}>Status</th></tr></thead>
              <tbody>
                {(orders.data ?? []).map(o => {
                  const [bg, fg] = statusColors(o.status)
                  return (
                    <tr key={o.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                      <td style={td}>{o.orderNumber}{o.customerReference ? <span style={{ color: "var(--hf-text-muted)" }}> · {o.customerReference}</span> : null}</td>
                      <td style={td}>{o.siteName ?? "–"}</td>
                      <td style={td}>{o.fuelType}</td>
                      <td style={td}>{fmtL(o.litresOrdered)}</td>
                      <td style={td}>{fmtL(o.litresDelivered)}</td>
                      <td style={td}>{fmtRand(o.value)}</td>
                      <td style={td}><Chip bg={bg} fg={fg}>{statusLabel(o.status)}</Chip></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
      </div>

      <h2 style={{ fontSize: 14, fontWeight: 700, margin: "18px 0 8px", color: "var(--hf-text)" }}>Deliveries</h2>
      <div style={{ ...card, overflowX: "auto" }}>
        {deliveries.isError ? <p role="alert" style={{ padding: 16, color: "var(--hf-danger-text)", fontSize: 13 }}>{msg(deliveries.error, "Could not load deliveries")}</p>
          : (deliveries.data ?? []).length === 0 ? <p style={{ padding: 16, margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>{deliveries.isLoading ? "Loading…" : "No deliveries yet."}</p>
          : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Date</th><th style={th}>Receipt</th><th style={th}>Site</th><th style={th}>Fuel</th><th style={th}>Litres</th><th style={th}>Total</th></tr></thead>
              <tbody>
                {(deliveries.data ?? []).map(d => (
                  <tr key={d.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={td}>{new Date(d.deliveredAt).toLocaleDateString("en-ZA")}</td>
                    <td style={td}>{d.receiptNumber ?? "–"}</td>
                    <td style={td}>{d.siteName ?? "–"}</td>
                    <td style={td}>{d.fuelType}</td>
                    <td style={td}>{fmtL(d.litres)}</td>
                    <td style={td}>{fmtRand(d.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </div>
    </>
  )
}

function RequestForm({ accountId, sites, onDone }: { accountId: string; sites: PortalSite[]; onDone: (message: string) => void }) {
  const [f, setF] = useState({ siteId: "", fuelType: "DIESEL", litres: "", requestedDate: "", customerReference: "", notes: "" })
  const [err, setErr] = useState("")
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const send = useMutation({
    mutationFn: async () => (await fuelPortalApiClient.post(`/api/v1/fuel/portal/accounts/${accountId}/order-requests`, {
      siteId: f.siteId || null, fuelType: f.fuelType, litres: Number(f.litres), requestedDate: f.requestedDate || null,
      customerReference: f.customerReference.trim() || null, notes: f.notes.trim() || null,
    })).data as PortalOrder,
    onSuccess: (o) => onDone(`Request sent${o?.orderNumber ? ` (${o.orderNumber})` : ""}. We will confirm it with you shortly.`),
    onError: (e: any) => setErr(msg(e, "Could not send the request")),
  })
  return (
    <div style={{ ...card, padding: 18, marginBottom: 16 }}>
      <p style={{ margin: "0 0 12px", fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>Request fuel</p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 }}>
        <div>
          <label htmlFor="rq-site" style={labelStyle}>Delivery site</label>
          <select id="rq-site" style={inputStyle} value={f.siteId} onChange={set("siteId")}>
            <option value="">Not specified</option>
            {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="rq-fuel" style={labelStyle}>Fuel type *</label>
          <select id="rq-fuel" style={inputStyle} value={f.fuelType} onChange={set("fuelType")}>{PORTAL_FUEL_TYPES.map(t => <option key={t}>{t}</option>)}</select>
        </div>
        <div>
          <label htmlFor="rq-litres" style={labelStyle}>Litres *</label>
          <input id="rq-litres" type="number" min="0" style={inputStyle} value={f.litres} onChange={set("litres")} />
        </div>
        <div>
          <label htmlFor="rq-date" style={labelStyle}>Needed by</label>
          <input id="rq-date" type="date" style={inputStyle} value={f.requestedDate} onChange={set("requestedDate")} />
        </div>
        <div>
          <label htmlFor="rq-ref" style={labelStyle}>Your reference</label>
          <input id="rq-ref" maxLength={60} style={inputStyle} value={f.customerReference} onChange={set("customerReference")} />
        </div>
      </div>
      <div style={{ marginTop: 12 }}>
        <label htmlFor="rq-notes" style={labelStyle}>Notes</label>
        <textarea id="rq-notes" maxLength={300} rows={2} style={inputStyle} value={f.notes} onChange={set("notes")} />
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, margin: "10px 0 0" }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 14 }}>
        <button style={btnPrimary} disabled={send.isPending} onClick={() => { const v = validateRequest(f.fuelType, f.litres, f.requestedDate); if (v) return setErr(v); setErr(""); send.mutate() }}>Send request</button>
      </div>
    </div>
  )
}
