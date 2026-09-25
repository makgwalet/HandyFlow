// src/pages/contracting/ContractingPage.tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { FilePlus, FileText, Layout, BarChart2 } from 'lucide-react'
import ContractsDashboard from './ContractsDashboard'
import ContractsTab       from './ContractsTab'
import TemplatesTab       from './TemplatesTab'

type Tab = 'dashboard' | 'contracts' | 'templates'

// Unwrap ApiResponse<Page<T>> or ApiResponse<List<T>>
export const unwrap = (r: any): any[] => {
  const payload = r.data?.data ?? r.data
  return Array.isArray(payload) ? payload : payload?.content ?? []
}

export const fmtR = (n: any) =>
  n != null
    ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '—'

const TABS: { id: Tab; label: string; Icon: any }[] = [
  { id: 'dashboard',  label: 'Dashboard',  Icon: BarChart2  },
  { id: 'contracts',  label: 'Contracts',  Icon: FileText   },
  { id: 'templates',  label: 'Templates',  Icon: Layout     },
]

export default function ContractingPage() {
  const [tab, setTab] = useState<Tab>('contracts')

  const { data: contracts = [] } = useQuery<any[]>({
    queryKey: ['contracts', 'all'],
    queryFn: async () => unwrap(await apiClient.get('/api/v1/contracts?size=200')),
  })

  const signed  = contracts.filter(c => c.status === 'SIGNED')
  const pending = contracts.filter(c => c.status === 'SENT')
  const drafts  = contracts.filter(c => c.status === 'DRAFT')
  const totalVal = signed.reduce((s, c) => s + (Number(c.valueAmount) || 0), 0)

  const KPI_STATS = [
    { label: 'Active (signed)',     value: signed.length,  color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)' },
    { label: 'Pending signature',   value: pending.length, color: 'var(--hf-info-text)', bg: 'var(--hf-info-soft)' },
    { label: 'Drafts',              value: drafts.length,  color: 'var(--hf-warning-text)', bg: 'var(--hf-warning-soft)' },
    { label: 'Active value',        value: fmtR(totalVal), color: 'var(--hf-primary-text)', bg: 'var(--hf-indigo-soft)' },
  ]

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif' }}>
      {/* Page header */}
      <div style={{ marginBottom: 22 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--hf-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <FilePlus size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: 'var(--hf-text)', margin: 0 }}>Contracting</h1>
        </div>
        <p style={{ fontSize: 12, color: 'var(--hf-text-faint)', margin: '0 0 0 46px' }}>
          Contract lifecycle · OTP signing · Template library · Audit trail
        </p>
      </div>

      {/* KPI strip — only shown when there is data */}
      {contracts.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 20 }}>
          {KPI_STATS.map(k => (
            <div key={k.label} style={{ background: k.bg, borderRadius: 12, padding: '13px 18px' }}>
              <div style={{ fontSize: typeof k.value === 'number' ? 24 : 18, fontWeight: 800, color: k.color }}>{k.value}</div>
              <div style={{ fontSize: 11, color: k.color, marginTop: 2, opacity: 0.8 }}>{k.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* Main card */}
      <div style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 14, padding: 24 }}>
        {/* Tab bar */}
        <div style={{ display: 'flex', gap: 2, borderBottom: '1px solid var(--hf-border)', marginBottom: 26 }}>
          {TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{
              display: 'flex', alignItems: 'center', gap: 6,
              padding: '9px 18px', background: 'none', border: 'none',
              borderBottom: tab === t.id ? '2px solid var(--hf-primary)' : '2px solid transparent',
              color: tab === t.id ? 'var(--hf-primary-text)' : 'var(--hf-text-muted)',
              fontWeight: tab === t.id ? 700 : 400,
              fontSize: 13, cursor: 'pointer', marginBottom: -1, whiteSpace: 'nowrap',
            }}>
              <t.Icon size={14} />{t.label}
            </button>
          ))}
        </div>

        {tab === 'dashboard' && <ContractsDashboard onNavigate={setTab} />}
        {tab === 'contracts' && <ContractsTab />}
        {tab === 'templates' && <TemplatesTab />}
      </div>
    </div>
  )
}
