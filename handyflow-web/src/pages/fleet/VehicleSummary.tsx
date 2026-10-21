// src/pages/fleet/VehicleSummary.tsx
// The band at the top of a vehicle panel: service due, odometer, and the three documents, so nobody has to open a tab to see what needs attention.
import { Meter } from "./FleetBars"
import { TONE_COLOUR } from "./fleetTones"
import { vehicleSummary } from "./vehicleSummary.logic"
import { fmtOdo } from "./vehicleStatus"
import type { ListVehicle } from "./fleetBoards.logic"

const label: React.CSSProperties = { fontSize: 10, fontWeight: 700, textTransform: "uppercase", color: "var(--hf-text-faint)", marginBottom: 4 }
const box: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px", background: "var(--hf-surface)" }

export default function VehicleSummary({ vehicle }: { vehicle: Pick<ListVehicle, "currentOdometer" | "lastServiceKm" | "serviceIntervalKm" | "licenceDiscExpiry" | "roadworthyExpiry" | "insuranceExpiry"> }) {
  const s = vehicleSummary(vehicle)
  return (
    <section aria-label="Vehicle summary" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10, marginBottom: 12 }}>
      <div style={box}>
        <div style={label}>Next service</div>
        <Meter label="Service" {...s.service} />
        <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", marginTop: 6 }}>
          {s.odometer == null ? "Odometer not recorded" : `Odometer ${fmtOdo(s.odometer)}`}
          {s.lastServiceKm != null && ` · last service at ${fmtOdo(s.lastServiceKm)}`}
        </div>
      </div>
      <div style={box}>
        <div style={label}>Documents</div>
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 3 }}>
          {s.docs.map(d => <li key={d.label} aria-label={`${d.label}: ${d.text}`} style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5 }}><span>{d.label}</span><strong style={{ color: TONE_COLOUR[d.tone] }}>{d.text}</strong></li>)}
        </ul>
      </div>
    </section>)
}
