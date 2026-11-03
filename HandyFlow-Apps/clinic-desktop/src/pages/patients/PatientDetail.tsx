import { useParams, Link } from 'react-router-dom'
import { usePatient } from '@/hooks/use-patients'
import { usePatientAppointments } from '@/hooks/use-appointments'
import { usePatientLabResults } from '@/hooks/use-lab'
import { Card, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { formatDate, getInitials, calculateAge } from '@/lib/utils'
import { ArrowLeft, Calendar, FlaskConical, FileText, Phone, Mail, CreditCard, Activity } from 'lucide-react'

export function PatientDetail() {
  const { id } = useParams<{ id: string }>()
  const patient = usePatient(id)
  const appointments = usePatientAppointments(id)
  const labResults = usePatientLabResults(id)

  if (!patient) return <div className="flex h-96 items-center justify-center"><p className="text-medical-muted">Loading patient...</p></div>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <Link to="/patients"><Button variant="ghost" size="sm"><ArrowLeft className="mr-2 h-4 w-4" />Back to patients</Button></Link>
        <div className="flex gap-2">
          <Link to={`/schedule?patient=${patient.id}`}><Button variant="secondary" size="sm"><Calendar className="mr-2 h-4 w-4" />Book Appointment</Button></Link>
          <Link to={`/consultations?patient=${patient.id}`}><Button size="sm"><Activity className="mr-2 h-4 w-4" />New Consultation</Button></Link>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <div className="flex flex-col items-center border-b border-medical-border pb-6 pt-2">
            <div className="flex h-20 w-20 items-center justify-center rounded-full bg-primary-100 text-xl font-bold text-primary-700">{getInitials(patient.firstName, patient.lastName)}</div>
            <h2 className="mt-4 text-xl font-semibold text-medical-text">{patient.firstName} {patient.lastName}</h2>
            <p className="text-sm text-medical-muted">{patient.dateOfBirth ? `${calculateAge(patient.dateOfBirth)} years old` : 'Age unknown'}</p>
            <Badge variant={patient.isArchived ? 'danger' : 'success'} className="mt-2">{patient.isArchived ? 'Archived' : 'Active'}</Badge>
          </div>
          <div className="space-y-4 pt-6">
            <div className="flex items-center gap-3"><Phone className="h-4 w-4 text-medical-muted" /><span className="text-sm">{patient.phone || '—'}</span></div>
            <div className="flex items-center gap-3"><Mail className="h-4 w-4 text-medical-muted" /><span className="text-sm">{patient.email || '—'}</span></div>
            <div className="flex items-center gap-3"><CreditCard className="h-4 w-4 text-medical-muted" /><span className="text-sm">{patient.medicalAidScheme || 'Private'} &bull; {patient.medicalAidNumber || '—'}</span></div>
            <div className="flex items-center gap-3"><FileText className="h-4 w-4 text-medical-muted" /><span className="text-sm">{patient.idNumber || '—'}</span></div>
          </div>
        </Card>

        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader title="Appointments" action={<Badge variant="outline">{appointments?.length || 0} total</Badge>} />
            <div className="space-y-2">
              {appointments?.map((apt) => (
                <div key={apt.id} className="flex items-center justify-between rounded-lg border border-medical-border p-3">
                  <div className="flex items-center gap-3"><Calendar className="h-4 w-4 text-medical-muted" /><div><p className="text-sm font-medium">{apt.type}</p><p className="text-xs text-medical-muted">{formatDate(apt.startTime)}</p></div></div>
                  <Badge variant={apt.status === 'completed' ? 'success' : apt.status === 'cancelled' ? 'danger' : 'default'}>{apt.status.replace('_', ' ')}</Badge>
                </div>
              ))}
              {(!appointments || appointments.length === 0) && <p className="py-4 text-center text-sm text-medical-muted">No appointment history</p>}
            </div>
          </Card>

          <Card>
            <CardHeader title="Lab Results" action={<Badge variant="outline">{labResults?.length || 0} results</Badge>} />
            <div className="space-y-2">
              {labResults?.map((lab) => (
                <div key={lab.id} className="flex items-center justify-between rounded-lg border border-medical-border p-3">
                  <div className="flex items-center gap-3"><FlaskConical className="h-4 w-4 text-medical-muted" /><div><p className="text-sm font-medium">{lab.testName}</p><p className="text-xs text-medical-muted">{lab.source} &bull; {formatDate(lab.createdAt)}</p></div></div>
                  <Badge variant={lab.status === 'reviewed' ? 'success' : lab.status === 'unmatched' ? 'danger' : 'warning'}>{lab.status}</Badge>
                </div>
              ))}
              {(!labResults || labResults.length === 0) && <p className="py-4 text-center text-sm text-medical-muted">No lab results</p>}
            </div>
          </Card>
        </div>
      </div>
    </div>
  )
}