// src/components/shell/SectionedModulePage.tsx
//
// Shared page frame for modules whose sections are routes
// (`${basePath}/:section`, registered in navigation/moduleSections.ts):
//   - unknown, missing or forbidden section -> redirect to the default
//   - compact PageHeader with Module > Group > Section breadcrumbs
//   - section content in a surface panel, remounted per section so each one
//     starts fresh, as it did when it was a conditionally rendered tab
//
// `render` receives `goTo(sectionId)` so dashboards can link to sections.
import type { ReactNode } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../ui/PageHeader'
import { findSection, type ModuleSections } from '../../navigation/moduleSections'
import { useAuthStore } from '../../store/auth.store'

const NO_PERMISSIONS: string[] = []

interface SectionedModulePageProps {
  config: ModuleSections
  render: (sectionId: string, goTo: (sectionId: string) => void) => ReactNode
  /** Right-aligned header actions shown on every section (e.g. settings). */
  action?: ReactNode
  /** Line under the section title, e.g. a firm name. */
  subtitle?: ReactNode
  /** Content between the header and the section panel on every section
   *  (e.g. a setup warning or a KPI strip). */
  banner?: ReactNode
  /** Rendered after the panel, e.g. modals owned by the page. */
  children?: ReactNode
}

export function SectionedModulePage({ config, render, action, subtitle, banner, children }: SectionedModulePageProps) {
  const { section: rawSection } = useParams<{ section?: string }>()
  const navigate = useNavigate()
  const permissions = useAuthStore(s => s.user?.permissions ?? NO_PERMISSIONS)
  const base = config.basePath

  const found = findSection(config, rawSection, permissions)
  if (!found) return <Navigate replace to={`${base}/${config.defaultSection}`} />
  const { section, group } = found
  const goTo = (id: string) => navigate(`${base}/${id}`)

  return (
    <div>
      <PageHeader
        title={section.label}
        subtitle={subtitle}
        icon={section.icon}
        breadcrumbs={[
          { label: config.title, to: `${base}/${config.defaultSection}` },
          { label: group.label },
          { label: section.label },
        ]}
        action={action}
      />
      {banner}
      <div key={section.id} style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 14, padding: 24 }}>
        {render(section.id, goTo)}
      </div>
      {children}
    </div>
  )
}
