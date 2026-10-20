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

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { LOST_REASONS, allowedMoves, lostProblem, stageBody, type LostReason } from './pipeline.logic'

type Stage = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'WON' | 'LOST'

interface StageData { customerId: string; customerType: string; stage: Stage | null }

const STAGE_CONFIG: Record<Stage, { label: string; color: string; bg: string; border: string }> = {
  NEW:       { label: 'New',       color: 'var(--hf-text-muted)', bg: 'var(--hf-surface-muted)', border: 'var(--hf-border)' },
  CONTACTED: { label: 'Contacted', color: 'var(--hf-info-text)', bg: 'var(--hf-info-soft)', border: 'var(--hf-info-border)' },
  QUALIFIED: { label: 'Qualified', color: 'var(--hf-violet-text)', bg: 'var(--hf-violet-soft)', border: 'var(--hf-violet-border)' },
  WON:       { label: 'Won',       color: 'var(--hf-success-text)', bg: 'var(--hf-success-soft)', border: 'var(--hf-success-border-subtle)' },
  LOST:      { label: 'Lost',      color: 'var(--hf-danger-text)', bg: 'var(--hf-danger-soft)', border: 'var(--hf-danger-border)' },
}

const STAGE_ORDER: Stage[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'WON', 'LOST']

export function StageSelector({ customerId, customerType }: { customerId: string; customerType: string }) {
  const qc = useQueryClient()

  const { data } = useQuery<StageData>({
    queryKey: ['lead-stage', customerId],
    queryFn: () => apiClient.get(`/api/v1/crm/customers/${customerId}/stage`).then(r => r.data),
    enabled: customerType === 'LEAD',
  })

  const [askLost, setAskLost] = useState(false), [reason, setReason] = useState<LostReason | ''>(''), [note, setNote] = useState(''), [err, setErr] = useState<string | null>(null)
  const changeStage = useMutation({
    mutationFn: (v: { stage: Stage; reason?: LostReason | ''; note?: string }) => apiClient.patch(`/api/v1/crm/customers/${customerId}/stage`, stageBody(v.stage, v.reason, v.note)),
    onSuccess: () => { setAskLost(false); setReason(''); setNote(''); setErr(null); qc.invalidateQueries({ queryKey: ['lead-stage', customerId] }); qc.invalidateQueries({ queryKey: ['crm'] }) },
    onError: (e: unknown) => setErr((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not change the stage. Try again.'),
  })

  if (customerType !== 'LEAD') return null

  const currentStage = data?.stage

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', margin: '4px 0 12px' }}>
      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.06em', marginRight: 2 }}>
        Pipeline:
      </span>
      {STAGE_ORDER.map(stage => {
        const cfg = STAGE_CONFIG[stage]
        const active = currentStage === stage
        return (
          <button
            key={stage}
            onClick={() => { if (active) return; setErr(null); if (stage === 'LOST') setAskLost(true); else { setAskLost(false); changeStage.mutate({ stage }) } }}
            disabled={changeStage.isPending || (!!currentStage && !active && !allowedMoves(currentStage).includes(stage))}
            title={currentStage && !active && !allowedMoves(currentStage).includes(stage) ? 'Reopen the lead first' : undefined}
            style={{
              padding: '4px 11px', borderRadius: 20, fontSize: 12, fontWeight: 600,
              cursor: active ? 'default' : 'pointer', fontFamily: 'inherit',
              border: `1.5px solid ${active ? cfg.color : cfg.border}`,
              background: active ? cfg.bg : 'var(--hf-surface)',
              color: active ? cfg.color : 'var(--hf-text-faint)',
              opacity: changeStage.isPending ? 0.6 : 1,
            }}>
            {cfg.label}
          </button>
        )
      })}
      {askLost && (
        <div style={{ flexBasis: '100%', display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center', marginTop: 4 }}>
          <select aria-label="Lost reason" value={reason} onChange={e => setReason(e.target.value as LostReason | '')} style={{ padding: '4px 8px', fontSize: 12 }}>
            <option value="">Why was it lost?</option>{LOST_REASONS.map(r => <option key={r.id} value={r.id}>{r.label}</option>)}</select>
          <input aria-label="Lost note" placeholder="Note" maxLength={500} value={note} onChange={e => setNote(e.target.value)} style={{ padding: '4px 8px', fontSize: 12, minWidth: 180 }} />
          <button type="button" disabled={!!lostProblem(reason, note) || changeStage.isPending} onClick={() => changeStage.mutate({ stage: 'LOST', reason, note })} style={{ padding: '4px 11px', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>Mark lost</button>
          <button type="button" onClick={() => { setAskLost(false); setErr(null) }} style={{ padding: '4px 11px', fontSize: 12, cursor: 'pointer' }}>Cancel</button>
        </div>)}
      {err && <div role="alert" style={{ flexBasis: '100%', fontSize: 12, color: 'var(--hf-danger-text)' }}>{err}</div>}
    </div>
  )
}
