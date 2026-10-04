// src/pages/agriculture/AgProfitabilityTab.tsx
//
// The farm's gross margin (ADR-001, W5): revenue (ex-VAT, net of credit notes) minus DIRECT production costs, per crop cycle, group, animal and
// enterprise. Gross margin only; overheads, finance costs, depreciation and tax belong to Accounting. Units whose life is over show a FINAL margin;
// units still running show costs and sales to date, and unsold stock is not valued. Needs AGRICULTURE_FINANCE and INVOICE_READ (it shows revenue).
import { Fragment, useState } from "react"
import { AlertTriangle, Lock } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { useProfitability, type Subtotal, type UnitProfit } from "./agProfitability.api"
import { TYPE_LABEL, costParts, filterUnits, percentText, stateLabel, toneOf, typesPresent, type StateFilter, type TypeFilter } from "./agProfitability.logic"
import { Empty, Th } from "./agCropsUi"
import { btnGhost, card, fmtMoney, inp } from "./constants"

const TONE_COLOR = { gain: "var(--hf-success-text-strong)", loss: "var(--hf-danger-text)", even: "var(--hf-text-secondary)" } as const

/** The sign is written out as well as coloured, so a loss never depends on colour alone. */
function Margin({ value, percent, big }: { value: number; percent: number | null; big?: boolean }) {
  const tone = toneOf(value)
  return (
    <span style={{ color: TONE_COLOR[tone], fontWeight: 700, fontSize: big ? 20 : 12.5 }}>
      {tone === "loss" ? "Loss " : tone === "gain" ? "Gain " : ""}{fmtMoney(Math.abs(value))}
      <span style={{ fontWeight: 400, fontSize: big ? 12.5 : 11, marginLeft: 6 }}>{percentText(percent)}</span>
    </span>
  )
}

function Stat({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <div role="group" aria-label={label} style={{ ...card, flex: "1 1 200px" }}>
      <div style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)" }}>{label}</div>
      <div style={{ fontSize: 20, fontWeight: 700, color: "var(--hf-text)", marginTop: 4 }}>{children}</div>
      {hint && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", marginTop: 4 }}>{hint}</div>}
    </div>
  )
}

function SubtotalCard({ title, hint, s }: { title: string; hint: string; s: Subtotal }) {
  return (
    <div style={{ ...card, flex: "1 1 280px" }} aria-label={title}>
      <div style={{ fontSize: 12.5, fontWeight: 700, color: "var(--hf-text)" }}>{title} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>({s.units} unit{s.units === 1 ? "" : "s"})</span></div>
      {s.units === 0 ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 6 }}>None yet.</div> : (
        <div style={{ marginTop: 6, fontSize: 12.5, display: "grid", gap: 2 }}>
          <div>Revenue {fmtMoney(s.revenue)} · Direct costs {fmtMoney(s.directCost)}</div>
          <Margin value={s.grossMargin} percent={s.marginPercent} />
        </div>
      )}
      <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", marginTop: 6 }}>{hint}</div>
    </div>
  )
}

