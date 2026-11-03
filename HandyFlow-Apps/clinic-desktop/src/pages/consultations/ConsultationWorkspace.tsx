import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { usePatients } from '@/hooks/use-patients'
import { Card, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { useUIStore } from '@/stores/ui-store'
import { generateId } from '@/lib/utils'
import { Save, FileText, Pill, FlaskConical, Printer, Plus } from 'lucide-react'
import { db } from '@/db/clinic-db'

export function ConsultationWorkspace() {
  const [searchParams] = useSearchParams()
  const preselectedPatient = searchParams.get('patient')
  const patients = usePatients()
  const { showToast } = useUIStore()

  const [selectedPatient, setSelectedPatient] = useState(preselectedPatient || '')
  const [activeTab, setActiveTab] = useState<'notes' | 'prescriptions' | 'lab' | 'documents'>('notes')
  const [consultation, setConsultation] = useState({ chiefComplaint: '', historyOfPresentIllness: '', examinationFindings: '', diagnosis: '', notes: '', followUpDays: '' })
  const [vitals, setVitals] = useState({ bpSystolic: '', bpDiastolic: '', heartRate: '', temperature: '', oxygenSaturation: '' })
  const [prescriptions, setPrescriptions] = useState<Array<{ medicationName: string; dosage: string; frequency: string; duration: string }>>([])
  const [newScript, setNewScript] = useState({ medicationName: '', dosage: '', frequency: '', duration: '' })

  const handleSave = async () => {
    try {
      const tenantId = await db.getTenantId()
      const id = generateId()
      await db.consultations.add({
        id, tenantId, patientId: selectedPatient, practitionerId: 'prac-001',
        ...consultation, followUpDays: consultation.followUpDays ? parseInt(consultation.followUpDays) : undefined,
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), syncStatus: 'local',
      })
      const vitalEntries = Object.entries(vitals).filter(([, v]) => v !== '').map(([type, value]) => ({
        id: generateId(), consultationId: id,
        type: type.replace('bpSystolic', 'bp_systolic').replace('bpDiastolic', 'bp_diastolic').replace('heartRate', 'heart_rate').replace('oxygenSaturation', 'oxygen_saturation').replace('temperature', 'temperature') as any,
        value: parseFloat(value),
        unit: type === 'bpSystolic' || type === 'bpDiastolic' ? 'mmHg' : type === 'heartRate' ? 'bpm' : type === 'temperature' ? '°C' : '%',
        recordedAt: new Date().toISOString(),
      }))
      if (vitalEntries.length > 0) await db.vitals.bulkAdd(vitalEntries)
      if (prescriptions.length > 0) await db.prescriptions.bulkAdd(prescriptions.map(p => ({ id: generateId(), consultationId: id, ...p, createdAt: new Date().toISOString() })))
      showToast('Consultation saved locally', 'success')
    } catch { showToast('Failed to save consultation', 'error') }
  }

  const patient = patients?.find(p => p.id === selectedPatient)

  if (!selectedPatient) {
    return (
      <Card>
        <CardHeader title="New Consultation" subtitle="Select a patient to begin" />
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {patients?.map((p) => (
            <button key={p.id} onClick={() => setSelectedPatient(p.id)} className="flex items-center gap-3 rounded-lg border border-medical-border p-4 text-left transition-colors hover:border-primary-300 hover:bg-primary-50/30">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary-100 text-sm font-semibold text-primary-700">{p.firstName[0]}{p.lastName[0]}</div>
              <div><p className="font-medium text-medical-text">{p.firstName} {p.lastName}</p><p className="text-xs text-medical-muted">{p.medicalAidScheme || 'Private'}</p></div>
            </button>
          ))}
        </div>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between rounded-xl border border-medical-border bg-white p-4 shadow-sm">
        <div className="flex items-center gap-4">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary-100 text-sm font-bold text-primary-700">{patient?.firstName[0]}{patient?.lastName[0]}</div>
          <div>
            <h2 className="text-lg font-semibold text-medical-text">{patient?.firstName} {patient?.lastName}</h2>
            <div className="flex items-center gap-3 text-sm text-medical-muted"><span>{patient?.medicalAidScheme || 'Private'}</span><span>&bull;</span><span>{patient?.phone}</span></div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={() => setSelectedPatient('')}>Change Patient</Button>
          <Button size="sm" onClick={handleSave}><Save className="mr-2 h-4 w-4" />Save Consultation</Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        <Input label="BP Systolic" placeholder="120" value={vitals.bpSystolic} onChange={(e) => setVitals({ ...vitals, bpSystolic: e.target.value })} />
        <Input label="BP Diastolic" placeholder="80" value={vitals.bpDiastolic} onChange={(e) => setVitals({ ...vitals, bpDiastolic: e.target.value })} />
        <Input label="Heart Rate" placeholder="72" value={vitals.heartRate} onChange={(e) => setVitals({ ...vitals, heartRate: e.target.value })} />
        <Input label="Temp °C" placeholder="36.6" value={vitals.temperature} onChange={(e) => setVitals({ ...vitals, temperature: e.target.value })} />
        <Input label="SpO2 %" placeholder="98" value={vitals.oxygenSaturation} onChange={(e) => setVitals({ ...vitals, oxygenSaturation: e.target.value })} />
        <Input label="Follow-up (days)" placeholder="14" value={consultation.followUpDays} onChange={(e) => setConsultation({ ...consultation, followUpDays: e.target.value })} />
      </div>

      <div className="flex gap-1 rounded-lg border border-medical-border bg-slate-50 p-1">
        {[{ id: 'notes', label: 'Clinical Notes', icon: FileText }, { id: 'prescriptions', label: 'Prescriptions', icon: Pill }, { id: 'lab', label: 'Lab Requests', icon: FlaskConical }, { id: 'documents', label: 'Documents', icon: Printer }].map((tab) => (
          <button key={tab.id} onClick={() => setActiveTab(tab.id as typeof activeTab)} className={`flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium transition-colors ${activeTab === tab.id ? 'bg-white text-primary-700 shadow-sm' : 'text-medical-muted hover:text-medical-text'}`}>
            <tab.icon className="h-4 w-4" />{tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'notes' && (
        <div className="space-y-4">
          <Input label="Chief Complaint" placeholder="Patient's primary complaint..." value={consultation.chiefComplaint} onChange={(e) => setConsultation({ ...consultation, chiefComplaint: e.target.value })} />
          <div><label className="mb-1.5 block text-sm font-medium text-medical-text">History of Present Illness</label><textarea className="block w-full rounded-lg border border-medical-border bg-white px-3 py-2 text-sm placeholder:text-medical-muted focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500" rows={4} placeholder="Detailed history..." value={consultation.historyOfPresentIllness} onChange={(e) => setConsultation({ ...consultation, historyOfPresentIllness: e.target.value })} /></div>
          <div><label className="mb-1.5 block text-sm font-medium text-medical-text">Examination Findings</label><textarea className="block w-full rounded-lg border border-medical-border bg-white px-3 py-2 text-sm placeholder:text-medical-muted focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500" rows={4} placeholder="Physical examination findings..." value={consultation.examinationFindings} onChange={(e) => setConsultation({ ...consultation, examinationFindings: e.target.value })} /></div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Diagnosis" placeholder="Primary diagnosis..." value={consultation.diagnosis} onChange={(e) => setConsultation({ ...consultation, diagnosis: e.target.value })} />
            <Input label="ICD-10 Codes" placeholder="e.g. I10, E11.9" helper="Separate multiple codes with commas" />
          </div>
          <div><label className="mb-1.5 block text-sm font-medium text-medical-text">Additional Notes</label><textarea className="block w-full rounded-lg border border-medical-border bg-white px-3 py-2 text-sm placeholder:text-medical-muted focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500" rows={3} placeholder="Any additional notes..." value={consultation.notes} onChange={(e) => setConsultation({ ...consultation, notes: e.target.value })} /></div>
        </div>
      )}

      {activeTab === 'prescriptions' && (
        <div className="space-y-4">
          <Card>
            <CardHeader title="Add Prescription" />
            <div className="grid grid-cols-2 gap-4">
              <Input label="Medication" placeholder="Search NAPPI..." value={newScript.medicationName} onChange={(e) => setNewScript({ ...newScript, medicationName: e.target.value })} />
              <Input label="Dosage" placeholder="e.g. 5mg" value={newScript.dosage} onChange={(e) => setNewScript({ ...newScript, dosage: e.target.value })} />
              <Input label="Frequency" placeholder="e.g. Once daily" value={newScript.frequency} onChange={(e) => setNewScript({ ...newScript, frequency: e.target.value })} />
              <Input label="Duration" placeholder="e.g. 30 days" value={newScript.duration} onChange={(e) => setNewScript({ ...newScript, duration: e.target.value })} />
            </div>
            <Button className="mt-4" variant="secondary" onClick={() => { if (newScript.medicationName) { setPrescriptions([...prescriptions, newScript]); setNewScript({ medicationName: '', dosage: '', frequency: '', duration: '' }) } }}><Plus className="mr-2 h-4 w-4" />Add to Script</Button>
          </Card>
          {prescriptions.length > 0 && (
            <Card>
              <CardHeader title="Current Prescriptions" />
              <div className="space-y-2">
                {prescriptions.map((script, i) => (
                  <div key={i} className="flex items-center justify-between rounded-lg border border-medical-border p-3">
                    <div><p className="font-medium text-medical-text">{script.medicationName} {script.dosage}</p><p className="text-sm text-medical-muted">{script.frequency} &bull; {script.duration}</p></div>
                    <Button variant="ghost" size="sm" onClick={() => setPrescriptions(prescriptions.filter((_, idx) => idx !== i))}>Remove</Button>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>
      )}

      {activeTab === 'lab' && (
        <Card>
          <CardHeader title="Lab Requests" subtitle="Select tests to order" />
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {['Full Blood Count', 'Lipid Panel', 'Liver Function', 'Renal Function', 'HIV Viral Load', 'CD4 Count', 'HbA1c', 'Thyroid Panel', 'Urinalysis', 'Stool Culture'].map((test) => (
              <label key={test} className="flex items-center gap-2 rounded-lg border border-medical-border p-3 hover:bg-slate-50 cursor-pointer">
                <input type="checkbox" className="h-4 w-4 rounded border-medical-border text-primary-600 focus:ring-primary-500" /><span className="text-sm">{test}</span>
              </label>
            ))}
          </div>
        </Card>
      )}

      {activeTab === 'documents' && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Card className="cursor-pointer hover:border-primary-300 transition-colors"><div className="flex flex-col items-center gap-3 py-8"><FileText className="h-10 w-10 text-primary-600" /><p className="font-medium text-medical-text">Medical Certificate</p><p className="text-xs text-medical-muted">Generate sick note PDF</p></div></Card>
          <Card className="cursor-pointer hover:border-primary-300 transition-colors"><div className="flex flex-col items-center gap-3 py-8"><Printer className="h-10 w-10 text-primary-600" /><p className="font-medium text-medical-text">Print Prescription</p><p className="text-xs text-medical-muted">Generate script PDF</p></div></Card>
        </div>
      )}
    </div>
  )
}