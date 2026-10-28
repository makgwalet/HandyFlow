// A manager's view of the training of the people who report to them: what each must hold, their certificates and bookings.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate } from "./er.logic"
import { card, errorText, td, th, unwrap } from "./letters.shared"
import { Chip, Notice, Section } from "./ErParts"
import { expiryNote, orderBookings, STATE_LABEL } from "./myTraining.logic"
import { attention, cellText, orderTeam, STATUS_TONE, teamSummary, type Team } from "./trainingRequired.logic"

const ATTENTION_TEXT = { ACTION: "Needs action", EXPIRING: "Expiring soon", OK: "In order" } as const
const ATTENTION_TONE = { ACTION: STATUS_TONE.MISSING, EXPIRING: STATUS_TONE.EXPIRING, OK: STATUS_TONE.OK } as const

export default function TeamTraining() {
  const q = useQuery<Team>({ queryKey: ["training-team"], queryFn: async () => unwrap<Team>(await apiClient.get("/api/v1/training/team")) ?? { linked: false, members: [] } })
  const today = new Date().toISOString().slice(0, 10)
  const team = q.data
  const members = orderTeam(team?.members ?? [])

  return (
    <div>
      <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>The certificates, bookings and required training of the people who report to you.</p>
      {q.isError && <Notice kind="error">{errorText(q.error, "Your team's training could not be loaded.")}</Notice>}
      {q.isLoading && <div style={{ color: "var(--hf-text-faint)" }}>Loading…</div>}
      {team && !team.linked && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Your login is not linked to an employee record, so there is no team to show. Ask HR to link it.</p>}
      {team?.linked && <p style={{ fontSize: 13, margin: "0 0 14px" }} aria-live="polite">{teamSummary(members)}</p>}

      {members.map(m => {
        const a = attention(m)
        const certificates = m.certificates.filter(c => c.state !== "REVOKED")
        const bookings = orderBookings(m.bookings, today).filter(b => b.status === "ENROLLED")
        return (
          <Section key={m.employeeId} title={m.name}>
            <div style={card}>
              <div style={{ padding: "10px 12px", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                <Chip text={ATTENTION_TEXT[a]} color={ATTENTION_TONE[a].color} bg={ATTENTION_TONE[a].bg} />
                {m.jobTitle && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{m.jobTitle}</span>}
              </div>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Required course</th><th style={th}>Where they stand</th></tr></thead>
                <tbody>
                  {m.required.length === 0 && <tr><td style={td} colSpan={2}>Nothing is required of this person.</td></tr>}
                  {m.required.map(r => (
                    <tr key={r.courseId}>
                      <td style={td}>{r.courseTitle}</td>
                      <td style={td}><Chip text={cellText(r)} color={STATUS_TONE[r.status].color} bg={STATUS_TONE[r.status].bg} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Certificate</th><th style={th}>Status</th></tr></thead>
                <tbody>
                  {certificates.length === 0 && <tr><td style={td} colSpan={2}>No certificates held.</td></tr>}
                  {certificates.map(c => (
                    <tr key={c.id}>
                      <td style={td}>{c.courseTitle}<div style={{ color: "var(--hf-text-muted)" }}>{c.number}</div></td>
                      <td style={td}>{STATE_LABEL[c.state]}<div style={{ color: "var(--hf-text-muted)" }}>{expiryNote(c)}</div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {bookings.length > 0 && (
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr><th style={th}>Booked on</th><th style={th}>When</th></tr></thead>
                  <tbody>
                    {bookings.map(b => (
                      <tr key={b.id}><td style={td}>{b.courseTitle}</td><td style={td}>{fmtDate(b.startDate)}{b.venue ? ` · ${b.venue}` : ""}</td></tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </Section>
        )
      })}
    </div>
  )
}
