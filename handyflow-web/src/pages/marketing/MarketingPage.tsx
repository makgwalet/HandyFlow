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
    { label: 'Total contacts', value: summary?.totalContacts  ?? 0, color: '#1B3A6B', bg: '#EEF2FF', icon: <Users size={16} /> },
    { label: 'Opted in',       value: summary?.optedInCount   ?? 0, color: '#166534', bg: '#DCFCE7', icon: <CheckCircle size={16} /> },
    { label: 'Campaigns sent', value: summary?.sentCampaigns  ?? 0, color: '#0D9488', bg: '#F0FDF9', icon: <Send size={16} /> },
    { label: 'Scheduled',      value: summary?.scheduledCampaigns ?? 0, color: '#D97706', bg: '#FFFBEB', icon: <Clock size={16} /> },
    { label: 'Queue pending',  value: summary?.queuePending   ?? 0, color: '#7C3AED', bg: '#F5F3FF', icon: <Inbox size={16} /> },
  ]

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 22 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: '#0D9488', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Megaphone size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: 0 }}>Marketing</h1>
        </div>
        <p style={{ fontSize: 13, color: '#94A3B8', margin: 0, paddingLeft: 46 }}>
          Email campaigns · POPIA-compliant contacts · Templates · Analytics
        </p>
      </div>

      {/* KPI strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12, marginBottom: 24 }}>
        {kpis.map(k => (
          <div key={k.label} style={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 12, padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 36, height: 36, borderRadius: 9, background: k.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color: k.color, flexShrink: 0 }}>{k.icon}</div>
            <div>
              <div style={{ fontSize: 22, fontWeight: 800, color: k.color, letterSpacing: '-0.02em' }}>{k.value}</div>
              <div style={{ fontSize: 11, color: '#9CA3AF', marginTop: 1 }}>{k.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Main card */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: 24 }}>
        <div style={{ display: 'flex', gap: 0, borderBottom: '1px solid #E2E8F0', marginBottom: 26, overflowX: 'auto' }}>
          {TABS.map(tab => {
            const Icon   = tab.icon
            const active = activeTab === tab.id
            return (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '10px 20px', background: 'none', border: 'none', whiteSpace: 'nowrap', borderBottom: `2px solid ${active ? '#0D9488' : 'transparent'}`, color: active ? '#0D9488' : '#64748B', fontWeight: active ? 700 : 400, fontSize: 14, cursor: 'pointer', marginBottom: -1, transition: 'all 0.15s' }}>
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
