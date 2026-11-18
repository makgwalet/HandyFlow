// src/pages/contracting/ReportsTab.tsx
// Portfolio reporting over recorded contract, obligation and renewal data, plus the contract register as CSV.
import { useState, type CSSProperties } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { fmtR } from './contractingApi'

interface Slow { contractNumber: string; title: string; days: number }
interface Report {
  from: string; to: string
  turnaround: { count: number; averageDays: number; medianDays: number; p90Days: number; slowest: Slow[] }
  signedByType: { contractType: string; contracts: number; totalValue: number; withoutValue: number }[]
  expiryProfile: { month: string; contracts: number; totalValue: number }[]
  obligations: { due: number; completedOnTime: number; completedLate: number; waived: number; stillOpenOverdue: number; stillOpenNotYetDue: number; onTimeRate: number | null }
  renewals: { decisionsByType: Record<string, number>; noticePassedWithoutDecision: number }
  templates: { publishedByLegalStatus: Record<string, number> }
}

const LEGAL_LABEL: Record<string, string> = {
  APPROVED: 'Legal review recorded', CONDITIONAL: 'Conditional review', NOT_REVIEWED: 'Not reviewed', EXPIRED: 'Review expired', WITHDRAWN: 'Review withdrawn', REJECTED: 'Review rejected',
}
const DECISION_LABEL: Record<string, string> = { RENEW: 'Renew', RENEGOTIATE: 'Renegotiate', DO_NOT_RENEW: 'Do not renew' }

