// src/components/shell/Sidebar.tsx
import { NavLink, Link } from 'react-router-dom'
import { Building2, LayoutGrid, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import type { ModuleNavItem } from '../../navigation/modules'
import { WORKSPACE_NAV } from '../../navigation/modules'

interface SidebarProps {
  modules: ModuleNavItem[]
  pinnedKeys: string[]
  mini: boolean
  /** Off-canvas state on small screens. */
  mobileOpen: boolean
  onNavigate: () => void
  onToggleMini: () => void
}

interface NavEntry { key: string; icon: ModuleNavItem['icon']; label: string; route: string }

function NavSection({ label, items, onNavigate }: { label: string; items: NavEntry[]; onNavigate: () => void }) {
  if (items.length === 0) return null
  return (
    <div className="hf-nav-section" role="group" aria-label={label}>
      <div className="hf-nav-section-label" aria-hidden="true">{label}</div>
      {items.map(({ key, icon: Icon, label: text, route }) => (
        <NavLink key={key} to={route} title={text} onClick={onNavigate}
          className={({ isActive }) => 'hf-nav-item' + (isActive ? ' active' : '')}>
          <Icon size={18} aria-hidden="true" />
          <span className="hf-nav-label">{text}</span>
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
 * Phase 2 adds a context mode: inside a module that registers sections
 * (starting with Security), this list is replaced by that module's grouped
 * sections plus a "back to all modules" link.
 */
export function Sidebar({ modules, pinnedKeys, mini, mobileOpen, onNavigate, onToggleMini }: SidebarProps) {
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
        <NavSection label="Home" onNavigate={onNavigate}
          items={[{ key: 'home', icon: LayoutGrid, label: 'All modules', route: '/dashboard' }]} />
        <NavSection label="Pinned" items={pinned} onNavigate={onNavigate} />
        <NavSection label={pinned.length ? 'More modules' : 'Modules'} items={others} onNavigate={onNavigate} />
        <NavSection label="Workspace" items={workspace} onNavigate={onNavigate} />
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
