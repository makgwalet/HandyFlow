// Overview card: how complete the patient's profile is, and a way into the profile page.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Card, primaryBtn } from "./OverviewCard"
import { GRAY } from "./patientFile.shared"
import { firstIncomplete, profileSummary, type Completeness, type SectionId } from "./profileView"

export default function ProfileCard({ patientId, onOpenProfile }: { patientId: string; onOpenProfile?: (section?: SectionId) => void }) {
  const canRead = usePermission("CLINIC_PATIENT_READ")
  const canEdit = usePermission("CLINIC_PATIENT_UPDATE")
  const { data } = useQuery<{ completeness: Completeness }>({
    queryKey: ["pf-profile", patientId], enabled: canRead, retry: false,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/clinic/patients/${patientId}/profile`); return r.data?.data ?? r.data },
  })
  const c = data?.completeness
  if (!canRead || !c || !onOpenProfile) return null
  const complete = c.done === c.total
  return (
    <Card title="Profile" aside={<strong style={{ fontSize: 13 }}>{c.percent}%</strong>}>
      <div role="progressbar" aria-label="Profile completeness" aria-valuenow={c.percent} aria-valuemin={0} aria-valuemax={100}
        style={{ height: 6, borderRadius: 3, background: "var(--hf-surface-sunken)", overflow: "hidden", marginBottom: 8 }}>
        <div style={{ width: `${c.percent}%`, height: "100%", background: "var(--hf-primary)" }} />
      </div>
      <div style={{ fontSize: 12, color: GRAY, marginBottom: 10 }}>{profileSummary(c)}</div>
      <button type="button" onClick={() => onOpenProfile(complete ? "identity" : firstIncomplete(c))} style={primaryBtn}>
        {complete ? (canEdit ? "Update profile" : "View profile") : (canEdit ? "Complete profile" : "View profile")}
      </button>
    </Card>
  )
}
