// src/pages/modules/AdminModulesPage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import {
  Package, Edit3, TrendingUp, X, CheckCircle,
  AlertTriangle, RefreshCw, DollarSign,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

const fmtR = (n: any) => n != null ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}` : '—'

const CATEGORY_COLOR: Record<string, { color: string; bg: string }> = {
  CORE:       { color: '#0D9488', bg: '#0D948820' },
  FINANCE:    { color: '#60A5FA', bg: '#1D4ED820' },
  OPERATIONS: { color: '#F6AD55', bg: '#D9770620' },
  INDUSTRY:   { color: '#B794F4', bg: '#7C3AED20' },
  ENTERPRISE: { color: '#FC8181', bg: '#DC262620' },
}

const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px', border: '1.5px solid #2D3748',
  borderRadius: 8, fontSize: 13, background: '#1A202C', color: '#F7FAFC',
  outline: 'none', boxSizing: 'border-box' as const, fontFamily: 'inherit',
}
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
  padding: '9px 14px', border: '1.5px solid #2D3748',
  borderRadius: 8, background: '#1A202C', fontSize: 13,
  cursor: 'pointer', color: '#A0AEC0', fontWeight: 500,
}

const navigate = useNavigate()

function PriceModal({ module: mod, onClose, onSaved }: { module: any; onClose: () => void; onSaved: () => void }) {
  const [price, setPrice] = useState(String(mod.monthly_price ?? mod.monthlyPrice ?? ''))
  const [error, setError] = useState('')

  const update = useMutation({
    mutationFn: () => adminApi.put('/billing/modules/pricing', {
      moduleKey: mod.key, newPrice: parseFloat(price),
    }),
    onSuccess: () => { onSaved(); onClose() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to update price'),
  })

  const currentPrice = Number(mod.monthly_price ?? mod.monthlyPrice ?? 0)
  const newPrice     = parseFloat(price) || 0
  const diff         = newPrice - currentPrice

  return (
    <div style={{ position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(4px)' }}>
      <div style={{ background: '#13161E', border: '1px solid #2D3748', borderRadius: 16, padding: 28, width: 420, boxShadow: '0 25px 80px rgba(0,0,0,0.5)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#F7FAFC' }}>Update price</h3>
            <div style={{ fontSize: 12, color: '#4A5568', marginTop: 3 }}>{mod.name}</div>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ marginBottom: 16 }}>
          <label style={lbl}>New monthly price (R) *</label>
          <input autoFocus type="number" step="0.01" min="0" value={price}
            onChange={e => setPrice(e.target.value)} style={inp} />
          {price && !isNaN(newPrice) && (
            <div style={{ marginTop: 8, display: 'flex', gap: 12, fontSize: 12 }}>
              <span style={{ color: '#718096' }}>Current: {fmtR(currentPrice)}</span>
              <span style={{ color: diff > 0 ? '#68D391' : diff < 0 ? '#FC8181' : '#718096', fontWeight: 700 }}>
                New: {fmtR(newPrice)} {diff !== 0 && `(${diff > 0 ? '+' : ''}${fmtR(diff)})`}
              </span>
            </div>
          )}
          <div style={{ marginTop: 10, padding: '10px 12px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8, fontSize: 12, color: '#718096', lineHeight: 1.6 }}>
            Price change applies to <strong style={{ color: '#F7FAFC' }}>new activations only</strong>. Existing subscribers retain their current price until you explicitly migrate them.
          </div>
        </div>
        {error && <div style={{ marginBottom: 12, padding: '10px 12px', background: '#3B1515', border: '1px solid #FC818150', borderRadius: 8, fontSize: 13, color: '#FC8181' }}>{error}</div>}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <button onClick={onClose} style={btnS}>Cancel</button>
          <button onClick={() => update.mutate()}
            disabled={!price || isNaN(newPrice) || newPrice < 0 || update.isPending}
            style={{ ...btnP, opacity: !price ? 0.5 : 1 }}>
            {update.isPending ? 'Saving...' : 'Update price'}
          </button>
        </div>
      </div>
    </div>
  )
}

export function AdminModulesPage() {
  const qc = useQueryClient()
  const [editingModule, setEditingModule] = useState<any>(null)
  const [toast, setToast]                 = useState<{ msg: string; ok: boolean } | null>(null)

  const showToast = (msg: string, ok = true) => {
    setToast({ msg, ok }); setTimeout(() => setToast(null), 4000)
  }

  const { data: mrr = [], isLoading, refetch } = useQuery<any[]>({
    queryKey: ['admin-module-catalogue'],
    queryFn: async () => {
      const r = await adminApi.get('/billing/mrr')
      return r.data?.data ?? r.data ?? []
    },
  })

  const { data: adoption = [] } = useQuery<any[]>({
    queryKey: ['admin-module-adoption'],
    queryFn: async () => {
      const r = await adminApi.get('/reports/module-adoption')
      return r.data?.data ?? r.data ?? []
    },
  })

  // Merge mrr + adoption data by key
  const modules = mrr.map((m: any) => {
    const a = (adoption as any[]).find(x => x.key === m.key) ?? {}
    return { ...m, ...a }
  })

  // Group by category
  const grouped = modules.reduce((acc: any, m: any) => {
    const cat = m.category ?? 'OTHER'
    if (!acc[cat]) acc[cat] = []
    acc[cat].push(m)
    return acc
  }, {} as Record<string, any[]>)

  const totalMrr = modules.reduce((s: number, m: any) => s + (Number(m.module_mrr) || 0), 0)
  const totalActive = modules.reduce((s: number, m: any) => s + (Number(m.active_count || m.active) || 0), 0)
  const totalTrial  = modules.reduce((s: number, m: any) => s + (Number(m.trial_count || m.trial) || 0), 0)

  return (
    <div style={{ color: '#F7FAFC' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: '#1A202C', border: '1px solid #2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Package size={16} color="#0D9488" />
            </div>
            <h1 style={{ fontSize: 22, fontWeight: 800, margin: 0 }}>Module Catalogue</h1>
          </div>
          <p style={{ fontSize: 13, color: '#4A5568', margin: 0, paddingLeft: 46 }}>
            Pricing · Adoption · Active vs trial counts per module
          </p>
        </div>
        <button onClick={() => refetch()} style={btnS}><RefreshCw size={13} /></button>
        <button onClick={() => navigate('/modules/new')} style={btnP}>
          <Plus size={14} /> New module
        </button>
      </div>

      {/* Summary strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 24 }}>
        {[
          { label: 'Total module MRR', value: fmtR(totalMrr), color: '#0D9488', bg: '#0D948820', border: '#0D948840', icon: <DollarSign size={16} /> },
          { label: 'Active subscriptions', value: totalActive, color: '#68D391', bg: '#16653420', border: '#16653440', icon: <CheckCircle size={16} /> },
          { label: 'Trial subscriptions', value: totalTrial, color: '#F6AD55', bg: '#D9770620', border: '#D9770640', icon: <TrendingUp size={16} /> },
        ].map(k => (
          <div key={k.label} style={{ background: k.bg, border: `1px solid ${k.border}`, borderRadius: 12, padding: '16px 20px', display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ color: k.color }}>{k.icon}</div>
            <div>
              <div style={{ fontSize: 24, fontWeight: 800, color: k.color }}>{k.value}</div>
              <div style={{ fontSize: 11, color: '#4A5568', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginTop: 2 }}>{k.label}</div>
            </div>
          </div>
        ))}
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 80, color: '#4A5568' }}>
          <RefreshCw size={28} style={{ marginBottom: 10, animation: 'spin 1s linear infinite' }} />
          <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
          Loading modules...
        </div>
      ) : (
        Object.entries(grouped).map(([cat, mods]) => {
          const catCfg = CATEGORY_COLOR[cat] ?? { color: '#718096', bg: '#2D374820' }
          return (
            <div key={cat} style={{ marginBottom: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                <span style={{ background: catCfg.bg, color: catCfg.color, padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cat}</span>
                <div style={{ height: 1, flex: 1, background: '#1E2532' }} />
              </div>
              <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid #1E2532' }}>
                      {['Module','Price/mo','Active','Trial','Cancelled','Conv. rate','MRR',''].map(h => (
                        <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568', letterSpacing: '0.05em' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {(mods as any[]).map((m: any) => {
                      const active      = Number(m.active_count || m.active) || 0
                      const trial       = Number(m.trial_count  || m.trial)  || 0
                      const cancelled   = Number(m.cancelled)   || 0
                      const convRate    = m.conversion_rate_pct != null ? `${m.conversion_rate_pct}%` : '—'
                      const modMrr      = Number(m.module_mrr)  || 0
                      const monthlyPrice = Number(m.monthly_price || m.monthlyPrice) || 0
                      return (
                        <tr key={m.key} style={{ borderBottom: '1px solid #1E2532' }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                          <td style={{ padding: '12px 16px' }}>
                            <div style={{ fontWeight: 700, color: '#F7FAFC' }}>{m.name}</div>
                            <div style={{ fontSize: 11, color: '#4A5568', fontFamily: 'monospace' }}>{m.key}</div>
                          </td>
                          <td style={{ padding: '12px 16px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                              <span style={{ fontWeight: 700, color: '#F7FAFC' }}>{fmtR(monthlyPrice)}</span>
                              <button onClick={() => setEditingModule(m)} title="Edit price"
                                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#4A5568', display: 'flex', padding: 2 }}>
                                <Edit3 size={11} />
                              </button>
                            </div>
                          </td>
                          <td style={{ padding: '12px 16px', color: active > 0 ? '#68D391' : '#4A5568', fontWeight: active > 0 ? 700 : 400 }}>{active}</td>
                          <td style={{ padding: '12px 16px', color: trial > 0 ? '#F6AD55' : '#4A5568' }}>{trial}</td>
                          <td style={{ padding: '12px 16px', color: '#4A5568' }}>{cancelled}</td>
                          <td style={{ padding: '12px 16px', color: '#A0AEC0', fontWeight: 600 }}>{convRate}</td>
                          <td style={{ padding: '12px 16px', fontWeight: 700, color: modMrr > 0 ? '#0D9488' : '#4A5568' }}>{fmtR(modMrr)}</td>
                          <td style={{ padding: '12px 16px' }}>
                            <button onClick={() => setEditingModule(m)}
                              style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 6, cursor: 'pointer', color: '#718096', fontSize: 11 }}>
                              <Edit3 size={11} /> Price
                            </button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )
        })
      )}

      {editingModule && (
        <PriceModal module={editingModule} onClose={() => setEditingModule(null)}
          onSaved={() => { qc.invalidateQueries({ queryKey: ['admin-module-catalogue'] }); showToast(`Price updated for ${editingModule.name}`) }} />
      )}

      {toast && (
        <div style={{ position: 'fixed' as const, bottom: 24, right: 24, zIndex: 3000, display: 'flex', alignItems: 'center', gap: 9, background: toast.ok ? '#1C3A2A' : '#3B1515', border: `1px solid ${toast.ok ? '#68D39150' : '#FC818150'}`, borderRadius: 10, padding: '12px 18px', boxShadow: '0 8px 24px rgba(0,0,0,0.4)', fontSize: 13, fontWeight: 600, color: toast.ok ? '#68D391' : '#FC8181' }}>
          {toast.ok ? <CheckCircle size={15} /> : <AlertTriangle size={15} />}{toast.msg}
          <button onClick={() => setToast(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', padding: 0, display: 'flex', marginLeft: 4 }}><X size={13} /></button>
        </div>
      )}
    </div>
  )
}
