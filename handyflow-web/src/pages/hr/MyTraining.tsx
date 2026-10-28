// An employee's own training: where they are booked, and the certificates they hold.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate } from "./er.logic"
import { card, downloadLetter, errorText, plain, td, th, unwrap } from "./letters.shared"
import { Chip, Notice, Section } from "./ErParts"
import { canDownload, expiryNote, orderBookings, STATE_LABEL, type CertState, type MyTraining as Mine } from "./myTraining.logic"

const BASE = "/api/v1/training/mine"

const CHIP: Record<CertState, { color: string; bg: string }> = {
  VALID: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  EXPIRING: { color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft)" },
  EXPIRED: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  REVOKED: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" },
}

export default function MyTraining() {
  const [error, setError] = useState<string | null>(null)
  const q = useQuery<Mine>({ queryKey: ["training-mine"], queryFn: async () => unwrap<Mine>(await apiClient.get(BASE)) ?? { bookings: [], certificates: [] } })
  const today = new Date().toISOString().slice(0, 10)
  const bookings = orderBookings(q.data?.bookings ?? [], today)
  const certificates = q.data?.certificates ?? []
  const download = async (id: string, number: string) => setError(await downloadLetter(`${BASE}/certificates/${id}/pdf`, `${number}.pdf`))

  return (
    <div>
      <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>The training you are booked on and the certificates you hold.</p>
      {q.isError && <Notice kind="error">{errorText(q.error, "Your training could not be loaded.")}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}

      <Section title="My certificates">
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Course</th><th style={th}>Issued</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>
              {q.isLoading && <tr><td style={td} colSpan={4}>Loading…</td></tr>}
              {!q.isLoading && certificates.length === 0 && <tr><td style={td} colSpan={4}>You hold no certificates yet.</td></tr>}
              {certificates.map(c => (
                <tr key={c.id}>
                  <td style={td}>{c.courseTitle}<div style={{ color: "var(--hf-text-muted)" }}>{c.number}</div></td>
                  <td style={td}>{fmtDate(c.issueDate)}</td>
                  <td style={td}><Chip text={STATE_LABEL[c.state]} color={CHIP[c.state].color} bg={CHIP[c.state].bg} /><div style={{ color: "var(--hf-text-muted)", marginTop: 4 }}>{expiryNote(c)}</div></td>
                  <td style={td}>{canDownload(c) && <button onClick={() => download(c.id, c.number)} style={plain} aria-label={`Download certificate ${c.number}`}>Download PDF</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <Section title="My bookings">
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Course</th><th style={th}>When</th><th style={th}>Where</th><th style={th}>Status</th></tr></thead>
            <tbody>
              {q.isLoading && <tr><td style={td} colSpan={4}>Loading…</td></tr>}
              {!q.isLoading && bookings.length === 0 && <tr><td style={td} colSpan={4}>You are not booked on any training.</td></tr>}
              {bookings.map(b => (
                <tr key={b.id}>
                  <td style={td}>{b.courseTitle}</td>
                  <td style={td}>{fmtDate(b.startDate)}{b.endDate && b.endDate !== b.startDate ? ` to ${fmtDate(b.endDate)}` : ""}</td>
                  <td style={td}>{b.venue ?? "—"}</td>
                  <td style={td}>{b.statusLabel}{b.passed != null && (b.passed ? " (passed)" : " (not passed)")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>
    </div>
  )
}
