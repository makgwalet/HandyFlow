// src/pages/contracting/TemplatesTab.tsx
//
// Fixes over original:
//  • Template preview now renders HTML properly via dangerouslySetInnerHTML
//    (original was showing raw tags in a <pre>)
//  • Rendered / HTML-source toggle on each expanded template card
//  • Live rendered preview while typing in the body editor
//  • Variable tags are click-to-copy pills
//  • Correct API unwrap
//  • System vs custom template section headers
//  • "Used in N contracts" count surfaced where available

import { sanitizeHtml } from './sanitizeHtml'
import { RichTextEditor } from './RichTextEditor'
import { hasContent } from './richText'
import { usePermissions } from '../../hooks/usePermission'
import { useState, type CSSProperties, type ChangeEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X, Lock, Eye, EyeOff, Copy, Check } from 'lucide-react'
import { unwrap } from './contractingApi'

// ─── Constants ────────────────────────────────────────────────────────────────

const CONTRACT_TYPES = [
  { value: 'SERVICE_AGREEMENT', label: 'Service Agreement'       },
  { value: 'NDA',               label: 'Non-Disclosure Agreement' },
  { value: 'EMPLOYMENT',        label: 'Employment Contract'      },
  { value: 'JOINT_VENTURE',     label: 'Joint Venture'            },
  { value: 'EQUIPMENT_HIRE',    label: 'Equipment Hire'           },
  { value: 'LEASE',             label: 'Lease Agreement'          },
  { value: 'SUPPLY',            label: 'Supply Agreement'         },
  { value: 'SUBCONTRACTOR',     label: 'Subcontractor'            },
  { value: 'SERVICE_LEVEL',     label: 'Service Level Agreement'  },
  { value: 'CONSULTING',        label: 'Consulting Agreement'     },
  { value: 'RETAINER',          label: 'Retainer Agreement'       },
  { value: 'ACKNOWLEDGMENT_OF_DEBT', label: 'Acknowledgment of Debt' },
  { value: 'OTHER',             label: 'Other'                    },
]

const TYPE_COLOR: Record<string, string> = {
  SERVICE_AGREEMENT: 'var(--hf-accent-text)', NDA: 'var(--hf-violet-text)', EMPLOYMENT: 'var(--hf-info-text)',
  JOINT_VENTURE: 'var(--hf-warning-text)', EQUIPMENT_HIRE: 'var(--hf-orange-text)', LEASE: 'var(--hf-success-text-strong)',
  SUPPLY: 'var(--hf-sky-text)', SUBCONTRACTOR: 'var(--hf-danger-text)', SERVICE_LEVEL: 'var(--hf-indigo-text)',
  CONSULTING: 'var(--hf-pink-text)', RETAINER: 'var(--hf-warning-text-deep)', ACKNOWLEDGMENT_OF_DEBT: 'var(--hf-warning-text-strong)', OTHER: 'var(--hf-text-muted)',
}

interface Template {
  id: string
  name: string
  contractType: string
  description: string
  bodyTemplate: string
  variables: Record<string, string>
  isSystem: boolean
  templateVersion?: number
  status?: string        // DRAFT, INTERNAL_REVIEW, LEGAL_REVIEW, APPROVED, PUBLISHED, SUPERSEDED
  legalStatus?: string   // NOT_REVIEWED, APPROVED, CONDITIONAL, REJECTED, EXPIRED, WITHDRAWN
  legalArea?: string
  requiredSigningMethod?: string
}

const STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Draft', INTERNAL_REVIEW: 'Internal review', LEGAL_REVIEW: 'Legal review',
  APPROVED: 'Approved', PUBLISHED: 'Published', SUPERSEDED: 'Superseded',
}
const LEGAL_LABEL: Record<string, string> = {
  NOT_REVIEWED: 'Not legally reviewed', APPROVED: 'Legal review recorded', CONDITIONAL: 'Conditional legal review',
  REJECTED: 'Legal review rejected', EXPIRED: 'Legal review expired', WITHDRAWN: 'Legal review withdrawn',
}
const LEGAL_COLOR: Record<string, string> = {
  APPROVED: 'var(--hf-success-text-strong)', CONDITIONAL: 'var(--hf-warning-text)', NOT_REVIEWED: 'var(--hf-text-muted)',
  REJECTED: 'var(--hf-danger-text)', EXPIRED: 'var(--hf-danger-text)', WITHDRAWN: 'var(--hf-danger-text)',
}
const SIGNING_METHODS = [
  { value: '',                    label: 'Not classified' },
  { value: 'SIMPLE_ELECTRONIC',   label: 'Simple electronic (OTP and drawn signature)' },
  { value: 'ADVANCED_ELECTRONIC', label: 'Advanced electronic (not supported yet)' },
  { value: 'WET_INK',             label: 'Wet ink (not supported yet)' },
  { value: 'IN_PERSON',           label: 'In person (not supported yet)' },
]

