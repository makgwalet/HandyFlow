// B06: customer enquiries. Record what a customer asked for, turn it into a quotation, or close it as lost or cancelled with a reason.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import type { CustomerAccount } from "./customerOps"
import { fmtL } from "./orderOps"
import { SOURCES, STATUS_LABEL, isNew, who, validateEnquiry, validateClose, canQuote, type Enquiry } from "./enquiryOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const BASE = "/api/v1/fuel/enquiries"
const FUELS = ["DIESEL", "DIESEL_50PPM", "DIESEL_500PPM", "PETROL_95", "PETROL_93", "PARAFFIN"]

type Dialog = { kind: "new" } | { kind: "close"; enquiry: Enquiry; how: "lost" | "cancel" } | null
function Err({ text }: { text: string }) { return <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, margin: "10px 0 0" }}>{text}</p> }

export default function EnquiriesTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_ORDERS_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [err, setErr] = useState("")
  const list = useQuery<Enquiry[]>({ queryKey: ["fuel-enquiries"], queryFn: async () => unwrap(await apiClient.get(BASE)) })
  const refresh = () => { for (const k of ["fuel-enquiries", "fuel-orders"]) qc.invalidateQueries({ queryKey: [k] }) }
  const quote = useMutation({
    mutationFn: (id: string) => apiClient.post(`${BASE}/${id}/quote`, {}),
    onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not raise the quotation")),
  })
  const rows = list.data ?? []
  const open = rows.filter(isNew).length

  return (
    <div>
      <p style={{ margin: "0 0 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Record what a customer asks for. Quoting raises an ordinary quotation (price from the contract or price list unless you set one on the quotation), which you then revise and confirm under Orders.
      </p>
      {err && <Err text={err} />}
      <div style={{ display: "flex", alignItems: "center", margin: "6px 0 8px" }}>
        <h3 style={{ margin: 0, fontSize: 14, flex: 1 }}>Enquiries{open > 0 ? ` (${open} open)` : ""}</h3>
        {canManage && <button style={btnPrimary} onClick={() => setDialog({ kind: "new" })}>New enquiry</button>}
      </div>
      {rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No enquiries yet.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Number</th><th style={th}>Customer</th><th style={th}>Fuel</th><th style={th}>Litres</th><th style={th}>Needed</th><th style={th}>Source</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {rows.map(e => (
                <tr key={e.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}>{e.enquiryNumber}</td>
                  <td style={td}>{who(e)}{!e.accountId && <div style={{ fontSize: 11, color: "var(--hf-warning-text)" }}>No customer account yet</div>}</td>
                  <td style={td}>{e.fuelType}</td>
                  <td style={td}>{fmtL(e.litres)}</td>
                  <td style={td}>{e.requestedDate ?? "–"}</td>
                  <td style={td}>{SOURCES[e.source] ?? e.source}</td>
                  <td style={td}>
                    <Chip bg={e.status === "NEW" ? "var(--hf-info-soft)" : "var(--hf-surface-alt)"} fg={e.status === "NEW" ? "var(--hf-info-text)" : "var(--hf-text-muted)"}>{STATUS_LABEL[e.status]}</Chip>
                    {e.quoteOrderNumber && <div style={{ fontSize: 11 }}>{e.quoteOrderNumber}</div>}
                    {e.decisionReason && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{e.decisionReason}</div>}
                  </td>
                  <td style={td}>{canManage && isNew(e) && (
                    <span style={{ display: "flex", gap: 6 }}>
                      <button style={btnPrimary} disabled={!canQuote(e) || quote.isPending} title={canQuote(e) ? "" : "Link a customer account first"} onClick={() => quote.mutate(e.id)}>Quote</button>
                      <button style={btnGhost} onClick={() => setDialog({ kind: "close", enquiry: e, how: "lost" })}>Lost</button>
                      <button style={btnGhost} onClick={() => setDialog({ kind: "close", enquiry: e, how: "cancel" })}>Cancel</button>
                    </span>)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {dialog?.kind === "new" && <NewDialog onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "close" && <CloseDialog enquiry={dialog.enquiry} how={dialog.how} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function NewDialog({ onDone, onClose }: { onDone: () => void; onClose: () => void }) {
  const [accountId, setAccountId] = useState(""); const [name, setName] = useState(""); const [phone, setPhone] = useState(""); const [fuel, setFuel] = useState("DIESEL")
  const [litres, setLitres] = useState(""); const [date, setDate] = useState(""); const [source, setSource] = useState("PHONE"); const [notes, setNotes] = useState(""); const [err, setErr] = useState("")
  const accounts = useQuery<CustomerAccount[]>({ queryKey: ["fuel-customer-accounts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/customer-accounts")) })
  const save = useMutation({
    mutationFn: () => apiClient.post(BASE, { accountId: accountId || null, contactName: name || null, contactPhone: phone || null, fuelType: fuel, litres: Number(litres), requestedDate: date || null, source, notes: notes || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not record the enquiry")),
  })
  return (
    <Modal title="New enquiry" onClose={onClose}>
      <label style={labelStyle}>Customer account</label>
      <select aria-label="Customer account" value={accountId} onChange={e => setAccountId(e.target.value)} style={inputStyle}>
        <option value="">Not an account yet</option>{(accounts.data ?? []).map(a => <option key={a.id} value={a.id}>{a.customerName}</option>)}
      </select>
      {!accountId && <>
        <label style={{ ...labelStyle, marginTop: 10 }}>Who is asking *</label><input aria-label="Who is asking" value={name} onChange={e => setName(e.target.value)} style={inputStyle} />
        <label style={{ ...labelStyle, marginTop: 10 }}>Phone</label><input aria-label="Phone" value={phone} onChange={e => setPhone(e.target.value)} style={inputStyle} /></>}
      <label style={{ ...labelStyle, marginTop: 10 }}>Fuel *</label>
      <select aria-label="Fuel" value={fuel} onChange={e => setFuel(e.target.value)} style={inputStyle}>{FUELS.map(f => <option key={f} value={f}>{f}</option>)}</select>
      <label style={{ ...labelStyle, marginTop: 10 }}>Litres *</label><input aria-label="Litres" inputMode="decimal" value={litres} onChange={e => setLitres(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Needed by</label><input aria-label="Needed by" type="date" value={date} onChange={e => setDate(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Came in by</label>
      <select aria-label="Source" value={source} onChange={e => setSource(e.target.value)} style={inputStyle}>{Object.entries(SOURCES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
      <label style={{ ...labelStyle, marginTop: 10 }}>Notes</label><input aria-label="Notes" value={notes} onChange={e => setNotes(e.target.value)} style={inputStyle} />
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateEnquiry(accountId, name, fuel, litres); if (v) return setErr(v); setErr(""); save.mutate() }}>Save enquiry</button>
      </div>
    </Modal>
  )
}

function CloseDialog({ enquiry, how, onDone, onClose }: { enquiry: Enquiry; how: "lost" | "cancel"; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/${enquiry.id}/${how}`, { reason: reason.trim() }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not close the enquiry")),
  })
  return (
    <Modal title={`${how === "lost" ? "Mark as lost" : "Cancel enquiry"} ${enquiry.enquiryNumber}`} onClose={onClose}>
      <label style={labelStyle}>Reason *</label>
      <input aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} />
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Back</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateClose(reason); if (v) return setErr(v); setErr(""); save.mutate() }}>{how === "lost" ? "Mark as lost" : "Cancel enquiry"}</button>
      </div>
    </Modal>
  )
}
