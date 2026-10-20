// src/pages/fleet/VehicleEditForm.tsx
//
// Edit a vehicle's details, identity (engine number, masses) and valuation. Saves the whole record, so nothing is dropped.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ACQUISITION_METHODS, DRIVE_TYPES, FUEL_TYPES, TRANSMISSIONS, VEHICLE_TYPES, derivedPayload, optionLabel, toVehicleForm, vehicleBody, vehicleProblem, withCurrent, type VehicleForm, type VehicleFull } from "./vehicleEdit.logic"

const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const input: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5, width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5, cursor: "pointer" }
const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(180px,1fr))", gap: 8, marginBottom: 10 }

export default function VehicleEditForm({ vehicleId, vehicle, onDone }: { vehicleId: string; vehicle: VehicleFull; onDone: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<VehicleForm>(() => toVehicleForm(vehicle))
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/fleet/vehicles/${vehicleId}`, vehicleBody(f)),
    onSuccess: () => { for (const k of [["fleet-vehicles"], ["fleet", "compliance", vehicleId]]) qc.invalidateQueries({ queryKey: k }); onDone() },
    onError: e => setError(errorOf(e)),
  })
  const problem = vehicleProblem(f), payload = derivedPayload(f)
  const set = (k: keyof VehicleForm, v: string) => { setF({ ...f, [k]: v }); setError(null) }
  const text = (k: keyof VehicleForm, label: string, type = "text") => <label style={{ fontSize: 12 }}>{label}<input aria-label={label} type={type} style={input} value={f[k]} onChange={e => set(k, e.target.value)} /></label>
  const pick = (k: keyof VehicleForm, label: string, options: string[], blank = false) => (
    <label style={{ fontSize: 12 }}>{label}<select aria-label={label} style={input} value={f[k]} onChange={e => set(k, e.target.value)}>
      {blank && <option value="">Not recorded</option>}{withCurrent(options, f[k]).map(o => <option key={o} value={o}>{optionLabel(o)}</option>)}</select></label>)
  return (
    <form onSubmit={e => { e.preventDefault(); if (!problem) save.mutate() }} aria-label="Edit vehicle">
      <h4 style={{ margin: "0 0 6px" }}>Vehicle</h4>
      <div style={grid}>{text("registration", "Registration")}{text("make", "Make")}{text("model", "Model")}{text("year", "Year", "number")}{text("colour", "Colour")}{pick("vehicleType", "Type", VEHICLE_TYPES)}{pick("fuelType", "Fuel type", FUEL_TYPES, true)}{text("tankCapacityLitres", "Tank capacity (L)", "number")}{text("dailyRate", "Daily rate (R)", "number")}</div>
      <h4 style={{ margin: "0 0 6px" }}>Identity</h4>
      <div style={grid}>{text("vin", "VIN / chassis number")}{text("engineNumber", "Engine number")}{text("variant", "Variant")}{text("bodyType", "Body type")}{pick("transmission", "Transmission", TRANSMISSIONS, true)}{pick("driveType", "Drive type", DRIVE_TYPES, true)}
        {text("axleCount", "Axles", "number")}{text("gvmKg", "GVM (kg)", "number")}{text("tareKg", "Tare (kg)", "number")}{text("payloadKg", "Payload (kg)", "number")}{text("gcmKg", "Gross combination mass (kg)", "number")}</div>
      {payload != null && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: "-4px 0 8px" }}>Payload will be saved as {payload.toLocaleString("en-ZA")} kg (GVM minus tare).</div>}
      <h4 style={{ margin: "0 0 6px" }}>Papers and service</h4>
      <div style={grid}>{text("licenceDiscExpiry", "Licence disc expiry", "date")}{text("roadworthyExpiry", "Roadworthy expiry", "date")}{text("insuranceExpiry", "Insurance expiry", "date")}{text("serviceIntervalKm", "Service every (km)", "number")}{text("serviceIntervalDays", "Service every (days)", "number")}</div>
      <h4 style={{ margin: "0 0 6px" }}>Acquisition and value</h4>
      <div style={grid}>{text("acquisitionDate", "Acquired on", "date")}{pick("acquisitionMethod", "Acquired by", ACQUISITION_METHODS, true)}{text("purchasePrice", "Purchase price (R)", "number")}{text("currentBookValue", "Book value (R)", "number")}{text("currentEstimatedValue", "Estimated value (R)", "number")}{text("valuedAt", "Valued on", "date")}</div>
      <label style={{ fontSize: 12 }}>Notes<textarea aria-label="Notes" rows={2} style={{ ...input, fontFamily: "inherit" }} value={f.notes} onChange={e => set("notes", e.target.value)} /></label>
      <div style={{ marginTop: 10, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <button type="submit" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending}>{save.isPending ? "Saving…" : "Save changes"}</button>
        <button type="button" style={ghost} onClick={onDone}>Cancel</button>
        {problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
      </div>
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </form>
  )
}
