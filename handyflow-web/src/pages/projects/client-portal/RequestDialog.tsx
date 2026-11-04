// src/pages/projects/client-portal/RequestDialog.tsx
// Send the project team a question, a change request, or (from the home page) a request for a new project.
import { useState } from 'react'
import { PM_PORTAL, pmPortalClient, type ClientRequest } from '../../../api/projectClientPortal.client'
import { errorText } from '../../../api/projectDocs'
import { toast } from '../../../store/toast.store'
import { Btn, Dialog, ErrorLine, Field, inp, muted, today } from '../tabs/pmKit'

type Kind = ClientRequest['kind']
const INTRO: Record<Kind, string> = {
  QUESTION: 'Ask the project team something. Their answer appears here and you are not committing to anything.',
  CHANGE: 'Describe what you would like changed. The team will price it; nothing changes until you approve the change order they send back.',
  NEW_PROJECT: 'Tell the team about the work you need. They will contact you and set it up.',
}

export function RequestDialog({ kind: initial, projectId, accounts, onClose, onDone }:
  { kind: Kind; projectId?: string; accounts?: { grantId: string; clientName: string }[]; onClose: () => void; onDone: () => void }) {
  const [kind, setKind] = useState<Kind>(initial)
  const [grantId, setGrantId] = useState(accounts?.[0]?.grantId ?? '')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [neededBy, setNeededBy] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const send = async () => {
    if (!title.trim()) { setErr('Give it a short title'); return }
    if (neededBy && neededBy < today()) { setErr('The date is in the past'); return }
    setBusy(true); setErr('')
    try {
      await pmPortalClient.post(`${PM_PORTAL}/me/requests`, { kind, projectId: kind === 'NEW_PROJECT' ? null : projectId,
        grantId: kind === 'NEW_PROJECT' ? grantId || null : null, title: title.trim(), description: description.trim() || null, neededBy: neededBy || null })
      toast.success('Sent to the project team'); onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not send it')) } finally { setBusy(false) }
  }
  return (
    <Dialog title={kind === 'NEW_PROJECT' ? 'Request a new project' : kind === 'CHANGE' ? 'Request a change' : 'Ask a question'} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={send}>{busy ? 'Sending…' : 'Send'}</Btn></>}>
      {kind !== 'NEW_PROJECT' && (
        <div role="radiogroup" aria-label="Type" style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
          {(['QUESTION', 'CHANGE'] as const).map(k => (
            <button key={k} role="radio" aria-checked={kind === k} onClick={() => setKind(k)}
              style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer',
                border: `1px solid ${kind === k ? 'var(--hf-primary)' : 'var(--hf-border)'}`, background: kind === k ? 'var(--hf-primary-soft, var(--hf-info-soft))' : 'var(--hf-surface)',
                color: kind === k ? 'var(--hf-primary-text)' : 'var(--hf-text-muted)' }}>{k === 'QUESTION' ? 'Question' : 'Change request'}</button>))}
        </div>)}
      <div style={{ ...muted, marginBottom: 12, lineHeight: 1.5 }}>{INTRO[kind]}</div>
      {kind === 'NEW_PROJECT' && accounts && accounts.length > 1 && (
        <Field label="For"><select value={grantId} onChange={e => setGrantId(e.target.value)} style={inp}>
          {accounts.map(a => <option key={a.grantId} value={a.grantId}>{a.clientName}</option>)}</select></Field>)}
      <Field label="Title"><input autoFocus maxLength={300} value={title} onChange={e => setTitle(e.target.value)} style={inp}
        placeholder={kind === 'QUESTION' ? 'When will the roof be finished?' : kind === 'CHANGE' ? 'Add a second gate on the east side' : 'Paving at our Midrand site'} /></Field>
      <Field label={kind === 'NEW_PROJECT' ? 'What is needed, where and roughly when' : 'Details'}>
        <textarea rows={5} maxLength={5000} value={description} onChange={e => setDescription(e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      <Field label="Needed by (optional)"><input type="date" min={today()} value={neededBy} onChange={e => setNeededBy(e.target.value)} style={{ ...inp, width: 180 }} /></Field>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
