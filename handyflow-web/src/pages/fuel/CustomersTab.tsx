// Stage 1B: fuel customer accounts (type, credit limit, terms, credit hold) and delivery sites.
import { Fragment, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { CUSTOMER_TYPES, typeLabel, fmtR, validateAccount, validateHold, validateSite, type CustomerAccount, type CustomerSite } from "./customerOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fallback: string) => e?.response?.data?.message ?? fallback

type Dialog = { kind: "account"; account?: CustomerAccount } | { kind: "hold"; account: CustomerAccount } | { kind: "site"; account: CustomerAccount; site?: CustomerSite } | null
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }

export default function CustomersTab() {
  const canManage = usePermissions(["FUEL_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [err, setErr] = useState("")

  const accounts = useQuery<CustomerAccount[]>({ queryKey: ["fuel-customer-accounts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/customer-accounts")) })
  const detail = useQuery<CustomerAccount>({ queryKey: ["fuel-customer-account", open], enabled: !!open, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/customer-accounts/${open}`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-customer-accounts"] }); qc.invalidateQueries({ queryKey: ["fuel-customer-account"] }) }

  const release = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/customer-accounts/${id}/credit-hold`, { hold: false, reason: null }),
    onSuccess: () => { setErr(""); refresh() },
    onError: (e: any) => setErr(msg(e, "Could not release the hold")),
  })
  const removeSite = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/fuel/customer-sites/${id}`),
    onSuccess: () => { setErr(""); refresh() },
    onError: (e: any) => setErr(msg(e, "Could not remove the site")),
  })

  const list = accounts.data ?? []
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 14, gap: 10 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1 }}>
          A fuel account sits on top of a CRM customer and adds the type, credit limit and terms. Fuel is not scheduled or dispatched for an account on credit hold.
        </p>
        {canManage && <button style={btnPrimary} onClick={() => setDialog({ kind: "account" })}>New customer account</button>}
      </div>
      {err && <Err text={err} />}
      {accounts.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
        : list.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No customer accounts yet.</p>
        : (
          <div style={{ ...card, overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Customer</th><th style={th}>Type</th><th style={th}>Credit limit</th><th style={th}>Terms</th><th style={th}>Sites</th><th style={th}>Status</th><th style={th}></th></tr></thead>
              <tbody>
                {list.map(a => (
                  <Fragment key={a.id}>
                    <tr style={{ borderTop: "1px solid var(--hf-border)" }}>
                      <td style={td}><button style={{ ...btnGhost, border: "none", padding: 0, fontWeight: 600 }} onClick={() => setOpen(open === a.id ? null : a.id)}>{a.customerName}</button></td>
                      <td style={td}>{typeLabel(a.customerType)}</td>
                      <td style={td}>{fmtR(a.creditLimit)}</td>
                      <td style={td}>{a.paymentTermsDays} days</td>
                      <td style={td}>{a.siteCount}</td>
                      <td style={td}>{a.creditHold ? <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">On credit hold</Chip> : <Chip bg="var(--hf-success-soft-strong)" fg="var(--hf-success-text-strong)">Active</Chip>}</td>
                      <td style={td}>
                        {canManage && <span style={{ display: "flex", gap: 6 }}>
                          <button style={btnGhost} onClick={() => setDialog({ kind: "account", account: a })}>Edit</button>
                          {a.creditHold
                            ? <button style={btnGhost} disabled={release.isPending} onClick={() => release.mutate(a.id)}>Release hold</button>
                            : <button style={btnGhost} onClick={() => setDialog({ kind: "hold", account: a })}>Credit hold</button>}
                        </span>}
                      </td>
                    </tr>
                    {open === a.id && (
                      <tr key={a.id + "-d"}><td colSpan={7} style={{ ...td, background: "var(--hf-surface-alt)" }}>
                        {a.creditHold && <p style={{ margin: "0 0 8px", color: "var(--hf-danger-text)", fontSize: 13 }}>Hold reason: {a.creditHoldReason}</p>}
                        <div style={{ display: "flex", alignItems: "center", marginBottom: 6 }}>
                          <strong style={{ fontSize: 13, flex: 1 }}>Delivery sites</strong>
                          {canManage && <button style={btnGhost} onClick={() => setDialog({ kind: "site", account: a })}>Add site</button>}
                        </div>
                        {(detail.data?.sites ?? []).length === 0 ? <span style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No sites yet.</span>
                          : (detail.data?.sites ?? []).map(s => (
                            <div key={s.id} style={{ display: "flex", gap: 10, padding: "6px 0", fontSize: 13, borderTop: "1px solid var(--hf-border)" }}>
                              <span style={{ flex: 1 }}><strong>{s.name}</strong>{s.address ? ` · ${s.address}` : ""}{s.deliveryInstructions ? ` · ${s.deliveryInstructions}` : ""}</span>
                              {canManage && <><button style={btnGhost} onClick={() => setDialog({ kind: "site", account: a, site: s })}>Edit</button>
                                <button style={btnGhost} onClick={() => removeSite.mutate(s.id)}>Remove</button></>}
                            </div>
                          ))}
                      </td></tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      {dialog?.kind === "account" && <AccountDialog account={dialog.account} existing={list} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "hold" && <HoldDialog account={dialog.account} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "site" && <SiteDialog account={dialog.account} site={dialog.site} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function AccountDialog({ account, existing, onDone, onClose }: { account?: CustomerAccount; existing: CustomerAccount[]; onDone: () => void; onClose: () => void }) {
  const [customerId, setCustomerId] = useState(account?.crmCustomerId ?? "")
  const [type, setType] = useState(account?.customerType ?? "")
  const [limit, setLimit] = useState(account ? String(account.creditLimit) : "0")
  const [terms, setTerms] = useState(account ? String(account.paymentTermsDays) : "30")
  const [licence, setLicence] = useState(account?.licenceNumber ?? "")
  const [notes, setNotes] = useState(account?.notes ?? "")
  const [err, setErr] = useState("")
  const crm = useQuery<any[]>({ queryKey: ["fuel-crm-customers"], enabled: !account, queryFn: async () => { const r = await apiClient.get("/api/v1/crm/customers", { params: { size: 200 } }); const d = unwrap(r); return Array.isArray(d) ? d : (d?.content ?? []) } })
  const taken = new Set(existing.map(a => a.crmCustomerId))
  const save = useMutation({
    mutationFn: () => {
      const body = { crmCustomerId: customerId || null, customerType: type, creditLimit: Number(limit), paymentTermsDays: Number(terms), licenceNumber: licence || null, notes: notes || null }
      return account ? apiClient.put(`/api/v1/fuel/customer-accounts/${account.id}`, body) : apiClient.post("/api/v1/fuel/customer-accounts", body)
    },
    onSuccess: () => { onDone(); onClose() },
    onError: (e: any) => setErr(msg(e, "Could not save the account")),
  })
  const submit = () => { const v = validateAccount(customerId, type, limit, terms, !account); if (v) return setErr(v); setErr(""); save.mutate() }
  return (
    <Modal title={account ? `Edit ${account.customerName}` : "New customer account"} onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {!account && <div><label style={labelStyle}>Customer *</label>
          <select aria-label="Customer" value={customerId} onChange={e => setCustomerId(e.target.value)} style={inputStyle}>
            <option value="">Choose…</option>
            {(crm.data ?? []).filter(c => !taken.has(c.id)).map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select></div>}
        <div><label style={labelStyle}>Customer type *</label>
          <select aria-label="Customer type" value={type} onChange={e => setType(e.target.value)} style={inputStyle}>
            <option value="">Choose…</option>{CUSTOMER_TYPES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div><label style={labelStyle}>Credit limit (R) *</label><input aria-label="Credit limit" type="number" value={limit} onChange={e => setLimit(e.target.value)} style={inputStyle} /></div>
          <div><label style={labelStyle}>Payment terms (days) *</label><input aria-label="Payment terms" type="number" value={terms} onChange={e => setTerms(e.target.value)} style={inputStyle} /></div>
        </div>
        <div><label style={labelStyle}>Licence / permit number</label><input value={licence} onChange={e => setLicence(e.target.value)} style={inputStyle} /></div>
        <div><label style={labelStyle}>Notes</label><input value={notes} onChange={e => setNotes(e.target.value)} style={inputStyle} /></div>
        {err && <Err text={err} />}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
          <button style={btnGhost} onClick={onClose}>Cancel</button>
          <button style={btnPrimary} disabled={save.isPending} onClick={submit}>Save account</button>
        </div>
      </div>
    </Modal>
  )
}

function HoldDialog({ account, onDone, onClose }: { account: CustomerAccount; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const hold = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/customer-accounts/${account.id}/credit-hold`, { hold: true, reason }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not place the hold")),
  })
  return (
    <Modal title={`Credit hold: ${account.customerName}`} onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 0 }}>No new deliveries can be scheduled or dispatched for this customer until the hold is released.</p>
      <label style={labelStyle}>Reason *</label>
      <input aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} />
      {err && <Err text={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={hold.isPending} onClick={() => { const v = validateHold(reason); if (v) return setErr(v); setErr(""); hold.mutate() }}>Place on hold</button>
      </div>
    </Modal>
  )
}

