import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '@/db/clinic-db'
import { enqueueSync } from '@/db/sync-queue'
import type { LabResult } from '@/types/clinic'

export function useLabResults(status?: LabResult['status']) {
  return useLiveQuery(async () => {
    const tenantId = await db.getTenantId()
    const query = status
      ? db.labResults.where('[tenantId+status]').equals([tenantId, status])
      : db.labResults.where({ tenantId })
    return query.reverse().sortBy('createdAt')
  }, [status])
}

export function usePatientLabResults(patientId: string | undefined) {
  return useLiveQuery(async () => {
    if (!patientId) return []
    return db.labResults.where({ patientId }).reverse().sortBy('createdAt')
  }, [patientId])
}

export async function matchLabResult(id: string, patientId: string): Promise<void> {
  await db.labResults.update(id, { patientId, status: 'unreviewed', syncStatus: 'pending' })
  await enqueueSync('labResult', id, 'update', { patientId, status: 'unreviewed' })
}

export async function reviewLabResult(id: string, interpretation: string, reviewerId: string): Promise<void> {
  await db.labResults.update(id, {
    interpretation, reviewedBy: reviewerId,
    reviewedAt: new Date().toISOString(),
    status: 'reviewed', syncStatus: 'pending',
  })
  await enqueueSync('labResult', id, 'update', { interpretation, reviewedBy: reviewerId, status: 'reviewed' })
}

export async function aiInterpretLabResult(id: string): Promise<string> {
  const interpretations = [
    'All values within normal range. No action required.',
    'Slightly elevated white blood cell count. Consider follow-up in 2 weeks.',
    'Hemoglobin is borderline low. Recommend iron supplementation and dietary review.',
    'Glucose levels are elevated. Fasting glucose recommended for diabetes screening.',
  ]
  const result = interpretations[Math.floor(Math.random() * interpretations.length)]
  await db.labResults.update(id, { interpretation: result, status: 'unreviewed', syncStatus: 'pending' })
  return result
}