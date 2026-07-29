// src/pages/customers/ConsentPanel.tsx
//
// POPIA consent status panel — shown inside the ViewModal below Customer 360.
// Shows the active consent record, allows recording and withdrawing consent.

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Shield, ShieldOff, ShieldCheck, ChevronDown, ChevronUp, AlertCircle } from 'lucide-react'
import { apiClient } from '../../api/client'

// ── Types ─────────────────────────────────────────────────────────────────────

type LawfulBasis   = 'CONSENT' | 'CONTRACT' | 'LEGAL_OBLIGATION' | 'VITAL_INTEREST' | 'PUBLIC_INTEREST' | 'LEGITIMATE_INTEREST'
type ConsentSource = 'WEB_FORM' | 'IMPORT' | 'PHONE' | 'IN_PERSON' | 'EMAIL'

interface ConsentRecord {
  id:                   string
  lawfulBasis:          LawfulBasis
  purposes:             string[]
  consentedAt:          string
  consentSource:        ConsentSource
  consentEvidence:      string | null
  withdrawnAt:          string | null
  withdrawalReason:     string | null
  retentionExpiresAt:   string | null
  lastReviewedAt:       string | null
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })

const BASIS_LABELS: Record<LawfulBasis, string> = {
  CONSENT:             'Consent',
  CONTRACT:            'Contract',
  LEGAL_OBLIGATION:    'Legal obligation',
  VITAL_INTEREST:      'Vital interest',
  PUBLIC_INTEREST:     'Public interest',
  LEGITIMATE_INTEREST: 'Legitimate interest',
}

const SOURCE_LABELS: Record<ConsentSource, string> = {
  WEB_FORM:  'Web form',
  IMPORT:    'Data import',
  PHONE:     'Phone',
  IN_PERSON: 'In person',
  EMAIL:     'Email',
}

// ── Component ─────────────────────────────────────────────────────────────────

