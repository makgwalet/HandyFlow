import { useUIStore } from '@/stores/ui-store'
import { useAuth } from '@/hooks/use-auth'
import { cn } from '@/lib/utils'
import {
  LayoutDashboard, Users, Calendar, Stethoscope, FlaskConical,
  CreditCard, Settings, ChevronLeft, ChevronRight, LogOut,
  ClipboardList
} from 'lucide-react'
import { NavLink } from 'react-router-dom'

const navItems = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/patients', label: 'Patients', icon: Users },
  { path: '/schedule', label: 'Schedule', icon: Calendar },
  { path: '/consultations', label: 'Consultations', icon: Stethoscope },
  { path: '/lab', label: 'Lab Inbox', icon: FlaskConical },
  { path: '/billing', label: 'Billing', icon: CreditCard },
  { path: '/waitlist', label: 'Waitlist', icon: ClipboardList },
  { path: '/settings', label: 'Settings', icon: Settings },
]

export function Sidebar() {
  const { sidebarOpen, setSidebarOpen, setCurrentPage } = useUIStore()
  const { user, logout } = useAuth()

  return (
    <aside
      className={cn(
        'fixed left-0 top-0 z-40 flex h-screen flex-col border-r border-medical-border bg-white transition-all duration-300',
        sidebarOpen ? 'w-64' : 'w-16'
      )}
    >
      <div className="flex h-16 items-center justify-between border-b border-medical-border px-4">
        {sidebarOpen && (
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-600 text-white font-bold text-sm">HF</div>
            <span className="text-sm font-semibold text-medical-text">Clinic</span>
          </div>
        )}
        <button onClick={() => setSidebarOpen(!sidebarOpen)} className="rounded-lg p-1.5 text-medical-muted hover:bg-slate-100">
          {sidebarOpen ? <ChevronLeft className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </button>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            onClick={() => setCurrentPage(item.label)}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                isActive ? 'bg-primary-50 text-primary-700' : 'text-medical-muted hover:bg-slate-50 hover:text-medical-text'
              )
            }
          >
            <item.icon className="h-5 w-5 flex-shrink-0" />
            {sidebarOpen && <span>{item.label}</span>}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-medical-border p-3">
        {sidebarOpen && user && (
          <div className="mb-3 px-3">
            <p className="text-sm font-medium text-medical-text">{user.firstName} {user.lastName}</p>
            <p className="text-xs text-medical-muted">{user.role}</p>
          </div>
        )}
        <button onClick={logout} className={cn('flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-medical-muted transition-colors hover:bg-rose-50 hover:text-rose-600', !sidebarOpen && 'justify-center')}>
          <LogOut className="h-5 w-5" />
          {sidebarOpen && <span>Log out</span>}
        </button>
      </div>
    </aside>
  )
}