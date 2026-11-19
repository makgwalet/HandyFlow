// Top of the contract workspace: the next required action and the tab bar.
import { AlertCircle, CheckCircle2, Clock } from 'lucide-react'
import { WORKSPACE_TABS, nextRequiredAction, type WorkspaceTab, type WorkspacePartyInput } from './contractWorkspace'

interface Props {
  contract: { status: string }
  parties: WorkspacePartyInput[]
  unresolvedAmendments: number
  canManage: boolean
  tab: WorkspaceTab
  onTab: (t: WorkspaceTab) => void
  busy: boolean
  onAction: (a: 'submit-for-review' | 'send-for-signing') => void
  sendApproval?: { required: boolean; state: 'NONE' | 'PENDING' | 'APPROVED' }
}

const TONE = {
  todo: { bg: 'var(--hf-info-soft)', border: 'var(--hf-info-border)', color: 'var(--hf-info-text)', Icon: AlertCircle },
  waiting: { bg: 'var(--hf-warning-soft)', border: 'var(--hf-warning-border)', color: 'var(--hf-warning-text-deep)', Icon: Clock },
  done: { bg: 'var(--hf-success-soft)', border: 'var(--hf-success-border-subtle)', color: 'var(--hf-success-text-strong)', Icon: CheckCircle2 },
}

export function WorkspaceHeader({ contract, parties, unresolvedAmendments, canManage, tab, onTab, busy, onAction, sendApproval }: Props) {
  const next = nextRequiredAction({ status: contract.status, parties, unresolvedAmendments, canManage, sendApproval })
  const t = TONE[next.tone]
  return (
    <div style={{ marginBottom: 14 }}>
      <div role="status" aria-label="Next required action" style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '10px 14px', borderRadius: 8, background: t.bg, border: `1px solid ${t.border}`, color: t.color, marginBottom: 12, flexWrap: 'wrap' }}>
        <t.Icon size={16} />
        <div style={{ flex: 1, minWidth: 200 }}>
          <div style={{ fontSize: 13, fontWeight: 700 }}>{next.title}</div>
          {next.detail && <div style={{ fontSize: 12, opacity: 0.9 }}>{next.detail}</div>}
        </div>
        {next.tab && tab !== next.tab && (
          <button onClick={() => onTab(next.tab as WorkspaceTab)} style={{ padding: '6px 12px', borderRadius: 7, border: `1px solid ${t.border}`, background: 'var(--hf-surface)', color: t.color, fontWeight: 600, fontSize: 12, cursor: 'pointer' }}>
            Go to {WORKSPACE_TABS.find(x => x.id === next.tab)?.label}
          </button>
        )}
        {next.action && (
          <button disabled={busy} onClick={() => onAction(next.action as 'submit-for-review' | 'send-for-signing')} style={{ padding: '6px 12px', borderRadius: 7, border: 'none', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', fontWeight: 600, fontSize: 12, cursor: 'pointer' }}>
            {next.title}
          </button>
        )}
      </div>
      <div role="tablist" aria-label="Contract workspace" style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--hf-border)', flexWrap: 'wrap' }}>
        {WORKSPACE_TABS.map(w => (
          <button key={w.id} role="tab" aria-selected={tab === w.id} onClick={() => onTab(w.id)}
            style={{ padding: '8px 14px', fontSize: 13, fontWeight: tab === w.id ? 700 : 500, cursor: 'pointer', background: 'none', border: 'none',
              borderBottom: tab === w.id ? '2px solid var(--hf-primary)' : '2px solid transparent', color: tab === w.id ? 'var(--hf-text)' : 'var(--hf-text-muted)' }}>
            {w.label}
          </button>
        ))}
      </div>
    </div>
  )
}