export function ConsentPanel({ customerId }: { customerId: string }) {
  const qc                          = useQueryClient()
  const [expanded, setExpanded]     = useState(false)
  const [showRecord, setShowRecord] = useState(false)
  const [showWithdraw, setShowWithdraw] = useState(false)
  const [withdrawReason, setWithdrawReason] = useState('')
  const [recordForm, setRecordForm] = useState({
    lawfulBasis: 'CONSENT' as LawfulBasis,
    purposes:    'SERVICE_DELIVERY',
    source:      'IN_PERSON' as ConsentSource,
    evidence:    '',
    retentionYears: 7,
  })
  const [formError, setFormError] = useState('')

  const invalidate = () => qc.invalidateQueries({ queryKey: ['consent', customerId] })

  const { data, isLoading, isError } = useQuery<{ data: ConsentRecord }>({
    queryKey: ['consent', customerId],
    queryFn: () => apiClient.get(`/api/v1/crm/customers/${customerId}/consent`).then(r => r.data),
    retry: false, // 404 = no consent yet — expected, not an error
  })

  const recordMutation = useMutation({
    mutationFn: (body: object) =>
      apiClient.post(`/api/v1/crm/customers/${customerId}/consent`, body),
    onSuccess: () => { invalidate(); setShowRecord(false); setFormError('') },
    onError: (e: any) => setFormError(e?.response?.data?.message ?? 'Failed to record consent'),
  })

  const withdrawMutation = useMutation({
    mutationFn: (reason: string) =>
      apiClient.delete(`/api/v1/crm/customers/${customerId}/consent`, { data: { reason } }),
    onSuccess: () => { invalidate(); setShowWithdraw(false); setWithdrawReason('') },
    onError: (e: any) => setFormError(e?.response?.data?.message ?? 'Failed to withdraw consent'),
  })

  // FIX: "no retention-review action button" — CustomerRetentionScheduler
  // creates RETENTION_REVIEW_REQUIRED timeline entries every night, but
  // nothing called the already-built POST /consent/{id}/review endpoint,
  // so those flags just accumulated unactioned. No request body needed —
  // the controller reads the reviewing user from @RequestAttribute
  // ("userId"), populated server-side from the JWT, not something the
  // client needs to send.
  const reviewMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/v1/crm/customers/consent/${consent?.id}/review`),
    onSuccess: () => invalidate(),
    onError: (e: any) => setFormError(e?.response?.data?.message ?? 'Failed to record review'),
  })

  const consent = data?.data
  const hasConsent   = !!consent && !consent.withdrawnAt
  const isWithdrawn  = !!consent && !!consent.withdrawnAt
  const isExpired    = consent?.retentionExpiresAt
    ? new Date(consent.retentionExpiresAt) < new Date()
    : false

  const submitRecord = () => {
    setFormError('')
    recordMutation.mutate({
      lawfulBasis:    recordForm.lawfulBasis,
      purposes:       recordForm.purposes.split(',').map(s => s.trim()).filter(Boolean),
      source:         recordForm.source,
      evidence:       recordForm.evidence || null,
      retentionYears: recordForm.retentionYears,
    })
  }

  const submitWithdraw = () => {
    if (!withdrawReason.trim()) { setFormError('Withdrawal reason is required'); return }
    setFormError('')
    withdrawMutation.mutate(withdrawReason.trim())
  }

  return (
    <div style={{
      marginTop: 16, paddingTop: 16,
      borderTop: '1px solid #F1F5F9',
    }}>
      {/* Section header — always visible */}
      <button
        onClick={() => setExpanded(e => !e)}
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          width: '100%', background: 'none', border: 'none', cursor: 'pointer',
          padding: 0, marginBottom: expanded ? 12 : 0,
        }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {hasConsent
            ? <ShieldCheck size={13} color="#16A34A" />
            : isWithdrawn
              ? <ShieldOff size={13} color="#EA580C" />
              : <Shield size={13} color="#94A3B8" />
          }
          <span style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8',
                         textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            POPIA Consent
          </span>
          {hasConsent && (
            <span style={{ fontSize: 10, fontWeight: 600, padding: '1px 7px',
                           background: '#F0FDF4', color: '#16A34A',
                           border: '1px solid #BBF7D0', borderRadius: 20 }}>
              Active{isExpired ? ' — review required' : ''}
            </span>
          )}
          {isWithdrawn && (
            <span style={{ fontSize: 10, fontWeight: 600, padding: '1px 7px',
                           background: '#FFF7ED', color: '#EA580C',
                           border: '1px solid #FED7AA', borderRadius: 20 }}>
              Withdrawn
            </span>
          )}
          {!consent && !isLoading && !isError && (
            <span style={{ fontSize: 10, fontWeight: 600, padding: '1px 7px',
                           background: '#F8FAFC', color: '#94A3B8',
                           border: '1px solid #E2E8F0', borderRadius: 20 }}>
              Not recorded
            </span>
          )}
        </div>
        {expanded
          ? <ChevronUp size={14} color="#94A3B8" />
          : <ChevronDown size={14} color="#94A3B8" />
        }
      </button>

      {/* Expanded content */}
      {expanded && (
        <div>
          {isLoading && (
            <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>Loading…</p>
          )}

          {/* Active consent details */}
          {hasConsent && consent && !showWithdraw && !showRecord && (
            <div>
              <ConsentDetail label="Lawful basis"  value={BASIS_LABELS[consent.lawfulBasis]} />
              <ConsentDetail label="Purposes"      value={consent.purposes.join(', ') || '—'} />
              <ConsentDetail label="Source"        value={SOURCE_LABELS[consent.consentSource]} />
              <ConsentDetail label="Consented"     value={fmtDate(consent.consentedAt)} />
              {consent.consentEvidence && (
                <ConsentDetail label="Evidence" value={consent.consentEvidence} />
              )}
              {consent.retentionExpiresAt && (
                <ConsentDetail
                  label="Retention expires"
                  value={fmtDate(consent.retentionExpiresAt)}
                  warn={isExpired}
                />
              )}
              {consent.lastReviewedAt && (
                <ConsentDetail label="Last reviewed" value={fmtDate(consent.lastReviewedAt)} />
              )}
              {isExpired && !consent.lastReviewedAt && (
                <div style={{
                  marginTop: 8, padding: '8px 10px', background: '#FFF7ED',
                  border: '1px solid #FED7AA', borderRadius: 8,
                }}>
                  <p style={{ fontSize: 11.5, color: '#9A3412', margin: '0 0 6px', lineHeight: 1.4 }}>
                    Retention period has expired. Review whether this record still needs
                    to be kept (e.g. an outstanding invoice) or should be deleted.
                  </p>
                  <button
                    onClick={() => reviewMutation.mutate()}
                    disabled={reviewMutation.isPending}
                    style={actionBtn('#FFF7ED', '#C2410C', '#FED7AA')}>
                    {reviewMutation.isPending ? 'Recording…' : 'Mark retention reviewed'}
                  </button>
                </div>
              )}
              {/* recordReview only sets lastReviewedAt — it doesn't touch
                  retentionExpiresAt, so isExpired stays true even after
                  review. Once reviewed at least once, this becomes an
                  informational line rather than repeating the same urgent
                  banner forever, which would look like nothing happened. */}
              {isExpired && consent.lastReviewedAt && (
                <div style={{
                  marginTop: 8, padding: '8px 10px', background: '#F8FAFC',
                  border: '1px solid #E2E8F0', borderRadius: 8,
                }}>
                  <p style={{ fontSize: 11.5, color: '#64748B', margin: '0 0 6px', lineHeight: 1.4 }}>
                    Reviewed {fmtDate(consent.lastReviewedAt)} — retention is still past expiry.
                    Extend it or withdraw consent if the data no longer needs to be kept.
                  </p>
                  <button
                    onClick={() => reviewMutation.mutate()}
                    disabled={reviewMutation.isPending}
                    style={actionBtn('#F8FAFC', '#475569', '#E2E8F0')}>
                    {reviewMutation.isPending ? 'Recording…' : 'Mark reviewed again'}
                  </button>
                </div>
              )}
              <button
                onClick={() => { setShowWithdraw(true); setFormError('') }}
                style={{
                  marginTop: 10, fontSize: 12, color: '#EA580C', background: 'none',
                  border: '1px solid #FED7AA', borderRadius: 6, padding: '5px 10px',
                  cursor: 'pointer',
                }}>
                Withdraw consent
              </button>
            </div>
          )}

          {/* Withdrawn consent summary */}
          {isWithdrawn && consent && !showRecord && (
            <div>
              <ConsentDetail label="Withdrawn"         value={fmtDate(consent.withdrawnAt!)} />
              <ConsentDetail label="Reason"            value={consent.withdrawalReason ?? '—'} />
              <ConsentDetail label="Original basis"    value={BASIS_LABELS[consent.lawfulBasis]} />
              <button
                onClick={() => { setShowRecord(true); setFormError('') }}
                style={{
                  marginTop: 10, fontSize: 12, color: '#1D4ED8', background: 'none',
                  border: '1px solid #BFDBFE', borderRadius: 6, padding: '5px 10px',
                  cursor: 'pointer',
                }}>
                Record new consent
              </button>
            </div>
          )}

          {/* No consent yet */}
          {!consent && !isLoading && !showRecord && (
            <div>
              <p style={{ fontSize: 12, color: '#64748B', margin: '0 0 10px', lineHeight: 1.5 }}>
                No consent record exists for this customer.
                Record a lawful basis for processing their personal information (POPIA Section 11).
              </p>
              <button
                onClick={() => { setShowRecord(true); setFormError('') }}
                style={{
                  fontSize: 12, color: '#1D4ED8', background: '#EFF6FF',
                  border: '1px solid #BFDBFE', borderRadius: 6, padding: '5px 10px',
                  cursor: 'pointer',
                }}>
                Record consent
              </button>
            </div>
          )}

          {/* Withdraw form */}
          {showWithdraw && (
            <div style={{ marginTop: 8 }}>
              <label style={{ fontSize: 12, fontWeight: 600, color: '#374151', display: 'block', marginBottom: 4 }}>
                Withdrawal reason <span style={{ color: '#DC2626' }}>*</span>
              </label>
              <textarea
                value={withdrawReason}
                onChange={e => setWithdrawReason(e.target.value)}
                placeholder="e.g. Customer requested opt-out via email 2025-06-25"
                rows={2}
                style={{
                  width: '100%', padding: '8px 10px', fontSize: 13,
                  border: '1.5px solid #E2E8F0', borderRadius: 8,
                  fontFamily: 'inherit', resize: 'vertical', boxSizing: 'border-box',
                }}
              />
              {formError && <ErrLine msg={formError} />}
              <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                <button onClick={submitWithdraw} disabled={withdrawMutation.isPending}
                  style={actionBtn('#FEF2F2', '#DC2626', '#FECACA')}>
                  {withdrawMutation.isPending ? 'Withdrawing…' : 'Confirm withdrawal'}
                </button>
                <button onClick={() => { setShowWithdraw(false); setFormError('') }}
                  style={actionBtn('#F8FAFC', '#374151', '#E2E8F0')}>
                  Cancel
                </button>
              </div>
            </div>
          )}

          {/* Record consent form */}
          {showRecord && (
            <div style={{ marginTop: 8 }}>
              <FormRow label="Lawful basis">
                <select value={recordForm.lawfulBasis}
                  onChange={e => setRecordForm(p => ({ ...p, lawfulBasis: e.target.value as LawfulBasis }))}
                  style={selectStyle}>
                  {Object.entries(BASIS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </FormRow>
              <FormRow label="Source">
                <select value={recordForm.source}
                  onChange={e => setRecordForm(p => ({ ...p, source: e.target.value as ConsentSource }))}
                  style={selectStyle}>
                  {Object.entries(SOURCE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </FormRow>
              <FormRow label="Purposes (comma-separated)">
                <input value={recordForm.purposes}
                  onChange={e => setRecordForm(p => ({ ...p, purposes: e.target.value }))}
                  placeholder="SERVICE_DELIVERY, MARKETING"
                  style={inputStyle} />
              </FormRow>
              <FormRow label="Evidence (optional)">
                <input value={recordForm.evidence}
                  onChange={e => setRecordForm(p => ({ ...p, evidence: e.target.value }))}
                  placeholder="Signed service agreement 2025-06-25"
                  style={inputStyle} />
              </FormRow>
              <FormRow label="Retention (years)">
                <input type="number" min={1} max={30} value={recordForm.retentionYears}
                  onChange={e => setRecordForm(p => ({ ...p, retentionYears: Number(e.target.value) }))}
                  style={{ ...inputStyle, width: 80 }} />
              </FormRow>
              {formError && <ErrLine msg={formError} />}
              <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                <button onClick={submitRecord} disabled={recordMutation.isPending}
                  style={actionBtn('#EFF6FF', '#1D4ED8', '#BFDBFE')}>
                  {recordMutation.isPending ? 'Recording…' : 'Record consent'}
                </button>
                <button onClick={() => { setShowRecord(false); setFormError('') }}
                  style={actionBtn('#F8FAFC', '#374151', '#E2E8F0')}>
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

function ConsentDetail({ label, value, warn = false }: { label: string; value: string; warn?: boolean }) {
  return (
    <div style={{ display: 'flex', gap: 8, marginBottom: 4, fontSize: 12 }}>
      <span style={{ color: '#94A3B8', minWidth: 120, flexShrink: 0 }}>{label}</span>
      <span style={{ color: warn ? '#EA580C' : '#0F172A', fontWeight: warn ? 600 : 400 }}>{value}</span>
    </div>
  )
}

function FormRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 8 }}>
      <label style={{ fontSize: 11, fontWeight: 600, color: '#374151', display: 'block', marginBottom: 3 }}>
        {label}
      </label>
      {children}
    </div>
  )
}

function ErrLine({ msg }: { msg: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginTop: 6, fontSize: 12, color: '#DC2626' }}>
      <AlertCircle size={11} />{msg}
    </div>
  )
}

const selectStyle: React.CSSProperties = {
  width: '100%', padding: '6px 10px', fontSize: 13,
  border: '1.5px solid #E2E8F0', borderRadius: 6,
  fontFamily: 'inherit', background: 'white',
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '6px 10px', fontSize: 13,
  border: '1.5px solid #E2E8F0', borderRadius: 6,
  fontFamily: 'inherit', boxSizing: 'border-box',
}

const actionBtn = (bg: string, color: string, border: string): React.CSSProperties => ({
  fontSize: 12, color, background: bg, border: `1px solid ${border}`,
  borderRadius: 6, padding: '5px 12px', cursor: 'pointer', fontFamily: 'inherit',
})
