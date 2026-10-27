// src/pages/facilities/InspectionsTab.tsx
//
// Inspection checklists. A template is a list of prompts (some marked critical); an inspection answers each prompt
// PASS, FAIL or N/A. Completing an inspection with any FAIL makes the result FAIL, and the server raises a
// corrective action for each failure (critical failures are flagged). The server enforces the rules.
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ClipboardCheck, ListChecks, Plus, Trash2 } from 'lucide-react'
import { facilitiesApi, type Inspection, type InspectionTemplate } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, DefList, Dialog, Field, Notice, StatusChip, Toolbar, type Column } from './ui'
import { EvidencePanel } from './Evidence'
import { fmtDateTime, inputStyle } from './format'

const CAN_INSPECT = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_TECHNICIAN', 'FACILITIES_INSPECT']
const CAN_TEMPLATES = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_COMPLIANCE_MANAGE']
const STATUSES = [{ id: '', label: 'All' }, { id: 'IN_PROGRESS', label: 'In progress' }, { id: 'COMPLETED', label: 'Completed' }, { id: 'CANCELLED', label: 'Cancelled' }]

export default function InspectionsTab() {
  const [status, setStatus] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [templates, setTemplates] = useState(false)
  const canInspect = usePermissions(CAN_INSPECT), canTemplates = usePermissions(CAN_TEMPLATES)
  const q = useQuery({ queryKey: ['facilities', 'inspections', status], queryFn: () => facilitiesApi.inspections(status || undefined) })
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const siteName = useMemo(() => new Map((sites.data ?? []).map(s => [s.id, s.name])), [sites.data])
  const cols: Column<Inspection>[] = [
    { key: 't', header: 'Checklist', render: i => <strong>{i.templateName}</strong> },
    { key: 's', header: 'Site', render: i => siteName.get(i.siteId) ?? '—' },
    { key: 'by', header: 'Inspector', render: i => i.inspectorName ?? '—' },
    { key: 'p', header: 'Answered', align: 'right', render: i => `${i.answered} / ${i.total}` },
    { key: 'r', header: 'Result', render: i => i.result ? <Chip tone={i.result === 'PASS' ? 'ok' : 'bad'}>{i.result === 'PASS' ? 'Pass' : 'Fail'}</Chip> : '—' },
    { key: 'st', header: 'Status', render: i => <StatusChip status={i.status} /> },
    { key: 'd', header: 'Started', render: i => <span style={{ whiteSpace: 'nowrap' }}>{fmtDateTime(i.startedAt)}</span> },
  ]
  return (
    <div>
      <Toolbar>
        <div role="tablist" aria-label="Filter by status" style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {STATUSES.map(f => (
            <button key={f.id} type="button" role="tab" aria-selected={status === f.id} onClick={() => setStatus(f.id)}
              style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12.5, fontWeight: 600, cursor: 'pointer', border: `1px solid ${status === f.id ? 'var(--hf-accent)' : 'var(--hf-border)'}`,
                background: status === f.id ? 'var(--hf-accent-soft)' : 'var(--hf-surface)', color: status === f.id ? 'var(--hf-accent-text-strong)' : 'var(--hf-text-secondary)' }}>{f.label}</button>
          ))}
        </div>
        {canTemplates && <Btn onClick={() => setTemplates(true)}><ListChecks size={15} />Checklists</Btn>}
        {canInspect && <Btn kind="primary" onClick={() => setStarting(true)}><Plus size={15} />Start inspection</Btn>}
      </Toolbar>
      <DataTable rows={q.data ?? []} columns={cols} rowKey={i => i.id} loading={q.isLoading} error={q.error} onRowClick={i => setOpen(i.id)}
        rowTone={i => (i.result === 'FAIL' ? 'warn' : undefined)} empty="No inspections yet." />
      {starting && <StartDialog onClose={() => setStarting(false)} onStarted={id => { setStarting(false); setOpen(id) }} />}
      {open && <InspectionPanel id={open} siteName={siteName} onClose={() => setOpen(null)} />}
      {templates && <TemplatesDialog onClose={() => setTemplates(false)} />}
    </div>
  )
}

