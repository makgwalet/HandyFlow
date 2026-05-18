// src/components/layout/Sidebar.tsx

import { NavLink } from 'react-router-dom'
import { cn } from '../../lib/utils'
import {
  LayoutDashboard, Users, Package, FileText,
  CreditCard, Settings, LogOut, Building2
} from 'lucide-react'
import { useAuthStore } from '../../store/auth.store'

const navItems = [
  { to: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/customers',  icon: Users,           label: 'Customers' },
  { to: '/catalogue',  icon: Package,          label: 'Catalogue' },
  { to: '/quotes',     icon: FileText,         label: 'Quotes' },
  { to: '/billing',    icon: CreditCard,       label: 'Billing' },
]

export function Sidebar() {
  const { user, logout } = useAuthStore()

  return (
    <aside className="w-64 bg-[#1B3A6B] min-h-screen flex flex-col">
      {/* Logo */}
      <div className="px-6 py-5 border-b border-blue-800">
        <div className="flex items-center gap-2">
          <Building2 className="w-7 h-7 text-[#0D9488]" />
          <span className="text-white font-bold text-xl">HandyFlow</span>
        </div>
        {user && (
          <p className="mt-1 text-blue-300 text-xs truncate">
            {user.firstName} {user.lastName}
          </p>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-1">
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => cn(
              'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
              isActive
                ? 'bg-blue-800 text-white'
                : 'text-blue-200 hover:bg-blue-800/50 hover:text-white'
            )}
          >
            <Icon className="w-4 h-4" />
            {label}
          </NavLink>
        ))}
      </nav>

      {/* Footer */}
      <div className="px-3 py-4 border-t border-blue-800 space-y-1">
        <NavLink
          to="/settings"
          className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-blue-200 hover:bg-blue-800/50 hover:text-white transition-colors"
        >
          <Settings className="w-4 h-4" />
          Settings
        </NavLink>
        <button
          onClick={logout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-blue-200 hover:bg-red-800/50 hover:text-red-200 transition-colors"
        >
          <LogOut className="w-4 h-4" />
          Sign out
        </button>
      </div>
    </aside>
  )
}