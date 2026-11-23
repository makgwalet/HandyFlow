// src/pages/agriculture/AgRegistersTab.tsx
//
// The three registers an auditor, bank, insurer, abattoir or GLOBALG.A.P. customer asks for, as CSV files that open in Excel:
// herd, veterinary treatments (with withdrawal end dates) and sprays (with pre-harvest intervals). The sale document is made from
// a sale or transfer out on the animal's or group's Movements tab.
import { useState } from "react"
import { Download } from "lucide-react"
import { btnPrimary, inp, lbl, panel } from "./constants"
import { downloadFile } from "./agDownload"
import { todayISO } from "./agCrops.logic"
import type { FarmResponse } from "./AgFarmsTab"

const BASE = "/api/v1/agriculture"

function Register({ title, text, children, busy, onDownload }: { title: string; text: string; children?: React.ReactNode; busy: boolean; onDownload: () => void }) {
  return (
    <div style={{ ...panel, marginBottom: 12 }}>
      <p style={{ fontSize: 13.5, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>{title}</p>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{text}</p>
      {children}
      <button type="button" disabled={busy} onClick={onDownload} style={{ ...btnPrimary, opacity: busy ? 0.6 : 1 }}><Download size={14} />{busy ? "Preparing…" : "Download CSV"}</button>
    </div>
  )
}

export default function AgRegistersTab({ farm }: { farm: FarmResponse }) {
  const [busy, setBusy] = useState<string | null>(null)
  const [from, setFrom] = useState("")
  const [to, setTo] = useState(todayISO())
  const run = async (key: string, path: string, params: Record<string, string | undefined>, name: string) => {
    setBusy(key); await downloadFile(`${BASE}/farms/${farm.id}/registers/${path}`, params, name); setBusy(null)
  }
  const range = { from: from || undefined, to: to || undefined }
  const rangeProblem = from && to && from > to ? "The start date is after the end date." : ""
  return (
    <div>
      <Register title="Herd register" busy={busy === "herd"} onDownload={() => run("herd", "herd", {}, "herd-register.csv")}
        text="Every animal and group on this farm, sold, dead or active: tag or batch, species, breed, birth date, status, acquisition, sire and dam, with the farm's brand and ear marks." />
      <div style={{ ...panel, marginBottom: 12 }}>
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>Period for the treatment and spray registers (leave the start empty for the last ten years).</p>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, maxWidth: 420 }}>
          <div><label style={lbl} htmlFor="reg-from">From</label><input id="reg-from" type="date" style={inp} value={from} onChange={e => setFrom(e.target.value)} /></div>
          <div><label style={lbl} htmlFor="reg-to">To</label><input id="reg-to" type="date" style={inp} value={to} onChange={e => setTo(e.target.value)} /></div>
        </div>
        {rangeProblem && <p role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "8px 0 0" }}>{rangeProblem}</p>}
      </div>
      <Register title="Veterinary treatment register" busy={busy === "treatments" || !!rangeProblem} onDownload={() => run("treatments", "treatments", range, "treatment-register.csv")}
        text="Every vaccination and treatment: date, animal or batch, product, dosage, who gave it, the vet, the withdrawal period and the date it ends." />
      <Register title="Spray and input register" busy={busy === "sprays" || !!rangeProblem} onDownload={() => run("sprays", "sprays", range, "spray-register.csv")}
        text="Every input applied to a crop: date, crop cycle, product, quantity, method, who applied it, the weather, the pre-harvest interval and the date the crop is clear to harvest." />
    </div>
  )
}
