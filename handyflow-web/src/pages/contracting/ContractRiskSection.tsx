// src/pages/contracting/ContractRiskSection.tsx
// Risk check on one contract. It runs the published, currently reviewed risk rules and stores the result. "Not assessed" means no
// rule applied, which is not the same as low risk. A failed rule means the contract does not meet that rule, not that it is unlawful.
import { useState, type CSSProperties } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermissions } from '../../hooks/usePermission'
import { LEVEL_COLOR, LEVEL_LABEL, type RiskAssessment } from './riskRules'

const btn: CSSProperties = { padding: '5px 11px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }
const errMsg = (e: unknown, fallback: string): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
const fmt = (s: string) => new Date(s).toLocaleString('en-ZA')

export function ContractRiskSection({ contractId }: { contractId: string }) {
  const qc = useQueryClient()
  const canCheck = usePermissions(['CONTRACTS_MANAGE', 'CONTRACTS_ADMIN'], 'any')
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const key = ['contract-risk', contractId]

  const { data: history = [] } = useQuery<RiskAssessment[]>({
    queryKey: key, enabled: open,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/contracts/${contractId}/risk-assessments`); return (r.data?.data ?? r.data) as RiskAssessment[] },
  })
  const run = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/${contractId}/risk-assessments`, {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setError('') },
    onError: (e: unknown) => setError(errMsg(e, 'Could not run the risk check')),
  })
  const latest = history[0]

  return (
    <div style={{ marginTop: 12, border: '1px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface-muted)' }}>
      <button onClick={() => setOpen(o => !o)} style={{ width: '100%', textAlign: 'left', padding: '9px 12px', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 700, fontSize: 13, color: 'var(--hf-text)' }}>
        Risk check {open ? '▾' : '▸'}
      </button>
      {open && (
        <div style={{ padding: '0 12px 12px' }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 8 }}>
            {canCheck && <button style={btn} disabled={run.isPending} onClick={() => run.mutate()}>{run.isPending ? 'Checking…' : 'Run risk check'}</button>}
            <span style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>Runs the published rules your legal reviewer has approved.</span>
          </div>
          {error && <div style={{ fontSize: 13, color: 'var(--hf-danger-text)', marginBottom: 8 }}>{error}</div>}
          {!latest ? <div style={{ fontSize: 13, color: 'var(--hf-text-faint)' }}>No risk check has been run on this contract.</div> : (
            <div>
              <div style={{ fontSize: 14, fontWeight: 700, color: LEVEL_COLOR[latest.riskLevel] }}>{LEVEL_LABEL[latest.riskLevel]}</div>
              <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginBottom: 6 }}>
                {fmt(latest.assessedAt)}{latest.contractVersionNo ? ` · text version ${latest.contractVersionNo}` : ''} · {latest.rulesEvaluated} rule{latest.rulesEvaluated === 1 ? '' : 's'} run
                {latest.rulesSkipped > 0 ? ` · ${latest.rulesSkipped} skipped (other contract types or review lapsed)` : ''}
              </div>
              {latest.riskLevel === 'NOT_ASSESSED' && <div style={{ fontSize: 13, color: 'var(--hf-text-secondary)' }}>No published rule applied to this contract, so nothing was checked. That is not the same as low risk.</div>}
              {latest.findings.map(f => (
                <div key={f.ruleId} style={{ borderTop: '1px solid var(--hf-border)', padding: '6px 0', fontSize: 13 }}>
                  <strong style={{ color: LEVEL_COLOR[f.severity] }}>{LEVEL_LABEL[f.severity]}</strong> · {f.ruleName} <span style={{ color: 'var(--hf-text-muted)', fontSize: 12 }}>(v{f.ruleVersion})</span>{f.blocksSending ? <span style={{ color: 'var(--hf-danger-text)', fontSize: 12 }}> · blocks sending</span> : null}
                  <div style={{ color: 'var(--hf-text-secondary)' }}>{f.message}</div>
                  {f.remediation && <div style={{ color: 'var(--hf-text-muted)', fontSize: 12 }}>What to do: {f.remediation}</div>}
                  {f.reviewedBy && <div style={{ color: 'var(--hf-text-muted)', fontSize: 12 }}>Rule reviewed by {f.reviewedBy}{f.reviewDate ? ` on ${f.reviewDate}` : ''}</div>}
                </div>
              ))}
              {latest.integrityFindings.length > 0 && (
                <div style={{ borderTop: '1px solid var(--hf-border)', paddingTop: 6, marginTop: 4 }}>
                  <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--hf-text-secondary)' }}>Library clauses that are no longer current (not part of the rating)</div>
                  {latest.integrityFindings.map(i => <div key={i.clauseId} style={{ fontSize: 13, color: 'var(--hf-text-secondary)' }}>{i.clauseTitle} (v{i.clauseVersion}): {i.message}</div>)}
                </div>
              )}
              {history.length > 1 && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 6 }}>Earlier checks: {history.slice(1, 6).map(h => `${fmt(h.assessedAt)} (${LEVEL_LABEL[h.riskLevel]})`).join(' · ')}</div>}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
