// src/pages/agriculture/AgMarginOverview.tsx
//
// The Dashboard's gross-margin card (ADR-001, W6b): every active farm side by side. Margins show revenue, so the card exists only for people who hold
// AGRICULTURE_FINANCE and INVOICE_READ; for everyone else it is simply absent (the Dashboard is theirs to read, so there is no "access denied" noise).
// The figures are each farm's own Profitability totals, summed by the server, so this can never disagree with a farm's Profitability screen.
import { AlertTriangle } from "lucide-react"
import { Link } from "react-router-dom"
import { usePermission } from "../../hooks/usePermission"
import { Margin } from "./AgProfitabilityTab"
import { useProfitabilityOverview, type FarmMargin } from "./agProfitability.api"
import { Th } from "./agCropsUi"
import { AG_ACCENT_TEXT, card, fmtMoney } from "./constants"

const hasActivity = (f: FarmMargin) => f.revenue !== 0 || f.directCost !== 0

function Figure({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div role="group" aria-label={label}>
      <div style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)" }}>{label}</div>
      <div style={{ fontSize: 18, fontWeight: 700, color: "var(--hf-text)", marginTop: 2 }}>{children}</div>
    </div>
  )
}

export default function AgMarginOverview() {
  const canFinance = usePermission("AGRICULTURE_FINANCE")
  const canInvoices = usePermission("INVOICE_READ")           // both hooks always run, in the same order
  const allowed = canFinance && canInvoices
  const { data, isLoading, isError, refetch } = useProfitabilityOverview(allowed)
  if (!allowed) return null
  if (isLoading) return <section style={{ ...card, marginBottom: 16 }} aria-label="Gross margin, all farms"><p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>Loading gross margin…</p></section>
  if (isError || !data) {
    return (
      <section style={{ ...card, marginBottom: 16 }} aria-label="Gross margin, all farms">
        <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: 0 }}>We couldn't load gross margin. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>
      </section>
    )
  }
  if (data.farms.length === 0) return null
  const t = data.totals

  return (
    <section style={{ ...card, marginBottom: 16 }} aria-label="Gross margin, all farms">
      <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", marginBottom: 10, gap: 8, flexWrap: "wrap" }}>
        <h3 style={{ fontSize: 13, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Gross margin <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>all farms</span></h3>
        <Link to="/agriculture/profitability" style={{ fontSize: 12.5, fontWeight: 600, color: AG_ACCENT_TEXT }}>Open Profitability</Link>
      </div>
      <div style={{ display: "flex", gap: 28, flexWrap: "wrap", marginBottom: 6 }}>
        <Figure label="Revenue">{fmtMoney(t.revenue)}</Figure>
        <Figure label="Direct costs">{fmtMoney(t.directCost)}</Figure>
        <Figure label="Gross margin"><Margin value={t.grossMargin} percent={t.marginPercent} big /></Figure>
      </div>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>
        {data.complete.units} finished, {data.inProgress.units} still running{data.breedingStock.units > 0 ? `, ${data.breedingStock.units} breeding stock` : ""}. Revenue ex-VAT; not net profit.
      </p>
      <div style={{ overflowX: "auto" }}>
        <table aria-label="Gross margin by farm" style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
          <thead><tr style={{ textAlign: "left" }}>{["Farm", "Revenue", "Direct costs", "Gross margin", ""].map(h => <Th key={h}>{h}</Th>)}</tr></thead>
          <tbody>
            {data.farms.map(f => (
              <tr key={f.farmId} aria-label={`Farm ${f.farmName}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={{ padding: "8px 10px", fontWeight: 600 }}>{f.farmName}</td>
                {hasActivity(f) ? (
                  <>
                    <td style={{ padding: "8px 10px" }}>{fmtMoney(f.revenue)}</td>
                    <td style={{ padding: "8px 10px" }}>{fmtMoney(f.directCost)}</td>
                    <td style={{ padding: "8px 10px" }}><Margin value={f.grossMargin} percent={f.marginPercent} /></td>
                  </>
                ) : <td colSpan={3} style={{ padding: "8px 10px", color: "var(--hf-text-faint)" }}>No activity yet</td>}
                <td style={{ padding: "8px 10px" }}>
                  {f.cautions > 0 && <span role="img" aria-label={`${f.cautions} thing${f.cautions === 1 ? "" : "s"} to read with care`} title="Read with care" style={{ color: "var(--hf-warning-text)" }}><AlertTriangle size={13} style={{ verticalAlign: "-2px" }} /> {f.cautions}</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {data.notes.length > 0 && (
        <details style={{ marginTop: 10, fontSize: 12, color: "var(--hf-text-secondary)" }}>
          <summary style={{ cursor: "pointer", fontWeight: 600 }}>About these figures ({data.notes.length})</summary>
          <ul aria-label="Notes about these figures" style={{ margin: "6px 0 0", paddingLeft: 18, display: "grid", gap: 4 }}>{data.notes.map(n => <li key={n}>{n}</li>)}</ul>
        </details>
      )}
    </section>
  )
}
