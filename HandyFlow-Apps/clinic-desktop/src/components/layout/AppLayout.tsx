// src/components/layout/AppLayout.tsx
import { useState, useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useSyncStore } from '@/stores/sync-store'
import { cn } from '@/lib/utils'

const navItems = [
  { id: 'dashboard', label: 'Dashboard', icon: HomeIcon, path: '/' },
  { id: 'patients', label: 'Patients', icon: UsersIcon, path: '/patients' },
  { id: 'schedule', label: 'Schedule', icon: CalendarIcon, path: '/schedule' },
  { id: 'consultations', label: 'Consultations', icon: HeartIcon, path: '/consultations' },
  { id: 'lab', label: 'Lab Inbox', icon: FlaskIcon, path: '/lab', badge: 'labCount' },
  { id: 'billing', label: 'Billing', icon: BanknoteIcon, path: '/billing' },
]

export function AppLayout({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [outboxOpen, setOutboxOpen] = useState(false)
  const { status, lastSyncAt, pendingCount, labCount } = useSyncStore()

  const syncConfig = {
    online: { bg: 'bg-emerald-50', text: 'text-emerald-700', border: 'border-emerald-200', dot: 'bg-emerald-500', label: 'Online & synced' },
    syncing: { bg: 'bg-blue-50', text: 'text-blue-700', border: 'border-blue-200', dot: 'bg-blue-500', label: 'Syncing...' },
    offline: { bg: 'bg-slate-100', text: 'text-slate-600', border: 'border-slate-200', dot: 'bg-slate-400', label: `Offline · ${pendingCount} queued` },
    conflict: { bg: 'bg-rose-50', text: 'text-rose-700', border: 'border-rose-200', dot: 'bg-rose-500', label: 'Conflicts detected' },
  }
  const s = syncConfig[status]

  return (
    <div className="flex h-screen w-full bg-slate-50 text-slate-800 overflow-hidden">
      {/* Sidebar */}
      <aside className={cn(
        "bg-white border-r border-slate-200 flex flex-col justify-between shrink-0 transition-all duration-300",
        sidebarOpen ? "w-56" : "w-16"
      )}>
        <div>
          <div className="h-16 flex items-center px-5 border-b border-slate-100">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-teal-600 text-white shrink-0">
              <ZapIcon className="h-5 w-5" />
            </div>
            {sidebarOpen && <span className="ml-3 font-semibold text-slate-800 tracking-tight">HandyFlow</span>}
          </div>
          <nav className="mt-4 px-3 space-y-1">
            {navItems.map((item) => {
              const active = location.pathname.startsWith(item.path)
              return (
                <Link
                  key={item.id}
                  to={item.path}
                  className={cn(
                    "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors relative",
                    active ? "bg-teal-50 text-teal-700" : "text-slate-500 hover:bg-slate-50 hover:text-slate-700"
                  )}
                >
                  <item.icon className="h-5 w-5 shrink-0" />
                  {sidebarOpen && <span>{item.label}</span>}
                  {sidebarOpen && item.badge && labCount > 0 && (
                    <span className="ml-auto h-5 min-w-[1.25rem] flex items-center justify-center rounded-full bg-rose-500 text-[10px] font-bold text-white px-1.5">
                      {labCount}
                    </span>
                  )}
                </Link>
              )
            })}
          </nav>
        </div>
        <div className="p-3 border-t border-slate-100">
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="w-full flex items-center justify-center lg:justify-start gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-slate-500 hover:bg-slate-50 transition-colors"
          >
            {sidebarOpen ? <PanelLeftClose className="h-5 w-5" /> : <PanelLeftOpen className="h-5 w-5" />}
            {sidebarOpen && <span>Collapse</span>}
          </button>
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 flex flex-col min-w-0">
        {/* Top Bar */}
        <header className="h-16 bg-white border-b border-slate-200 flex items-center justify-between px-6 shrink-0">
          <div className="flex items-center gap-4">
            <h1 className="text-lg font-semibold text-slate-800">
              {navItems.find(n => location.pathname.startsWith(n.path))?.label ?? 'Dashboard'}
            </h1>
            <span className="text-sm text-slate-400">
              {new Date().toLocaleDateString('en-ZA', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
            </span>
          </div>
          <div className="flex items-center gap-4">
            <button
              onClick={() => setOutboxOpen(true)}
              className={cn(
                "flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium border transition-all hover:brightness-95",
                s.bg, s.text, s.border
              )}
            >
              <span className="relative flex h-2 w-2">
                {status === 'syncing' && (
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75" />
                )}
                <span className={cn("relative inline-flex rounded-full h-2 w-2", s.dot)} />
              </span>
              <span>{s.label}</span>
              {lastSyncAt && status === 'online' && (
                <span className="opacity-60">· {formatRelativeTime(lastSyncAt)}</span>
              )}
            </button>
            <div className="h-8 w-px bg-slate-200" />
            <div className="flex items-center gap-3">
              <div className="text-right hidden sm:block">
                <p className="text-sm font-medium text-slate-700">Dr. Sarah Smith</p>
                <p className="text-xs text-slate-400">General Practitioner</p>
              </div>
              <div className="h-9 w-9 rounded-full bg-teal-100 flex items-center justify-center text-sm font-bold text-teal-700">
                SS
              </div>
            </div>
          </div>
        </header>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {children}
        </div>
      </main>

      {/* Outbox Slide-over */}
      <OutboxPanel open={outboxOpen} onClose={() => setOutboxOpen(false)} />
    </div>
  )
}