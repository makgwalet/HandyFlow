// src/pages/clinic/AllergySnapshot.tsx
// The allergies on record when a consultation was signed. Older consultations have none captured and show nothing.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

interface Item { allergen: string; allergenType?: string; severity?: string; reaction?: string }
interface Snapshot { captured: boolean; capturedAt?: string; items: Item[] }

const unwrap = (r: any) => r.data?.data ?? r.data
const lower = (v?: string) => (v ?? "").toLowerCase().replace(/_/g, " ")

export default function AllergySnapshot({ consultationId }: { consultationId: string }) {
  const { data } = useQuery<Snapshot>({
    queryKey: ["clinic-allergy-snapshot", consultationId],
    queryFn: async () => {
      // A snapshot that cannot be loaded is simply not shown; it must never get in the way of the consultation.
      try { return unwrap(await apiClient.get(`/api/v1/clinic/consultations/${consultationId}/allergy-snapshot`)) }
      catch { return { captured: false, items: [] } }
    },
  })
  if (!data?.captured) return null
  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.06em", marginBottom: 6 }}>
        ALLERGIES ON RECORD WHEN SIGNED
      </div>
      {data.items.length === 0
        ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No allergies were recorded at the time.</div>
        : data.items.map(i => (
          <div key={i.allergen} style={{ fontSize: 13, padding: "2px 0" }}>
            <strong>{i.allergen}</strong>
            {i.severity ? ` (${lower(i.severity)})` : ""}{i.reaction ? `: ${i.reaction}` : ""}
          </div>
        ))}
    </div>
  )
}
