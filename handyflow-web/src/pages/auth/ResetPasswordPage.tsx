// src/pages/auth/ResetPasswordPage.tsx
//
// Completes the forgot-password flow. ForgotPasswordPage sends the email;
// this page is where the link in that email actually lands. Confirmed
// missing entirely — App.tsx had no /reset-password route and no page
// component existed anywhere, so the link in a real password-reset email
// just fell through to the catch-all redirect. The backend endpoint
// (POST /api/v1/auth/reset-password) was already fully implemented; only
// the frontend half was missing.
//
// Unlike AcceptInvitePage, there's no GET .../validate/{token} endpoint
// to pre-check the token before showing the form — AuthController only
// exposes the one POST that validates the token as part of performing the
// reset itself. So this shows the form directly and only discovers an
// invalid/expired token when the user actually submits, surfacing
// whatever message the backend returns at that point.
import { useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Building2, Eye, EyeOff, Check, CheckCircle2 } from 'lucide-react'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate  = useNavigate()
  const token     = params.get('token') ?? ''

  const [password,        setPassword]        = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPass,        setShowPass]        = useState(false)
  const [error,           setError]           = useState('')
  const [done,            setDone]            = useState(false)

  const reset = useMutation({
    mutationFn: () => apiClient.post('/api/v1/auth/reset-password', {
      token,
      newPassword: password,
    }),
    onSuccess: () => setDone(true),
    onError: (e: any) => setError(
      e.response?.data?.message || 'This reset link is invalid or has expired. Please request a new one.'
    ),
  })

  const handleSubmit = () => {
    setError('')
    if (password.length < 8) { setError('Password must be at least 8 characters'); return }
    if (password !== confirmPassword) { setError('Passwords do not match'); return }
    reset.mutate()
  }

  if (!token) return (
    <div style={pageStyle}>
      <div style={{ width: '100%', maxWidth: 420 }}>
        <div style={{ background: 'white', borderRadius: 20, padding: 32, textAlign: 'center' }}>
          <div style={{ fontSize: 40, marginBottom: 16 }}>🔗</div>
          <h2 style={{ fontSize: 20, fontWeight: 700, color: '#0F172A', marginBottom: 10 }}>Invalid reset link</h2>
          <p style={{ fontSize: 14, color: '#64748B', marginBottom: 24, lineHeight: 1.6 }}>
            This password reset link is missing its token. Please request a new one.
          </p>
          <button onClick={() => navigate('/forgot-password')}
            style={{ padding: '10px 24px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
            Request new link
          </button>
        </div>
      </div>
    </div>
  )

  return (
    <div style={pageStyle}>
      <div style={{ width: '100%', maxWidth: 460 }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <div style={{ width: 38, height: 38, background: '#0D9488', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Building2 size={19} color="white" strokeWidth={2.5} />
            </div>
            <span style={{ fontSize: 20, fontWeight: 800, color: 'white', fontFamily: "'Syne', sans-serif", letterSpacing: '-0.5px' }}>HandyFlow</span>
          </div>
        </div>

        {/* Card */}
        <div style={{ background: 'white', borderRadius: 20, padding: 32, boxShadow: '0 24px 80px rgba(0,0,0,0.3)' }}>
          {done ? (
            <div style={{ textAlign: 'center', padding: '8px 0' }}>
              <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#F0FDF4', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
                <CheckCircle2 size={26} color="#0D9488" />
              </div>
              <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: '0 0 8px' }}>Password reset</h2>
              <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.6, margin: '0 0 24px' }}>
                Your password has been changed successfully.
              </p>
              <button onClick={() => navigate('/login')}
                style={{ width: '100%', padding: '13px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 10, fontSize: 15, fontWeight: 700, cursor: 'pointer' }}>
                Sign in →
              </button>
            </div>
          ) : (
            <>
              <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 6px' }}>
                Set a new password
              </h2>
              <p style={{ fontSize: 14, color: '#64748B', margin: '0 0 24px' }}>
                Choose a new password for your account.
              </p>

              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>New password *</label>
                <div style={{ position: 'relative' }}>
                  <input type={showPass ? 'text' : 'password'} value={password}
                    onChange={e => setPassword(e.target.value)}
                    placeholder="At least 8 characters"
                    style={{ width: '100%', padding: '11px 44px 11px 14px', border: '1.5px solid #E2E8F0', borderRadius: 10, fontSize: 14, boxSizing: 'border-box' as const }}
                    autoFocus />
                  <button type="button" onClick={() => setShowPass(s => !s)}
                    style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}>
                    {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div style={{ marginBottom: 20 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>Confirm new password *</label>
                <input type="password" value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  placeholder="Re-enter your password"
                  style={{ width: '100%', padding: '11px 14px', border: `1.5px solid ${confirmPassword && password !== confirmPassword ? '#DC2626' : '#E2E8F0'}`, borderRadius: 10, fontSize: 14, boxSizing: 'border-box' as const }} />
                {confirmPassword && password !== confirmPassword && (
                  <div style={{ marginTop: 4, fontSize: 12, color: '#DC2626' }}>Passwords do not match</div>
                )}
              </div>

              {/* Password strength hints — matches AcceptInvitePage's checklist exactly */}
              <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
                {[
                  { label: '8+ chars', ok: password.length >= 8 },
                  { label: 'Passwords match', ok: password === confirmPassword && confirmPassword.length > 0 },
                ].map(h => (
                  <div key={h.label} style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, color: h.ok ? '#0D9488' : '#94A3B8' }}>
                    <div style={{ width: 14, height: 14, borderRadius: '50%', background: h.ok ? '#0D9488' : '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      {h.ok && <Check size={9} color="white" strokeWidth={3} />}
                    </div>
                    {h.label}
                  </div>
                ))}
              </div>

              {error && (
                <div style={{ marginBottom: 16, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>
              )}

              <button onClick={handleSubmit}
                disabled={reset.isPending || !password || !confirmPassword}
                style={{ width: '100%', padding: '13px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 10, fontSize: 15, fontWeight: 700, cursor: 'pointer', opacity: !password || !confirmPassword ? 0.5 : 1 }}>
                {reset.isPending ? 'Resetting...' : 'Reset password'}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

const pageStyle: React.CSSProperties = {
  minHeight: '100vh',
  background: 'linear-gradient(135deg, #0F172A 0%, #1B3A6B 50%, #0D9488 100%)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  padding: '24px 16px', fontFamily: "'Inter', system-ui, sans-serif",
}
