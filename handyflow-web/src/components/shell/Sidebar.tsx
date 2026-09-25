// src/components/shell/Sidebar.tsx
import { useState } from 'react'
import { NavLink, Link } from 'react-router-dom'
import { ArrowLeft, Building2, LayoutGrid, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import type { ModuleNavItem } from '../../navigation/modules'
import { WORKSPACE_NAV } from '../../navigation/modules'
import { sectionsForPath, visibleGroups, type ModuleSections } from '../../navigation/moduleSections'
import { useAuthStore } from '../../store/auth.store'

interface SidebarProps {
  modules: ModuleNavItem[]
  pinnedKeys: string[]
  pathname: string
  mini: boolean
  /** Off-canvas state on small screens. */
  mobileOpen: boolean
  onNavigate: () => void
  onToggleMini: () => void
}

interface NavEntry { key: string; icon: ModuleNavItem['icon']; label: string; route: string; badge?: string }

function NavSection({ label, items, onNavigate }: { label: string; items: NavEntry[]; onNavigate: () => void }) {
  if (items.length === 0) return null
  return (
    <div className="hf-nav-section" role="group" aria-label={label}>
      <div className="hf-nav-section-label" aria-hidden="true">{label}</div>
      {items.map(({ key, icon: Icon, label: text, route, badge }) => (
        <NavLink key={key} to={route} title={text} onClick={onNavigate}
          className={({ isActive }) => 'hf-nav-item' + (isActive ? ' active' : '')}>
          <Icon size={18} aria-hidden="true" />
          <span className="hf-nav-label">{text}</span>
          {badge && <span className="hf-nav-badge">{badge}</span>}
        </NavLink>
      ))}
    </div>
  )
}

/**
 * Global navigation: Home, the user's pinned modules, every other
 * subscribed module, then workspace links. Pinned modules are listed once
 * (in the Pinned group) rather than twice.
 *
 * Context mode: inside a module that registers sections (see
 * navigation/moduleSections.ts), the list is replaced by that module's
 * grouped sections. "All modules" flips back to the global list without
 * navigating away, so users can jump to another module from anywhere.
 */
export function Sidebar({ modules, pinnedKeys, pathname, mini, mobileOpen, onNavigate, onToggleMini }: SidebarProps) {
  // Which module the user asked to see the global list for. Stored by key so
  // entering a different module shows its sections again automatically.
  const [globalFor, setGlobalFor] = useState<string | null>(null)
  const config = sectionsForPath(pathname)
  // Only offer context mode for modules the tenant can actually open.
  const context = config && modules.some(m => m.key === config.moduleKey) ? config : undefined
  const showContext = !!context && globalFor !== context.moduleKey

  const byKey = new Map(modules.map(m => [m.key, m]))
  const pinned = pinnedKeys.map(k => byKey.get(k)).filter((m): m is ModuleNavItem => !!m)
  const pinnedSet = new Set(pinned.map(m => m.key))
  const others = modules.filter(m => !pinnedSet.has(m.key)).sort((a, b) => a.label.localeCompare(b.label))
  const workspace = WORKSPACE_NAV.map(w => ({ ...w, key: w.route }))

  return (
    <aside className={'hf-sidebar' + (mobileOpen ? ' is-open' : '')} aria-label="Main navigation">
      <Link to="/dashboard" className="hf-sidebar-brand" title="HandyFlow home" onClick={onNavigate}>
        <span className="hf-sidebar-logo"><Building2 size={16} strokeWidth={2.4} aria-hidden="true" /></span>
        <span className="hf-sidebar-brand-name">HandyFlow</span>
      </Link>

      <nav className="hf-sidebar-nav">
        {showContext && context ? (
          <ContextNav config={context} onNavigate={onNavigate} onShowAll={() => setGlobalFor(context.moduleKey)} />
        ) : (<>
        {context && (
          <button type="button" className="hf-nav-item hf-nav-button hf-nav-return" onClick={() => setGlobalFor(null)}
            title={`Back to ${context.title}`}>
            <context.icon size={18} aria-hidden="true" />
            <span className="hf-nav-label">Back to {context.title}</span>
          </button>
        )}
        <NavSection label="Home" onNavigate={onNavigate}
          items={[{ key: 'home', icon: LayoutGrid, label: 'All modules', route: '/dashboard' }]} />
        <NavSection label="Pinned" items={pinned} onNavigate={onNavigate} />
        <NavSection label={pinned.length ? 'More modules' : 'Modules'} items={others} onNavigate={onNavigate} />
        <NavSection label="Workspace" items={workspace} onNavigate={onNavigate} />
        </>)}
      </nav>

      <div className="hf-sidebar-footer">
        <button type="button" className="hf-collapse-btn" onClick={onToggleMini}
          aria-label={mini ? 'Expand sidebar' : 'Collapse sidebar'} title={mini ? 'Expand sidebar' : 'Collapse sidebar'}>
          {mini ? <PanelLeftOpen size={18} aria-hidden="true" /> : <PanelLeftClose size={18} aria-hidden="true" />}
          <span className="hf-nav-label">Collapse</span>
        </button>
      </div>
    </aside>
  )
}

const NO_PERMISSIONS: string[] = []

function ContextNav({ config, onNavigate, onShowAll }: {
  config: ModuleSections; onNavigate: () => void; onShowAll: () => void
}) {
  const permissions = useAuthStore(s => s.user?.permissions ?? NO_PERMISSIONS)
  const Icon = config.icon
  return (
    <>
      <button type="button" className="hf-nav-item hf-nav-button" onClick={onShowAll} title="All modules">
        <ArrowLeft size={18} aria-hidden="true" />
        <span className="hf-nav-label">All modules</span>
      </button>
      <div className="hf-nav-context-title" title={config.title}>
        <span className="hf-nav-context-icon"><Icon size={16} aria-hidden="true" /></span>
        <span className="hf-nav-label">{config.title}</span>
      </div>
      {visibleGroups(config, permissions).map(group => (
        <NavSection key={group.label} label={group.label} onNavigate={onNavigate}
          items={group.sections.map(sec => ({
            key: sec.id, icon: sec.icon, label: sec.label, badge: sec.badge,
            route: `${config.basePath}/${sec.id}`,
          }))} />
      ))}
    </>
  )
}
