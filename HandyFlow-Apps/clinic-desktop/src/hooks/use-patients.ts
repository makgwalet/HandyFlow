import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '@/db/clinic-db'
import { enqueueSync } from '@/db/sync-queue'
import type { Patient } from '@/types/clinic'
import { generateId } from '@/lib/utils'

export function usePatients(search?: string, includeArchived = false) {
  return useLiveQuery(async () => {
    const tenantId = await db.getTenantId()
    let query = db.patients.where({ tenantId })
    if (!includeArchived) {
      query = db.patients.where('[tenantId+isArchived]').equals([tenantId, false])
    }
    const results = await query.toArray()
    if (search) {
      const lower = search.toLowerCase()
      return results.filter(p =>
        p.firstName.toLowerCase().includes(lower) ||
        p.lastName.toLowerCase().includes(lower) ||
        p.phone?.includes(lower) ||
        p.medicalAidNumber?.includes(lower)
      )
    }
    return results.sort((a, b) => a.lastName.localeCompare(b.lastName))
  }, [search, includeArchived])
}

export function usePatient(id: string | undefined) {
  return useLiveQuery(async () => {
    if (!id) return undefined
    return db.patients.get(id)
  }, [id])
}

export async function createPatient(data: Omit<Patient, 'id' | 'tenantId' | 'createdAt' | 'updatedAt' | 'syncStatus'>): Promise<Patient> {
  const tenantId = await db.getTenantId()
  const patient: Patient = {
    id: generateId(), tenantId, ...data,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    syncStatus: 'local',
  }
  await db.patients.add(patient)
  await enqueueSync('patient', patient.id, 'create', patient)
  return patient
}

export async function updatePatient(id: string, updates: Partial<Patient>): Promise<void> {
  await db.patients.update(id, { ...updates, updatedAt: new Date().toISOString(), syncStatus: 'pending' })
  await enqueueSync('patient', id, 'update', updates)
}