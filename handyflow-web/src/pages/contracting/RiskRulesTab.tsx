// src/pages/contracting/RiskRulesTab.tsx
// Risk rules. No rules are supplied: your legal reviewer decides what to check. A rule is a draft until its review is recorded; only
// a published rule whose review has not lapsed is run against contracts. A rule marked "blocks sending" stops a contract being sent
// for signing while it fails.
import { useState, type CSSProperties } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X } from 'lucide-react'
import { usePermissions } from '../../hooks/usePermission'
import { RULE_TYPES, SEVERITIES, type RiskRule } from './riskRules'

const CLAUSE_CATEGORIES = ['DEFINITIONS', 'FORCE_MAJEURE', 'DOMICILIUM', 'PAYMENT', 'TERMINATION', 'CONFIDENTIALITY', 'LIABILITY', 'DISPUTE_RESOLUTION', 'DATA_PROTECTION', 'OTHER']
const STATUS_LABEL: Record<string, string> = { DRAFT: 'Draft', APPROVED: 'Approved', PUBLISHED: 'Published', SUPERSEDED: 'Superseded', WITHDRAWN: 'Withdrawn' }
const LEGAL_LABEL: Record<string, string> = { NOT_REVIEWED: 'Not reviewed', REVIEWED: 'Legal review recorded', REVIEW_EXPIRED: 'Review expired', SUPERSEDED: 'Superseded', WITHDRAWN: 'Withdrawn' }
const LEGAL_COLOR: Record<string, string> = { REVIEWED: 'var(--hf-success-text-strong)', NOT_REVIEWED: 'var(--hf-text-muted)', REVIEW_EXPIRED: 'var(--hf-danger-text)', WITHDRAWN: 'var(--hf-danger-text)', SUPERSEDED: 'var(--hf-text-muted)' }
const typeLabel = (t: string) => RULE_TYPES.find(([k]) => k === t)?.[1] ?? t

const inp: CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: 'var(--hf-surface)' }
const lbl: CSSProperties = { display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 4 }
const btn: CSSProperties = { padding: '5px 11px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }
const MODAL: CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }

const errMsg = (e: unknown, fallback: string): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
const list = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T

const EMPTY = { name: '', description: '', ruleType: 'REQUIRES_PHRASE', paramText: '', paramNumber: '', appliesToTypes: '', severity: 'MEDIUM', blocksSending: false, message: '', remediation: '', legalArea: '', jurisdiction: 'South Africa' }
const EMPTY_REVIEW = { reviewerName: '', reviewerProfessionalDetails: '', reviewDate: new Date().toISOString().slice(0, 10), nextReviewDate: '', notes: '' }

