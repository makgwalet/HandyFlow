// src/pages/marketing/SuppressionsTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, ShieldAlert, Search, Unlock, AlertTriangle, CheckCircle,
  ChevronLeft, ChevronRight,
} from 'lucide-react'

const PAGE_SIZE = 25
const EMAIL_RE  = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

const fmtDT = (d: any) => d
  ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  : '—'

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }

const REASONS: Record<string, { label: string; color: string; bg: string; help: string }> = {
  UNSUBSCRIBED: { label: 'Unsubscribed', color: '#9333EA', bg: '#FAF5FF', help: 'Recipient used the unsubscribe link' },
  COMPLAINT:    { label: 'Complaint',    color: '#B91C1C', bg: '#FEF2F2', help: 'Recipient marked the email as spam / complained' },
  HARD_BOUNCE:  { label: 'Hard bounce',  color: '#C2410C', bg: '#FFF7ED', help: 'Address permanently undeliverable' },
  MANUAL:       { label: 'Manual',       color: '#1D4ED8', bg: '#EFF6FF', help: 'Added by staff' },
}

export default function SuppressionsTab() {
  const qc = useQueryClient()
  const [activeOnly, setActiveOnly] = useState(true)
  const [page, setPage]             = useState(0)
  const [search, setSearch]         = useState('')

  const [showAdd, setShowAdd]   = useState(false)
  const [addForm, setAddForm]   = useState({ email: '', reason: 'MANUAL', note: '' })
  const [addError, setAddError] = useState('')

  const [liftTarget, setLiftTarget] = useState<any>(null)
  const [liftReason, setLiftReason] = useState('')
  const [liftError, setLiftError]   = useState('')

  const [toast, setToast] = useState<{ msg: string; ok: boolean } | null>(null)
  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 4500) }

  const { data: pageData, isLoading } = useQuery<any>({
    queryKey: ['marketing-suppressions', activeOnly, page],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/marketing/suppressions?activeOnly=${activeOnly}&page=${page}&size=${PAGE_SIZE}`)
      return r.data?.data ?? r.data
    },
    placeholderData: (prev: any) => prev,
  })

  const rows: any[]      = pageData?.content ?? []
  const totalElements    = pageData?.totalElements ?? rows.length
  const totalPages       = pageData?.totalPages ?? 1

  const refreshAll = () => {
    qc.invalidateQueries({ queryKey: ['marketing-suppressions'] })
    qc.invalidateQueries({ queryKey: ['marketing-active-suppressions'] })
    qc.invalidateQueries({ queryKey: ['marketing-summary'] })
    qc.invalidateQueries({ queryKey: ['marketing-contacts'] })
    qc.invalidateQueries({ queryKey: ['marketing-campaigns'] })
  }

  const addSuppression = useMutation({
    mutationFn: () => apiClient.post('/api/v1/marketing/suppressions', {
      email: addForm.email.trim(), reason: addForm.reason, note: addForm.note.trim() || null,
    }),
    onSuccess: () => {
      refreshAll()
      setShowAdd(false); setAddForm({ email: '', reason: 'MANUAL', note: '' }); setAddError('')
      showToast('Address suppressed. The contact was opted out and any queued emails were cancelled.')
    },
    onError: (e: any) => setAddError(e.response?.data?.message || 'Failed to add suppression'),
  })

  const liftSuppression = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/marketing/suppressions/${liftTarget.id}/lift`, { reason: liftReason.trim() }),
    onSuccess: () => {
      refreshAll()
      setLiftTarget(null); setLiftReason(''); setLiftError('')
      showToast('Suppression lifted. The contact stays opted out until fresh consent is recorded.')
    },
    onError: (e: any) => setLiftError(e.response?.data?.message || 'Failed to lift suppression'),
  })

  const visible = rows.filter(r => !search || r.email?.toLowerCase().includes(search.toLowerCase()))
  const addValid  = EMAIL_RE.test(addForm.email.trim())
  const liftValid = liftReason.trim().length >= 10

  return (
    <div>
      {/* Explainer */}
      <div style={{ padding: '12px 16px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 10, marginBottom: 18, display: 'flex', gap: 10 }}>
        <ShieldAlert size={16} color="#0369A1" style={{ flexShrink: 0, marginTop: 2 }} />
        <div style={{ fontSize: 12, color: '#0C4A6E', lineHeight: 1.6 }}>
          <strong>The suppression list is enforced by the server before every send.</strong> Anyone listed here is excluded from
          campaigns at launch and again immediately before each email goes out — even if they are still marked as opted in.
          Unsubscribe-link clicks are added automatically. Lifting a suppression does <em>not</em> opt the contact back in.
        </div>
      </div>

      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <div style={{ position: 'relative' }}>
            <Search size={13} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search this page…"
              style={{ padding: '7px 10px 7px 28px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', width: 210 }} />
          </div>
          <select value={activeOnly ? 'ACTIVE' : 'ALL'}
            onChange={e => { setActiveOnly(e.target.value === 'ACTIVE'); setPage(0) }}
            style={{ padding: '7px 10px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
            <option value="ACTIVE">Active suppressions</option>
            <option value="ALL">Including lifted (history)</option>
          </select>
          <span style={{ fontSize: 12, color: '#94A3B8' }}>{totalElements} {activeOnly ? 'active' : 'total'}</span>
        </div>
        <button onClick={() => { setShowAdd(true); setAddError('') }}
          style={{ display: 'flex', alignItems: 'center', gap: 6, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '8px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={14} /> Add suppression
        </button>
      </div>

      {/* Table */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading suppression list…</div>
      ) : visible.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '50px 20px', color: '#94A3B8' }}>
          <ShieldAlert size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 700, color: '#475569', marginBottom: 6 }}>
            {rows.length === 0 ? 'No suppressed addresses' : 'No addresses match your search'}
          </div>
          <div style={{ fontSize: 13 }}>
            {rows.length === 0 ? 'Unsubscribes, complaints and bounces will appear here automatically.' : 'Try a different search term.'}
          </div>
        </div>
      ) : (
        <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                {['Email', 'Reason', 'Source', 'Note', 'Status', 'Added', ''].map(h => (
                  <th key={h} style={{ padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: '#64748B', letterSpacing: '0.05em', whiteSpace: 'nowrap' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visible.map((s, i) => {
                const r = REASONS[s.reason] ?? REASONS.MANUAL
                return (
                  <tr key={s.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA', borderBottom: '1px solid #F1F5F9' }}>
                    <td style={{ padding: '11px 16px', fontWeight: 600, color: '#0F172A' }}>{s.email}</td>
                    <td style={{ padding: '11px 16px' }}>
                      <span title={r.help} style={{ background: r.bg, color: r.color, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{r.label}</span>
                    </td>
                    <td style={{ padding: '11px 16px', color: '#94A3B8', fontSize: 12 }}>{s.source ?? '—'}</td>
                    <td style={{ padding: '11px 16px', color: '#475569', fontSize: 12, maxWidth: 240 }}>{s.note ?? '—'}</td>
                    <td style={{ padding: '11px 16px' }}>
                      {s.active ? (
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#FEF2F2', color: '#B91C1C', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <ShieldAlert size={10} /> Active
                        </span>
                      ) : (
                        <div>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#F1F5F9', color: '#64748B', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                            <Unlock size={10} /> Lifted
                          </span>
                          {s.liftReason && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 3, maxWidth: 200 }}>{s.liftReason}</div>}
                        </div>
                      )}
                    </td>
                    <td style={{ padding: '11px 16px', color: '#64748B', fontSize: 12, whiteSpace: 'nowrap' }}>
                      {fmtDT(s.createdAt)}
                      {!s.active && <div style={{ color: '#94A3B8' }}>Lifted {fmtDT(s.liftedAt)}</div>}
                    </td>
                    <td style={{ padding: '11px 16px' }}>
                      {s.active && (
                        <button onClick={() => { setLiftTarget(s); setLiftReason(''); setLiftError('') }}
                          style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 11px', background: '#fff', color: '#475569', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                          <Unlock size={11} /> Lift
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 10, marginTop: 14 }}>
          <span style={{ fontSize: 12, color: '#94A3B8' }}>Page {page + 1} of {totalPages}</span>
          <button disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}
            style={{ display: 'flex', padding: 6, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', cursor: page === 0 ? 'default' : 'pointer', opacity: page === 0 ? 0.4 : 1 }}>
            <ChevronLeft size={14} />
          </button>
          <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}
            style={{ display: 'flex', padding: 6, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', cursor: page + 1 >= totalPages ? 'default' : 'pointer', opacity: page + 1 >= totalPages ? 0.4 : 1 }}>
            <ChevronRight size={14} />
          </button>
        </div>
      )}

      {/* Add suppression modal */}
      {showAdd && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 440, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Add to suppression list</h3>
              <button onClick={() => setShowAdd(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={lbl}>Email address *</label>
                <input autoFocus type="email" value={addForm.email} onChange={e => setAddForm(p => ({ ...p, email: e.target.value }))} placeholder="name@company.co.za" style={inp} />
              </div>
              <div>
                <label style={lbl}>Reason</label>
                <select value={addForm.reason} onChange={e => setAddForm(p => ({ ...p, reason: e.target.value }))} style={inp}>
                  <option value="MANUAL">Manual — requested directly (phone, email, in person)</option>
                  <option value="COMPLAINT">Complaint — marked as spam / complained</option>
                  <option value="HARD_BOUNCE">Hard bounce — address permanently invalid</option>
                </select>
              </div>
              <div>
                <label style={lbl}>Note (optional)</label>
                <textarea value={addForm.note} onChange={e => setAddForm(p => ({ ...p, note: e.target.value }))} rows={3}
                  placeholder="e.g. Asked by phone on 12 June to stop all marketing" style={{ ...inp, resize: 'vertical' }} />
              </div>
              <div style={{ padding: '9px 12px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 8, fontSize: 12, color: '#92400E' }}>
                The contact will be opted out and any emails already queued for this address will be cancelled.
              </div>
            </div>
            {addError && <div style={{ marginTop: 12, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{addError}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button disabled={!addValid || addSuppression.isPending} onClick={() => addSuppression.mutate()}
                style={{ padding: '9px 22px', background: '#B91C1C', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: 'pointer', opacity: !addValid ? 0.5 : 1 }}>
                {addSuppression.isPending ? 'Suppressing…' : 'Suppress address'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Lift suppression modal */}
      {liftTarget && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 480, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Lift suppression</h3>
              <button onClick={() => setLiftTarget(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ fontSize: 14, color: '#0F172A', fontWeight: 600, marginBottom: 4 }}>{liftTarget.email}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginBottom: 14 }}>
              Suppressed as <strong>{(REASONS[liftTarget.reason] ?? REASONS.MANUAL).label}</strong> on {fmtDT(liftTarget.createdAt)}
            </div>
            <div style={{ padding: '10px 14px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 8, fontSize: 12, color: '#92400E', marginBottom: 14, display: 'flex', gap: 8 }}>
              <AlertTriangle size={14} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
              <div>
                Lifting does <strong>not</strong> opt this contact back in. They will receive no marketing until you record fresh,
                documented consent. Do not lift a suppression for someone who unsubscribed or complained unless they have
                explicitly asked to hear from you again.
              </div>
            </div>
            <label style={lbl}>Documented reason * (min. 10 characters)</label>
            <textarea autoFocus value={liftReason} onChange={e => setLiftReason(e.target.value)} rows={3}
              placeholder="e.g. Customer emailed on 3 July asking to be re-added to the newsletter" style={{ ...inp, resize: 'vertical' }} />
            <div style={{ fontSize: 11, color: liftValid ? '#166534' : '#94A3B8', marginTop: 4, display: 'flex', alignItems: 'center', gap: 4 }}>
              {liftValid && <CheckCircle size={11} />} {liftReason.trim().length} / 10 characters
            </div>
            {liftError && <div style={{ marginTop: 12, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{liftError}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setLiftTarget(null)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button disabled={!liftValid || liftSuppression.isPending} onClick={() => liftSuppression.mutate()}
                style={{ padding: '9px 22px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: 'pointer', opacity: !liftValid ? 0.5 : 1 }}>
                {liftSuppression.isPending ? 'Lifting…' : 'Lift suppression'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toast */}
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 3000, display: 'flex', alignItems: 'center', gap: 10, background: toast.ok ? '#DCFCE7' : '#FEF2F2', border: `1px solid ${toast.ok ? '#86EFAC' : '#FECACA'}`, borderRadius: 10, padding: '12px 18px', boxShadow: '0 8px 24px rgba(0,0,0,0.12)', maxWidth: 400 }}>
          {toast.ok ? <CheckCircle size={16} color="#166534" style={{ flexShrink: 0 }} /> : <AlertTriangle size={16} color="#DC2626" style={{ flexShrink: 0 }} />}
          <span style={{ fontSize: 13, fontWeight: 600, color: toast.ok ? '#166534' : '#DC2626' }}>{toast.msg}</span>
          <button onClick={() => setToast(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: toast.ok ? '#166534' : '#DC2626', marginLeft: 4, display: 'flex', padding: 0 }}><X size={14} /></button>
        </div>
      )}
    </div>
  )
}