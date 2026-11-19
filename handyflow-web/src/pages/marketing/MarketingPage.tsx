// src/pages/marketing/MarketingPage.tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Megaphone, LayoutDashboard, FileText, Users, Filter, Workflow, BarChart2, Settings as SettingsIcon } from 'lucide-react'
import DashboardTab from './DashboardTab'
import CampaignsTab from './CampaignsTab'
import TemplatesTab from './TemplatesTab'
import ContactsTab from './ContactsTab'
import AudiencesTab from './AudiencesTab'
import JourneysTab from './JourneysTab'
import AnalyticsTab from './AnalyticsTab'
import SettingsTab from './SettingsTab'
import ApprovalsBell from './ApprovalsBell'
import { Tabs, C } from './ui'

type Tab = 'dashboard' | 'campaigns' | 'templates' | 'contacts' | 'segments' | 'automation' | 'reports' | 'settings'

const TABS = [
  { id: 'dashboard',  label: 'Dashboard',  icon: <LayoutDashboard size={15} /> },
  { id: 'campaigns',  label: 'Campaigns',  icon: <Megaphone size={15} /> },
  { id: 'templates',  label: 'Templates',  icon: <FileText size={15} /> },
  { id: 'contacts',   label: 'Contacts',   icon: <Users size={15} /> },
  { id: 'segments',   label: 'Segments',   icon: <Filter size={15} /> },
  { id: 'automation', label: 'Automation', icon: <Workflow size={15} /> },
  { id: 'reports',    label: 'Reports',    icon: <BarChart2 size={15} /> },
  { id: 'settings',   label: 'Settings',   icon: <SettingsIcon size={15} /> },
]

export function MarketingPage() {
  const [tab, setTab] = useState<Tab>('dashboard')
  const [openNew, setOpenNew] = useState(false)   // opens the campaign wizard when the Campaigns tab mounts

  const { data: summary } = useQuery({
    queryKey: ['marketing-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/summary'); return r.data?.data ?? r.data },
    refetchInterval: 30_000,
  })

  const go = (t: string) => setTab(t as Tab)

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 18 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: C.primary, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Megaphone size={18} color="#fff" /></div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: C.ink, margin: 0 }}>Marketing</h1>
          <div style={{ marginLeft: 'auto' }}><ApprovalsBell onOpen={() => setTab('campaigns')} /></div>
        </div>
        <p style={{ fontSize: 13, color: C.mute, margin: 0, paddingLeft: 46 }}>
          Campaigns, segments and automation, built on opt-in consent and a suppression list that is checked before every send.
        </p>
      </div>

      <div style={{ background: '#fff', border: `1px solid ${C.line}`, borderRadius: 14, padding: '4px 24px 24px' }}>
        <div style={{ marginBottom: 22 }}><Tabs tabs={TABS} active={tab} onChange={go} /></div>

        {tab === 'dashboard'  && <DashboardTab onNavigate={go} onCreate={() => { setOpenNew(true); setTab('campaigns') }} />}
        {tab === 'campaigns'  && <CampaignsTab autoOpenNew={openNew} onAutoOpened={() => setOpenNew(false)} />}
        {tab === 'templates'  && <TemplatesTab />}
        {tab === 'contacts'   && <ContactsTab />}
        {tab === 'segments'   && <AudiencesTab />}
        {tab === 'automation' && <JourneysTab />}
        {tab === 'reports'    && <AnalyticsTab summary={summary} />}
        {tab === 'settings'   && <SettingsTab />}
      </div>
    </div>
  )
}