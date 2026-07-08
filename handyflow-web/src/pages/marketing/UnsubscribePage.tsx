// src/pages/marketing/UnsubscribePage.tsx
//
// Public-facing page for the unsubscribe link in every marketing email.
// No login required — matches the CAN-SPAM/POPIA convention of a one-click
// unsubscribe that works straight from an email client with no extra steps.
//
// This page didn't exist at all before — the backend endpoint
// (GET /api/v1/marketing/unsubscribe/{token}) was correct (once its SQL bug
// was fixed), but nothing in the frontend ever actually called it. Clicking
// the link in a real email just bounced back into the app with no route
// matching /unsubscribe/:token.

import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { apiClient } from '../../api/client'
import { CheckCircle, XCircle, MailX } from 'lucide-react'

export default function UnsubscribePage() {
  const { token } = useParams<{ token: string }>()
  const [status, setStatus]   = useState<'loading' | 'success' | 'error'>('loading')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!token) {
      setStatus('error')
      setMessage('This unsubscribe link is missing its token.')
      return
    }
    // GET, not POST — matches the backend's @GetMapping exactly. This is a
    // deliberate, standard exception to "GETs shouldn't have side effects":
    // one-click email unsubscribe links need to work as a plain <a href>
    // inside an email client with no JS/forms involved, which only a GET
    // can do. The action is idempotent in effect (unsubscribing twice is a
    // harmless no-op), so this is safe despite bending the usual rule.
    apiClient.get(`/api/v1/marketing/unsubscribe/${token}`)
      .then(r => {
        setMessage(r.data?.message ?? 'You have been unsubscribed. You will no longer receive marketing emails.')
        setStatus('success')
      })
      .catch(e => {
        setStatus('error')
        setMessage(e.response?.data?.message ?? 'This unsubscribe link is invalid or has expired.')
      })
  }, [token])

  return (
    <div style={{ minHeight: '100vh', background: '#F8FAFC', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20, fontFamily: 'system-ui, sans-serif' }}>
      <div style={{ maxWidth: 460, width: '100%', background: '#fff', borderRadius: 16, padding: 40, textAlign: 'center' as const, border: '1px solid #E2E8F0', boxShadow: '0 4px 24px rgba(0,0,0,0.06)' }}>

        {status === 'loading' && (
          <>
            <div style={{ width: 40, height: 40, border: '3px solid #E2E8F0', borderTopColor: '#0D9488', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 20px' }} />
            <div style={{ color: '#64748B', fontSize: 14 }}>Processing your request…</div>
          </>
        )}

        {status === 'success' && (
          <>
            <div style={{ width: 60, height: 60, borderRadius: '50%', background: '#DCFCE7', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }}>
              <CheckCircle size={28} color="#166534" />
            </div>
            <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', marginBottom: 10 }}>You've been unsubscribed</h2>
            <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.7 }}>{message}</p>
          </>
        )}

        {status === 'error' && (
          <>
            <div style={{ width: 60, height: 60, borderRadius: '50%', background: '#FEF2F2', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }}>
              <XCircle size={28} color="#DC2626" />
            </div>
            <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', marginBottom: 10 }}>Link not valid</h2>
            <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.7 }}>{message}</p>
          </>
        )}

        <div style={{ marginTop: 28, paddingTop: 20, borderTop: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, color: '#CBD5E1', fontSize: 11 }}>
          <MailX size={12} /> HandyFlow Marketing
        </div>
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
    </div>
  )
}
