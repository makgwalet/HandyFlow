// Truck stop driver tabs: services (showers, meals, washes...) on one bill, priced from Bookings, paid at the till or charged to a customer account.
import { Fragment, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtR } from "./orderOps"
import { chargeableAccounts, onlyFleetOptions, type StayAccount } from "./stayOps"
import { onlyTabs, sellable, validateQuantity, validateNewTab, tabActions, tabInvoiceText, canRaiseTabInvoice, paidText, vatBreakdown, vatText, TAB_STATUS_LABEL, TILL_METHODS, type Tab } from "./tabOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const BASE = "/api/v1/fuel/truck-stop"

export default function TabsTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_TRUCKSTOP_MANAGE"])
  const qc = useQueryClient()
  const tabs = useQuery<Tab[]>({ queryKey: ["fuel-ts-tabs"], queryFn: async () => onlyTabs(unwrap(await apiClient.get(`${BASE}/tabs`))) })
  const vat = useQuery({ queryKey: ["fuel-ts-vat"], retry: false, queryFn: async () => unwrap(await apiClient.get(`${BASE}/tabs/vat`)) })
  const offerings = useQuery({ queryKey: ["fuel-ts-tab-offerings"], queryFn: async () => sellable(unwrap(await apiClient.get(`${BASE}/offerings`))) })
  const accounts = useQuery<StayAccount[]>({ queryKey: ["fuel-stay-accounts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/customer-accounts")) })
  const accountList: StayAccount[] = Array.isArray(accounts.data) ? accounts.data.filter((a: any) => a?.customerName) : []
  const accountName = (id: string | null) => accountList.find(a => a.id === id)?.customerName
  const [open, setOpen] = useState<string | null>(null)
  const [dialog, setDialog] = useState(false)
  const [note, setNote] = useState("")
  const [err, setErr] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["fuel-ts-tabs"] })
  const list = tabs.data ?? []

  const act = useMutation({
    mutationFn: (a: { id: string; path: string; body?: unknown }) => apiClient.post(`${BASE}/tabs/${a.id}/${a.path}`, a.body ?? {}),
    onSuccess: (r: any) => { setErr(""); setNote(r?.data?.message ?? ""); refresh() },
    onError: (e: any) => setErr(msg(e, "That did not work")),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <p style={{ margin: 0, fontSize: 12.5, color: "var(--hf-text-muted)" }}>A driver's bill of truck stop services. Prices come from Bookings. Fuel is sold at the pumps, not here.</p>
        {canManage && <button style={btnPrimary} onClick={() => setDialog(true)}>New tab</button>}
      </div>
      {note && <p role="status" style={{ fontSize: 12.5 }}>{note}</p>}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{err}</p>}
      {list.length === 0 ? <div style={{ ...card, padding: 16, fontSize: 13 }}>No tabs yet.</div> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Vehicle</th><th style={th}>Driver</th><th style={th}>Opened</th><th style={th}>Total</th><th style={th}>Payment</th><th style={th}>Status</th></tr></thead>
            <tbody>
              {list.map(t => (
                <Fragment key={t.id}>
                  <tr style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={td}><button style={{ ...btnGhost, padding: "2px 6px" }} onClick={() => setOpen(open === t.id ? null : t.id)}>{t.vehicleRegistration}</button>{t.fleetVehicleId && <> <Chip bg="var(--hf-surface-2)" fg="var(--hf-text-muted)">Own truck</Chip></>}</td>
                    <td style={td}>{t.driverName ?? "–"}</td>
                    <td style={td}>{new Date(t.openedAt).toLocaleString("en-ZA", { dateStyle: "short", timeStyle: "short" })}</td>
                    <td style={td}>{fmtR(t.total)}</td>
                    <td style={td}>{paidText(t, accountName(t.accountId) ?? undefined)}{tabInvoiceText(t) && <div style={{ fontSize: 11, color: t.invoiceState === "FAILED" || t.invoiceState === "LINK_FAILED" ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>{tabInvoiceText(t)}</div>}</td>
                    <td style={td}><Chip bg="var(--hf-surface-2)" fg="var(--hf-text-muted)">{TAB_STATUS_LABEL[t.status]}</Chip></td>
                  </tr>
                  {open === t.id && (
                    <tr><td colSpan={6} style={{ ...td, background: "var(--hf-surface-alt)" }}>
                      <TabDetail tab={t} canManage={canManage} offerings={offerings.data ?? []} vat={vat.data} onChanged={refresh}
                        onAct={(path, body) => act.mutate({ id: t.id, path, body })} busy={act.isPending} />
                    </td></tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {dialog && <NewTabDialog accounts={chargeableAccounts(accountList)} onDone={refresh} onClose={() => setDialog(false)} />}
    </div>
  )
}

function TabDetail({ tab, canManage, offerings, vat, onChanged, onAct, busy }: { tab: Tab; vat?: unknown; canManage: boolean; offerings: { id: string; serviceName: string; price: number | null }[]; onChanged: () => void; onAct: (path: string, body?: unknown) => void; busy: boolean }) {
  const [offeringId, setOfferingId] = useState("")
  const [qty, setQty] = useState("1")
  const [method, setMethod] = useState("CASH")
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const actions = canManage ? tabActions(tab) : []
  const add = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/tabs/${tab.id}/lines`, { offeringId, quantity: Number(qty) }),
    onSuccess: () => { setErr(""); setQty("1"); onChanged() }, onError: (e: any) => setErr(msg(e, "Could not add the service")),
  })
  const remove = useMutation({
    mutationFn: (lineId: string) => apiClient.delete(`${BASE}/tabs/${tab.id}/lines/${lineId}`),
    onSuccess: () => { setErr(""); onChanged() }, onError: (e: any) => setErr(msg(e, "Could not remove the line")),
  })
  return (
    <div>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr><th style={th}>Service</th><th style={th}>Qty</th><th style={th}>Price</th><th style={th}>Amount</th><th style={th}></th></tr></thead>
        <tbody>
          {tab.lines.length === 0 && <tr><td style={td} colSpan={5}>No services on this tab yet.</td></tr>}
          {tab.lines.map(l => (
            <tr key={l.id}>
              <td style={td}>{l.description}</td><td style={td}>{l.quantity}</td><td style={td}>{fmtR(l.unitPrice)}</td><td style={td}>{fmtR(l.amount)}</td>
              <td style={td}>{actions.includes("addLines") && <button style={btnGhost} aria-label={`Remove ${l.description}`} onClick={() => remove.mutate(l.id)}>Remove</button>}</td>
            </tr>))}
        </tbody>
      </table>
      {actions.includes("addLines") && (
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 10, flexWrap: "wrap" }}>
          <div><label style={labelStyle} htmlFor={`tl-s-${tab.id}`}>Service</label>
            <select id={`tl-s-${tab.id}`} value={offeringId} onChange={e => setOfferingId(e.target.value)} style={inputStyle}>
              <option value="">Choose a service…</option>
              {offerings.map(o => <option key={o.id} value={o.id}>{o.serviceName} · {fmtR(o.price ?? 0)}</option>)}
            </select></div>
          <div><label style={labelStyle} htmlFor={`tl-q-${tab.id}`}>Quantity</label><input id={`tl-q-${tab.id}`} inputMode="decimal" value={qty} onChange={e => setQty(e.target.value)} style={{ ...inputStyle, width: 90 }} /></div>
          <button style={btnPrimary} disabled={add.isPending} onClick={() => {
            if (!offeringId) return setErr("Choose a service.")
            const m = validateQuantity(qty); if (m) return setErr(m)
            setErr(""); add.mutate()
          }}>Add</button>
        </div>
      )}
      {offerings.length === 0 && actions.includes("addLines") && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>No services can be sold yet. Add services under Truck stop, and give them a price in Bookings.</p>}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      <p style={{ fontSize: 13, fontWeight: 600, margin: "10px 0 0" }}>Total {fmtR(tab.total)} ex VAT</p>
      {vatText(vatBreakdown(tab.total, vat), fmtR) && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>{vatText(vatBreakdown(tab.total, vat), fmtR)}</p>}
      <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 8, flexWrap: "wrap" }}>
        {actions.includes("settle") && (<>
          <div><label style={labelStyle} htmlFor={`tl-m-${tab.id}`}>Paid by</label>
            <select id={`tl-m-${tab.id}`} value={method} onChange={e => setMethod(e.target.value)} style={inputStyle}>{TILL_METHODS.map(m => <option key={m.id} value={m.id}>{m.label}</option>)}</select></div>
          <button style={btnPrimary} disabled={busy} onClick={() => onAct("settle", { paymentMethod: method })}>Settle at the till</button>
        </>)}
        {actions.includes("charge") && <button style={btnPrimary} disabled={busy} onClick={() => onAct("charge")}>Charge to account</button>}
        {actions.includes("void") && (<>
          <div><label style={labelStyle} htmlFor={`tl-v-${tab.id}`}>Void reason</label><input id={`tl-v-${tab.id}`} value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} /></div>
          <button style={btnGhost} disabled={busy} onClick={() => { if (reason.trim().length < 5) return setErr("Give a reason of at least 5 characters."); onAct("void", { reason: reason.trim() }) }}>Void tab</button>
        </>)}
        {canManage && canRaiseTabInvoice(tab) && <button style={btnGhost} disabled={busy} onClick={() => onAct("invoice")}>Raise invoice</button>}
      </div>
    </div>
  )
}

function NewTabDialog({ accounts, onDone, onClose }: { accounts: StayAccount[]; onDone: () => void; onClose: () => void }) {
  const [reg, setReg] = useState(""); const [driver, setDriver] = useState(""); const [accountId, setAccountId] = useState(""); const [err, setErr] = useState("")
  const [fleetId, setFleetId] = useState("")
  const fleet = useQuery({ queryKey: ["fuel-ts-fleet-vehicles"], retry: false, queryFn: async () => onlyFleetOptions(unwrap(await apiClient.get(`${BASE}/fleet-vehicles`))) })
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/tabs`, { vehicleRegistration: reg.trim(), driverName: driver.trim() || null, accountId: accountId || null, fleetVehicleId: fleetId || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not open the tab")),
  })
  return (
    <Modal title="New driver tab" onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {(fleet.data ?? []).length > 0 && (
          <div><label style={labelStyle} htmlFor="nt-fleet">Own truck (Fleet)</label>
            <select id="nt-fleet" value={fleetId} onChange={e => { setFleetId(e.target.value); const v = (fleet.data ?? []).find(x => x.id === e.target.value); if (v) setReg(v.registration) }} style={inputStyle}>
              <option value="">Not one of our trucks</option>
              {(fleet.data ?? []).map(v => <option key={v.id} value={v.id}>{v.label}</option>)}
            </select></div>
        )}
        <div><label style={labelStyle} htmlFor="nt-reg">Vehicle registration *</label><input id="nt-reg" value={reg} maxLength={20} onChange={e => setReg(e.target.value)} style={inputStyle} /></div>
        <div><label style={labelStyle} htmlFor="nt-driver">Driver</label><input id="nt-driver" value={driver} maxLength={120} onChange={e => setDriver(e.target.value)} style={inputStyle} /></div>
        <div><label style={labelStyle} htmlFor="nt-acct">Charge to</label>
          <select id="nt-acct" value={accountId} onChange={e => setAccountId(e.target.value)} style={inputStyle}>
            <option value="">Pays at the till (no invoice)</option>
            {accounts.map(a => <option key={a.id} value={a.id}>{a.customerName}</option>)}
          </select>
          <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 3 }}>A customer account gets a draft invoice when the tab is charged. Accounts on credit hold are not listed.</div></div>
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const m = validateNewTab(reg); if (m) return setErr(m); setErr(""); save.mutate() }}>Open tab</button>
      </div>
    </Modal>
  )
}
