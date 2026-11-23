// src/pages/agriculture/AgFarmSetup.tsx
//
// "Getting started" card at the top of a farm: what is set up and what is next, ticked from what already exists (counts from the
// same endpoints the tabs use, one row asked for each). Shown to people who can manage Agriculture, until everything is done or
// they dismiss it (remembered in this browser only).
import { useEffect, useState } from "react"
import { useQueries, useQueryClient } from "@tanstack/react-query"
import { Check, Circle, X } from "lucide-react"
import { apiClient } from "../../api/client"
import AgManage from "./AgManage"
import { btnGhost } from "./constants"
import { dismissKey, nextStep, setupProgress, setupSteps, type SetupTab } from "./agSetup.logic"

const count = async (url: string): Promise<number> => (await apiClient.get<{ totalElements?: number }>(url, { params: { size: 1 } })).data?.totalElements ?? 0

function wasDismissed(farmId: string): boolean {
  try { return localStorage.getItem(dismissKey(farmId)) === "1" } catch { return false }
}

export default function AgFarmSetup({ farmId, activeTab, onOpenTab }: { farmId: string; activeTab: string; onOpenTab: (tab: SetupTab) => void }) {
  const qc = useQueryClient()
  // what was added on a tab is counted when the person moves to another tab
  useEffect(() => { void qc.invalidateQueries({ queryKey: ["ag-setup", farmId] }) }, [activeTab, farmId, qc])
  const [dismissed, setDismissed] = useState(() => wasDismissed(farmId))
  const base = `/api/v1/agriculture/farms/${farmId}`
  const results = useQueries({
    queries: [
      { queryKey: ["ag-setup", farmId, "areas"], queryFn: () => count(`${base}/production-areas`) },
      { queryKey: ["ag-setup", farmId, "species"], queryFn: () => count("/api/v1/agriculture/species") },
      { queryKey: ["ag-setup", farmId, "animals"], queryFn: () => count(`${base}/animals`) },
      { queryKey: ["ag-setup", farmId, "groups"], queryFn: () => count(`${base}/groups`) },
      { queryKey: ["ag-setup", farmId, "stock"], queryFn: () => count(`${base}/inventory-items`) },
    ],
  })
  if (dismissed || results.some(r => r.isLoading || r.isError)) return null
  const [areas, species, animals, groups, stock] = results.map(r => r.data ?? 0)
  const steps = setupSteps({ areas, species, animals, groups, stock })
  const next = nextStep(steps)
  if (!next) return null

  const dismiss = () => {
    setDismissed(true)
    try { localStorage.setItem(dismissKey(farmId), "1") } catch { /* private window: it simply shows again next time */ }
  }

  return (
    <AgManage>
      <section aria-label="Getting started" style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
          <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>Getting started <span style={{ fontWeight: 500, color: "var(--hf-text-faint)" }}>· {setupProgress(steps)}</span></p>
          <button onClick={dismiss} aria-label="Hide getting started" title="Hide" style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={15} /></button>
        </div>
        {steps.map(s => (
          <div key={s.key} style={{ display: "flex", gap: 10, alignItems: "flex-start", padding: "6px 0" }}>
            {s.done ? <Check size={15} style={{ color: "var(--hf-success-text)", marginTop: 2 }} aria-label="Done" /> : <Circle size={15} style={{ color: "var(--hf-text-faint)", marginTop: 2 }} aria-label="To do" />}
            <div style={{ flex: 1 }}>
              <p style={{ fontSize: 13, fontWeight: 600, margin: 0, color: s.done ? "var(--hf-text-muted)" : "var(--hf-text)" }}>{s.title}</p>
              {!s.done && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>{s.hint}</p>}
            </div>
            {!s.done && s.tab && s.action && <button onClick={() => onOpenTab(s.tab as SetupTab)} style={btnGhost}>{s.action}</button>}
          </div>
        ))}
      </section>
    </AgManage>
  )
}
