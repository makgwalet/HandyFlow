// src/pages/fleet/VehicleSummary.tsx
// The band at the top of a vehicle panel: service due, odometer, and the three documents, so nobody has to open a tab to see what needs attention.
import { useInRouterContext, useNavigate } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import PhotoBox from "./PhotoBox"
import { Meter } from "./FleetBars"
import { TONE_COLOUR } from "./fleetTones"
import { vehicleSummary } from "./vehicleSummary.logic"
import { insuranceLine } from "./vehicleOverview.logic"
import { serviceDue } from "./vehicleTabs.logic"
import { fmtOdo } from "./vehicleStatus"
import type { ListVehicle } from "./fleetBoards.logic"

const label: React.CSSProperties = { fontSize: 10, fontWeight: 700, textTransform: "uppercase", color: "var(--hf-text-faint)", marginBottom: 4 }
const orderButton: React.CSSProperties = { marginTop: 8, padding: "5px 12px", fontSize: 12, fontWeight: 700, borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", cursor: "pointer", textDecoration: "none", display: "inline-block" }
const box: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px", background: "var(--hf-surface)" }

/** Opens the workshop with a new work order for this vehicle. Inside the app it navigates; elsewhere (tests, embeds) it is a plain link. */
function CreateOrder({ vehicleId }: { vehicleId: string }) {
  const href = `/fleet/workshop?vehicleId=${encodeURIComponent(vehicleId)}`
  return useInRouterContext() ? <RouterOrder href={href} /> : <a href={href} style={orderButton}>Create work order</a>
}
function RouterOrder({ href }: { href: string }) {
  const navigate = useNavigate()
  return <button type="button" style={orderButton} onClick={() => navigate(href)}>Create work order</button>
}

export default function VehicleSummary({ vehicleId, name, vehicle }: { vehicleId: string; name: string; vehicle: Pick<ListVehicle, "currentOdometer" | "lastServiceKm" | "serviceIntervalKm" | "licenceDiscExpiry" | "roadworthyExpiry" | "insuranceExpiry"> }) {
  const s = vehicleSummary(vehicle), canManage = usePermission("FLEET_MANAGE"), due = serviceDue(vehicle)
  const insurance = useQuery<{ insurer: string | null; policyNumber: string | null } | null>({ queryKey: ["fleet", "insurance", vehicleId], queryFn: async () => { const o = (await apiClient.get(`/api/v1/fleet/vehicles/${vehicleId}/insurance`)).data as { data?: unknown } | null; return (o && typeof o === "object" && "data" in o ? o.data : o) as { insurer: string | null; policyNumber: string | null } | null } })
  const policy = insuranceLine(insurance.data)
  return (
    <section aria-label="Vehicle summary" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10, marginBottom: 12 }}>
      <div style={{ ...box, display: "flex", alignItems: "flex-start" }}><PhotoBox base={`/api/v1/fleet/vehicles/${vehicleId}`} canEdit={canManage} name={name} /></div>
      <div style={box} aria-label="Service due summary">
        <div style={label}>Service due</div>
        <Meter label="Service" {...s.service} />
        <dl style={{ margin: "8px 0 0", fontSize: 12, display: "grid", gridTemplateColumns: "auto 1fr", gap: "2px 10px" }}>
          <dt style={{ color: "var(--hf-text-faint)" }}>Next service</dt><dd style={{ margin: 0, fontWeight: 600 }}>{due.dueKm == null ? "Not set" : fmtOdo(due.dueKm)}</dd>
          <dt style={{ color: "var(--hf-text-faint)" }}>{due.remainingKm != null && due.remainingKm < 0 ? "Overdue by" : "Due in"}</dt><dd style={{ margin: 0, fontWeight: 600, color: TONE_COLOUR[due.tone] }}>{due.remainingKm == null ? "—" : fmtOdo(Math.abs(due.remainingKm))}</dd>
          <dt style={{ color: "var(--hf-text-faint)" }}>Last service</dt><dd style={{ margin: 0, fontWeight: 600 }}>{s.lastServiceKm == null ? "Not recorded" : fmtOdo(s.lastServiceKm)}</dd>
          <dt style={{ color: "var(--hf-text-faint)" }}>Odometer</dt><dd style={{ margin: 0, fontWeight: 600 }}>{s.odometer == null ? "Not recorded" : fmtOdo(s.odometer)}</dd>
        </dl>
        {canManage && <CreateOrder vehicleId={vehicleId} />}
      </div>
      <div style={box}>
        <div style={label}>Documents</div>
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 3 }}>
          {s.docs.map(d => <li key={d.label} aria-label={`${d.label}: ${d.text}`} style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5 }}><span>{d.label}</span><strong style={{ color: TONE_COLOUR[d.tone] }}>{d.text}</strong></li>)}
        </ul>
        {policy && <div aria-label="Insurance policy" style={{ fontSize: 11.5, color: "var(--hf-text-muted)", marginTop: 6 }}>{policy}</div>}
      </div>
    </section>)
}
