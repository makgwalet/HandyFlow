// Pure rules for the template health checklist and for which templates a new contract may start from.

export type HealthState = 'ok' | 'warn' | 'fail'
export interface HealthItem { id: string; label: string; state: HealthState; detail?: string; blocksPublish: boolean }

export interface HealthInput {
  bodyTemplate?: string | null
  variables?: Record<string, string> | null
  legalStatus?: string | null
}
export interface PublishApproval { required: boolean; state: 'NONE' | 'PENDING' | 'APPROVED' }

/** Distinct {{placeholder}} names used in a template body, in order of first use. */
export function placeholdersIn(body: string | null | undefined): string[] {
  const seen = new Set<string>()
  for (const m of (body ?? '').matchAll(/\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}/g)) seen.add(m[1])
  return Array.from(seen)
}

const LEGAL_TEXT: Record<string, string> = {
  NOT_REVIEWED: 'No legal review has been recorded for this version.',
  REJECTED: 'The legal review rejected this version.',
  EXPIRED: 'The legal review has expired.',
  WITHDRAWN: 'The legal review was withdrawn.',
}

export function templateHealth(t: HealthInput, approval: PublishApproval): { items: HealthItem[]; publishBlocker: string | null } {
  const items: HealthItem[] = []
  const hasBody = !!(t.bodyTemplate ?? '').replace(/<[^>]*>/g, '').trim()
  items.push({ id: 'body', label: 'Document text', state: hasBody ? 'ok' : 'fail', detail: hasBody ? undefined : 'The template has no text.', blocksPublish: true })

  const declared = Object.keys(t.variables ?? {})
  const used = placeholdersIn(t.bodyTemplate)
  const undeclared = used.filter(u => !declared.includes(u))
  const unused = declared.filter(d => !used.includes(d))
  items.push({
    id: 'placeholders', label: 'Placeholders', blocksPublish: false,
    state: undeclared.length || unused.length ? 'warn' : 'ok',
    detail: [
      undeclared.length ? `Used but not declared: ${undeclared.join(', ')}.` : '',
      unused.length ? `Declared but never used: ${unused.join(', ')}.` : '',
    ].filter(Boolean).join(' ') || undefined,
  })

  const legal = t.legalStatus ?? 'NOT_REVIEWED'
  items.push({
    id: 'legal', label: 'Legal review evidence', blocksPublish: legal !== 'APPROVED' && legal !== 'CONDITIONAL',
    state: legal === 'APPROVED' ? 'ok' : legal === 'CONDITIONAL' ? 'warn' : 'fail',
    detail: legal === 'APPROVED' ? undefined : legal === 'CONDITIONAL' ? 'Reviewed with conditions. Check the outstanding issues before publishing.' : LEGAL_TEXT[legal] ?? legal,
  })

  if (approval.required) {
    items.push({
      id: 'approval', label: 'Approval to publish', blocksPublish: approval.state !== 'APPROVED',
      state: approval.state === 'APPROVED' ? 'ok' : approval.state === 'PENDING' ? 'warn' : 'fail',
      detail: approval.state === 'APPROVED' ? undefined : approval.state === 'PENDING' ? 'Waiting for someone else to approve.' : 'Your policy needs a second person to approve publishing.',
    })
  }
  const blocker = items.find(i => i.blocksPublish && i.state !== 'ok')
  return { items, publishBlocker: blocker ? `${blocker.label}: ${blocker.detail}` : null }
}

/** Why a published template cannot start a new contract, or null when it can. Mirrors the server's check. */
export function templateUnusableReason(t: { status?: string | null; legalStatus?: string | null }): string | null {
  if (t.status && t.status !== 'PUBLISHED') return 'Not published'
  const l = t.legalStatus
  if (l === 'EXPIRED' || l === 'WITHDRAWN' || l === 'REJECTED') return `Legal review ${l.toLowerCase()}`
  return null
}
