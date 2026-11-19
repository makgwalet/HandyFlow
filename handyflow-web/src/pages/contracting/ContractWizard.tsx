// Five-step contract creation wizard: Basics, Parties, Terms, Review, Finish.
// Saving creates a normal DRAFT contract through the existing create and add-party endpoints; nothing is sent or signed here.
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Check, Plus, Trash2, X } from 'lucide-react'
import { apiClient } from '../../api/client'
import { apiError, unwrapOne, fmtMoney, type ContractTemplateRow } from './contractingApi'
import { templateUnusableReason } from './templateHealth'
import {
  WIZARD_STEPS, WIZARD_CONTRACT_TYPES, emptyDraft, emptyParty, termsFor, validateStep, firstInvalidStep, buildContractPayload,
  buildPartyPayload, readinessNotes, unfilledVariables, type WizardDraft, type WizardParty, type WizardStep, type Errors,
} from './contractWizard'

const typeLabel = (t: string) => WIZARD_CONTRACT_TYPES.find(x => x[0] === t)?.[1] ?? t

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, boxSizing: 'border-box', background: 'var(--hf-surface)' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 4 }
const btn = (primary = false): React.CSSProperties => ({
  padding: '9px 16px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6,
  border: primary ? 'none' : '1px solid var(--hf-border)',
  background: primary ? 'var(--hf-primary)' : 'var(--hf-surface)',
  color: primary ? 'var(--hf-text-on-solid)' : 'var(--hf-text-secondary)',
})
const grid: React.CSSProperties = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }

function Field({ label, error, children, wide }: { label: string; error?: string; children: React.ReactNode; wide?: boolean }) {
  return (
    <div style={wide ? { gridColumn: '1/-1' } : undefined}>
      <label style={lbl}>{label}{children}</label>
      {error && <div role="alert" style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginTop: 3 }}>{error}</div>}
    </div>
  )
}

interface Created { id: string; failedParties: string[] }

interface Props {
  templates: ContractTemplateRow[]
  onClose: () => void
  onCreated?: (id: string) => void
}

