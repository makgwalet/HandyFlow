// src/pages/fleet/InsuranceForm.tsx
//
// Record or change a vehicle's insurance profile. Saving also updates the vehicle's insurance expiry on the server.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { POLICY_TYPES, POLICY_TYPE_LABEL, insuranceBody, insuranceProblem, toInsuranceForm, type InsuranceData, type InsuranceForm as Form } from "./insurance.logic"

const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const input: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5, width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5, cursor: "pointer" }
const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(180px,1fr))", gap: 8, marginBottom: 10 }

export default function InsuranceForm({ vehicleId, current, onDone }: { vehicleId: string; current: InsuranceData | null; onDone: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<Form>(() => toInsuranceForm(current))
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/fleet/vehicles/${vehicleId}/insurance`, insuranceBody(f)),
    onSuccess: () => { for (const k of [["fleet", "insurance", vehicleId], ["fleet", "compliance", vehicleId], ["fleet-vehicles"]]) qc.invalidateQueries({ queryKey: k }); onDone() },
    onError: e => setError(errorOf(e)),
  })
  const problem = insuranceProblem(f)
  const set = <K extends keyof Form>(k: K, v: Form[K]) => { setF({ ...f, [k]: v }); setError(null) }
  const text = (k: keyof Form, label: string, type = "text") => <label style={{ fontSize: 12 }}>{label}<input aria-label={label} type={type} style={input} value={f[k] as string} onChange={e => set(k, e.target.value)} /></label>
  const check = (k: "insured" | "trackingRequired" | "windscreenCover", label: string) => <label style={{ fontSize: 12.5, display: "flex", gap: 6, alignItems: "center" }}><input aria-label={label} type="checkbox" checked={f[k]} onChange={e => set(k, e.target.checked)} />{label}</label>
  return (
    <form onSubmit={e => { e.preventDefault(); if (!problem) save.mutate() }} aria-label="Edit insurance">
      <div style={{ marginBottom: 10 }}>{check("insured", "Vehicle is insured")}</div>
      {f.insured && <>
        <div style={grid}>{text("insurer", "Insurer")}{text("policyNumber", "Policy number")}
          <label style={{ fontSize: 12 }}>Policy type<select aria-label="Policy type" style={input} value={f.policyType} onChange={e => set("policyType", e.target.value)}>{POLICY_TYPES.map(t => <option key={t} value={t}>{POLICY_TYPE_LABEL[t]}</option>)}</select></label>
          {text("policyStart", "Policy start", "date")}{text("policyExpiry", "Policy expiry", "date")}{text("premium", "Premium (R)", "number")}{text("excess", "Excess (R)", "number")}
          {text("coverAmount", "Cover amount (R)", "number")}{text("vehicleValue", "Insured vehicle value (R)", "number")}{text("thirdPartyCoverAmount", "Third-party cover (R)", "number")}{text("broker", "Broker")}{text("brokerContact", "Broker contact")}</div>
        <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 8 }}>{check("windscreenCover", "Windscreen cover")}{check("trackingRequired", "Insurer requires tracking")}</div>
        {f.trackingRequired && <div style={grid}>{text("trackingProvider", "Tracking provider")}{text("trackingDeviceNumber", "Tracking device number")}</div>}
        <label style={{ fontSize: 12 }}>Special conditions<textarea aria-label="Special conditions" rows={2} style={{ ...input, fontFamily: "inherit" }} value={f.specialConditions} onChange={e => set("specialConditions", e.target.value)} /></label>
      </>}
      <div style={{ marginTop: 10, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <button type="submit" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending}>{save.isPending ? "Saving…" : "Save insurance"}</button>
        <button type="button" style={ghost} onClick={onDone}>Cancel</button>
        {problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
      </div>
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </form>
  )
}
