// src/pages/contracting/ClausesTab.tsx
// The clause library. The wording comes from your legal reviewer: nothing is supplied. A clause is a draft until a review of that
// exact wording is recorded; only a published clause whose review has not lapsed can go into a contract.
import { useState, type CSSProperties } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X } from 'lucide-react'
import { usePermissions } from '../../hooks/usePermission'
import { sanitizeHtml } from './sanitizeHtml'
import { clauseFormProblem, publishBlocker, kindLabel, needsApprovalMessage, CLAUSE_KINDS } from './clauseRules'
import { RichTextEditor } from './RichTextEditor'
import { hasContent } from './richText'

export interface Clause {
  id: string; familyId: string; versionNo: number; title: string; category: string; body: string; legalArea?: string | null
  jurisdiction: string; status: 'DRAFT' | 'APPROVED' | 'PUBLISHED' | 'SUPERSEDED' | 'WITHDRAWN'
  legalStatus: 'NOT_REVIEWED' | 'REVIEWED' | 'REVIEW_EXPIRED' | 'SUPERSEDED' | 'WITHDRAWN'
  reviewerName?: string | null; reviewerProfessionalDetails?: string | null; reviewDate?: string | null; nextReviewDate?: string | null
  reviewNotes?: string | null; withdrawnReason?: string | null; usable: boolean
  evidenceDocumentId?: string | null; evidenceFileName?: string | null
  clauseKind?: string; sourceReference?: string | null; evidenceRequired?: boolean; evidenceRequiredReason?: string | null
}

const CLAUSE_CATEGORIES = [
  ['DEFINITIONS', 'Definitions'], ['FORCE_MAJEURE', 'Force majeure'], ['DOMICILIUM', 'Domicilium'], ['PAYMENT', 'Payment'],
  ['TERMINATION', 'Termination'], ['CONFIDENTIALITY', 'Confidentiality'], ['LIABILITY', 'Liability'],
  ['DISPUTE_RESOLUTION', 'Dispute resolution'], ['DATA_PROTECTION', 'Data protection'], ['OTHER', 'Other'],
] as const

const STATUS_LABEL: Record<string, string> = { DRAFT: 'Draft', APPROVED: 'Approved', PUBLISHED: 'Published', SUPERSEDED: 'Superseded', WITHDRAWN: 'Withdrawn' }
const LEGAL_LABEL: Record<string, string> = { NOT_REVIEWED: 'Not reviewed', REVIEWED: 'Legal review recorded', REVIEW_EXPIRED: 'Review expired', SUPERSEDED: 'Superseded', WITHDRAWN: 'Withdrawn' }
const LEGAL_COLOR: Record<string, string> = { REVIEWED: 'var(--hf-success-text-strong)', NOT_REVIEWED: 'var(--hf-text-muted)', REVIEW_EXPIRED: 'var(--hf-danger-text)', WITHDRAWN: 'var(--hf-danger-text)', SUPERSEDED: 'var(--hf-text-muted)' }
const catLabel = (c: string) => CLAUSE_CATEGORIES.find(([k]) => k === c)?.[1] ?? c

const inp: CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: 'var(--hf-surface)' }
const lbl: CSSProperties = { display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 4 }
const btn: CSSProperties = { padding: '5px 11px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }
const MODAL: CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }

const errMsg = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
const list = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T

const EMPTY = { title: '', category: 'OTHER', body: '', legalArea: '', jurisdiction: 'South Africa', clauseKind: 'STANDARD', sourceReference: '' }
const EMPTY_REVIEW = { reviewerName: '', reviewerProfessionalDetails: '', reviewDate: new Date().toISOString().slice(0, 10), nextReviewDate: '', notes: '' }

