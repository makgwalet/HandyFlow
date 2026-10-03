// src/pages/fleet/EquipmentTab.tsx
//
// The numbers Agriculture needs to cost a machine's use (ADR-001, W4): the engine-hours meter and the operating rate per hour. The rate is what
// an hour of use costs to keep the machine running: service and repairs ONLY, never fuel (allocated from fuel dispatches) and never depreciation.
// Anyone with FLEET_READ can see it; changing it needs FLEET_MANAGE.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { equipmentChanged, equipmentProblem, parseNonNegative } from "./equipment.logic"

interface Equipment { id: string; registration: string; make: string | null; model: string | null; vehicleType: string; status: string; engineHours: number | null; operatingRatePerHour: number | null }

/** The list from either response shape: { data: [...] } or { data: { content: [...] } } (the API envelope, with or without paging). */
const unwrap = (r: { data: unknown }): Equipment[] => {
  const outer = r.data as { data?: unknown } | null
  const p = (outer && typeof outer === "object" && !Array.isArray(outer) && "data" in outer ? outer.data : r.data) as Equipment[] | { content?: Equipment[] } | null
  return (Array.isArray(p) ? p : p?.content) ?? []
}
const th: React.CSSProperties = { padding: "8px 10px", fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)", textAlign: "left" }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 12.5, verticalAlign: "top" }
const input: React.CSSProperties = { width: 130, padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }

function Row({ v, canEdit }: { v: Equipment; canEdit: boolean }) {
  const qc = useQueryClient()
  const [hours, setHours] = useState(v.engineHours == null ? "" : String(v.engineHours))
  const [rate, setRate] = useState(v.operatingRatePerHour == null ? "" : String(v.operatingRatePerHour))
  const [message, setMessage] = useState("")
  const save = useMutation({
    mutationFn: (body: { engineHours: number | null; operatingRatePerHour: number | null }) => apiClient.patch(`/api/v1/fleet/vehicles/${v.id}/equipment`, body),
    onSuccess: () => { setMessage("Saved"); qc.invalidateQueries({ queryKey: ["fleet", "equipment"] }) },
    onError: () => setMessage("Couldn't save. Try again."),
  })
  const problem = equipmentProblem(hours, rate)
  const changed = equipmentChanged(hours, rate, v)
  const submit = () => {
    const h = parseNonNegative(hours, "Engine hours"), r = parseNonNegative(rate, "The operating rate")
    if (h.ok && r.ok) { setMessage(""); save.mutate({ engineHours: h.value, operatingRatePerHour: r.value }) }
  }
  return (
    <tr aria-label={v.registration} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
      <td style={td}><strong>{v.registration}</strong><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{[v.make, v.model].filter(Boolean).join(" ") || v.vehicleType}</div></td>
      <td style={td}><input aria-label={`Engine hours for ${v.registration}`} type="number" min="0" step="any" style={input} value={hours} disabled={!canEdit} onChange={e => { setHours(e.target.value); setMessage("") }} /></td>
      <td style={td}><input aria-label={`Operating rate per hour for ${v.registration}`} type="number" min="0" step="any" style={input} value={rate} disabled={!canEdit} placeholder="Not set" onChange={e => { setRate(e.target.value); setMessage("") }} />
        {problem && <div role="alert" style={{ fontSize: 11, color: "var(--hf-danger-text)", marginTop: 4, maxWidth: 220 }}>{problem}</div>}</td>
      <td style={td}>{canEdit && <button type="button" style={{ ...btn, opacity: !problem && changed && !save.isPending ? 1 : 0.5 }} disabled={!!problem || !changed || save.isPending} aria-label={`Save ${v.registration}`} onClick={submit}>Save</button>}
        {message && <span role="status" style={{ marginLeft: 8, fontSize: 12, color: message === "Saved" ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>{message}</span>}</td>
    </tr>
  )
}

export default function EquipmentTab() {
  const canEdit = usePermission("FLEET_MANAGE")
  const { data, isLoading, isError, refetch } = useQuery<Equipment[]>({ queryKey: ["fleet", "equipment"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/equipment")) })

  return (
    <div>
      <div style={{ marginBottom: 14, fontSize: 12.5, color: "var(--hf-text-muted)", maxWidth: 720 }}>
        For machines run by the hour, such as tractors and harvesters. The <strong>operating rate</strong> is what one hour of use costs to keep the machine running: <strong>service and repairs only</strong>.
        Don't include fuel (Agriculture allocates fuel from your fuel dispatches) or depreciation, or the same cost would be counted twice. Agriculture reads the rate when it costs a day's use, and keeps the rate it used.
      </div>
      {isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
      {isError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load the equipment. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>}
      {data && (data.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No vehicles yet. Add a vehicle first.</p> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Vehicle</th><th style={th}>Engine hours</th><th style={th}>Operating rate (R per hour)</th><th style={th} /></tr></thead>
            <tbody>{data.map(v => <Row key={v.id} v={v} canEdit={canEdit} />)}</tbody>
          </table>
          {!canEdit && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", marginTop: 8 }}>You can see these but not change them. Changing a rate needs Fleet manage access.</p>}
        </div>
      ))}
    </div>
  )
}
