// src/pages/login/AdminLoginPage.tsx
import { useState } from 'react'
import { authApi } from '../../api/client'
import { authStore } from '../../store/auth'
import { Shield, Eye, EyeOff, AlertTriangle, Smartphone } from 'lucide-react'

const inp: React.CSSProperties = {
  width: '100%', padding: '11px 14px', border: '1.5px solid #2D3748',
  borderRadius: 10, fontSize: 14, background: '#1A202C', color: '#F7FAFC',
  outline: 'none', boxSizing: 'border-box' as const, fontFamily: 'inherit',
}
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 12, fontWeight: 600, color: '#A0AEC0',
  marginBottom: 6, letterSpacing: '0.04em',
}

export function AdminLoginPage({ onLogin }: { onLogin: () => void }) {
  const [step,     setStep]     = useState<'password' | 'totp' | 'setup'>('password')
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [showPw,   setShowPw]   = useState(false)
  const [totpCode, setTotpCode] = useState('')
  const [partial,  setPartial]  = useState('')
  const [adminId,  setAdminId]  = useState('')
  const [qrUri,    setQrUri]    = useState('')
  const [secret,   setSecret]   = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  const handlePassword = async () => {
    if (!email || !password) return
    setLoading(true); setError('')
    try {
      const r = await authApi.post('/login', { email, password })
      const d = r.data?.data ?? r.data
      if (d.state === 'TOTP_SETUP_REQUIRED') {
        // Trigger TOTP setup
        setAdminId(d.adminId)
        const s = await authApi.post(`/totp/setup?adminId=${d.adminId}`)
        const sd = s.data?.data ?? s.data
        setQrUri(sd.otpAuthUri); setSecret(sd.secret)
        setStep('setup')
      } else if (d.state === 'TOTP_REQUIRED') {
        setPartial(d.token)
        setStep('totp')
      }
    } catch (e: any) {
      setError(e.response?.data?.message || 'Invalid credentials')
    } finally { setLoading(false) }
  }

  const handleTotp = async () => {
    if (!totpCode) return
    setLoading(true); setError('')
    try {
      const r = await authApi.post('/verify-totp', { partialToken: partial, code: totpCode })
      const d = r.data?.data ?? r.data
      authStore.set({
        adminId: d.adminId, email: d.email, fullName: d.fullName,
        role: d.role, token: d.token, expiresAt: d.expiresAt,
      })
      onLogin()
    } catch (e: any) {
      setError(e.response?.data?.message || 'Invalid TOTP code')
      setTotpCode('')
    } finally { setLoading(false) }
  }

  const handleConfirmSetup = async () => {
    if (!totpCode) return
    setLoading(true); setError('')
    try {
      await authApi.post(`/totp/confirm?adminId=${adminId}`, { code: totpCode })
      // Now do a fresh login to get full token
      const r = await authApi.post('/login', { email, password })
      const d = r.data?.data ?? r.data
      setPartial(d.token)
      setStep('totp')
    } catch (e: any) {
      setError(e.response?.data?.message || 'TOTP confirmation failed')
      setTotpCode('')
    } finally { setLoading(false) }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'linear-gradient(135deg, #0F1117 0%, #1A202C 100%)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
      <div style={{ width: '100%', maxWidth: 400 }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 52, height: 52, background: 'linear-gradient(135deg, #1B3A6B, #0D9488)', borderRadius: 14, marginBottom: 14, boxShadow: '0 8px 24px rgba(13,148,136,0.3)' }}>
            <Shield size={24} color="#fff" />
          </div>
          <div style={{ fontSize: 22, fontWeight: 800, color: '#F7FAFC', marginBottom: 4 }}>HandyFlow Admin</div>
          <div style={{ fontSize: 13, color: '#718096' }}>Superadmin portal — staff only</div>
        </div>

        <div style={{ background: '#1A202C', border: '1px solid #2D3748', borderRadius: 16, padding: 28, boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>

          {step === 'password' && (
            <>
              <h2 style={{ fontSize: 16, fontWeight: 700, color: '#F7FAFC', marginBottom: 22 }}>Sign in</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div>
                  <label style={lbl}>Email address</label>
                  <input autoFocus type="email" value={email} onChange={e => setEmail(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handlePassword()}
                    placeholder="admin@handyflow.co.za" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Password</label>
                  <div style={{ position: 'relative' as const }}>
                    <input type={showPw ? 'text' : 'password'} value={password}
                      onChange={e => setPassword(e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && handlePassword()}
                      placeholder="••••••••••••" style={{ ...inp, paddingRight: 44 }} />
                    <button onClick={() => setShowPw(p => !p)}
                      style={{ position: 'absolute' as const, right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex' }}>
                      {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </div>
              </div>
              {error && (
                <div style={{ marginTop: 12, display: 'flex', gap: 8, padding: '10px 12px', background: '#742A2A', border: '1px solid #FC8181', borderRadius: 8, fontSize: 13, color: '#FEB2B2' }}>
                  <AlertTriangle size={15} style={{ flexShrink: 0, marginTop: 1 }} />{error}
                </div>
              )}
              <button onClick={handlePassword} disabled={!email || !password || loading}
                style={{ width: '100%', marginTop: 18, padding: '12px', background: loading ? '#2D3748' : 'linear-gradient(135deg, #1B3A6B, #2563EB)', color: '#fff', border: 'none', borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: loading ? 'not-allowed' : 'pointer', transition: 'opacity 0.15s' }}>
                {loading ? 'Signing in...' : 'Continue'}
              </button>
            </>
          )}

          {step === 'totp' && (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 22 }}>
                <div style={{ width: 36, height: 36, borderRadius: 9, background: '#2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Smartphone size={18} color="#0D9488" />
                </div>
                <div>
                  <h2 style={{ fontSize: 15, fontWeight: 700, color: '#F7FAFC', margin: 0 }}>Two-factor authentication</h2>
                  <div style={{ fontSize: 12, color: '#718096', marginTop: 2 }}>Open your authenticator app</div>
                </div>
              </div>
              <label style={lbl}>6-digit code</label>
              <input autoFocus type="text" inputMode="numeric" maxLength={6} value={totpCode}
                onChange={e => setTotpCode(e.target.value.replace(/\D/g,''))}
                onKeyDown={e => e.key === 'Enter' && handleTotp()}
                placeholder="000000"
                style={{ ...inp, fontSize: 24, letterSpacing: '0.3em', textAlign: 'center' as const }} />
              {error && (
                <div style={{ marginTop: 12, display: 'flex', gap: 8, padding: '10px 12px', background: '#742A2A', border: '1px solid #FC8181', borderRadius: 8, fontSize: 13, color: '#FEB2B2' }}>
                  <AlertTriangle size={15} />{error}
                </div>
              )}
              <button onClick={handleTotp} disabled={totpCode.length < 6 || loading}
                style={{ width: '100%', marginTop: 18, padding: '12px', background: totpCode.length < 6 ? '#2D3748' : 'linear-gradient(135deg, #065F46, #0D9488)', color: '#fff', border: 'none', borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: totpCode.length < 6 ? 'not-allowed' : 'pointer' }}>
                {loading ? 'Verifying...' : 'Verify and sign in'}
              </button>
              <button onClick={() => { setStep('password'); setError(''); setTotpCode('') }} style={{ width: '100%', marginTop: 10, padding: '10px', background: 'none', border: 'none', color: '#718096', fontSize: 13, cursor: 'pointer' }}>
                ← Back to password
              </button>
            </>
          )}

          {step === 'setup' && (
            <>
              <div style={{ marginBottom: 20 }}>
                <h2 style={{ fontSize: 15, fontWeight: 700, color: '#F7FAFC', marginBottom: 6 }}>Set up two-factor authentication</h2>
                <div style={{ fontSize: 13, color: '#718096', lineHeight: 1.6 }}>Scan this QR code with Google Authenticator, Authy, or any TOTP app.</div>
              </div>
              {/* QR code rendered via Google Charts API */}
              <div style={{ textAlign: 'center', marginBottom: 18 }}>
                <img src={`https://api.qrserver.com/v1/create-qr-code/?data=${encodeURIComponent(qrUri)}&size=180x180&bgcolor=1A202C&color=F7FAFC&margin=10`}
                  alt="TOTP QR Code" style={{ borderRadius: 10, width: 180, height: 180 }} />
                <div style={{ marginTop: 10, fontSize: 11, color: '#718096' }}>Can't scan? Manual key:</div>
                <div style={{ fontFamily: 'monospace', fontSize: 12, color: '#0D9488', background: '#2D3748', padding: '6px 12px', borderRadius: 6, marginTop: 4, letterSpacing: '0.1em' }}>{secret}</div>
              </div>
              <label style={lbl}>Enter the 6-digit code from your app to confirm</label>
              <input autoFocus type="text" inputMode="numeric" maxLength={6} value={totpCode}
                onChange={e => setTotpCode(e.target.value.replace(/\D/g,''))}
                onKeyDown={e => e.key === 'Enter' && handleConfirmSetup()}
                placeholder="000000"
                style={{ ...inp, fontSize: 20, letterSpacing: '0.25em', textAlign: 'center' as const }} />
              {error && (
                <div style={{ marginTop: 12, display: 'flex', gap: 8, padding: '10px 12px', background: '#742A2A', border: '1px solid #FC8181', borderRadius: 8, fontSize: 13, color: '#FEB2B2' }}>
                  <AlertTriangle size={15} />{error}
                </div>
              )}
              <button onClick={handleConfirmSetup} disabled={totpCode.length < 6 || loading}
                style={{ width: '100%', marginTop: 16, padding: '12px', background: totpCode.length < 6 ? '#2D3748' : 'linear-gradient(135deg, #065F46, #0D9488)', color: '#fff', border: 'none', borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: totpCode.length < 6 ? 'not-allowed' : 'pointer' }}>
                {loading ? 'Confirming...' : 'Confirm and sign in'}
              </button>
            </>
          )}
        </div>

        <div style={{ textAlign: 'center', marginTop: 20, fontSize: 12, color: '#4A5568' }}>
          HandyFlow Admin Portal · Restricted access · All actions are logged
        </div>
      </div>
    </div>
  )
}