function Row({ u, open, onToggle }: { u: UnitProfit; open: boolean; onToggle: () => void }) {
  const parts = costParts(u)
  return (
    <Fragment>
      <tr aria-label={`${TYPE_LABEL[u.targetType]} ${u.label}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
        <td style={{ padding: "9px 10px" }}>
          <button type="button" aria-expanded={open} aria-label={`${open ? "Hide" : "Show"} details for ${u.label}`} onClick={onToggle} style={{ ...btnGhost, padding: "2px 8px", marginRight: 8 }}>{open ? "−" : "+"}</button>
          <strong>{u.label}</strong>
          <span style={{ color: "var(--hf-text-faint)", marginLeft: 8, fontSize: 11.5 }}>{TYPE_LABEL[u.targetType]}</span>
          {u.caveats.length > 0 && <span role="img" aria-label="Read with care" title="Read with care" style={{ marginLeft: 8, color: "var(--hf-warning-text)" }}><AlertTriangle size={13} style={{ verticalAlign: "-2px" }} /></span>}
        </td>
        <td style={{ padding: "9px 10px" }}>{stateLabel(u.state)}</td>
        <td style={{ padding: "9px 10px" }}>{fmtMoney(u.revenue)}</td>
        <td style={{ padding: "9px 10px" }}>{fmtMoney(u.directCost)}</td>
        <td style={{ padding: "9px 10px" }}><Margin value={u.grossMargin} percent={u.marginPercent} /></td>
      </tr>
      {open && (
        <tr aria-label={`Details for ${u.label}`}>
          <td colSpan={5} style={{ padding: "4px 10px 12px 44px", background: "var(--hf-surface-muted)", fontSize: 12.5 }}>
            {parts.length === 0 ? <div>No costs recorded.</div> : parts.map(p => <div key={p.label} style={{ display: "flex", justifyContent: "space-between", maxWidth: 520 }}><span>{p.label}</span><strong>{fmtMoney(p.amount)}</strong></div>)}
            {u.state === "IN_PROGRESS" && <div style={{ marginTop: 6, color: "var(--hf-text-muted)" }}>Still running: this is costs and sales to date. Unsold stock isn't valued, so the margin isn't final.</div>}
            {u.caveats.map(c => <div key={c} role="note" style={{ marginTop: 6, color: "var(--hf-warning-text)" }}>{c}</div>)}
          </td>
        </tr>
      )}
    </Fragment>
  )
}

export default function AgProfitabilityTab({ farmId }: { farmId: string }) {
  const canFinance = usePermission("AGRICULTURE_FINANCE")
  const canInvoices = usePermission("INVOICE_READ")
  const allowed = canFinance && canInvoices
  const { data, isLoading, isError, refetch } = useProfitability(farmId, allowed)
  const [type, setType] = useState<TypeFilter>("ALL")
  const [state, setState] = useState<StateFilter>("ALL")
  const [openKey, setOpenKey] = useState<string | null>(null)

  if (!allowed) {
    return (
      <div role="note" style={{ textAlign: "center", padding: "36px 12px" }}>
        <Lock size={26} style={{ color: "var(--hf-text-disabled)", marginBottom: 8 }} />
        <p style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>You don't have access to profitability</p>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Margins need the Agriculture finance permission and access to invoices, because they show revenue. Ask an administrator.</p>
      </div>
    )
  }
  if (isLoading) return <Empty>Loading profitability…</Empty>
  if (isError || !data) return <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load profitability. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>

  const t = data.totals
  const shown = filterUnits(data.units, type, state)
  const types = typesPresent(data.units)
  const key = (u: UnitProfit) => `${u.targetType}:${u.targetId ?? "none"}`

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
        <Stat label="Revenue" hint="Ex-VAT, net of credit notes">{fmtMoney(t.revenue)}</Stat>
        <Stat label="Direct costs" hint="Recorded costs, labour, equipment, fuel and other direct costs">{fmtMoney(t.directCost)}</Stat>
        <Stat label="Gross margin" hint="Revenue minus direct costs. Not net profit."><Margin value={t.grossMargin} percent={t.marginPercent} big /></Stat>
      </div>

      <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
        <SubtotalCard title="Finished" s={data.complete} hint="Harvested, closed or sold: these margins are final." />
        <SubtotalCard title="Still running" s={data.inProgress} hint="Costs and sales to date. Unsold stock isn't valued." />
      </div>

      <div style={{ ...card, fontSize: 12.5 }} aria-label="Where the direct costs come from">
        <strong>Where the costs come from:</strong> recorded {fmtMoney(t.recordedCost)} · labour {fmtMoney(t.labour)} · equipment {fmtMoney(t.equipment)} · fuel {fmtMoney(t.fuel)} · other direct {fmtMoney(t.otherDirect)}
      </div>

      {data.notes.length > 0 && (
        <ul aria-label="Notes about this report" style={{ margin: 0, paddingLeft: 18, fontSize: 12.5, color: "var(--hf-text-secondary)", display: "grid", gap: 4 }}>
          {data.notes.map(n => <li key={n}>{n}</li>)}
        </ul>
      )}

      <div>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 10 }}>
          <label style={{ fontSize: 12, display: "grid", gap: 4 }}>Type
            <select aria-label="Filter by type" style={{ ...inp, width: 170 }} value={type} onChange={e => setType(e.target.value as TypeFilter)}>
              <option value="ALL">All types</option>
              {types.map(x => <option key={x} value={x}>{TYPE_LABEL[x]}</option>)}
            </select>
          </label>
          <label style={{ fontSize: 12, display: "grid", gap: 4 }}>Show
            <select aria-label="Filter by state" style={{ ...inp, width: 170 }} value={state} onChange={e => setState(e.target.value as StateFilter)}>
              <option value="ALL">Finished and running</option>
              <option value="COMPLETE">Finished only</option>
              <option value="IN_PROGRESS">Still running only</option>
            </select>
          </label>
        </div>
        {data.units.length === 0 ? <Empty>Nothing to show yet. Margins appear once costs are recorded or sales are allocated.</Empty>
          : shown.length === 0 ? <Empty>No units match these filters.</Empty> : (
            <div style={{ overflowX: "auto" }}>
              <table aria-label="Gross margin by unit" style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
                <thead><tr style={{ textAlign: "left" }}>{["Unit", "Margin is", "Revenue", "Direct costs", "Gross margin"].map(h => <Th key={h}>{h}</Th>)}</tr></thead>
                <tbody>{shown.map(u => <Row key={key(u)} u={u} open={openKey === key(u)} onToggle={() => setOpenKey(openKey === key(u) ? null : key(u))} />)}</tbody>
              </table>
            </div>
          )}
      </div>
    </div>
  )
}
