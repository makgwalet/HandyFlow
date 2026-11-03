// src/pages/contracting/ContractSharingSection.tsx
// Secure sharing of a signed contract (its frozen executed copy) with people outside the company. Shown in the contract's detail.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { apiClient } from '../../api/client'
import { usePermissions } from '../../hooks/usePermission'
import { SecureSharePanel, type ShareRecipient } from '../../components/secure/SecureSharePanel'

interface SharingView {
  contractId: string; contractNumber: string; status: string; shareable: boolean; disposed: boolean
  executedCopy: { sha256: string; sizeBytes: number | null; storedAt: string } | null
  shareId: string | null; recipients: ShareRecipient[]
}

export function ContractSharingSection({ contractId }: { contractId: string }) {
  const qc = useQueryClient()
  const canChange = usePermissions(['USER_UPDATE', 'CONTRACTS_MANAGE', 'CONTRACTS_ADMIN'], 'any')
  const base = `/api/v1/contracts/${contractId}/sharing`
  const { data, isLoading, isError } = useQuery<SharingView>({ queryKey: ['contract-sharing', contractId], queryFn: async () => (await apiClient.get(base)).data })
  if (isLoading) return <div style={{ fontSize: 12, color: 'var(--hf-text-faint)', marginBottom: 14 }}>Loading sharing…</div>
  if (isError || !data) return null
  const copy = data.executedCopy
  return (
    <div style={{ marginBottom: 14 }}>
      <SecureSharePanel base={base} recipients={data.recipients} canChange={canChange} shareable={data.shareable}
        notShareableReason={data.disposed ? 'This contract was disposed of under the records retention policy; it can no longer be shared.' : null}
        onChanged={() => qc.invalidateQueries({ queryKey: ['contract-sharing', contractId] })}
        intro={<>
          People you give access to receive their own link to the signed contract and confirm their email with a code each time.
          {copy
            ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginLeft: 6 }}><ShieldCheck size={12} /> Signed copy stored {new Date(copy.storedAt).toLocaleDateString('en-ZA')} · sha256 {copy.sha256.slice(0, 12)}…</span>
            : data.shareable ? ' The signed copy is stored the first time it is shared.' : null}
        </>} />
    </div>
  )
}
