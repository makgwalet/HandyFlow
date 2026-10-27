// src/pages/facilities/ApprovalLimitsDialog.tsx
//
// The most each person may approve on one purchase order. Once any limit exists, approvers without one cannot approve
// orders; administrators are never limited. Administrators only.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { facilitiesApi } from '../../api/facilities.api'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, Dialog, EmptyState, Field, Notice } from './ui'
import { fmtDate, fmtMoney, inputStyle } from './format'

export default function ApprovalLimitsDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const limits = useQuery({ queryKey: ['facilities', 'approval-limits'], queryFn: facilitiesApi.approvalLimits })
  const users = useQuery({ queryKey: ['identity', 'users'], queryFn: facilitiesApi.users, staleTime: 5 * 60_000, retry: false })
  const [userId, setUserId] = useState('')
  const [amount, setAmount] = useState('')
  const name = (id: string) => users.data?.find(u => u.id === id)?.name ?? `User ${id.slice(0, 8)}`
  const n = Number(amount)
  const refresh = () => qc.invalidateQueries({ queryKey: ['facilities', 'approval-limits'] })
  const set = useMutation({
    mutationFn: () => facilitiesApi.setApprovalLimit(userId.trim(), n),
    onSuccess: () => { refresh(); setUserId(''); setAmount(''); toast.success('Limit saved') },
    onError: e => toast.error(errorMessage(e)),
  })
  const remove = useMutation({
    mutationFn: (id: string) => facilitiesApi.removeApprovalLimit(id),
    onSuccess: () => { refresh(); toast.success('Limit removed') },
    onError: e => toast.error(errorMessage(e)),
  })
  const people = users.data ?? []
  return (
    <Dialog title="Purchase order approval limits" onClose={onClose} width={560}>
      <Notice tone="info">Once you set a limit for anyone, only people with a limit (and administrators) can approve purchase orders, and only up to their amount.</Notice>
      <h3 style={{ margin: '18px 0 8px', fontSize: 13, fontWeight: 700 }}>Current limits</h3>
      {limits.isError && <Notice tone="bad">The limits could not be loaded.</Notice>}
      {limits.data?.length === 0 && <EmptyState>No limits set. Anyone who can approve orders may approve any amount.</EmptyState>}
      {limits.data?.map(l => (
        <div key={l.userId} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 0', borderTop: '1px solid var(--hf-border-subtle)' }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13.5 }}>{name(l.userId)}</div>
            <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>Updated {fmtDate(l.updatedAt)}</div>
          </div>
          <strong>{fmtMoney(l.maxOrderValue)}</strong>
          <Btn aria-label={`Remove limit for ${name(l.userId)}`} busy={remove.isPending && remove.variables === l.userId} onClick={() => remove.mutate(l.userId)}><Trash2 size={13} /></Btn>
        </div>
      ))}
      <h3 style={{ margin: '20px 0 8px', fontSize: 13, fontWeight: 700 }}>Set a limit</h3>
      <Field label="Person *" hint={users.isError ? "Your role can't list users, so paste the person's user id." : undefined}>
        {users.isError
          ? <input value={userId} onChange={e => setUserId(e.target.value)} placeholder="User id" style={inputStyle} />
          : <select value={userId} onChange={e => setUserId(e.target.value)} style={inputStyle}><option value="">Select a person…</option>{people.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}</select>}
      </Field>
      <Field label="Most they may approve on one order (R) *"><input inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} style={inputStyle} /></Field>
      <Btn kind="primary" busy={set.isPending} disabled={!userId.trim() || amount === '' || !(n >= 0)} onClick={() => set.mutate()}>Save limit</Btn>
      <p style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>To change someone's limit, save a new amount for them.</p>
    </Dialog>
  )
}
