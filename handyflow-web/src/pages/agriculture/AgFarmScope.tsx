// src/pages/agriculture/AgFarmScope.tsx
//
// Crop cycles, seasons and cost reports are farm-scoped on the server. This picks the farm (kept in the URL
// as ?farm=<id>, defaulting to the first active farm) and renders its children for that farm only. It does
// not loop over every farm: an "all farms" view needs a tenant-wide endpoint that does not exist yet.
import type { ReactNode } from "react"
import { Link, useSearchParams } from "react-router-dom"
import { Tractor } from "lucide-react"
import { useActiveFarms } from "./agCrops.api"
import type { FarmResponse } from "./AgFarmsTab"
import { AG_ACCENT_TEXT, inp, lbl } from "./constants"

export default function AgFarmScope({ children }: { children: (farm: FarmResponse) => ReactNode }) {
  const { data: farms, isLoading, isError, refetch } = useActiveFarms()
  const [params, setParams] = useSearchParams()

  if (isLoading) return <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading farms…</p>
  if (isError) {
    return (
      <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>
        We couldn't load your farms. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button>
      </div>
    )
  }
  if (!farms || farms.length === 0) {
    return (
      <div style={{ textAlign: "center", padding: "36px 12px" }}>
        <Tractor size={28} style={{ color: "var(--hf-text-disabled)", marginBottom: 8 }} />
        <p style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>No active farms yet</p>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>
          Crops, seasons and costs belong to a farm. <Link to="/agriculture/farms" style={{ color: AG_ACCENT_TEXT, fontWeight: 600 }}>Register a farm</Link> first.
        </p>
      </div>
    )
  }

  const farm = farms.find(f => f.id === params.get("farm")) ?? farms[0]
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 18 }}>
        <label htmlFor="ag-farm-scope" style={{ ...lbl, margin: 0 }}>Farm</label>
        <select id="ag-farm-scope" value={farm.id} style={{ ...inp, width: "auto", minWidth: 220 }}
          onChange={e => setParams(prev => { const next = new URLSearchParams(prev); next.set("farm", e.target.value); return next }, { replace: true })}>
          {farms.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
        </select>
      </div>
      {/* keyed by farm so each farm starts with fresh filters and no half-open forms */}
      <div key={farm.id}>{children(farm)}</div>
    </div>
  )
}
