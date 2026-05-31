import { useState, useEffect } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useAuthStore } from '../../store/auth.store'
import { Building2, Eye, EyeOff, Check } from 'lucide-react'
import type { User } from '../../types/auth.types'

export function AcceptInvitePage() {
  const [params]   = useSearchParams()
  const navigate   = useNavigate()
  const { setAuth } = useAuthStore()
  const token      = params.get('token') ?? ''

  const [password,        setPassword]        = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPass,        setShowPass]        = useState(false)
  const [error,           setError]           = useState('')

  // Validate token first
  const { data: invitation, isLoading, isError } = useQuery({
    queryKey: ['invitation-validate', token],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/identity/invitations/validate/${token}`)
      return res.data as { firstName: string; lastName: string; email: string; roleName: string }
    },
    enabled: !!token,
    retry: false,
  })

  const accept = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/identity/invitations/accept', body),
    onSuccess: (res) => {
      const data = res.data
      if (data?.accessToken) {
        const user: User = {
          userId: data.userId, tenantId: data.tenantId,
          email: data.email, firstName: data.firstName,
          lastName: data.lastName, permissions: data.permissions,
        }
        setAuth(data.accessToken, user)
      }
      navigate('/dashboard')
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create account'),
  })

  const handleSubmit = () => {
    setError('')
    if (password.length < 8) { setError('Password must be at least 8 characters'); return }
    if (password !== confirmPassword) { setError('Passwords do not match'); return }
    accept.mutate({ token, password })
  }

  if (!token) return (
    <ErrorPage message="Invalid invitation link — no token found." />
  )

  if (isLoading) return (
    <div style={pageStyle}>
      <div style={{ color: 'rgba(255,255,255,0.7)', fontSize: 16 }}>Validating invitation...</div>
    </div>
  )

  if (isError) return (
    <ErrorPage message="This invitation link is invalid or has expired. Please ask your admin to send a new one." />
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
          <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 6px' }}>
            You've been invited 🎉
          </h2>
          <p style={{ fontSize: 14, color: '#64748B', margin: '0 0 24px' }}>
            Set your password to join as <strong>{invitation?.firstName} {invitation?.lastName}</strong> with role <strong>{invitation?.roleName}</strong>.
          </p>

          {/* Pre-filled email */}
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>Email</label>
            <input value={invitation?.email ?? ''} disabled
              style={{ width: '100%', padding: '11px 14px', border: '1.5px solid #E2E8F0', borderRadius: 10, fontSize: 14, boxSizing: 'border-box' as const, background: '#F8FAFC', color: '#64748B' }} />
          </div>

          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>Password *</label>
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
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>Confirm password *</label>
            <input type="password" value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              placeholder="Re-enter your password"
              style={{ width: '100%', padding: '11px 14px', border: `1.5px solid ${confirmPassword && password !== confirmPassword ? '#DC2626' : '#E2E8F0'}`, borderRadius: 10, fontSize: 14, boxSizing: 'border-box' as const }} />
            {confirmPassword && password !== confirmPassword && (
              <div style={{ marginTop: 4, fontSize: 12, color: '#DC2626' }}>Passwords do not match</div>
            )}
          </div>

          {/* Password strength hints */}
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
            disabled={accept.isPending || !password || !confirmPassword}
            style={{ width: '100%', padding: '13px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 10, fontSize: 15, fontWeight: 700, cursor: 'pointer', opacity: !password || !confirmPassword ? 0.5 : 1 }}>
            {accept.isPending ? 'Creating account...' : 'Create account & sign in →'}
          </button>
        </div>
      </div>
    </div>
  )
}

function ErrorPage({ message }: { message: string }) {
  const navigate = useNavigate()
  return (
    <div style={pageStyle}>
      <div style={{ width: '100%', maxWidth: 420 }}>
        <div style={{ background: 'white', borderRadius: 20, padding: 32, textAlign: 'center' }}>
          <div style={{ fontSize: 40, marginBottom: 16 }}>🔗</div>
          <h2 style={{ fontSize: 20, fontWeight: 700, color: '#0F172A', marginBottom: 10 }}>Invalid invitation</h2>
          <p style={{ fontSize: 14, color: '#64748B', marginBottom: 24, lineHeight: 1.6 }}>{message}</p>
          <button onClick={() => navigate('/login')}
            style={{ padding: '10px 24px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
            Go to sign in
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
