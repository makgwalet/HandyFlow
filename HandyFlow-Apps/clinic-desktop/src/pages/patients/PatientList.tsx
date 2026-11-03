import { useState } from 'react'
import { usePatients, createPatient } from '@/hooks/use-patients'
import { Card, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { Table, TableHead, TableBody, TableRow, TableCell, TableHeader } from '@/components/ui/Table'
import { useUIStore } from '@/stores/ui-store'
import { formatDate, getInitials, calculateAge } from '@/lib/utils'
import { Search, Plus, User, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export function PatientList() {
  const [search, setSearch] = useState('')
  const [showModal, setShowModal] = useState(false)
  const patients = usePatients(search)
  const { showToast } = useUIStore()

  const [formData, setFormData] = useState({
    firstName: '', lastName: '', email: '', phone: '',
    dateOfBirth: '', medicalAidNumber: '', medicalAidScheme: '',
  })

  const handleCreate = async () => {
    try {
      await createPatient(formData)
      showToast('Patient registered successfully', 'success')
      setShowModal(false)
      setFormData({ firstName: '', lastName: '', email: '', phone: '', dateOfBirth: '', medicalAidNumber: '', medicalAidScheme: '' })
    } catch { showToast('Failed to register patient', 'error') }
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader title="Patients" subtitle="Manage your patient records" action={<Button onClick={() => setShowModal(true)}><Plus className="mr-2 h-4 w-4" />New Patient</Button>} />
        <div className="mb-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-medical-muted" />
            <Input placeholder="Search by name, phone, or medical aid number..." value={search} onChange={(e) => setSearch(e.target.value)} className="pl-10" />
          </div>
        </div>
        <Table>
          <TableHead>
            <tr><TableHeader>Patient</TableHeader><TableHeader>Contact</TableHeader><TableHeader>Age</TableHeader><TableHeader>Medical Aid</TableHeader><TableHeader>Status</TableHeader><TableHeader></TableHeader></tr>
          </TableHead>
          <TableBody>
            {patients?.map((patient) => (
              <TableRow key={patient.id}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary-100 text-xs font-semibold text-primary-700">{getInitials(patient.firstName, patient.lastName)}</div>
                    <div>
                      <p className="font-medium text-medical-text">{patient.firstName} {patient.lastName}</p>
                      <p className="text-xs text-medical-muted">{patient.idNumber || 'No ID'}</p>
                    </div>
                  </div>
                </TableCell>
                <TableCell><p className="text-sm">{patient.phone || '—'}</p><p className="text-xs text-medical-muted">{patient.email || '—'}</p></TableCell>
                <TableCell>{patient.dateOfBirth ? `${calculateAge(patient.dateOfBirth)} yrs` : '—'}</TableCell>
                <TableCell><p className="text-sm">{patient.medicalAidScheme || '—'}</p><p className="text-xs text-medical-muted">{patient.medicalAidNumber || '—'}</p></TableCell>
                <TableCell><Badge variant={patient.syncStatus === 'synced' ? 'success' : 'warning'}>{patient.syncStatus === 'synced' ? 'Synced' : 'Local'}</Badge></TableCell>
                <TableCell><Link to={`/patients/${patient.id}`}><Button variant="ghost" size="sm"><ChevronRight className="h-4 w-4" /></Button></Link></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {(!patients || patients.length === 0) && (
          <div className="flex flex-col items-center justify-center py-12"><User className="h-12 w-12 text-medical-muted/50" /><p className="mt-4 text-sm text-medical-muted">No patients found</p></div>
        )}
      </Card>

      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title="Register New Patient" size="lg" footer={<><Button variant="secondary" onClick={() => setShowModal(false)}>Cancel</Button><Button onClick={handleCreate}>Register Patient</Button></>}>
        <div className="grid grid-cols-2 gap-4">
          <Input label="First Name" value={formData.firstName} onChange={(e) => setFormData({ ...formData, firstName: e.target.value })} required />
          <Input label="Last Name" value={formData.lastName} onChange={(e) => setFormData({ ...formData, lastName: e.target.value })} required />
          <Input label="Email" type="email" value={formData.email} onChange={(e) => setFormData({ ...formData, email: e.target.value })} />
          <Input label="Phone" value={formData.phone} onChange={(e) => setFormData({ ...formData, phone: e.target.value })} />
          <Input label="Date of Birth" type="date" value={formData.dateOfBirth} onChange={(e) => setFormData({ ...formData, dateOfBirth: e.target.value })} />
          <Input label="Medical Aid Scheme" value={formData.medicalAidScheme} onChange={(e) => setFormData({ ...formData, medicalAidScheme: e.target.value })} />
          <Input label="Medical Aid Number" value={formData.medicalAidNumber} onChange={(e) => setFormData({ ...formData, medicalAidNumber: e.target.value })} />
        </div>
      </Modal>
    </div>
  )
}