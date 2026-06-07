// src/pages/reports/AdminReportsPage.tsx
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import { BarChart2, TrendingUp, Users, Package, RefreshCw, Download } from 'lucide-react'

const fmtR = (n: any) => n != null ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 0 })}` : '—'
const pct  = (n: any) => n != null ? `${Number(n).toFixed(1)}%` : '—'

function SectionHeader({ title, sub }: { title: string; sub?: string }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC' }}>{title}</div>
      {sub && <div style={{ fontSize: 12, color: '#4A5568', marginTop: 2 }}>{sub}</div>}
    </div>
  )
}

// Simple bar drawn with divs — no chart library needed
function MiniBar({ value, max, color }: { value: number; max: number; color: string }) {
  const pctVal = max > 0 ? Math.round((value / max) * 100) : 0
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div style={{ flex: 1, height: 6, background: '#1E2532', borderRadius: 99, overflow: 'hidden' }}>
        <div style={{ width: `${pctVal}%`, height: '100%', background: color, borderRadius: 99, transition: 'width 0.3s' }} />
      </div>
      <span style={{ fontSize: 11, color, fontWeight: 700, minWidth: 28, textAlign: 'right' as const }}>{pctVal}%</span>
    </div>
  )
}

export function AdminReportsPage() {
  const { data: adoption = [], isLoading: loadingAdoption } = useQuery<any[]>({
    queryKey: ['admin-module-adoption'],
    queryFn: async () => { const r = await adminApi.get('/reports/module-adoption'); return r.data?.data ?? r.data ?? [] },
  })

  const { data: mrr = [], isLoading: loadingMrr } = useQuery<any[]>({
    queryKey: ['admin-mrr-breakdown'],
    queryFn: async () => { const r = await adminApi.get('/billing/mrr'); return r.data?.data ?? r.data ?? [] },
  })

  const { data: tenants = [] } = useQuery<any[]>({
    queryKey: ['admin-tenants-report'],
    queryFn: async () => { const r = await adminApi.get('/tenants?size=200&sortBy=mrr'); return r.data?.data ?? r.data ?? [] },
  })

  const maxActive = Math.max(...(adoption as any[]).map((m: any) => Number(m.active) || 0), 1)
  const maxMrr    = Math.max(...(mrr as any[]).map((m: any) => Number(m.module_mrr) || 0), 1)

  const totalMrr = (mrr as any[]).reduce((s: number, m: any) => s + (Number(m.module_mrr) || 0), 0)

  const exportCsv = (data: any[], filename: string) => {
    if (!data.length) return
    const headers = Object.keys(data[0])
    const rows    = data.map(r => headers.map(h => r[h] ?? '').join(','))
    const csv     = [headers.join(','), ...rows].join('\n')
    const a = document.createElement('a')
    a.href = 'data:text/csv;charset=utf-8,' + encodeURIComponent(csv)
    a.download = filename; a.click()
  }

  return (
    <div style={{ color: '#F7FAFC' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 28 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: '#1A202C', border: '1px solid #2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <BarChart2 size={16} color="#0D9488" />
            </div>
            <h1 style={{ fontSize: 22, fontWeight: 800, margin: 0 }}>Reports</h1>
          </div>
          <p style={{ fontSize: 13, color: '#4A5568', margin: 0, paddingLeft: 46 }}>
            Module adoption · MRR breakdown · Tenant analysis
          </p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>

        {/* Module adoption */}
        <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: '20px 22px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
            <SectionHeader title="Module adoption" sub="Active tenants per module" />
            <button onClick={() => exportCsv(adoption, 'module-adoption.csv')}
              style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 6, cursor: 'pointer', color: '#718096', fontSize: 11 }}>
              <Download size={11} /> CSV
            </button>
          </div>
          {loadingAdoption ? (
            <div style={{ textAlign: 'center', padding: 40, color: '#4A5568' }}>
              <RefreshCw size={20} style={{ animation: 'spin 1s linear infinite' }} />
              <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {(adoption as any[]).map((m: any) => {
                const active    = Number(m.active)    || 0
                const trial     = Number(m.trial)     || 0
                const cancelled = Number(m.cancelled) || 0
                const convRate  = m.conversion_rate_pct
                return (
                  <div key={m.key}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                      <div>
                        <span style={{ fontSize: 13, fontWeight: 600, color: '#F7FAFC' }}>{m.name}</span>
                        <span style={{ fontSize: 11, color: '#4A5568', marginLeft: 6 }}>{m.category}</span>
                      </div>
                      <div style={{ display: 'flex', gap: 10, fontSize: 11 }}>
                        <span style={{ color: '#68D391' }}>{active} active</span>
                        <span style={{ color: '#F6AD55' }}>{trial} trial</span>
                        {cancelled > 0 && <span style={{ color: '#FC8181' }}>{cancelled} cancelled</span>}
                        {convRate != null && <span style={{ color: '#A0AEC0' }}>{pct(convRate)} conv.</span>}
                      </div>
                    </div>
                    <MiniBar value={active} max={maxActive} color="#0D9488" />
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* MRR by module */}
        <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: '20px 22px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
            <SectionHeader title="MRR by module" sub={`Total: ${fmtR(totalMrr)}/month`} />
            <button onClick={() => exportCsv(mrr, 'mrr-breakdown.csv')}
              style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 6, cursor: 'pointer', color: '#718096', fontSize: 11 }}>
              <Download size={11} /> CSV
            </button>
          </div>
          {loadingMrr ? (
            <div style={{ textAlign: 'center', padding: 40, color: '#4A5568' }}>
              <RefreshCw size={20} style={{ animation: 'spin 1s linear infinite' }} />
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {(mrr as any[]).filter(m => (Number(m.module_mrr) || 0) > 0).map((m: any) => {
                const modMrr = Number(m.module_mrr) || 0
                const share  = totalMrr > 0 ? Math.round((modMrr / totalMrr) * 100) : 0
                return (
                  <div key={m.key}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: '#F7FAFC' }}>{m.name}</span>
                      <div style={{ display: 'flex', gap: 8, fontSize: 11 }}>
                        <span style={{ color: '#0D9488', fontWeight: 700 }}>{fmtR(modMrr)}</span>
                        <span style={{ color: '#4A5568' }}>{share}% of MRR</span>
                      </div>
                    </div>
                    <MiniBar value={modMrr} max={maxMrr} color="#0D9488" />
                  </div>
                )
              })}
              {(mrr as any[]).every(m => (Number(m.module_mrr) || 0) === 0) && (
                <div style={{ textAlign: 'center', padding: 40, color: '#4A5568', fontSize: 13 }}>No active paid subscriptions yet</div>
              )}
            </div>
          )}
        </div>

        {/* Tenant MRR table — full width */}
        <div style={{ gridColumn: '1/-1', background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ padding: '16px 22px', borderBottom: '1px solid #1E2532', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7FAFC' }}>Tenant MRR ranking</div>
              <div style={{ fontSize: 12, color: '#4A5568', marginTop: 2 }}>All tenants sorted by monthly revenue</div>
            </div>
            <button onClick={() => exportCsv(tenants, 'tenant-mrr.csv')}
              style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 6, cursor: 'pointer', color: '#718096', fontSize: 11 }}>
              <Download size={11} /> CSV
            </button>
          </div>
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #1E2532' }}>
                {['#','Tenant','Slug','Status','Modules','Users','MRR'].map(h => (
                  <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(tenants as any[]).slice(0, 25).map((t: any, i: number) => {
                const statusColor = t.subscription_status === 'ACTIVE' ? '#68D391' : t.subscription_status === 'PILOT' ? '#F6AD55' : t.subscription_status === 'SUSPENDED' ? '#FC8181' : '#718096'
                return (
                  <tr key={t.id ?? i} style={{ borderBottom: '1px solid #1E2532' }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <td style={{ padding: '11px 16px', color: '#4A5568', fontSize: 12, fontWeight: 700 }}>{i + 1}</td>
                    <td style={{ padding: '11px 16px', fontWeight: 600, color: '#F7FAFC' }}>{t.name}</td>
                    <td style={{ padding: '11px 16px', color: '#4A5568', fontFamily: 'monospace', fontSize: 12 }}>{t.slug}</td>
                    <td style={{ padding: '11px 16px' }}>
                      <span style={{ color: statusColor, fontSize: 11, fontWeight: 700 }}>{t.subscription_status}</span>
                    </td>
                    <td style={{ padding: '11px 16px', color: '#A0AEC0' }}>{t.module_count}</td>
                    <td style={{ padding: '11px 16px', color: '#A0AEC0' }}>{t.user_count}</td>
                    <td style={{ padding: '11px 16px', fontWeight: 700, color: Number(t.mrr) > 0 ? '#0D9488' : '#4A5568' }}>{fmtR(t.mrr)}</td>
                  </tr>
                )
              })}
              {tenants.length === 0 && (
                <tr><td colSpan={7} style={{ padding: '40px', textAlign: 'center', color: '#4A5568' }}>No tenants yet</td></tr>
              )}
            </tbody>
          </table>
        </div>

      </div>
    </div>
  )
}
