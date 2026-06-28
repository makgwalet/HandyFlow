// src/pages/projects/ProjectsPage.tsx
// Single-page module — matches ClinicPage pattern exactly
import { useState } from "react"
import { HardHat, LayoutDashboard, FolderOpen } from "lucide-react"
import { ProjectDashboard } from "./ProjectDashboard"
import { ProjectListTab }   from "./ProjectListTab"
import { ProjectDetailTab } from "./ProjectDetailTab"

export type ProjectsView =
  | { screen: "dashboard" }
  | { screen: "list" }
  | { screen: "detail"; projectId: string }

const ACCENT = "#1B3A6B"

export function ProjectsPage() {
  const [view, setView] = useState<ProjectsView>({ screen: "dashboard" })

  type Tab = "dashboard" | "projects"
  const tab: Tab = view.screen === "dashboard" ? "dashboard" : "projects"

  const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
    { id: "dashboard", label: "Dashboard", icon: LayoutDashboard },
    { id: "projects",  label: "Projects",  icon: FolderOpen      },
  ]

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Page header — matches ClinicPage exactly */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: ACCENT,
            display: "flex", alignItems: "center", justifyContent: "center" }}>
            <HardHat size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Projects</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Gantt · Resources · Budget · Risk register · Site diaries
        </p>
      </div>

      {/* Card */}
      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        {/* Tab bar */}
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0",
          marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon   = t.icon
            const active = tab === t.id
            return (
              <button key={t.id}
                onClick={() => setView(t.id === "dashboard" ? { screen: "dashboard" } : { screen: "list" })}
                style={{
                  display: "flex", alignItems: "center", gap: 6,
                  padding: "10px 16px", background: "none", border: "none",
                  whiteSpace: "nowrap",
                  borderBottom: active ? `2px solid ${ACCENT}` : "2px solid transparent",
                  color:      active ? ACCENT : "#64748B",
                  fontWeight: active ? 600 : 400,
                  fontSize: 13, cursor: "pointer", marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {/* Content */}
        {view.screen === "dashboard" && (
          <ProjectDashboard onOpen={(id) => setView({ screen: "detail", projectId: id })}
                            onList={() => setView({ screen: "list" })} />
        )}
        {view.screen === "list" && (
          <ProjectListTab onOpen={(id) => setView({ screen: "detail", projectId: id })} />
        )}
        {view.screen === "detail" && (
          <ProjectDetailTab projectId={view.projectId}
                            onBack={() => setView({ screen: "list" })} />
        )}
      </div>
    </div>
  )
}
