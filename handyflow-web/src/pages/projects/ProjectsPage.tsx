// src/pages/projects/ProjectsPage.tsx
//
// Sections are routes (/projects/:section) with navigation in the sidebar (see PROJECTS_SECTIONS in
// navigation/moduleSections.ts and components/shell/SectionedModulePage). An open project is
// /projects/projects?project=<id>, so it can be bookmarked and survives a refresh.
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { PROJECTS_SECTIONS, findSection } from "../../navigation/moduleSections"
import { ProjectDashboard } from "./ProjectDashboard"
import { ProjectListTab }   from "./ProjectListTab"
import { ProjectDetailTab } from "./ProjectDetailTab"

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function ProjectsPage() {
  const { section } = useParams<{ section?: string }>()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const base = PROJECTS_SECTIONS.basePath
  const projectId = params.get("project")

  // /projects/<id> used to be a separate detail page. Keep those links working.
  if (section && UUID.test(section) && !findSection(PROJECTS_SECTIONS, section)) {
    return <Navigate replace to={`${base}/projects?project=${section}`} />
  }

  const openProject = (id: string) => navigate(`${base}/projects?project=${id}`)
  const closeProject = () => navigate(`${base}/projects`)

  return (
    <SectionedModulePage config={PROJECTS_SECTIONS}
      subtitle="Gantt · Resources · Budget · Risk register · Site diaries"
      detail={section === "projects" && projectId ? { label: "Project", subtitle: "Project details" } : undefined}
      render={(id, goTo) => {
        switch (id) {
          case "dashboard": return <ProjectDashboard onOpen={openProject} onList={() => goTo("projects")} />
          case "projects":  return projectId
            ? <ProjectDetailTab projectId={projectId} onBack={closeProject} />
            : <ProjectListTab onOpen={openProject} />
          default:          return null
        }
      }} />
  )
}