interface LegalReview {
  id: string; templateVersion: number; outcome: string; withdrawnAt?: string | null; expired?: boolean
  reviewerName: string; reviewerProfessionalDetails: string; reviewerOrganisation?: string | null
  reviewDate: string; legalArea: string; jurisdiction: string; nextReviewDate?: string | null
  outstandingIssues?: string | null; withdrawnReason?: string | null
  evidenceDocumentId?: string | null; evidenceFileName?: string | null
}

const errMsg = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

const smallBtn: CSSProperties = {
  padding: '5px 11px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)',
  fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)',
}

// ─── Legal review: record a reviewer's outcome, and show the history ───────────

function LegalReviewModal({ template, onClose, onSubmit, error, busy }: {
  template: Template; onClose: () => void; onSubmit: (body: Record<string, unknown>, file: File | null) => void; error: string; busy: boolean
}) {
  const [f, setF] = useState({
    reviewerName: '', reviewerProfessionalDetails: '', reviewerOrganisation: '', reviewDate: new Date().toISOString().slice(0, 10),
    legalArea: template.legalArea ?? '', jurisdiction: 'South Africa', scopeAssumptions: '', outstandingIssues: '',
    outcome: 'APPROVED', nextReviewDate: '', evidenceDocumentId: '',
  })
  const [file, setFile] = useState<File | null>(null)
  const set = (k: string) => (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => setF(p => ({ ...p, [k]: e.target.value }))
  const ok = f.reviewerName && f.reviewerProfessionalDetails && f.reviewDate && f.legalArea
    && (f.outcome !== 'CONDITIONAL' || f.outstandingIssues.trim())
  return (
    <div style={MODAL}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 640, maxHeight: '92vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>Record legal review: {template.name}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
        </div>
        <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 14, lineHeight: 1.5 }}>
          Record the review by a South African attorney or other qualified legal practitioner, for version {template.templateVersion ?? 1}.
          This is evidence that a review took place on the date given. It is not a guarantee that every contract made from this template is lawful
          or suitable for every transaction.
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <div><label style={lbl}>Reviewer name *</label><input style={inp} value={f.reviewerName} onChange={set('reviewerName')} /></div>
          <div><label style={lbl}>Professional details *</label><input style={inp} value={f.reviewerProfessionalDetails} onChange={set('reviewerProfessionalDetails')} placeholder="e.g. admitted attorney, Law Society no." /></div>
          <div><label style={lbl}>Firm or organisation</label><input style={inp} value={f.reviewerOrganisation} onChange={set('reviewerOrganisation')} /></div>
          <div><label style={lbl}>Review date *</label><input type="date" style={inp} value={f.reviewDate} onChange={set('reviewDate')} /></div>
          <div><label style={lbl}>Legal area *</label><input style={inp} value={f.legalArea} onChange={set('legalArea')} placeholder="e.g. Employment, Contract law" /></div>
          <div><label style={lbl}>Jurisdiction</label><input style={inp} value={f.jurisdiction} onChange={set('jurisdiction')} /></div>
          <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Scope and assumptions</label><textarea rows={2} style={inp} value={f.scopeAssumptions} onChange={set('scopeAssumptions')} /></div>
          <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Outstanding issues {f.outcome === 'CONDITIONAL' ? '*' : ''}</label><textarea rows={2} style={inp} value={f.outstandingIssues} onChange={set('outstandingIssues')} /></div>
          <div>
            <label style={lbl}>Outcome *</label>
            <select style={inp} value={f.outcome} onChange={set('outcome')}>
              <option value="APPROVED">Approved</option><option value="CONDITIONAL">Conditionally approved</option><option value="REJECTED">Rejected</option>
            </select>
          </div>
          <div><label style={lbl}>Next review date</label><input type="date" style={inp} value={f.nextReviewDate} onChange={set('nextReviewDate')} /></div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Evidence: the reviewer's signed-off review (PDF or scan)</label>
            <input type="file" accept=".pdf,.png,.jpg,.jpeg,.doc,.docx" onChange={e => setFile(e.target.files?.[0] ?? null)} style={inp} />
            {file && <div style={{ fontSize: 11, color: 'var(--hf-text-muted)', marginTop: 4 }}>{file.name} ({Math.round(file.size / 1024)} KB), stored with the review once it is recorded.</div>}
          </div>
        </div>
        {error && <div style={{ marginTop: 12, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 18 }}>
          <button onClick={onClose} style={smallBtn}>Cancel</button>
          <button disabled={!ok || busy} onClick={() => onSubmit({
            ...f, nextReviewDate: f.nextReviewDate || null, evidenceDocumentId: null,
          }, file)} style={{ ...smallBtn, background: ok ? 'var(--hf-primary)' : 'var(--hf-text-faint)', color: 'var(--hf-text-on-solid)', border: 'none' }}>
            {busy ? 'Saving…' : 'Record review'}
          </button>
        </div>
      </div>
    </div>
  )
}

