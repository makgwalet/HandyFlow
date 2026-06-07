// src/pages/modules/AdminNewModulePage.tsx
import { useState, useEffect } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { adminApi } from '../../api/client'
import {
  Package, Plus, CheckCircle, AlertTriangle, ArrowLeft,
  ChevronRight, Zap, Shield, Eye, Settings, X,
  RefreshCw, Tag, DollarSign,
} from 'lucide-react'

const inp: React.CSSProperties = {
  width: '100%', padding: '10px 13px', border: '1.5px solid #2D3748',
  borderRadius: 8, fontSize: 13, background: '#1A202C', color: '#F7FAFC',
  outline: 'none', boxSizing: 'border-box' as const, fontFamily: 'inherit',
}
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 11, fontWeight: 700, color: '#718096',
  marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.06em',
}
const btnP: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6,
  background: 'linear-gradient(135deg,#1B3A6B,#2563EB)', color: '#fff',
  border: 'none', borderRadius: 9, padding: '11px 20px',
  fontSize: 14, fontWeight: 700, cursor: 'pointer',
}
const fmtR = (n: any) => n ? `R ${Number(n).toFixed(2)}` : 'R 0.00'

const CATEGORIES = ['CORE','FINANCE','OPERATIONS','INDUSTRY','ENTERPRISE','OTHER']
const CATEGORY_COLOR: Record<string, string> = {
  CORE: '#0D9488', FINANCE: '#60A5FA', OPERATIONS: '#F6AD55',
  INDUSTRY: '#B794F4', ENTERPRISE: '#FC8181', OTHER: '#718096',
}

// Common lucide icons available for module assignment
const ICON_OPTIONS = [
  'Package','BarChart2','Users','FileText','Settings','Shield','Zap',
  'Building2','Truck','Wrench','Globe','Database','CreditCard','Calendar',
  'Map','Layers','Box','Camera','BookOpen','Briefcase','Tool','Layout',
  'Activity','Award','CheckSquare','Clock','Code','Edit3','Flag','Heart',
  'Home','Key','Link','Lock','Mail','Monitor','Phone','PieChart','Search',
  'Server','Star','Target','TrendingUp','Upload','Video','Wifi',
]

function Step({ n, label, active, done }: { n: number; label: string; active: boolean; done: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div style={{
        width: 28, height: 28, borderRadius: '50%', flexShrink: 0,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: done ? '#0D9488' : active ? '#2563EB' : '#2D3748',
        fontSize: 12, fontWeight: 700, color: '#fff',
      }}>
        {done ? <CheckCircle size={14} /> : n}
      </div>
      <span style={{ fontSize: 13, color: active ? '#F7FAFC' : done ? '#0D9488' : '#4A5568', fontWeight: active ? 700 : 400 }}>
        {label}
      </span>
    </div>
  )
}