function SiteDialog({ account, site, onDone, onClose }: { account: CustomerAccount; site?: CustomerSite; onDone: () => void; onClose: () => void }) {
  const [f, setF] = useState({ name: site?.name ?? "", address: site?.address ?? "", contactName: site?.contactName ?? "", contactPhone: site?.contactPhone ?? "", deliveryInstructions: site?.deliveryInstructions ?? "", operatingHours: site?.operatingHours ?? "" })
  const [err, setErr] = useState("")
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const save = useMutation({
    mutationFn: () => site ? apiClient.put(`/api/v1/fuel/customer-sites/${site.id}`, f) : apiClient.post(`/api/v1/fuel/customer-accounts/${account.id}/sites`, f),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the site")),
  })
  return (
    <Modal title={site ? "Edit site" : `Add site for ${account.customerName}`} onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <div><label style={labelStyle}>Site name *</label><input aria-label="Site name" value={f.name} onChange={set("name")} style={inputStyle} /></div>
        <div><label style={labelStyle}>Address</label><input value={f.address} onChange={set("address")} style={inputStyle} /></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Contact name</label><input value={f.contactName} onChange={set("contactName")} style={inputStyle} /></div>
          <div><label style={labelStyle}>Contact phone</label><input value={f.contactPhone} onChange={set("contactPhone")} style={inputStyle} /></div>
        </div>
        <div><label style={labelStyle}>Delivery instructions</label><input value={f.deliveryInstructions} onChange={set("deliveryInstructions")} style={inputStyle} /></div>
        <div><label style={labelStyle}>Operating hours</label><input value={f.operatingHours} onChange={set("operatingHours")} style={inputStyle} /></div>
        {err && <Err text={err} />}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
          <button style={btnGhost} onClick={onClose}>Cancel</button>
          <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateSite(f.name); if (v) return setErr(v); setErr(""); save.mutate() }}>Save site</button>
        </div>
      </div>
    </Modal>
  )
}

function Err({ text }: { text: string }) {
  return <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p>
}
