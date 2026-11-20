// src/pages/legalcompliance/EcosystemTab.tsx
//
// "Across HandyFlow": compliance counts that other modules (Fleet, Fuel, ...) publish. Endpoint: GET /api/v1/legalcompliance/ecosystem.
// These are the other modules' own figures, shown here and not re-counted or blended into any score. Each row links back to the module that owns the data.
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { one } from "./lcHelpers"
import { Err, Badge } from "./LcUi"
import { ChevronRight } from "lucide-react"

interface Signal { key: string; label: string; count: number; severity: "CRITICAL" | "WARNING" | "INFO"; actionUrl: string | null }
interface ModuleView { moduleKey: string; moduleName: string; status: "OK" | "UNAVAILABLE"; signals: Signal[] }
interface Ecosystem { generatedAt: string; modules: ModuleView[]; critical: number; warnings: number }

const tone = { CRITICAL: { c: "#991B1B", b: "#FEE2E2", t: "Critical" }, WARNING: { c: "#92400E", b: "#FEF3C7", t: "Warning" }, INFO: { c: "#475569", b: "#E2E8F0", t: "Note" } } as const

export default function EcosystemTab() {
  const navigate = useNavigate()
  const { data, isLoading, isError } = useQuery<Ecosystem>({ queryKey: ["lc-ecosystem"], queryFn: async () => one(await apiClient.get("/api/v1/legalcompliance/ecosystem")) })
  if (isLoading) return <p style={{ color: "#64748B" }}>Loading…</p>
  if (isError || !data) return <Err m="Could not load the cross-module view" />
  return (
    <div>
      <p style={{ fontSize: 12, color: "#64748B", marginTop: 0, maxWidth: 720 }}>
        What the other HandyFlow modules you use report about their own compliance. These are counts from those modules, as of {new Date(data.generatedAt).toLocaleString("en-ZA")}; they are not added to the indicators on the executive overview.
        Open a row to deal with it where the data lives.
      </p>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        {[["Critical", data.critical], ["Warnings", data.warnings]].map(([l, v]) => (
          <div key={String(l)} style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "8px 14px" }}><div style={{ fontSize: 11, color: "#64748B" }}>{l}</div><div style={{ fontSize: 18, fontWeight: 700, color: "#0F172A" }}>{v}</div></div>
        ))}
      </div>
      {data.modules.length === 0 && <p style={{ fontSize: 13, color: "#64748B" }}>None of the modules you use currently reports compliance figures here.</p>}
      {data.modules.map(m => (
        <div key={m.moduleKey} style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 700, color: "#0F172A", marginBottom: 6 }}>{m.moduleName}</div>
          {m.status === "UNAVAILABLE" ? <p style={{ fontSize: 13, color: "#92400E" }}>This module's figures could not be loaded just now. Try again shortly.</p>
            : m.signals.length === 0 ? <p style={{ fontSize: 13, color: "#166534" }}>Nothing flagged.</p>
            : m.signals.map(s => (
              <button key={s.key} disabled={!s.actionUrl} onClick={() => s.actionUrl && navigate(s.actionUrl)} style={{ all: "unset", cursor: s.actionUrl ? "pointer" : "default", display: "flex", alignItems: "center", gap: 8, padding: "8px 12px", border: "1px solid #E2E8F0", borderRadius: 8, marginBottom: 4, width: "calc(100% - 24px)", background: "#fff" }}>
                <Badge text={tone[s.severity].t} color={tone[s.severity].c} bg={tone[s.severity].b} /><span style={{ flex: 1, fontSize: 13, color: "#0F172A" }}>{s.count} {s.label}</span>{s.actionUrl && <ChevronRight size={14} color="#94A3B8" />}
              </button>
            ))}
        </div>
      ))}
    </div>
  )
}
