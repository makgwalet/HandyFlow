// src/pages/auth/RegisterPage.tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useAuthStore } from '../../store/auth.store'
import type { User } from '../../types/auth.types'
import {
  Building2, User as UserIcon, ChevronRight, ChevronLeft,
  Check, Shield, Fuel, HardHat, Car, Package,
  Briefcase, Calculator, CalendarCheck, HeartPulse,
  PartyPopper, FileText, Wallet, FilePen, Eye, EyeOff,
  Zap, Clock, ExternalLink,
} from 'lucide-react'

const MODULE_ICONS: Record<string, React.ElementType> = {
  crm: UserIcon, invoicing: FileText, catalogue: Package,
  security: Shield, fuel: Fuel, earthmoving: HardHat,
  property: Building2, fleet: Car, hr: Briefcase,
  accounting: Calculator, bookings: CalendarCheck,
  clinic: HeartPulse, events: PartyPopper,
  contracting: FilePen, expenses: Wallet,
}

const MODULE_COLORS: Record<string, { bg: string; color: string }> = {
  crm:         { bg: '#DBEAFE', color: '#1D4ED8' },
  invoicing:   { bg: '#DCFCE7', color: '#166534' },
  catalogue:   { bg: '#F3E8FF', color: '#7C3AED' },
  security:    { bg: '#F0FDF4', color: '#0D9488' },
  fuel:        { bg: '#FEF3C7', color: '#D97706' },
  earthmoving: { bg: '#FEF9C3', color: '#854D0E' },
  property:    { bg: '#EDE9FE', color: '#7C3AED' },
  fleet:       { bg: '#E0F2FE', color: '#0369A1' },
  hr:          { bg: '#FCE7F3', color: '#9D174D' },
  accounting:  { bg: '#ECFDF5', color: '#059669' },
  bookings:    { bg: '#FFF7ED', color: '#EA580C' },
  clinic:      { bg: '#FFF1F2', color: '#BE123C' },
  events:      { bg: '#FFFBEB', color: '#D97706' },
  contracting: { bg: '#F0F9FF', color: '#0284C7' },
  expenses:    { bg: '#FDF4FF', color: '#9333EA' },
}

const ALL_MODULES = [
  { moduleKey: 'crm',         name: 'CRM',          description: 'Customers & contacts',         monthlyPrice: 0,   category: 'Core' },
  { moduleKey: 'invoicing',   name: 'Invoicing',     description: 'Quotes & invoices',            monthlyPrice: 0,   category: 'Core' },
  { moduleKey: 'catalogue',   name: 'Catalogue',     description: 'Products & services',          monthlyPrice: 0,   category: 'Core' },
  { moduleKey: 'security',    name: 'Security',      description: 'Guards, sites & QR patrols',   monthlyPrice: 299, category: 'Industry' },
  { moduleKey: 'fuel',        name: 'Fuel',          description: 'Tanks, dispatch & deliveries',  monthlyPrice: 199, category: 'Industry' },
  { moduleKey: 'earthmoving', name: 'Earthmoving',   description: 'Assets & operators',           monthlyPrice: 299, category: 'Industry' },
  { moduleKey: 'property',    name: 'Property',      description: 'Units, leases & rent',         monthlyPrice: 249, category: 'Industry' },
  { moduleKey: 'fleet',       name: 'Fleet',         description: 'Vehicles & trips',             monthlyPrice: 199, category: 'Industry' },
  { moduleKey: 'clinic',      name: 'Clinic',        description: 'Patients & consultations',     monthlyPrice: 349, category: 'Industry' },
  { moduleKey: 'hr',          name: 'HR & Payroll',  description: 'Employees & pay runs',         monthlyPrice: 299, category: 'Business' },
  { moduleKey: 'accounting',  name: 'Accounting',    description: 'Chart of accounts & reports',  monthlyPrice: 249, category: 'Business' },
  { moduleKey: 'bookings',    name: 'Bookings',      description: 'Appointments & scheduling',    monthlyPrice: 199, category: 'Business' },
  { moduleKey: 'events',      name: 'Events',        description: 'Ticketing & QR check-in',      monthlyPrice: 299, category: 'Business' },
  { moduleKey: 'expenses',    name: 'Expenses',      description: 'Staff expense claims',         monthlyPrice: 149, category: 'Business' },
  { moduleKey: 'contracting', name: 'Contracting',   description: 'Contracts & OTP signing',      monthlyPrice: 299, category: 'Business' },
]

