// src/pages/marketing/PublicMarketingPages.tsx
import { useEffect, useState, type ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { apiClient } from '../../api/client'

const page: React.CSSProperties = { minHeight: '100vh', background: '#F1F5F9', display: 'flex', alignItems: 'flex-start', justifyContent: 'center', padding: '40px 16px', fontFamily: "'Inter', system-ui, sans-serif", boxSizing: 'border-box' }
const card: React.CSSProperties = { background: '#fff', borderRadius: 16, padding: 28, width: '100%', maxWidth: 480, boxShadow: '0 4px 24px rgba(15,23,42,0.08)' }
const inp: React.CSSProperties = { width: '100%', padding: '11px 13px', border: '1.5px solid #E2E8F0', borderRadius: 9, fontSize: 15, boxSizing: 'border-box', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }
const btn = (disabled?: boolean, color = '#2563EB'): React.CSSProperties => ({ width: '100%', padding: '12px 16px', background: color, color: '#fff', border: 'none', borderRadius: 9, fontSize: 15, fontWeight: 700, cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.55 : 1 })
const msg = (e: any, fallback: string) => e?.response?.data?.message || fallback

const Shell = ({ children }: { children: ReactNode }) => <div style={page}><div style={card}>{children}</div></div>
const Err = ({ text }: { text: string }) => <div style={{ padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 9, fontSize: 14, color: '#B91C1C', marginTop: 14 }}>{text}</div>

// ── /subscribe/:slug ─────────────────────────────────────────────────────────
export function SubscribePage() {
  const { slug } = useParams()
  const [form, setForm] = useState<any>(null)
  const [error, setError] = useState('')
  const [email, setEmail] = useState(''); const [name, setName] = useState('')
  const [topics, setTopics] = useState<string[]>([]); const [consent, setConsent] = useState(false)
  const [website, setWebsite] = useState('')   // honeypot: real people never see or fill this
  const [busy, setBusy] = useState(false); const [done, setDone] = useState(false)

  useEffect(() => {
    apiClient.get(`/api/v1/marketing/public/forms/${slug}`)
      .then(r => { const f = r.data?.data; setForm(f); setTopics((f.topics ?? []).map((t: any) => t.id)) })
      .catch(e => setError(msg(e, 'This form is not available.')))
  }, [slug])

  const submit = async () => {
    setBusy(true); setError('')
    try { await apiClient.post(`/api/v1/marketing/public/forms/${slug}/submit`, { email: email.trim(), name: name.trim() || null, topicIds: topics, consent, website }); setDone(true) }
    catch (e: any) { setError(msg(e, 'Something went wrong. Please try again.')) }
    finally { setBusy(false) }
  }

  if (!form) return <Shell>{error ? <Err text={error} /> : <div style={{ color: '#94A3B8' }}>Loading…</div>}</Shell>
  if (done) return (
    <Shell>
      <h2 style={{ margin: '0 0 10px', color: '#0F172A' }}>Check your inbox</h2>
      <p style={{ color: '#475569', lineHeight: 1.6, fontSize: 15 }}>
        If this address can be subscribed, we’ve sent a confirmation email from {form.businessName}. Press the button in it to finish — you won’t receive marketing emails until you do.
      </p>
    </Shell>)

  const ok = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim()) && consent
  return (
    <Shell>
      <div style={{ fontSize: 12, fontWeight: 700, color: '#2563EB', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>{form.businessName}</div>
      <h1 style={{ margin: '0 0 8px', fontSize: 24, color: '#0F172A' }}>{form.title}</h1>
      {form.description && <p style={{ color: '#475569', lineHeight: 1.6, fontSize: 15, margin: '0 0 18px' }}>{form.description}</p>}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {form.collectName && <div><label style={lbl}>Name</label><input style={inp} value={name} maxLength={150} autoComplete="name" onChange={e => setName(e.target.value)} /></div>}
        <div><label style={lbl}>Email address *</label><input style={inp} type="email" value={email} autoComplete="email" onChange={e => setEmail(e.target.value)} /></div>
        <div aria-hidden="true" style={{ position: 'absolute', left: '-9999px', height: 0, overflow: 'hidden' }}>
          <label>Leave this empty<input tabIndex={-1} autoComplete="off" value={website} onChange={e => setWebsite(e.target.value)} /></label></div>
        {form.topics.length > 0 && (
          <div><label style={lbl}>I’m interested in</label>
            {form.topics.map((t: any) => (
              <label key={t.id} style={{ display: 'flex', gap: 10, alignItems: 'flex-start', padding: '6px 0', fontSize: 14, color: '#334155', cursor: 'pointer' }}>
                <input type="checkbox" style={{ marginTop: 3 }} checked={topics.includes(t.id)} onChange={e => setTopics(p => e.target.checked ? [...p, t.id] : p.filter(x => x !== t.id))} />
                <span><strong>{t.name}</strong>{t.description && <span style={{ color: '#64748B' }}> — {t.description}</span>}</span></label>))}</div>)}
        <label style={{ display: 'flex', gap: 10, alignItems: 'flex-start', fontSize: 13, color: '#334155', lineHeight: 1.55, cursor: 'pointer', padding: '10px 12px', background: '#F8FAFC', borderRadius: 9 }}>
          <input type="checkbox" style={{ marginTop: 3 }} checked={consent} onChange={e => setConsent(e.target.checked)} /><span>{form.consentText}</span></label>
        <button style={btn(!ok || busy)} disabled={!ok || busy} onClick={submit}>{busy ? 'Sending…' : form.buttonLabel}</button>
      </div>
      {error && <Err text={error} />}
      <p style={{ fontSize: 11, color: '#94A3B8', marginTop: 16, lineHeight: 1.5 }}>We’ll email you a link to confirm. You can unsubscribe or change your topics at any time from any email we send.</p>
    </Shell>)
}

// ── /confirm/:token ──────────────────────────────────────────────────────────
export function ConfirmPage() {
  const { token } = useParams()
  const [res, setRes] = useState<any>(null); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const go = async () => {
    setBusy(true); setError('')
    try { setRes((await apiClient.post(`/api/v1/marketing/public/confirm/${token}`)).data?.data) }
    catch (e: any) { setError(msg(e, 'This link could not be confirmed.')) }
    finally { setBusy(false) }
  }
  if (res) return (
    <Shell>
      <h2 style={{ margin: '0 0 10px', color: '#166534' }}>{res.alreadyConfirmed ? 'Already confirmed' : 'You’re subscribed'}</h2>
      <p style={{ color: '#475569', lineHeight: 1.6, fontSize: 15 }}>You’ll now receive marketing emails from {res.businessName}. You can change what you receive or unsubscribe at any time.</p>
      {res.preferencesToken && <a href={`/preferences/${res.preferencesToken}`} style={{ display: 'block', textAlign: 'center', padding: '11px', border: '1.5px solid #E2E8F0', borderRadius: 9, color: '#2563EB', fontWeight: 600, textDecoration: 'none', marginTop: 16 }}>Manage my preferences</a>}
    </Shell>)
  return (
    <Shell>
      <h2 style={{ margin: '0 0 10px', color: '#0F172A' }}>Confirm your subscription</h2>
      <p style={{ color: '#475569', lineHeight: 1.6, fontSize: 15, marginBottom: 18 }}>Press the button to start receiving marketing emails. If you didn’t ask for this, just close this page — nothing will happen.</p>
      <button style={btn(busy)} disabled={busy} onClick={go}>{busy ? 'Confirming…' : 'Confirm subscription'}</button>
      {error && <Err text={error} />}
    </Shell>)
}

// ── /preferences/:token ──────────────────────────────────────────────────────
export function PreferencesPage() {
  const { token } = useParams()
  const [view, setView] = useState<any>(null); const [sel, setSel] = useState<string[]>([])
  const [error, setError] = useState(''); const [saved, setSaved] = useState(''); const [busy, setBusy] = useState(false); const [gone, setGone] = useState(false)

  const apply = (v: any) => { setView(v); setSel((v.topics ?? []).filter((t: any) => t.subscribed).map((t: any) => t.id)) }
  useEffect(() => { apiClient.get(`/api/v1/marketing/public/preferences/${token}`).then(r => apply(r.data?.data)).catch(e => setError(msg(e, 'This link is invalid or has expired.'))) }, [token])

  const save = async () => {
    setBusy(true); setError(''); setSaved('')
    try { apply((await apiClient.put(`/api/v1/marketing/public/preferences/${token}`, { topicIds: sel })).data?.data); setSaved('Your preferences are saved.') }
    catch (e: any) { setError(msg(e, 'Could not save your preferences.')) }
    finally { setBusy(false) }
  }
  const unsubscribeAll = async () => {
    if (!window.confirm('Unsubscribe from all marketing emails from ' + view.businessName + '?')) return
    setBusy(true); setError('')
    try { await apiClient.post(`/api/v1/marketing/unsubscribe/${token}`); setGone(true) }
    catch (e: any) { setError(msg(e, 'Could not unsubscribe. Please try again.')) }
    finally { setBusy(false) }
  }

  if (!view) return <Shell>{error ? <Err text={error} /> : <div style={{ color: '#94A3B8' }}>Loading…</div>}</Shell>
  if (gone || !view.optedIn) return (
    <Shell><h2 style={{ margin: '0 0 10px', color: '#0F172A' }}>{gone ? 'You’ve been unsubscribed' : 'You’re not subscribed'}</h2>
      <p style={{ color: '#475569', lineHeight: 1.6, fontSize: 15 }}>{view.businessName} won’t send you marketing emails. This doesn’t affect messages about services you’ve asked for.</p></Shell>)
  return (
    <Shell>
      <div style={{ fontSize: 12, fontWeight: 700, color: '#2563EB', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>{view.businessName}</div>
      <h1 style={{ margin: '0 0 6px', fontSize: 22, color: '#0F172A' }}>Email preferences</h1>
      <p style={{ color: '#64748B', fontSize: 14, margin: '0 0 18px' }}>{view.name ? `${view.name}, ` : ''}you’re subscribed as {view.email}.</p>
      {view.topics.length > 0 ? (
        <>
          <label style={lbl}>Choose what you’d like to hear about</label>
          {view.topics.map((t: any) => (
            <label key={t.id} style={{ display: 'flex', gap: 10, alignItems: 'flex-start', padding: '8px 0', fontSize: 14, color: '#334155', cursor: 'pointer' }}>
              <input type="checkbox" style={{ marginTop: 3 }} checked={sel.includes(t.id)} onChange={e => setSel(p => e.target.checked ? [...p, t.id] : p.filter(x => x !== t.id))} />
              <span><strong>{t.name}</strong>{t.description && <span style={{ color: '#64748B' }}> — {t.description}</span>}</span></label>))}
          <button style={{ ...btn(busy), marginTop: 14 }} disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save preferences'}</button>
        </>) : <p style={{ color: '#475569', fontSize: 14 }}>There are no topics to choose from. You can still unsubscribe below.</p>}
      {saved && <div style={{ padding: '10px 14px', background: '#DCFCE7', border: '1px solid #86EFAC', borderRadius: 9, fontSize: 14, color: '#166534', marginTop: 14 }}>{saved}</div>}
      {error && <Err text={error} />}
      <div style={{ borderTop: '1px solid #E2E8F0', marginTop: 22, paddingTop: 16 }}>
        <button onClick={unsubscribeAll} disabled={busy} style={{ background: 'none', border: 'none', color: '#B91C1C', fontSize: 14, fontWeight: 600, cursor: 'pointer', padding: 0 }}>Unsubscribe from all marketing emails</button>
      </div>
    </Shell>)
}