// src/pages/recruiter/RecruiterPage.tsx
import { useState } from 'react'
import { useQuery, useQueries, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, Search, Download, ChevronRight, Star,
  Briefcase, Users, Calendar, UserCheck, AlertTriangle,
  CheckCircle, Clock, MapPin, Building2, ExternalLink,
  FileText, Edit3, Trash2, Send, Video, Phone,
  Monitor, Mic, BarChart2, Link2, UserPlus, Filter,
  ChevronDown, ChevronUp,
} from 'lucide-react'

// ── Types ──────────────────────────────────────────────────────────────────
interface Job {
  id: string; title: string; department: string | null; location: string | null
  jobType: string; experienceLevel: string; description: string
  requirements: string | null; benefits: string | null
  salaryMin: number | null; salaryMax: number | null; showSalary: boolean
  status: string; slug: string | null; closesAt: string | null
  applicationCount: number; createdAt: string
}
interface Application {
  id: string; jobId: string; jobTitle: string | null
  applicantId: string; applicantName: string | null; applicantEmail: string | null
  applicantPhone: string | null; hasCv: boolean
  stage: string; source: string; score: number | null
  notes: string | null; rejectionReason: string | null
  hrEmployeeId: string | null
  interviews: Interview[]; history: StageHistory[]
  appliedAt: string; stageChangedAt: string; hiredAt: string | null
  offeredSalary: number | null; offeredSalaryFrequency: string | null
  offeredStartDate: string | null; offerBenefits: string | null
  offerLetterSentAt: string | null
  referrerName: string | null; referredByUserId: string | null; referredByUserName: string | null
  referralBonusAmount: number | null; referralBonusStatus: string | null; referralBonusPaidAt: string | null
}
interface Interview {
  id: string; interviewType: string; scheduledAt: string | null
  interviewerName: string | null; outcome: string | null
  notes: string | null; score: number | null; location: string | null
  panelists: { userId: string; userName: string | null }[]
  roundTemplateId: string | null; roundName: string | null; roundSequence: number | null
  createdAt: string
}
interface StageHistory {
  fromStage: string | null; toStage: string; changedByName: string | null
  notes: string | null; createdAt: string
}
interface Summary {
  openJobs: number; draftJobs: number; filledJobs: number
  newApplications: number; inScreening: number; inInterview: number
  offersMade: number; hiredThisMonth: number
}

// ── Constants ──────────────────────────────────────────────────────────────
const STAGE: Record<string, { color: string; bg: string; border: string; dot: string; label: string }> = {
  APPLIED:    { color: 'var(--hf-text-muted)', bg: 'var(--hf-surface-muted)', border: 'var(--hf-border)', dot: '#CBD5E1', label: 'Applied'    },
  SCREENING:  { color: 'var(--hf-warning-text)', bg: 'var(--hf-warning-soft)', border: 'var(--hf-warning-border)', dot: 'var(--hf-warning)', label: 'Screening'  },
  INTERVIEW:  { color: 'var(--hf-info-text)', bg: 'var(--hf-info-soft)', border: 'var(--hf-info-border)', dot: '#60A5FA', label: 'Interview'  },
  ASSESSMENT: { color: 'var(--hf-violet-text)', bg: 'var(--hf-violet-soft)', border: 'var(--hf-violet-border)', dot: '#A78BFA', label: 'Assessment' },
  OFFER:      { color: 'var(--hf-accent-text)', bg: 'var(--hf-accent-soft)', border: 'var(--hf-accent-border)', dot: '#2DD4BF', label: 'Offer'      },
  HIRED:      { color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)', border: 'var(--hf-success-border)', dot: 'var(--hf-success)', label: 'Hired'      },
  REJECTED:   { color: 'var(--hf-danger-text)', bg: 'var(--hf-danger-soft)', border: 'var(--hf-danger-border)', dot: 'var(--hf-danger)', label: 'Rejected'   },
  WITHDRAWN:  { color: 'var(--hf-text-faint)', bg: 'var(--hf-surface-muted)', border: 'var(--hf-border)', dot: '#CBD5E1', label: 'Withdrawn'  },
}
const JOB_STATUS: Record<string, { color: string; bg: string; border: string; label: string }> = {
  DRAFT:  { color: 'var(--hf-text-muted)', bg: 'var(--hf-surface-muted)', border: 'var(--hf-border)', label: 'Draft'  },
  OPEN:   { color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)', border: 'var(--hf-success-border)', label: 'Open'   },
  PAUSED: { color: 'var(--hf-warning-text)', bg: 'var(--hf-warning-soft)', border: 'var(--hf-warning-border)', label: 'Paused' },
  CLOSED: { color: 'var(--hf-danger-text)', bg: 'var(--hf-danger-soft)', border: 'var(--hf-danger-border)', label: 'Closed' },
  FILLED: { color: 'var(--hf-accent-text)', bg: 'var(--hf-accent-soft)', border: 'var(--hf-accent-border)', label: 'Filled' },
}
const INTERVIEW_TYPE_ICON: Record<string, any> = {
  PHONE: Phone, VIDEO: Video, IN_PERSON: Users, TECHNICAL: Monitor, PANEL: Mic,
}
const PIPELINE_STAGES = ['APPLIED','SCREENING','INTERVIEW','ASSESSMENT','OFFER','HIRED']

// ── Helpers ────────────────────────────────────────────────────────────────
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: 'var(--hf-surface)', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }
const btnP: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 8, padding: '9px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const btnS: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, padding: '9px 14px', border: '1.5px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)', fontSize: 13, cursor: 'pointer', color: 'var(--hf-text-secondary)', fontWeight: 500 }

const fmtDate = (d: any) => d ? new Date(d + (String(d).includes('T') ? '' : 'T00:00:00')).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
const fmtR    = (n: any) => n ? `R\u00A0${Number(n).toLocaleString('en-ZA')}` : ''
const stars   = (n: number | null) => n ? '★'.repeat(n) + '☆'.repeat(5 - n) : '—'

// ── Star Rating ────────────────────────────────────────────────────────────
function StarRating({ value, onChange }: { value: number | null; onChange: (n: number) => void }) {
  const [hover, setHover] = useState(0)
  return (
    <div style={{ display: 'flex', gap: 3 }}>
      {[1,2,3,4,5].map(i => (
        <button key={i} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(0)}
          onClick={() => onChange(i)}
          style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 22, color: i <= (hover || value || 0) ? 'var(--hf-warning-text)' : 'var(--hf-text-disabled)', padding: '0 1px', lineHeight: 1 }}>
          ★
        </button>
      ))}
    </div>
  )
}

// ── Confirm Modal ──────────────────────────────────────────────────────────
function ConfirmModal({ title, message, danger = false, confirmLabel, loading, onConfirm, onCancel, children }: any) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 28, width: 440, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ display: 'flex', gap: 14, marginBottom: 20 }}>
          <div style={{ width: 40, height: 40, borderRadius: '50%', background: danger ? 'var(--hf-danger-soft)' : 'var(--hf-success-soft-strong)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            {danger ? <AlertTriangle size={18} color="#DC2626" /> : <CheckCircle size={18} color="#166534" />}
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', lineHeight: 1.6 }}>{message}</div>
          </div>
        </div>
        {children}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 16 }}>
          <button onClick={onCancel} style={btnS}>Cancel</button>
          <button onClick={onConfirm} disabled={loading}
            style={{ ...btnP, background: danger ? 'var(--hf-danger)' : 'var(--hf-primary)', opacity: loading ? 0.6 : 1 }}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

