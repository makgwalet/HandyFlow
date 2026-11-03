export interface Patient {
  id: string;
  tenantId: string;
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
  dateOfBirth?: string;
  idNumber?: string;
  medicalAidNumber?: string;
  medicalAidScheme?: string;
  principalId?: string;
  isArchived: boolean;
  createdAt: string;
  updatedAt: string;
  syncStatus: 'synced' | 'local' | 'pending' | 'conflict';
}

export interface Practitioner {
  id: string;
  tenantId: string;
  firstName: string;
  lastName: string;
  email: string;
  specialty?: string;
  hpcsaNumber?: string;
  isActive: boolean;
}

export interface Appointment {
  id: string;
  tenantId: string;
  patientId: string;
  practitionerId: string;
  startTime: string;
  endTime: string;
  status: 'scheduled' | 'confirmed' | 'checked_in' | 'in_progress' | 'completed' | 'cancelled' | 'no_show';
  type: string;
  notes?: string;
  createdAt: string;
  syncStatus: 'synced' | 'local' | 'pending' | 'conflict';
}

export interface Consultation {
  id: string;
  tenantId: string;
  patientId: string;
  practitionerId: string;
  appointmentId?: string;
  chiefComplaint?: string;
  historyOfPresentIllness?: string;
  examinationFindings?: string;
  diagnosis?: string;
  diagnosisCodes?: string[];
  notes?: string;
  followUpDays?: number;
  createdAt: string;
  updatedAt: string;
  syncStatus: 'synced' | 'local' | 'pending' | 'conflict';
}

export interface Vital {
  id: string;
  consultationId: string;
  type: 'bp_systolic' | 'bp_diastolic' | 'heart_rate' | 'temperature' | 'respiratory_rate' | 'oxygen_saturation' | 'weight' | 'height' | 'blood_glucose';
  value: number;
  unit: string;
  recordedAt: string;
}

export interface Prescription {
  id: string;
  consultationId: string;
  medicationName: string;
  nappiCode?: string;
  dosage: string;
  frequency: string;
  duration: string;
  instructions?: string;
  createdAt: string;
}

export interface LabResult {
  id: string;
  tenantId: string;
  patientId?: string;
  patientNameRaw?: string;
  source: string;
  labReference?: string;
  testName: string;
  status: 'unmatched' | 'unreviewed' | 'reviewed' | 'filed';
  interpretation?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  fileName?: string;
  fileData?: Uint8Array;
  collectedAt?: string;
  createdAt: string;
  syncStatus: 'synced' | 'local' | 'pending' | 'conflict';
}

export interface Claim {
  id: string;
  tenantId: string;
  consultationId: string;
  patientId: string;
  status: 'draft' | 'submitted' | 'acknowledged' | 'paid' | 'partial' | 'rejected';
  schemeAmount?: number;
  patientPortion?: number;
  totalAmount: number;
  referenceNumber?: string;
  reason?: string;
  createdAt: string;
  updatedAt: string;
  syncStatus: 'synced' | 'local' | 'pending' | 'conflict';
}

export interface Payment {
  id: string;
  tenantId: string;
  patientId: string;
  amount: number;
  method: 'cash' | 'card' | 'eft' | 'medical_aid';
  reference?: string;
  notes?: string;
  createdAt: string;
  syncStatus: 'synced' | 'local' | 'pending';
}

export interface WaitlistEntry {
  id: string;
  tenantId: string;
  patientId: string;
  requestedDate: string;
  requestedTime?: string;
  practitionerId?: string;
  reason?: string;
  status: 'active' | 'contacted' | 'scheduled' | 'cancelled';
  createdAt: string;
}

export interface ConsentEvent {
  id: string;
  patientId: string;
  consentType: string;
  granted: boolean;
  recordedAt: string;
  recordedBy: string;
}

export interface SyncQueueItem {
  id: string;
  entityType: string;
  entityId: string;
  operation: 'create' | 'update' | 'delete';
  payload: string;
  createdAt: number;
  retryCount: number;
  lastError?: string;
  status: 'pending' | 'processing' | 'failed' | 'resolved';
}

export interface User {
  id: string;
  tenantId: string;
  email: string;
  firstName: string;
  lastName: string;
  permissions: string[];
  role?: string;
}

export type ViewMode = 'day' | 'week' | 'month';