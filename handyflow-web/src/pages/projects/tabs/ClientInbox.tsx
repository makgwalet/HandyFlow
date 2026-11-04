// src/pages/projects/tabs/ClientInbox.tsx
// What clients sent on this project through the portal: questions and change requests to accept, answer or decline,
// and files to accept into the document register or reject. Shown at the top of the Client portal tab.
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Check, X } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { KIND_LABEL, REQUEST_STATUS, UPLOAD_STATUS, type ClientRequest, type ClientUpload } from '../../../api/projectClientPortal.client'
import { DecideDialog } from '../ClientAccessPage'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, ReasonDialog, SectionTitle, card, day, downloadFile, inp, list, muted } from './pmKit'

export function ClientInbox({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const canWrite = usePermission('PM_WRITE')
  const [deciding, setDeciding] = useState<ClientRequest | null>(null)
  const [accepting, setAccepting] = useState<ClientUpload | null>(null)
  const [rejecting, setRejecting] = useState<ClientUpload | null>(null)
  const [showClosed, setShowClosed] = useState(false)
  const base = `/api/v1/projects/${projectId}`
  const { data: requests = [], isLoading: rl } = useQuery<ClientRequest[]>({ queryKey: ['pm-client-requests', projectId], queryFn: async () => list<ClientRequest>(await apiClient.get(`${base}/client-requests`)) })
  const { data: uploads = [], isLoading: ul } = useQuery<ClientUpload[]>({ queryKey: ['pm-client-uploads', projectId], queryFn: async () => list<ClientUpload>(await apiClient.get(`${base}/client-uploads`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['pm-client-requests', projectId] }); qc.invalidateQueries({ queryKey: ['pm-client-uploads', projectId] }) }
  const openReq = requests.filter(r => r.status === 'OPEN'), pendingUp = uploads.filter(u => u.status === 'PENDING')
  const reqRows = showClosed ? requests : openReq, upRows = showClosed ? uploads : pendingUp

  return (
    <div style={{ marginBottom: 22 }}>
      <SectionTitle right={<label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--hf-text-muted)' }}>
        <input type="checkbox" checked={showClosed} onChange={e => setShowClosed(e.target.checked)} /> Show handled</label>}>
        From the client {openReq.length + pendingUp.length > 0 && <Pill tone="warning">{openReq.length + pendingUp.length} waiting</Pill>}</SectionTitle>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 14 }}>
        <div style={{ ...card, padding: 0 }}>
          <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--hf-border)', fontSize: 12, fontWeight: 700, color: 'var(--hf-text-muted)' }}>Questions & change requests</div>
          {rl ? <Empty>Loading…</Empty> : reqRows.length === 0 ? <Empty>{showClosed ? 'Nothing from the client yet.' : 'Nothing waiting.'}</Empty> : reqRows.map(r => (
            <div key={r.id} style={{ padding: '10px 14px', borderBottom: '1px solid var(--hf-border)' }}>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={muted}>{r.requestNumber}</span><Pill>{KIND_LABEL[r.kind]}</Pill><b style={{ fontSize: 13 }}>{r.title}</b>
                <span style={{ flex: 1 }} />{r.status !== 'OPEN' && <Pill tone={REQUEST_STATUS[r.status].tone}>{REQUEST_STATUS[r.status].label}</Pill>}
              </div>
              {r.description && <div style={{ fontSize: 12.5, marginTop: 4, whiteSpace: 'pre-wrap' }}>{r.description}</div>}
              <div style={{ ...muted, marginTop: 3 }}>{r.raisedByName} · {day(r.createdAt)}{r.neededBy ? ` · needed by ${day(r.neededBy)}` : ''}</div>
              {r.response && <div style={{ ...muted, marginTop: 3 }}>Reply ({r.decidedByName}): {r.response}{r.linkedType ? ` · became ${r.linkedType === 'RFI' ? 'an RFI' : 'a change order'}` : ''}</div>}
              {r.status === 'OPEN' && canWrite && <div style={{ marginTop: 6 }}><Btn small onClick={() => setDeciding(r)}>Respond</Btn></div>}
            </div>))}
        </div>
        <div style={{ ...card, padding: 0 }}>
          <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--hf-border)', fontSize: 12, fontWeight: 700, color: 'var(--hf-text-muted)' }}>Files sent by the client</div>
          {ul ? <Empty>Loading…</Empty> : upRows.length === 0 ? <Empty>{showClosed ? 'No files from the client yet.' : 'Nothing waiting.'}</Empty> : upRows.map(u => (
            <div key={u.id} style={{ padding: '10px 14px', borderBottom: '1px solid var(--hf-border)' }}>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <b style={{ fontSize: 13, wordBreak: 'break-word', flex: 1 }}>{u.fileName}</b>
                {u.status !== 'PENDING' && <Pill tone={UPLOAD_STATUS[u.status].tone}>{u.status === 'ACCEPTED' ? 'In the register' : 'Rejected'}</Pill>}
              </div>
              <div style={muted}>{u.uploadedByName} · {day(u.createdAt)} · {(u.sizeBytes / 1024 / 1024).toFixed(1)} MB</div>
              {u.note && <div style={{ fontSize: 12.5, marginTop: 3 }}>{u.note}</div>}
              {u.reason && <div style={{ ...muted, marginTop: 3 }}>Reason: {u.reason}</div>}
              <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                <Btn small kind="ghost" onClick={() => downloadFile(`${base}/client-uploads/${u.id}/download`, u.fileName)}><Download size={12} /> Open</Btn>
                {u.status === 'PENDING' && canWrite && <>
                  <Btn small kind="ghost" onClick={() => setRejecting(u)}><X size={12} /> Reject</Btn>
                  <Btn small onClick={() => setAccepting(u)}><Check size={12} /> Add to register</Btn></>}
              </div>
            </div>))}
        </div>
      </div>
      {deciding && <DecideDialog request={deciding} path={`${base}/client-requests/${deciding.id}/decide`} onClose={() => setDeciding(null)} onDone={refresh} />}
      {accepting && <AcceptFileDialog base={base} upload={accepting} onClose={() => setAccepting(null)} onDone={refresh} />}
      {rejecting && <ReasonDialog title={`Reject ${rejecting.fileName}`} confirm="Reject" danger max={1000} intro="The client sees this reason."
        onClose={() => setRejecting(null)}
        onSubmit={async reason => { await apiClient.post(`${base}/client-uploads/${rejecting.id}/reject`, { reason }); toast.success('File rejected'); refresh() }} />}
    </div>
  )
}

