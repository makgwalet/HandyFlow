// src/pages/marketing/MarketingPage.tsx
// Sections are routes (/marketing/:section), like the other modules; /marketing opens on the dashboard.
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import DashboardTab from './DashboardTab'
import CampaignsTab from './CampaignsTab'
import TemplatesTab from './TemplatesTab'
import ContactsTab from './ContactsTab'
import AudiencesTab from './AudiencesTab'
import JourneysTab from './JourneysTab'
import AnalyticsTab from './AnalyticsTab'
import SettingsTab from './SettingsTab'
import ApprovalsBell from './ApprovalsBell'
import { SectionedModulePage } from '../../components/shell/SectionedModulePage'
import { MARKETING_SECTIONS } from '../../navigation/moduleSections'

export function MarketingPage() {
  const navigate = useNavigate()
  const [openNew, setOpenNew] = useState(false)   // opens the campaign wizard when the Campaigns section mounts

  const { data: summary } = useQuery({
    queryKey: ['marketing-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/summary'); return r.data?.data ?? r.data },
    refetchInterval: 30_000,
  })

  return (
    <SectionedModulePage
      config={MARKETING_SECTIONS}
      subtitle="Campaigns, segments and automation, built on opt-in consent and a suppression list that is checked before every send."
      action={<ApprovalsBell onOpen={() => navigate('/marketing/campaigns')} />}
      render={(id, goTo) => {
        switch (id) {
          case 'dashboard':  return <DashboardTab onNavigate={goTo} onCreate={() => { setOpenNew(true); goTo('campaigns') }} />
          case 'campaigns':  return <CampaignsTab autoOpenNew={openNew} onAutoOpened={() => setOpenNew(false)} />
          case 'templates':  return <TemplatesTab />
          case 'contacts':   return <ContactsTab />
          case 'segments':   return <AudiencesTab />
          case 'automation': return <JourneysTab />
          case 'reports':    return <AnalyticsTab summary={summary} />
          case 'settings':   return <SettingsTab />
          default:           return null
        }
      }} />
  )
}
