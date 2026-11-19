// Pure helpers for the five-step contract creation wizard (Basics, Parties, Terms, Review, Finish).
// Kept free of React so the rules can be unit-tested directly.

export type WizardStep = 'basics' | 'parties' | 'terms' | 'review' | 'finish'
export const WIZARD_STEPS: { id: WizardStep; label: string }[] = [
  { id: 'basics', label: 'Basics' },
  { id: 'parties', label: 'Parties' },
  { id: 'terms', label: 'Terms' },
  { id: 'review', label: 'Review' },
  { id: 'finish', label: 'Finish' },
]

export interface WizardParty {
  partyType: string; partyRole: string; fullName: string; companyName: string
  email: string; phone: string; idNumber: string; signingOrder: string
}

export interface WizardDraft {
  source: 'TEMPLATE' | 'SCRATCH'
  templateId: string
  title: string
  contractType: string
  variables: Record<string, string>
  parties: WizardParty[]
  valueAmount: string
  startDate: string
  endDate: string
  autoRenew: boolean
  renewalNoticeDays: string
  notes: string
}

export const emptyParty = (order = 1): WizardParty => ({
  partyType: 'INDIVIDUAL', partyRole: 'COUNTERPARTY', fullName: '', companyName: '',
  email: '', phone: '', idNumber: '', signingOrder: String(order),
})

export const emptyDraft = (): WizardDraft => ({
  source: 'SCRATCH', templateId: '', title: '', contractType: 'SERVICE_AGREEMENT', variables: {},
  parties: [], valueAmount: '', startDate: '', endDate: '', autoRenew: false, renewalNoticeDays: '30', notes: '',
})

/** Which term fields make sense for a contract type. Hidden fields are never sent. */
export function termsFor(contractType: string): { value: boolean; renewal: boolean } {
  return {
    value: contractType !== 'NDA',
    renewal: !['NDA', 'EMPLOYMENT', 'ACKNOWLEDGMENT_OF_DEBT'].includes(contractType),
  }
}

export const unfilledVariables = (keys: string[], values: Record<string, string>): string[] =>
  keys.filter(k => !(values[k] ?? '').trim())

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export type Errors = Record<string, string>

export function validateStep(step: WizardStep, d: WizardDraft): Errors {
  const e: Errors = {}
  if (step === 'basics') {
    if (!d.title.trim()) e.title = 'Give the contract a title.'
    if (d.source === 'TEMPLATE' && !d.templateId) e.templateId = 'Choose a template, or start from scratch.'
  }
  if (step === 'parties') {
    d.parties.forEach((p, i) => {
      if (!p.fullName.trim()) e[`party${i}.fullName`] = 'Enter the name.'
      if (p.email.trim() && !EMAIL.test(p.email.trim())) e[`party${i}.email`] = 'That email address does not look right.'
      const order = Number(p.signingOrder)
      if (!Number.isInteger(order) || order < 1) e[`party${i}.signingOrder`] = 'Use a whole number from 1.'
    })
  }
  if (step === 'terms') {
    const t = termsFor(d.contractType)
    if (t.value && d.valueAmount.trim() && !(Number(d.valueAmount) >= 0)) e.valueAmount = 'Enter an amount of zero or more.'
    if (d.startDate && d.endDate && d.endDate < d.startDate) e.endDate = 'The end date is before the start date.'
    if (t.renewal && d.autoRenew) {
      const n = Number(d.renewalNoticeDays)
      if (!Number.isInteger(n) || n < 1) e.renewalNoticeDays = 'Use a whole number of days.'
    }
  }
  return e
}

/** The first step that still has errors, or null when everything is valid. */
export function firstInvalidStep(d: WizardDraft): WizardStep | null {
  for (const s of ['basics', 'parties', 'terms'] as WizardStep[]) {
    if (Object.keys(validateStep(s, d)).length) return s
  }
  return null
}

export function buildContractPayload(d: WizardDraft): Record<string, unknown> {
  const t = termsFor(d.contractType)
  const vars = d.source === 'TEMPLATE' ? d.variables : {}
  return {
    title: d.title.trim(),
    contractType: d.contractType,
    templateId: d.source === 'TEMPLATE' ? d.templateId || null : null,
    variables: Object.keys(vars).length ? vars : null,
    valueAmount: t.value && d.valueAmount.trim() ? Number(d.valueAmount) : null,
    currency: 'ZAR',
    startDate: d.startDate || null,
    endDate: d.endDate || null,
    autoRenew: t.renewal && d.autoRenew,
    renewalNoticeDays: t.renewal && d.autoRenew ? Number(d.renewalNoticeDays) || 30 : null,
    notes: d.notes.trim() || null,
  }
}

export function buildPartyPayload(p: WizardParty): Record<string, unknown> {
  return {
    partyType: p.partyType,
    partyRole: p.partyRole,
    fullName: p.fullName.trim(),
    companyName: p.partyType === 'COMPANY' ? p.companyName.trim() || null : null,
    email: p.email.trim() || null,
    phone: p.phone.trim() || null,
    idNumber: p.idNumber.trim() || null,
    signingOrder: Number(p.signingOrder) || 1,
  }
}

/** Things that will block sending later; shown as guidance, never as a block on saving a draft. */
export function readinessNotes(d: WizardDraft, variableKeys: string[]): string[] {
  const notes: string[] = []
  const signers = d.parties.filter(p => p.partyRole !== 'WITNESS')
  if (signers.length === 0) notes.push('No parties yet. Add at least one before sending for signature.')
  const noContact = d.parties.filter(p => !p.email.trim() && !p.phone.trim())
  if (noContact.length) notes.push(`${noContact.length} part${noContact.length === 1 ? 'y has' : 'ies have'} no email or phone, so a signing link cannot be sent.`)
  const open = unfilledVariables(variableKeys, d.variables)
  if (d.source === 'TEMPLATE' && open.length) notes.push(`${open.length} placeholder${open.length === 1 ? '' : 's'} still blank: ${open.map(k => k.replace(/_/g, ' ')).join(', ')}.`)
  if (!d.startDate || !d.endDate) notes.push('Start and end dates are not both set.')
  return notes
}

export const WIZARD_CONTRACT_TYPES = [
  ['SERVICE_AGREEMENT', 'Service Agreement'], ['NDA', 'Non-Disclosure Agreement'], ['EMPLOYMENT', 'Employment Contract'],
  ['JOINT_VENTURE', 'Joint Venture'], ['EQUIPMENT_HIRE', 'Equipment Hire'], ['LEASE', 'Lease Agreement'],
  ['SUPPLY', 'Supply Agreement'], ['SUBCONTRACTOR', 'Subcontractor'], ['SERVICE_LEVEL', 'Service Level Agreement'],
  ['CONSULTING', 'Consulting Agreement'], ['RETAINER', 'Retainer Agreement'],
  ['ACKNOWLEDGMENT_OF_DEBT', 'Acknowledgment of Debt'], ['OTHER', 'Other'],
] as const
