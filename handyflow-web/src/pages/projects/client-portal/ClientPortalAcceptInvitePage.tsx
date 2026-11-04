// src/pages/projects/client-portal/ClientPortalAcceptInvitePage.tsx
// Opened from the invite email. Shows who the invite is for; a new person chooses a password, someone who already has
// a portal login enters its password instead (the server insists, so an invite cannot be attached to another account).
import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { usePortalAuthStore } from '../../../store/portalAuth.store'
import { PM_PORTAL, PM_PORTAL_HOME, pmPortalClient, type AuthResult, type InviteInfo } from '../../../api/projectClientPortal.client'
import { errorText } from '../../../api/projectDocs'
import { Btn, ErrorLine, Field, day, inp, muted } from '../tabs/pmKit'
import { AuthCard } from './PortalShell'

export function ClientPortalAcceptInvitePage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const setAuth = usePortalAuthStore(s => s.setAuth)
  const [info, setInfo] = useState<InviteInfo | null>(null)
  const [loadErr, setLoadErr] = useState('')
  const [name, setName] = useState('')
  const [pw, setPw] = useState('')
  const [pw2, setPw2] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!token) { setLoadErr('This link is missing its invite code. Open the link from the email again.'); return }
    pmPortalClient.get(`${PM_PORTAL}/auth/invite-info`, { params: { token } })
      .then(r => { const i = r.data as InviteInfo; setInfo(i); setName(i.contactName ?? '') })
      .catch(async e => setLoadErr(await errorText(e, 'This invite link is not valid.')))
  }, [token])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setErr('')
    if (!info) return
    if (!info.existingAccount) {
      if (!name.trim()) { setErr('Enter your name'); return }
      if (pw.length < 8) { setErr('Use at least 8 characters for the password'); return }
      if (pw !== pw2) { setErr('The passwords do not match'); return }
    }
    setBusy(true)
    try {
      const r = (await pmPortalClient.post(`${PM_PORTAL}/auth/register`, { inviteToken: token, password: pw, fullName: name.trim() || null })).data as AuthResult
      setAuth(r.token, { portalUserId: r.portalUserId, email: r.email, fullName: r.fullName })
      navigate(PM_PORTAL_HOME, { replace: true })
    } catch (e2) { setErr(await errorText(e2, 'Could not accept the invite')) } finally { setBusy(false) }
  }

  if (loadErr) return <AuthCard title="Invite not valid" sub={loadErr}>
    <Btn onClick={() => navigate(`${PM_PORTAL_HOME}/login`)}>Go to sign in</Btn></AuthCard>
  if (!info) return <AuthCard title="Opening your invite…"><div style={muted}>One moment.</div></AuthCard>

  return (
    <AuthCard title={`Projects for ${info.clientName}`}
      sub={<>{info.invitedByName ? `${info.invitedByName} invited ` : 'You are invited as '}<b>{info.email}</b> to follow these projects online.</>}>
      <form onSubmit={submit}>
        {info.existingAccount ? <>
          <div style={{ ...muted, marginBottom: 12, lineHeight: 1.5 }}>You already have a portal login for this email (from another client portal). Enter its password to add these projects to it.</div>
          <Field label="Your current password"><input type="password" required autoFocus autoComplete="current-password" value={pw} onChange={e => setPw(e.target.value)} style={inp} /></Field>
        </> : <>
          <Field label="Your name"><input required autoFocus value={name} onChange={e => setName(e.target.value)} maxLength={255} style={inp} /></Field>
          <Field label="Choose a password" hint="At least 8 characters"><input type="password" required autoComplete="new-password" value={pw} onChange={e => setPw(e.target.value)} style={inp} /></Field>
          <Field label="Type it again"><input type="password" required autoComplete="new-password" value={pw2} onChange={e => setPw2(e.target.value)} style={inp} /></Field>
        </>}
        {err && <ErrorLine text={err} />}
        <div style={{ display: 'flex', alignItems: 'center', marginTop: 6, gap: 10 }}>
          <span style={{ ...muted, flex: 1 }}>Link valid until {day(info.expiresAt)}</span>
          <Btn type="submit" kind="primary" disabled={busy}>{busy ? 'Working…' : info.existingAccount ? 'Add to my login' : 'Create my login'}</Btn>
        </div>
      </form>
    </AuthCard>
  )
}
