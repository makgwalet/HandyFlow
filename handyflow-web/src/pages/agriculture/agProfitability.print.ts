// src/pages/agriculture/agProfitability.print.ts
//
// The printable (and "Save as PDF") view of the gross-margin report (ADR-001, W6 leftovers). It is a SELF-CONTAINED HTML document opened in its own window, so it does
// not depend on hiding the app's menus and cannot be broken by them. Every value that came from a user (farm, season, unit and tag names, caveat text) is HTML-escaped:
// a farm called "<script>" must print as text, never run. A loss is written as the word "Loss", so a black-and-white printout still reads correctly.
import type { Profitability, UnitProfit } from "./agProfitability.api"
import { TYPE_LABEL, costParts, percentText, stateLabel, toneOf } from "./agProfitability.logic"
import { fmtMoney } from "./constants"

/** Escapes text for HTML (including inside attribute values). */
export function esc(v: string | number | null | undefined): string {
  return String(v ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;")
}

const money = (n: number) => esc(fmtMoney(n))
const marginText = (n: number, pct: number | null) => {
  const tone = toneOf(n)
  return `${tone === "loss" ? "Loss " : tone === "gain" ? "Gain " : ""}${money(Math.abs(n))} <span class="pct">(${esc(percentText(pct))})</span>`
}

const STYLE = `
  @page { size: A4; margin: 14mm; }
  * { box-sizing: border-box; }
  body { font: 12px/1.45 -apple-system, "Segoe UI", Roboto, Arial, sans-serif; color: #111; margin: 0; }
  h1 { font-size: 20px; margin: 0 0 2px; } h2 { font-size: 13px; margin: 18px 0 6px; }
  .meta { color: #444; margin: 0 0 12px; }
  .totals { display: flex; gap: 28px; margin: 10px 0 4px; } .totals div b { display: block; font-size: 17px; }
  .totals span { font-size: 10px; text-transform: uppercase; letter-spacing: .4px; color: #555; }
  table { width: 100%; border-collapse: collapse; } th { text-align: left; font-size: 10px; text-transform: uppercase; letter-spacing: .4px; color: #555; border-bottom: 1.5px solid #111; padding: 5px 6px; }
  td { padding: 5px 6px; border-bottom: 1px solid #ccc; vertical-align: top; } td.n, th.n { text-align: right; }
  tr.detail td { color: #444; font-size: 11px; border-bottom: none; padding-top: 0; } tr.care td { font-size: 11px; font-style: italic; padding-top: 0; }
  .pct { color: #555; font-weight: 400; } .small { color: #444; font-size: 11px; } ul { margin: 4px 0 0; padding-left: 18px; } li { margin: 2px 0; }
  tr { break-inside: avoid; } footer { margin-top: 18px; padding-top: 6px; border-top: 1px solid #999; color: #555; font-size: 10.5px; }
`

function unitRows(u: UnitProfit): string {
  const parts = costParts(u)
  const detail = parts.length ? `<tr class="detail"><td colspan="5">${parts.map(p => `${esc(p.label)} ${money(p.amount)}`).join(" · ")}</td></tr>` : ""
  const care = u.caveats.length ? `<tr class="care"><td colspan="5">Read with care: ${u.caveats.map(esc).join(" ")}</td></tr>` : ""
  return `<tr><td><b>${esc(u.label)}</b> <span class="small">${esc(TYPE_LABEL[u.targetType])}</span></td><td>${esc(stateLabel(u.state))}</td><td class="n">${money(u.revenue)}</td><td class="n">${money(u.directCost)}</td><td class="n">${marginText(u.grossMargin, u.marginPercent)}</td></tr>${detail}${care}`
}

/** The whole printable document for one farm's report (or one season of it). Every unit is included, whatever the screen's filters were. */
export function profitabilityPrintHtml(d: Profitability, ctx: { farm: string; season: string | null; generatedOn: string }): string {
  const t = d.totals
  const covers = ctx.season ? `Season: ${ctx.season} (its crop cycles only)` : "Whole farm"
  const sub = (label: string, s: { units: number; revenue: number; directCost: number; grossMargin: number; marginPercent: number | null }) =>
    s.units > 0 ? `<tr><td>${esc(label)} <span class="small">(${s.units} unit${s.units === 1 ? "" : "s"})</span></td><td class="n">${money(s.revenue)}</td><td class="n">${money(s.directCost)}</td><td class="n">${marginText(s.grossMargin, s.marginPercent)}</td></tr>` : ""
  const subs = [sub("Finished (final)", d.complete), sub("Still running (to date)", d.inProgress), sub("Breeding stock (running costs and sales only)", d.breedingStock)].join("")
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>${esc(`Gross margin report - ${ctx.farm}`)}</title><style>${STYLE}</style></head>
<body>
<h1>Gross margin report</h1>
<p class="meta"><b>${esc(ctx.farm)}</b> · ${esc(covers)} · Generated ${esc(ctx.generatedOn)}</p>
<div class="totals">
  <div><span>Revenue (ex-VAT)</span><b>${money(t.revenue)}</b></div>
  <div><span>Direct costs</span><b>${money(t.directCost)}</b></div>
  <div><span>Gross margin</span><b>${marginText(t.grossMargin, t.marginPercent)}</b></div>
</div>
<p class="small">Recorded costs ${money(t.recordedCost)} · labour ${money(t.labour)} · equipment ${money(t.equipment)} · fuel ${money(t.fuel)} · other direct ${money(t.otherDirect)}</p>
${subs ? `<h2>By stage</h2><table><thead><tr><th>Stage</th><th class="n">Revenue</th><th class="n">Direct costs</th><th class="n">Gross margin</th></tr></thead><tbody>${subs}</tbody></table>` : ""}
<h2>By unit</h2>
${d.units.length === 0 ? "<p>Nothing to show yet. Margins appear once costs are recorded or sales are allocated.</p>"
  : `<table><thead><tr><th>Unit</th><th>Margin is</th><th class="n">Revenue</th><th class="n">Direct costs</th><th class="n">Gross margin</th></tr></thead><tbody>${d.units.map(unitRows).join("")}</tbody></table>`}
${d.notes.length ? `<h2>Notes</h2><ul>${d.notes.map(n => `<li>${esc(n)}</li>`).join("")}</ul>` : ""}
<footer>Gross margin only: revenue (ex-VAT, net of credit notes) minus direct production costs. Overheads, finance costs, depreciation and tax are not included. This is not net profit.</footer>
</body></html>`
}

/**
 * Opens the document in a new window and prints it (the browser's print dialog also offers "Save as PDF"). Returns false when the browser blocked the window, so the
 * caller can tell the user to allow pop-ups.
 */
export function openPrintWindow(html: string): boolean {
  const w = window.open("", "_blank")
  if (!w) return false
  w.document.open(); w.document.write(html); w.document.close()
  w.focus()
  setTimeout(() => { try { w.print() } catch { /* the window was closed meanwhile */ } }, 250)     // let the document lay out before the print dialog opens
  return true
}
