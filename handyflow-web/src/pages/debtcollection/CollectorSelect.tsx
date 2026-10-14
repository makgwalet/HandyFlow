import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { rowsOf, collectorLabel, type Collector } from "./collections.logic"
import { inp, lbl } from "./ui"

/** Pick a registered collector by name. Replaces typing a user ID. */
export default function CollectorSelect({ value, onChange, label = "Assigned to", blankLabel = "Unassigned" }: { value: string; onChange: (userId: string, c: Collector | null) => void; label?: string; blankLabel?: string }) {
  const { data: collectors = [], isLoading } = useQuery<Collector[]>({
    queryKey: ["dc-collectors"],
    queryFn: async () => rowsOf<Collector>(await apiClient.get("/api/v1/debtcollection/allocation/collectors")),
  })
  const active = collectors.filter(c => c.active)
  return (
    <div>
      <label style={lbl} htmlFor="dc-collector">{label}</label>
      <select id="dc-collector" value={value} onChange={e => onChange(e.target.value, active.find(c => c.userId === e.target.value) ?? null)} style={inp}>
        <option value="">{blankLabel}</option>
        {active.map(c => <option key={c.userId} value={c.userId}>{collectorLabel(c)}</option>)}
      </select>
      {!isLoading && active.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 5 }}>No collectors are registered yet. An administrator adds them under allocation setup.</div>}
    </div>
  )
}
