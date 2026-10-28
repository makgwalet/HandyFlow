// An employee's own profile: their next of kin and dependants (which they keep up to date) and their career with the company.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorText, unwrap } from "./attendance.shared"
import { Notice, Section } from "./ErParts"
import CareerPanel from "./CareerPanel"
import RelativesPanel from "./RelativesPanel"

export default function MyProfile() {
  // The start date comes from the first "Joined" line, so this screen needs no wider access than the employee already has.
  const start = useQuery<string | null>({
    queryKey: ["career", "mine-start"],
    queryFn: async () => {
      const lines = unwrap<{ kind: string; eventDate: string }[]>(await apiClient.get("/api/v1/hr/profile/career")) ?? []
      return lines.find(l => l.kind === "HIRED")?.eventDate ?? null
    },
  })
  return (
    <div>
      <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Keep your next of kin and dependants up to date, so the company can reach the right person in an emergency.
      </p>
      {start.isError && <Notice kind="error">{errorText(start.error, "Your profile could not be loaded. Your login may not be linked to an employee record.")}</Notice>}
      <Section title="Family"><RelativesPanel base="/api/v1/hr/profile/relatives" cacheKey="mine" canEdit /></Section>
      <Section title="My career"><CareerPanel listPath="/api/v1/hr/profile/career" cacheKey="mine" startDate={start.data ?? null} /></Section>
    </div>
  )
}
