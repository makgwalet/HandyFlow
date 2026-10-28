// An employee's own training: where they are booked, and the certificates they hold.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate } from "./er.logic"
import { card, downloadLetter, errorText, plain, primary, td, th, unwrap } from "./letters.shared"
import { Chip, Notice, Section } from "./ErParts"
import { canDownload, expiryNote, orderBookings, STATE_LABEL, type CertState, type MyTraining as Mine } from "./myTraining.logic"
import { canCancelBooking, orderOpenSessions, seatsText, type OpenSession } from "./trainingRequired.logic"

const BASE = "/api/v1/training/mine"

const CHIP: Record<CertState, { color: string; bg: string }> = {
  VALID: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  EXPIRING: { color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft)" },
  EXPIRED: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  REVOKED: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" },
}

export default function MyTraining() {
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const qc = useQueryClient()
  const q = useQuery<Mine>({ queryKey: ["training-mine"], queryFn: async () => unwrap<Mine>(await apiClient.get(BASE)) ?? { bookings: [], certificates: [] } })
  const open = useQuery<OpenSession[]>({ queryKey: ["training-mine-sessions"], queryFn: async () => unwrap<OpenSession[]>(await apiClient.get(`${BASE}/sessions`)) ?? [] })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["training-mine"] }); qc.invalidateQueries({ queryKey: ["training-mine-sessions"] }) }
  const book = useMutation({
    mutationFn: async (s: OpenSession) => apiClient.post(`${BASE}/bookings`, { sessionId: s.id }),
    onSuccess: (_r, s) => { setError(null); setDone(`You are booked on ${s.courseTitle}.`); refresh() },
    onError: e => { setDone(null); setError(errorText(e, "The booking could not be made.")); refresh() },
  })
  const cancel = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`${BASE}/bookings/${id}`),
    onSuccess: () => { setError(null); setDone("Your booking was cancelled."); refresh() },
    onError: e => { setDone(null); setError(errorText(e, "The booking could not be cancelled.")); refresh() },
  })
  const today = new Date().toISOString().slice(0, 10)
  const bookings = orderBookings(q.data?.bookings ?? [], today)
  const certificates = q.data?.certificates ?? []
  const download = async (id: string, number: string) => setError(await downloadLetter(`${BASE}/certificates/${id}/pdf`, `${number}.pdf`))

  return (
    <div>
      <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>The training you are booked on and the certificates you hold.</p>
      {q.isError && <Notice kind="error">{errorText(q.error, "Your training could not be loaded.")}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}
      {done && <Notice kind="ok">{done}</Notice>}

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

      <Section title="Book a session">
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Course</th><th style={th}>When</th><th style={th}>Where</th><th style={th}>Places</th><th style={th} /></tr></thead>
            <tbody>
              {open.isLoading && <tr><td style={td} colSpan={5}>Loading…</td></tr>}
              {open.isError && <tr><td style={td} colSpan={5}>{errorText(open.error, "Sessions could not be loaded.")}</td></tr>}
              {!open.isLoading && !open.isError && (open.data ?? []).length === 0 && <tr><td style={td} colSpan={5}>No sessions are open for you to book at the moment.</td></tr>}
              {orderOpenSessions(open.data ?? []).map(s => (
                <tr key={s.id}>
                  <td style={td}>{s.courseTitle}{s.required && <div style={{ color: "var(--hf-warning-text-strong)" }}>Required for your role</div>}</td>
                  <td style={td}>{fmtDate(s.startDate)}{s.endDate !== s.startDate ? ` to ${fmtDate(s.endDate)}` : ""}</td>
                  <td style={td}>{s.venue ?? "—"}</td>
                  <td style={td}>{seatsText(s.seatsLeft)}</td>
                  <td style={td}><button style={primary} disabled={book.isPending} onClick={() => book.mutate(s)} aria-label={`Book ${s.courseTitle}`}>Book</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <Section title="My bookings">
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Course</th><th style={th}>When</th><th style={th}>Where</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>
              {q.isLoading && <tr><td style={td} colSpan={5}>Loading…</td></tr>}
              {!q.isLoading && bookings.length === 0 && <tr><td style={td} colSpan={5}>You are not booked on any training.</td></tr>}
              {bookings.map(b => (
                <tr key={b.id}>
                  <td style={td}>{b.courseTitle}</td>
                  <td style={td}>{fmtDate(b.startDate)}{b.endDate && b.endDate !== b.startDate ? ` to ${fmtDate(b.endDate)}` : ""}</td>
                  <td style={td}>{b.venue ?? "—"}</td>
                  <td style={td}>{b.statusLabel}{b.passed != null && (b.passed ? " (passed)" : " (not passed)")}</td>
                  <td style={td}>{canCancelBooking(b, today) && <button style={plain} disabled={cancel.isPending} onClick={() => cancel.mutate(b.id)} aria-label={`Cancel booking for ${b.courseTitle}`}>Cancel booking</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>
    </div>
  )
}
