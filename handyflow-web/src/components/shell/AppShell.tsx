// src/components/shell/AppShell.tsx
//
// Authenticated staff shell: sidebar + top bar + content. Replaces the old
// ModuleLayout top-nav. Behaviour carried over unchanged:
//   subscribed-module list, pinning (server-backed, with one-time migration
//   of legacy localStorage pins), module switcher, Quotes/Billing/Settings,
//   notification bell + drawer, profile menu, sign out.
import { useCallback, useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bell, Eye, Menu, SlidersHorizontal } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useTheme } from '../../theme/ThemeContext'
import { useSubscribedModules } from '../../navigation/modules'
import { ModuleSwitcher } from '../layout/ModuleSwitcher'
import { NotificationDrawer } from '../layout/NotificationDrawer'
import { Sidebar } from './Sidebar'
import { ProfileMenu } from './ProfileMenu'
import { CustomizerDrawer } from './CustomizerDrawer'
import './shell.css'

const LEGACY_PINNED_KEY = 'handyflow-pinned-modules'
const MAX_PINNED = 12

export function AppShell() {
  const location = useLocation()
  const { prefs, isLoaded, isSupportSession, user: uiUser, updateMine } = useTheme()
  const { modules } = useSubscribedModules()
  // The off-canvas nav remembers the path it was opened on, so navigating
  // anywhere closes it without an effect.
  const [mobileNavPath, setMobileNavPath] = useState<string | null>(null)
  const mobileNavOpen = mobileNavPath === location.pathname
  const [customizerOpen, setCustomizerOpen] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)

  const { data: unreadData } = useQuery<{ unreadCount: number }>({
    queryKey: ['notifications-unread-count'],
    queryFn: async () => (await apiClient.get('/api/v1/notifications/unread-count')).data,
    refetchInterval: 60_000,
  })
  const unreadCount = unreadData?.unreadCount ?? 0

  // One-time migration of browser-only pins to the server. Never overwrites
  // pins the server already has.
  useEffect(() => {
    if (!isLoaded || isSupportSession) return
    let legacy: unknown
    try {
      const raw = localStorage.getItem(LEGACY_PINNED_KEY)
      legacy = raw ? JSON.parse(raw) : []
    } catch { return }
    if (!Array.isArray(legacy) || legacy.length === 0) return
    if ((uiUser?.pinnedModules.length ?? 0) === 0) {
      updateMine({ pinnedModules: legacy.filter((k): k is string => typeof k === 'string').slice(0, MAX_PINNED) })
    }
    try { localStorage.removeItem(LEGACY_PINNED_KEY) } catch { /* ignore */ }
  }, [isLoaded, isSupportSession, uiUser, updateMine])

  const togglePin = useCallback((key: string) => {
    const pinned = prefs.pinnedModules
    const next = pinned.includes(key) ? pinned.filter(k => k !== key) : [...pinned, key]
    updateMine({ pinnedModules: next.slice(0, MAX_PINNED) })
  }, [prefs.pinnedModules, updateMine])

  const mini = prefs.sidebar === 'MINI'
  const closeMobileNav = useCallback(() => setMobileNavPath(null), [])
  const closeCustomizer = useCallback(() => setCustomizerOpen(false), [])

  return (
    <div className="hf-shell">
      <Sidebar modules={modules} pinnedKeys={prefs.pinnedModules} mini={mini}
        mobileOpen={mobileNavOpen} onNavigate={closeMobileNav}
        onToggleMini={() => updateMine({ sidebar: mini ? 'FULL' : 'MINI' })} />
      {mobileNavOpen && <div className="hf-backdrop" onClick={closeMobileNav} aria-hidden="true" />}

      <div className="hf-main">
        <header className="hf-topbar">
          <button type="button" className="hf-icon-btn hf-mobile-only" aria-label="Open navigation"
            aria-expanded={mobileNavOpen} onClick={() => setMobileNavPath(location.pathname)}>
            <Menu size={18} aria-hidden="true" />
          </button>

          <ModuleSwitcher modules={modules} pinnedKeys={prefs.pinnedModules}
            onTogglePin={togglePin} currentPath={location.pathname} />

          <div className="hf-topbar-spacer" />

          <button type="button" className="hf-icon-btn" aria-label="Appearance settings" title="Appearance"
            aria-expanded={customizerOpen} onClick={() => setCustomizerOpen(true)}>
            <SlidersHorizontal size={18} aria-hidden="true" />
          </button>
          <button type="button" className="hf-icon-btn" title="Notifications"
            aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'}
            aria-expanded={notifOpen} onClick={() => setNotifOpen(true)}>
            <Bell size={18} aria-hidden="true" />
            {unreadCount > 0 && <span className="hf-badge-count" aria-hidden="true">{unreadCount > 99 ? '99+' : unreadCount}</span>}
          </button>
          <ProfileMenu />
        </header>

        {isSupportSession && (
          <div className="hf-support-banner" role="status">
            <Eye size={14} aria-hidden="true" /> Read-only support session. Changes are not saved.
          </div>
        )}

        <main className="hf-content" id="main-content">
          <div className="hf-content-inner">
            <Outlet />
          </div>
        </main>
      </div>

      <NotificationDrawer open={notifOpen} onClose={() => setNotifOpen(false)} />
      <CustomizerDrawer open={customizerOpen} onClose={closeCustomizer} />
    </div>
  )
}