export default function RiskRulesTab() {
  const qc = useQueryClient()
  const canAuthor = usePermissions(['CONTRACTS_TEMPLATES', 'CONTRACTS_ADMIN'], 'any')
  const [editing, setEditing] = useState<RiskRule | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [reviewing, setReviewing] = useState<RiskRule | null>(null)
  const [review, setReview] = useState(EMPTY_REVIEW)
  const [error, setError] = useState('')

  const { data: rules = [], isLoading } = useQuery<RiskRule[]>({
    queryKey: ['contract-risk-rules'],
    queryFn: async () => list<RiskRule[]>(await apiClient.get('/api/v1/contracts/risk-rules')),
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['contract-risk-rules'] }); setError('') }
  const kind = RULE_TYPES.find(([k]) => k === form.ruleType)?.[2] ?? 'none'

  const payload = () => ({
    ...form,
    paramText: kind === 'text' || kind === 'clause' ? form.paramText.trim() : null,
    paramNumber: kind === 'number' ? Number(form.paramNumber) : null,
    appliesToTypes: form.appliesToTypes.trim() || null, description: form.description || null, remediation: form.remediation || null, legalArea: form.legalArea || null,
  })
  const save = useMutation({
    mutationFn: () => editing ? apiClient.put(`/api/v1/contracts/risk-rules/${editing.id}`, payload()) : apiClient.post('/api/v1/contracts/risk-rules', payload()),
    onSuccess: () => { refresh(); setShowForm(false); setEditing(null); setForm(EMPTY) },
    onError: (e: unknown) => setError(errMsg(e, 'Could not save the rule')),
  })
  const act = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: Record<string, unknown> }) => apiClient.post(`/api/v1/contracts/risk-rules/${id}/${action}`, body ?? {}),
    onSuccess: refresh,
    onError: (e: unknown) => setError(errMsg(e, 'That step could not be completed')),
  })
  const approve = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/risk-rules/${reviewing!.id}/approve`, { ...review, nextReviewDate: review.nextReviewDate || null, notes: review.notes || null }),
    onSuccess: () => { refresh(); setReviewing(null) },
    onError: (e: unknown) => setError(errMsg(e, 'Could not record the review')),
  })

  const openNew = () => { setEditing(null); setForm(EMPTY); setShowForm(true); setError('') }
  const openEdit = (r: RiskRule) => {
    setEditing(r); setError(''); setShowForm(true)
    setForm({ name: r.name, description: r.description ?? '', ruleType: r.ruleType, paramText: r.paramText ?? '', paramNumber: r.paramNumber == null ? '' : String(r.paramNumber), appliesToTypes: r.appliesToTypes ?? '',
      severity: r.severity, blocksSending: r.blocksSending, message: r.message, remediation: r.remediation ?? '', legalArea: r.legalArea ?? '', jurisdiction: r.jurisdiction })
  }
  const withdraw = (r: RiskRule) => { const why = window.prompt('Why is this rule being withdrawn?'); if (why && why.trim()) act.mutate({ id: r.id, action: 'withdraw', body: { reason: why.trim() } }) }
  const needsValue = kind === 'none' || (kind === 'number' ? form.paramNumber.trim() !== '' && Number(form.paramNumber) >= 0 : form.paramText.trim() !== '')
  const validForm = form.name.trim() && form.message.trim() && needsValue
  const validReview = review.reviewerName.trim() && review.reviewerProfessionalDetails.trim() && review.reviewDate

  return (
    <div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 10 }}>
        <div style={{ flex: 1 }} />
        {canAuthor && <button onClick={openNew} style={{ ...btn, display: 'flex', gap: 6, alignItems: 'center', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', padding: '8px 14px' }}><Plus size={14} /> Add rule</button>}
      </div>
      <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 12, lineHeight: 1.5 }}>
        No rules are supplied. Add the checks your legal reviewer wants made, record their review, then publish the rule. Until a rule is published, contracts show as "Not assessed", which is not the same as low risk.
        A recorded review is evidence that a review took place on that date, not a guarantee. A failed rule means the contract does not meet that rule, not that it is unlawful.
      </div>
      {error && !showForm && !reviewing && <div style={{ marginBottom: 10, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}

      {isLoading ? <div style={{ color: 'var(--hf-text-muted)' }}>Loading…</div> : rules.length === 0 ? (
        <div style={{ color: 'var(--hf-text-faint)', fontSize: 14, padding: '24px 0' }}>There are no risk rules.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rules.map(r => (
            <div key={r.id} style={{ border: '1px solid var(--hf-border)', borderRadius: 10, padding: '12px 14px', background: 'var(--hf-surface)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
                <div style={{ fontWeight: 700, color: 'var(--hf-text)' }}>{r.name} <span style={{ fontWeight: 400, color: 'var(--hf-text-muted)', fontSize: 12 }}>v{r.versionNo} · {r.severity.toLowerCase()}{r.blocksSending ? ' · blocks sending' : ''}</span></div>
                <div style={{ fontSize: 12 }}><strong>{STATUS_LABEL[r.status]}</strong> · <span style={{ color: LEGAL_COLOR[r.legalStatus] }}>{LEGAL_LABEL[r.legalStatus]}</span></div>
              </div>
              <div style={{ fontSize: 13, color: 'var(--hf-text-secondary)', marginTop: 4 }}>
                {typeLabel(r.ruleType)}{r.paramText ? `: ${r.paramText}` : ''}{r.paramNumber != null ? `: ${r.paramNumber}` : ''}
                {r.appliesToTypes ? ` · only for ${r.appliesToTypes}` : ' · all contract types'}
              </div>
              <div style={{ fontSize: 13, color: 'var(--hf-text-secondary)', marginTop: 2 }}>Shown when it fails: {r.message}</div>
              {r.reviewerName && (
                <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>
                  Reviewed by {r.reviewerName}, {r.reviewerProfessionalDetails} on {r.reviewDate}{r.nextReviewDate ? ` · next review ${r.nextReviewDate}` : ''}
                </div>
              )}
              {r.legalStatus === 'REVIEW_EXPIRED' && <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginTop: 2 }}>This rule is not being run until it has a new review.</div>}
              {r.withdrawnReason && <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginTop: 2 }}>Withdrawn: {r.withdrawnReason}</div>}
              {canAuthor && (
                <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                  {r.status === 'DRAFT' && <button style={btn} disabled={act.isPending} onClick={() => openEdit(r)}>Edit</button>}
                  {r.status === 'DRAFT' && <button style={btn} disabled={act.isPending} onClick={() => { setReviewing(r); setReview(EMPTY_REVIEW); setError('') }}>Record legal review</button>}
                  {r.status === 'APPROVED' && <button style={btn} disabled={act.isPending} onClick={() => act.mutate({ id: r.id, action: 'publish' })}>Publish</button>}
                  {r.status === 'PUBLISHED' && <button style={btn} disabled={act.isPending} onClick={() => act.mutate({ id: r.id, action: 'new-version' })}>New version</button>}
                  {(r.status === 'PUBLISHED' || r.status === 'APPROVED') && <button style={btn} disabled={act.isPending} onClick={() => withdraw(r)}>Withdraw</button>}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <div style={MODAL}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 620, maxHeight: '92vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>{editing ? 'Edit draft rule' : 'Add risk rule'}</h3>
              <button onClick={() => setShowForm(false)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Name *</label><input style={inp} value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} /></div>
              <div style={{ gridColumn: '1/-1' }}>
                <label style={lbl}>Check</label>
                <select style={inp} value={form.ruleType} onChange={e => setForm(p => ({ ...p, ruleType: e.target.value, paramText: '', paramNumber: '' }))}>
                  {RULE_TYPES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                </select>
              </div>
              {kind === 'clause' && (
                <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Clause category *</label>
                  <select style={inp} value={form.paramText} onChange={e => setForm(p => ({ ...p, paramText: e.target.value }))}><option value="">Select…</option>{CLAUSE_CATEGORIES.map(c => <option key={c} value={c}>{c.replace('_', ' ').toLowerCase()}</option>)}</select></div>
              )}
              {kind === 'text' && <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Wording *</label><input style={inp} value={form.paramText} onChange={e => setForm(p => ({ ...p, paramText: e.target.value }))} /></div>}
              {kind === 'number' && <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Number *</label><input type="number" min={0} style={inp} value={form.paramNumber} onChange={e => setForm(p => ({ ...p, paramNumber: e.target.value }))} /></div>}
              <div><label style={lbl}>Severity</label><select style={inp} value={form.severity} onChange={e => setForm(p => ({ ...p, severity: e.target.value }))}>{SEVERITIES.map(s => <option key={s} value={s}>{s.toLowerCase()}</option>)}</select></div>
              <div><label style={lbl}>Only for contract types (comma separated, blank for all)</label><input style={inp} value={form.appliesToTypes} onChange={e => setForm(p => ({ ...p, appliesToTypes: e.target.value }))} /></div>
              <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Message shown when the rule fails *</label><input style={inp} value={form.message} onChange={e => setForm(p => ({ ...p, message: e.target.value }))} /></div>
              <div style={{ gridColumn: '1/-1' }}><label style={lbl}>What to do about it</label><input style={inp} value={form.remediation} onChange={e => setForm(p => ({ ...p, remediation: e.target.value }))} /></div>
              <div><label style={lbl}>Legal area</label><input style={inp} value={form.legalArea} onChange={e => setForm(p => ({ ...p, legalArea: e.target.value }))} /></div>
              <div><label style={lbl}>Jurisdiction</label><input style={inp} value={form.jurisdiction} onChange={e => setForm(p => ({ ...p, jurisdiction: e.target.value }))} /></div>
              <label style={{ gridColumn: '1/-1', display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, color: 'var(--hf-text-secondary)' }}>
                <input type="checkbox" checked={form.blocksSending} onChange={e => setForm(p => ({ ...p, blocksSending: e.target.checked }))} />
                Stop the contract being sent for signing while this rule fails
              </label>
            </div>
            {error && <div style={{ marginTop: 12, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
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
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>Record legal review: {reviewing.name}</h3>
              <button onClick={() => setReviewing(null)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
            </div>
            <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 12, lineHeight: 1.5 }}>
              Record the approval of this rule by a South African attorney or other qualified legal practitioner. Editing the rule afterwards clears the review.
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div><label style={lbl}>Reviewer name *</label><input style={inp} value={review.reviewerName} onChange={e => setReview(p => ({ ...p, reviewerName: e.target.value }))} /></div>
              <div><label style={lbl}>Professional details *</label><input style={inp} value={review.reviewerProfessionalDetails} onChange={e => setReview(p => ({ ...p, reviewerProfessionalDetails: e.target.value }))} placeholder="e.g. admitted attorney, firm" /></div>
              <div><label style={lbl}>Review date *</label><input type="date" style={inp} value={review.reviewDate} onChange={e => setReview(p => ({ ...p, reviewDate: e.target.value }))} /></div>
              <div><label style={lbl}>Next review date</label><input type="date" style={inp} value={review.nextReviewDate} onChange={e => setReview(p => ({ ...p, nextReviewDate: e.target.value }))} /></div>
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
