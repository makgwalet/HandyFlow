import { db } from './clinic-db'
import { generateId } from '@/lib/utils'

export async function seedDatabase(tenantId: string): Promise<void> {
  const count = await db.patients.count()
  if (count > 0) return

  const practitionerId = generateId()
  await db.practitioners.add({
    id: practitionerId,
    tenantId,
    firstName: 'Sarah',
    lastName: 'Smith',
    email: 'dr.smith@clinic.co.za',
    specialty: 'General Practitioner',
    hpcsaNumber: 'GP123456',
    isActive: true,
  })

  const patientIds = [generateId(), generateId(), generateId(), generateId(), generateId()]
  const now = new Date()

  await db.patients.bulkAdd([
    {
      id: patientIds[0], tenantId, firstName: 'John', lastName: 'Doe',
      email: 'john.doe@email.com', phone: '0825550199',
      dateOfBirth: '1985-03-15', medicalAidNumber: 'MA123456',
      medicalAidScheme: 'Discovery Health',
      isArchived: false, createdAt: now.toISOString(), updatedAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: patientIds[1], tenantId, firstName: 'Jane', lastName: 'Mokoena',
      email: 'jane.m@email.com', phone: '0834440299',
      dateOfBirth: '1992-07-22', medicalAidNumber: 'MA789012',
      medicalAidScheme: 'Bonitas',
      isArchived: false, createdAt: now.toISOString(), updatedAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: patientIds[2], tenantId, firstName: 'Pieter', lastName: 'van der Merwe',
      email: 'pieter.v@email.com', phone: '0713330198',
      dateOfBirth: '1978-11-05',
      isArchived: false, createdAt: now.toISOString(), updatedAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: patientIds[3], tenantId, firstName: 'Sarah', lastName: 'Johnson',
      email: 'sarah.j@email.com', phone: '0821112233',
      dateOfBirth: '2001-01-12',
      isArchived: false, createdAt: now.toISOString(), updatedAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: patientIds[4], tenantId, firstName: 'Thabo', lastName: 'Nkosi',
      email: 'thabo.n@email.com', phone: '0765558877',
      dateOfBirth: '1965-09-30', medicalAidNumber: 'MA555999',
      medicalAidScheme: 'Fedhealth',
      isArchived: false, createdAt: now.toISOString(), updatedAt: now.toISOString(), syncStatus: 'synced',
    },
  ])

  const today = new Date()
  today.setHours(9, 0, 0, 0)

  await db.appointments.bulkAdd([
    {
      id: generateId(), tenantId, patientId: patientIds[0], practitionerId,
      startTime: new Date(today.getTime() + 0).toISOString(),
      endTime: new Date(today.getTime() + 30 * 60000).toISOString(),
      status: 'checked_in', type: 'Follow-up', notes: 'Hypertension review',
      createdAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: generateId(), tenantId, patientId: patientIds[1], practitionerId,
      startTime: new Date(today.getTime() + 30 * 60000).toISOString(),
      endTime: new Date(today.getTime() + 60 * 60000).toISOString(),
      status: 'confirmed', type: 'Consultation', notes: 'Flu symptoms',
      createdAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: generateId(), tenantId, patientId: patientIds[2], practitionerId,
      startTime: new Date(today.getTime() + 60 * 60000).toISOString(),
      endTime: new Date(today.getTime() + 90 * 60000).toISOString(),
      status: 'scheduled', type: 'Annual checkup',
      createdAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: generateId(), tenantId, patientId: patientIds[3], practitionerId,
      startTime: new Date(today.getTime() + 120 * 60000).toISOString(),
      endTime: new Date(today.getTime() + 150 * 60000).toISOString(),
      status: 'scheduled', type: 'Script renewal',
      createdAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: generateId(), tenantId, patientId: patientIds[4], practitionerId,
      startTime: new Date(today.getTime() + 180 * 60000).toISOString(),
      endTime: new Date(today.getTime() + 210 * 60000).toISOString(),
      status: 'scheduled', type: 'Lab review',
      createdAt: now.toISOString(), syncStatus: 'synced',
    },
  ])

  await db.labResults.bulkAdd([
    {
      id: generateId(), tenantId, patientId: patientIds[0], patientNameRaw: 'J. Doe',
      source: 'Pathcare', labReference: 'PC-2026-78432',
      testName: 'Full Blood Count', status: 'unreviewed',
      fileName: 'fbc_jdoe.pdf',
      collectedAt: new Date(now.getTime() - 86400000).toISOString(),
      createdAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: generateId(), tenantId, patientNameRaw: 'Unknown',
      source: 'Lancet', labReference: 'LC-2026-11209',
      testName: 'HIV Viral Load', status: 'unmatched',
      fileName: 'hiv_vl.pdf',
      collectedAt: new Date(now.getTime() - 172800000).toISOString(),
      createdAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: generateId(), tenantId, patientId: patientIds[1], patientNameRaw: 'J. Mokoena',
      source: 'Pathcare', labReference: 'PC-2026-78433',
      testName: 'Lipid Panel', status: 'reviewed',
      interpretation: 'Total cholesterol slightly elevated. Recommend dietary review.',
      reviewedBy: practitionerId, reviewedAt: now.toISOString(),
      fileName: 'lipid_jmokoena.pdf',
      collectedAt: new Date(now.getTime() - 259200000).toISOString(),
      createdAt: now.toISOString(), syncStatus: 'synced',
    },
  ])

  await db.claims.bulkAdd([
    {
      id: generateId(), tenantId, consultationId: generateId(), patientId: patientIds[0],
      status: 'submitted', totalAmount: 850.00, patientPortion: 150.00,
      referenceNumber: 'HF-2026-001',
      createdAt: new Date(now.getTime() - 604800000).toISOString(),
      updatedAt: now.toISOString(), syncStatus: 'synced',
    },
    {
      id: generateId(), tenantId, consultationId: generateId(), patientId: patientIds[1],
      status: 'draft', totalAmount: 620.00, patientPortion: 0,
      createdAt: new Date(now.getTime() - 86400000).toISOString(),
      updatedAt: now.toISOString(), syncStatus: 'local',
    },
  ])

  await db.payments.bulkAdd([
    {
      id: generateId(), tenantId, patientId: patientIds[0],
      amount: 150.00, method: 'card', reference: 'TXN-88291',
      notes: 'Co-payment for consultation',
      createdAt: new Date(now.getTime() - 604800000).toISOString(), syncStatus: 'synced',
    },
    {
      id: generateId(), tenantId, patientId: patientIds[2],
      amount: 850.00, method: 'cash', reference: 'CASH-001',
      createdAt: new Date(now.getTime() - 172800000).toISOString(), syncStatus: 'synced',
    },
  ])

  await db.waitlist.add({
    id: generateId(), tenantId, patientId: patientIds[4],
    requestedDate: new Date(today.getTime() + 86400000).toISOString().split('T')[0],
    requestedTime: '10:00', practitionerId,
    reason: 'Back pain - urgent', status: 'active',
    createdAt: now.toISOString(),
  })

  await db.setTenantId(tenantId)
}