// An employee's own annual tax certificates (IRP5 / IT3(a)) once payroll has released them: open the list, download the PDF.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate } from "./er.logic"
import { card, downloadLetter, errorText, plain, td, th, unwrap } from "./letters.shared"
import { Notice } from "./ErParts"
import { certificateFileName, certificateTitle, type MineCertificate } from "./myCertificates.logic"

const BASE = "/api/v1/hr/tax-certificates/mine"

export default function MyTaxCertificates() {
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const q = useQuery<MineCertificate[]>({ queryKey: ["tax-certificates-mine"], queryFn: async () => unwrap<MineCertificate[]>(await apiClient.get(BASE)) ?? [] })
  const rows = q.data ?? []
  const open = async (c: MineCertificate) => {
    setBusy(c.id)
    setError(await downloadLetter(`${BASE}/${c.id}/pdf`, certificateFileName(c)))
    setBusy(null)
  }
  return (
    <div>
      <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Your annual tax certificates, once your employer has released them. You need them to file your return with SARS.
      </p>
      {q.isError && <Notice kind="error">{errorText(q.error, "Your tax certificates could not be loaded.")}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}
      <div style={card}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Certificate</th><th style={th}>Period</th><th style={th}>Released</th><th style={th}></th></tr></thead>
          <tbody>
            {q.isLoading && <tr><td style={td} colSpan={4}>Loading…</td></tr>}
            {!q.isLoading && rows.length === 0 && <tr><td style={td} colSpan={4}>No tax certificate has been released to you yet.</td></tr>}
            {rows.map(c => (
              <tr key={c.id}>
                <td style={td}><strong>{certificateTitle(c)}</strong><div style={{ color: "var(--hf-text-muted)" }}>{c.certificateNumber}</div></td>
                <td style={td}>{c.periodFrom ? `${fmtDate(c.periodFrom)} to ${fmtDate(c.periodTo)}` : "—"}</td>
                <td style={td}>{c.releasedAt ? fmtDate(c.releasedAt) : "—"}</td>
                <td style={td}><button onClick={() => open(c)} disabled={busy === c.id} style={plain}>{busy === c.id ? "Opening…" : "Download PDF"}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
