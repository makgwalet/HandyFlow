import { useState } from 'react'
import { X } from 'lucide-react'
import { withdrawStep, type ApprovalProgress } from './contractWithdraw'

interface Props {
  contractNumber: string
  signedCount: number
  totalSigners: number
  required: boolean
  state: ApprovalProgress
  busy: boolean
  error?: string
  onAsk: () => void
  onWithdraw: (reason: string) => void
  onClose: () => void
}

/** Cancels a signing attempt. Says plainly what happens, asks for a reason, and goes through approval first when policy needs it. */
export function WithdrawModal(p: Props) {
  const [reason, setReason] = useState('')
  const step = withdrawStep(p.required, p.state, reason, p.busy)
  const needsReason = step.step === 'WITHDRAW'
  const go = () => (step.step === 'ASK' ? p.onAsk() : step.step === 'WITHDRAW' ? p.onWithdraw(reason.trim()) : undefined)
  return (
    <div role="dialog" aria-label="Withdraw signing request" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 60 }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 480, maxWidth: '94vw', maxHeight: '90vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Withdraw {p.contractNumber}</h3>
          <button aria-label="Close" onClick={p.onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}><X size={20} /></button>
        </div>
        <div style={{ marginBottom: 14, padding: '10px 14px', background: 'var(--hf-warning-soft)', border: '1px solid var(--hf-warning-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-warning-text)' }}>
          <strong>This cancels the signing attempt.</strong>
          <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
            <li>Every signing link stops working straight away and waiting codes are discarded.</li>
            <li>Everyone it was sent to is told it was withdrawn.</li>
            <li>What was already recorded stays: views, code attempts and signatures.</li>
            <li>Afterwards you can make a new draft from it. That goes through approval and signing again.</li>
          </ul>
          {p.signedCount > 0 && (
            <p style={{ margin: '8px 0 0' }}>
              {p.signedCount} of {p.totalSigners} signer{p.totalSigners === 1 ? ' has' : 's have'} already signed. Their signatures are kept, but this version will not count as signed by everyone.
            </p>
          )}
        </div>
        {needsReason && (
          <div style={{ marginBottom: 14 }}>
            <label htmlFor="withdraw-reason" style={{ display: 'block', fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Why is it being withdrawn? *</label>
            <textarea id="withdraw-reason" autoFocus rows={3} value={reason} onChange={e => setReason(e.target.value)}
              placeholder="e.g. The wrong pricing schedule was attached"
              style={{ width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, boxSizing: 'border-box', background: 'var(--hf-surface)', resize: 'vertical' }} />
          </div>
        )}
        {step.why && <p role="note" style={{ margin: '0 0 12px', fontSize: 12, color: 'var(--hf-text-muted)' }}>{step.why}</p>}
        {p.error && <div role="alert" style={{ marginBottom: 12, padding: '9px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>{p.error}</div>}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <button onClick={p.onClose} style={{ padding: '9px 16px', border: '1px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)', fontSize: 13, cursor: 'pointer' }}>Cancel</button>
          <button disabled={step.disabled} onClick={go}
            style={{ padding: '9px 16px', border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: step.disabled ? 'not-allowed' : 'pointer', opacity: step.disabled ? 0.6 : 1, background: 'var(--hf-danger)', color: 'var(--hf-text-on-solid)' }}>
            {p.busy && step.step === 'WITHDRAW' ? 'Withdrawing…' : step.label}
          </button>
        </div>
      </div>
    </div>
  )
}
