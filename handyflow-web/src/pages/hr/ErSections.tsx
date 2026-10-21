// The read-only sections of one case. Each shows only what the server sent, so a section is absent when there is nothing to say.
import { fmtDate, fmtInstant, fmtTime, words } from "./er.logic"
import { td, th, type Detail } from "./er.shared"
import { Facts, Section } from "./ErParts"

const who = (p: { name: string } | null | undefined) => p?.name ?? null

export default function ErSections({ c }: { c: Detail }) {
  const h = c.hearing, d = c.decision, a = c.appeal
  return (
    <>
      <Section title="The matter">
        <Facts rows={[
          ["Category", words(c.category)], ["Employee", `${c.employee.name}${c.jobTitle ? ` · ${c.jobTitle}` : ""}${c.department ? ` · ${c.department}` : ""}`],
          ["Incident date", c.incidentDate ? fmtDate(c.incidentDate) : null], ["Reported", fmtDate(c.reportedOn)],
          ["Case officer", who(c.owner)], ["Raised by / initiator", who(c.initiator)], ["Chair", who(c.chair)], ["Complaint is about", who(c.respondent)],
          ["Description", c.description],
        ]} />
      </Section>

      {c.suspension && (
        <Section title="Precautionary suspension">
          <Facts rows={[["From", fmtDate(c.suspension.from)], ["To", c.suspension.to ? fmtDate(c.suspension.to) : "Until further notice"],
            ["Reason", c.suspension.reason], ["Pay", "Full pay. This is not a sanction."], ["Now", c.suspension.active ? "Suspended" : "Ended"]]} />
        </Section>
      )}

      {c.steps.length > 0 && (
        <Section title="Steps the process expects">
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
            {c.steps.map(s => (
              <li key={s.code} style={{ marginBottom: 4 }}>
                {s.label}: {s.doneOn ? <strong>done {fmtDate(s.doneOn)}</strong> : <span style={{ color: "var(--hf-warning-text-strong)" }}>not recorded</span>}
                {s.note ? <span style={{ color: "var(--hf-text-muted)" }}> – {s.note}</span> : null}
              </li>
            ))}
          </ul>
          {c.review && (
            <p style={{ fontSize: 13, margin: "10px 0 0" }}>
              Review period {fmtDate(c.review.from)} to {fmtDate(c.review.to)}
              {c.review.shorterThanMinimum && <span style={{ color: "var(--hf-warning-text-strong)" }}> – shorter than the company minimum</span>}
            </p>
          )}
        </Section>
      )}

      {(h?.noticeIssuedAt || h?.date) && (
        <Section title={c.type === "INCAPACITY" ? "Meeting" : "Hearing"}>
          <Facts rows={[
            ["Notice issued", h?.noticeIssuedAt ? fmtInstant(h.noticeIssuedAt) : null],
            ["Scheduled", h?.date ? `${fmtDate(h.date)} at ${fmtTime(h.time)}` : null], ["Venue", h?.venue ?? null],
            ["Held on", h?.heldOn ? fmtDate(h.heldOn) : null],
            ["Employee attended", h?.employeeAttended == null ? null : h.employeeAttended ? "Yes" : "No"],
            ["Representative", h?.representative ?? null], ["Notes", h?.notes ?? null],
          ]} />
        </Section>
      )}

      {c.charges.length > 0 && (
        <Section title="Charges">
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>#</th><th style={th}>Charge</th><th style={th}>Finding</th></tr></thead>
            <tbody>
              {c.charges.map(x => (
                <tr key={x.id}>
                  <td style={td}>{x.seq}</td><td style={td}>{x.charge}</td>
                  <td style={td}>{x.finding ? words(x.finding) : "–"}{x.findingNote ? <div style={{ color: "var(--hf-text-muted)" }}>{x.findingNote}</div> : null}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Section>
      )}

      {d?.outcome && (
        <Section title="Outcome">
          <Facts rows={[
            ["Outcome", words(d.outcome)], ["Decided on", fmtDate(d.decidedOn)],
            ["Warning valid", d.warningMonths ? `${d.warningMonths} months, until ${fmtDate(d.warningValidUntil)}` : null],
            ["Reasons", d.note], ["Employee's response", d.employeeResponse],
            ["Employee told on", d.communicatedOn ? fmtDate(d.communicatedOn) : "Not yet recorded"],
            ["Dismissal rights explained", d.dismissalRightsExplainedOn ? fmtDate(d.dismissalRightsExplainedOn) : null],
            ["CCMA referral deadline", d.ccmaDeadline ? fmtDate(d.ccmaDeadline) : null],
            ["Acknowledged by employee", d.acknowledged ? `Yes, ${fmtInstant(d.acknowledgedAt)}` : "No"],
          ]} />
        </Section>
      )}

      {a?.lodgedOn && (
        <Section title={c.type === "GRIEVANCE" ? "Next level" : "Appeal"}>
          <Facts rows={[
            ["Lodged", fmtDate(a.lodgedOn)], ["Deadline", a.deadline ? fmtDate(a.deadline) : null], ["Grounds", a.grounds],
            ["Decided by", who(a.decider)], ["Decided on", a.decidedOn ? fmtDate(a.decidedOn) : null],
            ["Result", a.outcome ? words(a.outcome) : "Waiting for a decision"], ["Reasons", a.note],
          ]} />
        </Section>
      )}

      {c.closedOn && (
        <Section title={c.status === "WITHDRAWN" ? "Withdrawn" : "Closed"}>
          <Facts rows={[["On", fmtDate(c.closedOn)], ["Reason", c.closedReason]]} />
        </Section>
      )}

      <Section title="Timeline">
        {c.events.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing recorded yet.</div> : (
          <ol style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
            {c.events.map((e, i) => (
              <li key={`${e.action}-${e.doneAt}-${i}`} style={{ marginBottom: 6 }}>
                <strong>{words(e.action)}</strong>{e.note ? ` – ${e.note}` : ""}
                <span style={{ color: "var(--hf-text-faint)" }}> · {fmtInstant(e.doneAt)}{e.doneBy ? ` · ${e.doneBy}` : ""}{e.visibleToEmployee ? "" : " · HR only"}</span>
              </li>
            ))}
          </ol>
        )}
        <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 8 }}>The timeline cannot be edited or deleted.</div>
      </Section>
    </>
  )
}
