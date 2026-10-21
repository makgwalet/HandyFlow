// src/pages/customers/CrmPage.tsx — the CRM home: customer register, sales pipeline board and funnel report.
import { Suspense, lazy, useState } from "react"
import { CustomersPage } from "./CustomersPage"
const PipelineBoard = lazy(() => import("./PipelineBoard")), TaskQueue = lazy(() => import("./TaskQueue")), FunnelReport = lazy(() => import("./FunnelReport")), DuplicatesPanel = lazy(() => import("./DuplicatesPanel")), ForecastPanel = lazy(() => import("./ForecastPanel")), TeamPanel = lazy(() => import("./TeamPanel")), CustomFieldsAdmin = lazy(() => import("./CustomFieldsAdmin"))

type Tab = "customers" | "pipeline" | "tasks" | "funnel" | "forecast" | "team" | "duplicates" | "fields"
const TABS: { id: Tab; label: string }[] = [{ id: "customers", label: "Customers" }, { id: "pipeline", label: "Pipeline" }, { id: "tasks", label: "Tasks" }, { id: "funnel", label: "Funnel report" }, { id: "forecast", label: "Forecast" }, { id: "team", label: "Team" }, { id: "duplicates", label: "Duplicates" }, { id: "fields", label: "Fields" }]

export function CrmPage() {
  const [tab, setTab] = useState<Tab>("customers")
  return (
    <div>
      <div role="tablist" aria-label="CRM sections" style={{ display: "flex", gap: 6, padding: "0 4px 8px", borderBottom: "1px solid var(--hf-border-subtle)", marginBottom: 12 }}>
        {TABS.map(t => <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}
          style={{ padding: "6px 14px", borderRadius: 8, border: "none", cursor: "pointer", fontWeight: 700, fontSize: 13, background: tab === t.id ? "var(--hf-info-soft)" : "transparent", color: tab === t.id ? "var(--hf-primary-text)" : "var(--hf-text-muted)" }}>{t.label}</button>)}
      </div>
      <Suspense fallback={<p>Loading…</p>}>
        {tab === "customers" ? <CustomersPage /> : tab === "pipeline" ? <PipelineBoard /> : tab === "tasks" ? <TaskQueue /> : tab === "forecast" ? <ForecastPanel /> : tab === "team" ? <TeamPanel /> : tab === "fields" ? <CustomFieldsAdmin /> : tab === "duplicates" ? <DuplicatesPanel /> : <FunnelReport />}
      </Suspense>
    </div>
  )
}
