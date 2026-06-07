// src/pages/discounts/AdminDiscountsPage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import {
  Tag, TrendingDown, Users, Plus, Trash2, Edit3,
  CheckCircle, X, AlertTriangle, RefreshCw,
  Save, ChevronRight, DollarSign, Handshake,
  BarChart2,
} from 'lucide-react'

const fmtR = (n: any) => n != null ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}` : '—'
const fmtPct = (n: any) => n != null ? `${Number(n).toFixed(1)}%` : '—'
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

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

type Tab = 'overview' | 'volume' | 'partnerships' | 'history'

function Toast({ msg, ok, onDismiss }: { msg: string; ok: boolean; onDismiss: () => void }) {
  return (
    <div style={{ position: 'fixed' as const, bottom: 24, right: 24, zIndex: 3000, display: 'flex', alignItems: 'center', gap: 10, background: ok ? '#1C3A2A' : '#3B1515', border: `1px solid ${ok ? '#68D39150' : '#FC818150'}`, borderRadius: 10, padding: '12px 18px', boxShadow: '0 8px 24px rgba(0,0,0,0.4)', fontSize: 13, fontWeight: 600, color: ok ? '#68D391' : '#FC8181' }}>
      {ok ? <CheckCircle size={15} /> : <AlertTriangle size={15} />}{msg}
      <button onClick={onDismiss} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', padding: 0, display: 'flex', marginLeft: 4 }}><X size={13} /></button>
    </div>
  )
}

// ── OVERVIEW TAB ──────────────────────────────────────────────────────────────
function OverviewTab() {
  const { data: stats, isLoading } = useQuery({
    queryKey: ['discount-stats'],
    queryFn: async () => { const r = await adminApi.get('/discounts/stats'); return r.data?.data ?? r.data ?? {} },
  })

  if (isLoading) return (
    <div style={{ textAlign: 'center', padding: 60, color: '#4A5568' }}>
      <RefreshCw size={24} style={{ animation: 'spin 1s linear infinite' }} />
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  )

  return (
    <div>
      {/* KPI strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 24 }}>
        {[
          { label: 'Total redemptions', value: stats?.totalRedemptions ?? 0, color: '#60A5FA', bg: '#1D4ED820', border: '#1D4ED840' },
          { label: 'Total discount given', value: fmtR(stats?.totalDiscountGiven), color: '#FC8181', bg: '#DC262620', border: '#DC262640' },
          { label: 'Active codes', value: stats?.activeCodeCount ?? 0, color: '#0D9488', bg: '#0D948820', border: '#0D948840' },
          { label: 'Active partnerships', value: stats?.activePartnershipCount ?? 0, color: '#B794F4', bg: '#7C3AED20', border: '#7C3AED40' },
        ].map(k => (
          <div key={k.label} style={{ background: k.bg, border: `1px solid ${k.border}`, borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: 24, fontWeight: 800, color: k.color, marginBottom: 4 }}>{k.value}</div>
            <div style={{ fontSize: 11, color: '#4A5568', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{k.label}</div>
          </div>
        ))}
      </div>

      {/* Top codes */}
      {stats?.topCodes?.length > 0 && (
        <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ padding: '14px 20px', borderBottom: '1px solid #1E2532', fontSize: 13, fontWeight: 700, color: '#F7FAFC' }}>Top performing codes</div>
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead><tr style={{ borderBottom: '1px solid #1E2532' }}>
              {['Code','Redemptions','Total discount given'].map(h => (
                <th key={h} style={{ padding: '10px 20px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568' }}>{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {stats.topCodes.map((c: any) => (
                <tr key={c.code} style={{ borderBottom: '1px solid #1E2532' }}
                  onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                  onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                  <td style={{ padding: '12px 20px', fontFamily: 'monospace', fontWeight: 700, color: '#0D9488', fontSize: 14 }}>{c.code}</td>
                  <td style={{ padding: '12px 20px', color: '#F7FAFC', fontWeight: 600 }}>{c.redemptions}</td>
                  <td style={{ padding: '12px 20px', color: '#FC8181', fontWeight: 600 }}>{fmtR(c.total_discount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

// ── VOLUME TIERS TAB ──────────────────────────────────────────────────────────
function VolumeTiersTab() {
  const qc = useQueryClient()
  const [showAdd,    setShowAdd]    = useState(false)
  const [editTier,   setEditTier]   = useState<any>(null)
  const [newMin,     setNewMin]     = useState('')
  const [newPct,     setNewPct]     = useState('')
  const [newDesc,    setNewDesc]    = useState('')
  const [error,      setError]      = useState('')
  const [toast,      setToast]      = useState<{ msg: string; ok: boolean } | null>(null)

  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4000) }

  const { data: tiers = [], isLoading } = useQuery<any[]>({
    queryKey: ['volume-tiers'],
    queryFn: async () => { const r = await adminApi.get('/discounts/volume'); return r.data?.data ?? r.data ?? [] },
  })

  const createTier = useMutation({
    mutationFn: () => adminApi.post('/discounts/volume', { minModules: parseInt(newMin), discountPct: parseFloat(newPct), description: newDesc }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['volume-tiers'] }); setShowAdd(false); setNewMin(''); setNewPct(''); setNewDesc(''); showToast('Volume tier created') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  const updateTier = useMutation({
    mutationFn: () => adminApi.put(`/discounts/volume/${editTier.id}`, {
      discountPct: parseFloat(editTier.discount_pct), description: editTier.description, active: editTier.active,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['volume-tiers'] }); setEditTier(null); showToast('Volume tier updated') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  const deleteTier = useMutation({
    mutationFn: (id: string) => adminApi.delete(`/discounts/volume/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['volume-tiers'] }); showToast('Volume tier deleted') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed', false),
  })

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ fontSize: 12, color: '#4A5568', maxWidth: 500 }}>
          Volume discounts apply automatically when a tenant activates a module and already has the minimum number of active modules. The highest applicable tier wins.
        </div>
        <button onClick={() => { setShowAdd(true); setError('') }} style={btnP}><Plus size={13} /> Add tier</button>
      </div>

      {/* Add form */}
      {showAdd && (
        <div style={{ background: '#1A202C', border: '1px solid #2D3748', borderRadius: 10, padding: 18, marginBottom: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '120px 120px 1fr auto', gap: 10, alignItems: 'end' }}>
            <div><label style={lbl}>Min modules *</label><input autoFocus type="number" min="1" value={newMin} onChange={e => setNewMin(e.target.value)} placeholder="5" style={inp} /></div>
            <div><label style={lbl}>Discount % *</label><input type="number" step="0.5" min="0.5" max="100" value={newPct} onChange={e => setNewPct(e.target.value)} placeholder="10" style={inp} /></div>
            <div><label style={lbl}>Description</label><input value={newDesc} onChange={e => setNewDesc(e.target.value)} placeholder="10% off when 5+ modules active" style={inp} /></div>
            <div style={{ display: 'flex', gap: 6 }}>
              <button onClick={() => createTier.mutate()} disabled={!newMin || !newPct || createTier.isPending} style={{ ...btnP, opacity: (!newMin || !newPct) ? 0.5 : 1, padding: '9px 12px' }}><Save size={13} /></button>
              <button onClick={() => { setShowAdd(false); setError('') }} style={{ ...btnS, padding: '9px 12px' }}><X size={13} /></button>
            </div>
          </div>
          {error && <div style={{ marginTop: 8, fontSize: 13, color: '#FC8181' }}>{error}</div>}
        </div>
      )}

      {/* Tiers table */}
      <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#4A5568' }}><RefreshCw size={20} style={{ animation: 'spin 1s linear infinite' }} /></div>
        ) : tiers.length === 0 ? (
          <div style={{ padding: '50px 20px', textAlign: 'center', color: '#4A5568' }}>
            <TrendingDown size={32} style={{ marginBottom: 10, opacity: 0.2 }} />
            <div style={{ color: '#718096', fontWeight: 600 }}>No volume tiers yet</div>
            <div style={{ fontSize: 12, marginTop: 4 }}>Default tiers (3, 5, 8, 12 modules) were seeded by the V69 migration.</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead><tr style={{ borderBottom: '1px solid #1E2532' }}>
              {['Threshold','Discount','Description','Status',''].map(h => (
                <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568' }}>{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {(tiers as any[]).map((t: any) => (
                editTier?.id === t.id ? (
                  <tr key={t.id} style={{ borderBottom: '1px solid #1E2532', background: '#1A202C' }}>
                    <td style={{ padding: '8px 16px', color: '#F7FAFC', fontWeight: 700 }}>{t.min_modules}+ modules</td>
                    <td style={{ padding: '8px 12px' }}>
                      <input type="number" step="0.5" value={editTier.discount_pct} onChange={e => setEditTier((p: any) => ({ ...p, discount_pct: e.target.value }))} style={{ ...inp, width: 80 }} />
                    </td>
                    <td style={{ padding: '8px 12px' }}>
                      <input value={editTier.description ?? ''} onChange={e => setEditTier((p: any) => ({ ...p, description: e.target.value }))} style={inp} />
                    </td>
                    <td style={{ padding: '8px 12px' }}>
                      <select value={editTier.active ? 'true' : 'false'} onChange={e => setEditTier((p: any) => ({ ...p, active: e.target.value === 'true' }))}
                        style={{ ...inp, width: 'auto', background: '#13161E' }}>
                        <option value="true">Active</option>
                        <option value="false">Disabled</option>
                      </select>
                    </td>
                    <td style={{ padding: '8px 12px' }}>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <button onClick={() => updateTier.mutate()} style={{ ...btnP, padding: '6px 10px', fontSize: 11 }}><Save size={11} /></button>
                        <button onClick={() => { setEditTier(null); setError('') }} style={{ ...btnS, padding: '6px 10px' }}><X size={11} /></button>
                      </div>
                    </td>
                  </tr>
                ) : (
                  <tr key={t.id} style={{ borderBottom: '1px solid #1E2532', opacity: !t.active ? 0.45 : 1 }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <td style={{ padding: '12px 16px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{ width: 32, height: 32, borderRadius: 8, background: '#1D4ED820', border: '1px solid #1D4ED840', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 800, color: '#60A5FA' }}>{t.min_modules}+</div>
                        <span style={{ fontSize: 13, color: '#A0AEC0' }}>modules</span>
                      </div>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ fontSize: 20, fontWeight: 800, color: '#0D9488' }}>{t.discount_pct}%</span>
                      <span style={{ fontSize: 12, color: '#4A5568', marginLeft: 4 }}>off</span>
                    </td>
                    <td style={{ padding: '12px 16px', color: '#718096', fontSize: 12 }}>{t.description ?? '—'}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: t.active ? '#68D391' : '#718096' }}>
                        {t.active ? 'Active' : 'Disabled'}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <div style={{ display: 'flex', gap: 5 }}>
                        <button onClick={() => setEditTier({ ...t })} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex', padding: 4 }}><Edit3 size={13} /></button>
                        <button onClick={() => { if (confirm('Delete this volume tier?')) deleteTier.mutate(t.id) }}
                          style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#FC818160', display: 'flex', padding: 4 }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.color = '#FC8181'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.color = '#FC818160'}>
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Visual diagram */}
      {tiers.length > 0 && (
        <div style={{ marginTop: 20, background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: '16px 20px' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: '#4A5568', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 14 }}>Discount ladder</div>
          <div style={{ display: 'flex', gap: 0, alignItems: 'flex-end', height: 80 }}>
            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15].map(n => {
              const activeTiers = (tiers as any[]).filter(t => t.active && t.min_modules <= n)
              const pct = activeTiers.length > 0 ? Math.max(...activeTiers.map((t: any) => Number(t.discount_pct))) : 0
              const maxPct = Math.max(...(tiers as any[]).map((t: any) => Number(t.discount_pct)), 1)
              const height = pct > 0 ? Math.round((pct / maxPct) * 64) + 16 : 8
              const isThreshold = (tiers as any[]).some(t => t.active && Number(t.min_modules) === n)
              return (
                <div key={n} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                  <div style={{ width: '80%', height, background: pct > 0 ? `rgba(13,148,136,${0.3 + (pct / maxPct) * 0.7})` : '#1E2532', borderRadius: '3px 3px 0 0', border: isThreshold ? '1.5px solid #0D9488' : '1px solid transparent', transition: 'height 0.3s' }} />
                  <div style={{ fontSize: 9, color: pct > 0 ? '#0D9488' : '#2D3748', fontWeight: isThreshold ? 700 : 400 }}>{n}</div>
                </div>
              )
            })}
          </div>
          <div style={{ marginTop: 8, fontSize: 11, color: '#4A5568' }}>
            Modules → discount % applied. Highlighted bars = tier thresholds.
          </div>
        </div>
      )}

      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}

