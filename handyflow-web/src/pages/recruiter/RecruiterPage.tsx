// src/pages/recruiter/RecruiterPage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
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
  interviews: Interview[]; stageHistory: StageHistory[]
  appliedAt: string; stageChangedAt: string; hiredAt: string | null
}
interface Interview {
  id: string; interviewType: string; scheduledAt: string | null
  interviewerName: string | null; outcome: string | null
  notes: string | null; score: number | null; createdAt: string
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
  APPLIED:    { color: '#64748B', bg: '#F8FAFC', border: '#E2E8F0', dot: '#CBD5E1', label: 'Applied'    },
  SCREENING:  { color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', dot: '#F59E0B', label: 'Screening'  },
  INTERVIEW:  { color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE', dot: '#60A5FA', label: 'Interview'  },
  ASSESSMENT: { color: '#7C3AED', bg: '#F5F3FF', border: '#DDD6FE', dot: '#A78BFA', label: 'Assessment' },
  OFFER:      { color: '#0D9488', bg: '#F0FDF9', border: '#99F6E4', dot: '#2DD4BF', label: 'Offer'      },
  HIRED:      { color: '#166534', bg: '#DCFCE7', border: '#86EFAC', dot: '#22C55E', label: 'Hired'      },
  REJECTED:   { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', dot: '#EF4444', label: 'Rejected'   },
  WITHDRAWN:  { color: '#94A3B8', bg: '#F8FAFC', border: '#E2E8F0', dot: '#CBD5E1', label: 'Withdrawn'  },
}
const JOB_STATUS: Record<string, { color: string; bg: string; border: string; label: string }> = {
  DRAFT:  { color: '#64748B', bg: '#F8FAFC', border: '#E2E8F0', label: 'Draft'  },
  OPEN:   { color: '#166534', bg: '#DCFCE7', border: '#86EFAC', label: 'Open'   },
  PAUSED: { color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', label: 'Paused' },
  CLOSED: { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', label: 'Closed' },
  FILLED: { color: '#0D9488', bg: '#F0FDF9', border: '#99F6E4', label: 'Filled' },
}
const INTERVIEW_TYPE_ICON: Record<string, any> = {
  PHONE: Phone, VIDEO: Video, IN_PERSON: Users, TECHNICAL: Monitor, PANEL: Mic,
}
const PIPELINE_STAGES = ['APPLIED','SCREENING','INTERVIEW','ASSESSMENT','OFFER','HIRED']

// ── Helpers ────────────────────────────────────────────────────────────────
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }
const btnP: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const btnS: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, padding: '9px 14px', border: '1.5px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 13, cursor: 'pointer', color: '#374151', fontWeight: 500 }

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
          style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 22, color: i <= (hover || value || 0) ? '#F59E0B' : '#E2E8F0', padding: '0 1px', lineHeight: 1 }}>
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
      <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 440, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ display: 'flex', gap: 14, marginBottom: 20 }}>
          <div style={{ width: 40, height: 40, borderRadius: '50%', background: danger ? '#FEF2F2' : '#DCFCE7', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            {danger ? <AlertTriangle size={18} color="#DC2626" /> : <CheckCircle size={18} color="#166534" />}
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>{message}</div>
          </div>
        </div>
        {children}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 16 }}>
          <button onClick={onCancel} style={btnS}>Cancel</button>
          <button onClick={onConfirm} disabled={loading}
            style={{ ...btnP, background: danger ? '#DC2626' : '#1B3A6B', opacity: loading ? 0.6 : 1 }}>
            {confirmLabel}
          </button>
        </div>
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
  const [showConvert,   setShowConvert]   = useState(false)
  const [showReject,    setShowReject]    = useState(false)
  const [targetStage, setTargetStage]     = useState('')
  const [stageNotes,  setStageNotes]      = useState('')
  const [rejectReason, setRejectReason]   = useState('')
  const [scoreVal,    setScoreVal]        = useState<number | null>(initial.score)
  const [notes,       setNotes]           = useState(initial.notes ?? '')
  const [ivType,      setIvType]          = useState('VIDEO')
  const [ivScheduled, setIvScheduled]     = useState('')
  const [ivInterviewer, setIvInterviewer] = useState('')
  const [startDate,   setStartDate]       = useState(new Date().toISOString().split('T')[0])
  const [jobTitle,    setJobTitle]        = useState(initial.jobTitle ?? '')
  const [department,  setDepartment]      = useState('')
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

  const moveStage = useMutation({
    mutationFn: ({ stage, notes, reason }: any) =>
      apiClient.post(`/api/v1/recruiter/applications/${app!.id}/stage`, {
        stage, notes: notes || null, rejectionReason: reason || null,
      }),
    onSuccess: () => { invalidate(); setShowMoveModal(false); setShowReject(false); setStageNotes(''); setRejectReason('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
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
      interviewerName: ivInterviewer || null,
    }),
    onSuccess: () => { invalidate(); setShowInterview(false); setIvScheduled(''); setIvInterviewer('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed'),
  })

  const convertToEmployee = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/recruiter/applications/${app!.id}/convert-to-employee`, {
      startDate, jobTitle, department: department || null,
    }),
    onSuccess: () => { invalidate(); setShowConvert(false) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to convert'),
  })

  const sc   = STAGE[app?.stage ?? 'APPLIED'] ?? STAGE.APPLIED
  const availableStages = PIPELINE_STAGES.filter(s => s !== app?.stage)

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'stretch', justifyContent: 'flex-end', zIndex: 1000 }}>
      <div style={{ background: '#fff', width: 600, height: '100%', overflowY: 'auto', boxShadow: '-8px 0 40px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column' }}>

        {/* Header */}
        <div style={{ padding: '20px 24px 0', borderBottom: '1px solid #F1F5F9', flexShrink: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
            <div>
              <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: '#0F172A', marginBottom: 5 }}>{app?.applicantName}</h2>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                  <span style={{ width: 5, height: 5, borderRadius: '50%', background: sc.dot }} />{sc.label}
                </span>
                <span style={{ fontSize: 12, color: '#64748B' }}>{app?.jobTitle}</span>
                {app?.hrEmployeeId && (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#DCFCE7', color: '#166534', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                    <UserCheck size={10} /> Onboarded to HR
                  </span>
                )}
              </div>
            </div>
            <button onClick={onClose} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#64748B', flexShrink: 0 }}>
              <X size={14} />
            </button>
          </div>

          {/* Contact strip */}
          <div style={{ display: 'flex', gap: 16, fontSize: 12, color: '#64748B', marginBottom: 12, flexWrap: 'wrap' }}>
            {app?.applicantEmail && <span>{app.applicantEmail}</span>}
            {app?.applicantPhone && <span>{app.applicantPhone}</span>}
            <span>Applied {fmtDate(app?.appliedAt)}</span>
            <span>Source: {app?.source?.replace('_',' ') ?? '—'}</span>
          </div>

          {/* Action buttons */}
          <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap', marginBottom: 14 }}>
            {availableStages.filter(s => !['REJECTED'].includes(s)).map(s => {
              const cfg = STAGE[s]
              return (
                <button key={s} onClick={() => { setTargetStage(s); setShowMoveModal(true) }}
                  style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.border}`, borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                  <ChevronRight size={10} />{cfg.label}
                </button>
              )
            })}
            {app?.stage !== 'REJECTED' && app?.stage !== 'WITHDRAWN' && (
              <button onClick={() => setShowReject(true)}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: '#FEF2F2', color: '#DC2626', border: '1px solid #FECACA', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                <X size={10} /> Reject
              </button>
            )}
            <button onClick={() => setShowInterview(true)}
              style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: '#EFF6FF', color: '#1D4ED8', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
              <Calendar size={10} /> Schedule interview
            </button>
            {app?.stage === 'HIRED' && !app?.hrEmployeeId && (
              <button onClick={() => setShowConvert(true)}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: '#DCFCE7', color: '#166534', border: '1px solid #86EFAC', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                <UserPlus size={10} /> Convert to employee
              </button>
            )}
            {app?.hasCv && (
              <button style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 12px', background: '#F5F3FF', color: '#7C3AED', border: '1px solid #DDD6FE', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                <FileText size={10} /> View CV
              </button>
            )}
          </div>

          {/* Tabs */}
          <div style={{ display: 'flex' }}>
            {(['overview','interviews','history'] as const).map(t => (
              <button key={t} onClick={() => setTab(t)}
                style={{ padding: '8px 16px', fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: tab === t ? '#1B3A6B' : '#9CA3AF', borderBottom: `2px solid ${tab === t ? '#1B3A6B' : 'transparent'}`, marginBottom: -1, textTransform: 'capitalize' }}>
                {t === 'interviews' ? `Interviews (${app?.interviews?.length ?? 0})` : t === 'history' ? `History (${app?.stageHistory?.length ?? 0})` : 'Overview'}
              </button>
            ))}
          </div>
        </div>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px 28px' }}>
          {error && <div style={{ marginBottom: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

          {tab === 'overview' && (
            <div>
              {/* Score */}
              <div style={{ marginBottom: 18, padding: '14px 16px', background: '#F8FAFC', borderRadius: 10, border: '1px solid #E2E8F0' }}>
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
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #F1F5F9', fontSize: 13 }}>
                  <span style={{ color: '#94A3B8', fontWeight: 600 }}>{k}</span>
                  <span style={{ color: '#374151', fontWeight: 500 }}>{v as string}</span>
                </div>
              ))}

              {app?.rejectionReason && (
                <div style={{ marginTop: 14, padding: '12px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 9 }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: '#DC2626', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>Rejection reason</div>
                  <div style={{ fontSize: 13, color: '#374151' }}>{app.rejectionReason}</div>
                </div>
              )}
            </div>
          )}

          {tab === 'interviews' && (
            <div>
              <button onClick={() => setShowInterview(true)} style={{ ...btnP, marginBottom: 16, fontSize: 12 }}>
                <Calendar size={13} /> Schedule interview
              </button>
              {(app?.interviews ?? []).length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: '#94A3B8', border: '1.5px dashed #E2E8F0', borderRadius: 12 }}>
                  <Calendar size={28} style={{ marginBottom: 10, opacity: 0.4 }} />
                  <div style={{ fontWeight: 600, color: '#475569' }}>No interviews scheduled</div>
                </div>
              ) : (app?.interviews ?? []).map((iv: Interview) => {
                const Icon = INTERVIEW_TYPE_ICON[iv.interviewType] ?? Video
                const outcomeColor = iv.outcome === 'PASSED' ? '#166534' : iv.outcome === 'FAILED' ? '#DC2626' : '#D97706'
                return (
                  <div key={iv.id} style={{ border: '1px solid #E2E8F0', borderRadius: 10, padding: '14px 16px', marginBottom: 10 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{ width: 30, height: 30, borderRadius: 7, background: '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          <Icon size={14} color="#1D4ED8" />
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 13, color: '#0F172A' }}>{iv.interviewType.replace('_',' ')}</div>
                          <div style={{ fontSize: 11, color: '#94A3B8' }}>
                            {iv.scheduledAt ? fmtDT(iv.scheduledAt) : 'Not scheduled'}
                            {iv.interviewerName && ` · ${iv.interviewerName}`}
                          </div>
                        </div>
                      </div>
                      {iv.outcome && (
                        <span style={{ fontSize: 11, fontWeight: 700, color: outcomeColor }}>{iv.outcome}</span>
                      )}
                    </div>
                    {iv.score && <div style={{ fontSize: 13, color: '#F59E0B' }}>{'★'.repeat(iv.score)}{'☆'.repeat(5 - iv.score)}</div>}
                    {iv.notes && <div style={{ fontSize: 12, color: '#64748B', marginTop: 6 }}>{iv.notes}</div>}
                  </div>
                )
              })}
            </div>
          )}

          {tab === 'history' && (
            <div>
              {(app?.stageHistory ?? []).length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: '#94A3B8' }}>No stage history</div>
              ) : (app?.stageHistory ?? []).map((h: StageHistory, i: number) => {
                const toStage = STAGE[h.toStage] ?? STAGE.APPLIED
                return (
                  <div key={i} style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                      <div style={{ width: 10, height: 10, borderRadius: '50%', background: toStage.dot, marginTop: 4, flexShrink: 0 }} />
                      {i < (app?.stageHistory?.length ?? 0) - 1 && <div style={{ width: 1, flex: 1, background: '#E2E8F0', marginTop: 4 }} />}
                    </div>
                    <div style={{ flex: 1, paddingBottom: 12 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
                        <span style={{ fontWeight: 700, fontSize: 13, color: '#0F172A' }}>
                          {h.fromStage ? `${STAGE[h.fromStage]?.label ?? h.fromStage} → ` : ''}{toStage.label}
                        </span>
                        <span style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDT(h.createdAt)}</span>
                      </div>
                      {h.changedByName && <div style={{ fontSize: 12, color: '#64748B' }}>by {h.changedByName}</div>}
                      {h.notes && <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 3 }}>{h.notes}</div>}
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
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Move to {STAGE[targetStage]?.label}</h3>
              <button onClick={() => setShowMoveModal(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div>
              <label style={lbl}>Notes (optional)</label>
              <textarea value={stageNotes} onChange={e => setStageNotes(e.target.value)} rows={3}
                placeholder="Add context about this stage move..."
                style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit', marginBottom: 12 }} />
              {targetStage === 'HIRED' && (
                <div style={{ padding: '10px 12px', background: '#DCFCE7', border: '1px solid #86EFAC', borderRadius: 8, fontSize: 12, color: '#166534', marginBottom: 12 }}>
                  Once marked Hired, you can convert this applicant to an HR employee record.
                </div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowMoveModal(false)} style={btnS}>Cancel</button>
              <button onClick={() => moveStage.mutate({ stage: targetStage, notes: stageNotes })} disabled={moveStage.isPending}
                style={{ ...btnP, background: STAGE[targetStage]?.color ?? '#1B3A6B' }}>
                {moveStage.isPending ? 'Moving...' : `Move to ${STAGE[targetStage]?.label}`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {showReject && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', gap: 14, marginBottom: 20 }}>
              <div style={{ width: 40, height: 40, borderRadius: '50%', background: '#FEF2F2', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <X size={18} color="#DC2626" />
              </div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 4 }}>Reject candidate</div>
                <div style={{ fontSize: 13, color: '#64748B' }}>A polite rejection email will be sent to the applicant.</div>
              </div>
            </div>
            <div>
              <label style={lbl}>Rejection reason (optional)</label>
              <textarea value={rejectReason} onChange={e => setRejectReason(e.target.value)} rows={3} autoFocus
                placeholder="After careful consideration, we have decided to move forward with other candidates whose experience more closely matches the role requirements."
                style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit' }} />
              <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 5 }}>This reason will be included in the rejection email.</div>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowReject(false)} style={btnS}>Cancel</button>
              <button onClick={() => moveStage.mutate({ stage: 'REJECTED', notes: null, reason: rejectReason })} disabled={moveStage.isPending}
                style={{ ...btnP, background: '#DC2626' }}>
                {moveStage.isPending ? 'Sending...' : 'Reject and notify'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Schedule Interview Modal */}
      {showInterview && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Schedule Interview</h3>
              <button onClick={() => setShowInterview(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={lbl}>Interview type</label>
                <select value={ivType} onChange={e => setIvType(e.target.value)} style={{ ...inp, background: '#fff' }}>
                  {['PHONE','VIDEO','IN_PERSON','TECHNICAL','PANEL'].map(t => <option key={t} value={t}>{t.replace('_',' ')}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Date & time</label>
                <input type="datetime-local" value={ivScheduled} onChange={e => setIvScheduled(e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Interviewer name</label>
                <input value={ivInterviewer} onChange={e => setIvInterviewer(e.target.value)} placeholder="Thabo Modise" style={inp} />
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

      {/* Convert to HR Employee Modal */}
      {showConvert && (
        <ConfirmModal title="Convert to HR employee" message={`Create an HR employee record for ${app?.applicantName}? Their profile will be pre-filled from their application.`}
          confirmLabel="Create employee record" loading={convertToEmployee.isPending}
          onConfirm={() => convertToEmployee.mutate()} onCancel={() => setShowConvert(false)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 14 }}>
            <div><label style={lbl}>Job title</label><input value={jobTitle} onChange={e => setJobTitle(e.target.value)} placeholder={initial.jobTitle ?? ''} style={inp} /></div>
            <div><label style={lbl}>Department</label><input value={department} onChange={e => setDepartment(e.target.value)} placeholder="Operations" style={inp} /></div>
            <div><label style={lbl}>Start date *</label><input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} style={inp} /></div>
          </div>
          {error && <div style={{ padding: '8px 12px', background: '#FEF2F2', borderRadius: 8, fontSize: 13, color: '#DC2626', marginBottom: 10 }}>{error}</div>}
        </ConfirmModal>
      )}
    </div>
  )
}

// ── Create / Edit Job Modal ────────────────────────────────────────────────
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
      <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 680, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 22 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{job ? 'Edit Job' : 'Post a Job'}</h3>
            <p style={{ margin: '3px 0 0', fontSize: 13, color: '#64748B' }}>New jobs start as Draft — publish when ready to receive applications</p>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
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
            <select value={form.jobType} onChange={e => f('jobType', e.target.value)} style={{ ...inp, background: '#fff' }}>
              {['FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','FREELANCE'].map(t => <option key={t} value={t}>{t.replace('_',' ')}</option>)}
            </select>
          </div>
          <div>
            <label style={lbl}>Experience level</label>
            <select value={form.experienceLevel} onChange={e => f('experienceLevel', e.target.value)} style={{ ...inp, background: '#fff' }}>
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
            <label htmlFor="showSalary" style={{ fontSize: 13, color: '#374151', cursor: 'pointer' }}>Show salary range on careers page</label>
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
        </div>

        {error && <div style={{ marginTop: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

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
    { label: 'Open positions',  value: summary?.openJobs ?? 0,        color: '#166534', bg: '#DCFCE7', icon: <Briefcase size={16} /> },
    { label: 'New applications',value: summary?.newApplications ?? 0,  color: '#D97706', bg: '#FFFBEB', icon: <Users size={16} /> },
    { label: 'In interview',    value: summary?.inInterview ?? 0,      color: '#1D4ED8', bg: '#EFF6FF', icon: <Calendar size={16} /> },
    { label: 'Offers made',     value: summary?.offersMade ?? 0,       color: '#0D9488', bg: '#F0FDF9', icon: <Send size={16} /> },
    { label: 'Hired this month',value: summary?.hiredThisMonth ?? 0,   color: '#166534', bg: '#DCFCE7', icon: <UserCheck size={16} /> },
    { label: 'Draft jobs',      value: summary?.draftJobs ?? 0,        color: '#64748B', bg: '#F8FAFC', icon: <FileText size={16} /> },
  ]

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 22, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: '#0D9488', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Briefcase size={18} color="#fff" />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: 0 }}>Recruiter</h1>
          </div>
          <p style={{ fontSize: 13, color: '#94A3B8', margin: 0, paddingLeft: 46 }}>
            Job postings · Applicant pipeline · Interviews · HR onboarding
          </p>
        </div>
        <button onClick={() => setShowCreate(true)} style={btnP}><Plus size={14} /> Post job</button>
      </div>

      {/* KPI strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 10, marginBottom: 22 }}>
        {kpis.map(k => (
          <div key={k.label} style={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 12, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: 8, background: k.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color: k.color, flexShrink: 0 }}>{k.icon}</div>
            <div>
              <div style={{ fontSize: 20, fontWeight: 800, color: k.color }}>{k.value}</div>
              <div style={{ fontSize: 10, color: '#9CA3AF' }}>{k.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Main card */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14 }}>
        {/* Tab bar + toolbar */}
        <div style={{ borderBottom: '1px solid #E2E8F0', padding: '0 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex' }}>
            {([
              { key: 'jobs',         label: 'Job Postings',  icon: <Briefcase size={13} /> },
              { key: 'pipeline',     label: 'Pipeline',      icon: <BarChart2 size={13} /> },
              { key: 'applications', label: 'Applications',  icon: <Users size={13} /> },
            ] as const).map(t => (
              <button key={t.key} onClick={() => setTab(t.key)}
                style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '14px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: tab === t.key ? '#1B3A6B' : '#9CA3AF', borderBottom: `2px solid ${tab === t.key ? '#1B3A6B' : 'transparent'}`, marginBottom: -1 }}>
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
              <Search size={13} style={{ position: 'absolute' as const, left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }} />
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder={tab === 'jobs' ? 'Search jobs...' : 'Search applicants...'}
                style={{ paddingLeft: 28, padding: '7px 10px 7px 28px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', width: 200 }} />
            </div>
            {tab === 'jobs' && (
              <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
                style={{ padding: '7px 10px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
                <option value="">All statuses</option>
                {Object.entries(JOB_STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
              </select>
            )}
            {(tab === 'applications') && (
              <>
                <select value={stageFilter} onChange={e => setStageFilter(e.target.value)}
                  style={{ padding: '7px 10px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
                  <option value="">All stages</option>
                  {Object.entries(STAGE).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                </select>
                <select value={jobFilter} onChange={e => setJobFilter(e.target.value)}
                  style={{ padding: '7px 10px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
                  <option value="">All jobs</option>
                  {jobs.map(j => <option key={j.id} value={j.id}>{j.title}</option>)}
                </select>
              </>
            )}
            {(search || statusFilter || stageFilter || jobFilter) && (
              <button onClick={() => { setSearch(''); setStatusFilter(''); setStageFilter(''); setJobFilter('') }}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '6px 10px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, background: '#F8FAFC', color: '#64748B', cursor: 'pointer' }}>
                <X size={11} /> Clear
              </button>
            )}
            <div style={{ marginLeft: 'auto', fontSize: 12, color: '#94A3B8' }}>
              {tab === 'jobs' ? `${filteredJobs.length} jobs` : `${filteredApps.length} applicants`}
            </div>
          </div>

          {/* ── JOBS TAB ── */}
          {tab === 'jobs' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {filteredJobs.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '60px 20px' }}>
                  <Briefcase size={36} style={{ marginBottom: 12, color: '#CBD5E1' }} />
                  <div style={{ fontWeight: 700, color: '#475569', fontSize: 15, marginBottom: 6 }}>No job postings yet</div>
                  <div style={{ fontSize: 13, color: '#94A3B8', marginBottom: 18 }}>Create your first job posting to start receiving applications.</div>
                  <button onClick={() => setShowCreate(true)} style={btnP}><Plus size={14} /> Post first job</button>
                </div>
              ) : filteredJobs.map(job => {
                const cfg = JOB_STATUS[job.status] ?? JOB_STATUS.DRAFT
                const closing = job.closesAt && new Date(job.closesAt) < new Date() && job.status === 'OPEN'
                return (
                  <div key={job.id} style={{ border: `1px solid ${closing ? '#FECACA' : '#E2E8F0'}`, borderRadius: 12, padding: '16px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', background: '#fff' }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = '0 2px 8px rgba(0,0,0,0.06)'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = 'none'}>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 5 }}>
                        <span style={{ fontWeight: 800, fontSize: 15, color: '#0F172A' }}>{job.title}</span>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.border}`, padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <span style={{ width: 4, height: 4, borderRadius: '50%', background: cfg.color }} />{cfg.label}
                        </span>
                        {closing && <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#FEF2F2', color: '#DC2626', padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}><AlertTriangle size={9} /> Closing date passed</span>}
                      </div>
                      <div style={{ display: 'flex', gap: 14, fontSize: 12, color: '#64748B', flexWrap: 'wrap', marginBottom: 8 }}>
                        {job.department && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Building2 size={11} />{job.department}</span>}
                        {job.location && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><MapPin size={11} />{job.location}</span>}
                        <span>{job.jobType.replace('_',' ')} · {job.experienceLevel}</span>
                        {job.showSalary && job.salaryMin && <span style={{ color: '#0D9488', fontWeight: 600 }}>{fmtR(job.salaryMin)}{job.salaryMax ? ` – ${fmtR(job.salaryMax)}` : '+'}</span>}
                        {job.closesAt && <span>Closes {fmtDate(job.closesAt)}</span>}
                      </div>
                      <div style={{ display: 'flex', gap: 12, fontSize: 12 }}>
                        <span style={{ color: job.applicationCount > 0 ? '#0D9488' : '#94A3B8', fontWeight: job.applicationCount > 0 ? 700 : 400 }}>
                          {job.applicationCount} application{job.applicationCount !== 1 ? 's' : ''}
                        </span>
                        {job.slug && (
                          <a href={`/careers/zeta-earthmoving/${job.slug}`} target="_blank" rel="noreferrer"
                            style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#1B3A6B', fontWeight: 600, textDecoration: 'none' }}>
                            <ExternalLink size={10} /> View posting
                          </a>
                        )}
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 6, flexShrink: 0, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                      {job.status === 'DRAFT' && (
                        <button onClick={() => setShowPublish(job)}
                          style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '6px 12px', background: '#DCFCE7', color: '#166534', border: '1px solid #86EFAC', borderRadius: 7, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                          <ExternalLink size={11} /> Publish
                        </button>
                      )}
                      {job.status === 'OPEN' && (
                        <>
                          <button onClick={() => doJobAction.mutate({ id: job.id, action: 'PAUSE' })}
                            style={{ padding: '6px 12px', background: '#FFFBEB', color: '#D97706', border: '1px solid #FDE68A', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>Pause</button>
                          <button onClick={() => doJobAction.mutate({ id: job.id, action: 'FILL' })}
                            style={{ padding: '6px 12px', background: '#F0FDF9', color: '#0D9488', border: '1px solid #99F6E4', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>Mark filled</button>
                        </>
                      )}
                      {job.status === 'PAUSED' && (
                        <button onClick={() => doJobAction.mutate({ id: job.id, action: 'PUBLISH' })}
                          style={{ padding: '6px 12px', background: '#DCFCE7', color: '#166534', border: '1px solid #86EFAC', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>Resume</button>
                      )}
                      <button onClick={() => setEditJob(job)} style={{ padding: '6px 10px', background: '#EFF6FF', color: '#1D4ED8', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 12, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}>
                        <Edit3 size={11} /> Edit
                      </button>
                      <button onClick={() => setShowDeleteJob(job)} style={{ padding: '6px 10px', background: '#FEF2F2', color: '#DC2626', border: '1px solid #FECACA', borderRadius: 7, fontSize: 12, cursor: 'pointer', display: 'flex', alignItems: 'center' }}>
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
                      <span style={{ fontSize: 12, fontWeight: 700, color: '#374151' }}>{cfg.label}</span>
                      <span style={{ background: '#F1F5F9', color: '#64748B', borderRadius: 20, padding: '1px 7px', fontSize: 11, fontWeight: 700 }}>{col.length}</span>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {col.map(a => (
                        <div key={a.id} onClick={() => setSelectedApp(a)}
                          style={{ border: '1px solid #E5E7EB', borderLeft: `3px solid ${cfg.dot}`, borderRadius: 9, padding: '11px 13px', cursor: 'pointer', background: '#fff', transition: 'box-shadow 0.15s' }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 12px rgba(0,0,0,0.08)'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = 'none'}>
                          <div style={{ fontWeight: 700, fontSize: 13, color: '#111827', marginBottom: 3 }}>{a.applicantName}</div>
                          <div style={{ fontSize: 11, color: '#94A3B8', marginBottom: 6 }}>{a.jobTitle}</div>
                          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11 }}>
                            <span style={{ color: '#64748B' }}>{fmtDate(a.appliedAt)}</span>
                            {a.score && <span style={{ color: '#F59E0B' }}>{'★'.repeat(a.score)}</span>}
                          </div>
                        </div>
                      ))}
                      {col.length === 0 && (
                        <div style={{ padding: '16px', textAlign: 'center', fontSize: 12, color: '#D1D5DB', border: '1.5px dashed #E5E7EB', borderRadius: 9 }}>Empty</div>
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
                <Users size={36} style={{ marginBottom: 12, color: '#CBD5E1' }} />
                <div style={{ fontWeight: 700, color: '#475569', fontSize: 15 }}>No applications found</div>
              </div>
            ) : (
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
                  <thead>
                    <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                      {['Applicant', 'Job', 'Stage', 'Score', 'Source', 'Applied', 'CV', ''].map(h => (
                        <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#64748B', letterSpacing: '0.05em' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {filteredApps.map((a, i) => {
                      const sc = STAGE[a.stage] ?? STAGE.APPLIED
                      return (
                        <tr key={a.id} onClick={() => setSelectedApp(a)}
                          style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA', cursor: 'pointer', transition: 'background 0.1s' }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#F0F9FF'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = i % 2 === 0 ? '#fff' : '#FAFAFA'}>
                          <td style={{ padding: '12px 16px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
                              <div style={{ width: 28, height: 28, borderRadius: '50%', background: sc.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                                <span style={{ fontSize: 10, fontWeight: 700, color: sc.color }}>{(a.applicantName ?? 'A').charAt(0).toUpperCase()}</span>
                              </div>
                              <div>
                                <div style={{ fontWeight: 700, color: '#0F172A' }}>{a.applicantName}</div>
                                <div style={{ fontSize: 11, color: '#94A3B8' }}>{a.applicantEmail}</div>
                              </div>
                            </div>
                          </td>
                          <td style={{ padding: '12px 16px', color: '#374151' }}>{a.jobTitle}</td>
                          <td style={{ padding: '12px 16px' }}>
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                              <span style={{ width: 4, height: 4, borderRadius: '50%', background: sc.dot }} />{sc.label}
                            </span>
                          </td>
                          <td style={{ padding: '12px 16px', color: '#F59E0B', fontWeight: 700 }}>
                            {a.score ? '★'.repeat(a.score) : <span style={{ color: '#CBD5E1' }}>—</span>}
                          </td>
                          <td style={{ padding: '12px 16px', fontSize: 12, color: '#64748B' }}>{a.source?.replace('_',' ') ?? '—'}</td>
                          <td style={{ padding: '12px 16px', fontSize: 12, color: '#94A3B8' }}>{fmtDate(a.appliedAt)}</td>
                          <td style={{ padding: '12px 16px' }}>
                            {a.hasCv ? <CheckCircle size={13} color="#0D9488" /> : <span style={{ color: '#CBD5E1', fontSize: 11 }}>—</span>}
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
