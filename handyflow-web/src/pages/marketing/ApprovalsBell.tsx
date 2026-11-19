// src/pages/marketing/ApprovalsBell.tsx
// Shows how many campaigns are waiting for review, and (for approvers) how many are waiting on them.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { apiClient } from '../../api/client'
import { C, fmtDT } from './ui'

export interface PendingReview { id: string; name: string; version: number; submittedAt?: string | null; canApprove: boolean }

/** What the badge counts: the items this user can act on; anyone else sees nothing to do. */
export const badgeCount = (items: PendingReview[]) => items.filter(i => i.canApprove).length

export default function ApprovalsBell({ onOpen }: { onOpen: () => void }) {
  const [open, setOpen] = useState(false)
  const { data: items = [] } = useQuery<PendingReview[]>({
    queryKey: ['marketing-pending-review'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/approvals/pending')).data?.data ?? [],
    refetchInterval: 60_000,
  })
  const n = badgeCount(items)
  return (
    <div style={{ position: 'relative' }}>
      <button type="button" onClick={() => setOpen(o => !o)} title="Campaigns awaiting approval"
        aria-label={n > 0 ? `Approvals, ${n} waiting for you` : 'Approvals'}
        style={{ position: 'relative', display: 'flex', padding: 9, background: '#fff', border: `1px solid ${C.line}`, borderRadius: 10, cursor: 'pointer', color: C.ink }}>
        <Bell size={17} aria-hidden="true" />
        {n > 0 && <span style={{ position: 'absolute', top: -6, right: -6, minWidth: 18, height: 18, padding: '0 5px', borderRadius: 9, background: '#DC2626', color: '#fff', fontSize: 10, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{n}</span>}
      </button>
      {open && (
        <div role="dialog" aria-label="Campaigns awaiting review"
          style={{ position: 'absolute', right: 0, top: 44, width: 320, background: '#fff', border: `1px solid ${C.line}`, borderRadius: 12, boxShadow: '0 12px 32px rgba(15,23,42,0.14)', zIndex: 50, overflow: 'hidden' }}>
          <div style={{ padding: '10px 14px', fontSize: 12, fontWeight: 800, color: C.ink, borderBottom: `1px solid ${C.line}` }}>Awaiting review ({items.length})</div>
          {items.length === 0 && <div style={{ padding: 18, fontSize: 12, color: C.mute, textAlign: 'center' }}>Nothing is waiting for review.</div>}
          {items.map(i => (
            <button key={i.id} type="button" onClick={() => { setOpen(false); onOpen() }}
              style={{ display: 'block', width: '100%', textAlign: 'left', padding: '10px 14px', background: 'none', border: 'none', borderBottom: `1px solid #F1F5F9`, cursor: 'pointer' }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: C.ink }}>{i.name}</div>
              <div style={{ fontSize: 11, color: C.mute, marginTop: 2 }}>Version {i.version} · submitted {fmtDT(i.submittedAt)} · {i.canApprove ? 'you can approve' : 'waiting for an approver'}</div>
            </button>))}
        </div>)}
    </div>
  )
}
