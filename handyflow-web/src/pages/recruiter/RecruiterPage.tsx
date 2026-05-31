import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, UserCheck, Briefcase, ChevronRight, X, Users } from 'lucide-react'

interface Job {
  id: string; title: string; department: string | null; location: string | null
  jobType: string; experienceLevel: string; status: string; slug: string
  applicationCount: number; closesAt: string | null; createdAt: string
}
interface Application {
  id: string; jobId: string; jobTitle: string; applicantName: string
  applicantEmail: string; hasCv: boolean; stage: string; source: string
  score: number | null; appliedAt: string; stageChangedAt: string
}

const STAGE_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  APPLIED:    { color: '#64748B', bg: '#F8FAFC', label: 'Applied'    },
  SCREENING:  { color: '#D97706', bg: '#FFFBEB', label: 'Screening'  },
  INTERVIEW:  { color: '#1D4ED8', bg: '#EFF6FF', label: 'Interview'  },
  ASSESSMENT: { color: '#7C3AED', bg: '#F3E8FF', label: 'Assessment' },
  OFFER:      { color: '#0D9488', bg: '#F0FDF4', label: 'Offer'      },
  HIRED:      { color: '#166534', bg: '#DCFCE7', label: 'Hired'      },
  REJECTED:   { color: '#DC2626', bg: '#FEF2F2', label: 'Rejected'   },
  WITHDRAWN:  { color: '#94A3B8', bg: '#F8FAFC', label: 'Withdrawn'  },
}

const STATUS_CONFIG: Record<string, { color: string; bg: string }> = {
  DRAFT:  { color: '#64748B', bg: '#F8FAFC' },
  OPEN:   { color: '#166534', bg: '#DCFCE7' },
  PAUSED: { color: '#D97706', bg: '#FFFBEB' },
  CLOSED: { color: '#DC2626', bg: '#FEF2F2' },
  FILLED: { color: '#0D9488', bg: '#F0FDF4' },
}

type Tab = 'jobs' | 'applications'