const inp: CSSProperties = { padding: '7px 10px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, background: 'var(--hf-surface)' }
const card: CSSProperties = { border: '1px solid var(--hf-border)', borderRadius: 12, padding: '14px 16px', background: 'var(--hf-surface)' }
const h: CSSProperties = { margin: '0 0 8px', fontSize: 14, fontWeight: 700, color: 'var(--hf-text)' }
const td: CSSProperties = { padding: '4px 8px', fontSize: 13, color: 'var(--hf-text-secondary)', textAlign: 'right' }
const tdl: CSSProperties = { ...td, textAlign: 'left' }
const btn: CSSProperties = { padding: '7px 14px', border: '1px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)', fontSize: 13, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }

const label = (t: string) => t.charAt(0) + t.slice(1).toLowerCase().replace(/_/g, ' ')
const pct = (r: number | null) => (r == null ? 'n/a (nothing completed)' : `${Math.round(r * 100)}%`)

export default function ReportsTab() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [error, setError] = useState('')

  const qs = [from && `from=${from}`, to && `to=${to}`].filter(Boolean).join('&')
  const { data: r, isLoading, error: loadError } = useQuery<Report>({
    queryKey: ['contract-report', from, to],
    queryFn: async () => { const res = await apiClient.get(`/api/v1/contracts/reports/portfolio${qs ? `?${qs}` : ''}`); return res.data?.data ?? res.data },
  })

  const download = async () => {
    try {
      const res = await apiClient.get('/api/v1/contracts/reports/register.csv', { responseType: 'blob' })
      const url = URL.createObjectURL(res.data as Blob)
      const a = document.createElement('a')
      a.href = url; a.download = 'contract-register.csv'; a.click()
      URL.revokeObjectURL(url); setError('')
    } catch { setError('The register could not be downloaded') }
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', marginBottom: 14 }}>
        <label style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>From <input type="date" style={inp} value={from} onChange={e => setFrom(e.target.value)} /></label>
        <label style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>To <input type="date" style={inp} value={to} onChange={e => setTo(e.target.value)} /></label>
        <span style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>{r ? `Showing ${r.from} to ${r.to}` : 'Defaults to the last 12 months'}</span>
        <div style={{ flex: 1 }} />
        <button style={btn} onClick={download}>Download contract register (CSV)</button>
      </div>
      {error && <div style={{ fontSize: 13, color: 'var(--hf-danger-text)', marginBottom: 10 }}>{error}</div>}
      {isLoading && <div style={{ color: 'var(--hf-text-muted)' }}>Loading…</div>}
      {loadError && <div style={{ color: 'var(--hf-danger-text)' }}>The report could not be loaded.</div>}

      {r && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 14 }}>
          <div style={card}>
            <h4 style={h}>Signing turnaround</h4>
            {r.turnaround.count === 0 ? <div style={{ fontSize: 13, color: 'var(--hf-text-faint)' }}>No contracts were signed in this period.</div> : (
              <>
                <div style={{ fontSize: 13, color: 'var(--hf-text-secondary)', marginBottom: 6 }}>
                  {r.turnaround.count} signed · average {r.turnaround.averageDays} days · median {r.turnaround.medianDays} · 90% within {r.turnaround.p90Days} days (from sending to last signature)
                </div>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}><tbody>
                  {r.turnaround.slowest.slice(0, 5).map(s => <tr key={s.contractNumber}><td style={tdl}>{s.contractNumber} · {s.title}</td><td style={td}>{s.days} days</td></tr>)}
                </tbody></table>
              </>
            )}
          </div>

          <div style={card}>
            <h4 style={h}>Signed contracts by type</h4>
            {r.signedByType.length === 0 ? <div style={{ fontSize: 13, color: 'var(--hf-text-faint)' }}>No signed contracts.</div> : (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead><tr><td style={tdl}>Type</td><td style={td}>Contracts</td><td style={td}>Value</td><td style={td}>No value set</td></tr></thead>
                <tbody>{r.signedByType.map(t => <tr key={t.contractType}><td style={tdl}>{label(t.contractType)}</td><td style={td}>{t.contracts}</td><td style={td}>{fmtR(t.totalValue)}</td><td style={td}>{t.withoutValue}</td></tr>)}</tbody>
              </table>
            )}
          </div>

          <div style={card}>
            <h4 style={h}>Expiry profile (next 12 months)</h4>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead><tr><td style={tdl}>Month</td><td style={td}>Contracts ending</td><td style={td}>Value</td></tr></thead>
              <tbody>{r.expiryProfile.map(m => <tr key={m.month}><td style={tdl}>{m.month}</td><td style={td}>{m.contracts}</td><td style={td}>{fmtR(m.totalValue)}</td></tr>)}</tbody>
            </table>
          </div>

          <div style={card}>
            <h4 style={h}>Obligations due in this period</h4>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}><tbody>
              <tr><td style={tdl}>Due</td><td style={td}>{r.obligations.due}</td></tr>
              <tr><td style={tdl}>Completed on time</td><td style={td}>{r.obligations.completedOnTime}</td></tr>
              <tr><td style={tdl}>Completed late</td><td style={td}>{r.obligations.completedLate}</td></tr>
              <tr><td style={tdl}>Waived</td><td style={td}>{r.obligations.waived}</td></tr>
              <tr><td style={tdl}>Still open and overdue</td><td style={{ ...td, color: r.obligations.stillOpenOverdue > 0 ? 'var(--hf-danger-text)' : undefined }}>{r.obligations.stillOpenOverdue}</td></tr>
              <tr><td style={tdl}>Still open, not yet due</td><td style={td}>{r.obligations.stillOpenNotYetDue}</td></tr>
              <tr><td style={tdl}><strong>On-time rate</strong></td><td style={td}><strong>{pct(r.obligations.onTimeRate)}</strong></td></tr>
            </tbody></table>
          </div>

          <div style={card}>
            <h4 style={h}>Renewals</h4>
            <div style={{ fontSize: 13, color: r.renewals.noticePassedWithoutDecision > 0 ? 'var(--hf-danger-text)' : 'var(--hf-text-secondary)', marginBottom: 6 }}>
              {r.renewals.noticePassedWithoutDecision} contract{r.renewals.noticePassedWithoutDecision === 1 ? '' : 's'} ending within a year {r.renewals.noticePassedWithoutDecision === 1 ? 'is' : 'are'} past the notice date with no decision.
            </div>
            {Object.keys(r.renewals.decisionsByType).length === 0 ? <div style={{ fontSize: 13, color: 'var(--hf-text-faint)' }}>No renewal decisions in this period.</div> : (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}><tbody>
                {Object.entries(r.renewals.decisionsByType).map(([k, v]) => <tr key={k}><td style={tdl}>{DECISION_LABEL[k] ?? k}</td><td style={td}>{v}</td></tr>)}
              </tbody></table>
            )}
          </div>

          <div style={card}>
            <h4 style={h}>Published templates: legal review currency</h4>
            {Object.keys(r.templates.publishedByLegalStatus).length === 0 ? <div style={{ fontSize: 13, color: 'var(--hf-text-faint)' }}>No published templates.</div> : (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}><tbody>
                {Object.entries(r.templates.publishedByLegalStatus).map(([k, v]) => (
                  <tr key={k}><td style={tdl}>{LEGAL_LABEL[k] ?? k}</td><td style={{ ...td, color: ['EXPIRED', 'WITHDRAWN', 'REJECTED'].includes(k) && v > 0 ? 'var(--hf-danger-text)' : undefined }}>{v}</td></tr>
                ))}
              </tbody></table>
            )}
            <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 6 }}>A recorded review is evidence a review took place, not a guarantee of compliance.</div>
          </div>
        </div>
      )}
    </div>
  )
}