const DOC_TYPES = ['GENERAL', 'DRAWING', 'SUBMITTAL', 'CONTRACT', 'REPORT', 'PHOTO']

function AcceptFileDialog({ base, upload, onClose, onDone }: { base: string; upload: ClientUpload; onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState({ title: upload.fileName.replace(/\.[^.]+$/, ''), documentType: /\.(jpe?g|png|heic|webp)$/i.test(upload.fileName) ? 'PHOTO' : 'GENERAL', visibility: 'INTERNAL', documentNumber: '', revision: '' })
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const save = async () => {
    if (!f.title.trim()) { setErr('Give the document a title'); return }
    setBusy(true); setErr('')
    try {
      await apiClient.post(`${base}/client-uploads/${upload.id}/accept`, { title: f.title.trim(), documentType: f.documentType, visibility: f.visibility,
        documentNumber: f.documentNumber.trim() || null, revision: f.revision.trim() || null })
      toast.success('Added to the document register'); onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not add the file')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Add to the document register" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Adding…' : 'Add'}</Btn></>}>
      <div style={{ ...muted, marginBottom: 10 }}>{upload.fileName} from {upload.uploadedByName}. It goes in as a new draft entry (or a new revision if you give an existing number) and follows your normal review.</div>
      <Field label="Title"><input value={f.title} maxLength={300} onChange={e => set('title', e.target.value)} style={inp} /></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Type"><select value={f.documentType} onChange={e => set('documentType', e.target.value)} style={inp}>{DOC_TYPES.map(t => <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>)}</select></Field>
        <Field label="Visible to"><select value={f.visibility} onChange={e => set('visibility', e.target.value)} style={inp}><option value="INTERNAL">Project team only</option><option value="CLIENT">Also the client, once approved</option></select></Field>
        <Field label="Existing number (for a new revision)"><input value={f.documentNumber} maxLength={50} onChange={e => set('documentNumber', e.target.value)} style={inp} /></Field>
        <Field label="Revision"><input value={f.revision} maxLength={20} onChange={e => set('revision', e.target.value)} style={inp} /></Field>
      </div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
