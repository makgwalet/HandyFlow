// src/pages/marketing/MarketingPage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, Megaphone, Send, Users, FileText, X, BarChart2,
  Search, Filter, Mail, CheckCircle, AlertTriangle, Eye,
  MousePointer, TrendingUp, RefreshCw, Play, Pause,
  XCircle, Clock, ChevronRight, Tag, Download, Settings,
  ArrowUpRight, Inbox,
} from 'lucide-react'
import CampaignsTab   from './CampaignsTab'
import TemplatesTab   from './TemplatesTab'
import ContactsTab    from './ContactsTab'
import AnalyticsTab   from './AnalyticsTab'

type Tab = 'campaigns' | 'templates' | 'contacts' | 'analytics'

const TABS = [
  { id: 'campaigns'  as Tab, label: 'Campaigns',  icon: Megaphone  },
  { id: 'templates'  as Tab, label: 'Templates',  icon: FileText   },
  { id: 'contacts'   as Tab, label: 'Contacts',   icon: Users      },
  { id: 'analytics'  as Tab, label: 'Analytics',  icon: BarChart2  },
]

export function MarketingPage() {
  const [activeTab, setActiveTab] = useState<Tab>('campaigns')

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

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 22 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--hf-accent)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Megaphone size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: 'var(--hf-text)', margin: 0 }}>Marketing</h1>
        </div>
        <p style={{ fontSize: 13, color: 'var(--hf-text-faint)', margin: 0, paddingLeft: 46 }}>
          Email campaigns · POPIA-compliant contacts · Templates · Analytics
        </p>
      </div>

      {/* KPI strip */}
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

      {/* Main card */}
      <div style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 14, padding: 24 }}>
        <div style={{ display: 'flex', gap: 0, borderBottom: '1px solid var(--hf-border)', marginBottom: 26, overflowX: 'auto' }}>
          {TABS.map(tab => {
            const Icon   = tab.icon
            const active = activeTab === tab.id
            return (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '10px 20px', background: 'none', border: 'none', whiteSpace: 'nowrap', borderBottom: `2px solid ${active ? '#0D9488' : 'transparent'}`, color: active ? 'var(--hf-accent-text)' : 'var(--hf-text-muted)', fontWeight: active ? 700 : 400, fontSize: 14, cursor: 'pointer', marginBottom: -1, transition: 'all 0.15s' }}>
                <Icon size={15} />{tab.label}
              </button>
            )
          })}
        </div>

        {activeTab === 'campaigns'  && <CampaignsTab />}
        {activeTab === 'templates'  && <TemplatesTab />}
        {activeTab === 'contacts'   && <ContactsTab />}
        {activeTab === 'analytics'  && <AnalyticsTab summary={summary} />}
      </div>
    </div>
  )
}
