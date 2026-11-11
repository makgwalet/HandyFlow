// src/pages/warehousing/WhseSetupTab.tsx
//
// Setup: how this warehouse is run. The operating model says whose stock is held; the industry profile says what tracking new items start with.
// They are separate choices, and neither changes how storage is billed or what the subscription allows.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnPrimary, card, ErrorLine, inputStyle, labelStyle } from "./whseUi"
import WhseWarehousesPanel from "./WhseWarehousesPanel"
import WhsePackagePanel from "./WhsePackagePanel"

export interface Option { value: string; label: string }
export interface WhseSettings {
  profileExists: boolean; operatingModel: string; industryProfile: string; defaultLotTracked: boolean; defaultExpiryTracked: boolean; activeClients: number
  operatingModels: Option[]; industryProfiles: Option[]
}

const MODEL_HELP: Record<string, string> = {
  INTERNAL: "One owner: your own stock. A second client account is not allowed.",
  THREE_PL: "Stock of several independent customers, each with their own account, tariffs and statements.",
  HYBRID: "Your own stock and your customers' stock in the same warehouse.",
}

/** Null when the change can be saved, else why not. Mirrors the server, which checks again. */
export function settingsProblem(s: Pick<WhseSettings, "profileExists" | "activeClients">, model: string): string | null {
  if (!s.profileExists) return "Set up the warehouse profile first."
  if (model === "INTERNAL" && s.activeClients > 1) return `An internal warehouse holds one owner's stock, but ${s.activeClients} client accounts are open. Close the extra accounts first.`
  return null
}

export function trackingText(lot: boolean, expiry: boolean): string {
  if (!lot) return "New items start with no lot or expiry tracking."
  return expiry ? "New items start with lot and expiry tracking switched on." : "New items start with lot tracking switched on."
}

export default function WhseSetupTab() {
  const qc = useQueryClient()
  const { data, isLoading } = useQuery<WhseSettings>({ queryKey: ["whse-settings"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/settings")).data })
  const [model, setModel] = useState("")
  const [industry, setIndustry] = useState("")
  useEffect(() => { if (data) { setModel(data.operatingModel); setIndustry(data.industryProfile) } }, [data])

  const save = useMutation({
    mutationFn: async () => apiClient.put("/api/v1/warehousing/settings", { operatingModel: model, industryProfile: industry }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["whse-settings"] }),
  })
  if (isLoading || !data) return <p style={{ fontSize: 13 }}>Loading…</p>

  const problem = settingsProblem(data, model)
  const changed = model !== data.operatingModel || industry !== data.industryProfile
  const preview = data.industryProfiles.find(p => p.value === industry)

  return (
    <div style={{ maxWidth: 720 }}>
      <h2 style={{ fontSize: 18, fontWeight: 700, margin: "0 0 4px" }}>Setup</h2>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 16px" }}>How this warehouse is run. Storage billing and your subscription are set elsewhere and are not changed here.</p>

      <div style={{ ...card, padding: 14, marginBottom: 16 }}>
        <fieldset style={{ border: "none", padding: 0, margin: 0 }}>
          <legend style={{ fontSize: 13, fontWeight: 700, padding: 0, marginBottom: 8 }}>Operating model</legend>
          {data.operatingModels.map(o => (
            <label key={o.value} style={{ display: "flex", gap: 8, alignItems: "flex-start", marginBottom: 8, fontSize: 13 }}>
              <input type="radio" name="whse-model" checked={model === o.value} onChange={() => setModel(o.value)} style={{ marginTop: 3 }} />
              <span><strong>{o.label}</strong><br /><span style={{ color: "var(--hf-text-muted)", fontSize: 12.5 }}>{MODEL_HELP[o.value]}</span></span>
            </label>
          ))}
          <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "6px 0 0" }}>{data.activeClients} client account{data.activeClients === 1 ? "" : "s"} open.</p>
        </fieldset>
      </div>

      <div style={{ ...card, padding: 14, marginBottom: 16 }}>
        <label style={{ ...labelStyle, fontSize: 13, fontWeight: 700 }} htmlFor="whse-industry">Industry profile</label>
        <select id="whse-industry" style={inputStyle} value={industry} onChange={e => setIndustry(e.target.value)}>
          {data.industryProfiles.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
        </select>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>
          {preview ? preview.label + ": " : ""}{trackingText(industry !== "GENERAL", industry !== "GENERAL")} Items already created keep their own settings, and any item can be changed.
        </p>
      </div>

      {problem && changed && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)" }}>{problem}</p>}
      <ErrorLine error={save.error} fallback="Could not save the setup" />
      <button style={{ ...btnPrimary, opacity: !changed || problem ? 0.5 : 1 }} disabled={!changed || !!problem || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Saving…" : "Save"}</button>
      {save.isSuccess && !changed && <span role="status" style={{ fontSize: 12.5, marginLeft: 10, color: "var(--hf-success-text-strong)" }}>Saved.</span>}
      <WhseWarehousesPanel />
      <WhsePackagePanel />
    </div>
  )
}
