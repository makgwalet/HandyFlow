// src/pages/marketing/MarketingPage.tsx
//
// Sections are routes (/marketing/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
// The KPI strip shows above every section; the summary it loads is also
// handed to Analytics.
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Users, CheckCircle, Send, Clock, Inbox } from 'lucide-react'
import CampaignsTab   from './CampaignsTab'
import TemplatesTab   from './TemplatesTab'
import ContactsTab    from './ContactsTab'
import AnalyticsTab   from './AnalyticsTab'
import { SectionedModulePage } from '../../components/shell/SectionedModulePage'
import { MARKETING_SECTIONS } from '../../navigation/moduleSections'

export function MarketingPage() {
  const { data: summary } = useQuery({
    queryKey: ['marketing-summary'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/summary')
      return r.data?.data ?? r.data
    },
    refetchInterval: 30_000,
  })

  const kpis = [
    { label: 'Total contacts', value: summary?.totalContacts  ?? 0, color: 'var(--hf-primary-text)', bg: 'var(--hf-indigo-soft)', icon: <Users size={16} /> },
    { label: 'Opted in',       value: summary?.optedInCount   ?? 0, color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)', icon: <CheckCircle size={16} /> },
    { label: 'Campaigns sent', value: summary?.sentCampaigns  ?? 0, color: 'var(--hf-accent-text)', bg: 'var(--hf-accent-soft)', icon: <Send size={16} /> },
    { label: 'Scheduled',      value: summary?.scheduledCampaigns ?? 0, color: 'var(--hf-warning-text)', bg: 'var(--hf-warning-soft)', icon: <Clock size={16} /> },
    { label: 'Queue pending',  value: summary?.queuePending   ?? 0, color: 'var(--hf-violet-text)', bg: 'var(--hf-violet-soft)', icon: <Inbox size={16} /> },
  ]

  const banner = (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12, marginBottom: 24 }}>
      {kpis.map(k => (
        <div key={k.label} style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 36, height: 36, borderRadius: 9, background: k.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color: k.color, flexShrink: 0 }}>{k.icon}</div>
          <div>
            <div style={{ fontSize: 22, fontWeight: 800, color: k.color, letterSpacing: '-0.02em' }}>{k.value}</div>
            <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 1 }}>{k.label}</div>
          </div>
        </div>
      ))}
    </div>
  )

  return (
    <SectionedModulePage config={MARKETING_SECTIONS} banner={banner}
      subtitle="Email campaigns · POPIA-compliant contacts · Templates · Analytics"
      render={id => {
        switch (id) {
          case 'campaigns': return <CampaignsTab />
          case 'templates': return <TemplatesTab />
          case 'contacts':  return <ContactsTab />
          case 'analytics': return <AnalyticsTab summary={summary} />
          default:          return null
        }
      }} />
  )
}