export function AdminNewModulePage() {
  const navigate = useNavigate()

  const [step,            setStep]            = useState<1 | 2 | 3>(1)
  const [key,             setKey]             = useState('')
  const [name,            setName]            = useState('')
  const [description,     setDescription]     = useState('')
  const [monthlyPrice,    setMonthlyPrice]     = useState('')
  const [icon,            setIcon]            = useState('Package')
  const [category,        setCategory]        = useState('OPERATIONS')
  const [sortOrder,       setSortOrder]       = useState('500')
  const [extraPerms,      setExtraPerms]      = useState<string[]>([])
  const [customPermInput, setCustomPermInput] = useState('')
  const [error,           setError]           = useState('')
  const [result,          setResult]          = useState<any>(null)

  // Auto-generate key from name
  useEffect(() => {
    if (name && !key) {
      setKey(name.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '').slice(0, 50))
    }
  }, [name])

  // Validate key in real time
  const keyValid  = /^[A-Z][A-Z0-9_]{1,48}[A-Z0-9]$/.test(key)
  const step1Done = !!name && !!monthlyPrice && keyValid && parseFloat(monthlyPrice) > 0

  // Standard permissions always created
  const stdPerms = key ? [key + '_READ', key + '_MANAGE', key + '_ADMIN'] : ['KEY_READ','KEY_MANAGE','KEY_ADMIN']
  const allPerms = [...stdPerms, ...extraPerms]

  const { data: existingPerms = [] } = useQuery<any[]>({
    queryKey: ['admin-all-permissions'],
    queryFn: async () => { const r = await adminApi.get('/permissions'); return r.data?.data ?? r.data ?? [] },
  })

  const createModule = useMutation({
    mutationFn: () => adminApi.post('/modules', {
      key, name, description, monthlyPrice: parseFloat(monthlyPrice),
      icon, category, sortOrder: parseInt(sortOrder) || 500,
      extraPermissions: extraPerms,
    }),
    onSuccess: (r) => {
      setResult(r.data?.data ?? r.data)
      setStep(3)
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create module'),
  })

  const addCustomPerm = () => {
    const p = customPermInput.toUpperCase().replace(/[^A-Z0-9_]/g, '_')
    if (p && !allPerms.includes(p)) {
      setExtraPerms(prev => [...prev, p])
    }
    setCustomPermInput('')
  }

  return (
    <div style={{ color: '#F7FAFC', maxWidth: 860, margin: '0 auto' }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 28 }}>
        <button onClick={() => navigate('/modules')}
          style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', color: '#718096', fontSize: 13 }}>
          <ArrowLeft size={15} /> Back to modules
        </button>
        <div style={{ height: 18, width: 1, background: '#2D3748' }} />
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 800, margin: 0 }}>New module</h1>
          <div style={{ fontSize: 12, color: '#4A5568', marginTop: 2 }}>
            Creates catalogue entry · generates permissions · auto-grants to all ADMIN roles
          </div>
        </div>
      </div>

      {/* Progress steps */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 28, padding: '14px 20px', background: '#13161E', border: '1px solid #1E2532', borderRadius: 10 }}>
        <Step n={1} label="Module details" active={step === 1} done={step > 1} />
        <ChevronRight size={14} color="#2D3748" />
        <Step n={2} label="Permissions" active={step === 2} done={step > 2} />
        <ChevronRight size={14} color="#2D3748" />
        <Step n={3} label="Done" active={step === 3} done={false} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 20, alignItems: 'start' }}>

        {/* LEFT: form */}
        <div>

          {/* Step 1: Module Details */}
          {step === 1 && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 14, padding: 24 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC', marginBottom: 20 }}>Step 1 — Module details</div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {/* Name */}
                <div>
                  <label style={lbl}>Module name *</label>
                  <input autoFocus value={name} onChange={e => setName(e.target.value)} placeholder="Fleet Tracking" style={inp} />
                  <div style={{ fontSize: 11, color: '#4A5568', marginTop: 4 }}>What tenants see in their billing and modules list</div>
                </div>

                {/* Key */}
                <div>
                  <label style={lbl}>Module key * (auto-generated, editable)</label>
                  <input value={key} onChange={e => setKey(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ''))}
                    placeholder="FLEET_TRACKING" style={{ ...inp, fontFamily: 'monospace', color: keyValid ? '#0D9488' : key ? '#FC8181' : '#F7FAFC' }} />
                  {key && !keyValid && (
                    <div style={{ fontSize: 11, color: '#FC8181', marginTop: 4 }}>Must be UPPER_SNAKE_CASE, 2–50 chars, start and end with letter/number</div>
                  )}
                  {key && keyValid && (
                    <div style={{ fontSize: 11, color: '#0D9488', marginTop: 4 }}>✓ Valid key — will generate {key}_READ, {key}_MANAGE, {key}_ADMIN</div>
                  )}
                </div>

                {/* Price */}
                <div>
                  <label style={lbl}>Monthly price (R) *</label>
                  <div style={{ position: 'relative' as const }}>
                    <span style={{ position: 'absolute' as const, left: 12, top: '50%', transform: 'translateY(-50%)', color: '#4A5568', fontSize: 13 }}>R</span>
                    <input type="number" step="0.01" min="0" value={monthlyPrice}
                      onChange={e => setMonthlyPrice(e.target.value)}
                      placeholder="349.00" style={{ ...inp, paddingLeft: 28 }} />
                  </div>
                </div>

                {/* Category + Icon */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div>
                    <label style={lbl}>Category</label>
                    <select value={category} onChange={e => setCategory(e.target.value)} style={{ ...inp, background: '#1A202C' }}>
                      {CATEGORIES.map(c => <option key={c}>{c}</option>)}
                    </select>
                  </div>
                  <div>
                    <label style={lbl}>Sort order</label>
                    <input type="number" value={sortOrder} onChange={e => setSortOrder(e.target.value)} placeholder="500" style={inp} />
                    <div style={{ fontSize: 11, color: '#4A5568', marginTop: 4 }}>Lower = appears first in catalogue</div>
                  </div>
                </div>

                {/* Icon */}
                <div>
                  <label style={lbl}>Icon (lucide-react name)</label>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <select value={icon} onChange={e => setIcon(e.target.value)} style={{ ...inp, background: '#1A202C', flex: 1 }}>
                      {ICON_OPTIONS.map(i => <option key={i}>{i}</option>)}
                    </select>
                    <div style={{ width: 40, height: 40, borderRadius: 9, background: '#1A202C', border: '1px solid #2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      <Package size={18} color="#0D9488" />
                    </div>
                  </div>
                </div>

                {/* Description */}
                <div>
                  <label style={lbl}>Description (shown to tenants)</label>
                  <textarea value={description} onChange={e => setDescription(e.target.value)} rows={3}
                    placeholder="Real-time GPS tracking and management for your vehicle fleet."
                    style={{ ...inp, resize: 'vertical' as const, fontFamily: 'inherit' }} />
                </div>
              </div>

              {error && (
                <div style={{ marginTop: 14, padding: '10px 14px', background: '#3B1515', border: '1px solid #FC818150', borderRadius: 8, fontSize: 13, color: '#FC8181' }}>
                  {error}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 22 }}>
                <button onClick={() => { setError(''); setStep(2) }} disabled={!step1Done}
                  style={{ ...btnP, opacity: step1Done ? 1 : 0.4 }}>
                  Next: Permissions <ChevronRight size={15} />
                </button>
              </div>
            </div>
          )}

          {/* Step 2: Permissions */}
          {step === 2 && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 14, padding: 24 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC', marginBottom: 6 }}>Step 2 — Permissions</div>
              <div style={{ fontSize: 12, color: '#4A5568', marginBottom: 20 }}>
                Three standard permissions are always created. Add extra ones for finer access control.
              </div>

              {/* Standard permissions */}
              <div style={{ marginBottom: 20 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Standard permissions (always created)</div>
                {stdPerms.map(p => {
                  const suffix = p.split('_').pop()
                  const Icon = suffix === 'READ' ? Eye : suffix === 'MANAGE' ? Settings : Shield
                  const color = suffix === 'READ' ? '#60A5FA' : suffix === 'MANAGE' ? '#F6AD55' : '#FC8181'
                  return (
                    <div key={p} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8, marginBottom: 6 }}>
                      <Icon size={14} color={color} />
                      <div style={{ flex: 1 }}>
                        <div style={{ fontFamily: 'monospace', fontSize: 12, color, fontWeight: 700 }}>{p}</div>
                        <div style={{ fontSize: 11, color: '#4A5568', marginTop: 1 }}>
                          {suffix === 'READ' ? `View ${name} data` : suffix === 'MANAGE' ? `Create and manage ${name} records` : `Full administrative access to ${name}`}
                        </div>
                      </div>
                      <CheckCircle size={13} color="#0D9488" />
                    </div>
                  )
                })}
              </div>

              {/* Extra permissions */}
              <div style={{ marginBottom: 20 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>
                  Extra permissions (optional)
                </div>

                {extraPerms.length > 0 && (
                  <div style={{ marginBottom: 10 }}>
                    {extraPerms.map(p => (
                      <div key={p} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8, marginBottom: 5 }}>
                        <Zap size={13} color="#B794F4" />
                        <span style={{ fontFamily: 'monospace', fontSize: 12, color: '#B794F4', flex: 1 }}>{p}</span>
                        <button onClick={() => setExtraPerms(prev => prev.filter(x => x !== p))}
                          style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#4A5568', display: 'flex', padding: 2 }}>
                          <X size={12} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                <div style={{ display: 'flex', gap: 8 }}>
                  <input value={customPermInput}
                    onChange={e => setCustomPermInput(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ''))}
                    onKeyDown={e => e.key === 'Enter' && addCustomPerm()}
                    placeholder={`${key || 'KEY'}_EXPORT`}
                    style={{ ...inp, fontFamily: 'monospace', flex: 1 }} />
                  <button onClick={addCustomPerm} disabled={!customPermInput}
                    style={{ ...btnP, padding: '10px 14px', opacity: customPermInput ? 1 : 0.4 }}>
                    <Plus size={14} /> Add
                  </button>
                </div>
                <div style={{ fontSize: 11, color: '#4A5568', marginTop: 6 }}>
                  Example: {key || 'KEY'}_EXPORT, {key || 'KEY'}_APPROVE, {key || 'KEY'}_REPORTS
                </div>
              </div>

              {/* Auto-grant notice */}
              <div style={{ padding: '12px 16px', background: '#0D948815', border: '1px solid #0D948830', borderRadius: 8, display: 'flex', gap: 10, alignItems: 'flex-start', marginBottom: 20 }}>
                <Zap size={15} color="#0D9488" style={{ flexShrink: 0, marginTop: 1 }} />
                <div style={{ fontSize: 12, color: '#718096', lineHeight: 1.6 }}>
                  All {allPerms.length} permission{allPerms.length !== 1 ? 's' : ''} will be automatically granted to every <strong style={{ color: '#F7FAFC' }}>ADMIN role</strong> across all tenants.
                  Non-admin users get access through <strong style={{ color: '#F7FAFC' }}>Settings → Team → Roles</strong> on each tenant.
                </div>
              </div>

              {error && (
                <div style={{ marginBottom: 14, padding: '10px 14px', background: '#3B1515', border: '1px solid #FC818150', borderRadius: 8, fontSize: 13, color: '#FC8181' }}>
                  {error}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                <button onClick={() => { setStep(1); setError('') }}
                  style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: '1.5px solid #2D3748', borderRadius: 8, padding: '10px 16px', cursor: 'pointer', color: '#A0AEC0', fontSize: 13 }}>
                  <ArrowLeft size={14} /> Back
                </button>
                <button onClick={() => createModule.mutate()} disabled={createModule.isPending}
                  style={{ ...btnP, background: createModule.isPending ? '#2D3748' : 'linear-gradient(135deg,#065F46,#0D9488)' }}>
                  {createModule.isPending
                    ? <><RefreshCw size={14} style={{ animation: 'spin 1s linear infinite' }} /> Creating...</>
                    : <><Zap size={14} /> Create module</>}
                </button>
              </div>
              <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
            </div>
          )}

          {/* Step 3: Success */}
          {step === 3 && result && (
            <div style={{ background: '#13161E', border: '1px solid #0D948830', borderRadius: 14, padding: 28 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 24 }}>
                <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#0D948820', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <CheckCircle size={28} color="#0D9488" />
                </div>
                <div>
                  <div style={{ fontSize: 18, fontWeight: 800, color: '#F7FAFC' }}>Module created successfully</div>
                  <div style={{ fontSize: 13, color: '#4A5568', marginTop: 2 }}>{result.name} is now live in the catalogue</div>
                </div>
              </div>

              {/* Stats */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 22 }}>
                {[
                  { label: 'Module key',    value: result.key,                        color: '#0D9488' },
                  { label: 'Permissions',   value: `${result.permissionsCreated?.length ?? 0} created`,  color: '#B794F4' },
                  { label: 'ADMIN grants',  value: `${result.adminRoleGrantsCount ?? 0} roles`,          color: '#F6AD55' },
                ].map(s => (
                  <div key={s.label} style={{ background: '#1A202C', border: '1px solid #2D3748', borderRadius: 10, padding: '12px 16px', textAlign: 'center' as const }}>
                    <div style={{ fontSize: 16, fontWeight: 800, color: s.color, fontFamily: s.label === 'Module key' ? 'monospace' : 'inherit' }}>{s.value}</div>
                    <div style={{ fontSize: 11, color: '#4A5568', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', marginTop: 4 }}>{s.label}</div>
                  </div>
                ))}
              </div>

              {/* Permissions created */}
              <div style={{ marginBottom: 22 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Permissions created</div>
                {(result.permissionsCreated ?? []).map((p: string) => (
                  <div key={p} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '7px 12px', background: '#1A202C', borderRadius: 7, marginBottom: 5 }}>
                    <CheckCircle size={12} color="#0D9488" />
                    <span style={{ fontFamily: 'monospace', fontSize: 12, color: '#0D9488' }}>{p}</span>
                  </div>
                ))}
              </div>

              {/* Next steps checklist */}
              <div style={{ background: '#1A202C', border: '1px solid #2D3748', borderRadius: 10, padding: '16px 18px', marginBottom: 22 }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: '#F7FAFC', marginBottom: 12 }}>What to do next</div>
                {[
                  { step: '1', text: `Write the Spring Boot service + controller for ${result.key}`, done: false },
                  { step: '2', text: `Create a Flyway migration V${68}+ for the ${result.key} database tables`, done: false },
                  { step: '3', text: `Build the React frontend page at src/pages/${result.key?.toLowerCase()}/`, done: false },
                  { step: '4', text: `Add the route in App.tsx and the nav item in Sidebar`, done: false },
                  { step: '5', text: `Test module activation in tenant Settings → Modules`, done: false },
                  { step: '6', text: `Set the admin_notes on the Lookups → Modules tab once done`, done: false },
                ].map(item => (
                  <div key={item.step} style={{ display: 'flex', gap: 10, padding: '6px 0', borderBottom: '1px solid #2D3748', fontSize: 12, color: '#718096', alignItems: 'flex-start' }}>
                    <div style={{ width: 18, height: 18, borderRadius: '50%', background: '#2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700, color: '#718096', flexShrink: 0, marginTop: 1 }}>{item.step}</div>
                    {item.text}
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', gap: 10 }}>
                <button onClick={() => navigate('/modules')} style={{ flex: 1, ...btnP, justifyContent: 'center' }}>
                  <Package size={14} /> View module catalogue
                </button>
                <button onClick={() => { setStep(1); setKey(''); setName(''); setDescription(''); setMonthlyPrice(''); setIcon('Package'); setCategory('OPERATIONS'); setSortOrder('500'); setExtraPerms([]); setResult(null); setError('') }}
                  style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '11px 18px', border: '1.5px solid #2D3748', borderRadius: 9, background: '#1A202C', fontSize: 13, cursor: 'pointer', color: '#A0AEC0' }}>
                  <Plus size={14} /> Add another
                </button>
              </div>
            </div>
          )}
        </div>

        {/* RIGHT: live preview card */}
        {step < 3 && (
          <div style={{ position: 'sticky' as const, top: 84 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Live preview</div>

            {/* Module card preview */}
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden', marginBottom: 14 }}>
              <div style={{ height: 4, background: `linear-gradient(90deg, ${CATEGORY_COLOR[category] || '#718096'}, transparent)` }} />
              <div style={{ padding: 18 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: `${CATEGORY_COLOR[category] || '#718096'}20`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Package size={20} color={CATEGORY_COLOR[category] || '#718096'} />
                  </div>
                  <span style={{ fontSize: 10, fontWeight: 700, color: CATEGORY_COLOR[category] || '#718096', background: `${CATEGORY_COLOR[category] || '#718096'}20`, padding: '2px 8px', borderRadius: 10 }}>{category}</span>
                </div>
                <div style={{ fontSize: 15, fontWeight: 700, color: '#F7FAFC', marginBottom: 4 }}>{name || 'Module name'}</div>
                <div style={{ fontSize: 12, color: '#718096', marginBottom: 14, lineHeight: 1.5 }}>{description || 'Module description will appear here...'}</div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ fontFamily: 'monospace', fontSize: 10, color: '#4A5568' }}>{key || 'MODULE_KEY'}</div>
                  <div style={{ fontSize: 14, fontWeight: 800, color: '#0D9488' }}>{monthlyPrice ? fmtR(monthlyPrice) : 'R 0.00'}<span style={{ fontSize: 10, color: '#4A5568', fontWeight: 400 }}>/mo</span></div>
                </div>
              </div>
            </div>

            {/* Permission summary */}
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 10, padding: 16 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Will create {allPerms.length} permission{allPerms.length !== 1 ? 's' : ''}</div>
              {allPerms.map((p, i) => (
                <div key={p} style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '5px 0', borderBottom: i < allPerms.length - 1 ? '1px solid #1E2532' : 'none' }}>
                  <div style={{ width: 5, height: 5, borderRadius: '50%', background: i < 3 ? '#0D9488' : '#B794F4', flexShrink: 0 }} />
                  <span style={{ fontFamily: 'monospace', fontSize: 11, color: i < 3 ? '#0D9488' : '#B794F4' }}>{p}</span>
                </div>
              ))}
            </div>

            {/* Pricing context */}
            {monthlyPrice && (
              <div style={{ marginTop: 14, background: '#13161E', border: '1px solid #1E2532', borderRadius: 10, padding: 16 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Revenue projection</div>
                {[10, 25, 50, 100].map(tenants => (
                  <div key={tenants} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 12 }}>
                    <span style={{ color: '#4A5568' }}>{tenants} tenants</span>
                    <span style={{ color: '#F7FAFC', fontWeight: 600 }}>{fmtR(parseFloat(monthlyPrice) * tenants)}/mo</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
