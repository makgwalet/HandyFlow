import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, Palette, Eye, Upload, X, CheckCircle, Clock, ImageIcon } from 'lucide-react'

interface CreativeJob {
  id: string; jobNumber: string; title: string; clientName: string | null
  jobType: string; status: string; description: string | null
  dueDate: string | null; deliverableCount: number; pendingProofs: number
  createdAt: string
}

interface Proof {
  id: string; jobId: string; version: number; fileUrl: string | null
  fileName: string | null; notes: string | null; status: string
  publicToken: string; approvedAt: string | null; createdAt: string
}

const STATUS_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  BRIEF:       { color: '#7C3AED', bg: '#F3E8FF', label: 'Brief'       },
  IN_PROGRESS: { color: '#D97706', bg: '#FFFBEB', label: 'In Progress' },
  REVIEW:      { color: '#1D4ED8', bg: '#EFF6FF', label: 'Review'      },
  APPROVED:    { color: '#166534', bg: '#DCFCE7', label: 'Approved'    },
  DELIVERED:   { color: '#0D9488', bg: '#F0FDF4', label: 'Delivered'   },
  CANCELLED:   { color: '#94A3B8', bg: '#F8FAFC', label: 'Cancelled'   },
}

const JOB_TYPES = ['LOGO','BRANDING','SOCIAL_MEDIA','PRINT','WEB','VIDEO','PHOTOGRAPHY','OTHER']

