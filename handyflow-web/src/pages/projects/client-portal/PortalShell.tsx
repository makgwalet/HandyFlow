// src/pages/projects/client-portal/PortalShell.tsx
// Frame for the Projects client portal: a plain header (who is signed in, sign out) and the toast host, since these
// pages sit outside the staff app shell.
import { Link, useNavigate } from 'react-router-dom'
import { LogOut, HardHat } from 'lucide-react'
import { Toaster } from '../../../components/ui/Toaster'
import { usePortalAuthStore } from '../../../store/portalAuth.store'
import { PM_PORTAL_HOME } from '../../../api/projectClientPortal.client'

export function PortalShell({ children, crumb }: { children: React.ReactNode; crumb?: React.ReactNode }) {
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)
  const navigate = useNavigate()
  return (
    <div style={{ minHeight: '100vh', background: 'var(--hf-canvas, var(--hf-surface-muted))', color: 'var(--hf-text)' }}>
      <style>{'@media (max-width: 600px) { .pmc-wide { display: none } }'}</style>
      <header style={{ background: 'var(--hf-surface)', borderBottom: '1px solid var(--hf-border)' }}>
        <div style={{ maxWidth: 1100, margin: '0 auto', padding: '12px 16px', display: 'flex', alignItems: 'center', gap: 12 }}>
          <Link to={PM_PORTAL_HOME} style={{ display: 'flex', alignItems: 'center', gap: 8, textDecoration: 'none', color: 'var(--hf-text)' }}>
            <span style={{ width: 30, height: 30, borderRadius: 8, background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><HardHat size={16} /></span>
            <b style={{ fontSize: 15, whiteSpace: 'nowrap' }}>Your projects</b>
          </Link>
          {crumb && <span className="pmc-wide" style={{ fontSize: 13, color: 'var(--hf-text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0 }}>/ {crumb}</span>}
          <span style={{ flex: 1 }} />
          {user && <span className="pmc-wide" style={{ fontSize: 12, color: 'var(--hf-text-muted)', whiteSpace: 'nowrap' }}>{user.fullName}</span>}
          <button onClick={() => { logout(); navigate(`${PM_PORTAL_HOME}/login`, { replace: true }) }}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: 'none', border: '1px solid var(--hf-border)', borderRadius: 8, padding: '5px 10px', fontSize: 12, cursor: 'pointer', color: 'var(--hf-text-muted)', whiteSpace: 'nowrap' }}>
            <LogOut size={13} /> Sign out</button>
        </div>
      </header>
      <main style={{ maxWidth: 1100, margin: '0 auto', padding: '20px 16px 48px' }}>{children}</main>
      <Toaster />
    </div>
  )
}

/** The centred card used by sign-in and accept-invite. */
export function AuthCard({ title, sub, children }: { title: string; sub?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16, background: 'var(--hf-canvas, var(--hf-surface-muted))' }}>
      <div style={{ width: 420, maxWidth: '100%', background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 14, padding: 28, boxShadow: 'var(--hf-shadow-lg, 0 20px 50px rgba(0,0,0,.12))' }}>
        <div style={{ textAlign: 'center', marginBottom: 20 }}>
          <div style={{ width: 44, height: 44, borderRadius: 10, background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 12px' }}><HardHat size={22} /></div>
          <h1 style={{ fontSize: 19, fontWeight: 800, margin: 0, color: 'var(--hf-text)' }}>{title}</h1>
          {sub && <p style={{ fontSize: 13, color: 'var(--hf-text-muted)', margin: '6px 0 0', lineHeight: 1.5 }}>{sub}</p>}
        </div>
        {children}
      </div>
      <Toaster />
    </div>
  )
}
