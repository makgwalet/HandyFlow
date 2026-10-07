// src/pages/clinic/PatientSummaryPrint.tsx
// "Print summary": a one-page paper summary of what is on record now (active allergies, conditions, medicines, open
// notes and alerts, latest measurements). It is read fresh when the button is pressed, so it never prints stale data.
// Everything printed is escaped, because notes and names are free text.
import { useState } from "react"
import { apiClient } from "../../api/client"
import { buildMatrix, type Observation } from "./ObservationMatrix"
import type { PatientNote } from "./PatientNotes"

export interface SummaryData {
  patient: { fullName: string; dateOfBirth?: string; patientNumber?: string }
  allergies: { allergen: string; severity?: string; reaction?: string; status: string }[]
  conditions: { conditionName: string; icd10Code?: string; status: string }[]
  medications: { medicineName: string; dose?: string; frequency?: string; status: string }[]
  notes: PatientNote[]
  observations: Observation[]
  printedAt: Date
}

export const esc = (v: unknown) =>
  String(v ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c] as string))
const words = (v?: string) => esc((v ?? "").toLowerCase().replace(/_/g, " "))
const active = <T extends { status: string }>(xs: T[]) => xs.filter(x => x.status === "ACTIVE")

const section = (title: string, rows: string[], empty: string) =>
  `<h2>${esc(title)}</h2>` + (rows.length ? `<ul>${rows.map(r => `<li>${r}</li>`).join("")}</ul>` : `<p class="none">${esc(empty)}</p>`)

export function summaryHtml(d: SummaryData): string {
  const m = buildMatrix(d.observations, 1)
  const latestDay = m.days[0]
  const body = [
    section("Allergies", active(d.allergies).map(a => `<b>${esc(a.allergen)}</b>${a.severity ? ` (${words(a.severity)})` : ""}${a.reaction ? ` – ${esc(a.reaction)}` : ""}`), "None recorded"),
    section("Conditions", active(d.conditions).map(c => `${esc(c.conditionName)}${c.icd10Code ? ` [${esc(c.icd10Code)}]` : ""}`), "None recorded"),
    section("Current medicines", active(d.medications).map(x => `${esc(x.medicineName)}${x.dose ? ` ${esc(x.dose)}` : ""}${x.frequency ? `, ${esc(x.frequency)}` : ""}`), "None recorded"),
    section("Alerts and notes", d.notes.filter(n => !n.resolvedAt).map(n => `${n.kind === "ALERT" ? `<b>ALERT${n.severity ? ` (${words(n.severity)})` : ""}:</b> ` : ""}${esc(n.body)}`), "None open"),
    section(latestDay ? `Latest measurements (${esc(latestDay)})` : "Latest measurements",
      m.rows.map(r => `${esc(r.label)}: ${esc(r.cells[0]?.value)}${r.unit ? ` ${esc(r.unit)}` : ""}`), "None recorded"),
  ].join("")
  const p = d.patient
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(p.fullName)} – summary</title><style>
body{font-family:Arial,sans-serif;font-size:12px;margin:24px;color:#000}h1{font-size:18px;margin:0 0 4px}
h2{font-size:13px;border-bottom:1px solid #000;margin:14px 0 4px}ul{margin:0;padding-left:18px}.none{margin:0;color:#444}
.meta{color:#333;margin-bottom:6px}.foot{margin-top:18px;font-size:10px;color:#444}</style></head><body>
<h1>${esc(p.fullName)}</h1><div class="meta">${p.patientNumber ? `Patient no. ${esc(p.patientNumber)} · ` : ""}${p.dateOfBirth ? `Born ${esc(p.dateOfBirth)}` : ""}</div>
${body}<div class="foot">Printed ${esc(d.printedAt.toLocaleString("en-ZA"))}. Reflects the record at that time.</div></body></html>`
}

const unwrap = (r: any) => r.data?.data ?? r.data

export default function PatientSummaryPrint({ patient }: { patient: SummaryData["patient"] & { id: string } }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const go = async () => {
    setBusy(true); setError("")
    // Open the window first: browsers only allow it straight from the click, not after awaiting.
    const w = window.open("", "_blank")
    if (!w) { setError("The browser blocked the print window. Allow pop-ups for this site."); setBusy(false); return }
    try {
      const base = `/api/v1/clinic/patients/${patient.id}`
      const get = async (path: string) => unwrap(await apiClient.get(`${base}/${path}`)) ?? []
      const [allergies, conditions, medications, notes, observations] =
        await Promise.all(["allergies", "conditions", "medications", "notes", "observations"].map(get))
      w.document.open(); w.document.write(summaryHtml({ patient, allergies, conditions, medications, notes, observations, printedAt: new Date() }))
      w.document.close(); w.focus(); w.print()
    } catch (e: any) {
      w.close(); setError(e?.response?.data?.message ?? "The summary could not be loaded, so nothing was printed.")
    } finally { setBusy(false) }
  }
  return (
    <span>
      <button type="button" onClick={go} disabled={busy}>{busy ? "Preparing…" : "Print summary"}</button>
      {error && <span role="status" style={{ marginLeft: 8, fontSize: 12, color: "var(--hf-danger-text)" }}>{error}</span>}
    </span>
  )
}
