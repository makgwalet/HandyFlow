// src/pages/creative/CreativePage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, Palette, X, CheckCircle, Clock, AlertTriangle,
  Send, Upload, FileText, Eye, MessageSquare, ChevronRight,
  BarChart2, Search, RefreshCw, Archive, Flag, User,
  Package, Image, Video, Camera, Layers, Globe, Film,
  PenTool, Box, Monitor, MoreHorizontal, Trash2, Edit3,
  Download, Link2, Star,
} from 'lucide-react'

// ── Types ──────────────────────────────────────────────────────────────────
interface Job {
  id: string; clientName: string; clientEmail: string | null
  title: string; jobType: string; status: string; priority: string
  description: string | null; brief: string | null
  dueDate: string | null; budget: number | null; quotedAmount: number | null
  proofCount: number; deliverableCount: number
  assignedTo: string | null; notes: string | null; invoiceId: string | null
  createdAt: string; updatedAt: string
}
interface Proof {
  id: string; versionNumber: number; title: string | null
  fileName: string | null; fileType: string | null; hasFile: boolean
  status: string; approvalToken: string; tokenExpiresAt: string | null
  sentAt: string | null; sentToEmail: string | null
  approvedAt: string | null; approvedByName: string | null
  rejectionReason: string | null; notes: string | null
  comments: Comment[]
  createdAt: string
}
interface Comment { id: string; authorName: string; authorType: string; comment: string; createdAt: string }
interface Deliverable { id: string; fileName: string; fileType: string | null; fileSize: number | null; notes: string | null; createdAt: string }
interface Summary { briefingCount: number; inProgressCount: number; awaitingApprovalCount: number; inRevisionCount: number; approvedCount: number; deliveredCount: number; overdueCount: number }

