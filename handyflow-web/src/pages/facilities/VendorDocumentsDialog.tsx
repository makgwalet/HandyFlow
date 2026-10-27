// src/pages/facilities/VendorDocumentsDialog.tsx
//
// A vendor's compliance position: which required documents are missing, lapsed or about to expire, whether the vendor
// may be given work, and the documents on file (add one, revoke one). Insurance, COIDA and tax clearance must be in
// force before a vendor can be approved.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { facilitiesApi, type Vendor, type VendorDocument } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, Dialog, Field, Notice, StatusChip } from './ui'
import { ReasonDialog } from './ComplianceForms'
import { VENDOR_DOC_TYPES, fmtDate, fmtMoney, inputStyle, titleCase } from './format'

const two: React.CSSProperties = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }
const blank = (v: string) => (v.trim() === '' ? undefined : v.trim())

export default function VendorDocumentsDialog({ vendor, onClose }: { vendor: Vendor; onClose: () => void }) {
  const [adding, setAdding] = useState(false)
  const [revoking, setRevoking] = useState<VendorDocument | null>(null)
  const canManage = usePermissions(['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_COMPLIANCE_MANAGE'])
  const q = useQuery({ queryKey: ['facilities', 'vendor-compliance', vendor.id], queryFn: () => facilitiesApi.vendorCompliance(vendor.id) })
  const c = q.data
  return (
    <Dialog title={`${vendor.companyName} — compliance`} onClose={onClose} side width={560}>
      {q.isError && <Notice tone="bad">The compliance position could not be loaded.</Notice>}
      {q.isLoading && <div aria-busy="true" style={{ color: 'var(--hf-text-muted)', fontSize: 13 }}>Loading…</div>}
      {c && (
        <>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
            <StatusChip status={c.onboardingStatus} />
            <Chip tone={c.eligible ? 'ok' : 'bad'}>{c.eligible ? 'Can be given work' : 'Cannot be given work'}</Chip>
            <Chip tone={c.complianceStatus === 'COMPLIANT' ? 'ok' : 'warn'}>{titleCase(c.complianceStatus)}</Chip>
          </div>
          {c.problems.length > 0 && (
            <Notice tone="warn"><ul style={{ margin: 0, paddingLeft: 18 }}>{c.problems.map(p => <li key={p}>{p}</li>)}</ul></Notice>
          )}
          {c.expiring.length > 0 && (
            <div style={{ marginTop: 10 }}><Notice tone="info">Expiring soon: {c.expiring.map(e => `${titleCase(e.docType)} in ${e.daysLeft} d (${fmtDate(e.expiryDate)})`).join(' · ')}</Notice></div>
          )}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '20px 0 8px' }}>
            <h3 style={{ margin: 0, fontSize: 13, fontWeight: 700 }}>Documents on file</h3>
            {canManage && <Btn onClick={() => setAdding(true)}><Plus size={14} />Add document</Btn>}
          </div>
          {c.documents.length === 0 && <div style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>No documents yet.</div>}
          {c.documents.map(d => (
            <div key={d.id} style={{ display: 'flex', gap: 10, alignItems: 'flex-start', padding: '10px 0', borderTop: '1px solid var(--hf-border-subtle)' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13.5, fontWeight: 600 }}>{titleCase(d.docType)} {d.reference && <span style={{ fontWeight: 400, color: 'var(--hf-text-muted)' }}>· {d.reference}</span>}</div>
                <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>
                  {d.issuedBy ? `${d.issuedBy} · ` : ''}{d.expiryDate ? `expires ${fmtDate(d.expiryDate)}` : 'no expiry'}{d.coverAmount != null ? ` · cover ${fmtMoney(d.coverAmount)}` : ''}
                </div>
                {d.revokedReason && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>Revoked: {d.revokedReason}</div>}
              </div>
              <StatusChip status={d.status} />
              {canManage && d.status !== 'REVOKED' && <Btn onClick={() => setRevoking(d)}>Revoke</Btn>}
            </div>
          ))}
        </>
      )}
      {adding && <AddDocumentDialog vendor={vendor} onClose={() => setAdding(false)} />}
      {revoking && (
        <ReasonDialog title={`Revoke ${titleCase(revoking.docType)}`} confirm="Revoke" onClose={() => setRevoking(null)}
          intro="A revoked document no longer counts towards the vendor's compliance." invalidate={['facilities']}
          onConfirm={reason => facilitiesApi.revokeVendorDocument(vendor.id, revoking.id, reason)} />
      )}
    </Dialog>
  )
}

function AddDocumentDialog({ vendor, onClose }: { vendor: Vendor; onClose: () => void }) {
  const qc = useQueryClient()
  const [docType, setDocType] = useState('PUBLIC_LIABILITY_INSURANCE')
  const [reference, setReference] = useState('')
  const [issuedBy, setIssuedBy] = useState('')
  const [issueDate, setIssueDate] = useState('')
  const [expiryDate, setExpiryDate] = useState('')
  const [cover, setCover] = useState('')
  const [ref, setRef] = useState('')
  const needsExpiry = docType !== 'OTHER'
  const coverNum = cover.trim() === '' ? null : Number(cover)
  const valid = (!needsExpiry || !!expiryDate) && (coverNum == null || (Number.isFinite(coverNum) && coverNum >= 0)) && (!issueDate || !expiryDate || expiryDate > issueDate)
  const save = useMutation({
    mutationFn: () => facilitiesApi.addVendorDocument(vendor.id, {
      docType, reference: blank(reference), issuedBy: blank(issuedBy), issueDate: issueDate || null, expiryDate: expiryDate || null,
      coverAmount: coverNum, documentRef: blank(ref),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success('Document added'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title="Add vendor document" onClose={onClose} width={520}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!valid} onClick={() => save.mutate()}>Add</Btn></>}>
      <Field label="Document *"><select value={docType} onChange={e => setDocType(e.target.value)} style={inputStyle}>{VENDOR_DOC_TYPES.map(t => <option key={t} value={t}>{titleCase(t)}</option>)}</select></Field>
      <div style={two}>
        <Field label="Reference / policy number"><input value={reference} onChange={e => setReference(e.target.value)} maxLength={120} style={inputStyle} /></Field>
        <Field label="Issued by"><input value={issuedBy} onChange={e => setIssuedBy(e.target.value)} maxLength={200} style={inputStyle} /></Field>
        <Field label="Issue date"><input type="date" value={issueDate} onChange={e => setIssueDate(e.target.value)} style={inputStyle} /></Field>
        <Field label={needsExpiry ? 'Expiry date *' : 'Expiry date'}><input type="date" value={expiryDate} onChange={e => setExpiryDate(e.target.value)} style={inputStyle} /></Field>
      </div>
      <Field label="Cover amount (R)" hint="For insurance: the amount the policy covers."><input inputMode="decimal" value={cover} onChange={e => setCover(e.target.value)} style={inputStyle} /></Field>
      <Field label="Document reference" hint="Where the copy is kept."><input value={ref} onChange={e => setRef(e.target.value)} maxLength={500} style={inputStyle} /></Field>
    </Dialog>
  )
}