function CompareModal({ applicationIds, onClose }: { applicationIds: string[]; onClose: () => void }) {
  const results = useQueries({
    queries: applicationIds.map(id => ({
      queryKey: ['rec-application', id],
      queryFn: async () => {
        const r = await apiClient.get(`/api/v1/recruiter/applications/${id}`)
        return r.data?.data ?? r.data
      },
    })),
  })

  const loading = results.some(r => r.isLoading)
  const candidates = results.map(r => r.data as Application | undefined).filter(Boolean) as Application[]

  const [cvError, setCvError] = useState('')
  const viewCv = async (id: string) => {
    setCvError('')
    try {
      const r = await apiClient.get(`/api/v1/recruiter/applications/${id}/cv`, { responseType: 'blob' })
      window.open(URL.createObjectURL(new Blob([r.data], { type: 'application/pdf' })), '_blank')
    } catch (e: any) {
      const data = e?.response?.data
      if (data instanceof Blob) {
        try { setCvError(JSON.parse(await data.text())?.message || 'Failed to load CV') }
        catch { setCvError('Failed to load CV') }
      } else {
        setCvError(e.response?.data?.message || 'Failed to load CV')
      }
    }
  }

  const rowStyle = { padding: '10px 16px', borderBottom: '1px solid var(--hf-border-subtle)', fontSize: 13, verticalAlign: 'top' as const }
  const labelStyle = { padding: '10px 16px', borderBottom: '1px solid var(--hf-border-subtle)', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', letterSpacing: '0.03em', textTransform: 'uppercase' as const, whiteSpace: 'nowrap' as const, background: 'var(--hf-surface-muted)', width: 140 }

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1200, backdropFilter: 'blur(2px)', padding: 20 }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 14, width: '100%', maxWidth: 980, maxHeight: '90vh', overflow: 'hidden', display: 'flex', flexDirection: 'column' as const, boxShadow: '0 25px 80px rgba(0,0,0,0.3)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '18px 24px', borderBottom: '1px solid var(--hf-border)' }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Compare candidates</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={20} /></button>
        </div>

        <div style={{ overflow: 'auto', flex: 1 }}>
          {loading ? (
            <div style={{ padding: 40, textAlign: 'center' as const, color: 'var(--hf-text-faint)', fontSize: 13 }}>Loading candidates...</div>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse' as const }}>
              <thead>
                <tr>
                  <th style={{ ...labelStyle, background: 'var(--hf-surface)' }}></th>
                  {candidates.map(c => {
                    const sc = STAGE[c.stage] ?? STAGE.APPLIED
                    return (
                      <th key={c.id} style={{ padding: '14px 16px', borderBottom: '1px solid var(--hf-border)', textAlign: 'left' as const, minWidth: 200 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                          <div style={{ width: 30, height: 30, borderRadius: '50%', background: sc.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                            <span style={{ fontSize: 11, fontWeight: 700, color: sc.color }}>{(c.applicantName ?? 'A').charAt(0).toUpperCase()}</span>
                          </div>
                          <div style={{ fontWeight: 800, fontSize: 14, color: 'var(--hf-text)' }}>{c.applicantName}</div>
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', fontWeight: 400 }}>{c.jobTitle}</div>
                      </th>
                    )
                  })}
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td style={labelStyle}>Stage</td>
                  {candidates.map(c => {
                    const sc = STAGE[c.stage] ?? STAGE.APPLIED
                    return (
                      <td key={c.id} style={rowStyle}>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          {sc.label}
                        </span>
                      </td>
                    )
                  })}
                </tr>
                <tr>
                  <td style={labelStyle}>Score</td>
                  {candidates.map(c => (
                    <td key={c.id} style={{ ...rowStyle, color: 'var(--hf-warning-text)', fontWeight: 700 }}>
                      {c.score ? '★'.repeat(c.score) + '☆'.repeat(5 - c.score) : <span style={{ color: 'var(--hf-text-disabled)' }}>Not scored</span>}
                    </td>
                  ))}
                </tr>
                <tr>
                  <td style={labelStyle}>Source</td>
                  {candidates.map(c => (
                    <td key={c.id} style={rowStyle}>{c.source?.replace('_', ' ') ?? '—'}</td>
                  ))}
                </tr>
                <tr>
                  <td style={labelStyle}>Applied</td>
                  {candidates.map(c => (
                    <td key={c.id} style={rowStyle}>{fmtDate(c.appliedAt)}</td>
                  ))}
                </tr>
                <tr>
                  <td style={labelStyle}>Interviews</td>
                  {candidates.map(c => {
                    const interviews = c.interviews ?? []
                    const passed = interviews.filter(iv => iv.outcome === 'PASSED').length
                    const failed = interviews.filter(iv => iv.outcome === 'FAILED').length
                    return (
                      <td key={c.id} style={rowStyle}>
                        {interviews.length === 0 ? <span style={{ color: 'var(--hf-text-disabled)' }}>None yet</span> : (
                          <>
                            {interviews.length} total
                            {(passed > 0 || failed > 0) && (
                              <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 2 }}>
                                {passed > 0 && <span style={{ color: 'var(--hf-success-text-strong)' }}>{passed} passed</span>}
                                {passed > 0 && failed > 0 && ' · '}
                                {failed > 0 && <span style={{ color: 'var(--hf-danger-text)' }}>{failed} failed</span>}
                              </div>
                            )}
                          </>
                        )}
                      </td>
                    )
                  })}
                </tr>
                <tr>
                  <td style={labelStyle}>Notes</td>
                  {candidates.map(c => (
                    <td key={c.id} style={{ ...rowStyle, color: 'var(--hf-text-secondary)', maxWidth: 220 }}>
                      {c.notes || <span style={{ color: 'var(--hf-text-disabled)' }}>—</span>}
                    </td>
                  ))}
                </tr>
                <tr>
                  <td style={{ ...labelStyle, borderBottom: 'none' }}>CV</td>
                  {candidates.map(c => (
                    <td key={c.id} style={{ ...rowStyle, borderBottom: 'none' }}>
                      {c.hasCv ? (
                        <button onClick={() => viewCv(c.id)}
                          style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: 'var(--hf-violet-soft)', color: 'var(--hf-violet-text)', border: '1px solid var(--hf-violet-border)', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                          <FileText size={11} /> View CV
                        </button>
                      ) : <span style={{ color: 'var(--hf-text-disabled)' }}>—</span>}
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          )}
        </div>

        {cvError && (
          <div style={{ margin: '0 24px 16px', padding: '10px 14px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 12, color: 'var(--hf-danger-text)' }}>
            {cvError}
          </div>
        )}
      </div>
    </div>
  )
}

// ── Application Detail Slide-over ──────────────────────────────────────────
function ApplicationDetail({ app: initial, onClose, onUpdated }: {
  app: Application; onClose: () => void; onUpdated: () => void
}) {
  const qc = useQueryClient()
  const [tab, setTab]             = useState<'overview'|'interviews'|'history'>('overview')
  const [showMoveModal, setShowMoveModal] = useState(false)
  const [showInterview, setShowInterview] = useState(false)
  const [showReferral, setShowReferral] = useState(false)
  const [refUserId, setRefUserId] = useState('')
  const [refBonusAmount, setRefBonusAmount] = useState('')
  const [refBonusStatus, setRefBonusStatus] = useState('')
  const [showConvert,   setShowConvert]   = useState(false)
  const [showReject,    setShowReject]    = useState(false)
  const [targetStage, setTargetStage]     = useState('')
  const [stageNotes,  setStageNotes]      = useState('')
  const [rejectReason, setRejectReason]   = useState('')
  const [offerSalary, setOfferSalary]     = useState('')
  const [offerFrequency, setOfferFrequency] = useState('MONTHLY')
  const [offerStartDate, setOfferStartDate] = useState('')
  const [offerBenefits, setOfferBenefits] = useState('')
  const [scoreVal,    setScoreVal]        = useState<number | null>(initial.score)
  const [notes,       setNotes]           = useState(initial.notes ?? '')
  const [ivType,      setIvType]          = useState('VIDEO')
  const [ivScheduled, setIvScheduled]     = useState('')
  const [ivInterviewer, setIvInterviewer] = useState('')
  const [ivInterviewerId, setIvInterviewerId] = useState('')
  const [ivMode, setIvMode]               = useState<'team' | 'external'>('team')
  const [ivLocation, setIvLocation]       = useState('')
  const [ivPanelistIds, setIvPanelistIds] = useState<string[]>([])
  const [ivRoundId, setIvRoundId]         = useState('')
  const [startDate,   setStartDate]       = useState(new Date().toISOString().split('T')[0])
  const [jobTitle,    setJobTitle]        = useState(initial.jobTitle ?? '')
  const [department,  setDepartment]      = useState('')
  const [createHrRecord, setCreateHrRecord] = useState(true)
  const [grossSalary, setGrossSalary]     = useState('')
  const [error, setError]                 = useState('')

  // Load full detail
  const { data: app } = useQuery<Application>({
    queryKey: ['rec-application', initial.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/recruiter/applications/${initial.id}`)
      return r.data?.data ?? r.data
    },
    initialData: initial,
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['rec-applications'] })
    qc.invalidateQueries({ queryKey: ['rec-summary'] })
    qc.invalidateQueries({ queryKey: ['rec-application', initial.id] })
    onUpdated()
  }

  // Fetched only while the Schedule Interview modal is open — no point
  // hitting this endpoint on every panel render.
  const { data: tenantUsers } = useQuery<{ id: string; email: string; firstName: string; lastName: string; status: string }[]>({
    queryKey: ['identity-users'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/identity/users')
      return r.data?.data ?? r.data
    },
    enabled: showInterview || showReferral,
  })
  const activeUsers = (tenantUsers ?? []).filter(u => u.status === 'ACTIVE')

  interface Round { id: string; name: string; sequence: number; description: string | null }
  const { data: jobRounds } = useQuery<Round[]>({
    queryKey: ['job-interview-rounds', app?.jobId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/recruiter/jobs/${app!.jobId}/interview-rounds`)
      return r.data?.data ?? r.data
    },
    enabled: showInterview && !!app?.jobId,
  })

  const moveStage = useMutation({
    mutationFn: ({ stage, notes, reason }: any) =>
      apiClient.post(`/api/v1/recruiter/applications/${app!.id}/stage`, {
        stage, notes: notes || null, rejectionReason: reason || null,
        // Only meaningful when stage === 'OFFER' — backend ignores these
        // for every other stage. Sent as null rather than omitted so an
        // accidental partial fill doesn't silently carry over from a
        // previous open of this modal.
        offeredSalary: stage === 'OFFER' && offerSalary !== '' ? Number(offerSalary) : null,
        offeredSalaryFrequency: stage === 'OFFER' ? offerFrequency : null,
        offeredStartDate: stage === 'OFFER' && offerStartDate !== '' ? offerStartDate : null,
        offerBenefits: stage === 'OFFER' && offerBenefits !== '' ? offerBenefits : null,
      }),
    onSuccess: () => {
      invalidate(); setShowMoveModal(false); setShowReject(false)
      setStageNotes(''); setRejectReason('')
      setOfferSalary(''); setOfferStartDate(''); setOfferBenefits('')
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  const parseBlobError = async (e: any, fallback: string) => {
    const data = e?.response?.data
    if (data instanceof Blob) {
      try {
        const json = JSON.parse(await data.text())
        return json?.message || fallback
      } catch { return fallback }
    }
    return e?.response?.data?.message || fallback
  }

  // window.open(blobUrl) always opens the browser's PDF viewer regardless of
  // the server's Content-Disposition header — that header only affects
  // direct HTTP navigation, not a blob: URL constructed client-side from an
  // already-fetched response. A temporary <a download> element is the only
  // way to force an actual file download from a blob.
  const downloadBlob = (data: BlobPart, filename: string) => {
    const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const downloadOfferLetter = useMutation({
    mutationFn: async () => {
      const r = await apiClient.get(`/api/v1/recruiter/applications/${app!.id}/offer-letter`, { responseType: 'blob' })
      downloadBlob(r.data, `Offer Letter - ${app?.applicantName ?? 'candidate'}.pdf`)
    },
    onError: async (e: any) => setError(await parseBlobError(e, 'Failed to download offer letter — offer terms may not be recorded yet')),
  })

  const sendOfferLetter = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/recruiter/applications/${app!.id}/offer-letter/send`),
    onSuccess: () => invalidate(),
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to send offer letter'),
  })

  const downloadScorecard = useMutation({
    mutationFn: async () => {
      const r = await apiClient.get(`/api/v1/recruiter/applications/${app!.id}/scorecard`, { responseType: 'blob' })
      downloadBlob(r.data, `Scorecard - ${app?.applicantName ?? 'candidate'}.pdf`)
    },
    onError: async (e: any) => setError(await parseBlobError(e, 'Failed to download scorecard')),
  })

  // window.open, not downloadBlob — this is View CV, meant to preview
  // inline in a new tab, unlike the offer-letter/scorecard buttons which
  // were explicitly changed to force a real download earlier this session.
  const viewCv = useMutation({
    mutationFn: async () => {
      const r = await apiClient.get(`/api/v1/recruiter/applications/${app!.id}/cv`, { responseType: 'blob' })
      window.open(URL.createObjectURL(new Blob([r.data], { type: 'application/pdf' })), '_blank')
    },
    onError: async (e: any) => setError(await parseBlobError(e, 'Failed to load CV — none may be on file for this candidate')),
  })

  const score = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/recruiter/applications/${app!.id}/score`, {
      score: scoreVal, notes: notes || null,
    }),
    onSuccess: () => invalidate(),
  })

  const scheduleInterview = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/recruiter/applications/${app!.id}/interviews`, {
      interviewType: ivType,
      scheduledAt: ivScheduled ? new Date(ivScheduled).toISOString() : null,
      interviewerId: ivMode === 'team' && ivInterviewerId !== '' ? ivInterviewerId : null,
      interviewerName: ivInterviewer || null,
      location: ivLocation || null,
      panelists: ivPanelistIds.map(id => {
        const u = activeUsers.find(u => u.id === id)
        return { userId: id, userName: u ? `${u.firstName} ${u.lastName}` : null }
      }),
      roundTemplateId: ivRoundId || null,
    }),
    onSuccess: () => {
      invalidate(); setShowInterview(false); setIvScheduled('')
      setIvInterviewer(''); setIvInterviewerId(''); setIvMode('team'); setIvLocation('')
      setIvPanelistIds([]); setIvRoundId('')
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  const updateReferral = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/recruiter/applications/${app!.id}/referral`, {
      referredByUserId: refUserId || null,
      bonusAmount: refBonusAmount !== '' ? Number(refBonusAmount) : null,
      bonusStatus: refBonusStatus || null,
    }),
    onSuccess: () => { invalidate(); setShowReferral(false) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to update referral'),
  })

  const convertToEmployee = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/recruiter/applications/${app!.id}/convert-to-employee`, {
      startDate, jobTitle, department: department || null,
      createHrRecord,
      // grossSalary is required by the backend when createHrRecord is true —
      // send null (not empty string) when unset or when this is an external
      // placement, so it doesn't accidentally coerce to 0.
      grossSalary: createHrRecord && grossSalary !== '' ? Number(grossSalary) : null,
    }),
    onSuccess: () => { invalidate(); setShowConvert(false) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to convert'),
  })

  const sc   = STAGE[app?.stage ?? 'APPLIED'] ?? STAGE.APPLIED
  const availableStages = PIPELINE_STAGES.filter(s => s !== app?.stage)
  // Mirrors RecApplication.isActive() on the backend — HIRED/REJECTED/WITHDRAWN
  // are terminal. Backend now rejects moveStage/scheduleInterview on these
  // with a 400 (APPLICATION_TERMINAL); this just keeps the buttons from
  // appearing clickable in the first place.
  const isTerminal = app?.stage === 'HIRED' || app?.stage === 'REJECTED' || app?.stage === 'WITHDRAWN'

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'stretch', justifyContent: 'flex-end', zIndex: 1000 }}>
      <div style={{ background: 'var(--hf-surface)', width: 600, height: '100%', overflowY: 'auto', boxShadow: '-8px 0 40px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column' }}>

        {/* Header */}
        <div style={{ padding: '20px 24px 0', borderBottom: '1px solid var(--hf-border-subtle)', flexShrink: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
            <div>
              <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: 'var(--hf-text)', marginBottom: 5 }}>{app?.applicantName}</h2>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                  <span style={{ width: 5, height: 5, borderRadius: '50%', background: sc.dot }} />{sc.label}
                </span>
                <span style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{app?.jobTitle}</span>
                {app?.hrEmployeeId && (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                    <UserCheck size={10} /> Onboarded to HR
                  </span>
                )}
              </div>
            </div>
            <button onClick={onClose} style={{ background: 'var(--hf-surface-sunken)', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: 'var(--hf-text-muted)', flexShrink: 0 }}>
              <X size={14} />
            </button>
          </div>

          {/* Contact strip */}
          <div style={{ display: 'flex', gap: 16, fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 12, flexWrap: 'wrap' }}>
            {app?.applicantEmail && <span>{app.applicantEmail}</span>}
            {app?.applicantPhone && <span>{app.applicantPhone}</span>}
            <span>Applied {fmtDate(app?.appliedAt)}</span>
            <span>Source: {app?.source?.replace('_',' ') ?? '—'}</span>
          </div>

          {/* Action buttons */}
          <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap', marginBottom: 14 }}>
            {!isTerminal && availableStages.filter(s => !['REJECTED'].includes(s)).map(s => {
              const cfg = STAGE[s]
              return (
                <button key={s} onClick={() => { setTargetStage(s); setShowMoveModal(true) }}
                  style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.border}`, borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                  <ChevronRight size={10} />{cfg.label}
                </button>
              )
            })}
            {!isTerminal && (
              <button onClick={() => setShowReject(true)}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', border: '1px solid var(--hf-danger-border)', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                <X size={10} /> Reject
              </button>
            )}
            {!isTerminal && (
              <button onClick={() => setShowInterview(true)}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: 'var(--hf-info-soft)', color: 'var(--hf-info-text)', border: '1px solid var(--hf-info-border)', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                <Calendar size={10} /> Schedule interview
              </button>
            )}
            {app?.stage === 'HIRED' && !app?.hrEmployeeId && (
              <button onClick={() => setShowConvert(true)}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', border: '1px solid var(--hf-success-border)', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                <UserPlus size={10} /> Convert to employee
              </button>
            )}
            {app?.hasCv && (
              <button onClick={() => viewCv.mutate()} disabled={viewCv.isPending}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: 'var(--hf-violet-soft)', color: 'var(--hf-violet-text)', border: '1px solid var(--hf-violet-border)', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                <FileText size={10} /> {viewCv.isPending ? 'Loading...' : 'View CV'}
              </button>
            )}
            <button onClick={() => downloadScorecard.mutate()} disabled={downloadScorecard.isPending}
              style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
              <FileText size={10} /> {downloadScorecard.isPending ? 'Preparing...' : 'Scorecard PDF'}
            </button>
            {app?.offeredSalary != null && (
              <>
                <button onClick={() => downloadOfferLetter.mutate()} disabled={downloadOfferLetter.isPending}
                  style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                  <FileText size={10} /> {downloadOfferLetter.isPending ? 'Preparing...' : 'Offer letter PDF'}
                </button>
                {app?.offerLetterSentAt ? (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', border: '1px solid var(--hf-success-border)', borderRadius: 7, fontSize: 11, fontWeight: 700 }}>
                    <CheckCircle size={10} /> Sent {fmtDate(app.offerLetterSentAt)}
                  </span>
                ) : (
                  <button onClick={() => sendOfferLetter.mutate()} disabled={sendOfferLetter.isPending}
                    style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', border: '1px solid var(--hf-success-border)', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                    <Calendar size={10} /> {sendOfferLetter.isPending ? 'Sending...' : 'Send offer letter'}
                  </button>
                )}
              </>
            )}
          </div>

          {/* Tabs */}
          <div style={{ display: 'flex' }}>
            {(['overview','interviews','history'] as const).map(t => (
              <button key={t} onClick={() => setTab(t)}
                style={{ padding: '8px 16px', fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: tab === t ? 'var(--hf-primary-text)' : 'var(--hf-text-faint)', borderBottom: `2px solid ${tab === t ? '#1B3A6B' : 'transparent'}`, marginBottom: -1, textTransform: 'capitalize' }}>
                {t === 'interviews' ? `Interviews (${app?.interviews?.length ?? 0})` : t === 'history' ? `History (${app?.history?.length ?? 0})` : 'Overview'}
              </button>
            ))}
          </div>
        </div>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px 28px' }}>
          {error && <div style={{ marginBottom: 12, padding: '10px 14px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}

          {tab === 'overview' && (
            <div>
              {/* Score */}
              <div style={{ marginBottom: 18, padding: '14px 16px', background: 'var(--hf-surface-muted)', borderRadius: 10, border: '1px solid var(--hf-border)' }}>
                <label style={{ ...lbl, marginBottom: 10 }}>Candidate rating</label>
                <StarRating value={scoreVal} onChange={v => setScoreVal(v)} />
                {scoreVal && (
                  <div style={{ marginTop: 10 }}>
                    <label style={lbl}>Internal notes</label>
                    <textarea value={notes} onChange={e => setNotes(e.target.value)} rows={3}
                      placeholder="Notes visible only to your team..." style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit' }} />
                    <button onClick={() => score.mutate()} style={{ ...btnP, marginTop: 8, fontSize: 12, padding: '6px 14px' }}>
                      Save rating
                    </button>
                  </div>
                )}
              </div>

              {/* Details */}
              {[
                ['Email',     app?.applicantEmail ?? '—'],
                ['Phone',     app?.applicantPhone ?? '—'],
                ['Job',       app?.jobTitle ?? '—'],
                ['Stage',     STAGE[app?.stage ?? 'APPLIED']?.label],
                ['Source',    app?.source?.replace(/_/g,' ') ?? '—'],
                ['Applied',   fmtDT(app?.appliedAt)],
                ['Stage changed', fmtDT(app?.stageChangedAt)],
                ['Hired',     app?.hiredAt ? fmtDT(app.hiredAt) : '—'],
              ].map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--hf-border-subtle)', fontSize: 13 }}>
                  <span style={{ color: 'var(--hf-text-faint)', fontWeight: 600 }}>{k}</span>
                  <span style={{ color: 'var(--hf-text-secondary)', fontWeight: 500 }}>{v as string}</span>
                </div>
              ))}

              {app?.rejectionReason && (
                <div style={{ marginTop: 14, padding: '12px 14px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 9 }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--hf-danger-text)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>Rejection reason</div>
                  <div style={{ fontSize: 13, color: 'var(--hf-text-secondary)' }}>{app.rejectionReason}</div>
                </div>
              )}

              {/* Referral */}
              <div style={{ marginTop: 14, padding: '12px 14px', background: 'var(--hf-surface-muted)', border: '1px solid var(--hf-border)', borderRadius: 9 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>Referral</div>
                    {app?.referredByUserId ? (
                      <>
                        <div style={{ fontSize: 13, color: 'var(--hf-text)', fontWeight: 600 }}>{app.referredByUserName}</div>
                        {app.referrerName && app.referrerName !== app.referredByUserName && (
                          <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>Candidate said: {app.referrerName}</div>
                        )}
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 6 }}>
                          {app.referralBonusAmount != null && (
                            <span style={{ fontSize: 12, color: 'var(--hf-text-secondary)', fontWeight: 600 }}>R {Number(app.referralBonusAmount).toLocaleString()}</span>
                          )}
                          {app.referralBonusStatus && app.referralBonusStatus !== 'NOT_SET' && (
                            <span style={{
                              fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 10,
                              background: app.referralBonusStatus === 'PAID' ? 'var(--hf-success-soft-strong)' : app.referralBonusStatus === 'APPROVED' ? 'var(--hf-info-soft)' : 'var(--hf-warning-soft-strong)',
                              color: app.referralBonusStatus === 'PAID' ? 'var(--hf-success-text-strong)' : app.referralBonusStatus === 'APPROVED' ? 'var(--hf-info-text)' : 'var(--hf-warning-text-deep)',
                            }}>
                              {app.referralBonusStatus}{app.referralBonusStatus === 'PAID' && app.referralBonusPaidAt ? ` · ${fmtDate(app.referralBonusPaidAt)}` : ''}
                            </span>
                          )}
                        </div>
                      </>
                    ) : app?.referrerName ? (
                      <div style={{ fontSize: 13, color: 'var(--hf-text)' }}>Candidate said: <strong>{app.referrerName}</strong> <span style={{ color: 'var(--hf-text-faint)', fontWeight: 400 }}>(unverified)</span></div>
                    ) : (
                      <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>No referral on this application</div>
                    )}
                  </div>
                  <button onClick={() => {
                    setRefUserId(app?.referredByUserId ?? '')
                    setRefBonusAmount(app?.referralBonusAmount != null ? String(app.referralBonusAmount) : '')
                    setRefBonusStatus(app?.referralBonusStatus ?? '')
                    setShowReferral(true)
                  }} style={{ background: 'none', border: '1px solid var(--hf-border)', borderRadius: 6, padding: '4px 10px', fontSize: 11, fontWeight: 600, color: 'var(--hf-text-secondary)', cursor: 'pointer', whiteSpace: 'nowrap' as const }}>
                    {app?.referredByUserId ? 'Edit' : 'Link referral'}
                  </button>
                </div>
              </div>
            </div>
          )}

          {tab === 'interviews' && (
            <div>
              <button onClick={() => setShowInterview(true)} style={{ ...btnP, marginBottom: 16, fontSize: 12 }}>
                <Calendar size={13} /> Schedule interview
              </button>
              {(app?.interviews ?? []).length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--hf-text-faint)', border: '1.5px dashed var(--hf-border)', borderRadius: 12 }}>
                  <Calendar size={28} style={{ marginBottom: 10, opacity: 0.4 }} />
                  <div style={{ fontWeight: 600, color: 'var(--hf-text-tertiary)' }}>No interviews scheduled</div>
                </div>
              ) : (app?.interviews ?? []).map((iv: Interview) => {
                const Icon = INTERVIEW_TYPE_ICON[iv.interviewType] ?? Video
                const outcomeColor = iv.outcome === 'PASSED' ? '#166534' : iv.outcome === 'FAILED' ? '#DC2626' : '#D97706'
                return (
                  <div key={iv.id} style={{ border: '1px solid var(--hf-border)', borderRadius: 10, padding: '14px 16px', marginBottom: 10 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{ width: 30, height: 30, borderRadius: 7, background: 'var(--hf-info-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          <Icon size={14} color="#1D4ED8" />
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--hf-text)', display: 'flex', alignItems: 'center', gap: 6 }}>
                            {iv.interviewType.replace('_',' ')}
                            {iv.roundName && (
                              <span style={{ fontSize: 10, fontWeight: 700, color: 'var(--hf-violet-text)', background: 'var(--hf-violet-soft)', padding: '2px 7px', borderRadius: 10 }}>
                                {iv.roundSequence != null ? `${iv.roundSequence}. ` : ''}{iv.roundName}
                              </span>
                            )}
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>
                            {iv.scheduledAt ? fmtDT(iv.scheduledAt) : 'Not scheduled'}
                            {iv.interviewerName && ` · ${iv.interviewerName}`}
                            {iv.panelists && iv.panelists.length > 0 &&
                              ` + ${iv.panelists.map(p => p.userName).filter(Boolean).join(', ')}`}
                          </div>
                        </div>
                      </div>
                      {iv.outcome && (
                        <span style={{ fontSize: 11, fontWeight: 700, color: outcomeColor }}>{iv.outcome}</span>
                      )}
                    </div>
                    {iv.score && <div style={{ fontSize: 13, color: 'var(--hf-warning-text)' }}>{'★'.repeat(iv.score)}{'☆'.repeat(5 - iv.score)}</div>}
                    {iv.location && (
                      <div style={{ fontSize: 12, color: 'var(--hf-info-text)', marginTop: 6, display: 'flex', alignItems: 'center', gap: 4 }}>
                        <MapPin size={11} />
                        {iv.location.startsWith('http') ? (
                          <a href={iv.location} target="_blank" rel="noreferrer" style={{ color: 'var(--hf-info-text)' }}>{iv.location}</a>
                        ) : iv.location}
                      </div>
                    )}
                    {iv.notes && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 6 }}>{iv.notes}</div>}
                  </div>
                )
              })}
            </div>
          )}

          {tab === 'history' && (
            <div>
              {(app?.history ?? []).length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--hf-text-faint)' }}>No stage history</div>
              ) : (app?.history ?? []).map((h: StageHistory, i: number) => {
                const toStage = STAGE[h.toStage] ?? STAGE.APPLIED
                return (
                  <div key={i} style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                      <div style={{ width: 10, height: 10, borderRadius: '50%', background: toStage.dot, marginTop: 4, flexShrink: 0 }} />
                      {i < (app?.history?.length ?? 0) - 1 && <div style={{ width: 1, flex: 1, background: 'var(--hf-surface-strong)', marginTop: 4 }} />}
                    </div>
                    <div style={{ flex: 1, paddingBottom: 12 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
                        <span style={{ fontWeight: 700, fontSize: 13, color: 'var(--hf-text)' }}>
                          {h.fromStage ? `${STAGE[h.fromStage]?.label ?? h.fromStage} → ` : ''}{toStage.label}
                        </span>
                        <span style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{fmtDT(h.createdAt)}</span>
                      </div>
                      {h.changedByName && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>by {h.changedByName}</div>}
                      {h.notes && <div style={{ fontSize: 12, color: 'var(--hf-text-faint)', marginTop: 3 }}>{h.notes}</div>}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* Move Stage Modal */}
      {showMoveModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 28, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Move to {STAGE[targetStage]?.label}</h3>
              <button onClick={() => setShowMoveModal(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={18} /></button>
            </div>
            <div>
              {targetStage === 'OFFER' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 14, padding: 12, background: 'var(--hf-surface-muted)', borderRadius: 8, border: '1px solid var(--hf-border)' }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-success-text-strong)', textTransform: 'uppercase' as const }}>Offer terms</div>
                  <div>
                    <label style={lbl}>Gross salary (monthly) *</label>
                    <input type="number" min="0" step="0.01" value={offerSalary} onChange={e => setOfferSalary(e.target.value)}
                      placeholder="e.g. 32000" style={inp} />
                  </div>
                  <div>
                    <label style={lbl}>Pay frequency</label>
                    <select value={offerFrequency} onChange={e => setOfferFrequency(e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
                      {['MONTHLY', 'WEEKLY', 'BIWEEKLY', 'ANNUALLY'].map(f => <option key={f} value={f}>{f}</option>)}
                    </select>
                  </div>
                  <div>
                    <label style={lbl}>Proposed start date</label>
                    <input type="date" value={offerStartDate} onChange={e => setOfferStartDate(e.target.value)} style={inp} />
                  </div>
                  <div>
                    <label style={lbl}>Benefits (optional)</label>
                    <textarea value={offerBenefits} onChange={e => setOfferBenefits(e.target.value)} rows={2}
                      placeholder="Medical aid, 13th cheque, etc." style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit' }} />
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>
                    These terms are required before an offer letter can be generated or sent.
                  </div>
                </div>
              )}
              <label style={lbl}>Notes (optional)</label>
              <textarea value={stageNotes} onChange={e => setStageNotes(e.target.value)} rows={3}
                placeholder="Add context about this stage move..."
                style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit', marginBottom: 12 }} />
              {targetStage === 'HIRED' && (
                <div style={{ padding: '10px 12px', background: 'var(--hf-success-soft-strong)', border: '1px solid var(--hf-success-border)', borderRadius: 8, fontSize: 12, color: 'var(--hf-success-text-strong)', marginBottom: 12 }}>
                  Once marked Hired, you can convert this applicant to an HR employee record.
                </div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowMoveModal(false)} style={btnS}>Cancel</button>
              <button onClick={() => moveStage.mutate({ stage: targetStage, notes: stageNotes })} disabled={moveStage.isPending}
                style={{ ...btnP, background: STAGE[targetStage]?.color ?? 'var(--hf-primary)' }}>
                {moveStage.isPending ? 'Moving...' : `Move to ${STAGE[targetStage]?.label}`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {showReject && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 28, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', gap: 14, marginBottom: 20 }}>
              <div style={{ width: 40, height: 40, borderRadius: '50%', background: 'var(--hf-danger-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <X size={18} color="#DC2626" />
              </div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 4 }}>Reject candidate</div>
                <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>A polite rejection email will be sent to the applicant.</div>
              </div>
            </div>
            <div>
              <label style={lbl}>Rejection reason (optional)</label>
              <textarea value={rejectReason} onChange={e => setRejectReason(e.target.value)} rows={3} autoFocus
                placeholder="After careful consideration, we have decided to move forward with other candidates whose experience more closely matches the role requirements."
                style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit' }} />
              <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 5 }}>This reason will be included in the rejection email.</div>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowReject(false)} style={btnS}>Cancel</button>
              <button onClick={() => moveStage.mutate({ stage: 'REJECTED', notes: null, reason: rejectReason })} disabled={moveStage.isPending}
                style={{ ...btnP, background: 'var(--hf-danger)' }}>
                {moveStage.isPending ? 'Sending...' : 'Reject and notify'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Schedule Interview Modal */}
      {showInterview && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 28, width: 460, maxHeight: '88vh', overflowY: 'auto' as const, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Schedule Interview</h3>
              <button onClick={() => setShowInterview(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={lbl}>Interview type</label>
                <select value={ivType} onChange={e => setIvType(e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
                  {['PHONE','VIDEO','IN_PERSON','TECHNICAL','PANEL'].map(t => <option key={t} value={t}>{t.replace('_',' ')}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Date & time</label>
                <input type="datetime-local" value={ivScheduled} onChange={e => setIvScheduled(e.target.value)} style={inp} />
              </div>
              {(jobRounds ?? []).length > 0 && (
                <div>
                  <label style={lbl}>Round (optional)</label>
                  <select value={ivRoundId} onChange={e => setIvRoundId(e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
                    <option value="">No specific round</option>
                    {jobRounds!.map(r => (
                      <option key={r.id} value={r.id}>{r.sequence}. {r.name}</option>
                    ))}
                  </select>
                </div>
              )}
              <div>
                <label style={lbl}>Interviewer</label>
                <div style={{ display: 'flex', gap: 7, marginBottom: 8 }}>
                  <button type="button" onClick={() => { setIvMode('team'); setIvInterviewer(''); }}
                    style={{ flex: 1, padding: '6px 10px', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer',
                      background: ivMode === 'team' ? 'var(--hf-info-soft)' : 'var(--hf-surface-muted)',
                      color: ivMode === 'team' ? 'var(--hf-info-text)' : 'var(--hf-text-muted)',
                      border: `1px solid ${ivMode === 'team' ? '#BFDBFE' : '#E2E8F0'}` }}>
                    From team
                  </button>
                  <button type="button" onClick={() => { setIvMode('external'); setIvInterviewerId(''); }}
                    style={{ flex: 1, padding: '6px 10px', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer',
                      background: ivMode === 'external' ? 'var(--hf-info-soft)' : 'var(--hf-surface-muted)',
                      color: ivMode === 'external' ? 'var(--hf-info-text)' : 'var(--hf-text-muted)',
                      border: `1px solid ${ivMode === 'external' ? '#BFDBFE' : '#E2E8F0'}` }}>
                    External / other
                  </button>
                </div>
                {ivMode === 'team' ? (
                  <>
                    <select value={ivInterviewerId} onChange={e => {
                      const id = e.target.value
                      setIvInterviewerId(id)
                      const u = activeUsers.find(u => u.id === id)
                      setIvInterviewer(u ? `${u.firstName} ${u.lastName}` : '')
                      setIvPanelistIds(prev => prev.filter(pid => pid !== id))
                    }} style={{ ...inp, background: 'var(--hf-surface)' }}>
                      <option value="">Select a team member...</option>
                      {activeUsers.map(u => (
                        <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>
                      ))}
                    </select>
                    <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 5 }}>
                      They'll get an email and in-app notification once this is scheduled.
                    </div>
                  </>
                ) : (
                  <>
                    <input value={ivInterviewer} onChange={e => setIvInterviewer(e.target.value)} placeholder="Thabo Modise" style={inp} />
                    <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 5 }}>
                      Not a platform user — no notification will be sent, this is just a label.
                    </div>
                  </>
                )}
              </div>
              {activeUsers.length > 0 && (
                <div>
                  <label style={lbl}>Additional panelists (optional)</label>
                  <div style={{ display: 'flex', flexWrap: 'wrap' as const, gap: 6, maxHeight: 120, overflowY: 'auto' as const, border: '1px solid var(--hf-border)', borderRadius: 8, padding: 8 }}>
                    {activeUsers.filter(u => u.id !== ivInterviewerId).map(u => {
                      const selected = ivPanelistIds.includes(u.id)
                      return (
                        <button key={u.id} type="button"
                          onClick={() => setIvPanelistIds(prev => selected ? prev.filter(id => id !== u.id) : [...prev, u.id])}
                          style={{
                            padding: '4px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600, cursor: 'pointer',
                            background: selected ? 'var(--hf-info-soft)' : 'var(--hf-surface-muted)',
                            color: selected ? 'var(--hf-info-text)' : 'var(--hf-text-muted)',
                            border: `1px solid ${selected ? '#BFDBFE' : '#E2E8F0'}`,
                          }}>
                          {u.firstName} {u.lastName}
                        </button>
                      )
                    })}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 5 }}>
                    Each panelist gets their own email and in-app notification, same as the primary interviewer.
                  </div>
                </div>
              )}
              <div>
                <label style={lbl}>{ivType === 'VIDEO' ? 'Meeting link' : ivType === 'PHONE' ? 'Number to call' : 'Venue'} (optional)</label>
                <input value={ivLocation} onChange={e => setIvLocation(e.target.value)}
                  placeholder={ivType === 'VIDEO' ? 'https://meet.google.com/...' : ivType === 'PHONE' ? '+27 82 123 4567' : '123 Main Street, Sandton'}
                  style={inp} />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowInterview(false)} style={btnS}>Cancel</button>
              <button onClick={() => scheduleInterview.mutate()} disabled={scheduleInterview.isPending}
                style={btnP}><Calendar size={13} /> {scheduleInterview.isPending ? 'Scheduling...' : 'Schedule'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Link Referral Modal */}
      {showReferral && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 28, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Link referral</h3>
              <button onClick={() => setShowReferral(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={18} /></button>
            </div>
            {app?.referrerName && (
              <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 14, padding: '8px 10px', background: 'var(--hf-surface-muted)', borderRadius: 7 }}>
                Candidate said they were referred by: <strong>{app.referrerName}</strong>
              </div>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={lbl}>Referred by (team member)</label>
                <select value={refUserId} onChange={e => setRefUserId(e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
                  <option value="">Not linked</option>
                  {activeUsers.map(u => (
                    <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label style={lbl}>Bonus amount (R)</label>
                <input type="number" min="0" value={refBonusAmount} onChange={e => setRefBonusAmount(e.target.value)} placeholder="e.g. 5000" style={inp} />
              </div>
              <div>
                <label style={lbl}>Bonus status</label>
                <select value={refBonusStatus} onChange={e => setRefBonusStatus(e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
                  <option value="">Leave unchanged</option>
                  {['NOT_SET', 'PENDING', 'APPROVED', 'PAID'].map(s => <option key={s} value={s}>{s}</option>)}
                </select>
                <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 5 }}>
                  Set automatically to PENDING once the candidate is hired, if linked. Approve and mark paid manually.
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowReferral(false)} style={btnS}>Cancel</button>
              <button onClick={() => updateReferral.mutate()} disabled={updateReferral.isPending} style={btnP}>
                {updateReferral.isPending ? 'Saving...' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Convert to HR Employee Modal */}
      {showConvert && (
        <ConfirmModal title="Convert to HR employee"
          message={createHrRecord
            ? `Create an HR employee record for ${app?.applicantName}? Their profile will be pre-filled from their application.`
            : `Mark ${app?.applicantName} as placed — no HR record will be created. Use this for candidates placed at a client company rather than hired internally.`}
          confirmLabel={createHrRecord ? 'Create employee record' : 'Mark as placed'} loading={convertToEmployee.isPending}
          onConfirm={() => convertToEmployee.mutate()} onCancel={() => setShowConvert(false)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 14 }}>
            <div>
              <label style={lbl}>Placement type</label>
              <div style={{ display: 'flex', gap: 7 }}>
                <button type="button" onClick={() => setCreateHrRecord(true)}
                  style={{ flex: 1, padding: '7px 10px', borderRadius: 7, fontSize: 12, fontWeight: 700, cursor: 'pointer',
                    background: createHrRecord ? 'var(--hf-success-soft-strong)' : 'var(--hf-surface-muted)',
                    color: createHrRecord ? 'var(--hf-success-text-strong)' : 'var(--hf-text-muted)',
                    border: `1px solid ${createHrRecord ? '#86EFAC' : '#E2E8F0'}` }}>
                  Internal hire
                </button>
                <button type="button" onClick={() => setCreateHrRecord(false)}
                  style={{ flex: 1, padding: '7px 10px', borderRadius: 7, fontSize: 12, fontWeight: 700, cursor: 'pointer',
                    background: !createHrRecord ? 'var(--hf-info-soft)' : 'var(--hf-surface-muted)',
                    color: !createHrRecord ? 'var(--hf-info-text)' : 'var(--hf-text-muted)',
                    border: `1px solid ${!createHrRecord ? '#BFDBFE' : '#E2E8F0'}` }}>
                  External placement
                </button>
              </div>
              <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 5 }}>
                {createHrRecord
                  ? 'Creates an employee record in HR — this tenant is the employer.'
                  : "Candidate joins a client company, not this tenant — no HR record is created."}
              </div>
            </div>
            <div><label style={lbl}>Job title</label><input value={jobTitle} onChange={e => setJobTitle(e.target.value)} placeholder={initial.jobTitle ?? ''} style={inp} /></div>
            <div><label style={lbl}>Department</label><input value={department} onChange={e => setDepartment(e.target.value)} placeholder="Operations" style={inp} /></div>
            <div><label style={lbl}>Start date *</label><input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} style={inp} /></div>
            {createHrRecord && (
              <div>
                <label style={lbl}>Gross salary (monthly) *</label>
                <input type="number" min="0" step="0.01" value={grossSalary} onChange={e => setGrossSalary(e.target.value)}
                  placeholder="e.g. 32000" style={inp} />
              </div>
            )}
          </div>
          {error && <div style={{ padding: '8px 12px', background: 'var(--hf-danger-soft)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)', marginBottom: 10 }}>{error}</div>}
        </ConfirmModal>
      )}
    </div>
  )
}

// ── Create / Edit Job Modal ────────────────────────────────────────────────
function InterviewRoundsSection({ jobId }: { jobId: string }) {
  const qc = useQueryClient()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState('')

  interface Round { id: string; name: string; sequence: number; description: string | null }
  const { data: rounds } = useQuery<Round[]>({
    queryKey: ['job-interview-rounds', jobId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/recruiter/jobs/${jobId}/interview-rounds`)
      return r.data?.data ?? r.data
    },
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['job-interview-rounds', jobId] })

  const addRound = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/recruiter/jobs/${jobId}/interview-rounds`, {
      name, sequence: (rounds?.length ?? 0) + 1, description: description || null,
    }),
    onSuccess: () => { invalidate(); setName(''); setDescription('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to add round'),
  })

  const deleteRound = useMutation({
    mutationFn: (roundId: string) => apiClient.delete(`/api/v1/recruiter/jobs/${jobId}/interview-rounds/${roundId}`),
    onSuccess: () => invalidate(),
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to remove round — it may already have interviews scheduled against it'),
  })

  return (
    <div style={{ gridColumn: '1/-1', borderTop: '1px solid var(--hf-border)', paddingTop: 16, marginTop: 4 }}>
      <label style={lbl}>Interview process (optional)</label>
      <p style={{ fontSize: 12, color: 'var(--hf-text-faint)', margin: '0 0 10px' }}>
        Define the rounds candidates for this role go through — e.g. Phone Screen, Technical, Final. Shown when scheduling an interview.
      </p>
      {(rounds ?? []).length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column' as const, gap: 6, marginBottom: 10 }}>
          {rounds!.map(r => (
            <div key={r.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '7px 10px', background: 'var(--hf-surface-muted)', border: '1px solid var(--hf-border)', borderRadius: 7 }}>
              <span style={{ fontSize: 13, color: 'var(--hf-text)' }}>
                <strong>{r.sequence}.</strong> {r.name}
                {r.description && <span style={{ color: 'var(--hf-text-faint)', fontWeight: 400 }}> — {r.description}</span>}
              </span>
              <button onClick={() => deleteRound.mutate(r.id)} disabled={deleteRound.isPending}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}>
                <X size={14} />
              </button>
            </div>
          ))}
        </div>
      )}
      <div style={{ display: 'flex', gap: 8 }}>
        <input value={name} onChange={e => setName(e.target.value)} placeholder="Round name, e.g. Technical"
          style={{ ...inp, flex: 1 }} />
        <input value={description} onChange={e => setDescription(e.target.value)} placeholder="Description (optional)"
          style={{ ...inp, flex: 1 }} />
        <button onClick={() => name.trim() && addRound.mutate()} disabled={!name.trim() || addRound.isPending}
          style={{ ...btnS, opacity: !name.trim() ? 0.5 : 1, whiteSpace: 'nowrap' as const }}>
          {addRound.isPending ? 'Adding...' : 'Add round'}
        </button>
      </div>
      {error && <div style={{ marginTop: 8, fontSize: 12, color: 'var(--hf-danger-text)' }}>{error}</div>}
    </div>
  )
}

function JobModal({ job, onClose, onSaved }: { job?: Job; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({
    title:           job?.title ?? '',
    department:      job?.department ?? '',
    location:        job?.location ?? '',
    jobType:         job?.jobType ?? 'FULL_TIME',
    experienceLevel: job?.experienceLevel ?? 'MID',
    description:     job?.description ?? '',
    requirements:    job?.requirements ?? '',
    benefits:        job?.benefits ?? '',
    salaryMin:       job?.salaryMin ? String(job.salaryMin) : '',
    salaryMax:       job?.salaryMax ? String(job.salaryMax) : '',
    showSalary:      job?.showSalary ?? false,
    closesAt:        job?.closesAt ?? '',
  })
  const [error, setError] = useState('')
  const f = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const save = useMutation({
    mutationFn: () => {
      const body = {
        title: form.title, department: form.department || null, location: form.location || null,
        jobType: form.jobType, experienceLevel: form.experienceLevel,
        description: form.description, requirements: form.requirements || null,
        benefits: form.benefits || null,
        salaryMin: form.salaryMin ? parseFloat(form.salaryMin) : null,
        salaryMax: form.salaryMax ? parseFloat(form.salaryMax) : null,
        showSalary: form.showSalary,
        closesAt: form.closesAt || null,
      }
      return job
        ? apiClient.put(`/api/v1/recruiter/jobs/${job.id}`, body)
        : apiClient.post('/api/v1/recruiter/jobs', body)
    },
    onSuccess: () => { onSaved(); onClose() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to save job'),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 680, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 22 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{job ? 'Edit Job' : 'Post a Job'}</h3>
            <p style={{ margin: '3px 0 0', fontSize: 13, color: 'var(--hf-text-muted)' }}>New jobs start as Draft — publish when ready to receive applications</p>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={20} /></button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Job title *</label>
            <input autoFocus value={form.title} onChange={e => f('title', e.target.value)} placeholder="Senior Equipment Operator" style={inp} />
          </div>
          <div>
            <label style={lbl}>Department</label>
            <input value={form.department} onChange={e => f('department', e.target.value)} placeholder="Operations" style={inp} />
          </div>
          <div>
            <label style={lbl}>Location</label>
            <input value={form.location} onChange={e => f('location', e.target.value)} placeholder="Pretoria, Gauteng / Remote" style={inp} />
          </div>
          <div>
            <label style={lbl}>Job type</label>
            <select value={form.jobType} onChange={e => f('jobType', e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
              {['FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','FREELANCE'].map(t => <option key={t} value={t}>{t.replace('_',' ')}</option>)}
            </select>
          </div>
          <div>
            <label style={lbl}>Experience level</label>
            <select value={form.experienceLevel} onChange={e => f('experienceLevel', e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
              {['JUNIOR','MID','SENIOR','LEAD','EXECUTIVE'].map(t => <option key={t}>{t}</option>)}
            </select>
          </div>
          <div>
            <label style={lbl}>Salary min (R)</label>
            <input type="number" value={form.salaryMin} onChange={e => f('salaryMin', e.target.value)} placeholder="15 000" style={inp} />
          </div>
          <div>
            <label style={lbl}>Salary max (R)</label>
            <input type="number" value={form.salaryMax} onChange={e => f('salaryMax', e.target.value)} placeholder="25 000" style={inp} />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <input type="checkbox" id="showSalary" checked={form.showSalary} onChange={e => f('showSalary', e.target.checked)} style={{ width: 16, height: 16 }} />
            <label htmlFor="showSalary" style={{ fontSize: 13, color: 'var(--hf-text-secondary)', cursor: 'pointer' }}>Show salary range on careers page</label>
          </div>
          <div>
            <label style={lbl}>Application closes</label>
            <input type="date" value={form.closesAt} onChange={e => f('closesAt', e.target.value)} style={inp} />
          </div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Job description *</label>
            <textarea value={form.description} onChange={e => f('description', e.target.value)} rows={5}
              placeholder="Provide an overview of the role, key responsibilities, and day-to-day activities..." style={{ ...inp, resize: 'vertical' as const, fontFamily: 'inherit' }} />
          </div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Requirements</label>
            <textarea value={form.requirements} onChange={e => f('requirements', e.target.value)} rows={4}
              placeholder="Minimum qualifications, education, certifications, and experience required..." style={{ ...inp, resize: 'vertical' as const, fontFamily: 'inherit' }} />
          </div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Benefits</label>
            <textarea value={form.benefits} onChange={e => f('benefits', e.target.value)} rows={3}
              placeholder="Medical aid, pension, performance bonuses, company vehicle, leave policy..." style={{ ...inp, resize: 'vertical' as const, fontFamily: 'inherit' }} />
          </div>
          {job && <InterviewRoundsSection jobId={job.id} />}
        </div>

        {error && <div style={{ marginTop: 12, padding: '10px 14px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 22 }}>
          <button onClick={onClose} style={btnS}>Cancel</button>
          <button disabled={!form.title || !form.description || save.isPending}
            onClick={() => save.mutate()}
            style={{ ...btnP, opacity: (!form.title || !form.description) ? 0.5 : 1 }}>
            {save.isPending ? 'Saving...' : job ? 'Save changes' : <><Briefcase size={13} /> Create job (Draft)</>}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main Page ──────────────────────────────────────────────────────────────
export function RecruiterPage() {
  const qc = useQueryClient()
  const [tab,           setTab]           = useState<'jobs'|'pipeline'|'applications'>('jobs')
  const [statusFilter,  setStatusFilter]  = useState('')
  const [stageFilter,   setStageFilter]   = useState('')
  const [jobFilter,     setJobFilter]     = useState('')
  const [search,        setSearch]        = useState('')
  const [showCreate,    setShowCreate]    = useState(false)
  const [editJob,       setEditJob]       = useState<Job | null>(null)
  const [selectedApp,   setSelectedApp]   = useState<Application | null>(null)
  const [showDeleteJob, setShowDeleteJob] = useState<Job | null>(null)
  const [showPublish,   setShowPublish]   = useState<Job | null>(null)
  const [compareIds,    setCompareIds]    = useState<string[]>([])
  const [showCompare,   setShowCompare]   = useState(false)

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['rec-jobs'] })
    qc.invalidateQueries({ queryKey: ['rec-summary'] })
    qc.invalidateQueries({ queryKey: ['rec-applications'] })
  }

  const { data: summary } = useQuery<Summary>({
    queryKey: ['rec-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/recruiter/summary'); return r.data?.data ?? r.data },
    refetchInterval: 30_000,
  })

  const { data: jobsPage } = useQuery({
    queryKey: ['rec-jobs', statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: '100' })
      if (statusFilter) params.set('status', statusFilter)
      const r = await apiClient.get(`/api/v1/recruiter/jobs?${params}`)
      return r.data?.data ?? r.data
    },
  })

  const { data: appsPage } = useQuery({
    queryKey: ['rec-applications', stageFilter, jobFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: '200' })
      if (stageFilter) params.set('stage', stageFilter)
      if (jobFilter)   params.set('jobId', jobFilter)
      const r = await apiClient.get(`/api/v1/recruiter/applications?${params}`)
      return r.data?.data ?? r.data
    },
    enabled: tab === 'applications' || tab === 'pipeline',
  })

  const doJobAction = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      apiClient.post(`/api/v1/recruiter/jobs/${id}/action/${action}`),
    onSuccess: () => { invalidate(); setShowPublish(null) },
  })

  const deleteJob = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/recruiter/jobs/${id}`),
    onSuccess: () => { invalidate(); setShowDeleteJob(null) },
  })

  const jobs: Job[] = jobsPage?.content ?? jobsPage ?? []
  const apps: Application[] = appsPage?.content ?? appsPage ?? []

  const filteredJobs = jobs.filter(j =>
    !search || j.title.toLowerCase().includes(search.toLowerCase()) ||
    (j.department ?? '').toLowerCase().includes(search.toLowerCase())
  )
  const filteredApps = apps.filter(a =>
    !search ||
    (a.applicantName ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (a.applicantEmail ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (a.jobTitle ?? '').toLowerCase().includes(search.toLowerCase())
  )

  // Pipeline grouped by stage
  const pipelineGroups = PIPELINE_STAGES.reduce((acc, s) => {
    acc[s] = apps.filter(a => a.stage === s)
    return acc
  }, {} as Record<string, Application[]>)

  const exportCSV = () => {
    const headers = ['Name','Email','Phone','Job','Stage','Score','Source','Applied','CV']
    const rows = filteredApps.map(a => [
      a.applicantName ?? '', a.applicantEmail ?? '', a.applicantPhone ?? '',
      a.jobTitle ?? '', a.stage, a.score ?? '', a.source,
      fmtDate(a.appliedAt), a.hasCv ? 'Yes' : 'No',
    ])
    const csv = [headers, ...rows].map(r => r.join(',')).join('\n')
    const el = document.createElement('a'); el.href = 'data:text/csv;charset=utf-8,' + encodeURIComponent(csv); el.download = 'applications.csv'; el.click()
  }

  const kpis = [
    { label: 'Open positions',  value: summary?.openJobs ?? 0,        color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)', icon: <Briefcase size={16} /> },
    { label: 'New applications',value: summary?.newApplications ?? 0,  color: 'var(--hf-warning-text)', bg: 'var(--hf-warning-soft)', icon: <Users size={16} /> },
    { label: 'In interview',    value: summary?.inInterview ?? 0,      color: 'var(--hf-info-text)', bg: 'var(--hf-info-soft)', icon: <Calendar size={16} /> },
    { label: 'Offers made',     value: summary?.offersMade ?? 0,       color: 'var(--hf-accent-text)', bg: 'var(--hf-accent-soft)', icon: <Send size={16} /> },
    { label: 'Hired this month',value: summary?.hiredThisMonth ?? 0,   color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)', icon: <UserCheck size={16} /> },
    { label: 'Draft jobs',      value: summary?.draftJobs ?? 0,        color: 'var(--hf-text-muted)', bg: 'var(--hf-surface-muted)', icon: <FileText size={16} /> },
  ]

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 22, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--hf-accent)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Briefcase size={18} color="#fff" />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: 'var(--hf-text)', margin: 0 }}>Recruiter</h1>
          </div>
          <p style={{ fontSize: 13, color: 'var(--hf-text-faint)', margin: 0, paddingLeft: 46 }}>
            Job postings · Applicant pipeline · Interviews · HR onboarding
          </p>
        </div>
        <button onClick={() => setShowCreate(true)} style={btnP}><Plus size={14} /> Post job</button>
      </div>

      {/* KPI strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 10, marginBottom: 22 }}>
        {kpis.map(k => (
          <div key={k.label} style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: 8, background: k.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color: k.color, flexShrink: 0 }}>{k.icon}</div>
            <div>
              <div style={{ fontSize: 20, fontWeight: 800, color: k.color }}>{k.value}</div>
              <div style={{ fontSize: 10, color: 'var(--hf-text-faint)' }}>{k.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Main card */}
      <div style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 14 }}>
        {/* Tab bar + toolbar */}
        <div style={{ borderBottom: '1px solid var(--hf-border)', padding: '0 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex' }}>
            {([
              { key: 'jobs',         label: 'Job Postings',  icon: <Briefcase size={13} /> },
              { key: 'pipeline',     label: 'Pipeline',      icon: <BarChart2 size={13} /> },
              { key: 'applications', label: 'Applications',  icon: <Users size={13} /> },
            ] as const).map(t => (
              <button key={t.key} onClick={() => setTab(t.key)}
                style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '14px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: tab === t.key ? 'var(--hf-primary-text)' : 'var(--hf-text-faint)', borderBottom: `2px solid ${tab === t.key ? '#1B3A6B' : 'transparent'}`, marginBottom: -1 }}>
                {t.icon}{t.label}
              </button>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 8, padding: '8px 0' }}>
            {tab === 'applications' && (
              <button onClick={exportCSV} style={btnS}><Download size={13} /> Export</button>
            )}
          </div>
        </div>

        <div style={{ padding: 24 }}>
          {/* Toolbar */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 18 }}>
            <div style={{ position: 'relative' as const }}>
              <Search size={13} style={{ position: 'absolute' as const, left: 9, top: '50%', transform: 'translateY(-50%)', color: 'var(--hf-text-faint)' }} />
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder={tab === 'jobs' ? 'Search jobs...' : 'Search applicants...'}
                style={{ paddingLeft: 28, padding: '7px 10px 7px 28px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, outline: 'none', width: 200 }} />
            </div>
            {tab === 'jobs' && (
              <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
                style={{ padding: '7px 10px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, outline: 'none', background: 'var(--hf-surface)' }}>
                <option value="">All statuses</option>
                {Object.entries(JOB_STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
              </select>
            )}
            {(tab === 'applications') && (
              <>
                <select value={stageFilter} onChange={e => setStageFilter(e.target.value)}
                  style={{ padding: '7px 10px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, outline: 'none', background: 'var(--hf-surface)' }}>
                  <option value="">All stages</option>
                  {Object.entries(STAGE).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                </select>
                <select value={jobFilter} onChange={e => setJobFilter(e.target.value)}
                  style={{ padding: '7px 10px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, outline: 'none', background: 'var(--hf-surface)' }}>
                  <option value="">All jobs</option>
                  {jobs.map(j => <option key={j.id} value={j.id}>{j.title}</option>)}
                </select>
              </>
            )}
            {(search || statusFilter || stageFilter || jobFilter) && (
              <button onClick={() => { setSearch(''); setStatusFilter(''); setStageFilter(''); setJobFilter('') }}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '6px 10px', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 12, background: 'var(--hf-surface-muted)', color: 'var(--hf-text-muted)', cursor: 'pointer' }}>
                <X size={11} /> Clear
              </button>
            )}
            <div style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--hf-text-faint)' }}>
              {tab === 'jobs' ? `${filteredJobs.length} jobs` : `${filteredApps.length} applicants`}
            </div>
          </div>

          {/* ── JOBS TAB ── */}
          {tab === 'jobs' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {filteredJobs.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '60px 20px' }}>
                  <Briefcase size={36} style={{ marginBottom: 12, color: 'var(--hf-text-disabled)' }} />
                  <div style={{ fontWeight: 700, color: 'var(--hf-text-tertiary)', fontSize: 15, marginBottom: 6 }}>No job postings yet</div>
                  <div style={{ fontSize: 13, color: 'var(--hf-text-faint)', marginBottom: 18 }}>Create your first job posting to start receiving applications.</div>
                  <button onClick={() => setShowCreate(true)} style={btnP}><Plus size={14} /> Post first job</button>
                </div>
              ) : filteredJobs.map(job => {
                const cfg = JOB_STATUS[job.status] ?? JOB_STATUS.DRAFT
                const closing = job.closesAt && new Date(job.closesAt) < new Date() && job.status === 'OPEN'
                return (
                  <div key={job.id} style={{ border: `1px solid ${closing ? '#FECACA' : '#E2E8F0'}`, borderRadius: 12, padding: '16px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', background: 'var(--hf-surface)' }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = '0 2px 8px rgba(0,0,0,0.06)'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = 'none'}>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 5 }}>
                        <span style={{ fontWeight: 800, fontSize: 15, color: 'var(--hf-text)' }}>{job.title}</span>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.border}`, padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <span style={{ width: 4, height: 4, borderRadius: '50%', background: cfg.color }} />{cfg.label}
                        </span>
                        {closing && <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}><AlertTriangle size={9} /> Closing date passed</span>}
                      </div>
                      <div style={{ display: 'flex', gap: 14, fontSize: 12, color: 'var(--hf-text-muted)', flexWrap: 'wrap', marginBottom: 8 }}>
                        {job.department && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Building2 size={11} />{job.department}</span>}
                        {job.location && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><MapPin size={11} />{job.location}</span>}
                        <span>{job.jobType.replace('_',' ')} · {job.experienceLevel}</span>
                        {job.showSalary && job.salaryMin && <span style={{ color: 'var(--hf-accent-text)', fontWeight: 600 }}>{fmtR(job.salaryMin)}{job.salaryMax ? ` – ${fmtR(job.salaryMax)}` : '+'}</span>}
                        {job.closesAt && <span>Closes {fmtDate(job.closesAt)}</span>}
                      </div>
                      <div style={{ display: 'flex', gap: 12, fontSize: 12 }}>
                        <span style={{ color: job.applicationCount > 0 ? 'var(--hf-accent-text)' : 'var(--hf-text-faint)', fontWeight: job.applicationCount > 0 ? 700 : 400 }}>
                          {job.applicationCount} application{job.applicationCount !== 1 ? 's' : ''}
                        </span>
                        {job.slug && (
                          <a href={`/careers/zeta-earthmoving/${job.slug}`} target="_blank" rel="noreferrer"
                            style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--hf-primary-text)', fontWeight: 600, textDecoration: 'none' }}>
                            <ExternalLink size={10} /> View posting
                          </a>
                        )}
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 6, flexShrink: 0, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                      {job.status === 'DRAFT' && (
                        <button onClick={() => setShowPublish(job)}
                          style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '6px 12px', background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', border: '1px solid var(--hf-success-border)', borderRadius: 7, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                          <ExternalLink size={11} /> Publish
                        </button>
                      )}
                      {job.status === 'OPEN' && (
                        <>
                          <button onClick={() => doJobAction.mutate({ id: job.id, action: 'PAUSE' })}
                            style={{ padding: '6px 12px', background: 'var(--hf-warning-soft)', color: 'var(--hf-warning-text)', border: '1px solid var(--hf-warning-border)', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>Pause</button>
                          <button onClick={() => doJobAction.mutate({ id: job.id, action: 'FILL' })}
                            style={{ padding: '6px 12px', background: 'var(--hf-accent-soft)', color: 'var(--hf-accent-text)', border: '1px solid var(--hf-accent-border)', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>Mark filled</button>
                        </>
                      )}
                      {job.status === 'PAUSED' && (
                        <button onClick={() => doJobAction.mutate({ id: job.id, action: 'PUBLISH' })}
                          style={{ padding: '6px 12px', background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', border: '1px solid var(--hf-success-border)', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>Resume</button>
                      )}
                      <button onClick={() => setEditJob(job)} style={{ padding: '6px 10px', background: 'var(--hf-info-soft)', color: 'var(--hf-info-text)', border: '1px solid var(--hf-info-border)', borderRadius: 7, fontSize: 12, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}>
                        <Edit3 size={11} /> Edit
                      </button>
                      <button onClick={() => setShowDeleteJob(job)} style={{ padding: '6px 10px', background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', border: '1px solid var(--hf-danger-border)', borderRadius: 7, fontSize: 12, cursor: 'pointer', display: 'flex', alignItems: 'center' }}>
                        <Trash2 size={11} />
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {/* ── PIPELINE TAB (Kanban) ── */}
          {tab === 'pipeline' && (
            <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 16, alignItems: 'flex-start' }}>
              {PIPELINE_STAGES.map(stage => {
                const cfg  = STAGE[stage]
                const col  = pipelineGroups[stage] ?? []
                return (
                  <div key={stage} style={{ minWidth: 230, maxWidth: 230, flexShrink: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 10 }}>
                      <div style={{ width: 8, height: 8, borderRadius: '50%', background: cfg.dot }} />
                      <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--hf-text-secondary)' }}>{cfg.label}</span>
                      <span style={{ background: 'var(--hf-surface-sunken)', color: 'var(--hf-text-muted)', borderRadius: 20, padding: '1px 7px', fontSize: 11, fontWeight: 700 }}>{col.length}</span>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {col.map(a => (
                        <div key={a.id} onClick={() => setSelectedApp(a)}
                          style={{ border: '1px solid var(--hf-border)', borderLeft: `3px solid ${cfg.dot}`, borderRadius: 9, padding: '11px 13px', cursor: 'pointer', background: 'var(--hf-surface)', transition: 'box-shadow 0.15s' }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 12px rgba(0,0,0,0.08)'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = 'none'}>
                          <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--hf-text)', marginBottom: 3 }}>{a.applicantName}</div>
                          <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginBottom: 6 }}>{a.jobTitle}</div>
                          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11 }}>
                            <span style={{ color: 'var(--hf-text-muted)' }}>{fmtDate(a.appliedAt)}</span>
                            {a.score && <span style={{ color: 'var(--hf-warning-text)' }}>{'★'.repeat(a.score)}</span>}
                          </div>
                        </div>
                      ))}
                      {col.length === 0 && (
                        <div style={{ padding: '16px', textAlign: 'center', fontSize: 12, color: 'var(--hf-text-disabled)', border: '1.5px dashed var(--hf-border)', borderRadius: 9 }}>Empty</div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {/* ── APPLICATIONS TAB ── */}
          {tab === 'applications' && (
            filteredApps.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '60px 20px' }}>
                <Users size={36} style={{ marginBottom: 12, color: 'var(--hf-text-disabled)' }} />
                <div style={{ fontWeight: 700, color: 'var(--hf-text-tertiary)', fontSize: 15 }}>No applications found</div>
              </div>
            ) : (
              <div style={{ border: '1px solid var(--hf-border)', borderRadius: 12, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
                  <thead>
                    <tr style={{ background: 'var(--hf-surface-muted)', borderBottom: '1px solid var(--hf-border)' }}>
                      {['', 'Applicant', 'Job', 'Stage', 'Score', 'Source', 'Applied', 'CV', ''].map(h => (
                        <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', letterSpacing: '0.05em' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {filteredApps.map((a, i) => {
                      const sc = STAGE[a.stage] ?? STAGE.APPLIED
                      const checked = compareIds.includes(a.id)
                      return (
                        <tr key={a.id} onClick={() => setSelectedApp(a)}
                          style={{ background: i % 2 === 0 ? 'var(--hf-surface)' : 'var(--hf-surface-muted)', cursor: 'pointer', transition: 'background 0.1s' }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = 'var(--hf-sky-soft)'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = i % 2 === 0 ? 'var(--hf-surface)' : 'var(--hf-surface-muted)'}>
                          <td style={{ padding: '12px 8px 12px 16px' }} onClick={e => e.stopPropagation()}>
                            <input type="checkbox" checked={checked}
                              onChange={() => setCompareIds(prev => checked ? prev.filter(id => id !== a.id) : [...prev, a.id])}
                              style={{ width: 15, height: 15, cursor: 'pointer' }} />
                          </td>
                          <td style={{ padding: '12px 16px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
                              <div style={{ width: 28, height: 28, borderRadius: '50%', background: sc.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                                <span style={{ fontSize: 10, fontWeight: 700, color: sc.color }}>{(a.applicantName ?? 'A').charAt(0).toUpperCase()}</span>
                              </div>
                              <div>
                                <div style={{ fontWeight: 700, color: 'var(--hf-text)' }}>{a.applicantName}</div>
                                <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{a.applicantEmail}</div>
                              </div>
                            </div>
                          </td>
                          <td style={{ padding: '12px 16px', color: 'var(--hf-text-secondary)' }}>{a.jobTitle}</td>
                          <td style={{ padding: '12px 16px' }}>
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                              <span style={{ width: 4, height: 4, borderRadius: '50%', background: sc.dot }} />{sc.label}
                            </span>
                          </td>
                          <td style={{ padding: '12px 16px', color: 'var(--hf-warning-text)', fontWeight: 700 }}>
                            {a.score ? '★'.repeat(a.score) : <span style={{ color: 'var(--hf-text-disabled)' }}>—</span>}
                          </td>
                          <td style={{ padding: '12px 16px', fontSize: 12, color: 'var(--hf-text-muted)' }}>{a.source?.replace('_',' ') ?? '—'}</td>
                          <td style={{ padding: '12px 16px', fontSize: 12, color: 'var(--hf-text-faint)' }}>{fmtDate(a.appliedAt)}</td>
                          <td style={{ padding: '12px 16px' }}>
                            {a.hasCv ? <CheckCircle size={13} color="#0D9488" /> : <span style={{ color: 'var(--hf-text-disabled)', fontSize: 11 }}>—</span>}
                          </td>
                          <td style={{ padding: '12px 16px' }}><ChevronRight size={14} color="#94A3B8" /></td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )
          )}
        </div>
      </div>

      {compareIds.length > 0 && (
        <div style={{ position: 'fixed', bottom: 24, left: '50%', transform: 'translateX(-50%)', background: 'var(--hf-inverse-surface)', color: 'var(--hf-text-on-solid)', borderRadius: 12, padding: '12px 16px', display: 'flex', alignItems: 'center', gap: 14, boxShadow: '0 10px 40px rgba(0,0,0,0.3)', zIndex: 900 }}>
          <span style={{ fontSize: 13, fontWeight: 600 }}>{compareIds.length} candidate{compareIds.length === 1 ? '' : 's'} selected</span>
          <button onClick={() => setShowCompare(true)} disabled={compareIds.length < 2}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 14px', background: compareIds.length < 2 ? '#334155' : 'var(--hf-surface)', color: compareIds.length < 2 ? 'var(--hf-text-faint)' : 'var(--hf-text)', border: 'none', borderRadius: 7, fontSize: 12, fontWeight: 700, cursor: compareIds.length < 2 ? 'default' : 'pointer' }}>
            <Users size={13} /> Compare
          </button>
          <button onClick={() => setCompareIds([])} style={{ background: 'none', border: 'none', color: 'var(--hf-text-faint)', cursor: 'pointer', display: 'flex' }}>
            <X size={16} />
          </button>
        </div>
      )}

      {showCompare && (
        <CompareModal applicationIds={compareIds} onClose={() => setShowCompare(false)} />
      )}

      {/* Modals */}
      {(showCreate || editJob) && (
        <JobModal job={editJob ?? undefined} onClose={() => { setShowCreate(false); setEditJob(null) }} onSaved={invalidate} />
      )}

      {showPublish && (
        <ConfirmModal title={`Publish "${showPublish.title}"?`}
          message="This job will be listed on your public careers page and will start accepting applications immediately."
          confirmLabel="Publish job" loading={doJobAction.isPending}
          onConfirm={() => doJobAction.mutate({ id: showPublish.id, action: 'PUBLISH' })}
          onCancel={() => setShowPublish(null)} />
      )}

      {showDeleteJob && (
        <ConfirmModal title={`Delete "${showDeleteJob.title}"?`}
          message="This job and all its applications will be permanently removed. This cannot be undone."
          danger confirmLabel="Delete job" loading={deleteJob.isPending}
          onConfirm={() => deleteJob.mutate(showDeleteJob.id)}
          onCancel={() => setShowDeleteJob(null)} />
      )}

      {selectedApp && (
        <ApplicationDetail app={selectedApp} onClose={() => setSelectedApp(null)} onUpdated={invalidate} />
      )}
    </div>
  )
}
