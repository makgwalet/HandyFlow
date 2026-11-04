// src/pages/projects/client-portal/ClientPortalLoginPage.tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePortalAuthStore } from '../../../store/portalAuth.store'
import { PM_PORTAL, PM_PORTAL_HOME, pmPortalClient, type AuthResult } from '../../../api/projectClientPortal.client'
import { errorText } from '../../../api/projectDocs'
import { Btn, ErrorLine, Field, inp } from '../tabs/pmKit'
import { AuthCard } from './PortalShell'

export function ClientPortalLoginPage() {
  const navigate = useNavigate()
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true); setErr('')
    try {
      const r = (await pmPortalClient.post(`${PM_PORTAL}/auth/login`, { email, password })).data as AuthResult
      setAuth(r.token, { portalUserId: r.portalUserId, email: r.email, fullName: r.fullName })
      navigate(PM_PORTAL_HOME, { replace: true })
    } catch (e2) { setErr(await errorText(e2, 'Invalid email or password')) } finally { setBusy(false) }
  }
  return (
    <AuthCard title="Your projects" sub="Sign in to follow progress, approve changes and send questions and files to the project team.">
      <form onSubmit={submit}>
        <Field label="Email"><input type="email" required autoFocus autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} style={inp} /></Field>
        <Field label="Password"><input type="password" required autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} style={inp} /></Field>
        {err && <ErrorLine text={err} />}
        <div style={{ display: 'flex', marginTop: 6 }}><span style={{ flex: 1 }} /><Btn type="submit" kind="primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</Btn></div>
      </form>
      <p style={{ fontSize: 12, color: 'var(--hf-text-faint)', marginTop: 18, lineHeight: 1.5 }}>
        You get access by invitation from the company running your project. It is the same login as their other client portals.</p>
    </AuthCard>
  )
}
