import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '@/db/clinic-db'
import { enqueueSync } from '@/db/sync-queue'
import type { Appointment } from '@/types/clinic'
import { generateId } from '@/lib/utils'

export function useAppointments(date?: string, status?: string) {
  return useLiveQuery(async () => {
    const tenantId = await db.getTenantId()
    let query = db.appointments.where('[tenantId+startTime]').between(
      [tenantId, date ? `${date}T00:00:00.000Z` : '1970-01-01'],
      [tenantId, date ? `${date}T23:59:59.999Z` : '9999-12-31']
    )
    const results = await query.toArray()
    if (status) return results.filter(a => a.status === status)
    return results.sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())
  }, [date, status])
}

export function usePatientAppointments(patientId: string | undefined) {
  return useLiveQuery(async () => {
    if (!patientId) return []
    return db.appointments.where({ patientId }).sortBy('startTime')
  }, [patientId])
}

export async function createAppointment(data: Omit<Appointment, 'id' | 'tenantId' | 'createdAt' | 'syncStatus'>): Promise<Appointment> {
  const tenantId = await db.getTenantId()
  const appointment: Appointment = {
    id: generateId(), tenantId, ...data,
    createdAt: new Date().toISOString(),
    syncStatus: 'local',
  }
  await db.appointments.add(appointment)
  await enqueueSync('appointment', appointment.id, 'create', appointment)
  return appointment
}

export async function updateAppointmentStatus(id: string, status: Appointment['status']): Promise<void> {
  await db.appointments.update(id, { status, syncStatus: 'pending' })
  await enqueueSync('appointment', id, 'update', { status })
}