// ── PARTNERSHIPS TAB ──────────────────────────────────────────────────────────
function PartnershipsTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [toast,      setToast]      = useState<{ msg: string; ok: boolean } | null>(null)
  const [error,      setError]      = useState('')
  const INIT = () => ({ partnerName: '', contactEmail: '', discountPct: '', appliesTo: 'ALL', moduleKey: '', tenantSlugs: '', validFrom: '', validTo: '', notes: '' })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4000) }

  const { data: partnerships = [], isLoading } = useQuery<any[]>({
    queryKey: ['partnerships'],
    queryFn: async () => { const r = await adminApi.get('/discounts/partnerships'); return r.data?.data ?? r.data ?? [] },
  })

  const { data: modules = [] } = useQuery<any[]>({
    queryKey: ['admin-module-catalogue'],
    queryFn: async () => { const r = await adminApi.get('/modules/catalogue'); return r.data?.data ?? r.data ?? [] },
  })

  const create = useMutation({
    mutationFn: () => adminApi.post('/discounts/partnerships', {
      partnerName: form.partnerName,
      contactEmail: form.contactEmail || null,
      discountPct: parseFloat(form.discountPct),
      appliesTo: form.appliesTo,
      moduleKey: form.moduleKey || null,
      tenantSlugs: form.tenantSlugs ? form.tenantSlugs.split(',').map(s => s.trim()).filter(Boolean) : [],
      validFrom: form.validFrom || null,
      validTo: form.validTo || null,
      notes: form.notes || null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['partnerships'] }); setShowCreate(false); setForm(INIT()); showToast('Partnership created') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  const deactivate = useMutation({
    mutationFn: (id: string) => adminApi.delete(`/discounts/partnerships/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['partnerships'] }); showToast('Partnership deactivated') },
    onError: (e: any) => showToast(e.response?.data?.message || 'Failed', false),
  })

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ fontSize: 12, color: '#4A5568', maxWidth: 500 }}>
          Partnership agreements give named partners a fixed discount on module activations. Apply to specific tenants or all tenants of that partner.
        </div>
        <button onClick={() => { setShowCreate(true); setError('') }} style={btnP}><Plus size={13} /> New partnership</button>
      </div>

      {showCreate && (
        <div style={{ background: '#1A202C', border: '1px solid #2D3748', borderRadius: 12, padding: 20, marginBottom: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC' }}>New partnership agreement</div>
            <button onClick={() => { setShowCreate(false); setError('') }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex' }}><X size={16} /></button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, marginBottom: 12 }}>
            <div><label style={lbl}>Partner name *</label><input autoFocus value={form.partnerName} onChange={e => f('partnerName', e.target.value)} placeholder="Acme Business Solutions" style={inp} /></div>
            <div><label style={lbl}>Contact email</label><input type="email" value={form.contactEmail} onChange={e => f('contactEmail', e.target.value)} placeholder="partner@acme.co.za" style={inp} /></div>
            <div><label style={lbl}>Discount % *</label><input type="number" step="0.5" min="0.5" max="100" value={form.discountPct} onChange={e => f('discountPct', e.target.value)} placeholder="15" style={inp} /></div>
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
            <div><label style={lbl}>Valid from</label><input type="date" value={form.validFrom} onChange={e => f('validFrom', e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Valid to</label><input type="date" value={form.validTo} onChange={e => f('validTo', e.target.value)} style={inp} /></div>
            <div style={{ gridColumn: '1/-1' }}>
              <label style={lbl}>Tenant slugs (comma-separated — blank = all tenants with this partner)</label>
              <input value={form.tenantSlugs} onChange={e => f('tenantSlugs', e.target.value)} placeholder="acme-holdings, acme-logistics, acme-retail" style={inp} />
            </div>
            <div style={{ gridColumn: '1/-1' }}>
              <label style={lbl}>Notes</label>
              <input value={form.notes} onChange={e => f('notes', e.target.value)} placeholder="MSA signed 2026-06-01. Reviewed annually." style={inp} />
            </div>
          </div>
          {error && <div style={{ marginBottom: 10, fontSize: 13, color: '#FC8181' }}>{error}</div>}
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button onClick={() => { setShowCreate(false); setError('') }} style={btnS}>Cancel</button>
            <button onClick={() => create.mutate()} disabled={!form.partnerName || !form.discountPct || create.isPending}
              style={{ ...btnP, opacity: (!form.partnerName || !form.discountPct) ? 0.5 : 1 }}>
              {create.isPending ? 'Creating...' : <><Handshake size={13} /> Create agreement</>}
            </button>
          </div>
        </div>
      )}

      {/* Partnerships list */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 40, color: '#4A5568' }}><RefreshCw size={20} style={{ animation: 'spin 1s linear infinite' }} /></div>
        ) : partnerships.length === 0 ? (
          <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: '50px 20px', textAlign: 'center', color: '#4A5568' }}>
            <Handshake size={32} style={{ marginBottom: 10, opacity: 0.2 }} />
            <div style={{ color: '#718096', fontWeight: 600 }}>No partnership agreements yet</div>
          </div>
        ) : (partnerships as any[]).map((p: any) => {
          const isExpired = p.valid_to && new Date(p.valid_to) < new Date()
          const isNotStarted = p.valid_from && new Date(p.valid_from) > new Date()
          const effective = p.active && !isExpired && !isNotStarted
          return (
            <div key={p.id} style={{ background: '#13161E', border: `1px solid ${effective ? '#1E2532' : '#2D374840'}`, borderRadius: 12, padding: '16px 20px', opacity: !p.active ? 0.5 : 1 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                    <div style={{ width: 36, height: 36, borderRadius: 9, background: '#7C3AED20', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Handshake size={16} color="#B794F4" />
                    </div>
                    <div>
                      <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC' }}>{p.partner_name}</div>
                      {p.contact_email && <div style={{ fontSize: 11, color: '#4A5568' }}>{p.contact_email}</div>}
                    </div>
                    <div style={{ fontSize: 22, fontWeight: 800, color: '#B794F4', marginLeft: 8 }}>{p.discount_pct}% off</div>
                    <span style={{ fontSize: 11, fontWeight: 700, color: effective ? '#68D391' : isExpired ? '#FC8181' : '#F6AD55', background: effective ? '#16653420' : isExpired ? '#DC262620' : '#D9770620', padding: '2px 8px', borderRadius: 20 }}>
                      {effective ? 'Active' : isExpired ? 'Expired' : isNotStarted ? 'Scheduled' : 'Deactivated'}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: 16, fontSize: 12, color: '#718096', flexWrap: 'wrap', paddingLeft: 46 }}>
                    <span>Applies to: <strong style={{ color: '#F7FAFC' }}>{p.applies_to === 'MODULE' ? p.module_key : 'All modules'}</strong></span>
                    {p.valid_from && <span>From: {fmtDate(p.valid_from)}</span>}
                    {p.valid_to   && <span>To: {fmtDate(p.valid_to)}</span>}
                    {p.tenant_ids && p.tenant_ids.length > 0 && <span>{p.tenant_ids.length} tenant{p.tenant_ids.length !== 1 ? 's' : ''}</span>}
                  </div>
                  {p.notes && <div style={{ paddingLeft: 46, marginTop: 6, fontSize: 12, color: '#4A5568' }}>{p.notes}</div>}
                </div>
                {p.active && (
                  <button onClick={() => { if (confirm(`Deactivate partnership with ${p.partner_name}?`)) deactivate.mutate(p.id) }}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#FC818160', display: 'flex', padding: 4, flexShrink: 0 }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.color = '#FC8181'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.color = '#FC818160'}>
                    <X size={16} />
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}

// ── REDEMPTION HISTORY TAB ────────────────────────────────────────────────────
function RedemptionHistoryTab() {
  const { data: redemptions = [], isLoading } = useQuery<any[]>({
    queryKey: ['discount-redemptions'],
    queryFn: async () => { const r = await adminApi.get('/discounts/redemptions?limit=100'); return r.data?.data ?? r.data ?? [] },
  })

  const exportCsv = () => {
    if (!redemptions.length) return
    const headers = ['Code','Tenant','Module','Discount %','Original','Final','Saved','Date']
    const rows = (redemptions as any[]).map(r => [
      r.code, r.tenant_name, r.module_key, r.discount_pct,
      r.original_price, r.final_price,
      (Number(r.original_price) - Number(r.final_price)).toFixed(2),
      new Date(r.created_at).toLocaleDateString('en-ZA'),
    ])
    const csv = [headers, ...rows].map(r => r.join(',')).join('\n')
    const a = document.createElement('a'); a.href = 'data:text/csv;charset=utf-8,' + encodeURIComponent(csv); a.download = 'discount-redemptions.csv'; a.click()
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ fontSize: 12, color: '#4A5568' }}>Every time a discount code was applied at module activation</div>
        <button onClick={exportCsv} style={btnS}>Export CSV</button>
      </div>
      <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#4A5568' }}><RefreshCw size={20} style={{ animation: 'spin 1s linear infinite' }} /></div>
        ) : redemptions.length === 0 ? (
          <div style={{ padding: '50px 20px', textAlign: 'center', color: '#4A5568' }}>
            <Tag size={32} style={{ marginBottom: 10, opacity: 0.2 }} />
            <div style={{ color: '#718096', fontWeight: 600 }}>No redemptions yet</div>
            <div style={{ fontSize: 12, marginTop: 4 }}>Discount codes applied at module activation will appear here.</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead><tr style={{ borderBottom: '1px solid #1E2532' }}>
              {['Code','Tenant','Module','Discount','Original','Final','Saved','Date'].map(h => (
                <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568' }}>{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {(redemptions as any[]).map((r: any, i: number) => {
                const saved = Number(r.original_price) - Number(r.final_price)
                return (
                  <tr key={i} style={{ borderBottom: '1px solid #1E2532' }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <td style={{ padding: '11px 16px', fontFamily: 'monospace', fontWeight: 700, color: '#0D9488', fontSize: 13 }}>{r.code}</td>
                    <td style={{ padding: '11px 16px' }}>
                      <div style={{ fontWeight: 600, color: '#F7FAFC', fontSize: 12 }}>{r.tenant_name}</div>
                      <div style={{ fontSize: 10, color: '#4A5568' }}>{r.slug}</div>
                    </td>
                    <td style={{ padding: '11px 16px', color: '#A0AEC0', fontFamily: 'monospace', fontSize: 11 }}>{r.module_key}</td>
                    <td style={{ padding: '11px 16px', color: '#F6AD55', fontWeight: 700 }}>{fmtPct(r.discount_pct)}</td>
                    <td style={{ padding: '11px 16px', color: '#718096' }}>{fmtR(r.original_price)}</td>
                    <td style={{ padding: '11px 16px', fontWeight: 700, color: '#F7FAFC' }}>{fmtR(r.final_price)}</td>
                    <td style={{ padding: '11px 16px', color: '#FC8181', fontWeight: 600 }}>-{fmtR(saved)}</td>
                    <td style={{ padding: '11px 16px', color: '#4A5568', fontSize: 11 }}>{fmtDate(r.created_at)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

// ── MAIN PAGE ─────────────────────────────────────────────────────────────────
export function AdminDiscountsPage() {
  const [tab, setTab] = useState<Tab>('overview')

  const TABS: { id: Tab; label: string; icon: any; sub: string }[] = [
    { id: 'overview',      label: 'Overview',        icon: BarChart2,     sub: 'Stats & top codes' },
    { id: 'volume',        label: 'Volume discounts', icon: TrendingDown,  sub: 'Tier-based automatic discounts' },
    { id: 'partnerships',  label: 'Partnerships',     icon: Handshake,     sub: 'Named partner agreements' },
    { id: 'history',       label: 'Redemptions',      icon: Tag,           sub: 'Audit trail of code uses' },
  ]

  return (
    <div style={{ color: '#F7FAFC' }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: '#F7FAFC', margin: '0 0 4px' }}>Discounts & Promotions</h1>
        <p style={{ fontSize: 13, color: '#4A5568', margin: 0 }}>
          Volume tiers · Partnership pricing · Promo codes · Redemption history
        </p>
      </div>

      {/* Tab strip */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 24, flexWrap: 'wrap' }}>
        {TABS.map(t => {
          const Icon   = t.icon
          const active = tab === t.id
          return (
            <button key={t.id} onClick={() => setTab(t.id)}
              style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 16px', border: `1.5px solid ${active ? '#0D9488' : '#2D3748'}`, borderRadius: 10, background: active ? '#0D948815' : '#13161E', cursor: 'pointer', textAlign: 'left' as const, transition: 'all 0.15s' }}>
              <Icon size={16} color={active ? '#0D9488' : '#718096'} />
              <div>
                <div style={{ fontSize: 13, fontWeight: 700, color: active ? '#0D9488' : '#A0AEC0' }}>{t.label}</div>
                <div style={{ fontSize: 11, color: '#4A5568', marginTop: 1 }}>{t.sub}</div>
              </div>
            </button>
          )
        })}
      </div>

      <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 14, padding: 24 }}>
        {tab === 'overview'     && <OverviewTab />}
        {tab === 'volume'       && <VolumeTiersTab />}
        {tab === 'partnerships' && <PartnershipsTab />}
        {tab === 'history'      && <RedemptionHistoryTab />}
      </div>
    </div>
  )
}
