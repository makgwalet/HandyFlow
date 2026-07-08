// src/pages/auth/ForgotPasswordPage.tsx
//
// Two-screen flow: request → check-your-email confirmation.
// Deliberately never reveals whether the email/slug combination exists —
// same confirmation copy either way, to avoid leaking which companies
// are HandyFlow customers.

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Building2, Mail, ChevronLeft, CheckCircle2 } from 'lucide-react'

export function ForgotPasswordPage() {
  const [email, setEmail]           = useState('')
  const [tenantSlug, setTenantSlug] = useState('')
  const [sent, setSent]             = useState(false)
  const [error, setError]           = useState('')

  const submit = useMutation({
    mutationFn: () => apiClient.post('/api/v1/auth/forgot-password', { email, tenantSlug }),
    // Always resolve to the same "check your email" state — do not
    // branch UI on success/failure of whether the account exists.
    onSuccess: () => setSent(true),
    onError: () => setSent(true),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (!email || !tenantSlug) { setError('Please enter your company slug and email address'); return }
    submit.mutate()
  }

  return (
    <div style={pageStyle}>
      <div style={{ width: '100%', maxWidth: 440 }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <div style={{ width: 38, height: 38, background: '#0D9488', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Building2 size={19} color="white" strokeWidth={2.5} />
            </div>
            <span style={{ fontSize: 20, fontWeight: 800, color: 'white', letterSpacing: '-0.5px' }}>HandyFlow</span>
          </div>
        </div>

        <div style={{ background: 'white', borderRadius: 20, padding: 32, boxShadow: '0 24px 80px rgba(0,0,0,0.3)' }}>
          {!sent ? (
            <>
              <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 6px' }}>Reset your password</h2>
              <p style={{ fontSize: 14, color: '#64748B', margin: '0 0 24px', lineHeight: 1.5 }}>
                Enter your company slug and work email — we'll send a reset link if we find a match.
              </p>

              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <Field label="Company Slug *">
                  <input value={tenantSlug} onChange={e => setTenantSlug(e.target.value)}
                    placeholder="acme-security" style={inputStyle} autoFocus />
                </Field>
                <Field label="Email address *">
                  <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                    placeholder="you@company.co.za" style={inputStyle} />
                </Field>

                {error && (
                  <div style={{ padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>
                )}

                <button type="submit" disabled={submit.isPending}
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 10, padding: '13px 24px', fontSize: 15, fontWeight: 700, cursor: 'pointer', opacity: submit.isPending ? 0.7 : 1 }}>
                  <Mail size={16} /> {submit.isPending ? 'Sending…' : 'Send reset link'}
                </button>
              </form>
            </>
          ) : (
            <div style={{ textAlign: 'center', padding: '8px 0' }}>
              <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#F0FDF4', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
                <CheckCircle2 size={26} color="#0D9488" />
              </div>
              <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: '0 0 8px' }}>Check your email</h2>
              <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.6, margin: '0 0 4px' }}>
                If an account matches <strong>{email}</strong> at <strong>{tenantSlug}</strong>, a reset link is on its way.
              </p>
              <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>The link expires in 30 minutes.</p>
            </div>
          )}

          <a href="/login" style={{ marginTop: 20, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, fontSize: 13, color: '#1B3A6B', fontWeight: 600, textDecoration: 'none' }}>
            <ChevronLeft size={14} /> Back to sign in
          </a>
        </div>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>{label}</label>
      {children}
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '11px 14px', border: '1.5px solid #E2E8F0',
  borderRadius: 10, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff', color: '#0F172A',
}
const pageStyle: React.CSSProperties = {
  minHeight: '100vh',
  background: 'linear-gradient(135deg, #0F172A 0%, #1B3A6B 50%, #0D9488 100%)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  padding: '24px 16px', fontFamily: "'Inter', system-ui, sans-serif",
}
