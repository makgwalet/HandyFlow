// One enrolment as the learner or the employer sees it: dates, cohort, attendance counts and the recorded outcome. No scores and no competence wording.
import { attendanceText, periodText, statusText, type EnrolmentView } from "./learnerPortalRules"

export default function PortalEnrolmentCard({ e }: { e: EnrolmentView }) {
  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 16px", display: "grid", gap: 3 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <strong style={{ fontSize: 13 }}>{e.programmeTitle ?? "Programme"}{e.programmeCode ? ` (${e.programmeCode})` : ""}</strong>
        <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{statusText(e.status)}</span>
      </div>
      <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{e.enrolmentNo} · {periodText(e.startDate, e.expectedEndDate)}{e.cohortName ? ` · Group: ${e.cohortName}` : ""}</div>
      <div style={{ fontSize: 12.5 }}>{attendanceText(e.attendance)}</div>
      <div style={{ fontSize: 12.5 }}>{e.outcome}{e.outcomeOn ? ` on ${e.outcomeOn}` : ""}</div>
    </div>
  )
}