const CORE_KEYS = ['crm', 'invoicing', 'catalogue']
const INDUSTRY_MODULES = ALL_MODULES.filter(m => m.category !== 'Core')

type PlanType = 'pilot' | 'paid'

export function RegisterPage() {
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()

  const [planType, setPlanType]               = useState<PlanType | null>(null)
  const [step, setStep]                       = useState(0)
  const [showPass, setShowPass]               = useState(false)
  const [showConfirmPass, setShowConfirmPass] = useState(false)
  const [error, setError]                     = useState('')
  const [agreedToTerms, setAgreedToTerms]     = useState(false)
  const [pilotModule, setPilotModule]         = useState<string | null>(null)
  const [selectedModules, setSelectedModules] = useState<Set<string>>(new Set(CORE_KEYS))

  const [company, setCompany] = useState({ companyName: '', slug: '', phone: '' })
  const [account, setAccount] = useState({ firstName: '', lastName: '', email: '', password: '', confirmPassword: '' })

  const cf = (k: keyof typeof company, v: string) => setCompany(p => ({ ...p, [k]: v }))
  const af = (k: keyof typeof account, v: string) => setAccount(p => ({ ...p, [k]: v }))

  const handleCompanyName = (name: string) => {
    const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
    setCompany(p => ({ ...p, companyName: name, slug }))
  }

  // Steps: 0=plan, 1=company, 2=module pick, 3=account
  const steps = planType === 'pilot'
    ? ['Plan', 'Company', 'Module', 'Account']
    : ['Plan', 'Company', 'Modules', 'Account']

  const activeModuleKeys = planType === 'pilot'
    ? [...CORE_KEYS, ...(pilotModule ? [pilotModule] : [])]
    : Array.from(selectedModules)

  const monthlyTotal = ALL_MODULES
    .filter(m => activeModuleKeys.includes(m.moduleKey))
    .reduce((s, m) => s + m.monthlyPrice, 0)

  const togglePaidModule = (key: string) => {
    if (CORE_KEYS.includes(key)) return
    setSelectedModules(prev => {
      const next = new Set(prev)
      next.has(key) ? next.delete(key) : next.add(key)
      return next
    })
  }

  const register = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/auth/register', body),
    onSuccess: (res) => {
      const data = res.data
      const user: User = {
        userId: data.userId, tenantId: data.tenantId,
        email: data.email, firstName: data.firstName,
        lastName: data.lastName, permissions: data.permissions,
      }
      setAuth(data.accessToken, user)
      navigate('/dashboard')
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Registration failed. Email or slug may already be taken.'),
  })

  const handleSubmit = () => {
    setError('')
    if (!account.firstName || !account.lastName || !account.email || !account.password) {
      setError('All fields are required'); return
    }
    if (account.password.length < 8) { setError('Password must be at least 8 characters'); return }
    if (account.password !== account.confirmPassword) { setError('Passwords do not match'); return }
    if (!agreedToTerms) { setError('Please accept the terms and conditions to continue'); return }
    if (planType === 'pilot' && !pilotModule) { setError('Please select a pilot module'); return }

    register.mutate({
      companyName: company.companyName,
      slug: company.slug,
      firstName: account.firstName,
      lastName: account.lastName,
      email: account.email,
      password: account.password,
      moduleKeys: planType === 'pilot' ? [...CORE_KEYS, pilotModule!] : Array.from(selectedModules),
    })
  }

  const goNext = () => { setError(''); setStep(s => s + 1) }
  const goBack = () => { setError(''); setStep(s => s - 1) }

  const isWide = step === 2 && planType === 'paid'

  return (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #0F172A 0%, #1B3A6B 50%, #0D9488 100%)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: '24px 16px', fontFamily: "'Inter', system-ui, sans-serif",
    }}>
      <div style={{ width: '100%', maxWidth: isWide ? 920 : 500 }}>

        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <div style={{ width: 38, height: 38, background: '#0D9488', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Building2 size={19} color="white" strokeWidth={2.5} />
            </div>
            <span style={{ fontSize: 20, fontWeight: 800, color: 'white', letterSpacing: '-0.5px' }}>HandyFlow</span>
          </div>
          <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: 13 }}>South Africa's business management platform</div>
        </div>

        {/* Step indicator */}
        {planType && step > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 24 }}>
            {steps.slice(1).map((label, i) => {
              const sn = i + 1
              const done = step > sn
              const active = step === sn
              return (
                <div key={label} style={{ display: 'flex', alignItems: 'center' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3 }}>
                    <div style={{ width: 28, height: 28, borderRadius: '50%', background: done ? '#0D9488' : active ? 'white' : 'rgba(255,255,255,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      {done ? <Check size={12} color="white" strokeWidth={3} />
                        : <span style={{ fontSize: 12, fontWeight: 700, color: active ? '#1B3A6B' : 'rgba(255,255,255,0.4)' }}>{sn}</span>}
                    </div>
                    <span style={{ fontSize: 10, fontWeight: active ? 600 : 400, color: active ? 'white' : 'rgba(255,255,255,0.4)' }}>{label}</span>
                  </div>
                  {i < steps.length - 2 && (
                    <div style={{ width: 48, height: 2, background: done ? '#0D9488' : 'rgba(255,255,255,0.15)', margin: '0 6px', marginBottom: 18 }} />
                  )}
                </div>
              )
            })}
          </div>
        )}

        {/* Card */}
        <div style={{ background: 'white', borderRadius: 20, padding: isWide ? '28px 32px' : '32px', boxShadow: '0 24px 80px rgba(0,0,0,0.3)' }}>

          {/* STEP 0 — Plan selection */}
          {step === 0 && (
            <div>
              <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Get started with HandyFlow</h2>
              <p style={{ fontSize: 14, color: '#64748B', margin: '0 0 24px' }}>Choose how you'd like to begin. You can always upgrade later.</p>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {[
                  {
                    type: 'pilot' as PlanType,
                    icon: Clock, iconBg: '#F0FDF4', iconBorder: '#86EFAC', iconColor: '#0D9488',
                    title: 'Start a free 60-day pilot',
                    badge: 'No card required', badgeBg: '#DCFCE7', badgeColor: '#166534',
                    desc: 'Try HandyFlow with one industry module of your choice. Core apps (CRM, Invoicing, Catalogue) always included free. Upgrade to add more modules anytime.',
                    features: ['Core apps included', '1 industry module', '60 days free', 'No commitment'],
                    featColor: '#0D9488',
                    selectedBorder: '#0D9488', selectedBg: '#F0FDF4',
                  },
                  {
                    type: 'paid' as PlanType,
                    icon: Zap, iconBg: '#EFF6FF', iconBorder: '#BFDBFE', iconColor: '#1D4ED8',
                    title: 'Sign up with full module access',
                    badge: 'Choose your modules', badgeBg: '#EFF6FF', badgeColor: '#1D4ED8',
                    desc: 'Pick the exact modules your business needs. All modules start on a 60-day free trial — no charges until after the trial. Monthly billing, cancel anytime.',
                    features: ['All 15 modules available', '60-day trial on each', 'Monthly billing', 'Cancel anytime'],
                    featColor: '#1D4ED8',
                    selectedBorder: '#1B3A6B', selectedBg: '#EFF6FF',
                  },
                ].map(opt => {
                  const sel = planType === opt.type
                  const Icon = opt.icon
                  return (
                    <div key={opt.type} onClick={() => setPlanType(opt.type)}
                      style={{ border: `2px solid ${sel ? opt.selectedBorder : '#E2E8F0'}`, borderRadius: 14, padding: '18px 20px', cursor: 'pointer', background: sel ? opt.selectedBg : '#fff', transition: 'all 0.15s' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                          <div style={{ width: 40, height: 40, borderRadius: 10, background: opt.iconBg, border: `2px solid ${opt.iconBorder}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                            <Icon size={18} color={opt.iconColor} />
                          </div>
                          <div>
                            <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 3 }}>
                              {opt.title}
                              <span style={{ marginLeft: 8, background: opt.badgeBg, color: opt.badgeColor, fontSize: 11, padding: '2px 8px', borderRadius: 20, fontWeight: 600 }}>{opt.badge}</span>
                            </div>
                            <div style={{ fontSize: 13, color: '#64748B', lineHeight: 1.5, maxWidth: 380 }}>{opt.desc}</div>
                            <div style={{ display: 'flex', gap: 14, marginTop: 10, flexWrap: 'wrap' }}>
                              {opt.features.map(f => (
                                <div key={f} style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: opt.featColor }}>
                                  <Check size={11} strokeWidth={3} /> {f}
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>
                        <div style={{ width: 22, height: 22, borderRadius: '50%', border: `2px solid ${sel ? opt.selectedBorder : '#E2E8F0'}`, background: sel ? opt.selectedBorder : 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          {sel && <Check size={11} color="white" strokeWidth={3} />}
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>

              <button onClick={goNext} disabled={!planType}
                style={{ ...btnPrimary, width: '100%', marginTop: 20, justifyContent: 'center', opacity: planType ? 1 : 0.4 }}>
                Continue <ChevronRight size={16} />
              </button>

              <p style={{ marginTop: 16, textAlign: 'center', fontSize: 13, color: '#94A3B8' }}>
                Already have an account?{' '}
                <a href="/login" style={{ color: '#1B3A6B', fontWeight: 600, textDecoration: 'none' }}>Sign in</a>
              </p>
            </div>
          )}

          {/* STEP 1 — Company */}
          {step === 1 && (
            <div>
              <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Set up your company</h2>
              <p style={{ fontSize: 14, color: '#64748B', margin: '0 0 24px' }}>You can update these details later in Settings.</p>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <Field label="Company Name *">
                  <input value={company.companyName} onChange={e => handleCompanyName(e.target.value)}
                    placeholder="Zeta Earthmoving (Pty) Ltd" style={inputStyle} autoFocus />
                </Field>
                <Field label="Company Slug *" hint="Auto-generated from name. Used to identify your company on login.">
                  <input value={company.slug}
                    onChange={e => cf('slug', e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))}
                    placeholder="zeta-earthmoving" style={inputStyle} />
                  {company.slug && (
                    <div style={{ marginTop: 5, fontSize: 12, color: '#0D9488' }}>Login slug: <strong>{company.slug}</strong></div>
                  )}
                </Field>
                <Field label="Phone Number">
                  <input value={company.phone} onChange={e => cf('phone', e.target.value)} placeholder="+27 11 555 0100" style={inputStyle} />
                </Field>
              </div>

              {error && <ErrMsg msg={error} />}

              <div style={{ display: 'flex', gap: 10, marginTop: 24 }}>
                <button onClick={goBack} style={btnOutline}><ChevronLeft size={15} /> Back</button>
                <button onClick={goNext}
                  disabled={company.companyName.length < 2 || company.slug.length < 3}
                  style={{ ...btnPrimary, flex: 1, justifyContent: 'center', opacity: company.companyName.length >= 2 && company.slug.length >= 3 ? 1 : 0.4 }}>
                  Continue <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}

          {/* STEP 2 — Pilot: pick 1 module */}
          {step === 2 && planType === 'pilot' && (
            <div>
              <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Choose your pilot module</h2>
              <p style={{ fontSize: 14, color: '#64748B', margin: '0 0 8px' }}>Select <strong>one module</strong> to trial. CRM, Invoicing & Catalogue are always free.</p>
              <div style={{ padding: '10px 14px', background: '#F0FDF4', border: '1px solid #86EFAC', borderRadius: 8, fontSize: 13, color: '#166534', marginBottom: 20 }}>
                After your 60-day pilot you can add more modules. Your data is always kept safe.
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(175px, 1fr))', gap: 10, marginBottom: 20 }}>
                {INDUSTRY_MODULES.map(m => {
                  const sel = pilotModule === m.moduleKey
                  const Icon = MODULE_ICONS[m.moduleKey] || Package
                  const c = MODULE_COLORS[m.moduleKey] || { bg: '#F8FAFC', color: '#64748B' }
                  return (
                    <div key={m.moduleKey} onClick={() => setPilotModule(m.moduleKey)}
                      style={{ border: sel ? `2px solid ${c.color}` : '2px solid #E2E8F0', borderRadius: 12, padding: '14px 16px', cursor: 'pointer', background: sel ? c.bg : '#fff', position: 'relative', transition: 'all 0.15s' }}>
                      {sel && (
                        <div style={{ position: 'absolute', top: 8, right: 8, width: 18, height: 18, borderRadius: '50%', background: c.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          <Check size={10} color="white" strokeWidth={3} />
                        </div>
                      )}
                      <div style={{ width: 32, height: 32, borderRadius: 8, background: c.bg, border: `1px solid ${c.color}30`, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                        <Icon size={16} color={c.color} />
                      </div>
                      <div style={{ fontWeight: 700, fontSize: 13, color: '#0F172A', marginBottom: 2 }}>{m.name}</div>
                      <div style={{ fontSize: 11, color: '#94A3B8', lineHeight: 1.4, marginBottom: 6 }}>{m.description}</div>
                      <div style={{ fontSize: 12, fontWeight: 700, color: c.color }}>R {m.monthlyPrice}/mo after trial</div>
                    </div>
                  )
                })}
              </div>

              {error && <ErrMsg msg={error} />}

              <div style={{ display: 'flex', gap: 10 }}>
                <button onClick={goBack} style={btnOutline}><ChevronLeft size={15} /> Back</button>
                <button onClick={goNext} disabled={!pilotModule}
                  style={{ ...btnPrimary, flex: 1, justifyContent: 'center', opacity: pilotModule ? 1 : 0.4 }}>
                  Continue <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}

          {/* STEP 2 — Paid: pick modules */}
          {step === 2 && planType === 'paid' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
                <div>
                  <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Choose your modules</h2>
                  <p style={{ fontSize: 14, color: '#64748B', margin: 0 }}>All modules start on a 60-day free trial. Add or remove anytime in Billing.</p>
                </div>
                <div style={{ textAlign: 'right', flexShrink: 0, marginLeft: 20 }}>
                  <div style={{ fontSize: 11, color: '#94A3B8', marginBottom: 2 }}>AFTER 60-DAY TRIAL</div>
                  <div style={{ fontSize: 22, fontWeight: 800, color: '#1B3A6B' }}>R {monthlyTotal.toLocaleString('en-ZA')}/mo</div>
                  <div style={{ fontSize: 11, color: '#94A3B8' }}>{selectedModules.size} modules · Free now</div>
                </div>
              </div>

              <div style={{ padding: '10px 14px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 8, fontSize: 13, color: '#1D4ED8', marginBottom: 20 }}>
                CRM, Invoicing and Catalogue are free in every plan.
              </div>

              {['Core', 'Industry', 'Business'].map(cat => (
                <div key={cat} style={{ marginBottom: 20 }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.06em', marginBottom: 10 }}>{cat.toUpperCase()}</div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(185px, 1fr))', gap: 10 }}>
                    {ALL_MODULES.filter(m => m.category === cat).map(m => {
                      const isCore = CORE_KEYS.includes(m.moduleKey)
                      const sel = selectedModules.has(m.moduleKey)
                      const Icon = MODULE_ICONS[m.moduleKey] || Package
                      const c = MODULE_COLORS[m.moduleKey] || { bg: '#F8FAFC', color: '#64748B' }
                      return (
                        <div key={m.moduleKey} onClick={() => !isCore && togglePaidModule(m.moduleKey)}
                          style={{ border: sel ? `2px solid ${c.color}` : '2px solid #E2E8F0', borderRadius: 12, padding: '14px 16px', cursor: isCore ? 'default' : 'pointer', background: sel ? c.bg : '#fff', position: 'relative', transition: 'all 0.15s' }}>
                          {sel && !isCore && (
                            <div style={{ position: 'absolute', top: 8, right: 8, width: 18, height: 18, borderRadius: '50%', background: c.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                              <Check size={10} color="white" strokeWidth={3} />
                            </div>
                          )}
                          {isCore && <div style={{ position: 'absolute', top: 8, right: 8, fontSize: 9, background: '#F1F5F9', color: '#64748B', padding: '1px 5px', borderRadius: 4, fontWeight: 600 }}>FREE</div>}
                          <div style={{ width: 32, height: 32, borderRadius: 8, background: c.bg, border: `1px solid ${c.color}30`, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                            <Icon size={16} color={c.color} />
                          </div>
                          <div style={{ fontWeight: 700, fontSize: 13, color: '#0F172A', marginBottom: 2 }}>{m.name}</div>
                          <div style={{ fontSize: 11, color: '#94A3B8', lineHeight: 1.4, marginBottom: 6 }}>{m.description}</div>
                          <div style={{ fontSize: 12, fontWeight: 700, color: m.monthlyPrice === 0 ? '#166534' : c.color }}>
                            {m.monthlyPrice === 0 ? 'Included free' : `R ${m.monthlyPrice}/mo after trial`}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              ))}

              {error && <ErrMsg msg={error} />}

              <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
                <button onClick={goBack} style={btnOutline}><ChevronLeft size={15} /> Back</button>
                <button onClick={goNext} style={{ ...btnPrimary, flex: 1, justifyContent: 'center' }}>
                  Continue with {selectedModules.size} modules <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}

          {/* STEP 3 — Account (both plan types) */}
          {step === 3 && (
            <div>
              <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Create your account</h2>
              <p style={{ fontSize: 14, color: '#64748B', margin: '0 0 24px' }}>
                You'll use this to sign in to <strong>{company.companyName}</strong>.
              </p>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <Field label="First Name *">
                    <input value={account.firstName} onChange={e => af('firstName', e.target.value)} placeholder="Jane" style={inputStyle} autoFocus />
                  </Field>
                  <Field label="Last Name *">
                    <input value={account.lastName} onChange={e => af('lastName', e.target.value)} placeholder="Dlamini" style={inputStyle} />
                  </Field>
                </div>
                <Field label="Work Email *">
                  <input type="email" value={account.email} onChange={e => af('email', e.target.value)} placeholder="jane@company.co.za" style={inputStyle} />
                </Field>
                <Field label="Password *">
                  <div style={{ position: 'relative' }}>
                    <input type={showPass ? 'text' : 'password'} value={account.password}
                      onChange={e => af('password', e.target.value)} placeholder="At least 8 characters"
                      style={{ ...inputStyle, paddingRight: 44 }} />
                    <button type="button" onClick={() => setShowPass(s => !s)}
                      style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}>
                      {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </Field>
                <Field label="Confirm Password *">
                  <div style={{ position: 'relative' }}>
                    <input type={showConfirmPass ? 'text' : 'password'} value={account.confirmPassword}
                      onChange={e => af('confirmPassword', e.target.value)} placeholder="Re-enter your password"
                      style={{ ...inputStyle, paddingRight: 44, borderColor: account.confirmPassword && account.password !== account.confirmPassword ? '#DC2626' : '#E2E8F0' }} />
                    <button type="button" onClick={() => setShowConfirmPass(s => !s)}
                      style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}>
                      {showConfirmPass ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                  {account.confirmPassword && account.password !== account.confirmPassword && (
                    <div style={{ marginTop: 4, fontSize: 12, color: '#DC2626' }}>Passwords do not match</div>
                  )}
                </Field>
              </div>

              {/* Summary */}
              <div style={{ marginTop: 20, padding: '14px 16px', background: '#F8FAFC', borderRadius: 10, border: '1px solid #E2E8F0' }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B', marginBottom: 10, letterSpacing: '0.05em' }}>YOUR PLAN SUMMARY</div>
                {[
                  ['Company', company.companyName],
                  ['Plan type', planType === 'pilot' ? '60-day Pilot (1 industry module)' : 'Full signup'],
                  ['Modules', `${activeModuleKeys.length} modules`],
                  ['Trial period', '60 days free on all modules'],
                ].map(([label, value]) => (
                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 5 }}>
                    <span style={{ color: '#64748B' }}>{label}</span>
                    <span style={{ fontWeight: 600, color: '#0F172A' }}>{value}</span>
                  </div>
                ))}
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, borderTop: '1px solid #E2E8F0', paddingTop: 8, marginTop: 4 }}>
                  <span style={{ color: '#64748B' }}>After trial</span>
                  <span style={{ fontWeight: 700, color: monthlyTotal === 0 ? '#166534' : '#0F172A' }}>
                    {monthlyTotal === 0 ? 'Free' : `R ${monthlyTotal.toLocaleString('en-ZA')}/month`}
                  </span>
                </div>
              </div>

              {/* Terms */}
              <div onClick={() => setAgreedToTerms(t => !t)}
                style={{ marginTop: 16, padding: '12px 14px', background: agreedToTerms ? '#F0FDF4' : '#F8FAFC', border: `1px solid ${agreedToTerms ? '#86EFAC' : '#E2E8F0'}`, borderRadius: 10, cursor: 'pointer', display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                <div style={{ width: 18, height: 18, borderRadius: 5, flexShrink: 0, marginTop: 1, border: `2px solid ${agreedToTerms ? '#0D9488' : '#D1D5DB'}`, background: agreedToTerms ? '#0D9488' : 'white', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {agreedToTerms && <Check size={11} color="white" strokeWidth={3} />}
                </div>
                <div style={{ fontSize: 13, color: '#475569', lineHeight: 1.5 }}>
                  I agree to HandyFlow's{' '}
                  <a href="/terms" target="_blank" onClick={e => e.stopPropagation()} style={{ color: '#1B3A6B', fontWeight: 600, textDecoration: 'none' }}>Terms of Service <ExternalLink size={10} style={{ display: 'inline', verticalAlign: 'middle' }} /></a>
                  {' '}and{' '}
                  <a href="/privacy" target="_blank" onClick={e => e.stopPropagation()} style={{ color: '#1B3A6B', fontWeight: 600, textDecoration: 'none' }}>Privacy Policy <ExternalLink size={10} style={{ display: 'inline', verticalAlign: 'middle' }} /></a>.
                  {' '}My data will be processed in accordance with POPIA.
                  {planType === 'paid' && ' After the 60-day trial, selected modules will be billed monthly.'}
                </div>
              </div>

              {error && <ErrMsg msg={error} />}

              <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
                <button onClick={goBack} style={btnOutline}><ChevronLeft size={15} /> Back</button>
                <button onClick={handleSubmit} disabled={register.isPending || !agreedToTerms}
                  style={{ ...btnPrimary, flex: 1, justifyContent: 'center', opacity: agreedToTerms ? 1 : 0.4 }}>
                  {register.isPending ? 'Creating account...' : planType === 'pilot' ? 'Start free pilot 🚀' : 'Create account 🚀'}
                </button>
              </div>

              <p style={{ marginTop: 16, textAlign: 'center', fontSize: 13, color: '#94A3B8' }}>
                Already have an account?{' '}
                <a href="/login" style={{ color: '#1B3A6B', fontWeight: 600, textDecoration: 'none' }}>Sign in</a>
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>{label}</label>
      {children}
      {hint && <div style={{ marginTop: 5, fontSize: 12, color: '#94A3B8' }}>{hint}</div>}
    </div>
  )
}

function ErrMsg({ msg }: { msg: string }) {
  return (
    <div style={{ marginTop: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{msg}</div>
  )
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '11px 14px', border: '1.5px solid #E2E8F0',
  borderRadius: 10, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff', color: '#0F172A',
}
const btnPrimary: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8, background: '#1B3A6B', color: '#fff',
  border: 'none', borderRadius: 10, padding: '13px 24px', fontSize: 15, fontWeight: 700, cursor: 'pointer',
}
const btnOutline: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 6, background: '#fff', color: '#475569',
  border: '1.5px solid #E2E8F0', borderRadius: 10, padding: '13px 18px', fontSize: 14, cursor: 'pointer', flexShrink: 0,
}
