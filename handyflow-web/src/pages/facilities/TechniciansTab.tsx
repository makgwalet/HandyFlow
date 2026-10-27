// src/pages/facilities/TechniciansTab.tsx
//
// The technician directory and each technician's trade licences and tickets (electrical wireman, gas installer, first
// aid, driver's licence…). A licence has an expiry date; the register highlights what has lapsed or is about to.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, BadgeCheck, Pencil, Plus } from 'lucide-react'
import { facilitiesApi, type Technician, type TechnicianLicence } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, Dialog, Field, Notice, StatusChip, Toolbar, type Column } from './ui'
import { ReasonDialog } from './ComplianceForms'
import { EvidencePanel } from './Evidence'
import { LICENCE_TYPES, fmtDate, inputStyle, titleCase } from './format'

const two: React.CSSProperties = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }
const blank = (v: string) => (v.trim() === '' ? undefined : v.trim())

export default function TechniciansTab() {
  const [search, setSearch] = useState('')
  const [form, setForm] = useState<Technician | 'new' | null>(null)
  const [licencesFor, setLicencesFor] = useState<Technician | null>(null)
  const qc = useQueryClient()
  const canManage = usePermissions(['FACILITIES_MANAGE', 'FACILITIES_ADMIN'])
  const q = useQuery({ queryKey: ['facilities', 'technicians'], queryFn: facilitiesApi.technicians })
  const attention = useQuery({ queryKey: ['facilities', 'licence-attention'], queryFn: () => facilitiesApi.licencesNeedingAttention(60) })
  const toggle = useMutation({
    mutationFn: (t: Technician) => facilitiesApi.technicianActive(t.id, t.active ? 'deactivate' : 'reactivate'),
    onSuccess: t => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${t.name} ${t.active ? 'reactivated' : 'deactivated'}`) },
    onError: e => toast.error(errorMessage(e)),
  })
  const rows = (q.data ?? []).filter(t => !search.trim() || `${t.name} ${t.specialization ?? ''} ${t.contactEmail ?? ''}`.toLowerCase().includes(search.trim().toLowerCase()))
  const flagged = new Set((attention.data ?? []).map(l => l.technicianId))
  const cols: Column<Technician>[] = [
    { key: 'name', header: 'Technician', render: t => <div><strong>{t.name}</strong><div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{t.specialization ?? ''}</div></div> },
    { key: 'contact', header: 'Contact', render: t => <div>{t.contactPhone ?? '—'}<div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{t.contactEmail ?? ''}</div></div> },
    { key: 'user', header: 'Sign-in', render: t => (t.linkedUserId ? <Chip tone="ok">Linked</Chip> : <span style={{ color: 'var(--hf-text-muted)' }}>Not linked</span>) },
    { key: 'lic', header: 'Licences', render: t => (
      <Btn onClick={() => setLicencesFor(t)}>
        <BadgeCheck size={13} />Licences{flagged.has(t.id) && <Chip tone="warn" icon={<AlertTriangle size={11} />}>Check</Chip>}
      </Btn>
    ) },
    { key: 'act', header: 'Status', render: t => <StatusChip status={t.active ? 'ACTIVE' : 'INACTIVE'} /> },
    ...(canManage ? [{
      key: 'do', header: '', align: 'right' as const, render: (t: Technician) => (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          <Btn onClick={() => setForm(t)}><Pencil size={13} />Edit</Btn>
          <Btn busy={toggle.isPending && toggle.variables?.id === t.id} onClick={() => toggle.mutate(t)}>{t.active ? 'Deactivate' : 'Reactivate'}</Btn>
        </span>
      ),
    }] : []),
  ]
  return (
    <div>
      {(attention.data?.length ?? 0) > 0 && (
        <div style={{ marginBottom: 14 }}>
          <Notice tone="warn" icon={<AlertTriangle size={16} />}>
            {attention.data!.length} licence{attention.data!.length === 1 ? '' : 's'} expired or expiring within 60 days:{' '}
            {attention.data!.slice(0, 4).map(l => `${l.technicianName ?? 'Technician'} (${titleCase(l.licenceType)}, ${l.daysToExpiry != null && l.daysToExpiry < 0 ? 'expired' : `${l.daysToExpiry} d`})`).join(' · ')}
            {attention.data!.length > 4 ? ' …' : ''}
          </Notice>
        </div>
      )}
      <Toolbar search={search} onSearch={setSearch} placeholder="Search technicians">
        {canManage && <Btn kind="primary" onClick={() => setForm('new')}><Plus size={15} />New technician</Btn>}
      </Toolbar>
      <DataTable rows={rows} columns={cols} rowKey={t => t.id} loading={q.isLoading} error={q.error} empty="No technicians yet." />
      {form && <TechnicianFormDialog technician={form === 'new' ? undefined : form} onClose={() => setForm(null)} />}
      {licencesFor && <LicencesDialog technician={licencesFor} canManage={canManage} onClose={() => setLicencesFor(null)} />}
    </div>
  )
}

function TechnicianFormDialog({ technician, onClose }: { technician?: Technician; onClose: () => void }) {
  const qc = useQueryClient()
  const [name, setName] = useState(technician?.name ?? '')
  const [phone, setPhone] = useState(technician?.contactPhone ?? '')
  const [email, setEmail] = useState(technician?.contactEmail ?? '')
  const [spec, setSpec] = useState(technician?.specialization ?? '')
  const save = useMutation({
    mutationFn: () => {
      const b = { name: name.trim(), contactPhone: blank(phone), contactEmail: blank(email), specialization: blank(spec), linkedUserId: technician?.linkedUserId ?? null }
      return technician ? facilitiesApi.updateTechnician(technician.id, b) : facilitiesApi.createTechnician(b)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(technician ? 'Technician updated' : 'Technician added'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={technician ? `Edit ${technician.name}` : 'New technician'} onClose={onClose} width={480}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!name.trim()} onClick={() => save.mutate()}>Save</Btn></>}>
      <Field label="Name *"><input value={name} onChange={e => setName(e.target.value)} maxLength={200} style={inputStyle} autoFocus /></Field>
      <div style={two}>
        <Field label="Phone"><input value={phone} onChange={e => setPhone(e.target.value)} maxLength={40} style={inputStyle} /></Field>
        <Field label="Email"><input type="email" value={email} onChange={e => setEmail(e.target.value)} maxLength={200} style={inputStyle} /></Field>
      </div>
      <Field label="Specialisation" hint="e.g. Electrical, HVAC, Plumbing"><input value={spec} onChange={e => setSpec(e.target.value)} maxLength={120} style={inputStyle} /></Field>
    </Dialog>
  )
}

function LicencesDialog({ technician, canManage, onClose }: { technician: Technician; canManage: boolean; onClose: () => void }) {
  const [adding, setAdding] = useState(false)
  const [revoking, setRevoking] = useState<TechnicianLicence | null>(null)
  const [filesFor, setFilesFor] = useState<TechnicianLicence | null>(null)
  const q = useQuery({ queryKey: ['facilities', 'licences', technician.id], queryFn: () => facilitiesApi.licences(technician.id) })
  const licences = q.data ?? []
  return (
    <Dialog title={`${technician.name} — licences`} onClose={onClose} side width={560}
      footer={canManage ? <Btn kind="primary" onClick={() => setAdding(true)}><Plus size={14} />Add licence</Btn> : undefined}>
      {q.isError && <Notice tone="bad">The licences could not be loaded.</Notice>}
      {q.isLoading && <div aria-busy="true" style={{ color: 'var(--hf-text-muted)', fontSize: 13 }}>Loading…</div>}
      {!q.isLoading && !q.isError && licences.length === 0 && <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>No licences recorded.</div>}
      {licences.map(l => (
        <div key={l.id} style={{ padding: '11px 0', borderTop: '1px solid var(--hf-border-subtle)' }}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13.5, fontWeight: 600 }}>{titleCase(l.licenceType)}{l.reference && <span style={{ fontWeight: 400, color: 'var(--hf-text-muted)' }}> · {l.reference}</span>}</div>
              <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>
                {l.issuedBy ? `${l.issuedBy} · ` : ''}{l.expiryDate ? `expires ${fmtDate(l.expiryDate)}${l.daysToExpiry != null && l.inForce ? ` (${l.daysToExpiry} d)` : ''}` : 'no expiry'}
              </div>
              {l.revokedReason && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>Revoked: {l.revokedReason}</div>}
            </div>
            <StatusChip status={l.status} />
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            <Btn onClick={() => setFilesFor(filesFor?.id === l.id ? null : l)}>{filesFor?.id === l.id ? 'Hide copy' : 'Copy of licence'}</Btn>
            {canManage && l.status !== 'REVOKED' && <Btn onClick={() => setRevoking(l)}>Revoke</Btn>}
          </div>
          {filesFor?.id === l.id && (
            <EvidencePanel queryKey={['licence', l.id]} load={() => facilitiesApi.licenceEvidence(technician.id, l.id)}
              attach={f => facilitiesApi.attachLicenceEvidence(technician.id, l.id, f)} canAttach={canManage} />
          )}
        </div>
      ))}
      {adding && <AddLicenceDialog technician={technician} onClose={() => setAdding(false)} />}
      {revoking && (
        <ReasonDialog title={`Revoke ${titleCase(revoking.licenceType)}`} confirm="Revoke" onClose={() => setRevoking(null)}
          intro="A revoked licence no longer counts as in force." onConfirm={reason => facilitiesApi.revokeLicence(technician.id, revoking.id, reason)} />
      )}
    </Dialog>
  )
}

function AddLicenceDialog({ technician, onClose }: { technician: Technician; onClose: () => void }) {
  const qc = useQueryClient()
  const [type, setType] = useState('ELECTRICAL_WIREMAN')
  const [reference, setReference] = useState('')
  const [issuedBy, setIssuedBy] = useState('')
  const [issueDate, setIssueDate] = useState('')
  const [expiryDate, setExpiryDate] = useState('')
  const needsExpiry = type !== 'OTHER'
  const valid = (!needsExpiry || !!expiryDate) && (!issueDate || !expiryDate || expiryDate > issueDate)
  const save = useMutation({
    mutationFn: () => facilitiesApi.addLicence(technician.id, { licenceType: type, reference: blank(reference), issuedBy: blank(issuedBy), issueDate: issueDate || null, expiryDate: expiryDate || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success('Licence added'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title="Add licence" onClose={onClose} width={480}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!valid} onClick={() => save.mutate()}>Add</Btn></>}>
      <Field label="Licence *"><select value={type} onChange={e => setType(e.target.value)} style={inputStyle}>{LICENCE_TYPES.map(t => <option key={t} value={t}>{titleCase(t)}</option>)}</select></Field>
      <div style={two}>
        <Field label="Registration number"><input value={reference} onChange={e => setReference(e.target.value)} maxLength={120} style={inputStyle} /></Field>
        <Field label="Issued by"><input value={issuedBy} onChange={e => setIssuedBy(e.target.value)} maxLength={200} style={inputStyle} /></Field>
        <Field label="Issue date"><input type="date" value={issueDate} onChange={e => setIssueDate(e.target.value)} style={inputStyle} /></Field>
        <Field label={needsExpiry ? 'Expiry date *' : 'Expiry date'}><input type="date" value={expiryDate} onChange={e => setExpiryDate(e.target.value)} style={inputStyle} /></Field>
      </div>
    </Dialog>
  )
}
