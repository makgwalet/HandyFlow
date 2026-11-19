// src/pages/marketing/ReportsTab.tsx
import { useState } from 'react'
import { BarChart2, DollarSign } from 'lucide-react'
import AnalyticsTab from './AnalyticsTab'
import RevenuePanel from './RevenuePanel'
import { Tabs } from './ui'

export default function ReportsTab({ summary }: { summary?: any }) {
  const [sub, setSub] = useState('performance')
  return (
    <div>
      <div style={{ marginBottom: 18 }}>
        <Tabs active={sub} onChange={setSub} tabs={[
          { id: 'performance', label: 'Campaign performance', icon: <BarChart2 size={14} /> },
          { id: 'revenue', label: 'Revenue & ROI', icon: <DollarSign size={14} /> }]} />
      </div>
      {sub === 'performance' ? <AnalyticsTab summary={summary} /> : <RevenuePanel />}
    </div>
  )
}