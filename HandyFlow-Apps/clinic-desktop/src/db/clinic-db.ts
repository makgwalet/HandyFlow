import Dexie, { type Table } from 'dexie'
import type {
  Patient, Practitioner, Appointment, Consultation, Vital,
  Prescription, LabResult, Claim, Payment, WaitlistEntry,
  ConsentEvent, SyncQueueItem
} from '@/types/clinic'

export class ClinicDatabase extends Dexie {
  patients!: Table<Patient, string>
  practitioners!: Table<Practitioner, string>
  appointments!: Table<Appointment, string>
  consultations!: Table<Consultation, string>
  vitals!: Table<Vital, string>
  prescriptions!: Table<Prescription, string>
  labResults!: Table<LabResult, string>
  claims!: Table<Claim, string>
  payments!: Table<Payment, string>
  waitlist!: Table<WaitlistEntry, string>
  consentEvents!: Table<ConsentEvent, string>
  syncQueue!: Table<SyncQueueItem, string>
  metadata!: Table<{ key: string; value: string }, string>

  constructor() {
    super('ClinicDatabase')
    this.version(1).stores({
      patients: 'id, tenantId, lastName, firstName, principalId, isArchived, [tenantId+isArchived]',
      practitioners: 'id, tenantId, email, isActive, [tenantId+isActive]',
      appointments: 'id, tenantId, patientId, practitionerId, startTime, status, [tenantId+startTime]',
      consultations: 'id, tenantId, patientId, practitionerId, appointmentId, createdAt, [tenantId+patientId]',
      vitals: 'id, consultationId, type, recordedAt',
      prescriptions: 'id, consultationId, medicationName, createdAt',
      labResults: 'id, tenantId, patientId, status, source, createdAt, [tenantId+status]',
      claims: 'id, tenantId, patientId, consultationId, status, createdAt, [tenantId+status]',
      payments: 'id, tenantId, patientId, createdAt, [tenantId+patientId]',
      waitlist: 'id, tenantId, status, requestedDate, [tenantId+status]',
      consentEvents: 'id, patientId, consentType, recordedAt, [patientId+consentType]',
      syncQueue: 'id, entityType, status, createdAt, [entityType+status]',
      metadata: 'key',
    })
  }

  async getLastSyncTimestamp(): Promise<number> {
    const row = await this.metadata.get('lastSync')
    return row ? parseInt(row.value, 10) : 0
  }

  async setLastSyncTimestamp(ts: number): Promise<void> {
    await this.metadata.put({ key: 'lastSync', value: ts.toString() })
  }

  async getTenantId(): Promise<string> {
    const row = await this.metadata.get('tenantId')
    return row?.value || 'local'
  }

  async setTenantId(tenantId: string): Promise<void> {
    await this.metadata.put({ key: 'tenantId', value: tenantId })
  }
}

export const db = new ClinicDatabase()