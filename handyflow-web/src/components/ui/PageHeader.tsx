// src/components/ui/PageHeader.tsx
//
// Compact page header: optional breadcrumbs, title, one-line subtitle and a
// right-aligned action area. Deliberately small (about 64px) so data-heavy
// pages keep their vertical space; see UI-MODERNIZATION-PROGRESS.md for why
// the template's large illustrated banner was not adopted.
//
// Backwards compatible with the previous props (title, subtitle, action).
import type { ElementType, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'

export interface Breadcrumb { label: string; to?: string }

interface PageHeaderProps {
  title: string
  subtitle?: ReactNode
  /** Primary and secondary actions, rendered right-aligned. */
  action?: ReactNode
  breadcrumbs?: Breadcrumb[]
  icon?: ElementType
}

export function PageHeader({ title, subtitle, action, breadcrumbs, icon: Icon }: PageHeaderProps) {
  return (
    <header style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap', marginBottom: 20 }}>
      <div style={{ minWidth: 0 }}>
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav aria-label="Breadcrumb">
            <ol style={{ display: 'flex', alignItems: 'center', gap: 4, listStyle: 'none', margin: '0 0 6px', padding: 0, fontSize: 12.5, color: 'var(--hf-text-muted)' }}>
              {breadcrumbs.map((b, i) => {
                const last = i === breadcrumbs.length - 1
                return (
                  <li key={`${b.label}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    {b.to && !last
                      ? <Link to={b.to} style={{ color: 'inherit', textDecoration: 'none' }}>{b.label}</Link>
                      : <span aria-current={last ? 'page' : undefined} style={last ? { color: 'var(--hf-text-secondary)' } : undefined}>{b.label}</span>}
                    {!last && <ChevronRight size={13} aria-hidden="true" style={{ color: 'var(--hf-text-faint)' }} />}
                  </li>
                )
              })}
            </ol>
          </nav>
        )}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {Icon && (
            <span aria-hidden="true" style={{ width: 34, height: 34, borderRadius: 'var(--hf-radius-sm)', display: 'grid', placeItems: 'center', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', flexShrink: 0 }}>
              <Icon size={17} />
            </span>
          )}
          <h1 style={{ margin: 0, fontSize: 22, lineHeight: 1.25, fontWeight: 700, color: 'var(--hf-text)' }}>{title}</h1>
        </div>
        {subtitle && <p style={{ margin: '4px 0 0', fontSize: 13.5, color: 'var(--hf-text-muted)' }}>{subtitle}</p>}
      </div>
      {action && <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>{action}</div>}
    </header>
  )
}
