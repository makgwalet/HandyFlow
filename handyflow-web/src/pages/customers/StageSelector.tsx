// src/pages/customers/StageSelector.tsx
//
// FIX: "no lead/pipeline stage tracking" gap. Unlike ConsentPanel/
// FollowUpPanel/CommunicationPanel (collapsible, secondary detail), a
// pipeline stage is a defining characteristic of a lead — shown always
// visible near the top of the modal, not behind a click.
//
// Only renders for LEAD-type customers — a CUSTOMER has no pipeline
// position (see Customer.changeStage, which enforces this server-side
// too, not just here).

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Stage = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'WON' | 'LOST'

interface StageData { customerId: string; customerType: string; stage: Stage | null }

const STAGE_CONFIG: Record<Stage, { label: string; color: string; bg: string; border: string }> = {
  NEW:       { label: 'New',       color: '#64748B', bg: '#F8FAFC', border: '#E2E8F0' },
  CONTACTED: { label: 'Contacted', color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE' },
  QUALIFIED: { label: 'Qualified', color: '#9333EA', bg: '#FAF5FF', border: '#E9D5FF' },
  WON:       { label: 'Won',       color: '#16A34A', bg: '#F0FDF4', border: '#BBF7D0' },
  LOST:      { label: 'Lost',      color: '#DC2626', bg: '#FEF2F2', border: '#FECACA' },
}

const STAGE_ORDER: Stage[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'WON', 'LOST']

export function StageSelector({ customerId, customerType }: { customerId: string; customerType: string }) {
  const qc = useQueryClient()

  const { data } = useQuery<{ data: StageData }>({
    queryKey: ['lead-stage', customerId],
    queryFn: () => apiClient.get(`/api/v1/crm/customers/${customerId}/stage`).then(r => r.data),
    enabled: customerType === 'LEAD',
  })

  const changeStage = useMutation({
    mutationFn: (stage: Stage) => apiClient.patch(`/api/v1/crm/customers/${customerId}/stage`, { stage }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['lead-stage', customerId] }),
  })

  if (customerType !== 'LEAD') return null

  const currentStage = data?.data?.stage

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', margin: '4px 0 12px' }}>
      <span style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.06em', marginRight: 2 }}>
        Pipeline:
      </span>
      {STAGE_ORDER.map(stage => {
        const cfg = STAGE_CONFIG[stage]
        const active = currentStage === stage
        return (
          <button
            key={stage}
            onClick={() => !active && changeStage.mutate(stage)}
            disabled={changeStage.isPending}
            style={{
              padding: '4px 11px', borderRadius: 20, fontSize: 12, fontWeight: 600,
              cursor: active ? 'default' : 'pointer', fontFamily: 'inherit',
              border: `1.5px solid ${active ? cfg.color : cfg.border}`,
              background: active ? cfg.bg : 'white',
              color: active ? cfg.color : '#94A3B8',
              opacity: changeStage.isPending ? 0.6 : 1,
            }}>
            {cfg.label}
          </button>
        )
      })}
    </div>
  )
}
