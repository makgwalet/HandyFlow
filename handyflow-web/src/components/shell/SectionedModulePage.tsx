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
}

export function SectionedModulePage({ config, render }: SectionedModulePageProps) {
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
        icon={section.icon}
        breadcrumbs={[
          { label: config.title, to: `${base}/${config.defaultSection}` },
          { label: group.label },
          { label: section.label },
        ]}
      />
      <div key={section.id} style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 14, padding: 24 }}>
        {render(section.id, goTo)}
      </div>
    </div>
  )
}
