// src/pages/contracting/ContractingPage.tsx
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import ContractsDashboard from './ContractsDashboard'
import ContractsTab       from './ContractsTab'
import TemplatesTab       from './TemplatesTab'
import { SectionedModulePage } from '../../components/shell/SectionedModulePage'
import { CONTRACTING_SECTIONS } from '../../navigation/moduleSections'

// Unwrap ApiResponse<Page<T>> or ApiResponse<List<T>>
export const unwrap = (r: any): any[] => {
  const payload = r.data?.data ?? r.data
  return Array.isArray(payload) ? payload : payload?.content ?? []
}

export const fmtR = (n: any) =>
  n != null
    ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : '—'

export default function ContractingPage() {
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

  // KPI strip — only shown when there is data
  const banner = contracts.length > 0 ? (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 20 }}>
      {KPI_STATS.map(k => (
        <div key={k.label} style={{ background: k.bg, borderRadius: 12, padding: '13px 18px' }}>
          <div style={{ fontSize: typeof k.value === 'number' ? 24 : 18, fontWeight: 800, color: k.color }}>{k.value}</div>
          <div style={{ fontSize: 11, color: k.color, marginTop: 2, opacity: 0.8 }}>{k.label}</div>
        </div>
      ))}
    </div>
  ) : null

  // Sections are routes (/contracts/:section); /contracts opens on the list.
  return (
    <SectionedModulePage config={CONTRACTING_SECTIONS} banner={banner}
      subtitle="Contract lifecycle · OTP signing · Template library · Audit trail"
      render={(id, goTo) => {
        switch (id) {
          case 'dashboard': return <ContractsDashboard onNavigate={goTo} />
          case 'contracts': return <ContractsTab />
          case 'templates': return <TemplatesTab />
          default:          return null
        }
      }} />
  )
}
