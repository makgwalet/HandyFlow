// src/pages/facilities/ComplianceForms.tsx
//
// Dialogs for compliance paperwork: issue or renew a certificate, and the shared "give a reason" dialog used for
// revoking certificates, vendor documents and licences.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { facilitiesApi, type Certificate } from '../../api/facilities.api'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, Dialog, Field } from './ui'
import { CERTIFICATE_TYPES, inputStyle, titleCase } from './format'

const two: React.CSSProperties = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }
const blank = (v: string) => (v.trim() === '' ? undefined : v.trim())
const addYear = (iso: string) => { const d = new Date(`${iso}T00:00:00`); d.setFullYear(d.getFullYear() + 1); return d.toISOString().slice(0, 10) }
const today = () => new Date().toISOString().slice(0, 10)

/** Issue a new certificate, or (with `renewing`) the next one for the same site, asset and type, dated from today. */
export function CertificateFormDialog({ renewing, onClose }: { renewing?: Certificate; onClose: () => void }) {
  const qc = useQueryClient()
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const [siteId, setSiteId] = useState(renewing?.siteId ?? '')
  const [assetId, setAssetId] = useState(renewing?.assetId ?? '')
  const assets = useQuery({ queryKey: ['facilities', 'assets', siteId], queryFn: () => facilitiesApi.assets(siteId), enabled: !!siteId })
  const [type, setType] = useState(renewing?.certificateType ?? 'ELECTRICAL_COC')
  const [number, setNumber] = useState('')
  const [issuedBy, setIssuedBy] = useState(renewing?.issuedBy ?? '')
  const [issueDate, setIssueDate] = useState(today())
  const [expiryDate, setExpiryDate] = useState(addYear(today()))
  const [ref, setRef] = useState('')
  const valid = !!siteId && !!issueDate && !!expiryDate && expiryDate > issueDate
  const save = useMutation({
    mutationFn: () => facilitiesApi.issueCertificate({
      siteId, assetId: assetId || null, certificateType: type, certificateNumber: blank(number), issuedBy: blank(issuedBy),
      issueDate, expiryDate, documentRef: blank(ref),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(renewing ? 'Certificate renewed' : 'Certificate recorded'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={renewing ? `Renew ${titleCase(renewing.certificateType)}` : 'New certificate'} onClose={onClose} width={540}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!valid} onClick={() => save.mutate()}>{renewing ? 'Renew' : 'Save'}</Btn></>}>
      {renewing && <p style={{ margin: '0 0 14px', fontSize: 13, color: 'var(--hf-text-secondary)' }}>Enter the new certificate's details. The old certificate stays on record.</p>}
      <Field label="Site *">
        <select value={siteId} onChange={e => { setSiteId(e.target.value); setAssetId('') }} disabled={!!renewing?.siteId} style={inputStyle}>
          <option value="">Select a site…</option>{(sites.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </Field>
      <Field label="Asset (optional)">
        <select value={assetId} onChange={e => setAssetId(e.target.value)} disabled={!siteId || !!renewing} style={inputStyle}>
          <option value="">Whole site</option>{(assets.data ?? []).map(a => <option key={a.id} value={a.id}>{a.assetTag} — {a.name}</option>)}
        </select>
      </Field>
      <div style={two}>
        <Field label="Type *"><select value={type} onChange={e => setType(e.target.value)} disabled={!!renewing} style={inputStyle}>{CERTIFICATE_TYPES.map(t => <option key={t} value={t}>{titleCase(t)}</option>)}</select></Field>
        <Field label="Certificate number"><input value={number} onChange={e => setNumber(e.target.value)} maxLength={120} style={inputStyle} /></Field>
        <Field label="Issue date *"><input type="date" value={issueDate} onChange={e => setIssueDate(e.target.value)} style={inputStyle} /></Field>
        <Field label="Expiry date *" hint={expiryDate && expiryDate <= issueDate ? 'Must be after the issue date' : undefined}><input type="date" value={expiryDate} onChange={e => setExpiryDate(e.target.value)} style={inputStyle} /></Field>
      </div>
      <Field label="Issued by"><input value={issuedBy} onChange={e => setIssuedBy(e.target.value)} maxLength={200} style={inputStyle} /></Field>
      <Field label="Document reference" hint="Where the signed copy is kept, e.g. a file or drive reference."><input value={ref} onChange={e => setRef(e.target.value)} maxLength={500} style={inputStyle} /></Field>
    </Dialog>
  )
}

/** Asks for a reason, then runs `onConfirm`. Used wherever the server requires a reason for an irreversible change. */
export function ReasonDialog({ title, intro, confirm, danger = true, onConfirm, onClose, invalidate = ['facilities'] }:
  { title: string; intro?: string; confirm: string; danger?: boolean; onConfirm: (reason: string) => Promise<unknown>; onClose: () => void; invalidate?: string[] }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState('')
  const run = useMutation({
    mutationFn: () => onConfirm(reason.trim()),
    onSuccess: () => { qc.invalidateQueries({ queryKey: invalidate }); toast.success('Done'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={title} onClose={onClose} width={460}
      footer={<><Btn onClick={onClose}>Back</Btn><Btn kind={danger ? 'danger' : 'primary'} busy={run.isPending} disabled={!reason.trim()} onClick={() => run.mutate()}>{confirm}</Btn></>}>
      {intro && <p style={{ margin: '0 0 14px', fontSize: 13.5, color: 'var(--hf-text-secondary)' }}>{intro}</p>}
      <Field label="Reason *"><textarea value={reason} onChange={e => setReason(e.target.value)} rows={3} maxLength={500} style={{ ...inputStyle, resize: 'vertical' }} autoFocus /></Field>
    </Dialog>
  )
}