export default function ClausesTab() {
  const qc = useQueryClient()
  const canAuthor = usePermissions(['CONTRACTS_TEMPLATES', 'CONTRACTS_MANAGE'], 'any')
  const [category, setCategory] = useState('')
  const [editing, setEditing] = useState<Clause | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [reviewing, setReviewing] = useState<Clause | null>(null)
  const [review, setReview] = useState(EMPTY_REVIEW)
  const [reviewFile, setReviewFile] = useState<File | null>(null)
  const [error, setError] = useState('')
  const [askFor, setAskFor] = useState<string | null>(null)

  const { data: clauses = [], isLoading } = useQuery<Clause[]>({
    queryKey: ['contract-clauses', category],
    queryFn: async () => list<Clause[]>(await apiClient.get(`/api/v1/contracts/clauses${category ? `?category=${category}` : ''}`)),
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['contract-clauses'] }); setError('') }

  const save = useMutation({
    mutationFn: () => editing ? apiClient.put(`/api/v1/contracts/clauses/${editing.id}`, form) : apiClient.post('/api/v1/contracts/clauses', form),
    onSuccess: () => { refresh(); setShowForm(false); setEditing(null); setForm(EMPTY) },
    onError: (e: unknown) => setError(errMsg(e, 'Could not save the clause')),
  })
  const act = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: Record<string, unknown> }) => apiClient.post(`/api/v1/contracts/clauses/${id}/${action}`, body ?? {}),
    onSuccess: () => { refresh(); setAskFor(null) },
    onError: (e: unknown, v) => {
      const m = errMsg(e, 'That step could not be completed')
      setError(m)
      setAskFor(v.action === 'publish' && needsApprovalMessage(m) ? v.id : null)
    },
  })
  const requestApproval = useMutation({
    mutationFn: (id: string) => apiClient.post('/api/v1/contracts/approvals', { action: 'PUBLISH_CLAUSE', subjectId: id, note: null }),
    onSuccess: () => { refresh(); setAskFor(null); setError('Approval requested. Someone else can now review it under Approvals.') },
    onError: (e: unknown) => setError(errMsg(e, 'Could not ask for approval')),
  })
  const uploadEvidence = (id: string, fl: File) => {
    const fd = new FormData()
    fd.append('file', fl)
    return apiClient.post(`/api/v1/contracts/clauses/${id}/evidence`, fd)
  }
  // Records the approval first, then stores the reviewer's file. If only the file fails the approval stays recorded and the file can
  // be attached again from the clause.
  const approve = useMutation({
    mutationFn: async () => {
      const id = reviewing!.id
      await apiClient.post(`/api/v1/contracts/clauses/${id}/approve`, { ...review, nextReviewDate: review.nextReviewDate || null, notes: review.notes || null })
      if (!reviewFile) return { fileError: '' }
      try { await uploadEvidence(id, reviewFile); return { fileError: '' } }
      catch (e: unknown) { return { fileError: errMsg(e, 'The file could not be stored') } }
    },
    onSuccess: (r) => {
      refresh(); setReviewing(null); setReviewFile(null)
      if (r.fileError) setError(`The approval was recorded, but the evidence file was not stored: ${r.fileError}. Attach it from the clause.`)
    },
    onError: (e: unknown) => setError(errMsg(e, 'Could not record the review')),
  })
  const attach = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => uploadEvidence(id, file),
    onSuccess: refresh,
    onError: (e: unknown) => setError(errMsg(e, 'The file could not be stored')),
  })
  const download = async (c: Clause) => {
    try {
      const res = await apiClient.get(`/api/v1/contracts/clauses/${c.id}/evidence`, { responseType: 'blob' })
      const url = URL.createObjectURL(res.data as Blob)
      const a = document.createElement('a')
      a.href = url; a.download = c.evidenceFileName ?? 'clause-review-evidence'; a.click()
      URL.revokeObjectURL(url)
    } catch (e: unknown) { setError(errMsg(e, 'The evidence file could not be downloaded')) }
  }

  const openNew = () => { setEditing(null); setForm(EMPTY); setShowForm(true); setError('') }
  const openEdit = (c: Clause) => { setEditing(c); setForm({ title: c.title, category: c.category, body: c.body, legalArea: c.legalArea ?? '', jurisdiction: c.jurisdiction, clauseKind: c.clauseKind ?? 'STANDARD', sourceReference: c.sourceReference ?? '' }); setShowForm(true); setError('') }
  const withdraw = (c: Clause) => { const r = window.prompt('Why is this clause being withdrawn?'); if (r && r.trim()) act.mutate({ id: c.id, action: 'withdraw', body: { reason: r.trim() } }) }
  const formProblem = clauseFormProblem(form, hasContent(form.body))
  const validForm = !formProblem
  const validReview = review.reviewerName.trim() && review.reviewerProfessionalDetails.trim() && review.reviewDate

  return (
    <div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', marginBottom: 10 }}>
        <select value={category} onChange={e => setCategory(e.target.value)} style={{ ...inp, width: 'auto', fontSize: 13 }}>
          <option value="">All categories</option>
          {CLAUSE_CATEGORIES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <div style={{ flex: 1 }} />
        {canAuthor && <button onClick={openNew} style={{ ...btn, display: 'flex', gap: 6, alignItems: 'center', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', padding: '8px 14px' }}><Plus size={14} /> Add clause</button>}
      </div>
      <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 12, lineHeight: 1.5 }}>
        No clauses are supplied. Add the wording your legal reviewer provides (for example definitions, force majeure and domicilium), record their review, then publish it. A recorded review is evidence that a review took place on that date, not a guarantee that the clause suits every contract.
      </div>
      {error && !showForm && !reviewing && <div style={{ marginBottom: 10, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}

      {isLoading ? <div style={{ color: 'var(--hf-text-muted)' }}>Loading…</div> : clauses.length === 0 ? (
        <div style={{ color: 'var(--hf-text-faint)', fontSize: 14, padding: '24px 0' }}>The library is empty.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {clauses.map(c => (
            <div key={c.id} style={{ border: '1px solid var(--hf-border)', borderRadius: 10, padding: '12px 14px', background: 'var(--hf-surface)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
                <div style={{ fontWeight: 700, color: 'var(--hf-text)' }}>{c.title} <span style={{ fontWeight: 400, color: 'var(--hf-text-muted)', fontSize: 12 }}>v{c.versionNo} · {catLabel(c.category)}</span>
                  {c.clauseKind && c.clauseKind !== 'STANDARD' && <span style={{ marginLeft: 8, fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 999, background: 'var(--hf-warning-soft)', color: 'var(--hf-warning-text)' }}>{kindLabel(c.clauseKind)}</span>}</div>
                <div style={{ fontSize: 12 }}>
                  <strong>{STATUS_LABEL[c.status]}</strong> · <span style={{ color: LEGAL_COLOR[c.legalStatus] }}>{LEGAL_LABEL[c.legalStatus]}</span>
                </div>
              </div>
              {c.reviewerName && (
                <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>
                  Reviewed by {c.reviewerName}, {c.reviewerProfessionalDetails} on {c.reviewDate}{c.nextReviewDate ? ` · next review ${c.nextReviewDate}` : ''}
                </div>
              )}
              {(c.status === 'APPROVED' || c.status === 'PUBLISHED') && (
                <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>
                  Evidence: {c.evidenceDocumentId
                    ? <button style={{ ...btn, padding: '2px 8px' }} onClick={() => download(c)}>Download {c.evidenceFileName ?? 'file'}</button>
                    : <span style={{ color: 'var(--hf-warning-text)' }}>none attached</span>}
                </div>
              )}
              {c.sourceReference && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>Source: {c.sourceReference}</div>}
              {c.evidenceRequired && !c.evidenceDocumentId && c.status !== 'DRAFT' && c.status !== 'SUPERSEDED' && c.status !== 'WITHDRAWN' && (
                <div role="note" style={{ fontSize: 12, color: 'var(--hf-warning-text)', marginTop: 2 }}>{publishBlocker(c)}</div>
              )}
              {c.legalStatus === 'REVIEW_EXPIRED' && <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginTop: 2 }}>This clause cannot be put into contracts until it has a new review.</div>}
              {c.withdrawnReason && <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginTop: 2 }}>Withdrawn: {c.withdrawnReason}</div>}
              <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.7, color: 'var(--hf-text-secondary)', maxHeight: 140, overflowY: 'auto', border: '1px solid var(--hf-border)', borderRadius: 8, padding: '6px 10px', background: 'var(--hf-surface-muted)' }}
                dangerouslySetInnerHTML={{ __html: sanitizeHtml(c.body) }} />
              {canAuthor && (
                <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                  {c.status === 'DRAFT' && <button style={btn} disabled={act.isPending} onClick={() => openEdit(c)}>Edit</button>}
                  {c.status === 'DRAFT' && <button style={btn} disabled={act.isPending} onClick={() => { setReviewing(c); setReview(EMPTY_REVIEW); setReviewFile(null); setError('') }}>Record legal review</button>}
                  {(c.status === 'APPROVED' || c.status === 'PUBLISHED') && (
                    <label style={{ ...btn, display: 'inline-block' }}>
                      {c.evidenceDocumentId ? 'Upload replacement' : 'Attach evidence'}
                      <input type="file" accept=".pdf,.png,.jpg,.jpeg,.doc,.docx" style={{ display: 'none' }}
                        onChange={e => { const fl = e.target.files?.[0]; if (fl) attach.mutate({ id: c.id, file: fl }); e.target.value = '' }} />
                    </label>
                  )}
                  {c.status === 'APPROVED' && <button style={btn} disabled={act.isPending || !!publishBlocker(c)} title={publishBlocker(c) ?? undefined} onClick={() => act.mutate({ id: c.id, action: 'publish' })}>Publish</button>}
                  {c.status === 'APPROVED' && askFor === c.id && <button style={btn} disabled={requestApproval.isPending} onClick={() => requestApproval.mutate(c.id)}>Ask for approval</button>}
                  {c.status === 'APPROVED' && <button style={btn} disabled={act.isPending} onClick={() => act.mutate({ id: c.id, action: 'return-to-draft' })}>Return to draft</button>}
                  {c.status === 'PUBLISHED' && <button style={btn} disabled={act.isPending} onClick={() => act.mutate({ id: c.id, action: 'new-version' })}>New version</button>}
                  {(c.status === 'PUBLISHED' || c.status === 'APPROVED') && <button style={btn} disabled={act.isPending} onClick={() => withdraw(c)}>Withdraw</button>}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <div style={MODAL}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 640, maxHeight: '92vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>{editing ? 'Edit draft clause' : 'Add clause'}</h3>
              <button onClick={() => setShowForm(false)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Title *</label><input style={inp} value={form.title} onChange={e => setForm(p => ({ ...p, title: e.target.value }))} /></div>
              <div><label style={lbl}>Category</label><select style={inp} value={form.category} onChange={e => setForm(p => ({ ...p, category: e.target.value }))}>{CLAUSE_CATEGORIES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></div>
              <div><label style={lbl}>Legal area</label><input style={inp} value={form.legalArea} onChange={e => setForm(p => ({ ...p, legalArea: e.target.value }))} /></div>
              <div><label style={lbl}>Kind of wording</label>
                <select style={inp} value={form.clauseKind} onChange={e => setForm(p => ({ ...p, clauseKind: e.target.value }))}>{CLAUSE_KINDS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></div>
              <div><label style={lbl}>Source{form.clauseKind === 'SOURCED' ? ' *' : ''}</label>
                <input style={inp} value={form.sourceReference} placeholder="Where the wording came from" onChange={e => setForm(p => ({ ...p, sourceReference: e.target.value }))} /></div>
              <div style={{ gridColumn: '1/-1', fontSize: 12, color: 'var(--hf-text-muted)' }}>{CLAUSE_KINDS.find(([k]) => k === form.clauseKind)?.[2]}</div>
              <div style={{ gridColumn: '1/-1' }}>
                <label style={lbl}>Wording *</label>
                <RichTextEditor key={editing?.id ?? 'new'} value={form.body} onChange={html => setForm(p => ({ ...p, body: html }))} minHeight={200} />
              </div>
            </div>
            {error && <div style={{ marginTop: 12, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
            {formProblem && form.title.trim() && <div style={{ marginTop: 8, fontSize: 12, color: 'var(--hf-text-muted)' }}>{formProblem}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 18 }}>
              <button onClick={() => setShowForm(false)} style={btn}>Cancel</button>
              <button disabled={!validForm || save.isPending} onClick={() => save.mutate()} style={{ ...btn, background: validForm ? 'var(--hf-primary)' : 'var(--hf-text-faint)', color: 'var(--hf-text-on-solid)', border: 'none' }}>{save.isPending ? 'Saving…' : 'Save draft'}</button>
            </div>
          </div>
        </div>
      )}

      {reviewing && (
        <div style={MODAL}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 560 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>Record legal review: {reviewing.title}</h3>
              <button onClick={() => setReviewing(null)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
            </div>
            <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 12, lineHeight: 1.5 }}>
              Record the approval of this exact wording by a South African attorney or other qualified legal practitioner. Editing the clause afterwards clears the review.
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div><label style={lbl}>Reviewer name *</label><input style={inp} value={review.reviewerName} onChange={e => setReview(p => ({ ...p, reviewerName: e.target.value }))} /></div>
              <div><label style={lbl}>Professional details *</label><input style={inp} value={review.reviewerProfessionalDetails} onChange={e => setReview(p => ({ ...p, reviewerProfessionalDetails: e.target.value }))} placeholder="e.g. admitted attorney, firm" /></div>
              <div><label style={lbl}>Review date *</label><input type="date" style={inp} value={review.reviewDate} onChange={e => setReview(p => ({ ...p, reviewDate: e.target.value }))} /></div>
              <div><label style={lbl}>Next review date</label><input type="date" style={inp} value={review.nextReviewDate} onChange={e => setReview(p => ({ ...p, nextReviewDate: e.target.value }))} /></div>
              <div style={{ gridColumn: '1/-1' }}>
                <label style={lbl}>Evidence: the reviewer's signed-off review (PDF or scan, optional)</label>
                <input type="file" aria-label="Review evidence file" accept=".pdf,.png,.jpg,.jpeg,.doc,.docx" onChange={e => setReviewFile(e.target.files?.[0] ?? null)} />
              </div>
              <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Notes</label><textarea rows={2} style={inp} value={review.notes} onChange={e => setReview(p => ({ ...p, notes: e.target.value }))} /></div>
            </div>
            {error && <div style={{ marginTop: 12, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 18 }}>
              <button onClick={() => setReviewing(null)} style={btn}>Cancel</button>
              <button disabled={!validReview || approve.isPending} onClick={() => approve.mutate()} style={{ ...btn, background: validReview ? 'var(--hf-primary)' : 'var(--hf-text-faint)', color: 'var(--hf-text-on-solid)', border: 'none' }}>{approve.isPending ? 'Saving…' : 'Record approval'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
