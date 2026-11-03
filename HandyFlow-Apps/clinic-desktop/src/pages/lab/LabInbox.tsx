import { useState } from 'react'
import { useLabResults, matchLabResult, reviewLabResult, aiInterpretLabResult } from '@/hooks/use-lab'
import { usePatients } from '@/hooks/use-patients'
import { Card, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { Table, TableHead, TableBody, TableRow, TableCell, TableHeader } from '@/components/ui/Table'
import { useUIStore } from '@/stores/ui-store'
import { formatDate } from '@/lib/utils'
import { FlaskConical, AlertCircle, Brain, CheckCircle, UserCheck, FileText } from 'lucide-react'

export function LabInbox() {
  const [filter, setFilter] = useState<'all' | 'unmatched' | 'unreviewed' | 'reviewed'>('all')
  const labResults = useLabResults(filter === 'all' ? undefined : filter)
  const patients = usePatients()
  const { showToast } = useUIStore()

  const [selectedLab, setSelectedLab] = useState<string | null>(null)
  const [interpretation, setInterpretation] = useState('')
  const [showReviewModal, setShowReviewModal] = useState(false)
  const [showMatchModal, setShowMatchModal] = useState(false)
  const [selectedPatient, setSelectedPatient] = useState('')

  const handleMatch = async () => {
    if (!selectedLab || !selectedPatient) return
    try {
      await matchLabResult(selectedLab, selectedPatient)
      showToast('Patient matched successfully', 'success')
      setShowMatchModal(false)
      setSelectedLab(null)
    } catch {
      showToast('Failed to match patient', 'error')
    }
  }

  const handleAI = async (id: string) => {
    try {
      const result = await aiInterpretLabResult(id)
      showToast('AI interpretation generated', 'success')
      setInterpretation(result)
      setSelectedLab(id)
      setShowReviewModal(true)
    } catch {
      showToast('AI interpretation failed', 'error')
    }
  }

  const handleReview = async () => {
    if (!selectedLab) return
    try {
      await reviewLabResult(selectedLab, interpretation, 'prac-001')
      showToast('Lab result reviewed and signed off', 'success')
      setShowReviewModal(false)
      setSelectedLab(null)
      setInterpretation('')
    } catch {
      showToast('Failed to review', 'error')
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Lab Inbox"
          subtitle="Manage incoming lab results"
          action={
            <div className="flex gap-2">
              {(['all', 'unmatched', 'unreviewed', 'reviewed'] as const).map((f) => (
                <Button
                  key={f}
                  variant={filter === f ? 'primary' : 'secondary'}
                  size="sm"
                  onClick={() => setFilter(f)}
                >
                  {f.charAt(0).toUpperCase() + f.slice(1)}
                </Button>
              ))}
            </div>
          }
        />
        <Table>
          <TableHead>
            <tr>
              <TableHeader>Test</TableHeader>
              <TableHeader>Source</TableHeader>
              <TableHeader>Patient</TableHeader>
              <TableHeader>Date</TableHeader>
              <TableHeader>Status</TableHeader>
              <TableHeader>Actions</TableHeader>
            </tr>
          </TableHead>
          <TableBody>
            {labResults?.map((lab) => (
              <TableRow key={lab.id}>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <FlaskConical className="h-4 w-4 text-medical-muted" />
                    <span className="font-medium">{lab.testName}</span>
                  </div>
                  <p className="text-xs text-medical-muted">{lab.labReference}</p>
                </TableCell>
                <TableCell>{lab.source}</TableCell>
                <TableCell>
                  {lab.patientId ? (
                    <span className="text-sm">{patients?.find(p => p.id === lab.patientId)?.firstName || 'Unknown'}</span>
                  ) : (
                    <span className="text-sm text-rose-600 font-medium">{lab.patientNameRaw}</span>
                  )}
                </TableCell>
                <TableCell>{lab.collectedAt ? formatDate(lab.collectedAt) : '—'}</TableCell>
                <TableCell>
                  <Badge
                    variant={
                      lab.status === 'reviewed' ? 'success' :
                      lab.status === 'unmatched' ? 'danger' : 'warning'
                    }
                  >
                    {lab.status}
                  </Badge>
                </TableCell>
                <TableCell>
                  <div className="flex gap-1">
                    {lab.status === 'unmatched' && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => { setSelectedLab(lab.id); setShowMatchModal(true); }}
                      >
                        <UserCheck className="h-4 w-4" />
                      </Button>
                    )}
                    {lab.status === 'unreviewed' && (
                      <>
                        <Button variant="secondary" size="sm" onClick={() => handleAI(lab.id)}>
                          <Brain className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => { setSelectedLab(lab.id); setShowReviewModal(true); }}
                        >
                          <CheckCircle className="h-4 w-4" />
                        </Button>
                      </>
                    )}
                    <Button variant="ghost" size="sm">
                      <FileText className="h-4 w-4" />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      <Modal
        isOpen={showMatchModal}
        onClose={() => setShowMatchModal(false)}
        title="Match Lab Result to Patient"
        footer={
          <>
            <Button variant="secondary" onClick={() => setShowMatchModal(false)}>Cancel</Button>
            <Button onClick={handleMatch}>Match Patient</Button>
          </>
        }
      >
        <div className="space-y-4">
          <p className="text-sm text-medical-muted">Select the correct patient for this lab result.</p>
          <div className="max-h-64 overflow-y-auto space-y-1">
            {patients?.map((p) => (
              <button
                key={p.id}
                onClick={() => setSelectedPatient(p.id)}
                className={`flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors ${
                  selectedPatient === p.id ? 'border-primary-500 bg-primary-50' : 'border-medical-border hover:bg-slate-50'
                }`}
              >
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-xs font-semibold text-primary-700">
                  {p.firstName[0]}{p.lastName[0]}
                </div>
                <span className="text-sm font-medium">{p.firstName} {p.lastName}</span>
              </button>
            ))}
          </div>
        </div>
      </Modal>

      <Modal
        isOpen={showReviewModal}
        onClose={() => setShowReviewModal(false)}
        title="Review Lab Result"
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setShowReviewModal(false)}>Cancel</Button>
            <Button onClick={handleReview}>Sign Off</Button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-medical-text">Interpretation</label>
            <textarea
              className="block w-full rounded-lg border border-medical-border bg-white px-3 py-2 text-sm placeholder:text-medical-muted focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              rows={6}
              value={interpretation}
              onChange={(e) => setInterpretation(e.target.value)}
              placeholder="Enter your interpretation or edit the AI-generated text..."
            />
          </div>
        </div>
      </Modal>
    </div>
  )
}