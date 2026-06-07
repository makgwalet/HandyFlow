// src/pages/lookups/AdminLookupsPage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import {
  Calendar, Calculator, Tag, Package,
  Plus, Trash2, Edit3, CheckCircle, X,
  AlertTriangle, RefreshCw, ChevronDown, ChevronUp,
  Save, Eye, EyeOff,
} from 'lucide-react'

// ── Shared styles ─────────────────────────────────────────────────────────────
const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px', border: '1.5px solid #2D3748',
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
  border: 'none', borderRadius: 8, padding: '9px 16px',
  fontSize: 13, fontWeight: 600, cursor: 'pointer',
}
const btnS: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6,
  padding: '8px 14px', border: '1.5px solid #2D3748',
  borderRadius: 8, background: '#1A202C', fontSize: 13,
  cursor: 'pointer', color: '#A0AEC0', fontWeight: 500,
}
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}` : '—'
const fmtDate = (d: any) => d ? new Date(d + 'T00:00:00').toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtPct  = (n: any) => n != null ? `${Number(n).toFixed(0)}%` : '—'

type Tab = 'holidays' | 'tax' | 'discounts' | 'modules'

function Toast({ msg, ok, onDismiss }: { msg: string; ok: boolean; onDismiss: () => void }) {
  return (
    <div style={{ position: 'fixed' as const, bottom: 24, right: 24, zIndex: 3000, display: 'flex', alignItems: 'center', gap: 10, background: ok ? '#1C3A2A' : '#3B1515', border: `1px solid ${ok ? '#68D39150' : '#FC818150'}`, borderRadius: 10, padding: '12px 18px', boxShadow: '0 8px 24px rgba(0,0,0,0.4)', fontSize: 13, fontWeight: 600, color: ok ? '#68D391' : '#FC8181' }}>
      {ok ? <CheckCircle size={15} /> : <AlertTriangle size={15} />}{msg}
      <button onClick={onDismiss} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', padding: 0, display: 'flex', marginLeft: 4 }}><X size={13} /></button>
    </div>
  )
}

// ── PUBLIC HOLIDAYS TAB ───────────────────────────────────────────────────────
function HolidaysTab() {
  const qc = useQueryClient()
  const [year,      setYear]      = useState(new Date().getFullYear())
  const [showAdd,   setShowAdd]   = useState(false)
  const [newDate,   setNewDate]   = useState('')
  const [newName,   setNewName]   = useState('')
  const [toast,     setToast]     = useState<{ msg: string; ok: boolean } | null>(null)
  const [error,     setError]     = useState('')

  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4000) }

  const { data: holidays = [], isLoading } = useQuery<any[]>({
    queryKey: ['admin-holidays', year],
    queryFn: async () => { const r = await adminApi.get(`/lookups/holidays?year=${year}`); return r.data?.data ?? r.data ?? [] },
  })

  const addHoliday = useMutation({
    mutationFn: () => adminApi.post('/lookups/holidays', { date: newDate, name: newName }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-holidays'] }); setShowAdd(false); setNewDate(''); setNewName(''); showToast('Holiday added') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to add holiday'),
  })

  const deleteHoliday = useMutation({
    mutationFn: (id: string) => adminApi.delete(`/lookups/holidays/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-holidays'] }); showToast('Holiday removed') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed', false),
  })

  const SA_HOLIDAYS_2027 = [
    { date: '2027-01-01', name: 'New Year\'s Day' },
    { date: '2027-03-21', name: 'Human Rights Day' },
    { date: '2027-04-26', name: 'Freedom Day (observed)' },
    { date: '2027-05-01', name: 'Workers\' Day' },
    { date: '2027-06-16', name: 'Youth Day' },
    { date: '2027-08-09', name: 'National Women\'s Day' },
    { date: '2027-09-24', name: 'Heritage Day' },
    { date: '2027-12-16', name: 'Day of Reconciliation' },
    { date: '2027-12-25', name: 'Christmas Day' },
    { date: '2027-12-26', name: 'Day of Goodwill' },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <select value={year} onChange={e => setYear(Number(e.target.value))} style={{ ...inp, width: 'auto', background: '#1A202C' }}>
            {[2025,2026,2027,2028].map(y => <option key={y}>{y}</option>)}
          </select>
          <span style={{ fontSize: 12, color: '#4A5568' }}>{holidays.length} holidays</span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {year === 2027 && (
            <button onClick={async () => {
              for (const h of SA_HOLIDAYS_2027) {
                try { await adminApi.post('/lookups/holidays', h) } catch {}
              }
              qc.invalidateQueries({ queryKey: ['admin-holidays'] })
              showToast('2027 holidays seeded')
            }} style={btnS}>Seed 2027</button>
          )}
          <button onClick={() => { setShowAdd(true); setError('') }} style={btnP}><Plus size={13} /> Add holiday</button>
        </div>
      </div>

      {showAdd && (
        <div style={{ background: '#1A202C', border: '1px solid #2D3748', borderRadius: 10, padding: 18, marginBottom: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr auto', gap: 10, alignItems: 'end' }}>
            <div><label style={lbl}>Date *</label><input type="date" value={newDate} onChange={e => setNewDate(e.target.value)} style={inp} autoFocus /></div>
            <div><label style={lbl}>Holiday name *</label><input value={newName} onChange={e => setNewName(e.target.value)} placeholder="e.g. Election Day" style={inp} /></div>
            <div style={{ display: 'flex', gap: 6 }}>
              <button onClick={() => addHoliday.mutate()} disabled={!newDate || !newName || addHoliday.isPending} style={{ ...btnP, opacity: (!newDate || !newName) ? 0.5 : 1 }}><Save size={13} /></button>
              <button onClick={() => { setShowAdd(false); setError('') }} style={btnS}><X size={13} /></button>
            </div>
          </div>
          {error && <div style={{ marginTop: 10, fontSize: 13, color: '#FC8181' }}>{error}</div>}
        </div>
      )}

      <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#4A5568' }}><RefreshCw size={20} style={{ animation: 'spin 1s linear infinite' }} /><style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style></div>
        ) : holidays.length === 0 ? (
          <div style={{ padding: '40px 20px', textAlign: 'center', color: '#4A5568' }}>
            <Calendar size={32} style={{ marginBottom: 10, opacity: 0.2 }} />
            <div style={{ color: '#718096', fontWeight: 600 }}>No holidays for {year}</div>
            <div style={{ fontSize: 12, marginTop: 4 }}>Add public holidays to ensure accurate business-day calculations in SARS deadlines.</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead><tr style={{ borderBottom: '1px solid #1E2532' }}>
              {['Date','Holiday name','Day',''].map(h => <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568', letterSpacing: '0.05em' }}>{h}</th>)}
            </tr></thead>
            <tbody>
              {(holidays as any[]).map((h: any) => {
                const d   = new Date(h.holiday_date + 'T00:00:00')
                const day = d.toLocaleDateString('en-ZA', { weekday: 'long' })
                const isPast = d < new Date()
                return (
                  <tr key={h.id} style={{ borderBottom: '1px solid #1E2532', opacity: isPast ? 0.5 : 1 }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: '#F7FAFC', fontWeight: 600 }}>{fmtDate(h.holiday_date)}</td>
                    <td style={{ padding: '12px 16px', color: '#F7FAFC', fontWeight: 600 }}>{h.name}</td>
                    <td style={{ padding: '12px 16px', color: '#718096' }}>{day}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <button onClick={() => { if (confirm(`Remove "${h.name}"?`)) deleteHoliday.mutate(h.id) }}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#FC818160', display: 'flex', padding: 4 }}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).style.color = '#FC8181'}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).style.color = '#FC818160'}>
                        <Trash2 size={13} />
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}

// ── TAX TABLES TAB ────────────────────────────────────────────────────────────
function TaxTab() {
  const qc = useQueryClient()
  const [taxYear, setTaxYear] = useState(new Date().getFullYear())
  const [editBracket, setEditBracket] = useState<any>(null)
  const [editRebate,  setEditRebate]  = useState<any>(null)
  const [toast, setToast] = useState<{ msg: string; ok: boolean } | null>(null)
  const [error, setError] = useState('')

  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4000) }

  const { data: brackets = [] } = useQuery<any[]>({
    queryKey: ['admin-tax-tables', taxYear],
    queryFn: async () => { const r = await adminApi.get(`/lookups/tax-tables?taxYear=${taxYear}`); return r.data?.data ?? r.data ?? [] },
  })

  const { data: rebates = [] } = useQuery<any[]>({
    queryKey: ['admin-tax-rebates', taxYear],
    queryFn: async () => { const r = await adminApi.get(`/lookups/tax-rebates?taxYear=${taxYear}`); return r.data?.data ?? r.data ?? [] },
  })

  const updateBracket = useMutation({
    mutationFn: () => adminApi.put(`/lookups/tax-tables/${editBracket.id}`, {
      rate: parseFloat(editBracket.rate), incomeFrom: parseFloat(editBracket.income_from),
      incomeTo: editBracket.income_to ? parseFloat(editBracket.income_to) : null,
      baseTax: editBracket.base_tax ? parseFloat(editBracket.base_tax) : null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-tax-tables'] }); setEditBracket(null); showToast('Tax bracket updated') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  const updateRebate = useMutation({
    mutationFn: () => adminApi.put(`/lookups/tax-rebates/${editRebate.id}`, { amount: parseFloat(editRebate.amount) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-tax-rebates'] }); setEditRebate(null); showToast('Rebate updated') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <select value={taxYear} onChange={e => setTaxYear(Number(e.target.value))} style={{ ...inp, width: 'auto', background: '#1A202C' }}>
          {[2024,2025,2026,2027].map(y => <option key={y}>{y}</option>)}
        </select>
        <div style={{ fontSize: 12, color: '#4A5568', padding: '8px 12px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8 }}>
          Update after February budget speech. Changes apply immediately to payroll calculations.
        </div>
      </div>

      {/* Tax brackets */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: '#718096', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Income tax brackets — {taxYear}</div>
        <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead><tr style={{ borderBottom: '1px solid #1E2532' }}>
              {['Income from','Income to','Base tax','Rate','Taxable above',''].map(h => (
                <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568' }}>{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {brackets.length === 0 ? (
                <tr><td colSpan={6} style={{ padding: '30px', textAlign: 'center', color: '#4A5568' }}>No tax table data for {taxYear}</td></tr>
              ) : (brackets as any[]).map((b: any) => (
                editBracket?.id === b.id ? (
                  <tr key={b.id} style={{ borderBottom: '1px solid #1E2532', background: '#1A202C' }}>
                    <td style={{ padding: '8px 12px' }}><input type="number" value={editBracket.income_from} onChange={e => setEditBracket((p: any) => ({ ...p, income_from: e.target.value }))} style={{ ...inp, width: 100 }} /></td>
                    <td style={{ padding: '8px 12px' }}><input type="number" value={editBracket.income_to ?? ''} onChange={e => setEditBracket((p: any) => ({ ...p, income_to: e.target.value }))} style={{ ...inp, width: 100 }} /></td>
                    <td style={{ padding: '8px 12px' }}><input type="number" value={editBracket.base_tax ?? ''} onChange={e => setEditBracket((p: any) => ({ ...p, base_tax: e.target.value }))} style={{ ...inp, width: 100 }} /></td>
                    <td style={{ padding: '8px 12px' }}><input type="number" step="0.01" value={editBracket.rate} onChange={e => setEditBracket((p: any) => ({ ...p, rate: e.target.value }))} style={{ ...inp, width: 70 }} /></td>
                    <td colSpan={1} />
                    <td style={{ padding: '8px 12px' }}>
                      <div style={{ display: 'flex', gap: 5 }}>
                        <button onClick={() => updateBracket.mutate()} style={{ ...btnP, padding: '6px 10px', fontSize: 11 }}><Save size={11} /></button>
                        <button onClick={() => { setEditBracket(null); setError('') }} style={{ ...btnS, padding: '6px 10px' }}><X size={11} /></button>
                      </div>
                    </td>
                  </tr>
                ) : (
                  <tr key={b.id} style={{ borderBottom: '1px solid #1E2532' }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <td style={{ padding: '12px 16px', color: '#F7FAFC' }}>{fmtR(b.income_from)}</td>
                    <td style={{ padding: '12px 16px', color: '#A0AEC0' }}>{b.income_to ? fmtR(b.income_to) : '∞'}</td>
                    <td style={{ padding: '12px 16px', color: '#A0AEC0' }}>{b.base_tax ? fmtR(b.base_tax) : '—'}</td>
                    <td style={{ padding: '12px 16px', color: '#0D9488', fontWeight: 700 }}>{b.rate}%</td>
                    <td style={{ padding: '12px 16px', color: '#718096', fontSize: 11 }}>{b.income_from ? fmtR(b.income_from) : '—'}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <button onClick={() => { setEditBracket({ ...b }); setError('') }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex', padding: 4 }}>
                        <Edit3 size={13} />
                      </button>
                    </td>
                  </tr>
                )
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Tax rebates */}
      {rebates.length > 0 && (
        <div>
          <div style={{ fontSize: 12, fontWeight: 700, color: '#718096', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Tax rebates — {taxYear}</div>
          <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
              <thead><tr style={{ borderBottom: '1px solid #1E2532' }}>
                {['Type','Amount','Age',''].map(h => <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568' }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {(rebates as any[]).map((r: any) => (
                  editRebate?.id === r.id ? (
                    <tr key={r.id} style={{ borderBottom: '1px solid #1E2532', background: '#1A202C' }}>
                      <td style={{ padding: '8px 16px', color: '#F7FAFC' }}>{r.rebate_type}</td>
                      <td style={{ padding: '8px 12px' }}><input type="number" value={editRebate.amount} onChange={e => setEditRebate((p: any) => ({ ...p, amount: e.target.value }))} style={{ ...inp, width: 120 }} /></td>
                      <td style={{ padding: '8px 16px', color: '#718096' }}>{r.age_from ? `${r.age_from}+` : 'All'}</td>
                      <td style={{ padding: '8px 12px' }}>
                        <div style={{ display: 'flex', gap: 5 }}>
                          <button onClick={() => updateRebate.mutate()} style={{ ...btnP, padding: '6px 10px', fontSize: 11 }}><Save size={11} /></button>
                          <button onClick={() => setEditRebate(null)} style={{ ...btnS, padding: '6px 10px' }}><X size={11} /></button>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    <tr key={r.id} style={{ borderBottom: '1px solid #1E2532' }}
                      onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                      onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                      <td style={{ padding: '12px 16px', color: '#F7FAFC', fontWeight: 600 }}>{r.rebate_type}</td>
                      <td style={{ padding: '12px 16px', color: '#0D9488', fontWeight: 700 }}>{fmtR(r.amount)}</td>
                      <td style={{ padding: '12px 16px', color: '#718096' }}>{r.age_from ? `${r.age_from}+` : 'All'}</td>
                      <td style={{ padding: '12px 16px' }}>
                        <button onClick={() => setEditRebate({ ...r })} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex', padding: 4 }}><Edit3 size={13} /></button>
                      </td>
                    </tr>
                  )
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {error && <div style={{ marginTop: 12, fontSize: 13, color: '#FC8181' }}>{error}</div>}
      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}

// ── DISCOUNTS TAB ─────────────────────────────────────────────────────────────
function DiscountsTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [toast, setToast]           = useState<{ msg: string; ok: boolean } | null>(null)
  const [error, setError]           = useState('')
  const INIT = () => ({ code: '', description: '', discountType: 'PERCENT', value: '', appliesTo: 'ALL', moduleKey: '', validFrom: '', validTo: '', maxUses: '' })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4000) }

  const { data: discounts = [], isLoading } = useQuery<any[]>({
    queryKey: ['admin-discounts'],
    queryFn: async () => { const r = await adminApi.get('/billing/discounts'); return r.data?.data ?? r.data ?? [] },
  })

  const { data: modules = [] } = useQuery<any[]>({
    queryKey: ['admin-module-catalogue'],
    queryFn: async () => { const r = await adminApi.get('/modules/catalogue'); return r.data?.data ?? r.data ?? [] },
  })

  const createDiscount = useMutation({
    mutationFn: () => adminApi.post('/billing/discounts', {
      code: form.code, description: form.description,
      discountType: form.discountType, value: parseFloat(form.value),
      appliesTo: form.appliesTo, moduleKey: form.moduleKey || null,
      validFrom: form.validFrom || null, validTo: form.validTo || null,
      maxUses: form.maxUses ? parseInt(form.maxUses) : null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-discounts'] }); setShowCreate(false); setForm(INIT()); showToast('Discount code created') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  const deactivate = useMutation({
    mutationFn: (id: string) => adminApi.delete(`/billing/discounts/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-discounts'] }); showToast('Discount deactivated') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed', false),
  })

  const isExpired = (d: any) => d && new Date(d) < new Date()
  const isNotStarted = (d: any) => d && new Date(d) > new Date()

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <button onClick={() => { setShowCreate(true); setError('') }} style={btnP}><Plus size={13} /> Create discount code</button>
      </div>

      {showCreate && (
        <div style={{ background: '#1A202C', border: '1px solid #2D3748', borderRadius: 12, padding: 20, marginBottom: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC' }}>New discount code</div>
            <button onClick={() => { setShowCreate(false); setError('') }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex' }}><X size={16} /></button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, marginBottom: 12 }}>
            <div><label style={lbl}>Code * (auto-uppercased)</label><input autoFocus value={form.code} onChange={e => f('code', e.target.value.toUpperCase())} placeholder="LAUNCH50" style={inp} /></div>
            <div><label style={lbl}>Type</label>
              <select value={form.discountType} onChange={e => f('discountType', e.target.value)} style={{ ...inp, background: '#13161E' }}>
                <option value="PERCENT">Percentage (%)</option>
                <option value="FIXED">Fixed amount (R)</option>
              </select>
            </div>
            <div><label style={lbl}>Value * ({form.discountType === 'PERCENT' ? '%' : 'R'})</label><input type="number" step="0.01" min="0" value={form.value} onChange={e => f('value', e.target.value)} placeholder={form.discountType === 'PERCENT' ? '25' : '100'} style={inp} /></div>
            <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Description</label><input value={form.description} onChange={e => f('description', e.target.value)} placeholder="25% off for launch partners" style={inp} /></div>
            <div><label style={lbl}>Applies to</label>
              <select value={form.appliesTo} onChange={e => f('appliesTo', e.target.value)} style={{ ...inp, background: '#13161E' }}>
                <option value="ALL">All modules</option>
                <option value="MODULE">Specific module</option>
              </select>
            </div>
            {form.appliesTo === 'MODULE' && (
              <div><label style={lbl}>Module</label>
                <select value={form.moduleKey} onChange={e => f('moduleKey', e.target.value)} style={{ ...inp, background: '#13161E' }}>
                  <option value="">Select module...</option>
                  {(modules as any[]).map((m: any) => <option key={m.key} value={m.key}>{m.name}</option>)}
                </select>
              </div>
            )}
            <div><label style={lbl}>Max uses (blank = unlimited)</label><input type="number" value={form.maxUses} onChange={e => f('maxUses', e.target.value)} placeholder="100" style={inp} /></div>
            <div><label style={lbl}>Valid from (optional)</label><input type="datetime-local" value={form.validFrom} onChange={e => f('validFrom', e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Valid to (optional)</label><input type="datetime-local" value={form.validTo} onChange={e => f('validTo', e.target.value)} style={inp} /></div>
          </div>
          {error && <div style={{ marginBottom: 10, fontSize: 13, color: '#FC8181' }}>{error}</div>}
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button onClick={() => { setShowCreate(false); setError('') }} style={btnS}>Cancel</button>
            <button onClick={() => createDiscount.mutate()} disabled={!form.code || !form.value || createDiscount.isPending} style={{ ...btnP, opacity: (!form.code || !form.value) ? 0.5 : 1 }}>
              {createDiscount.isPending ? 'Creating...' : 'Create code'}
            </button>
          </div>
        </div>
      )}

      <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#4A5568' }}><RefreshCw size={20} style={{ animation: 'spin 1s linear infinite' }} /></div>
        ) : discounts.length === 0 ? (
          <div style={{ padding: '50px 20px', textAlign: 'center', color: '#4A5568' }}>
            <Tag size={32} style={{ marginBottom: 10, opacity: 0.2 }} />
            <div style={{ color: '#718096', fontWeight: 600 }}>No discount codes yet</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead><tr style={{ borderBottom: '1px solid #1E2532' }}>
              {['Code','Type','Value','Applies to','Uses','Valid period','Status',''].map(h => (
                <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568' }}>{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {(discounts as any[]).map((d: any) => {
                const expired    = isExpired(d.valid_to)
                const notStarted = isNotStarted(d.valid_from)
                const maxed      = d.max_uses && d.uses_count >= d.max_uses
                const statusLabel = !d.active ? 'Deactivated' : expired ? 'Expired' : notStarted ? 'Scheduled' : maxed ? 'Used up' : 'Active'
                const statusColor = !d.active || expired || maxed ? '#FC8181' : notStarted ? '#F6AD55' : '#68D391'
                return (
                  <tr key={d.id} style={{ borderBottom: '1px solid #1E2532' }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', fontWeight: 700, color: '#0D9488', fontSize: 14 }}>{d.code}</td>
                    <td style={{ padding: '12px 16px', color: '#A0AEC0' }}>{d.discount_type}</td>
                    <td style={{ padding: '12px 16px', fontWeight: 700, color: '#F7FAFC' }}>{d.discount_type === 'PERCENT' ? `${d.value}%` : fmtR(d.value)}</td>
                    <td style={{ padding: '12px 16px', color: '#A0AEC0' }}>{d.applies_to === 'MODULE' ? d.module_key : 'All modules'}</td>
                    <td style={{ padding: '12px 16px', color: '#718096' }}>{d.uses_count}{d.max_uses ? ` / ${d.max_uses}` : ''}</td>
                    <td style={{ padding: '12px 16px', color: '#718096', fontSize: 11 }}>
                      {d.valid_from ? new Date(d.valid_from).toLocaleDateString('en-ZA') : '—'} → {d.valid_to ? new Date(d.valid_to).toLocaleDateString('en-ZA') : '∞'}
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: statusColor }}>{statusLabel}</span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      {d.active && (
                        <button onClick={() => { if (confirm(`Deactivate "${d.code}"?`)) deactivate.mutate(d.id) }}
                          style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#FC818160', display: 'flex', padding: 4 }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.color = '#FC8181'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.color = '#FC818160'}>
                          <X size={13} />
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}

// ── MODULES TAB ───────────────────────────────────────────────────────────────
function ModulesTab() {
  const qc = useQueryClient()
  const [editNotes, setEditNotes] = useState<{ key: string; notes: string } | null>(null)
  const [toast,     setToast]     = useState<{ msg: string; ok: boolean } | null>(null)

  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4000) }

  const { data: modules = [], isLoading } = useQuery<any[]>({
    queryKey: ['admin-module-catalogue'],
    queryFn: async () => { const r = await adminApi.get('/modules/catalogue'); return r.data?.data ?? r.data ?? [] },
  })

  const saveNotes = useMutation({
    mutationFn: () => adminApi.put(`/modules/${editNotes!.key}/notes`, { notes: editNotes!.notes }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-module-catalogue'] }); setEditNotes(null); showToast('Notes saved') },
  })

  const toggleActive = useMutation({
    mutationFn: ({ key, active }: { key: string; active: boolean }) =>
      adminApi.post(`/modules/${key}/${active ? 'activate' : 'deactivate'}`),
    onSuccess: (_, vars) => { qc.invalidateQueries({ queryKey: ['admin-module-catalogue'] }); showToast(`Module ${vars.active ? 'shown in' : 'hidden from'} catalogue`) },
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed', false),
  })

  const grouped = (modules as any[]).reduce((acc: any, m: any) => {
    const cat = m.category ?? 'OTHER'
    if (!acc[cat]) acc[cat] = []
    acc[cat].push(m)
    return acc
  }, {} as Record<string, any[]>)

  return (
    <div>
      <div style={{ marginBottom: 16, padding: '10px 14px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8, fontSize: 12, color: '#718096', lineHeight: 1.6 }}>
        Hiding a module removes it from the tenant self-service catalogue. Existing tenant subscriptions are unaffected.
        Admin notes are internal only — tenants never see them.
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 60, color: '#4A5568' }}><RefreshCw size={24} style={{ animation: 'spin 1s linear infinite' }} /></div>
      ) : (
        Object.entries(grouped).map(([cat, mods]) => (
          <div key={cat} style={{ marginBottom: 20 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 10 }}>{cat}</div>
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
              {(mods as any[]).map((m: any, i: number) => (
                <div key={m.key}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 18px', borderBottom: i < mods.length - 1 ? '1px solid #1E2532' : 'none', opacity: m.is_active === false ? 0.5 : 1 }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 3 }}>
                        <span style={{ fontWeight: 700, fontSize: 13, color: '#F7FAFC' }}>{m.name}</span>
                        <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#4A5568' }}>{m.key}</span>
                        {m.is_active === false && <span style={{ fontSize: 10, fontWeight: 700, color: '#FC8181', background: '#DC262620', padding: '1px 6px', borderRadius: 10 }}>Hidden</span>}
                      </div>
                      <div style={{ fontSize: 11, color: '#718096' }}>{m.description}</div>
                      {m.admin_notes && (
                        <div style={{ marginTop: 6, fontSize: 11, color: '#F6AD55', background: '#D9770615', border: '1px solid #D9770630', borderRadius: 6, padding: '4px 8px', display: 'inline-block' }}>
                          Note: {m.admin_notes}
                        </div>
                      )}
                    </div>
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexShrink: 0, marginLeft: 16 }}>
                      <span style={{ fontSize: 12, fontWeight: 700, color: '#0D9488' }}>R{m.monthly_price}/mo</span>
                      <button onClick={() => setEditNotes({ key: m.key, notes: m.admin_notes ?? '' })} title="Edit admin notes"
                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex', padding: 4 }}>
                        <Edit3 size={13} />
                      </button>
                      <button onClick={() => toggleActive.mutate({ key: m.key, active: m.is_active === false })}
                        title={m.is_active === false ? 'Show in catalogue' : 'Hide from catalogue'}
                        style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: m.is_active === false ? '#16653420' : '#DC262620', border: `1px solid ${m.is_active === false ? '#16653440' : '#DC262640'}`, borderRadius: 6, cursor: 'pointer', color: m.is_active === false ? '#68D391' : '#FC8181', fontSize: 11 }}>
                        {m.is_active === false ? <><Eye size={11} /> Show</> : <><EyeOff size={11} /> Hide</>}
                      </button>
                    </div>
                  </div>
                  {editNotes?.key === m.key && (
                    <div style={{ padding: '12px 18px', background: '#1A202C', borderTop: '1px solid #2D3748' }}>
                      <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
                        <div style={{ flex: 1 }}>
                          <label style={lbl}>Internal admin note</label>
                          <input value={editNotes.notes} onChange={e => setEditNotes(p => p ? { ...p, notes: e.target.value } : null)} placeholder="e.g. Being renegotiated — hold off activating" style={inp} autoFocus />
                        </div>
                        <button onClick={() => saveNotes.mutate()} style={{ ...btnP, padding: '9px 12px' }}><Save size={13} /></button>
                        <button onClick={() => setEditNotes(null)} style={{ ...btnS, padding: '9px 12px' }}><X size={13} /></button>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        ))
      )}
      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}

// ── MAIN PAGE ─────────────────────────────────────────────────────────────────
export function AdminLookupsPage() {
  const [tab, setTab] = useState<Tab>('holidays')

  const TABS: { id: Tab; label: string; icon: any; sub: string }[] = [
    { id: 'holidays',  label: 'Public Holidays', icon: Calendar,    sub: 'SA statutory holidays for deadline calculation' },
    { id: 'tax',       label: 'SARS Tax Tables', icon: Calculator,  sub: 'Income tax brackets & rebates — update post-budget' },
    { id: 'discounts', label: 'Discount Codes',  icon: Tag,         sub: 'Promo codes for module activations' },
    { id: 'modules',   label: 'Module Catalogue',icon: Package,     sub: 'Show/hide modules, admin notes' },
  ]

  return (
    <div style={{ color: '#F7FAFC' }}>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: '#F7FAFC', margin: '0 0 4px' }}>Lookup Data</h1>
        <p style={{ fontSize: 13, color: '#4A5568', margin: 0 }}>
          Manage platform reference data — holidays, SARS tables, discounts, module catalogue
        </p>
      </div>

      {/* Tab strip */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 24, flexWrap: 'wrap' }}>
        {TABS.map(t => {
          const Icon   = t.icon
          const active = tab === t.id
          return (
            <button key={t.id} onClick={() => setTab(t.id)}
              style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 16px', border: `1.5px solid ${active ? '#0D9488' : '#2D3748'}`, borderRadius: 10, background: active ? '#0D948815' : '#13161E', cursor: 'pointer', transition: 'all 0.15s', textAlign: 'left' as const }}>
              <Icon size={16} color={active ? '#0D9488' : '#718096'} />
              <div>
                <div style={{ fontSize: 13, fontWeight: 700, color: active ? '#0D9488' : '#A0AEC0' }}>{t.label}</div>
                <div style={{ fontSize: 11, color: '#4A5568', marginTop: 1 }}>{t.sub}</div>
              </div>
            </button>
          )
        })}
      </div>

      {/* Content */}
      <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 14, padding: 24 }}>
        {tab === 'holidays'  && <HolidaysTab />}
        {tab === 'tax'       && <TaxTab />}
        {tab === 'discounts' && <DiscountsTab />}
        {tab === 'modules'   && <ModulesTab />}
      </div>
    </div>
  )
}
