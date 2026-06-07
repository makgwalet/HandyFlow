// src/pages/settings/ProfilePage.tsx
// Covers "Update profile" and "Change password" from the profile dropdown.
// Route: /profile  (add to App.tsx inside auth guard)
import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  User, Lock, CheckCircle, Eye, EyeOff,
  AlertTriangle, Save, ShieldCheck,
} from 'lucide-react'

interface Me {
  id: string; firstName: string; lastName: string
  email: string; phone: string | null
  jobTitle: string | null; department: string | null
}

const inp: React.CSSProperties = {
  width: '100%', padding: '10px 13px', border: '1.5px solid #E2E8F0',
  borderRadius: 9, fontSize: 14, boxSizing: 'border-box' as const,
  background: '#fff', outline: 'none', fontFamily: 'inherit', color: '#0F172A',
}
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 12, fontWeight: 700, color: '#6B7280',
  textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6,
}
const btnP: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 7,
  background: '#1B3A6B', color: '#fff', border: 'none',
  borderRadius: 9, padding: '11px 22px', fontSize: 14,
  fontWeight: 600, cursor: 'pointer',
}

function Toast({ msg, ok }: { msg: string; ok: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '11px 16px', background: ok ? '#DCFCE7' : '#FEF2F2', border: `1px solid ${ok ? '#86EFAC' : '#FECACA'}`, borderRadius: 10, fontSize: 13, fontWeight: 600, color: ok ? '#166534' : '#DC2626' }}>
      {ok ? <CheckCircle size={15} /> : <AlertTriangle size={15} />}
      {msg}
    </div>
  )
}

function Card({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div style={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 14, overflow: 'hidden', boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '18px 24px', borderBottom: '1px solid #F1F5F9' }}>
        <div style={{ width: 34, height: 34, borderRadius: 9, background: '#F0F9FF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {icon}
        </div>
        <h2 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: '#0F172A' }}>{title}</h2>
      </div>
      <div style={{ padding: '22px 24px' }}>{children}</div>
    </div>
  )
}