export function ContractWizard({ templates, onClose, onCreated }: Props) {
  const qc = useQueryClient()
  const [step, setStep] = useState<WizardStep>('basics')
  const [d, setD] = useState<WizardDraft>(emptyDraft())
  const [shown, setShown] = useState<Errors>({})
  const [created, setCreated] = useState<Created | null>(null)
  const [error, setError] = useState('')

  const set = <K extends keyof WizardDraft>(k: K, v: WizardDraft[K]) => setD(p => ({ ...p, [k]: v }))
  const template = templates.find(t => t.id === d.templateId) ?? null
  const varKeys = d.source === 'TEMPLATE' && template?.variables ? Object.keys(template.variables) : []
  const terms = termsFor(d.contractType)
  const idx = WIZARD_STEPS.findIndex(s => s.id === step)

  const pickTemplate = (id: string) => {
    const t = templates.find(x => x.id === id)
    setD(p => ({ ...p, templateId: id, variables: {}, contractType: t ? t.contractType : p.contractType }))
  }
  const setParty = (i: number, patch: Partial<WizardParty>) =>
    setD(p => ({ ...p, parties: p.parties.map((x, j) => (j === i ? { ...x, ...patch } : x)) }))

  const save = useMutation({
    mutationFn: async (draft: WizardDraft): Promise<Created> => {
      const res = await apiClient.post('/api/v1/contracts', buildContractPayload(draft))
      const id = unwrapOne<{ id: string }>(res).id
      const failedParties: string[] = []
      for (const p of draft.parties) {
        try { await apiClient.post(`/api/v1/contracts/${id}/parties`, buildPartyPayload(p)) }
        catch { failedParties.push(p.fullName.trim()) }
      }
      return { id, failedParties }
    },
    onSuccess: r => {
      qc.invalidateQueries({ queryKey: ['contracts'] })
      qc.invalidateQueries({ queryKey: ['contract-detail'] })
      setCreated(r); setStep('finish'); setError('')
      onCreated?.(r.id)
    },
    onError: (e: unknown) => setError(apiError(e, 'Failed to create the contract')),
  })

  const next = () => {
    const errs = validateStep(step, d)
    setShown(errs)
    if (Object.keys(errs).length) return
    setStep(WIZARD_STEPS[idx + 1].id)
  }
  const saveDraft = () => {
    const bad = firstInvalidStep(d)
    if (bad) { setStep(bad); setShown(validateStep(bad, d)); return }
    setError(''); save.mutate(d)
  }

  const notes = readinessNotes(d, varKeys)

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div role="dialog" aria-label="New contract" style={{ background: 'var(--hf-surface)', borderRadius: 16, width: 'min(960px, 96vw)', maxHeight: '92vh', display: 'flex', flexDirection: 'column', boxShadow: '0 20px 60px rgba(0,0,0,0.18)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '20px 28px 12px' }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>New contract</h3>
          <button aria-label="Close" onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}><X size={20} /></button>
        </div>

        <ol aria-label="Steps" style={{ display: 'flex', gap: 8, listStyle: 'none', margin: 0, padding: '0 28px 14px', flexWrap: 'wrap' }}>
          {WIZARD_STEPS.map((s, i) => (
            <li key={s.id} aria-current={s.id === step ? 'step' : undefined}
              style={{ fontSize: 12, fontWeight: s.id === step ? 700 : 500, padding: '4px 10px', borderRadius: 999,
                background: s.id === step ? 'var(--hf-primary)' : i < idx || created ? 'var(--hf-success-soft)' : 'var(--hf-surface-muted)',
                color: s.id === step ? 'var(--hf-text-on-solid)' : 'var(--hf-text-secondary)' }}>
              {i + 1}. {s.label}
            </li>
          ))}
        </ol>

        <div style={{ display: 'flex', gap: 20, padding: '0 28px 8px', overflowY: 'auto', flex: 1, flexWrap: 'wrap' }}>
          <div style={{ flex: '1 1 520px', minWidth: 0 }}>
            {step === 'basics' && (
              <div style={grid}>
                <div style={{ gridColumn: '1/-1' }}>
                  <div style={lbl}>How do you want to start?</div>
                  <div role="radiogroup" aria-label="Start from" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {([['TEMPLATE', 'From template'], ['SCRATCH', 'From scratch']] as const).map(([v, l]) => (
                      <button key={v} role="radio" aria-checked={d.source === v}
                        onClick={() => setD(p => ({ ...p, source: v, templateId: v === 'SCRATCH' ? '' : p.templateId, variables: {} }))}
                        style={{ ...btn(d.source === v) }}>{l}</button>
                    ))}
                    <button disabled title="Not available yet" style={{ ...btn(), opacity: 0.5, cursor: 'not-allowed' }}>Upload document</button>
                    <button disabled title="Not available yet" style={{ ...btn(), opacity: 0.5, cursor: 'not-allowed' }}>Copy existing</button>
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 4 }}>Uploading a document and copying an existing contract are not available yet.</div>
                </div>
                {d.source === 'TEMPLATE' && (
                  <Field label="Template *" error={shown.templateId} wide>
                    <select aria-label="Template" value={d.templateId} onChange={e => pickTemplate(e.target.value)} style={inp}>
                      <option value="">Choose a template…</option>
                      {templates.map(t => {
                        const why = templateUnusableReason(t)
                        return <option key={t.id} value={t.id} disabled={!!why}>{t.name}{why ? ` (${why.toLowerCase()})` : ''}</option>
                      })}
                    </select>
                  </Field>
                )}
                {d.source === 'TEMPLATE' && template && (
                  <div aria-label="Template legal review" style={{ gridColumn: '1/-1', fontSize: 12, color: template.legalStatus === 'APPROVED' || template.legalStatus === 'CONDITIONAL' ? 'var(--hf-success-text-strong)' : 'var(--hf-warning-text)' }}>
                    {template.legalStatus === 'APPROVED' ? `Legal review recorded${template.legalArea ? ` (${template.legalArea})` : ''}.`
                      : template.legalStatus === 'CONDITIONAL' ? 'Legal review recorded with conditions.'
                      : 'No legal review has been recorded for this template.'}
                    {' '}Evidence is under Templates, Legal reviews.
                  </div>
                )}
                <Field label="Title *" error={shown.title} wide>
                  <input aria-label="Title" autoFocus value={d.title} onChange={e => set('title', e.target.value)} placeholder="Service Agreement — Acme Corp" style={inp} />
                </Field>
                <Field label="Contract type *">
                  <select aria-label="Contract type" value={d.contractType} onChange={e => set('contractType', e.target.value)} style={inp}>
                    {WIZARD_CONTRACT_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                  </select>
                </Field>
              </div>
            )}

            {step === 'parties' && (
              <div>
                <p style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginTop: 0 }}>Add the people or companies who will sign. You can also add them later from the contract.</p>
                {d.parties.map((p, i) => (
                  <fieldset key={i} style={{ border: '1px solid var(--hf-border)', borderRadius: 10, padding: 14, marginBottom: 12 }}>
                    <legend style={{ fontSize: 12, fontWeight: 600 }}>Party {i + 1}</legend>
                    <div style={grid}>
                      <Field label="Type"><select aria-label={`Party ${i + 1} type`} value={p.partyType} onChange={e => setParty(i, { partyType: e.target.value })} style={inp}>
                        {['INDIVIDUAL', 'COMPANY', 'TRUST', 'GOVERNMENT'].map(v => <option key={v} value={v}>{v.charAt(0) + v.slice(1).toLowerCase()}</option>)}
                      </select></Field>
                      <Field label="Role"><select aria-label={`Party ${i + 1} role`} value={p.partyRole} onChange={e => setParty(i, { partyRole: e.target.value })} style={inp}>
                        {['COUNTERPARTY', 'CLIENT', 'SERVICE_PROVIDER', 'GUARANTOR', 'BENEFICIARY', 'WITNESS'].map(v => <option key={v} value={v}>{v.replace('_', ' ').toLowerCase()}</option>)}
                      </select></Field>
                      <Field label="Full name *" error={shown[`party${i}.fullName`]} wide><input aria-label={`Party ${i + 1} name`} value={p.fullName} onChange={e => setParty(i, { fullName: e.target.value })} style={inp} /></Field>
                      {p.partyType === 'COMPANY' && <Field label="Company name" wide><input aria-label={`Party ${i + 1} company`} value={p.companyName} onChange={e => setParty(i, { companyName: e.target.value })} style={inp} /></Field>}
                      <Field label="Email" error={shown[`party${i}.email`]}><input aria-label={`Party ${i + 1} email`} type="email" value={p.email} onChange={e => setParty(i, { email: e.target.value })} style={inp} /></Field>
                      <Field label="Phone (needed for OTP signing)"><input aria-label={`Party ${i + 1} phone`} value={p.phone} onChange={e => setParty(i, { phone: e.target.value })} style={inp} /></Field>
                      <Field label="SA ID / passport"><input aria-label={`Party ${i + 1} id`} value={p.idNumber} onChange={e => setParty(i, { idNumber: e.target.value })} style={inp} /></Field>
                      <Field label="Signing order" error={shown[`party${i}.signingOrder`]}><input aria-label={`Party ${i + 1} order`} type="number" min={1} value={p.signingOrder} onChange={e => setParty(i, { signingOrder: e.target.value })} style={inp} /></Field>
                    </div>
                    <button aria-label={`Remove party ${i + 1}`} onClick={() => set('parties', d.parties.filter((_, j) => j !== i))} style={{ ...btn(), marginTop: 10 }}><Trash2 size={13} /> Remove</button>
                  </fieldset>
                ))}
                <button onClick={() => set('parties', [...d.parties, emptyParty(d.parties.length + 1)])} style={btn()}><Plus size={13} /> Add party</button>
              </div>
            )}

            {step === 'terms' && (
              <div style={grid}>
                {terms.value && <Field label="Value (R)" error={shown.valueAmount}><input aria-label="Value" type="number" value={d.valueAmount} onChange={e => set('valueAmount', e.target.value)} placeholder="0.00" style={inp} /></Field>}
                <Field label="Start date"><input aria-label="Start date" type="date" value={d.startDate} onChange={e => set('startDate', e.target.value)} style={inp} /></Field>
                <Field label="End date" error={shown.endDate}><input aria-label="End date" type="date" value={d.endDate} min={d.startDate} onChange={e => set('endDate', e.target.value)} style={inp} /></Field>
                {terms.renewal && (
                  <div style={{ gridColumn: '1/-1', display: 'flex', gap: 14, alignItems: 'center', flexWrap: 'wrap' }}>
                    <label style={{ display: 'flex', gap: 7, fontSize: 13, alignItems: 'center' }}>
                      <input type="checkbox" checked={d.autoRenew} onChange={e => set('autoRenew', e.target.checked)} /> Auto-renew on expiry
                    </label>
                    {d.autoRenew && (
                      <span style={{ display: 'flex', gap: 7, alignItems: 'center', fontSize: 12 }}>
                        Renewal notice
                        <input aria-label="Renewal notice days" type="number" value={d.renewalNoticeDays} onChange={e => set('renewalNoticeDays', e.target.value)} style={{ ...inp, width: 70 }} /> days
                        {shown.renewalNoticeDays && <span role="alert" style={{ color: 'var(--hf-danger-text)' }}>{shown.renewalNoticeDays}</span>}
                      </span>
                    )}
                  </div>
                )}
                {varKeys.length > 0 && (
                  <div style={{ gridColumn: '1/-1' }}>
                    <div style={{ ...lbl, marginTop: 6 }}>Template placeholders</div>
                    <div style={grid}>
                      {varKeys.map(k => {
                        const type = (template?.variables ?? {})[k]
                        return (
                          <Field key={k} label={`${k.replace(/_/g, ' ')} (${type})`}>
                            <input aria-label={k.replace(/_/g, ' ')} type={type === 'date' ? 'date' : type === 'number' ? 'number' : 'text'}
                              value={d.variables[k] ?? ''} onChange={e => set('variables', { ...d.variables, [k]: e.target.value })} style={inp} />
                          </Field>
                        )
                      })}
                    </div>
                  </div>
                )}
                <Field label="Notes" wide><textarea aria-label="Notes" rows={2} value={d.notes} onChange={e => set('notes', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
              </div>
            )}

            {step === 'review' && (
              <div>
                <p style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginTop: 0 }}>Check the details. This saves a draft; nothing is sent for signature.</p>
                <div style={{ fontSize: 13, lineHeight: 1.8 }}>
                  <strong>{d.title || '(untitled)'}</strong> · {typeLabel(d.contractType)}<br />
                  {template && <>Template: {template.name}<br /></>}
                  Parties: {d.parties.length ? d.parties.map(p => p.fullName).join(', ') : 'none yet'}<br />
                  {terms.value && <>Value: {d.valueAmount ? fmtMoney(Number(d.valueAmount), 'ZAR') : '—'}<br /></>}
                  Dates: {d.startDate || '—'} to {d.endDate || '—'}
                </div>
                {notes.length > 0 && (
                  <div style={{ marginTop: 12, padding: '10px 12px', borderRadius: 8, background: 'var(--hf-warning-soft)', border: '1px solid var(--hf-warning-border)', fontSize: 12, color: 'var(--hf-warning-text-deep)' }}>
                    <strong>Before this can be sent for signature:</strong>
                    <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>{notes.map(n => <li key={n}>{n}</li>)}</ul>
                  </div>
                )}
              </div>
            )}

            {step === 'finish' && created && (
              <div role="status">
                <h4 style={{ margin: '0 0 8px', display: 'flex', alignItems: 'center', gap: 6 }}><Check size={16} /> Draft saved</h4>
                <p style={{ fontSize: 13, margin: 0 }}>“{d.title}” is in your register as a draft. Review it, then submit it for review when it is ready.</p>
                {created.failedParties.length > 0 && (
                  <div role="alert" style={{ marginTop: 12, padding: '9px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>
                    These parties could not be added: {created.failedParties.join(', ')}. Add them from the contract.
                  </div>
                )}
              </div>
            )}
            {error && <div role="alert" style={{ marginTop: 12, padding: '9px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
          </div>

          <aside aria-label="Contract summary" style={{ flex: '0 1 240px', background: 'var(--hf-surface-muted)', borderRadius: 12, padding: 14, fontSize: 12, alignSelf: 'flex-start' }}>
            <div style={{ fontWeight: 700, marginBottom: 6 }}>Contract summary</div>
            <div>{d.title || 'Untitled'}</div>
            <div>{typeLabel(d.contractType)}</div>
            <div>{d.source === 'TEMPLATE' ? (template ? `Template: ${template.name}` : 'Template: not chosen') : 'From scratch'}</div>
            <div>{d.parties.length} part{d.parties.length === 1 ? 'y' : 'ies'}</div>
            {terms.value && d.valueAmount && <div>{fmtMoney(Number(d.valueAmount), 'ZAR')}</div>}
            {varKeys.length > 0 && <div>{unfilledVariables(varKeys, d.variables).length} of {varKeys.length} placeholders blank</div>}
          </aside>
        </div>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'space-between', padding: '14px 28px 22px', flexWrap: 'wrap' }}>
          {step === 'finish' ? (
            <div style={{ marginLeft: 'auto' }}><button onClick={onClose} style={btn(true)}>Done</button></div>
          ) : (
            <>
              <div style={{ display: 'flex', gap: 8 }}>
                <button onClick={onClose} style={btn()}>Cancel</button>
                {idx > 0 && <button onClick={() => { setShown({}); setStep(WIZARD_STEPS[idx - 1].id) }} style={btn()}>Back</button>}
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button onClick={saveDraft} disabled={save.isPending} style={btn()}>Save draft</button>
                {step !== 'review'
                  ? <button onClick={next} style={btn(true)}>Next</button>
                  : <button onClick={saveDraft} disabled={save.isPending} style={btn(true)}>{save.isPending ? 'Saving…' : 'Create draft'}</button>}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
