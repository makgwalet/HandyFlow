// src/components/shell/ProfileMenu.tsx
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, CreditCard, Lock, LogOut, Settings, User } from 'lucide-react'
import { useAuthStore } from '../../store/auth.store'

/** Same items and behaviour as the previous ModuleLayout profile dropdown. */
export function ProfileMenu() {
  const navigate = useNavigate()
  const { user, logout } = useAuthStore()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onClick); document.removeEventListener('keydown', onKey) }
  }, [open])

  const go = (path: string) => { setOpen(false); navigate(path) }
  const initials = `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase() || '?'

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button type="button" className="hf-profile-btn" aria-haspopup="menu" aria-expanded={open}
        onClick={() => setOpen(o => !o)}>
        <span className="hf-avatar" aria-hidden="true">{initials}</span>
        <span className="hf-profile-name">{user?.firstName}</span>
        <ChevronDown size={14} aria-hidden="true" style={{ color: 'var(--hf-text-faint)' }} />
      </button>

      {open && (
        <div className="hf-menu" role="menu">
          <div className="hf-menu-header">
            <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--hf-text)' }}>{user?.firstName} {user?.lastName}</div>
            <div style={{ fontSize: 12, color: 'var(--hf-text-faint)', marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis' }}>{user?.email}</div>
          </div>
          <button type="button" role="menuitem" className="hf-menu-item" onClick={() => go('/profile')}><User size={15} aria-hidden="true" />Update profile</button>
          <button type="button" role="menuitem" className="hf-menu-item" onClick={() => go('/profile')}><Lock size={15} aria-hidden="true" />Change password</button>
          <button type="button" role="menuitem" className="hf-menu-item" onClick={() => go('/billing')}><CreditCard size={15} aria-hidden="true" />Billing & plan</button>
          <button type="button" role="menuitem" className="hf-menu-item" onClick={() => go('/settings')}><Settings size={15} aria-hidden="true" />Settings</button>
          <div className="hf-menu-divider" />
          <button type="button" role="menuitem" className="hf-menu-item danger"
            onClick={() => { setOpen(false); logout(); navigate('/login') }}>
            <LogOut size={15} aria-hidden="true" />Sign out
          </button>
        </div>
      )}
    </div>
  )
}