function LegalHistory({ templateId, onWithdraw, onAttach, onDownload, canAuthor }: {
  canAuthor: boolean
  templateId: string; onWithdraw: (reviewId: string) => void
  onAttach: (reviewId: string, file: File) => void; onDownload: (reviewId: string, fileName: string) => void
}) {
  const { data: reviews = [] } = useQuery<LegalReview[]>({
    queryKey: ['contract-template-reviews', templateId],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/contracts/templates/${templateId}/legal-reviews`)),
  })
  if (reviews.length === 0) return <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>No legal review recorded yet.</div>
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {reviews.map(r => (
        <div key={r.id} style={{ fontSize: 12, border: '1px solid var(--hf-border)', borderRadius: 8, padding: '8px 10px', background: 'var(--hf-surface)' }}>
          <div style={{ fontWeight: 700, color: 'var(--hf-text)' }}>
            v{r.templateVersion} · {r.outcome}{r.withdrawnAt ? ' (withdrawn)' : r.expired ? ' (expired)' : ''}
          </div>
          <div style={{ color: 'var(--hf-text-muted)' }}>
            {r.reviewerName}, {r.reviewerProfessionalDetails}{r.reviewerOrganisation ? `, ${r.reviewerOrganisation}` : ''} · {r.reviewDate} · {r.legalArea}, {r.jurisdiction}
            {r.nextReviewDate ? ` · next review ${r.nextReviewDate}` : ''}
          </div>
          {r.outstandingIssues && <div style={{ color: 'var(--hf-warning-text)' }}>Outstanding: {r.outstandingIssues}</div>}
          {r.withdrawnReason && <div style={{ color: 'var(--hf-danger-text)' }}>Withdrawn: {r.withdrawnReason}</div>}
          <div style={{ color: 'var(--hf-text-muted)' }}>
            Evidence: {r.evidenceDocumentId
              ? <button style={{ ...smallBtn, padding: '2px 8px' }} onClick={() => onDownload(r.id, r.evidenceFileName ?? 'legal-review-evidence')}>Download {r.evidenceFileName ?? 'file'}</button>
              : <span style={{ color: 'var(--hf-warning-text)' }}>none attached</span>}
          </div>
          {canAuthor && !r.withdrawnAt && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 6, flexWrap: 'wrap' }}>
              <label style={{ ...smallBtn, display: 'inline-block' }}>
                {r.evidenceDocumentId ? 'Upload replacement' : 'Attach evidence'}
                <input type="file" accept=".pdf,.png,.jpg,.jpeg,.doc,.docx" style={{ display: 'none' }}
                  onChange={e => { const fl = e.target.files?.[0]; if (fl) onAttach(r.id, fl); e.target.value = '' }} />
              </label>
              <button style={smallBtn} onClick={() => onWithdraw(r.id)}>Withdraw review</button>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

// ─── Shared styles ────────────────────────────────────────────────────────────

const inp: CSSProperties = {
  width: '100%', padding: '9px 12px',
  border: '1.5px solid var(--hf-border)', borderRadius: 8,
  fontSize: 14, boxSizing: 'border-box', background: 'var(--hf-surface)', outline: 'none',
}
const lbl: CSSProperties = {
  display: 'block', fontSize: 12,
  fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 4,
}
const MODAL: CSSProperties = {
  position: 'fixed', inset: 0,
  background: 'rgba(15,23,42,0.5)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 1000,
}

// ─── HtmlPreview — renders HTML body properly ────────────────────────────────

function HtmlPreview({ html, maxHeight = 280 }: { html: string; maxHeight?: number }) {
  return (
    <div
      style={{
        maxHeight, overflowY: 'auto',
        padding: '14px 16px',
        background: 'var(--hf-surface)', border: '1px solid var(--hf-border)',
        borderRadius: 7, fontSize: 13, lineHeight: 1.8, color: 'var(--hf-text-secondary)',
      }}
      dangerouslySetInnerHTML={{ __html: sanitizeHtml(html) }}
    />
  )
}

// ─── TemplateCard ─────────────────────────────────────────────────────────────

function TemplateCard({ template, onCopyVar, copied, onAction, onEdit, onRecordReview, onWithdraw, onAttach, onDownload, busy, canAuthor }: {
  template: Template
  onCopyVar: (v: string) => void
  copied: string | null
  onAction: (id: string, action: string) => void
  onEdit: (t: Template) => void
  onRecordReview: (t: Template) => void
  onWithdraw: (templateId: string, reviewId: string) => void
  onAttach: (templateId: string, reviewId: string, file: File) => void
  onDownload: (templateId: string, reviewId: string, fileName: string) => void
  busy: boolean
  canAuthor: boolean
}) {
  const [expanded, setExpanded] = useState(false)
  const [showLegal, setShowLegal] = useState(false)
  const status = template.status ?? 'PUBLISHED'
  const legal = template.legalStatus ?? 'NOT_REVIEWED'
  const signing = SIGNING_METHODS.find(m => m.value === (template.requiredSigningMethod ?? ''))?.label
  const [mode,     setMode]     = useState<'rendered' | 'source'>('rendered')

  const color    = TYPE_COLOR[template.contractType] ?? 'var(--hf-text-muted)'
  const varKeys  = template.variables ? Object.keys(template.variables) : []
  const typeLabel = CONTRACT_TYPES.find(t => t.value === template.contractType)?.label ?? template.contractType.replace(/_/g, ' ')

  return (
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 12, overflow: 'hidden' }}>
      {/* Colour stripe */}
      <div style={{ height: 3, background: color }} />

      {/* Card body */}
      <div style={{ padding: '14px 16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--hf-text)', marginBottom: 5 }}>{template.name}</div>
            <span style={{ background: `color-mix(in srgb, ${color} 9%, transparent)`, color, border: `1px solid color-mix(in srgb, ${color} 19%, transparent)`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
              {typeLabel}
            </span>
          </div>
          {template.isSystem && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, background: 'var(--hf-surface-sunken)', color: 'var(--hf-text-muted)', padding: '2px 8px', borderRadius: 6, fontSize: 10, fontWeight: 700, flexShrink: 0 }}>
              <Lock size={9} /> SYSTEM
            </div>
          )}
        </div>

        {/* Lifecycle and legal status. Legal review is evidence at a point in time, not a guarantee of compliance. */}
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center', marginBottom: 8, fontSize: 11 }}>
          <span style={{ fontWeight: 700, padding: '2px 8px', borderRadius: 20, background: 'var(--hf-surface-sunken)', color: 'var(--hf-text-secondary)' }}>
            {STATUS_LABEL[status] ?? status} · v{template.templateVersion ?? 1}
          </span>
          <span style={{ fontWeight: 600, color: LEGAL_COLOR[legal] ?? 'var(--hf-text-muted)' }}>{LEGAL_LABEL[legal] ?? legal}</span>
          {template.requiredSigningMethod && <span style={{ color: 'var(--hf-text-muted)' }}>· Signing: {signing}</span>}
        </div>
        {(legal === 'EXPIRED' || legal === 'WITHDRAWN' || legal === 'REJECTED') && status === 'PUBLISHED' && (
          <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginBottom: 8 }}>
            This template cannot be used for new contracts until it has a new legal review.
          </div>
        )}

        {template.description && (
          <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', lineHeight: 1.5, marginBottom: 10 }}>
            {template.description}
          </div>
        )}

        {/* Variable pills — click to copy */}
        {varKeys.length > 0 && (
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 10 }}>
            {varKeys.map(v => (
              <button
                key={v}
                onClick={() => onCopyVar(v)}
                title={`Click to copy {{${v}}}`}
                style={{
                  display: 'flex', alignItems: 'center', gap: 3,
                  background: 'var(--hf-surface-muted)', color: 'var(--hf-text-tertiary)',
                  padding: '2px 7px', borderRadius: 4,
                  fontSize: 10, fontFamily: 'monospace',
                  border: '1px solid var(--hf-border)', cursor: 'pointer',
                  transition: 'background 0.1s',
                }}>
                {copied === v ? <Check size={9} style={{ color: 'var(--hf-success-text-strong)' }} /> : <Copy size={9} />}
                {`{{${v}}}`}
              </button>
            ))}
          </div>
        )}

        {/* Lifecycle actions */}
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 10 }}>
          {canAuthor && (status === 'DRAFT' || status === 'INTERNAL_REVIEW') && <button style={smallBtn} disabled={busy} onClick={() => onEdit(template)}>Edit</button>}
          {canAuthor && status === 'DRAFT' && <button style={smallBtn} disabled={busy} onClick={() => onAction(template.id, 'submit-internal-review')}>Submit for internal review</button>}
          {canAuthor && status === 'INTERNAL_REVIEW' && <button style={smallBtn} disabled={busy} onClick={() => onAction(template.id, 'submit-legal-review')}>Submit for legal review</button>}
          {canAuthor && status === 'LEGAL_REVIEW' && <button style={smallBtn} disabled={busy} onClick={() => onRecordReview(template)}>Record legal review</button>}
          {canAuthor && status === 'APPROVED' && <button style={smallBtn} disabled={busy} onClick={() => onAction(template.id, 'publish')}>Publish</button>}
          {canAuthor && status === 'PUBLISHED' && <button style={smallBtn} disabled={busy} onClick={() => onAction(template.id, 'new-version')}>New version</button>}
          {canAuthor && status === 'PUBLISHED' && !template.isSystem && <button style={smallBtn} disabled={busy} onClick={() => onAction(template.id, 'archive')}>Archive</button>}
          <button style={smallBtn} onClick={() => setShowLegal(!showLegal)}>{showLegal ? 'Hide legal reviews' : 'Legal reviews'}</button>
        </div>
        {showLegal && <div style={{ marginBottom: 10 }}><LegalHistory canAuthor={canAuthor} templateId={template.id} onWithdraw={rid => onWithdraw(template.id, rid)}
          onAttach={(rid, fl) => onAttach(template.id, rid, fl)} onDownload={(rid, nm) => onDownload(template.id, rid, nm)} /></div>}

        {/* Preview toggle */}
        {template.bodyTemplate && (
          <button onClick={() => setExpanded(!expanded)} style={{ display: 'flex', alignItems: 'center', gap: 4, background: 'none', border: 'none', color: 'var(--hf-primary-text)', fontSize: 12, fontWeight: 600, cursor: 'pointer', padding: 0 }}>
            {expanded ? <EyeOff size={13} /> : <Eye size={13} />}
            {expanded ? 'Hide preview' : 'Preview'}
          </button>
        )}
      </div>

      {/* Expanded preview */}
      {expanded && template.bodyTemplate && (
        <div style={{ borderTop: '1px solid var(--hf-border)' }}>
          {/* Rendered / Source tabs */}
          <div style={{ display: 'flex', borderBottom: '1px solid var(--hf-border)' }}>
            {(['rendered', 'source'] as const).map(m => (
              <button key={m} onClick={() => setMode(m)} style={{
                flex: 1, padding: '7px 0',
                fontSize: 11, fontWeight: mode === m ? 700 : 400,
                cursor: 'pointer', background: mode === m ? 'var(--hf-surface-muted)' : 'var(--hf-surface)',
                color: mode === m ? 'var(--hf-primary-text)' : 'var(--hf-text-faint)',
                border: 'none',
                borderBottom: mode === m ? '2px solid var(--hf-primary)' : '2px solid transparent',
              }}>
                {m === 'rendered' ? 'Rendered' : 'HTML source'}
              </button>
            ))}
          </div>

          <div style={{ padding: '14px 16px', background: 'var(--hf-surface-muted)' }}>
            {mode === 'rendered' ? (
              /* FIX: renders HTML properly — original was showing raw tags in <pre> */
              <HtmlPreview html={template.bodyTemplate} />
            ) : (
              <pre style={{ margin: 0, fontSize: 11, color: 'var(--hf-text-tertiary)', fontFamily: 'monospace', whiteSpace: 'pre-wrap', lineHeight: 1.6, maxHeight: 280, overflowY: 'auto' }}>
                {template.bodyTemplate}
              </pre>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// TemplatesTab
// ═══════════════════════════════════════════════════════════════════════════════

export default function TemplatesTab() {
  const qc = useQueryClient()
  const canAuthor = usePermissions(['CONTRACTS_TEMPLATES', 'CONTRACTS_MANAGE'], 'any')

  const [showCreate, setShowCreate] = useState(false)
  const [copied,     setCopied]     = useState<string | null>(null)
  const [error,      setError]      = useState('')
  const EMPTY_FORM = { name: '', contractType: 'SERVICE_AGREEMENT', description: '', bodyTemplate: '', legalArea: '', requiredSigningMethod: '' }
  const [form, setForm] = useState(EMPTY_FORM)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [reviewFor, setReviewFor] = useState<Template | null>(null)
  const [reviewError, setReviewError] = useState('')

  // ── Queries ──────────────────────────────────────────────────────────────────

  const { data: templates = [], isLoading } = useQuery<Template[]>({
    queryKey: ['contract-templates'],
    queryFn: async () => unwrap(await apiClient.get('/api/v1/contracts/templates')),
  })

  // ── Mutations ────────────────────────────────────────────────────────────────

  const createTemplate = useMutation({
    mutationFn: (body: Record<string, unknown>) => editingId
      ? apiClient.put(`/api/v1/contracts/templates/${editingId}`, body)
      : apiClient.post('/api/v1/contracts/templates', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['contract-templates'] })
      setShowCreate(false)
      setForm(EMPTY_FORM)
      setEditingId(null)
      setError('')
    },
    onError: (e: unknown) => setError(errMsg(e, 'Failed to save template')),
  })

  // Lifecycle steps: submit-internal-review, submit-legal-review, publish, new-version, archive.
  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) => apiClient.post(`/api/v1/contracts/templates/${id}/${action}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['contract-templates'] }); setError('') },
    onError: (e: unknown) => setError(errMsg(e, 'That step could not be completed')),
  })

  const uploadEvidence = (id: string, reviewId: string, fl: File) => {
    const fd = new FormData()
    fd.append('file', fl)
    return apiClient.post(`/api/v1/contracts/templates/${id}/legal-reviews/${reviewId}/evidence`, fd)
  }

  // Records the review first, then stores the evidence file. If only the file fails, the review stays recorded and the
  // reviewer's file can be attached again from the review history.
  const recordReview = useMutation({
    mutationFn: async ({ id, body, file }: { id: string; body: Record<string, unknown>; file: File | null }) => {
      const res = await apiClient.post(`/api/v1/contracts/templates/${id}/legal-reviews`, body)
      if (!file) return { fileError: '' }
      try {
        await uploadEvidence(id, (res.data?.data ?? res.data).id as string, file)
        return { fileError: '' }
      } catch (e: unknown) {
        return { fileError: errMsg(e, 'The file could not be stored') }
      }
    },
    onSuccess: (r, v) => {
      qc.invalidateQueries({ queryKey: ['contract-templates'] })
      qc.invalidateQueries({ queryKey: ['contract-template-reviews', v.id] })
      setReviewFor(null); setReviewError('')
      setError(r.fileError ? `The review was recorded, but the evidence file was not stored: ${r.fileError}. Attach it from the legal reviews list.` : '')
    },
    onError: (e: unknown) => setReviewError(errMsg(e, 'Failed to record the review')),
  })

  const attachEvidence = useMutation({
    mutationFn: ({ id, reviewId, file }: { id: string; reviewId: string; file: File }) => uploadEvidence(id, reviewId, file),
    onSuccess: (_d, v) => { qc.invalidateQueries({ queryKey: ['contract-template-reviews', v.id] }); setError('') },
    onError: (e: unknown) => setError(errMsg(e, 'The file could not be stored')),
  })

  const downloadEvidence = async (id: string, reviewId: string, fileName: string) => {
    try {
      const res = await apiClient.get(`/api/v1/contracts/templates/${id}/legal-reviews/${reviewId}/evidence`, { responseType: 'blob' })
      const url = URL.createObjectURL(res.data as Blob)
      const a = document.createElement('a')
      a.href = url; a.download = fileName; a.click()
      URL.revokeObjectURL(url)
    } catch (e: unknown) { setError(errMsg(e, 'The evidence file could not be downloaded')) }
  }

  const withdrawReview = useMutation({
    mutationFn: ({ id, reviewId, reason }: { id: string; reviewId: string; reason: string }) =>
      apiClient.post(`/api/v1/contracts/templates/${id}/legal-reviews/${reviewId}/withdraw`, { reason }),
    onSuccess: (_d, v) => {
      qc.invalidateQueries({ queryKey: ['contract-templates'] })
      qc.invalidateQueries({ queryKey: ['contract-template-reviews', v.id] })
    },
    onError: (e: unknown) => setError(errMsg(e, 'Failed to withdraw the review')),
  })

  const startEdit = (t: Template) => {
    setForm({ name: t.name, contractType: t.contractType, description: t.description ?? '', bodyTemplate: t.bodyTemplate,
              legalArea: t.legalArea ?? '', requiredSigningMethod: t.requiredSigningMethod ?? '' })
    setEditingId(t.id); setShowCreate(true); setError('')
  }
  const askWithdraw = (templateId: string, reviewId: string) => {
    const reason = window.prompt('Why is this legal review being withdrawn?')
    if (reason && reason.trim()) withdrawReview.mutate({ id: templateId, reviewId, reason: reason.trim() })
  }

  const copyVariable = (varName: string) => {
    navigator.clipboard.writeText(`{{${varName}}}`).then(() => {
      setCopied(varName)
      setTimeout(() => setCopied(null), 1400)
    })
  }

  const cardProps = {
    canAuthor,
    onCopyVar: copyVariable, copied,
    onAction: (id: string, action: string) => act.mutate({ id, action }),
    onEdit: startEdit, onRecordReview: (t: Template) => { setReviewFor(t); setReviewError('') },
    onWithdraw: askWithdraw, busy: act.isPending,
    onAttach: (id: string, reviewId: string, file: File) => attachEvidence.mutate({ id, reviewId, file }),
    onDownload: downloadEvidence,
  }

  const systemTemplates = templates.filter(t => t.isSystem)
  const customTemplates  = templates.filter(t => !t.isSystem)

  const inp2: CSSProperties = { ...inp, fontSize: 13 }

  // ─── Render ──────────────────────────────────────────────────────────────────

  if (isLoading) return (
    <div style={{ textAlign: 'center', padding: 40, color: 'var(--hf-text-faint)' }}>Loading templates…</div>
  )

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
        <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>
          <span style={{ fontWeight: 700, color: 'var(--hf-text)' }}>{systemTemplates.length}</span> system templates ·{' '}
          <span style={{ fontWeight: 700, color: 'var(--hf-text)' }}>{customTemplates.length}</span> custom templates
        </div>
        {canAuthor && <button onClick={() => { setEditingId(null); setForm(EMPTY_FORM); setShowCreate(true); setError('') }}
          style={{ display: 'flex', alignItems: 'center', gap: 7, background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> New Template
        </button>}
      </div>

      {error && !showCreate && (
        <div style={{ marginBottom: 14, padding: '10px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>
      )}

      {/* System templates */}
      {systemTemplates.length > 0 && (
        <div style={{ marginBottom: 28 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 14 }}>
            <Lock size={11} style={{ color: 'var(--hf-text-faint)' }} />
            <span style={{ fontSize: 10, fontWeight: 700, color: 'var(--hf-text-faint)', letterSpacing: '0.07em', textTransform: 'uppercase' }}>
              System Templates — SA standard contracts
            </span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 14 }}>
            {systemTemplates.map(t => (
              <TemplateCard key={t.id} template={t} {...cardProps} />
            ))}
          </div>
        </div>
      )}

      {/* Custom templates */}
      {customTemplates.length > 0 && (
        <div>
          <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--hf-text-faint)', letterSpacing: '0.07em', textTransform: 'uppercase', marginBottom: 14 }}>
            Custom Templates
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 14 }}>
            {customTemplates.map(t => (
              <TemplateCard key={t.id} template={t} {...cardProps} />
            ))}
          </div>
        </div>
      )}

      {templates.length === 0 && (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--hf-text-faint)' }}>
          <div style={{ fontSize: 36, marginBottom: 12, opacity: 0.3 }}>📋</div>
          <div style={{ fontWeight: 600, color: 'var(--hf-text-tertiary)' }}>No templates yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>System templates seed automatically on first contract creation.</div>
        </div>
      )}

      {reviewFor && (
        <LegalReviewModal template={reviewFor} onClose={() => setReviewFor(null)} error={reviewError} busy={recordReview.isPending}
          onSubmit={(body, file) => recordReview.mutate({ id: reviewFor.id, body, file })} />
      )}

      {/* ── Create template modal ── */}
      {showCreate && (
        <div style={MODAL}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 720, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.18)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>{editingId ? 'Edit Draft Template' : 'New Contract Template'}</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}>
                <X size={20} style={{ color: 'var(--hf-text-faint)' }} />
              </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 16 }}>
              <div style={{ gridColumn: '1/-1' }}>
                <label style={lbl}>Template Name *</label>
                <input autoFocus value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g. Standard NDA — Mutual" style={inp2} />
              </div>
              <div>
                <label style={lbl}>Contract Type *</label>
                <select value={form.contractType} onChange={e => setForm(f => ({ ...f, contractType: e.target.value }))} style={{ ...inp2, background: 'var(--hf-surface)' }}>
                  {CONTRACT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Description</label>
                <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="When to use this template" style={inp2} />
              </div>
              <div>
                <label style={lbl}>Legal area</label>
                <input value={form.legalArea} onChange={e => setForm(f => ({ ...f, legalArea: e.target.value }))} placeholder="e.g. Employment, Contract law" style={inp2} />
              </div>
              <div>
                <label style={lbl}>Required signing method (set with your legal reviewer)</label>
                <select value={form.requiredSigningMethod} onChange={e => setForm(f => ({ ...f, requiredSigningMethod: e.target.value }))} style={{ ...inp2, background: 'var(--hf-surface)' }}>
                  {SIGNING_METHODS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                </select>
              </div>
            </div>

            {/* Body editor */}
            <div>
              <label style={lbl}>Template Body *</label>
              <div style={{ marginBottom: 8, padding: '10px 12px', background: 'var(--hf-info-soft)', border: '1px solid var(--hf-info-border)', borderRadius: 7, fontSize: 12, color: 'var(--hf-info-text)', lineHeight: 1.6 }}>
                <strong>Variables:</strong>{' '}
                Use <code style={{ background: 'var(--hf-surface)', padding: '1px 4px', borderRadius: 3 }}>{'{{variable_name}}'}</code> for dynamic fields, typed straight into the text.
                <code style={{ background: 'var(--hf-surface)', padding: '1px 4px', borderRadius: 3 }}>{'{{date}}'}</code> auto-resolves to today.
              </div>
              <RichTextEditor key={editingId ?? 'new'} value={form.bodyTemplate} onChange={html => setForm(f => ({ ...f, bodyTemplate: html }))} minHeight={260} />
            </div>

            {error && (
              <div style={{ marginTop: 14, padding: '10px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>
                {error}
              </div>
            )}

            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: '9px 18px', border: '1px solid var(--hf-border)', borderRadius: 9, background: 'var(--hf-surface)', fontSize: 13, cursor: 'pointer', color: 'var(--hf-text-secondary)' }}>
                Cancel
              </button>
              <button
                onClick={() => createTemplate.mutate(form)}
                disabled={!form.name || !hasContent(form.bodyTemplate) || createTemplate.isPending}
                style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '9px 22px', background: !form.name || !hasContent(form.bodyTemplate) ? 'var(--hf-text-faint)' : 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>
                {createTemplate.isPending ? 'Saving…' : editingId ? 'Save Draft' : 'Create Template'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