export function ProfilePage() {
  const qc = useQueryClient()

  // ── Profile ────────────────────────────────────────────────────────────────
  const [profileToast, setProfileToast] = useState<{ msg: string; ok: boolean } | null>(null)
  const showProfileToast = (msg: string, ok = true) => {
    setProfileToast({ msg, ok })
    setTimeout(() => setProfileToast(null), 4000)
  }

  const { data: me } = useQuery<Me>({
    queryKey: ['me'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/identity/me')
      return r.data?.data ?? r.data
    },
  })

  const [pf, setPf] = useState({ firstName: '', lastName: '', phone: '', jobTitle: '', department: '' })
  useEffect(() => {
    if (me) setPf({ firstName: me.firstName, lastName: me.lastName, phone: me.phone ?? '', jobTitle: me.jobTitle ?? '', department: me.department ?? '' })
  }, [me])

  const updateProfile = useMutation({
    mutationFn: () => apiClient.put('/api/v1/identity/me', {
      firstName:  pf.firstName,
      lastName:   pf.lastName,
      phone:      pf.phone   || null,
      jobTitle:   pf.jobTitle   || null,
      department: pf.department || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['me'] })
      showProfileToast('Profile updated successfully')
    },
    onError: (e: any) => showProfileToast(e.response?.data?.message || 'Failed to update profile', false),
  })

  // ── Password ───────────────────────────────────────────────────────────────
  const [pwToast, setPwToast] = useState<{ msg: string; ok: boolean } | null>(null)
  const showPwToast = (msg: string, ok = true) => {
    setPwToast({ msg, ok })
    setTimeout(() => setPwToast(null), 4000)
  }

  const [pw, setPw]   = useState({ current: '', next: '', confirm: '' })
  const [show, setShow] = useState({ current: false, next: false, confirm: false })
  const toggle = (k: keyof typeof show) => setShow(p => ({ ...p, [k]: !p[k] }))

  const strength = (s: string) => {
    let score = 0
    if (s.length >= 8)   score++
    if (/[A-Z]/.test(s)) score++
    if (/[0-9]/.test(s)) score++
    if (/[^A-Za-z0-9]/.test(s)) score++
    return score
  }
  const pwStrength = strength(pw.next)
  const strengthLabel = ['', 'Weak', 'Fair', 'Good', 'Strong'][pwStrength]
  const strengthColor = ['', '#DC2626', '#D97706', '#0D9488', '#166534'][pwStrength]

  const rules = [
    { label: 'At least 8 characters',        ok: pw.next.length >= 8 },
    { label: 'One uppercase letter',          ok: /[A-Z]/.test(pw.next) },
    { label: 'One number',                    ok: /[0-9]/.test(pw.next) },
    { label: 'Passwords match',               ok: pw.next === pw.confirm && pw.confirm.length > 0 },
  ]
  const pwValid = pw.current && pw.next && pw.next === pw.confirm && pw.next.length >= 8

  const changePassword = useMutation({
    mutationFn: () => apiClient.post('/api/v1/identity/me/password', {
      currentPassword: pw.current,
      newPassword:     pw.next,
    }),
    onSuccess: () => {
      setPw({ current: '', next: '', confirm: '' })
      showPwToast('Password changed successfully')
    },
    onError: (e: any) => showPwToast(e.response?.data?.message || 'Failed to change password', false),
  })

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 26 }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: '0 0 5px' }}>My Profile</h1>
        <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>
          Manage your personal details and account security
        </p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 720 }}>

        {/* ── Profile card ── */}
        <Card title="Personal information" icon={<User size={16} color="#0D9488" />}>
          {/* Avatar */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24, padding: '14px 18px', background: '#F8FAFC', borderRadius: 10, border: '1px solid #E2E8F0' }}>
            <div style={{ width: 54, height: 54, borderRadius: '50%', background: '#1B3A6B', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, fontWeight: 800, color: '#fff', flexShrink: 0 }}>
              {pf.firstName.charAt(0)}{pf.lastName.charAt(0)}
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 16, color: '#0F172A' }}>{pf.firstName} {pf.lastName}</div>
              <div style={{ fontSize: 13, color: '#64748B', marginTop: 2 }}>{me?.email}</div>
              {pf.jobTitle && <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 1 }}>{pf.jobTitle}{pf.department ? ` · ${pf.department}` : ''}</div>}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div>
              <label style={lbl}>First name *</label>
              <input value={pf.firstName} onChange={e => setPf(p => ({ ...p, firstName: e.target.value }))} style={inp} autoFocus />
            </div>
            <div>
              <label style={lbl}>Last name *</label>
              <input value={pf.lastName} onChange={e => setPf(p => ({ ...p, lastName: e.target.value }))} style={inp} />
            </div>
            <div>
              <label style={lbl}>Phone</label>
              <input type="tel" value={pf.phone} onChange={e => setPf(p => ({ ...p, phone: e.target.value }))} placeholder="+27 11 555 0100" style={inp} />
            </div>
            <div>
              <label style={lbl}>Job title</label>
              <input value={pf.jobTitle} onChange={e => setPf(p => ({ ...p, jobTitle: e.target.value }))} placeholder="Senior Manager" style={inp} />
            </div>
            <div>
              <label style={lbl}>Department</label>
              <input value={pf.department} onChange={e => setPf(p => ({ ...p, department: e.target.value }))} placeholder="Operations" style={inp} />
            </div>
            <div>
              <label style={lbl}>Email address</label>
              <input value={me?.email ?? ''} disabled style={{ ...inp, background: '#F8FAFC', color: '#94A3B8', cursor: 'not-allowed' }} />
              <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>Contact support to change your email</div>
            </div>
          </div>

          {profileToast && <div style={{ marginTop: 16 }}><Toast msg={profileToast.msg} ok={profileToast.ok} /></div>}

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
            <button onClick={() => updateProfile.mutate()}
              disabled={!pf.firstName || !pf.lastName || updateProfile.isPending}
              style={{ ...btnP, opacity: (!pf.firstName || !pf.lastName) ? 0.5 : 1 }}>
              {updateProfile.isPending ? 'Saving...' : <><Save size={14} /> Save changes</>}
            </button>
          </div>
        </Card>

        {/* ── Password card ── */}
        <Card title="Change password" icon={<Lock size={16} color="#0D9488" />}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {/* Current password */}
            <div>
              <label style={lbl}>Current password *</label>
              <div style={{ position: 'relative' as const }}>
                <input type={show.current ? 'text' : 'password'} value={pw.current}
                  onChange={e => setPw(p => ({ ...p, current: e.target.value }))}
                  placeholder="Your current password" style={{ ...inp, paddingRight: 44 }} />
                <button onClick={() => toggle('current')}
                  style={{ position: 'absolute' as const, right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', padding: 0, display: 'flex' }}>
                  {show.current ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            {/* New password */}
            <div>
              <label style={lbl}>New password *</label>
              <div style={{ position: 'relative' as const }}>
                <input type={show.next ? 'text' : 'password'} value={pw.next}
                  onChange={e => setPw(p => ({ ...p, next: e.target.value }))}
                  placeholder="Create a strong password" style={{ ...inp, paddingRight: 44 }} />
                <button onClick={() => toggle('next')}
                  style={{ position: 'absolute' as const, right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', padding: 0, display: 'flex' }}>
                  {show.next ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>

              {/* Strength bar */}
              {pw.next.length > 0 && (
                <div style={{ marginTop: 8 }}>
                  <div style={{ display: 'flex', gap: 4, marginBottom: 5 }}>
                    {[1,2,3,4].map(i => (
                      <div key={i} style={{ flex: 1, height: 4, borderRadius: 4, background: i <= pwStrength ? strengthColor : '#E2E8F0', transition: 'background 0.2s' }} />
                    ))}
                  </div>
                  {strengthLabel && <div style={{ fontSize: 12, color: strengthColor, fontWeight: 600 }}>{strengthLabel}</div>}
                </div>
              )}
            </div>

            {/* Confirm */}
            <div>
              <label style={lbl}>Confirm new password *</label>
              <div style={{ position: 'relative' as const }}>
                <input type={show.confirm ? 'text' : 'password'} value={pw.confirm}
                  onChange={e => setPw(p => ({ ...p, confirm: e.target.value }))}
                  placeholder="Re-enter new password" style={{ ...inp, paddingRight: 44 }} />
                <button onClick={() => toggle('confirm')}
                  style={{ position: 'absolute' as const, right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', padding: 0, display: 'flex' }}>
                  {show.confirm ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            {/* Rules checklist */}
            {pw.next.length > 0 && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                {rules.map(r => (
                  <div key={r.label} style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12, color: r.ok ? '#166534' : '#94A3B8' }}>
                    <div style={{ width: 14, height: 14, borderRadius: '50%', background: r.ok ? '#DCFCE7' : '#F1F5F9', border: `1.5px solid ${r.ok ? '#22C55E' : '#E2E8F0'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      {r.ok && <CheckCircle size={9} color="#166534" />}
                    </div>
                    {r.label}
                  </div>
                ))}
              </div>
            )}
          </div>

          {pwToast && <div style={{ marginTop: 16 }}><Toast msg={pwToast.msg} ok={pwToast.ok} /></div>}

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12, color: '#64748B' }}>
              <ShieldCheck size={13} color="#0D9488" />
              Use a unique password not used on other sites
            </div>
            <button onClick={() => changePassword.mutate()}
              disabled={!pwValid || changePassword.isPending}
              style={{ ...btnP, background: pwValid ? '#1B3A6B' : '#94A3B8', cursor: pwValid ? 'pointer' : 'default' }}>
              {changePassword.isPending ? 'Changing...' : <><Lock size={14} /> Change password</>}
            </button>
          </div>
        </Card>

      </div>
    </div>
  )
}
