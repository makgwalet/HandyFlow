import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '@/db/clinic-db'
import { enqueueSync } from '@/db/sync-queue'
import type { Claim, Payment } from '@/types/clinic'
import { generateId } from '@/lib/utils'

export function useClaims(status?: Claim['status']) {
  return useLiveQuery(async () => {
    const tenantId = await db.getTenantId()
    const query = status
      ? db.claims.where('[tenantId+status]').equals([tenantId, status])
      : db.claims.where({ tenantId })
    return query.reverse().sortBy('createdAt')
  }, [status])
}

export function usePayments(patientId?: string) {
  return useLiveQuery(async () => {
    const tenantId = await db.getTenantId()
    const query = patientId
      ? db.payments.where('[tenantId+patientId]').equals([tenantId, patientId])
      : db.payments.where({ tenantId })
    return query.reverse().sortBy('createdAt')
  }, [patientId])
}

export function useOutstandingBalances() {
  return useLiveQuery(async () => {
    const tenantId = await db.getTenantId()
    const claims = await db.claims.where({ tenantId }).toArray()
    const payments = await db.payments.where({ tenantId }).toArray()
    const patientMap = new Map<string, { patientId: string; total: number; paid: number }>()
    for (const claim of claims) {
      if (claim.status === 'paid') continue
      const entry = patientMap.get(claim.patientId) || { patientId: claim.patientId, total: 0, paid: 0 }
      entry.total += claim.patientPortion || claim.totalAmount
      patientMap.set(claim.patientId, entry)
    }
    for (const payment of payments) {
      const entry = patientMap.get(payment.patientId)
      if (entry) entry.paid += payment.amount
    }
    const patients = await db.patients.where({ tenantId }).toArray()
    return Array.from(patientMap.values())
      .map(o => ({ ...o, patient: patients.find(p => p.id === o.patientId), balance: o.total - o.paid }))
      .filter(o => o.balance > 0)
      .sort((a, b) => b.balance - a.balance)
  })
}

export async function recordPayment(data: Omit<Payment, 'id' | 'tenantId' | 'createdAt' | 'syncStatus'>): Promise<Payment> {
  const tenantId = await db.getTenantId()
  const payment: Payment = {
    id: generateId(), tenantId, ...data,
    createdAt: new Date().toISOString(),
    syncStatus: 'local',
  }
  await db.payments.add(payment)
  await enqueueSync('payment', payment.id, 'create', payment)
  return payment
}