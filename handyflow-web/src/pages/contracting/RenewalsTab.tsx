// src/pages/contracting/RenewalsTab.tsx
// Signed contracts nearing their end date. Choosing "Renew" creates a successor draft that has to be reviewed and signed again.
import { useState, type CSSProperties } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { X } from 'lucide-react'
import { unwrap } from './contractingApi'
import { usePermissions } from '../../hooks/usePermission'

interface RenewalItem {
  contractId: string; contractNumber: string; title: string; endDate: string; autoRenew: boolean; renewalNoticeDays: number
  noticeDeadline: string; daysToEnd: number; daysToNotice: number; state: 'DECISION_NEEDED' | 'NOTICE_PASSED' | 'PAST_END' | 'DECIDED'
  latestDecision?: string | null; successorContractId?: string | null
}

const WINDOWS = [30, 60, 90, 180]
const STATE_LABEL: Record<string, string> = { DECISION_NEEDED: 'Decision needed', NOTICE_PASSED: 'Notice date passed, no decision', PAST_END: 'Past end date, auto-renews, no decision', DECIDED: 'Decided' }
const STATE_COLOR: Record<string, string> = { DECISION_NEEDED: 'var(--hf-warning-text)', NOTICE_PASSED: 'var(--hf-danger-text)', PAST_END: 'var(--hf-danger-text)', DECIDED: 'var(--hf-success-text-strong)' }
const DECISION_LABEL: Record<string, string> = { RENEW: 'Renew', RENEGOTIATE: 'Renegotiate', DO_NOT_RENEW: 'Do not renew' }

const inp: CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: 'var(--hf-surface)' }
const lbl: CSSProperties = { display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 4 }
const btn: CSSProperties = { padding: '5px 11px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }
const MODAL: CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }

const errMsg = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
const fmt = (d: string) => new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })

export default function RenewalsTab() {
  const qc = useQueryClient()
  const canWrite = usePermissions(['CONTRACTS_MANAGE', 'CONTRACTS_ADMIN'], 'any')
  const [windowDays, setWindowDays] = useState(90)
  const [deciding, setDeciding] = useState<RenewalItem | null>(null)
  const [f, setF] = useState({ decision: 'RENEW', note: '', newStartDate: '', newEndDate: '' })
  const [error, setError] = useState('')

  const { data: items = [], isLoading } = useQuery<RenewalItem[]>({
    queryKey: ['contract-renewals', windowDays],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/contracts/renewals?withinDays=${windowDays}`)) as RenewalItem[],
  })

  const decide = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/${deciding!.contractId}/renewal-decisions`, {
      decision: f.decision, note: f.note || null, newStartDate: f.newStartDate || null, newEndDate: f.newEndDate || null }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['contract-renewals'] })
      qc.invalidateQueries({ queryKey: ['contracts'] })
      setDeciding(null); setError('')
    },
    onError: (e: unknown) => setError(errMsg(e, 'Could not record the decision')),
  })

  const open = (r: RenewalItem) => { setDeciding(r); setF({ decision: 'RENEW', note: '', newStartDate: '', newEndDate: '' }); setError('') }
  const valid = f.decision !== 'RENEW' || !!f.newEndDate

  return (
    <div>
      <div style={{ display: 'flex', gap: 6, marginBottom: 12, alignItems: 'center' }}>
        <span style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>Ending within</span>
        {WINDOWS.map(d => (
          <button key={d} onClick={() => setWindowDays(d)} style={{ ...btn, background: windowDays === d ? 'var(--hf-primary)' : 'var(--hf-surface)', color: windowDays === d ? 'var(--hf-text-on-solid)' : 'var(--hf-primary-text)' }}>{d} days</button>
        ))}
      </div>
      <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 12, lineHeight: 1.5 }}>
        The notice date is the end date minus the contract's notice period. Contracts are not extended automatically. Renewing creates a draft successor that is reviewed and signed like any other contract.
      </div>

      {isLoading ? <div style={{ color: 'var(--hf-text-muted)' }}>Loading…</div> : items.length === 0 ? (
        <div style={{ color: 'var(--hf-text-faint)', fontSize: 14, padding: '24px 0' }}>No signed contracts end in this period.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {items.map(r => (
            <div key={r.contractId} style={{ border: '1px solid var(--hf-border)', borderRadius: 10, padding: '10px 14px', background: 'var(--hf-surface)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontWeight: 700, color: 'var(--hf-text)' }}>{r.contractNumber} · {r.title}</div>
                  <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>
                    Ends {fmt(r.endDate)} ({r.daysToEnd} day{r.daysToEnd === 1 ? '' : 's'}) · notice by {fmt(r.noticeDeadline)}
                    {r.daysToNotice >= 0 ? ` (${r.daysToNotice} day${r.daysToNotice === 1 ? '' : 's'})` : ''}{r.autoRenew ? ' · marked auto-renew' : ''}
                  </div>
                </div>
                <div style={{ textAlign: 'right', fontSize: 12, fontWeight: 700, color: STATE_COLOR[r.state] }}>
                  {STATE_LABEL[r.state]}{r.latestDecision ? `: ${DECISION_LABEL[r.latestDecision] ?? r.latestDecision}` : ''}
                </div>
              </div>
              {r.successorContractId && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 4 }}>A renewal draft was created. Find it in Contracts.</div>}
              {canWrite && (r.state !== 'DECIDED' || (r.latestDecision !== 'RENEW' && !r.successorContractId)) && (
                <button style={{ ...btn, marginTop: 8 }} onClick={() => open(r)}>{r.state === 'DECIDED' ? 'Change decision' : 'Record decision'}</button>
              )}
            </div>
          ))}
        </div>
      )}

      {deciding && (
        <div style={MODAL}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 520 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>Renewal decision: {deciding.contractNumber}</h3>
              <button onClick={() => setDeciding(null)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'grid', gap: 12 }}>
              <div>
                <label style={lbl}>Decision</label>
                <select style={inp} value={f.decision} onChange={e => setF(p => ({ ...p, decision: e.target.value }))}>
                  <option value="RENEW">Renew (creates a draft successor)</option>
                  <option value="RENEGOTIATE">Renegotiate</option>
                  <option value="DO_NOT_RENEW">Do not renew</option>
                </select>
              </div>
              {f.decision === 'RENEW' && (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div><label style={lbl}>New start date (default: day after end)</label><input type="date" style={inp} value={f.newStartDate} onChange={e => setF(p => ({ ...p, newStartDate: e.target.value }))} /></div>
                  <div><label style={lbl}>New end date *</label><input type="date" style={inp} value={f.newEndDate} onChange={e => setF(p => ({ ...p, newEndDate: e.target.value }))} /></div>
                </div>
              )}
              <div><label style={lbl}>Note</label><textarea rows={2} style={inp} value={f.note} onChange={e => setF(p => ({ ...p, note: e.target.value }))} /></div>
            </div>
            {error && <div style={{ marginTop: 12, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 18 }}>
              <button onClick={() => setDeciding(null)} style={btn}>Cancel</button>
              <button disabled={!valid || decide.isPending} onClick={() => decide.mutate()} style={{ ...btn, background: valid ? 'var(--hf-primary)' : 'var(--hf-text-faint)', color: 'var(--hf-text-on-solid)', border: 'none' }}>{decide.isPending ? 'Saving…' : 'Record decision'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
