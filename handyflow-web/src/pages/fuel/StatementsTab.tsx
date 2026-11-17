// What Fuel charged to a customer account in a period (fuel deliveries, overnight stays, charged driver tabs), with each charge's invoice status and where the invoices stand.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnPrimary, btnGhost, card, inputStyle, labelStyle } from "../warehousing/whseUi"
import { fmtR } from "./orderOps"
import type { StayAccount } from "./stayOps"
import { asStatement, defaultRange, validateRange, invoiceLabel, toCsv, KIND_LABEL, STATUS_LABEL, DIVISION_CHOICES, hasSettlement } from "./statementOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const todayStr = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export default function StatementsTab() {
  const accounts = useQuery<StayAccount[]>({ queryKey: ["fuel-stay-accounts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/customer-accounts")) })
  const list: StayAccount[] = Array.isArray(accounts.data) ? accounts.data.filter((a: any) => a?.customerName) : []
  const range = defaultRange(todayStr())
  const [accountId, setAccountId] = useState("")
  const [from, setFrom] = useState(range.from)
  const [to, setTo] = useState(range.to)
  const [division, setDivision] = useState("ALL")
  const [asked, setAsked] = useState<{ accountId: string; from: string; to: string; division: string } | null>(null)
  const [err, setErr] = useState("")

  const q = useQuery({
    queryKey: ["fuel-ts-statement", asked], enabled: !!asked, retry: false,
    queryFn: async () => asStatement(unwrap(await apiClient.get(`/api/v1/fuel/account-statements/${asked!.accountId}?from=${asked!.from}&to=${asked!.to}&division=${asked!.division}`))),
  })
  const s = q.data ?? null
  const name = list.find(a => a.id === asked?.accountId)?.customerName ?? ""

  function show() {
    if (!accountId) return setErr("Choose a customer account.")
    const m = validateRange(from, to); if (m) return setErr(m)
    setErr(""); setAsked({ accountId, from, to, division })
  }

  function download() {
    if (!s) return
    try {
      const url = URL.createObjectURL(new Blob([toCsv(s)], { type: "text/csv;charset=utf-8" }))
      const a = document.createElement("a"); a.href = url; a.download = `statement-${s.from}-to-${s.to}.csv`; a.click(); URL.revokeObjectURL(url)
    } catch { setErr("Could not prepare the download.") }
  }

  return (
    <div>
      <div style={{ ...card, padding: 16 }}>
        <p style={{ margin: "0 0 10px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
          What Fuel charged to a customer account: fuel deliveries (by the day delivered), overnight stays (by the day the truck left) and driver tabs (by the day they were charged). Line amounts are before VAT. For the invoices Fuel raised, the amounts paid and credited are read from Invoicing; payments are recorded there, not here.
        </p>
        <div style={{ display: "flex", gap: 10, alignItems: "flex-end", flexWrap: "wrap" }}>
          <div><label style={labelStyle} htmlFor="st-acct">Customer account</label>
            <select id="st-acct" value={accountId} onChange={e => setAccountId(e.target.value)} style={inputStyle}>
              <option value="">Choose an account…</option>
              {list.map(a => <option key={a.id} value={a.id}>{a.customerName}</option>)}
            </select></div>
          <div><label style={labelStyle} htmlFor="st-from">From</label><input id="st-from" type="date" value={from} onChange={e => setFrom(e.target.value)} style={inputStyle} /></div>
          <div><label style={labelStyle} htmlFor="st-to">To</label><input id="st-to" type="date" value={to} onChange={e => setTo(e.target.value)} style={inputStyle} /></div>
          <div><label style={labelStyle} htmlFor="st-div">Show</label>
            <select id="st-div" value={division} onChange={e => setDivision(e.target.value)} style={inputStyle}>
              {DIVISION_CHOICES.map(d => <option key={d.id} value={d.id}>{d.label}</option>)}
            </select></div>
          <button style={btnPrimary} onClick={show}>Show statement</button>
        </div>
        {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
        {q.isError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{(q.error as any)?.response?.data?.message ?? "Could not load the statement"}</p>}
      </div>

      {s && (
        <div style={{ ...card, overflowX: "auto", marginTop: 14 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 12px 0" }}>
            <strong style={{ fontSize: 14 }}>{name} · {s.from} to {s.to}</strong>
            <button style={btnGhost} onClick={download} disabled={s.lines.length === 0}>Download CSV</button>
          </div>
          {s.lines.length === 0 ? <p style={{ padding: 12, fontSize: 13 }}>Nothing was charged to this account in this period.</p> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Date</th><th style={th}>Type</th><th style={th}>Vehicle</th><th style={th}>Description</th><th style={th}>Amount</th><th style={th}>Invoice</th></tr></thead>
              <tbody>
                {s.lines.map(l => (
                  <tr key={`${l.kind}-${l.sourceId}`} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={td}>{l.date}</td><td style={td}>{KIND_LABEL[l.kind] ?? l.kind}</td><td style={td}>{l.vehicleRegistration}</td><td style={td}>{l.description}</td>
                    <td style={td}>{fmtR(l.amount)}</td><td style={td}>{invoiceLabel(l)}</td>
                  </tr>))}
              </tbody>
            </table>
          )}
          <p style={{ fontSize: 13, margin: 0, padding: 12 }}>
            <strong>Total {fmtR(s.total)}</strong> · Invoiced {fmtR(s.invoiced)} · Not invoiced yet {fmtR(s.notInvoiced)}
          </p>
          {s.settlementKnown === false && s.lines.some(l => l.invoiceNumber) && <p role="status" style={{ fontSize: 12, margin: 0, padding: "0 12px 12px", color: "var(--hf-text-muted)" }}>Invoicing could not be read just now, so paid and credited amounts are not shown.</p>}
          {hasSettlement(s) && (
            <div style={{ padding: "0 12px 12px" }}>
              <strong style={{ fontSize: 13 }}>Where the invoices stand (including VAT)</strong>
              <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 6 }}>
                <thead><tr><th style={th}>Invoice</th><th style={th}>Status</th><th style={th}>Total</th><th style={th}>Paid</th><th style={th}>Credited</th><th style={th}>Still owed</th></tr></thead>
                <tbody>
                  {(s.invoices ?? []).map(i => (
                    <tr key={i.invoiceId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                      <td style={td}>{i.invoiceNumber}</td><td style={td}>{STATUS_LABEL[i.status] ?? i.status}</td><td style={td}>{fmtR(i.total)}</td><td style={td}>{fmtR(i.paid)}</td><td style={td}>{fmtR(i.credited)}</td><td style={td}>{fmtR(i.outstanding)}</td>
                    </tr>))}
                </tbody>
              </table>
              <p style={{ fontSize: 13, margin: "6px 0 0" }}><strong>Still owed {fmtR(s.outstanding ?? 0)}</strong> · Paid {fmtR(s.paid ?? 0)} · Credited {fmtR(s.credited ?? 0)}</p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
