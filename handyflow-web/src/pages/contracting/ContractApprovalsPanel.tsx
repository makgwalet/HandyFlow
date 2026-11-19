// Approvals for one contract: what is waiting, what was decided, and asking for the approvals this status allows.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { ApprovalCard } from './ApprovalsTab'
import { ACTION_LABEL, listOf, requestableActions, type Approval, type PolicyRow } from './approvalsShared'
import { apiError } from './contractingApi'

export function ContractApprovalsPanel({ contractId, status, canManage }: { contractId: string; status: string; canManage: boolean }) {
  const qc = useQueryClient()
  const [error, setError] = useState('')
  const { data: all = [], isLoading } = useQuery<Approval[]>({
    queryKey: ['contract-approvals', 'list'],
    queryFn: async () => listOf(await apiClient.get('/api/v1/contracts/approvals')),
  })
  const { data: policy = [] } = useQuery<PolicyRow[]>({
    queryKey: ['contract-approvals', 'policy'],
    queryFn: async () => listOf(await apiClient.get('/api/v1/contracts/approvals/policy')),
  })
  const mine = all.filter(a => a.subjectId === contractId)
  const askable = canManage ? requestableActions(status, all, contractId) : []
  const request = useMutation({
    mutationFn: (action: string) => apiClient.post('/api/v1/contracts/approvals', { action, subjectId: contractId, note: null }),
    onSuccess: () => { setError(''); qc.invalidateQueries({ queryKey: ['contract-approvals'] }) },
    onError: (e: unknown) => setError(apiError(e, 'Could not ask for approval')),
  })
  const required = (action: string) => policy.find(p => p.action === action)?.required

  return (
    <div style={{ marginBottom: 14 }}>
      {askable.length > 0 && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 10 }}>
          {askable.map(a => (
            <button key={a} disabled={request.isPending} onClick={() => request.mutate(a)}
              style={{ padding: '8px 14px', borderRadius: 7, border: '1px solid var(--hf-border)', background: 'var(--hf-surface)', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
              Ask for approval: {ACTION_LABEL[a].toLowerCase()}{required(a) ? '' : ' (optional)'}
            </button>
          ))}
        </div>
      )}
      {error && <div role="alert" style={{ marginBottom: 10, fontSize: 12, color: 'var(--hf-danger-text)' }}>{error}</div>}
      {isLoading ? <div style={{ fontSize: 13 }}>Loading…</div> : mine.length === 0 ? (
        <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>No approval requests for this contract yet.</div>
      ) : (
        <div style={{ display: 'grid', gap: 10 }}>
          {mine.map(a => <ApprovalCard key={a.id} a={a} allowSole={policy.find(p => p.action === a.action)?.allowSoleUserException ?? false} />)}
        </div>
      )}
    </div>
  )
}