function StartDialog({ onClose, onStarted }: { onClose: () => void; onStarted: (id: string) => void }) {
  const qc = useQueryClient()
  const [templateId, setTemplateId] = useState('')
  const [siteId, setSiteId] = useState('')
  const [assetId, setAssetId] = useState('')
  const templates = useQuery({ queryKey: ['facilities', 'inspection-templates'], queryFn: () => facilitiesApi.inspectionTemplates() })
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const assets = useQuery({ queryKey: ['facilities', 'assets', siteId], queryFn: () => facilitiesApi.assets(siteId), enabled: !!siteId })
  const start = useMutation({
    mutationFn: () => facilitiesApi.startInspection({ templateId, siteId, assetId: assetId || null }),
    onSuccess: i => { qc.invalidateQueries({ queryKey: ['facilities', 'inspections'] }); toast.success('Inspection started'); onStarted(i.id) },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title="Start inspection" onClose={onClose} width={480}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={start.isPending} disabled={!templateId || !siteId} onClick={() => start.mutate()}>Start</Btn></>}>
      <Field label="Checklist *" hint={templates.data && templates.data.length === 0 ? 'No active checklists. Create one under Checklists.' : undefined}>
        <select value={templateId} onChange={e => setTemplateId(e.target.value)} style={inputStyle}>
          <option value="">Select a checklist…</option>
          {(templates.data ?? []).filter(t => t.active).map(t => <option key={t.id} value={t.id}>{t.name} ({t.items.length} items)</option>)}
        </select>
      </Field>
      <Field label="Site *">
        <select value={siteId} onChange={e => { setSiteId(e.target.value); setAssetId('') }} style={inputStyle}>
          <option value="">Select a site…</option>
          {(sites.data ?? []).filter(s => s.status !== 'CLOSED').map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </Field>
      <Field label="Asset" hint="Optional.">
        <select value={assetId} onChange={e => setAssetId(e.target.value)} disabled={!siteId} style={inputStyle}>
          <option value="">Whole site</option>
          {(assets.data ?? []).map(a => <option key={a.id} value={a.id}>{a.assetTag} · {a.name}</option>)}
        </select>
      </Field>
    </Dialog>
  )
}

type Outcome = 'PASS' | 'FAIL' | 'NA'
const OUTCOMES: { id: Outcome; label: string }[] = [{ id: 'PASS', label: 'Pass' }, { id: 'FAIL', label: 'Fail' }, { id: 'NA', label: 'N/A' }]

function InspectionPanel({ id, siteName, onClose }: { id: string; siteName: Map<string, string>; onClose: () => void }) {
  const qc = useQueryClient()
  const canInspect = usePermissions(CAN_INSPECT)
  const q = useQuery({ queryKey: ['facilities', 'inspections', 'one', id], queryFn: () => facilitiesApi.inspection(id) })
  const [draft, setDraft] = useState<Record<string, { outcome?: Outcome; comment?: string }>>({})
  const [completing, setCompleting] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [notes, setNotes] = useState('')
  const [cancelReason, setCancelReason] = useState('')
  const insp = q.data
  const editable = canInspect && insp?.status === 'IN_PROGRESS'
  const refresh = () => { qc.invalidateQueries({ queryKey: ['facilities'] }) }

  const merged = (r: Inspection['results'][number]) => ({ outcome: draft[r.itemId]?.outcome ?? r.outcome ?? undefined, comment: draft[r.itemId]?.comment ?? r.comment ?? '' })
  const changed = Object.entries(draft).filter(([, d]) => d.outcome || d.comment !== undefined)
  const save = useMutation({
    mutationFn: () => facilitiesApi.answerInspection(id, changed.flatMap(([itemId]) => {
      const r = insp!.results.find(x => x.itemId === itemId)!; const m = merged(r)
      return m.outcome ? [{ itemId, outcome: m.outcome, comment: m.comment.trim() || undefined }] : []
    })),
    onSuccess: () => { refresh(); setDraft({}); toast.success('Answers saved') },
    onError: e => toast.error(errorMessage(e)),
  })
  const complete = useMutation({
    mutationFn: async () => {
      if (changed.length) await save.mutateAsync()
      return facilitiesApi.completeInspection(id, notes.trim() || undefined)
    },
    onSuccess: r => { refresh(); setCompleting(false); toast.success(r.result === 'FAIL' ? `Completed with failures. ${r.actions.length} corrective action(s) raised.` : 'Completed. All clear.') },
    onError: e => toast.error(errorMessage(e)),
  })
  const cancel = useMutation({
    mutationFn: () => facilitiesApi.cancelInspection(id, cancelReason.trim()),
    onSuccess: () => { refresh(); toast.success('Inspection cancelled'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  const unanswered = insp ? insp.results.filter(r => !merged(r).outcome).length : 0
  const failing = insp ? insp.results.filter(r => merged(r).outcome === 'FAIL').length : 0

  return (
    <Dialog title={insp?.templateName ?? 'Inspection'} onClose={onClose} side width={600}
      footer={editable ? (
        <>
          <Btn kind="danger" busy={cancel.isPending} onClick={() => setConfirmCancel(true)}>Cancel inspection</Btn>
          <Btn busy={save.isPending} disabled={changed.length === 0} onClick={() => save.mutate()}>Save answers</Btn>
          <Btn kind="primary" disabled={unanswered > 0} onClick={() => setCompleting(true)}>Complete</Btn>
        </>
      ) : undefined}>
      {q.isLoading && <div style={{ color: 'var(--hf-text-muted)' }}>Loading…</div>}
      {q.error && <Notice tone="bad">We couldn't load this inspection.</Notice>}
      {insp && (
        <>
          <DefList items={[
            ['Site', siteName.get(insp.siteId) ?? '—'], ['Inspector', insp.inspectorName ?? '—'], ['Started', fmtDateTime(insp.startedAt)],
            ['Status', <StatusChip key="s" status={insp.status} />],
            ...(insp.result ? [['Result', <Chip key="r" tone={insp.result === 'PASS' ? 'ok' : 'bad'}>{insp.result === 'PASS' ? 'Pass' : 'Fail'}</Chip>] as [string, React.ReactNode]] : []),
            ...(insp.completedAt ? [['Completed', fmtDateTime(insp.completedAt)] as [string, string]] : []),
            ...(insp.notes ? [['Notes', insp.notes] as [string, string]] : []),
            ...(insp.actions.length ? [['Corrective actions', `${insp.actions.length} raised. See Corrective actions.`] as [string, string]] : []),
          ]} />
          <h3 style={{ margin: '22px 0 10px', fontSize: 13, fontWeight: 700 }}>Checklist ({insp.total - unanswered} of {insp.total} answered{failing ? `, ${failing} failing` : ''})</h3>
          <ol style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: 10 }}>
            {insp.results.map(r => {
              const m = merged(r)
              return (
                <li key={r.itemId} style={{ border: '1px solid var(--hf-border)', borderRadius: 10, padding: 12, background: m.outcome === 'FAIL' ? 'var(--hf-danger-soft)' : undefined }}>
                  <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap' }}>
                    <div style={{ fontSize: 13.5, fontWeight: 600, flex: '1 1 240px' }}>{r.position}. {r.prompt} {r.critical && <Chip tone="bad">Critical</Chip>}</div>
                    <div role="radiogroup" aria-label={`Answer for ${r.prompt}`} style={{ display: 'flex', gap: 6 }}>
                      {OUTCOMES.map(o => {
                        const on = m.outcome === o.id
                        const tone = o.id === 'PASS' ? 'var(--hf-success-text)' : o.id === 'FAIL' ? 'var(--hf-danger-text)' : 'var(--hf-text-secondary)'
                        return (
                          <button key={o.id} type="button" role="radio" aria-checked={on} disabled={!editable}
                            onClick={() => setDraft(d => ({ ...d, [r.itemId]: { ...d[r.itemId], outcome: o.id } }))}
                            style={{ padding: '5px 12px', borderRadius: 8, fontSize: 12.5, fontWeight: 700, cursor: editable ? 'pointer' : 'default',
                              border: `1px solid ${on ? tone : 'var(--hf-border-strong)'}`, background: on ? 'var(--hf-surface-sunken)' : 'var(--hf-surface)', color: on ? tone : 'var(--hf-text-muted)' }}>{o.label}</button>
                        )
                      })}
                    </div>
                  </div>
                  {(editable || m.comment) && (
                    <input aria-label={`Comment for ${r.prompt}`} placeholder={m.outcome === 'FAIL' ? 'What is wrong?' : 'Comment (optional)'} disabled={!editable} value={m.comment} maxLength={1000}
                      onChange={e => setDraft(d => ({ ...d, [r.itemId]: { ...d[r.itemId], comment: e.target.value } }))} style={{ ...inputStyle, marginTop: 8, fontSize: 13 }} />
                  )}
                </li>
              )
            })}
          </ol>
          <EvidencePanel queryKey={['inspection', id]} load={() => facilitiesApi.evidence('inspections', id)}
            attach={f => facilitiesApi.attachEvidence('inspections', id, f)} canAttach={canInspect && insp?.status === 'IN_PROGRESS'}
            note="Photos of what you found, especially for failed checks." />
          {confirmCancel && (
            <Dialog title="Cancel inspection" onClose={() => setConfirmCancel(false)} width={420}
              footer={<><Btn onClick={() => setConfirmCancel(false)}>Keep going</Btn><Btn kind="danger" busy={cancel.isPending} disabled={!cancelReason.trim()} onClick={() => cancel.mutate()}>Cancel inspection</Btn></>}>
              <p style={{ margin: '0 0 14px', fontSize: 14, color: 'var(--hf-text-secondary)' }}>This inspection will be cancelled and its answers will not count.</p>
              <Field label="Reason *"><textarea value={cancelReason} onChange={e => setCancelReason(e.target.value)} rows={3} maxLength={500} style={{ ...inputStyle, resize: 'vertical' }} autoFocus /></Field>
            </Dialog>
          )}
          {completing && (
            <Dialog title="Complete inspection" onClose={() => setCompleting(false)} width={440}
              footer={<><Btn onClick={() => setCompleting(false)}>Back</Btn><Btn kind="primary" busy={complete.isPending} onClick={() => complete.mutate()}>Complete</Btn></>}>
              {failing > 0
                ? <Notice tone="warn">{failing} item{failing > 1 ? 's' : ''} failed. A corrective action will be raised for each one.</Notice>
                : <Notice tone="info">Every item passed or is not applicable.</Notice>}
              <div style={{ height: 14 }} />
              <Field label="Notes"><textarea value={notes} onChange={e => setNotes(e.target.value)} rows={3} maxLength={2000} style={{ ...inputStyle, resize: 'vertical' }} /></Field>
            </Dialog>
          )}
        </>
      )}
    </Dialog>
  )
}

// ── checklist templates ─────────────────────────────────────────────────────────────────────
function TemplatesDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<InspectionTemplate | 'new' | null>(null)
  const q = useQuery({ queryKey: ['facilities', 'inspection-templates', 'all'], queryFn: () => facilitiesApi.inspectionTemplates(true) })
  const toggle = useMutation({
    mutationFn: (t: InspectionTemplate) => facilitiesApi.templateActive(t.id, t.active ? 'deactivate' : 'activate'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['facilities', 'inspection-templates'] }),
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title="Inspection checklists" onClose={onClose} width={620} footer={<Btn kind="primary" onClick={() => setEditing('new')}><Plus size={14} />New checklist</Btn>}>
      {q.isLoading ? <div style={{ color: 'var(--hf-text-muted)' }}>Loading…</div> : (q.data ?? []).length === 0
        ? <div style={{ color: 'var(--hf-text-muted)', fontSize: 13 }}>No checklists yet.</div>
        : (q.data ?? []).map(t => (
          <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 0', borderTop: '1px solid var(--hf-border-subtle)' }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600, fontSize: 13.5 }}>{t.name} <span style={{ color: 'var(--hf-text-faint)', fontWeight: 400 }}>rev {t.revision}</span></div>
              <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{t.items.length} items{t.assetCategory ? ` · ${t.assetCategory}` : ''}{t.active ? '' : ' · inactive'}</div>
            </div>
            <Btn onClick={() => setEditing(t)}>Edit</Btn>
            <Btn busy={toggle.isPending && toggle.variables?.id === t.id} onClick={() => toggle.mutate(t)}>{t.active ? 'Deactivate' : 'Activate'}</Btn>
          </div>
        ))}
      {editing && <TemplateForm template={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </Dialog>
  )
}

function TemplateForm({ template, onClose }: { template?: InspectionTemplate; onClose: () => void }) {
  const qc = useQueryClient()
  const [name, setName] = useState(template?.name ?? '')
  const [description, setDescription] = useState(template?.description ?? '')
  const [category, setCategory] = useState(template?.assetCategory ?? '')
  const [items, setItems] = useState(template ? template.items.map(i => ({ prompt: i.prompt, critical: i.critical })) : [{ prompt: '', critical: false }])
  const save = useMutation({
    mutationFn: () => facilitiesApi.saveInspectionTemplate(template?.id ?? null, {
      name: name.trim(), description: description.trim() || undefined, assetCategory: category.trim() || undefined, items: items.map(i => ({ prompt: i.prompt.trim(), critical: i.critical })),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities', 'inspection-templates'] }); toast.success('Checklist saved'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  const valid = name.trim() && items.length > 0 && items.every(i => i.prompt.trim())
  const setItem = (n: number, p: Partial<(typeof items)[number]>) => setItems(l => l.map((x, j) => (j === n ? { ...x, ...p } : x)))
  return (
    <Dialog title={template ? `Edit ${template.name}` : 'New checklist'} onClose={onClose} width={560}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!valid} onClick={() => save.mutate()}>Save</Btn></>}>
      {template && <p style={{ margin: '0 0 14px', fontSize: 12.5, color: 'var(--hf-text-muted)' }}>Saving a change starts a new revision. Inspections already under way keep the revision they started with.</p>}
      <Field label="Name *"><input value={name} onChange={e => setName(e.target.value)} maxLength={200} style={inputStyle} autoFocus /></Field>
      <Field label="Asset category" hint="Optional, e.g. HVAC or LIFT."><input value={category} onChange={e => setCategory(e.target.value)} maxLength={60} style={inputStyle} /></Field>
      <Field label="Description"><input value={description} onChange={e => setDescription(e.target.value)} maxLength={1000} style={inputStyle} /></Field>
      <h3 style={{ margin: '4px 0 8px', fontSize: 13, fontWeight: 700 }}><ClipboardCheck size={14} style={{ verticalAlign: -2 }} /> Items</h3>
      {items.map((it, n) => (
        <div key={n} style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto 34px', gap: 8, alignItems: 'center', marginBottom: 8 }}>
          <input aria-label={`Prompt ${n + 1}`} value={it.prompt} onChange={e => setItem(n, { prompt: e.target.value })} maxLength={300} placeholder="What should the inspector check?" style={inputStyle} />
          <label style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12.5, color: 'var(--hf-text-secondary)', whiteSpace: 'nowrap' }}>
            <input type="checkbox" checked={it.critical} onChange={e => setItem(n, { critical: e.target.checked })} /> Critical
          </label>
          <button type="button" aria-label={`Remove item ${n + 1}`} disabled={items.length === 1} onClick={() => setItems(l => l.filter((_, j) => j !== n))}
            style={{ background: 'none', border: 'none', cursor: items.length === 1 ? 'not-allowed' : 'pointer', color: 'var(--hf-text-muted)', opacity: items.length === 1 ? 0.4 : 1, display: 'flex' }}><Trash2 size={16} /></button>
        </div>
      ))}
      <Btn onClick={() => setItems(l => [...l, { prompt: '', critical: false }])} disabled={items.length >= 100}><Plus size={14} />Add item</Btn>
    </Dialog>
  )
}