// ── Constants ──────────────────────────────────────────────────────────────
const STATUS: Record<string, { label: string; color: string; bg: string; border: string; dot: string }> = {
  BRIEFING:          { label: 'Brief',             color: '#7C3AED', bg: '#F5F3FF', border: '#DDD6FE', dot: '#A78BFA' },
  IN_PROGRESS:       { label: 'In Progress',       color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', dot: '#F59E0B' },
  AWAITING_APPROVAL: { label: 'Awaiting Approval', color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE', dot: '#60A5FA' },
  IN_REVISION:       { label: 'In Revision',       color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', dot: '#EF4444' },
  APPROVED:          { label: 'Approved',           color: '#166534', bg: '#DCFCE7', border: '#86EFAC', dot: '#22C55E' },
  DELIVERED:         { label: 'Delivered',          color: '#0D9488', bg: '#F0FDF9', border: '#99F6E4', dot: '#2DD4BF' },
  INVOICED:          { label: 'Invoiced',           color: '#0369A1', bg: '#E0F2FE', border: '#BAE6FD', dot: '#38BDF8' },
  CANCELLED:         { label: 'Cancelled',          color: '#94A3B8', bg: '#F8FAFC', border: '#E2E8F0', dot: '#CBD5E1' },
}
const PRIORITY: Record<string, { color: string; bg: string }> = {
  LOW:    { color: '#64748B', bg: '#F8FAFC' },
  NORMAL: { color: '#1D4ED8', bg: '#EFF6FF' },
  HIGH:   { color: '#D97706', bg: '#FFFBEB' },
  URGENT: { color: '#DC2626', bg: '#FEF2F2' },
}
const JOB_TYPES = ['LOGO','SOCIAL_MEDIA','VIDEO','PHOTOGRAPHY','PRINT','WEB_DESIGN','ANIMATION','COPYWRITING','BRANDING','ILLUSTRATION','PACKAGING','PRESENTATION','OTHER']
const TYPE_ICON: Record<string, any> = {
  LOGO: Star, SOCIAL_MEDIA: Globe, VIDEO: Video, PHOTOGRAPHY: Camera,
  PRINT: FileText, WEB_DESIGN: Monitor, ANIMATION: Film,
  COPYWRITING: PenTool, BRANDING: Layers, ILLUSTRATION: Image,
  PACKAGING: Box, PRESENTATION: BarChart2, OTHER: Palette,
}
const PIPELINE_STATUSES = ['BRIEFING','IN_PROGRESS','AWAITING_APPROVAL','IN_REVISION','APPROVED','DELIVERED']

// ── Helpers ────────────────────────────────────────────────────────────────
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }
const fmtDate  = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtDT    = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
const fmtR     = (n: any) => n ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}` : '—'
const isOverdue = (d: string | null, status: string) =>
  d && !['APPROVED','DELIVERED','INVOICED','CANCELLED'].includes(status) && new Date(d) < new Date()

const btnPrimary: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const btnSecondary: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, padding: '9px 16px', border: '1.5px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 13, cursor: 'pointer', color: '#374151', fontWeight: 500 }

// ── Confirm Modal ──────────────────────────────────────────────────────────
function ConfirmModal({ title, message, confirmLabel = 'Confirm', danger = false, onConfirm, onCancel }: {
  title: string; message: string; confirmLabel?: string; danger?: boolean
  onConfirm: () => void; onCancel: () => void
}) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 400, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 22 }}>
          <div style={{ width: 40, height: 40, borderRadius: '50%', background: danger ? '#FEF2F2' : '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <AlertTriangle size={18} color={danger ? '#DC2626' : '#1D4ED8'} />
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>{message}</div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <button onClick={onCancel} style={btnSecondary}>Cancel</button>
          <button onClick={onConfirm} style={{ ...btnPrimary, background: danger ? '#DC2626' : '#1B3A6B' }}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  )
}

// ── Job Detail Modal ───────────────────────────────────────────────────────
function JobDetailModal({ job, onClose, onRefresh }: { job: Job; onClose: () => void; onRefresh: () => void }) {
  const qc = useQueryClient()
  const [tab, setTab]               = useState<'details'|'proofs'|'deliverables'|'brief'>('details')
  const [showUploadProof, setShowUploadProof] = useState(false)
  const [showSendProof,   setShowSendProof]   = useState<string | null>(null)
  const [showAddDeliverable, setShowAddDeliverable] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm]   = useState(false)
  const [newComment, setNewComment]   = useState('')
  const [uploadFile,  setUploadFile]  = useState('')
  const [uploadName,  setUploadName]  = useState('')
  const [uploadType,  setUploadType]  = useState('')
  const [uploadNotes, setUploadNotes] = useState('')
  const [sendEmail,   setSendEmail]   = useState(job.clientEmail ?? '')
  const [sendMessage, setSendMessage] = useState('')
  const [delFile,     setDelFile]     = useState('')
  const [delName,     setDelName]     = useState('')
  const [delNotes,    setDelNotes]    = useState('')
  const [error,       setError]       = useState('')

  const { data: proofs = [] } = useQuery<Proof[]>({
    queryKey: ['creative-proofs', job.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/creative/jobs/${job.id}/proofs`)
      return r.data?.data ?? r.data ?? []
    },
  })

  const { data: deliverables = [] } = useQuery<Deliverable[]>({
    queryKey: ['creative-deliverables', job.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/creative/jobs/${job.id}/deliverables`)
      return r.data?.data ?? r.data ?? []
    },
  })

  const doAction = useMutation({
    mutationFn: (action: string) => apiClient.post(`/api/v1/creative/jobs/${job.id}/action/${action}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['creative-jobs'] }); qc.invalidateQueries({ queryKey: ['creative-summary'] }); onRefresh() },
    onError: (e: any) => setError(e.response?.data?.message || 'Action failed'),
  })

  const uploadProof = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/creative/jobs/${job.id}/proofs`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['creative-proofs', job.id] }); qc.invalidateQueries({ queryKey: ['creative-jobs'] }); setShowUploadProof(false); setUploadFile(''); setUploadName(''); setError('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Upload failed'),
  })

  const sendProof = useMutation({
    mutationFn: ({ proofId, email, message }: any) =>
      apiClient.post(`/api/v1/creative/jobs/${job.id}/proofs/${proofId}/send`, { email, message }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['creative-proofs', job.id] }); setShowSendProof(null); setSendMessage(''); setError('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to send proof'),
  })

  const addComment = useMutation({
    mutationFn: ({ proofId, comment }: any) =>
      apiClient.post(`/api/v1/creative/jobs/${job.id}/proofs/${proofId}/comments`, { comment }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['creative-proofs', job.id] }); setNewComment('') },
  })

  const addDeliverable = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/creative/jobs/${job.id}/deliverables`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['creative-deliverables', job.id] }); qc.invalidateQueries({ queryKey: ['creative-jobs'] }); qc.invalidateQueries({ queryKey: ['creative-summary'] }); setShowAddDeliverable(false); setDelFile(''); setDelName(''); setError('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to add deliverable'),
  })

  const deleteJob = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/creative/jobs/${job.id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['creative-jobs'] }); qc.invalidateQueries({ queryKey: ['creative-summary'] }); onClose() },
  })

  // File input handler — convert to base64
  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>, onLoad: (b64: string, name: string, type: string) => void) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      const b64 = (reader.result as string).split(',')[1]
      onLoad(b64, file.name, file.type)
    }
    reader.readAsDataURL(file)
  }

  const sc = STATUS[job.status] ?? STATUS.BRIEFING
  const overdue = isOverdue(job.dueDate, job.status)
  const TABS = ['details','proofs','brief','deliverables'] as const

  const ACTIONS: Record<string, { label: string; action: string; color: string; bg: string; next: string }[]> = {
    BRIEFING:          [{ label: 'Start work', action: 'START', color: '#D97706', bg: '#FFFBEB', next: 'IN_PROGRESS' }],
    IN_PROGRESS:       [{ label: 'Send for approval', action: 'SEND', color: '#1D4ED8', bg: '#EFF6FF', next: 'AWAITING_APPROVAL' }],
    AWAITING_APPROVAL: [{ label: 'Mark approved', action: 'APPROVE', color: '#166534', bg: '#DCFCE7', next: 'APPROVED' }, { label: 'Request revision', action: 'REVISE', color: '#DC2626', bg: '#FEF2F2', next: 'IN_REVISION' }],
    IN_REVISION:       [{ label: 'Resume work', action: 'START', color: '#D97706', bg: '#FFFBEB', next: 'IN_PROGRESS' }],
    APPROVED:          [{ label: 'Mark delivered', action: 'DELIVER', color: '#0D9488', bg: '#F0FDF9', next: 'DELIVERED' }],
    DELIVERED:         [],
  }
  const currentActions = ACTIONS[job.status] ?? []

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'flex-start', justifyContent: 'center', zIndex: 1000, padding: '28px 20px', overflowY: 'auto' }}>
      <div style={{ background: '#fff', borderRadius: 16, width: '100%', maxWidth: 760, boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }} onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div style={{ padding: '20px 24px', borderBottom: '1px solid #F1F5F9' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
            <div style={{ flex: 1, marginRight: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: '#111827' }}>{job.title}</h2>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                  <span style={{ width: 5, height: 5, borderRadius: '50%', background: sc.dot }} />{sc.label}
                </span>
                {overdue && <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#FEF2F2', color: '#DC2626', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}><AlertTriangle size={10} /> Overdue</span>}
              </div>
              <div style={{ display: 'flex', gap: 16, fontSize: 12, color: '#64748B', flexWrap: 'wrap' }}>
                <span>{job.jobType.replace('_', ' ')}</span>
                <span>Client: <strong style={{ color: '#374151' }}>{job.clientName}</strong></span>
                {job.dueDate && <span style={{ color: overdue ? '#DC2626' : '#64748B' }}>Due: {fmtDate(job.dueDate)}</span>}
                {job.quotedAmount && <span>Quoted: <strong style={{ color: '#0D9488' }}>{fmtR(job.quotedAmount)}</strong></span>}
                <span style={{ background: PRIORITY[job.priority]?.bg ?? '#F8FAFC', color: PRIORITY[job.priority]?.color ?? '#64748B', padding: '1px 7px', borderRadius: 10, fontSize: 10, fontWeight: 700 }}>{job.priority}</span>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
              <button onClick={() => setShowDeleteConfirm(true)} style={{ background: 'none', border: '1.5px solid #E5E7EB', borderRadius: 7, padding: '6px 8px', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}>
                <Trash2 size={14} />
              </button>
              <button onClick={onClose} style={{ background: '#F1F5F9', border: 'none', borderRadius: 7, padding: '6px 8px', cursor: 'pointer', color: '#64748B', display: 'flex' }}>
                <X size={16} />
              </button>
            </div>
          </div>

          {/* Pipeline status bar */}
          <div style={{ display: 'flex', gap: 4, marginBottom: 14, overflowX: 'auto' }}>
            {PIPELINE_STATUSES.map((s, i) => {
              const cfg      = STATUS[s]
              const isCurrent = job.status === s
              const isPast   = PIPELINE_STATUSES.indexOf(job.status) > i
              return (
                <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 4, flex: 1, minWidth: 0 }}>
                  <div style={{ flex: 1, padding: '4px 8px', borderRadius: 6, background: isCurrent ? cfg.bg : isPast ? '#F0FDF4' : '#F8FAFC', border: `1px solid ${isCurrent ? cfg.border : isPast ? '#86EFAC' : '#E2E8F0'}`, textAlign: 'center' as const }}>
                    <div style={{ fontSize: 10, fontWeight: 700, color: isCurrent ? cfg.color : isPast ? '#166534' : '#94A3B8', whiteSpace: 'nowrap' as const, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {isPast && !isCurrent ? '✓ ' : ''}{cfg.label}
                    </div>
                  </div>
                  {i < PIPELINE_STATUSES.length - 1 && <ChevronRight size={10} color="#CBD5E1" style={{ flexShrink: 0 }} />}
                </div>
              )
            })}
          </div>

          {/* Action buttons */}
          {currentActions.length > 0 && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {currentActions.map(a => (
                <button key={a.action} onClick={() => doAction.mutate(a.action)}
                  disabled={doAction.isPending}
                  style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: a.bg, color: a.color, border: `1px solid`, borderColor: STATUS[a.next]?.border ?? '#E2E8F0', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                  <ChevronRight size={12} /> {a.label}
                </button>
              ))}
              {!['DELIVERED','INVOICED','CANCELLED'].includes(job.status) && (
                <button onClick={() => setShowUploadProof(true)}
                  style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: '#EFF6FF', color: '#1D4ED8', border: '1px solid #BFDBFE', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                  <Upload size={12} /> Upload Proof
                </button>
              )}
              {job.status === 'APPROVED' && (
                <button onClick={() => setShowAddDeliverable(true)}
                  style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: '#F0FDF9', color: '#0D9488', border: '1px solid #99F6E4', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                  <Package size={12} /> Add Deliverable
                </button>
              )}
            </div>
          )}

          {/* Tabs */}
          <div style={{ display: 'flex', gap: 0, marginTop: 16 }}>
            {TABS.map(t => (
              <button key={t} onClick={() => setTab(t)}
                style={{ padding: '9px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: tab === t ? '#1B3A6B' : '#9CA3AF', borderBottom: `2px solid ${tab === t ? '#1B3A6B' : 'transparent'}`, marginBottom: -1, textTransform: 'capitalize' }}>
                {t === 'proofs' ? `Proofs (${(proofs as Proof[]).length})` : t === 'deliverables' ? `Deliverables (${(deliverables as Deliverable[]).length})` : t}
              </button>
            ))}
          </div>
        </div>

        {/* Body */}
        <div style={{ padding: '22px 24px 26px' }}>
          {error && <div style={{ marginBottom: 14, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

          {tab === 'details' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 200px', gap: 24 }}>
              <div>
                {job.description && (
                  <div style={{ marginBottom: 18 }}>
                    <div style={{ fontSize: 12, fontWeight: 700, color: '#9CA3AF', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Description</div>
                    <div style={{ fontSize: 14, color: '#374151', lineHeight: 1.7, background: '#F9FAFB', borderRadius: 9, padding: '12px 14px' }}>{job.description}</div>
                  </div>
                )}
                {job.notes && (
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 700, color: '#9CA3AF', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Internal notes</div>
                    <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.6, background: '#FFFBEB', borderRadius: 9, padding: '12px 14px', border: '1px solid #FDE68A' }}>{job.notes}</div>
                  </div>
                )}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                {[
                  { label: 'Client',    value: job.clientName },
                  { label: 'Email',     value: job.clientEmail ?? '—' },
                  { label: 'Type',      value: job.jobType.replace('_',' ') },
                  { label: 'Priority',  value: job.priority },
                  { label: 'Due date',  value: fmtDate(job.dueDate) },
                  { label: 'Budget',    value: fmtR(job.budget) },
                  { label: 'Quoted',    value: fmtR(job.quotedAmount) },
                  { label: 'Proofs',    value: `${(proofs as Proof[]).length} versions` },
                  { label: 'Created',   value: fmtDate(job.createdAt) },
                ].map(r => (
                  <div key={r.label}>
                    <div style={{ fontSize: 10, color: '#9CA3AF', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{r.label}</div>
                    <div style={{ fontSize: 13, color: '#374151', fontWeight: 600, marginTop: 2 }}>{r.value}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === 'brief' && (
            <div>
              {job.brief ? (
                <div style={{ fontSize: 14, color: '#374151', lineHeight: 1.8, background: '#F9FAFB', borderRadius: 10, padding: '16px 18px', border: '1px solid #E2E8F0', whiteSpace: 'pre-wrap' as const }}>{job.brief}</div>
              ) : (
                <div style={{ textAlign: 'center', padding: '40px', color: '#94A3B8' }}>
                  <FileText size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
                  <div style={{ fontWeight: 600, color: '#475569' }}>No brief added yet</div>
                </div>
              )}
            </div>
          )}

          {tab === 'proofs' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 14 }}>
                {!['DELIVERED','INVOICED','CANCELLED'].includes(job.status) && (
                  <button onClick={() => setShowUploadProof(true)} style={btnPrimary}>
                    <Upload size={13} /> Upload New Version
                  </button>
                )}
              </div>
              {(proofs as Proof[]).length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: '#94A3B8', border: '1.5px dashed #E2E8F0', borderRadius: 12 }}>
                  <Upload size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
                  <div style={{ fontWeight: 600, color: '#475569' }}>No proofs yet</div>
                  <div style={{ fontSize: 13, marginTop: 4 }}>Upload the first proof version to start the approval process.</div>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                  {(proofs as Proof[]).map((proof) => {
                    const ps = proof.status === 'APPROVED' ? { color: '#166534', bg: '#DCFCE7', label: 'Approved' }
                             : proof.status === 'REJECTED'   ? { color: '#DC2626', bg: '#FEF2F2', label: 'Changes requested' }
                             : proof.status === 'SUPERSEDED' ? { color: '#94A3B8', bg: '#F8FAFC', label: 'Superseded' }
                             : { color: '#D97706', bg: '#FFFBEB', label: 'Pending review' }
                    return (
                      <div key={proof.id} style={{ border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 18px', background: proof.status === 'APPROVED' ? '#F0FDF4' : '#fff' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                          <div>
                            <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A', marginBottom: 4 }}>
                              Version {proof.versionNumber}
                              {proof.title && <span style={{ fontWeight: 400, color: '#64748B' }}> — {proof.title}</span>}
                            </div>
                            <div style={{ fontSize: 12, color: '#94A3B8' }}>
                              {proof.fileName ?? 'No file'} · {fmtDate(proof.createdAt)}
                              {proof.sentAt && ` · Sent to ${proof.sentToEmail} on ${fmtDate(proof.sentAt)}`}
                            </div>
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ background: ps.bg, color: ps.color, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{ps.label}</span>
                          </div>
                        </div>

                        {proof.status === 'APPROVED' && (
                          <div style={{ padding: '8px 12px', background: '#DCFCE7', borderRadius: 8, marginBottom: 12, fontSize: 12, color: '#166534', fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6 }}>
                            <CheckCircle size={13} /> Approved by {proof.approvedByName} on {fmtDT(proof.approvedAt)}
                          </div>
                        )}

                        {proof.rejectionReason && (
                          <div style={{ padding: '8px 12px', background: '#FEF2F2', borderRadius: 8, marginBottom: 12, fontSize: 12, color: '#DC2626', display: 'flex', alignItems: 'flex-start', gap: 6 }}>
                            <AlertTriangle size={13} style={{ flexShrink: 0, marginTop: 1 }} /> <span><strong>Changes requested:</strong> {proof.rejectionReason}</span>
                          </div>
                        )}

                        {/* Approval link */}
                        {proof.status === 'PENDING' && (
                          <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                            <button onClick={() => { setShowSendProof(proof.id); setSendEmail(job.clientEmail ?? ''); setError('') }}
                              style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                              <Send size={11} /> Send to client
                            </button>
                            <button onClick={() => navigator.clipboard.writeText(`${window.location.origin}/creative/approve/${proof.approvalToken}`)}
                              style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#F1F5F9', color: '#374151', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}>
                              <Link2 size={11} /> Copy link
                            </button>
                          </div>
                        )}

                        {/* Comments */}
                        {proof.comments.length > 0 && (
                          <div style={{ marginTop: 10 }}>
                            <div style={{ fontSize: 11, fontWeight: 700, color: '#9CA3AF', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Comments ({proof.comments.length})</div>
                            {proof.comments.map(c => (
                              <div key={c.id} style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                                <div style={{ width: 24, height: 24, borderRadius: '50%', background: c.authorType === 'CLIENT' ? '#0D9488' : '#1B3A6B', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, color: '#fff', fontWeight: 700, flexShrink: 0 }}>
                                  {c.authorName.charAt(0).toUpperCase()}
                                </div>
                                <div style={{ flex: 1, background: '#F9FAFB', borderRadius: 8, padding: '8px 12px' }}>
                                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
                                    <span style={{ fontSize: 12, fontWeight: 700, color: '#0F172A' }}>{c.authorName}</span>
                                    <span style={{ fontSize: 10, color: '#94A3B8' }}>{c.authorType === 'CLIENT' ? 'Client' : 'Team'} · {fmtDate(c.createdAt)}</span>
                                  </div>
                                  <div style={{ fontSize: 13, color: '#374151' }}>{c.comment}</div>
                                </div>
                              </div>
                            ))}
                          </div>
                        )}

                        {/* Add team comment */}
                        {['PENDING','IN_REVISION'].includes(job.status) && proof.status === 'PENDING' && (
                          <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                            <input value={newComment} onChange={e => setNewComment(e.target.value)} placeholder="Add internal note..."
                              onKeyDown={e => { if (e.key === 'Enter' && newComment.trim()) { addComment.mutate({ proofId: proof.id, comment: newComment }); }}}
                              style={{ ...inp, flex: 1, fontSize: 13 }} />
                            <button onClick={() => addComment.mutate({ proofId: proof.id, comment: newComment })} disabled={!newComment.trim()}
                              style={{ ...btnPrimary, padding: '8px 12px', opacity: !newComment.trim() ? 0.5 : 1 }}>
                              <MessageSquare size={13} />
                            </button>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          )}

          {tab === 'deliverables' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 14 }}>
                {['APPROVED','DELIVERED'].includes(job.status) && (
                  <button onClick={() => setShowAddDeliverable(true)} style={btnPrimary}>
                    <Package size={13} /> Add Deliverable
                  </button>
                )}
              </div>
              {(deliverables as Deliverable[]).length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: '#94A3B8', border: '1.5px dashed #E2E8F0', borderRadius: 12 }}>
                  <Package size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
                  <div style={{ fontWeight: 600, color: '#475569' }}>No deliverables yet</div>
                  <div style={{ fontSize: 13, marginTop: 4 }}>Upload final files once the proof is approved.</div>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {(deliverables as Deliverable[]).map((d) => (
                    <div key={d.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px', border: '1px solid #E2E8F0', borderRadius: 10, background: '#F0FDF9' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div style={{ width: 36, height: 36, borderRadius: 8, background: '#0D9488', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          <FileText size={16} color="#fff" />
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 13, color: '#0F172A' }}>{d.fileName}</div>
                          <div style={{ fontSize: 11, color: '#64748B' }}>
                            {d.fileType ?? ''}{d.fileSize ? ` · ${Math.round(d.fileSize / 1024)}KB` : ''} · {fmtDate(d.createdAt)}
                          </div>
                          {d.notes && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 1 }}>{d.notes}</div>}
                        </div>
                      </div>
                      <CheckCircle size={16} color="#0D9488" />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Send Proof Modal */}
      {showSendProof && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 440, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Send proof to client</h3>
              <button onClick={() => setShowSendProof(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div><label style={lbl}>Client email *</label><input type="email" value={sendEmail} onChange={e => setSendEmail(e.target.value)} style={inp} autoFocus /></div>
              <div><label style={lbl}>Custom message (optional)</label><textarea value={sendMessage} onChange={e => setSendMessage(e.target.value)} rows={3} placeholder="Add a personal note to your client..." style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit' }} /></div>
              <div style={{ padding: '10px 12px', background: '#EFF6FF', borderRadius: 8, fontSize: 12, color: '#1D4ED8' }}>
                The client will receive a secure link to view and approve the proof. No HandyFlow account required.
              </div>
            </div>
            {error && <div style={{ marginTop: 10, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowSendProof(null)} style={btnSecondary}>Cancel</button>
              <button disabled={!sendEmail || sendProof.isPending}
                onClick={() => sendProof.mutate({ proofId: showSendProof, email: sendEmail, message: sendMessage || null })}
                style={{ ...btnPrimary, opacity: !sendEmail ? 0.5 : 1 }}>
                {sendProof.isPending ? 'Sending...' : <><Send size={13} /> Send proof</>}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Upload Proof Modal */}
      {showUploadProof && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 480, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Upload Proof</h3>
              <button onClick={() => setShowUploadProof(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={lbl}>Proof file *</label>
                <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '28px 20px', border: `2px dashed ${uploadFile ? '#0D9488' : '#E2E8F0'}`, borderRadius: 10, cursor: 'pointer', background: uploadFile ? '#F0FDF9' : '#F9FAFB', transition: 'all 0.15s' }}>
                  <input type="file" style={{ display: 'none' }} onChange={e => handleFileInput(e, (b64, name, type) => { setUploadFile(b64); setUploadName(name); setUploadType(type) })} />
                  {uploadFile ? <><CheckCircle size={24} color="#0D9488" /><span style={{ fontSize: 13, color: '#0D9488', fontWeight: 600 }}>{uploadName}</span></> : <><Upload size={24} color="#94A3B8" /><span style={{ fontSize: 13, color: '#64748B' }}>Click to select file</span><span style={{ fontSize: 11, color: '#94A3B8' }}>Images, PDFs, videos — up to your storage plan</span></>}
                </label>
              </div>
              <div><label style={lbl}>Version title (optional)</label><input value={uploadNotes} onChange={e => setUploadNotes(e.target.value)} placeholder="e.g. Version 2 — revised colours" style={inp} /></div>
            </div>
            {error && <div style={{ marginTop: 10, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowUploadProof(false)} style={btnSecondary}>Cancel</button>
              <button disabled={!uploadFile || uploadProof.isPending}
                onClick={() => uploadProof.mutate({ fileBase64: uploadFile, fileName: uploadName, fileType: uploadType, notes: uploadNotes || null })}
                style={{ ...btnPrimary, opacity: !uploadFile ? 0.5 : 1 }}>
                {uploadProof.isPending ? 'Uploading...' : <><Upload size={13} /> Upload proof</>}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Deliverable Modal */}
      {showAddDeliverable && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 480, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Add Deliverable</h3>
              <button onClick={() => setShowAddDeliverable(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={lbl}>Final file *</label>
                <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '28px 20px', border: `2px dashed ${delFile ? '#0D9488' : '#E2E8F0'}`, borderRadius: 10, cursor: 'pointer', background: delFile ? '#F0FDF9' : '#F9FAFB' }}>
                  <input type="file" style={{ display: 'none' }} onChange={e => handleFileInput(e, (b64, name, type) => { setDelFile(b64); setDelName(name) })} />
                  {delFile ? <><CheckCircle size={24} color="#0D9488" /><span style={{ fontSize: 13, color: '#0D9488', fontWeight: 600 }}>{delName}</span></> : <><Package size={24} color="#94A3B8" /><span style={{ fontSize: 13, color: '#64748B' }}>Click to select final file</span></>}
                </label>
              </div>
              <div><label style={lbl}>Notes (optional)</label><input value={delNotes} onChange={e => setDelNotes(e.target.value)} placeholder="e.g. Final print-ready PDF" style={inp} /></div>
            </div>
            {error && <div style={{ marginTop: 10, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowAddDeliverable(false)} style={btnSecondary}>Cancel</button>
              <button disabled={!delFile || addDeliverable.isPending}
                onClick={() => addDeliverable.mutate({ fileBase64: delFile, fileName: delName, fileType: '', fileSize: 0, notes: delNotes || null })}
                style={{ ...btnPrimary, opacity: !delFile ? 0.5 : 1 }}>
                {addDeliverable.isPending ? 'Uploading...' : <><Package size={13} /> Add deliverable</>}
              </button>
            </div>
          </div>
        </div>
      )}

      {showDeleteConfirm && (
        <ConfirmModal
          title="Delete job?"
          message={`"${job.title}" and all its proofs and comments will be permanently deleted. Deliverables will also be removed.`}
          confirmLabel="Delete job"
          danger
          onConfirm={() => deleteJob.mutate()}
          onCancel={() => setShowDeleteConfirm(false)}
        />
      )}
    </div>
  )
}

// ── Create Job Modal ───────────────────────────────────────────────────────
function CreateJobModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({
    clientName: '', clientEmail: '', title: '', jobType: 'LOGO',
    description: '', brief: '', priority: 'NORMAL',
    dueDate: '', budget: '', quotedAmount: '', notes: '',
  })
  const [error, setError] = useState('')
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const create = useMutation({
    mutationFn: () => apiClient.post('/api/v1/creative/jobs', {
      clientName: form.clientName, clientEmail: form.clientEmail || null,
      title: form.title, jobType: form.jobType,
      description: form.description || null, brief: form.brief || null,
      priority: form.priority, dueDate: form.dueDate || null,
      budget: form.budget ? parseFloat(form.budget) : null,
      quotedAmount: form.quotedAmount ? parseFloat(form.quotedAmount) : null,
      notes: form.notes || null,
    }),
    onSuccess: () => { onSaved(); onClose() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create job'),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 640, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>New Creative Job</h3>
            <p style={{ margin: '3px 0 0', fontSize: 13, color: '#64748B' }}>Create a job bag to start the creative workflow</p>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Job title *</label>
            <input autoFocus value={form.title} onChange={e => f('title', e.target.value)} placeholder="Brand identity for Acme Corp" style={inp} />
          </div>
          <div>
            <label style={lbl}>Client name *</label>
            <input value={form.clientName} onChange={e => f('clientName', e.target.value)} placeholder="Acme Construction (Pty) Ltd" style={inp} />
          </div>
          <div>
            <label style={lbl}>Client email</label>
            <input type="email" value={form.clientEmail} onChange={e => f('clientEmail', e.target.value)} placeholder="design@client.co.za" style={inp} />
          </div>
          <div>
            <label style={lbl}>Job type *</label>
            <select value={form.jobType} onChange={e => f('jobType', e.target.value)} style={{ ...inp, background: '#fff' }}>
              {JOB_TYPES.map(t => <option key={t} value={t}>{t.replace('_',' ')}</option>)}
            </select>
          </div>
          <div>
            <label style={lbl}>Priority</label>
            <select value={form.priority} onChange={e => f('priority', e.target.value)} style={{ ...inp, background: '#fff' }}>
              {['LOW','NORMAL','HIGH','URGENT'].map(p => <option key={p}>{p}</option>)}
            </select>
          </div>
          <div>
            <label style={lbl}>Due date</label>
            <input type="date" value={form.dueDate} onChange={e => f('dueDate', e.target.value)} style={inp} />
          </div>
          <div>
            <label style={lbl}>Budget (R)</label>
            <input type="number" value={form.budget} onChange={e => f('budget', e.target.value)} placeholder="0.00" style={inp} />
          </div>
          <div>
            <label style={lbl}>Quoted amount (R)</label>
            <input type="number" value={form.quotedAmount} onChange={e => f('quotedAmount', e.target.value)} placeholder="0.00" style={inp} />
          </div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Description</label>
            <textarea value={form.description} onChange={e => f('description', e.target.value)} rows={3} placeholder="Brief overview of the project..." style={{ ...inp, resize: 'vertical' as const, fontFamily: 'inherit' }} />
          </div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Creative brief (detailed)</label>
            <textarea value={form.brief} onChange={e => f('brief', e.target.value)} rows={4} placeholder="Target audience, brand personality, colour references, competitors to avoid, deliverable specs..." style={{ ...inp, resize: 'vertical' as const, fontFamily: 'inherit' }} />
          </div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Internal notes</label>
            <input value={form.notes} onChange={e => f('notes', e.target.value)} placeholder="Budget constraints, client quirks, deadline notes..." style={inp} />
          </div>
        </div>

        {error && <div style={{ marginTop: 14, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 22 }}>
          <button onClick={onClose} style={btnSecondary}>Cancel</button>
          <button onClick={() => create.mutate()} disabled={!form.title || !form.clientName || create.isPending}
            style={{ ...btnPrimary, opacity: (!form.title || !form.clientName) ? 0.5 : 1 }}>
            {create.isPending ? 'Creating...' : <><Plus size={13} /> Create job</>}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main Page ──────────────────────────────────────────────────────────────
export function CreativePage() {
  const qc = useQueryClient()
  const [view,         setView]         = useState<'grid'|'pipeline'>('grid')
  const [statusFilter, setStatusFilter] = useState('')
  const [search,       setSearch]       = useState('')
  const [typeFilter,   setTypeFilter]   = useState('')
  const [showCreate,   setShowCreate]   = useState(false)
  const [selectedJob,  setSelectedJob]  = useState<Job | null>(null)

  const { data: jobs = [], isLoading } = useQuery<Job[]>({
    queryKey: ['creative-jobs', statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: '200' })
      if (statusFilter) params.set('status', statusFilter)
      const r = await apiClient.get(`/api/v1/creative/jobs?${params}`)
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const { data: summary } = useQuery<Summary>({
    queryKey: ['creative-summary'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/creative/summary')
      return r.data?.data ?? r.data
    },
    refetchInterval: 30_000,
  })

  const refreshJob = async (id: string) => {
    const r = await apiClient.get(`/api/v1/creative/jobs/${id}`)
    setSelectedJob(r.data?.data ?? r.data)
    qc.invalidateQueries({ queryKey: ['creative-jobs'] })
  }

  const filtered = (jobs as Job[]).filter(j => {
    if (search && !j.title.toLowerCase().includes(search.toLowerCase()) &&
        !j.clientName.toLowerCase().includes(search.toLowerCase())) return false
    if (typeFilter && j.jobType !== typeFilter) return false
    return true
  })

  const kpis = [
    { label: 'Briefing',          value: summary?.briefingCount ?? 0,          color: '#7C3AED', bg: '#F5F3FF' },
    { label: 'In Progress',        value: summary?.inProgressCount ?? 0,        color: '#D97706', bg: '#FFFBEB' },
    { label: 'Awaiting Approval',  value: summary?.awaitingApprovalCount ?? 0,  color: '#1D4ED8', bg: '#EFF6FF' },
    { label: 'In Revision',        value: summary?.inRevisionCount ?? 0,        color: '#DC2626', bg: '#FEF2F2' },
    { label: 'Approved',           value: summary?.approvedCount ?? 0,          color: '#166534', bg: '#DCFCE7' },
    { label: 'Delivered',          value: summary?.deliveredCount ?? 0,         color: '#0D9488', bg: '#F0FDF9' },
    { label: 'Overdue',            value: summary?.overdueCount ?? 0,           color: summary?.overdueCount ? '#DC2626' : '#94A3B8', bg: summary?.overdueCount ? '#FEF2F2' : '#F8FAFC' },
  ]

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 22, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: '#7C3AED', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Palette size={18} color="#fff" />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: 0 }}>Creative Studio</h1>
          </div>
          <p style={{ fontSize: 13, color: '#94A3B8', margin: 0, paddingLeft: 46 }}>
            Design briefs · Proof approvals · Client sign-off portal · Deliverables
          </p>
        </div>
        <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} /> New Job</button>
      </div>

      {/* KPI strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 10, marginBottom: 22 }}>
        {kpis.map(k => (
          <div key={k.label} onClick={() => setStatusFilter(statusFilter === k.label.toUpperCase().replace(/ /g,'_') ? '' : k.label.toUpperCase().replace(/ /g,'_'))}
            style={{ background: k.bg, border: `1px solid transparent`, borderRadius: 10, padding: '12px 14px', cursor: 'pointer', transition: 'all 0.15s' }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: k.color }}>{k.value}</div>
            <div style={{ fontSize: 10, color: k.color, opacity: 0.8, marginTop: 2 }}>{k.label}</div>
          </div>
        ))}
      </div>

      {/* Main card */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: 24 }}>
        {/* Toolbar */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 10 }}>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <div style={{ position: 'relative' as const }}>
              <Search size={13} style={{ position: 'absolute' as const, left: 9, top: '50%', transform: 'translateY(-50%)', color: '#9CA3AF' }} />
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search jobs..."
                style={{ paddingLeft: 28, padding: '7px 10px 7px 28px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', width: 200 }} />
            </div>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
              style={{ padding: '7px 10px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
              <option value="">All statuses</option>
              {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
            </select>
            <select value={typeFilter} onChange={e => setTypeFilter(e.target.value)}
              style={{ padding: '7px 10px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
              <option value="">All types</option>
              {JOB_TYPES.map(t => <option key={t} value={t}>{t.replace('_',' ')}</option>)}
            </select>
            {(search || statusFilter || typeFilter) && (
              <button onClick={() => { setSearch(''); setStatusFilter(''); setTypeFilter('') }}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '7px 10px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, background: '#F8FAFC', color: '#64748B', cursor: 'pointer' }}>
                <X size={11} /> Clear
              </button>
            )}
          </div>
          <div style={{ display: 'flex', gap: 4 }}>
            {(['grid','pipeline'] as const).map(v => (
              <button key={v} onClick={() => setView(v)}
                style={{ padding: '6px 14px', borderRadius: 7, border: '1.5px solid #E2E8F0', background: view === v ? '#1B3A6B' : '#fff', color: view === v ? '#fff' : '#64748B', fontSize: 12, fontWeight: 600, cursor: 'pointer', textTransform: 'capitalize' }}>
                {v}
              </button>
            ))}
          </div>
        </div>

        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 48, color: '#94A3B8' }}>Loading jobs...</div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 20px' }}>
            <Palette size={40} style={{ marginBottom: 12, color: '#CBD5E1' }} />
            <div style={{ fontWeight: 700, color: '#475569', fontSize: 16, marginBottom: 6 }}>No creative jobs yet</div>
            <div style={{ fontSize: 13, color: '#94A3B8', marginBottom: 20 }}>Create your first job bag to start the design workflow.</div>
            <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} /> Create first job</button>
          </div>
        ) : view === 'grid' ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
            {filtered.map(job => {
              const cfg  = STATUS[job.status] ?? STATUS.BRIEFING
              const Icon = TYPE_ICON[job.jobType] ?? Palette
              const overdue = isOverdue(job.dueDate, job.status)
              return (
                <div key={job.id} onClick={() => setSelectedJob(job)}
                  style={{ border: `1px solid ${overdue ? '#FCA5A5' : '#E5E7EB'}`, borderLeft: `3px solid ${cfg.dot}`, borderRadius: 12, padding: '18px 20px', cursor: 'pointer', background: '#fff', transition: 'box-shadow 0.15s' }}
                  onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 16px rgba(0,0,0,0.08)'}
                  onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = 'none'}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                    <div style={{ width: 38, height: 38, borderRadius: 9, background: cfg.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      <Icon size={18} color={cfg.color} />
                    </div>
                    <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
                      {overdue && <AlertTriangle size={12} color="#DC2626" />}
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.border}`, padding: '1px 8px', borderRadius: 20, fontSize: 10, fontWeight: 700 }}>
                        <span style={{ width: 4, height: 4, borderRadius: '50%', background: cfg.dot }} />{cfg.label}
                      </span>
                    </div>
                  </div>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#111827', marginBottom: 4 }}>{job.title}</div>
                  <div style={{ fontSize: 12, color: '#64748B', marginBottom: 10 }}>
                    {job.clientName} · {job.jobType.replace('_',' ')}
                  </div>
                  {job.description && (
                    <div style={{ fontSize: 12, color: '#94A3B8', lineHeight: 1.4, marginBottom: 10, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' } as any}>
                      {job.description}
                    </div>
                  )}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 11, color: '#94A3B8' }}>
                    <div style={{ display: 'flex', gap: 10 }}>
                      {job.proofCount > 0 && <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}><Eye size={10} />{job.proofCount} proofs</span>}
                      {job.deliverableCount > 0 && <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: '#0D9488' }}><Package size={10} />{job.deliverableCount} files</span>}
                    </div>
                    {job.dueDate && <span style={{ color: overdue ? '#DC2626' : '#94A3B8', fontWeight: overdue ? 700 : 400 }}>Due {fmtDate(job.dueDate)}</span>}
                  </div>
                  {job.quotedAmount && (
                    <div style={{ marginTop: 8, fontSize: 12, fontWeight: 700, color: '#0D9488' }}>{fmtR(job.quotedAmount)}</div>
                  )}
                </div>
              )
            })}
          </div>
        ) : (
          // Pipeline view
          <div style={{ display: 'flex', gap: 14, overflowX: 'auto', paddingBottom: 16, alignItems: 'flex-start' }}>
            {PIPELINE_STATUSES.map(status => {
              const cfg  = STATUS[status]
              const col  = filtered.filter(j => j.status === status)
              return (
                <div key={status} style={{ minWidth: 260, maxWidth: 260, flexShrink: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 10, padding: '0 2px' }}>
                    <div style={{ width: 8, height: 8, borderRadius: '50%', background: cfg.dot }} />
                    <span style={{ fontSize: 12, fontWeight: 700, color: '#374151' }}>{cfg.label}</span>
                    <span style={{ background: '#F1F5F9', color: '#64748B', borderRadius: 20, padding: '1px 7px', fontSize: 11, fontWeight: 700 }}>{col.length}</span>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {col.map(job => (
                      <div key={job.id} onClick={() => setSelectedJob(job)}
                        style={{ border: '1px solid #E5E7EB', borderRadius: 9, padding: '12px 14px', cursor: 'pointer', background: '#fff', transition: 'box-shadow 0.15s' }}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 12px rgba(0,0,0,0.08)'}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = 'none'}>
                        <div style={{ fontWeight: 700, fontSize: 13, color: '#111827', marginBottom: 3 }}>{job.title}</div>
                        <div style={{ fontSize: 11, color: '#94A3B8', marginBottom: 6 }}>{job.clientName}</div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11 }}>
                          <span style={{ color: '#64748B' }}>{job.jobType.replace('_',' ')}</span>
                          {isOverdue(job.dueDate, job.status) && <span style={{ color: '#DC2626', fontWeight: 700 }}>Overdue</span>}
                          {job.quotedAmount ? <span style={{ color: '#0D9488', fontWeight: 600 }}>{fmtR(job.quotedAmount)}</span> : null}
                        </div>
                      </div>
                    ))}
                    {col.length === 0 && (
                      <div style={{ padding: '20px', textAlign: 'center', fontSize: 12, color: '#D1D5DB', border: '1.5px dashed #E5E7EB', borderRadius: 9 }}>No jobs</div>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {showCreate && (
        <CreateJobModal onClose={() => setShowCreate(false)}
          onSaved={() => { qc.invalidateQueries({ queryKey: ['creative-jobs'] }); qc.invalidateQueries({ queryKey: ['creative-summary'] }) }} />
      )}

      {selectedJob && (
        <JobDetailModal job={selectedJob} onClose={() => setSelectedJob(null)}
          onRefresh={() => refreshJob(selectedJob.id)} />
      )}
    </div>
  )
}
