// What an employer sees of its own people on programmes. Read-only. Only learners whose consent has been recorded are listed; the rest are counted.
import { useQuery } from "@tanstack/react-query"
import { trainProvPortalClient } from "../../api/trainProvPortal.client"
import PortalEnrolmentCard from "./PortalEnrolmentCard"
import { hiddenText, type EmployerLearners } from "./learnerPortalRules"

const muted = { color: "var(--hf-text-faint)", fontSize: 13 } as const

export default function TrainProvEmployerLearners() {
  const { data, isLoading, isError } = useQuery<EmployerLearners>({
    queryKey: ["trainprov-portal-programme-learners"],
    queryFn: async () => (await trainProvPortalClient.get("/api/v1/training-provider/portal/me/programme-learners")).data,
  })
  if (isLoading) return <p style={muted}>Loading…</p>
  if (isError || !data) return <p role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>Could not load your learners. Try again later.</p>
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <p style={muted}>{data.note}</p>
      {hiddenText(data.hiddenForConsent) && <p style={muted}>{hiddenText(data.hiddenForConsent)}</p>}
      {data.rows.length === 0 ? <p style={muted}>No learners to show yet.</p> : data.rows.map(r => (
        <div key={r.enrolment.enrolmentNo} style={{ display: "grid", gap: 4 }}>
          <div style={{ fontSize: 13, fontWeight: 700 }}>{r.fullName} <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>{r.learnerNo}</span></div>
          <PortalEnrolmentCard e={r.enrolment} />
        </div>
      ))}
    </div>
  )
}
