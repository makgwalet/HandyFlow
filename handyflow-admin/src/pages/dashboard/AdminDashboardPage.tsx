// src/pages/dashboard/AdminDashboardPage.tsx
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import { authStore } from '../../store/auth'
import { useNavigate } from 'react-router-dom'
import {
  Building2, TrendingUp, AlertTriangle, Clock,
  Users, Package, CheckCircle, ArrowRight,
  RefreshCw, DollarSign,
} from 'lucide-react'

const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 0 })}` : 'R 0'
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

function StatCard({ label, value, sub, color, bg, icon, onClick }: any) {
  return (
    <div onClick={onClick}
      style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: '18px 20px', cursor: onClick ? 'pointer' : 'default', transition: 'border-color 0.15s' }}
      onMouseEnter={e => onClick && ((e.currentTarget as HTMLElement).style.borderColor = '#2D3748')}
      onMouseLeave={e => onClick && ((e.currentTarget as HTMLElement).style.borderColor = '#1E2532')}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
        <div style={{ width: 36, height: 36, borderRadius: 9, background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color, flexShrink: 0 }}>{icon}</div>
      </div>
      <div style={{ fontSize: 28, fontWeight: 800, color, letterSpacing: '-0.5px', marginBottom: 4 }}>{value}</div>
      <div style={{ fontSize: 11, color: '#4A5568', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
      {sub && <div style={{ fontSize: 11, color: '#718096', marginTop: 4 }}>{sub}</div>}
    </div>
  )
}

export function AdminDashboardPage() {
  const navigate = useNavigate()
  const user     = authStore.get()

  const { data: dash, isLoading, refetch, dataUpdatedAt } = useQuery({
    queryKey: ['admin-dashboard'],
    queryFn: async () => { const r = await adminApi.get('/dashboard'); return r.data?.data ?? r.data },
    refetchInterval: 60_000,
  })

  const { data: expiring = [] } = useQuery<any[]>({
    queryKey: ['admin-expiring-7d'],
    queryFn: async () => { const r = await adminApi.get('/pilots/expiring?days=7'); return r.data?.data ?? r.data ?? [] },
  })

  const { data: overdue = [] } = useQuery<any[]>({
    queryKey: ['admin-overdue'],
    queryFn: async () => { const r = await adminApi.get('/billing/overdue'); return r.data?.data ?? r.data ?? [] },
  })

  const hour = new Date().getHours()
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'

  return (
    <div style={{ color: '#F7FAFC' }}>
      {/* Header */}
      <div style={{ marginBottom: 28, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: '#F7FAFC', margin: '0 0 5px', letterSpacing: '-0.3px' }}>
            {greeting}, {user?.fullName?.split(' ')[0] ?? 'Admin'}
          </h1>
          <div style={{ fontSize: 13, color: '#4A5568' }}>
            Platform health snapshot · {new Date().toLocaleDateString('en-ZA', { weekday: 'long', day: 'numeric', month: 'long' })}
          </div>
        </div>
        <button onClick={() => refetch()}
          style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 14px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8, color: '#718096', fontSize: 12, cursor: 'pointer', fontWeight: 500 }}>
          <RefreshCw size={13} /> Refresh
          {dataUpdatedAt ? <span style={{ color: '#4A5568', marginLeft: 4 }}>· {new Date(dataUpdatedAt).toLocaleTimeString('en-ZA', { hour: '2-digit', minute: '2-digit' })}</span> : null}
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 80, color: '#4A5568' }}>
          <RefreshCw size={28} style={{ marginBottom: 12, animation: 'spin 1s linear infinite' }} />
          <div>Loading platform data...</div>
          <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
        </div>
      ) : (
        <>
          {/* MRR banner */}
          <div style={{ background: 'linear-gradient(135deg, #1B3A6B22, #0D948822)', border: '1px solid #0D948840', borderRadius: 14, padding: '20px 24px', marginBottom: 24, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 700, color: '#0D9488', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 6 }}>Monthly Recurring Revenue</div>
              <div style={{ fontSize: 38, fontWeight: 900, color: '#0D9488', letterSpacing: '-1px' }}>{fmtR(dash?.mrr)}</div>
              <div style={{ fontSize: 13, color: '#718096', marginTop: 4 }}>ARR projection: <strong style={{ color: '#F7FAFC' }}>{fmtR(dash?.arrProjection)}</strong></div>
            </div>
            <div style={{ display: 'flex', gap: 24 }}>
              {[
                { label: 'Total tenants',   value: dash?.totalTenants   ?? 0, color: '#F7FAFC' },
                { label: 'Active',          value: dash?.activeTenants  ?? 0, color: '#0D9488' },
                { label: 'Pilot',           value: dash?.pilotTenants   ?? 0, color: '#D97706' },
                { label: 'Suspended',       value: dash?.suspendedTenants ?? 0, color: '#FC8181' },
              ].map(s => (
                <div key={s.label} style={{ textAlign: 'center' as const }}>
                  <div style={{ fontSize: 24, fontWeight: 800, color: s.color }}>{s.value}</div>
                  <div style={{ fontSize: 10, color: '#4A5568', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{s.label}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Stat grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 24 }}>
            <StatCard label="New signups (7d)" value={dash?.newSignupsThisWeek ?? 0} color="#60A5FA" bg="#1D4ED822" icon={<Users size={16} />} onClick={() => navigate('/tenants')} />
            <StatCard label="Pilots expiring (7d)" value={dash?.pilotsExpiring7d ?? 0} color="#F6AD55" bg="#D9770622" icon={<Clock size={16} />} sub={`${dash?.pilotsExpiring14d ?? 0} within 14 days`} onClick={() => navigate('/tenants?filter=expiring')} />
            <StatCard label="Overdue accounts" value={dash?.overdueAccounts ?? 0} color="#FC8181" bg="#DC262622" icon={<AlertTriangle size={16} />} onClick={() => navigate('/billing?filter=overdue')} />
            <StatCard label="Conversions (month)" value={dash?.conversionsThisMonth ?? 0} color="#68D391" bg="#16653422" icon={<CheckCircle size={16} />} sub={`${dash?.churnThisMonth ?? 0} churned`} />
          </div>

          {/* Alerts */}
          {(expiring.length > 0 || overdue.length > 0) && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>

              {/* Expiring pilots */}
              {expiring.length > 0 && (
                <div style={{ background: '#13161E', border: '1px solid #D9770640', borderRadius: 12, overflow: 'hidden' }}>
                  <div style={{ padding: '14px 18px', borderBottom: '1px solid #1E2532', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Clock size={14} color="#D97706" />
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#F7FAFC' }}>Pilots expiring in 7 days</span>
                    </div>
                    <span style={{ background: '#D9770622', color: '#D97706', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{expiring.length}</span>
                  </div>
                  <div style={{ maxHeight: 260, overflowY: 'auto' }}>
                    {expiring.map((t: any) => (
                      <div key={t.slug} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '11px 18px', borderBottom: '1px solid #1E2532', cursor: 'pointer' }}
                        onClick={() => navigate(`/tenants/${t.slug}`)}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                        <div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: '#F7FAFC' }}>{t.name}</div>
                          <div style={{ fontSize: 11, color: '#718096' }}>{t.slug} · {t.trial_module_count} trial modules</div>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <div style={{ fontSize: 11, color: '#D97706', fontWeight: 700 }}>Expires {fmtDate(t.earliest_expiry)}</div>
                          <ArrowRight size={12} color="#4A5568" />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Overdue accounts */}
              {overdue.length > 0 && (
                <div style={{ background: '#13161E', border: '1px solid #DC262640', borderRadius: 12, overflow: 'hidden' }}>
                  <div style={{ padding: '14px 18px', borderBottom: '1px solid #1E2532', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <AlertTriangle size={14} color="#FC8181" />
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#F7FAFC' }}>Overdue accounts</span>
                    </div>
                    <span style={{ background: '#DC262622', color: '#FC8181', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{overdue.length}</span>
                  </div>
                  <div style={{ maxHeight: 260, overflowY: 'auto' }}>
                    {overdue.map((t: any) => (
                      <div key={t.slug} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '11px 18px', borderBottom: '1px solid #1E2532', cursor: 'pointer' }}
                        onClick={() => navigate(`/tenants/${t.slug}`)}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                        <div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: '#F7FAFC' }}>{t.name}</div>
                          <div style={{ fontSize: 11, color: '#718096' }}>{t.slug}</div>
                        </div>
                        <div style={{ textAlign: 'right' as const }}>
                          <div style={{ fontSize: 13, fontWeight: 700, color: '#FC8181' }}>{fmtR(t.amount_owed)}</div>
                          <div style={{ fontSize: 11, color: '#718096' }}>{Math.round(t.days_overdue)} days overdue</div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* MRR by module */}
          {dash?.mrrByModule?.length > 0 && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden', marginBottom: 24 }}>
              <div style={{ padding: '14px 20px', borderBottom: '1px solid #1E2532' }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: '#F7FAFC' }}>MRR by module</div>
              </div>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid #1E2532' }}>
                      {['Module','Active tenants','Trial','Monthly revenue'].map(h => (
                        <th key={h} style={{ padding: '10px 20px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568', letterSpacing: '0.05em' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {dash.mrrByModule.map((m: any) => (
                      <tr key={m.key} style={{ borderBottom: '1px solid #1E2532' }}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                        <td style={{ padding: '11px 20px', fontWeight: 600, color: '#F7FAFC' }}>{m.name}</td>
                        <td style={{ padding: '11px 20px', color: '#0D9488', fontWeight: 700 }}>{m.active_count}</td>
                        <td style={{ padding: '11px 20px', color: '#718096' }}>{m.trial_count}</td>
                        <td style={{ padding: '11px 20px', fontWeight: 700, color: m.module_mrr > 0 ? '#68D391' : '#4A5568' }}>{fmtR(m.module_mrr)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Top 10 tenants */}
          {dash?.top10TenantsByMrr?.length > 0 && (
            <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
              <div style={{ padding: '14px 20px', borderBottom: '1px solid #1E2532', display: 'flex', justifyContent: 'space-between' }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: '#F7FAFC' }}>Top tenants by MRR</div>
                <button onClick={() => navigate('/tenants')} style={{ display: 'flex', alignItems: 'center', gap: 4, background: 'none', border: 'none', cursor: 'pointer', color: '#0D9488', fontSize: 12 }}>
                  View all <ArrowRight size={12} />
                </button>
              </div>
              <div>
                {dash.top10TenantsByMrr.map((t: any, i: number) => (
                  <div key={t.slug ?? i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 20px', borderBottom: '1px solid #1E2532', cursor: 'pointer' }}
                    onClick={() => navigate(`/tenants/${t.slug}`)}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <div style={{ width: 22, height: 22, borderRadius: '50%', background: '#2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700, color: '#718096', flexShrink: 0 }}>{i + 1}</div>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: '#F7FAFC' }}>{t.name}</div>
                        <div style={{ fontSize: 11, color: '#4A5568' }}>{t.slug} · {t.module_count} modules</div>
                      </div>
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 800, color: '#0D9488' }}>{fmtR(t.tenant_mrr)}/mo</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