export function RecruiterPage() {
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState<Tab>('jobs')
  const [showCreate, setShowCreate] = useState(false)
  const [selectedApp, setSelectedApp] = useState<Application | null>(null)
  const [error, setError] = useState('')
  const [form, setForm] = useState({ title: '', department: '', location: '', jobType: 'FULL_TIME', experienceLevel: 'MID', description: '', requirements: '', benefits: '' })
  const f = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: summary } = useQuery({
    queryKey: ['recruiter-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/recruiter/summary'); return r.data },
  })
  const { data: jobs = [], isLoading: loadingJobs } = useQuery<Job[]>({
    queryKey: ['recruiter-jobs'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/recruiter/jobs?size=50'); return r.data?.content || [] },
  })
  const { data: applications = [], isLoading: loadingApps } = useQuery<Application[]>({
    queryKey: ['recruiter-applications'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/recruiter/applications?size=50'); return r.data?.content || [] },
    enabled: activeTab === 'applications',
  })

  const createJob = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/recruiter/jobs', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['recruiter-jobs'] }); qc.invalidateQueries({ queryKey: ['recruiter-summary'] }); setShowCreate(false); setForm({ title: '', department: '', location: '', jobType: 'FULL_TIME', experienceLevel: 'MID', description: '', requirements: '', benefits: '' }) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create job'),
  })
  const updateJobStatus = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      apiClient.post(`/api/v1/recruiter/jobs/${id}/action/${action}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['recruiter-jobs'] }),
  })
  const moveStage = useMutation({
    mutationFn: ({ id, stage }: { id: string; stage: string }) =>
      apiClient.post(`/api/v1/recruiter/applications/${id}/stage`, { stage }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['recruiter-applications'] })
      if (selectedApp?.id === vars.id) setSelectedApp(s => s ? { ...s, stage: vars.stage } : null)
    },
  })

  const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
  const stats = [
    { label: 'Open Jobs',       value: summary?.openJobs       ?? 0, color: '#166534' },
    { label: 'New Applications',value: summary?.newApplications ?? 0, color: '#D97706' },
    { label: 'In Interview',    value: summary?.inInterview     ?? 0, color: '#1D4ED8' },
    { label: 'Hired This Month',value: summary?.hiredThisMonth  ?? 0, color: '#0D9488' },
  ]

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0F172A', margin: '0 0 4px' }}>Recruiter</h1>
        <p style={{ fontSize: 14, color: '#64748B', margin: 0 }}>Job postings, applicant pipeline and HR onboarding</p>
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, minWidth: 120, background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: 26, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: 24 }}>
        <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #E2E8F0', marginBottom: 24 }}>
          {(['jobs', 'applications'] as Tab[]).map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)} style={{
              display: 'flex', alignItems: 'center', gap: 7, padding: '10px 18px',
              background: 'none', border: 'none', borderBottom: activeTab === tab ? '2px solid #0D9488' : '2px solid transparent',
              color: activeTab === tab ? '#0D9488' : '#64748B', fontWeight: activeTab === tab ? 600 : 400,
              fontSize: 14, cursor: 'pointer', marginBottom: -1,
            }}>
              {tab === 'jobs' ? <Briefcase size={15} /> : <Users size={15} />}
              {tab === 'jobs' ? 'Job Postings' : 'Applications'}
            </button>
          ))}
        </div>

        {activeTab === 'jobs' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
              <button onClick={() => { setShowCreate(true); setError('') }} style={btnPrimary}><Plus size={15} /> Post Job</button>
            </div>
            {loadingJobs ? (
              <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading...</div>
            ) : jobs.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
                <UserCheck size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No jobs posted yet</div>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {jobs.map(job => {
                  const cfg = STATUS_CONFIG[job.status] || STATUS_CONFIG.DRAFT
                  return (
                    <div key={job.id} style={{ border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 3 }}>
                          <span style={{ fontWeight: 700, fontSize: 15, color: '#0F172A' }}>{job.title}</span>
                          <span style={{ background: cfg.bg, color: cfg.color, padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{job.status}</span>
                        </div>
                        <div style={{ fontSize: 12, color: '#94A3B8' }}>
                          {job.jobType} · {job.experienceLevel}
                          {job.department && ` · ${job.department}`}
                          {job.location && ` · ${job.location}`}
                          {job.closesAt && ` · Closes ${fmtDate(job.closesAt)}`}
                        </div>
                        <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{job.applicationCount} applications</div>
                      </div>
                      <div style={{ display: 'flex', gap: 8 }}>
                        {job.status === 'DRAFT' && (
                          <button onClick={() => updateJobStatus.mutate({ id: job.id, action: 'PUBLISH' })}
                            style={{ padding: '6px 14px', background: '#DCFCE7', color: '#166534', border: '1px solid #86EFAC', borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                            Publish
                          </button>
                        )}
                        {job.status === 'OPEN' && (
                          <button onClick={() => updateJobStatus.mutate({ id: job.id, action: 'CLOSE' })}
                            style={{ padding: '6px 14px', background: '#FEF2F2', color: '#DC2626', border: '1px solid #FECACA', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>
                            Close
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}

        {activeTab === 'applications' && (
          <div>
            {loadingApps ? (
              <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading...</div>
            ) : applications.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
                <Users size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No applications yet</div>
              </div>
            ) : (
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead><tr style={{ background: '#F8FAFC' }}>
                    {['Applicant', 'Job', 'Stage', 'Applied', 'Score', ''].map(h => <th key={h} style={th}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {applications.map((a, i) => {
                      const cfg = STAGE_CONFIG[a.stage] || STAGE_CONFIG.APPLIED
                      return (
                        <tr key={a.id} onClick={() => setSelectedApp(a)}
                          style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA', cursor: 'pointer' }}>
                          <td style={td}>
                            <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{a.applicantName}</div>
                            <div style={{ fontSize: 11, color: '#94A3B8' }}>{a.applicantEmail}</div>
                          </td>
                          <td style={td}><span style={{ fontSize: 13, color: '#475569' }}>{a.jobTitle}</span></td>
                          <td style={td}><span style={{ background: cfg.bg, color: cfg.color, padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{cfg.label}</span></td>
                          <td style={td}><span style={{ fontSize: 12, color: '#94A3B8' }}>{fmtDate(a.appliedAt)}</span></td>
                          <td style={td}>{a.score ? <span style={{ fontWeight: 700 }}>{'★'.repeat(a.score)}</span> : <span style={{ color: '#94A3B8' }}>—</span>}</td>
                          <td style={td}><ChevronRight size={16} color="#94A3B8" /></td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Application Detail */}
      {selectedApp && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 560, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <div>
                <h3 style={{ margin: '0 0 4px', fontSize: 17, fontWeight: 700, color: '#0F172A' }}>{selectedApp.applicantName}</h3>
                <div style={{ fontSize: 12, color: '#94A3B8' }}>{selectedApp.applicantEmail} · {selectedApp.jobTitle}</div>
              </div>
              <button onClick={() => setSelectedApp(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>
            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 10 }}>Move to Stage</div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {['SCREENING', 'INTERVIEW', 'ASSESSMENT', 'OFFER', 'HIRED', 'REJECTED'].map(s => {
                  const cfg = STAGE_CONFIG[s]
                  return (
                    <button key={s} onClick={() => moveStage.mutate({ id: selectedApp.id, stage: s })}
                      style={{ padding: '6px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: '1.5px solid',
                        borderColor: selectedApp.stage === s ? cfg.color : '#E2E8F0',
                        background: selectedApp.stage === s ? cfg.bg : '#fff',
                        color: selectedApp.stage === s ? cfg.color : '#64748B',
                      }}>
                      {cfg.label}
                    </button>
                  )
                })}
              </div>
            </div>
            <div style={{ padding: '12px 16px', background: '#F8FAFC', borderRadius: 10, fontSize: 13, color: '#374151' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                {[['Source', selectedApp.source], ['Applied', fmtDate(selectedApp.appliedAt)], ['Has CV', selectedApp.hasCv ? 'Yes' : 'No'], ['Score', selectedApp.score ? '★'.repeat(selectedApp.score) : 'Not rated']].map(([l, v]) => (
                  <div key={l as string}><div style={{ fontSize: 10, fontWeight: 600, color: '#94A3B8', marginBottom: 2 }}>{(l as string).toUpperCase()}</div><div style={{ fontWeight: 600 }}>{v as string}</div></div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Create Job Modal */}
      {showCreate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 560, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Post a Job</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <F label="Job Title *"><input value={form.title} onChange={e => f('title', e.target.value)} placeholder="Senior Equipment Operator" style={inp} /></F>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <F label="Department"><input value={form.department} onChange={e => f('department', e.target.value)} placeholder="Operations" style={inp} /></F>
                <F label="Location"><input value={form.location} onChange={e => f('location', e.target.value)} placeholder="Pretoria, Gauteng" style={inp} /></F>
                <F label="Job Type">
                  <select value={form.jobType} onChange={e => f('jobType', e.target.value)} style={inp}>
                    {['FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','FREELANCE'].map(t => <option key={t}>{t}</option>)}
                  </select>
                </F>
                <F label="Experience Level">
                  <select value={form.experienceLevel} onChange={e => f('experienceLevel', e.target.value)} style={inp}>
                    {['JUNIOR','MID','SENIOR','LEAD','EXECUTIVE'].map(t => <option key={t}>{t}</option>)}
                  </select>
                </F>
              </div>
              <F label="Job Description *">
                <textarea value={form.description} onChange={e => f('description', e.target.value)} rows={4} placeholder="Role overview and responsibilities..." style={{ ...inp, resize: 'vertical' as const }} />
              </F>
              <F label="Requirements">
                <textarea value={form.requirements} onChange={e => f('requirements', e.target.value)} rows={3} placeholder="Minimum requirements and qualifications..." style={{ ...inp, resize: 'vertical' as const }} />
              </F>
            </div>
            {error && <div style={{ marginTop: 10, color: '#DC2626', fontSize: 13 }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createJob.mutate({ title: form.title, department: form.department || null, location: form.location || null, jobType: form.jobType, experienceLevel: form.experienceLevel, description: form.description, requirements: form.requirements || null })}
                disabled={!form.title || !form.description || createJob.isPending} style={btnPrimary}>
                {createJob.isPending ? 'Posting...' : 'Post Job (Draft)'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function F({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 5 }}>{label}</label>{children}</div>
}
const btnPrimary: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 500, cursor: 'pointer' }
const btnCancel: React.CSSProperties  = { padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer', color: '#374151' }
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff' }
const th: React.CSSProperties = { padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 600, color: '#64748B', letterSpacing: '0.05em', borderBottom: '1px solid #E2E8F0' }
const td: React.CSSProperties = { padding: '12px 16px', fontSize: 13, borderBottom: '1px solid #F1F5F9' }
