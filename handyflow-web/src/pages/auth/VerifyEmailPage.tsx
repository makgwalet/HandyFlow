// src/pages/auth/VerifyEmailPage.tsx
//
// Where the "Verify your email address" link in the welcome email lands.
// Unlike ResetPasswordPage, no user input is needed to verify — this
// auto-submits on mount and just shows the result. Deliberately
// non-blocking: verifying doesn't gate anything, so there's no reason to
// make this page feel like a gate either — a simple "verified, you're
// good" or "link expired, no big deal" and a way back into the app.
import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Building2, CheckCircle2, AlertTriangle, Loader2 } from 'lucide-react'

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const navigate  = useNavigate()
  const token     = params.get('token') ?? ''

  const [attempted, setAttempted] = useState(false)

  const verify = useMutation({
    mutationFn: () => apiClient.post('/api/v1/auth/verify-email', { token }),
  })

  useEffect(() => {
    if (token && !attempted) {
      setAttempted(true)
      verify.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  return (
    <div style={pageStyle}>
      <div style={{ width: '100%', maxWidth: 440 }}>
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <div style={{ width: 38, height: 38, background: '#0D9488', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Building2 size={19} color="white" strokeWidth={2.5} />
            </div>
            <span style={{ fontSize: 20, fontWeight: 800, color: 'white', letterSpacing: '-0.5px' }}>HandyFlow</span>
          </div>
        </div>

        <div style={{ background: 'white', borderRadius: 20, padding: 36, textAlign: 'center', boxShadow: '0 24px 80px rgba(0,0,0,0.3)' }}>
          {!token ? (
            <>
              <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#FEF2F2', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
                <AlertTriangle size={26} color="#DC2626" />
              </div>
              <h1 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: '0 0 10px' }}>Invalid link</h1>
              <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.6, margin: '0 0 24px' }}>
                This verification link is missing its token.
              </p>
            </>
          ) : verify.isPending || !attempted ? (
            <>
              <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#F0F9FF', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
                <Loader2 size={26} color="#1B3A6B" className="animate-spin" />
              </div>
              <h1 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: '0 0 10px' }}>Verifying your email…</h1>
            </>
          ) : verify.isSuccess ? (
            <>
              <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#F0FDF4', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
                <CheckCircle2 size={26} color="#0D9488" />
              </div>
              <h1 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: '0 0 10px' }}>Email verified</h1>
              <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.6, margin: '0 0 24px' }}>
                Thanks — your email address is confirmed.
              </p>
            </>
          ) : (
            <>
              <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#FFFBEB', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
                <AlertTriangle size={26} color="#D97706" />
              </div>
              {/* FIX: was distinguishing "already verified" vs "expired"
                  here — that's now dead code, since the backend treats
                  already-verified as silent success (returns 200, lands
                  in the isSuccess branch above) rather than an error.
                  This branch only ever means expired/invalid now. */}
              <h1 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: '0 0 10px' }}>
                Link expired
              </h1>
              <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.6, margin: '0 0 24px' }}>
                {(verify.error as any)?.response?.data?.message
                  ?? 'This verification link is invalid or has expired.'}
              </p>
              <p style={{ fontSize: 13, color: '#94A3B8', margin: '0 0 24px' }}>
                No rush either way — this doesn't affect your access to HandyFlow.
              </p>
            </>
          )}

          <button onClick={() => navigate('/dashboard')}
            style={{ padding: '11px 24px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
            Go to dashboard
          </button>
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