export function CreativePage() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [selected, setSelected]     = useState<CreativeJob | null>(null)
  const [proofs, setProofs]         = useState<Proof[]>([])
  const [statusFilter, setStatusFilter] = useState('')
  const [error, setError] = useState('')

  const [form, setForm] = useState({
    title: '', clientName: '', jobType: 'LOGO', description: '', dueDate: '',
  })
  const f = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: jobs = [], isLoading } = useQuery<CreativeJob[]>({
    queryKey: ['creative-jobs', statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: '50' })
      if (statusFilter) params.set('status', statusFilter)
      const r = await apiClient.get(`/api/v1/creative/jobs?${params}`)
      return r.data?.content || []
    },
  })

  const { data: summary } = useQuery({
    queryKey: ['creative-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/creative/summary'); return r.data },
  })

  const createJob = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/creative/jobs', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['creative-jobs'] }); qc.invalidateQueries({ queryKey: ['creative-summary'] }); setShowCreate(false); setForm({ title: '', clientName: '', jobType: 'LOGO', description: '', dueDate: '' }) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create job'),
  })

  const loadProofs = async (job: CreativeJob) => {
    setSelected(job)
    const r = await apiClient.get(`/api/v1/creative/jobs/${job.id}/proofs`)
    setProofs(r.data || [])
  }

  const updateStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      apiClient.patch(`/api/v1/creative/jobs/${id}/status`, { status }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['creative-jobs'] })
      if (selected?.id === vars.id) setSelected(s => s ? { ...s, status: vars.status } : null)
    },
  })

  const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

  const stats = [
    { label: 'Active Jobs',    value: summary?.activeJobs    ?? 0, color: '#1B3A6B' },
    { label: 'Pending Review', value: summary?.pendingReview ?? 0, color: '#D97706' },
    { label: 'Delivered',      value: summary?.delivered     ?? 0, color: '#166534' },
    { label: 'Pending Proofs', value: summary?.pendingProofs ?? 0, color: '#7C3AED' },
  ]

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0F172A', margin: '0 0 4px' }}>Creative Studio</h1>
        <p style={{ fontSize: 14, color: '#64748B', margin: 0 }}>Design jobs, proof approvals and deliverable tracking</p>
      </div>

      {/* Stats */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, minWidth: 120, background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: 26, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: 24 }}>
        {/* Toolbar */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 10 }}>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {['', 'BRIEF', 'IN_PROGRESS', 'REVIEW', 'APPROVED', 'DELIVERED'].map(s => (
              <button key={s} onClick={() => setStatusFilter(s)} style={filterBtn(statusFilter === s)}>
                {s ? (STATUS_CONFIG[s]?.label || s) : 'All'}
              </button>
            ))}
          </div>
          <button onClick={() => { setShowCreate(true); setError('') }} style={btnPrimary}>
            <Plus size={15} /> New Job
          </button>
        </div>

        {/* Jobs grid */}
        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading jobs...</div>
        ) : jobs.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
            <Palette size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
            <div style={{ fontWeight: 600, color: '#475569' }}>No creative jobs found</div>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
            {jobs.map(job => {
              const cfg = STATUS_CONFIG[job.status] || STATUS_CONFIG.BRIEF
              return (
                <div key={job.id} onClick={() => loadProofs(job)}
                  style={{ border: '1px solid #E2E8F0', borderRadius: 12, padding: '18px 20px', cursor: 'pointer', transition: 'box-shadow 0.15s' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 12px rgba(0,0,0,0.08)' }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.boxShadow = 'none' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
                    <div style={{ width: 40, height: 40, borderRadius: 10, background: '#F3E8FF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Palette size={20} color="#7C3AED" />
                    </div>
                    <span style={{ background: cfg.bg, color: cfg.color, padding: '3px 10px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                  </div>
                  <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 3 }}>{job.title}</div>
                  <div style={{ fontSize: 12, color: '#94A3B8', marginBottom: 10 }}>
                    #{job.jobNumber} · {job.jobType}
                    {job.clientName && ` · ${job.clientName}`}
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#64748B' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      <ImageIcon size={12} /> {job.deliverableCount} deliverables
                    </span>
                    {job.pendingProofs > 0 && (
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#D97706', fontWeight: 600 }}>
                        <Clock size={12} /> {job.pendingProofs} proofs pending
                      </span>
                    )}
                    {job.dueDate && (
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>Due {fmtDate(job.dueDate)}</span>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Job Detail Modal */}
      {selected && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 600, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
              <div>
                <h3 style={{ margin: '0 0 4px', fontSize: 18, fontWeight: 700, color: '#0F172A' }}>{selected.title}</h3>
                <div style={{ fontSize: 13, color: '#94A3B8' }}>#{selected.jobNumber} · {selected.jobType}</div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>

            {/* Status actions */}
            <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
              {['BRIEF', 'IN_PROGRESS', 'REVIEW', 'APPROVED', 'DELIVERED'].map(s => {
                const cfg = STATUS_CONFIG[s]
                return (
                  <button key={s} onClick={() => updateStatus.mutate({ id: selected.id, status: s })}
                    style={{ padding: '6px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: '1.5px solid',
                      borderColor: selected.status === s ? cfg.color : '#E2E8F0',
                      background: selected.status === s ? cfg.bg : '#fff',
                      color: selected.status === s ? cfg.color : '#64748B',
                    }}>
                    {cfg.label}
                  </button>
                )
              })}
            </div>

            {/* Proofs */}
            <div style={{ marginBottom: 12 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 10 }}>Proof Versions</div>
              {proofs.length === 0 ? (
                <div style={{ padding: '20px', background: '#F8FAFC', borderRadius: 8, textAlign: 'center', fontSize: 13, color: '#94A3B8' }}>
                  No proofs uploaded yet
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {proofs.map(proof => (
                    <div key={proof.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', background: '#F8FAFC', borderRadius: 10, border: '1px solid #E2E8F0' }}>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>
                          Version {proof.version} — {proof.fileName || 'Proof'}
                        </div>
                        <div style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDate(proof.createdAt)}</div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        {proof.status === 'APPROVED' ? (
                          <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#166534', fontSize: 12, fontWeight: 600 }}>
                            <CheckCircle size={14} /> Approved
                          </span>
                        ) : (
                          <span style={{ color: '#D97706', fontSize: 12, fontWeight: 600 }}>Pending</span>
                        )}
                        {proof.fileUrl && (
                          <a href={proof.fileUrl} target="_blank" rel="noreferrer"
                            style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#1D4ED8', fontSize: 12 }}>
                            <Eye size={13} /> View
                          </a>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div style={{ marginTop: 12, padding: '10px 14px', background: '#F0F9FF', borderRadius: 8, fontSize: 12, color: '#0369A1' }}>
              Client approval link is emailed automatically when a proof is uploaded.
            </div>
          </div>
        </div>
      )}

      {/* Create Job Modal */}
      {showCreate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 500, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>New Creative Job</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <F label="Job Title *"><input value={form.title} onChange={e => f('title', e.target.value)} placeholder="Brand identity for client" style={inp} /></F>
              <F label="Client Name"><input value={form.clientName} onChange={e => f('clientName', e.target.value)} placeholder="Acme Corp" style={inp} /></F>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <F label="Job Type *">
                  <select value={form.jobType} onChange={e => f('jobType', e.target.value)} style={inp}>
                    {JOB_TYPES.map(t => <option key={t}>{t}</option>)}
                  </select>
                </F>
                <F label="Due Date"><input type="date" value={form.dueDate} onChange={e => f('dueDate', e.target.value)} style={inp} /></F>
              </div>
              <F label="Description">
                <textarea value={form.description} onChange={e => f('description', e.target.value)} rows={3} placeholder="Job brief and requirements..." style={{ ...inp, resize: 'vertical' as const }} />
              </F>
            </div>
            {error && <div style={{ marginTop: 10, color: '#DC2626', fontSize: 13 }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createJob.mutate({ title: form.title, clientName: form.clientName || null, jobType: form.jobType, description: form.description || null, dueDate: form.dueDate || null })}
                disabled={!form.title || createJob.isPending} style={btnPrimary}>
                {createJob.isPending ? 'Creating...' : 'Create Job'}
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

const filterBtn = (active: boolean): React.CSSProperties => ({ padding: '6px 12px', borderRadius: 6, fontSize: 12, cursor: 'pointer', border: active ? '1px solid #0D9488' : '1px solid #E2E8F0', background: active ? '#F0FDF4' : '#fff', color: active ? '#0D9488' : '#64748B', fontWeight: active ? 600 : 400 })
const btnPrimary: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 500, cursor: 'pointer' }
const btnCancel: React.CSSProperties  = { padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer', color: '#374151' }
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff' }
