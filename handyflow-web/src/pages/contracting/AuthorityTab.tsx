// src/pages/contracting/AuthorityTab.tsx
//
// Delegated authority. Backend: /api/v1/contracts/authority.
//  • Who may approve, negotiate, sign, renew or terminate, for which contract types, up to what value, and until when.
//  • A holder can delegate part of their own authority for a stated period; the server refuses anything greater than they hold.
//  • Enforcement is off until an administrator turns it on (it cannot be turned on while nobody holds authority).
// The server decides everything; this screen mirrors its checks so a form can say what is wrong first.

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermission } from '../../hooks/usePermission'
import { WIZARD_CONTRACT_TYPES } from './wizardRules'
import {
  AUTHORITY_ACTIONS, AUTHORITY_ROLES, ACTION_WORD, STATE_LABEL, grantState, limitText, typesText, grantProblem, delegationProblem, enforcementBlocker,
  type Grant, type GrantForm, type DelegationForm, type AuthorityAction,
} from './authorityShared'

interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
interface EventRow { id: string; grantId: string | null; eventType: string; actorUserId: string | null; detail: string | null; createdAt: string }

const typeLabel = (t: string) => WIZARD_CONTRACT_TYPES.find(x => x[0] === t)?.[1] ?? t
const userName = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(' ') || u.email || u.id
const today = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Johannesburg' })
const fmtDate = (d: string | null) => (d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : 'No end date')
const fmtDT = (d: string) => new Date(d).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' })
const apiError = (e: unknown, fallback: string) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

const card: React.CSSProperties = { background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: 16, marginBottom: 14 }
const field: React.CSSProperties = { width: '100%', padding: '8px 10px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, boxSizing: 'border-box', background: 'var(--hf-surface)' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 12, fontWeight: 600, marginBottom: 4, color: 'var(--hf-text-secondary)' }
const btn = (primary = false, danger = false): React.CSSProperties => ({
  padding: '7px 14px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer',
  border: primary || danger ? 'none' : '1.5px solid var(--hf-border)',
  background: danger ? 'var(--hf-danger-text)' : primary ? 'var(--hf-primary-text)' : 'var(--hf-surface)',
  color: primary || danger ? 'var(--hf-text-on-solid)' : 'var(--hf-text)',
})
const STATE_COLOR: Record<string, { color: string; bg: string }> = {
  IN_FORCE: { color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)' },
  SCHEDULED: { color: 'var(--hf-info-text)', bg: 'var(--hf-info-soft)' },
  EXPIRED: { color: 'var(--hf-text-muted)', bg: 'var(--hf-surface-muted)' },
  REVOKED: { color: 'var(--hf-danger-text)', bg: 'var(--hf-danger-soft)' },
  SOURCE_ENDED: { color: 'var(--hf-warning-text)', bg: 'var(--hf-warning-soft)' },
}

function ActionChecks({ value, onChange, allowed }: { value: AuthorityAction[]; onChange: (v: AuthorityAction[]) => void; allowed?: AuthorityAction[] }) {
  return (
    <div role="group" aria-label="Actions" style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
      {AUTHORITY_ACTIONS.filter(a => !allowed || allowed.includes(a.id)).map(a => (
        <label key={a.id} title={a.hint} style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 13 }}>
          <input type="checkbox" checked={value.includes(a.id)} onChange={e => onChange(e.target.checked ? [...value, a.id] : value.filter(x => x !== a.id))} />
          {a.label}
        </label>
      ))}
    </div>
  )
}

function TypeChecks({ value, onChange, allowed }: { value: string[]; onChange: (v: string[]) => void; allowed?: string[] }) {
  const list = WIZARD_CONTRACT_TYPES.filter(t => !allowed || allowed.length === 0 || allowed.includes(t[0]))
  return (
    <div role="group" aria-label="Contract types" style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
      {list.map(([id, label]) => (
        <label key={id} style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 13 }}>
          <input type="checkbox" checked={value.includes(id)} onChange={e => onChange(e.target.checked ? [...value, id] : value.filter(x => x !== id))} />
          {label}
        </label>
      ))}
    </div>
  )
}

export default function AuthorityTab() {
  const qc = useQueryClient()
  const isAdmin = usePermission('CONTRACTS_ADMIN')
  const canDelegate = usePermission('CONTRACTS_MANAGE') || isAdmin
  const [error, setError] = useState('')
  const [adding, setAdding] = useState(false)
  const [delegating, setDelegating] = useState<Grant | null>(null)
  const [revoking, setRevoking] = useState<Grant | null>(null)
  const [revokeReason, setRevokeReason] = useState('')
  const [showAudit, setShowAudit] = useState(false)
  const [confirmOn, setConfirmOn] = useState(false)
  const [gf, setGf] = useState<GrantForm>({ kind: 'USER', userId: '', role: '', actions: [], types: [], unlimited: false, maxAmount: '', from: today(), to: '' })
  const [df, setDf] = useState<DelegationForm>({ toUserId: '', actions: [], types: [], unlimited: false, maxAmount: '', from: today(), to: '' })

  const { data: grants = [] } = useQuery<Grant[]>({
    queryKey: ['contract-authority', 'grants'],
    queryFn: async () => (await apiClient.get('/api/v1/contracts/authority/grants')).data?.data ?? [],
  })
  const { data: settings } = useQuery<{ enforced: boolean }>({
    queryKey: ['contract-authority', 'settings'],
    queryFn: async () => (await apiClient.get('/api/v1/contracts/authority/settings')).data?.data ?? { enforced: false },
  })
  const { data: users = [] } = useQuery<UserRow[]>({
    queryKey: ['contract-authority', 'users'], retry: false,
    queryFn: async () => { const d = (await apiClient.get<UserRow[]>('/api/v1/identity/users')).data; return Array.isArray(d) ? d : [] },
  })
  const { data: events = [] } = useQuery<EventRow[]>({
    queryKey: ['contract-authority', 'events'], enabled: isAdmin && showAudit,
    queryFn: async () => (await apiClient.get('/api/v1/contracts/authority/events')).data?.data ?? [],
  })

  const nameOf = (id: string | null) => (id ? userName(users.find(u => u.id === id) ?? { id }) : '')
  const who = (g: Grant) => g.userId ? nameOf(g.userId) : AUTHORITY_ROLES.find(r => r.id === g.role)?.label ?? g.role ?? ''
  const refresh = () => qc.invalidateQueries({ queryKey: ['contract-authority'] })
  const fail = (e: unknown) => setError(apiError(e, 'That did not work'))

  const setEnforced = useMutation({
    mutationFn: (enforced: boolean) => apiClient.put('/api/v1/contracts/authority/settings', { enforced }),
    onSuccess: () => { refresh(); setConfirmOn(false); setError('') }, onError: fail,
  })
  const grant = useMutation({
    mutationFn: () => apiClient.post('/api/v1/contracts/authority/grants', {
      userId: gf.kind === 'USER' ? gf.userId : null, role: gf.kind === 'ROLE' ? gf.role : null, contractTypes: gf.types, actions: gf.actions,
      unlimited: gf.unlimited, maxAmount: gf.unlimited ? null : Number(gf.maxAmount), effectiveFrom: gf.from || null, expiresOn: gf.to || null,
    }),
    onSuccess: () => { refresh(); setAdding(false); setError('') }, onError: fail,
  })
  const delegate = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/authority/grants/${delegating?.id}/delegate`, {
      toUserId: df.toUserId, actions: df.actions, contractTypes: df.types, unlimited: df.unlimited,
      maxAmount: df.unlimited ? null : Number(df.maxAmount), effectiveFrom: df.from, expiresOn: df.to,
    }),
    onSuccess: () => { refresh(); setDelegating(null); setError('') }, onError: fail,
  })
  const revoke = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/authority/grants/${revoking?.id}/revoke`, { reason: revokeReason }),
    onSuccess: () => { refresh(); setRevoking(null); setRevokeReason(''); setError('') }, onError: fail,
  })

  const t = today()
  const mine = grants.filter(g => g.mine && g.inForce)
  const enforced = settings?.enforced ?? false
  const blocker = enforcementBlocker(grants)
  const gProblem = grantProblem(gf)
  const dProblem = delegating ? delegationProblem(delegating, df, t) : null

  return (
    <div>
      {error && <div role="alert" style={{ ...card, background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', color: 'var(--hf-danger-text)', fontSize: 13 }}>{error}</div>}

      <section aria-label="Enforcement" style={card}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ flex: '1 1 320px' }}>
            <div style={{ fontWeight: 700, fontSize: 15 }}>Delegated authority is {enforced ? 'enforced' : 'not enforced'}</div>
            <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--hf-text-muted)', lineHeight: 1.6 }}>
              {enforced
                ? 'Approving, signing for someone, accepting a counterparty change, renewing and terminating each need authority that covers the contract. Nobody can exceed what they hold.'
                : 'Today anyone with the contract permission can act, as before. Set up authority below, then turn enforcement on.'}
            </p>
          </div>
          {isAdmin && !confirmOn && (
            <button style={btn(!enforced)} disabled={!enforced && !!blocker} title={!enforced && blocker ? blocker : undefined}
              onClick={() => (enforced ? setEnforced.mutate(false) : setConfirmOn(true))}>
              {enforced ? 'Turn enforcement off' : 'Turn enforcement on'}
            </button>
          )}
        </div>
        {isAdmin && !enforced && blocker && <p role="note" style={{ margin: '8px 0 0', fontSize: 12, color: 'var(--hf-warning-text)' }}>{blocker}</p>}
        {confirmOn && (
          <div role="alertdialog" aria-label="Confirm enforcement" style={{ marginTop: 12, padding: 12, border: '1px solid var(--hf-warning-border)', background: 'var(--hf-warning-soft)', borderRadius: 8, fontSize: 13 }}>
            From now on people without matching authority will be refused. Everyone who needs to approve, sign, renew or terminate must hold authority first.
            <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
              <button style={btn(true)} onClick={() => setEnforced.mutate(true)}>Turn enforcement on</button>
              <button style={btn()} onClick={() => setConfirmOn(false)}>Not yet</button>
            </div>
          </div>
        )}
      </section>

      <section aria-label="My authority" style={card}>
        <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 8 }}>What you can do</div>
        {mine.length === 0 ? (
          <p style={{ margin: 0, fontSize: 13, color: 'var(--hf-text-muted)' }}>You hold no delegated authority today.</p>
        ) : mine.map(g => (
          <div key={g.id} style={{ fontSize: 13, padding: '4px 0' }}>
            <strong>{g.actions.map(a => ACTION_WORD[a]).join(', ')}</strong> · {typesText(g.contractTypes, typeLabel)} · {limitText(g)} · until {fmtDate(g.expiresOn)}
            {g.delegatedFromId && <span style={{ color: 'var(--hf-text-muted)' }}> · delegated to you{g.delegatedBy ? ` by ${nameOf(g.delegatedBy)}` : ''}</span>}
          </div>
        ))}
      </section>

      <section aria-label="Grants" style={card}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
          <div style={{ fontWeight: 700, fontSize: 14 }}>Authority in the organisation</div>
          {isAdmin && <button style={btn(true)} onClick={() => { setAdding(true); setError('') }}>Give authority</button>}
        </div>
        {grants.length === 0 && <p style={{ margin: 0, fontSize: 13, color: 'var(--hf-text-muted)' }}>No authority has been set up yet.</p>}
        {grants.map(g => {
          const st = grantState(g, t)
          const sc = STATE_COLOR[st]
          return (
            <div key={g.id} style={{ borderTop: '1px solid var(--hf-border)', padding: '10px 0', display: 'flex', gap: 12, justifyContent: 'space-between', flexWrap: 'wrap' }}>
              <div style={{ flex: '1 1 320px', fontSize: 13 }}>
                <div style={{ fontWeight: 600 }}>
                  {who(g)} {g.delegatedFromId && <span style={{ fontWeight: 400, color: 'var(--hf-text-muted)' }}>(temporary delegation{g.delegatedBy ? ` from ${nameOf(g.delegatedBy)}` : ''})</span>}
                </div>
                <div style={{ color: 'var(--hf-text-secondary)' }}>
                  {g.actions.map(a => ACTION_WORD[a]).join(', ')} · {typesText(g.contractTypes, typeLabel)} · {limitText(g)}
                </div>
                <div style={{ color: 'var(--hf-text-muted)', fontSize: 12 }}>
                  {fmtDate(g.effectiveFrom)} to {fmtDate(g.expiresOn)}{g.revokedReason ? ` · Revoked: ${g.revokedReason}` : ''}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={{ padding: '3px 10px', borderRadius: 20, fontSize: 12, fontWeight: 600, color: sc.color, background: sc.bg }}>{STATE_LABEL[st]}</span>
                {canDelegate && g.mine && g.inForce && !g.delegatedFromId && (
                  <button style={btn()} onClick={() => { setDelegating(g); setDf({ toUserId: '', actions: [], types: [], unlimited: false, maxAmount: '', from: t, to: '' }); setError('') }}>Delegate</button>
                )}
                {g.status === 'ACTIVE' && isAdmin && (
                  <button style={btn(false, true)} onClick={() => { setRevoking(g); setRevokeReason(''); setError('') }}>Revoke</button>
                )}
              </div>
            </div>
          )
        })}
      </section>

      {adding && (
        <section aria-label="Give authority" style={card}>
          <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 10 }}>Give authority</div>
          <div style={{ display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
            <div>
              <label style={lbl} htmlFor="ga-kind">To</label>
              <select id="ga-kind" style={field} value={gf.kind} onChange={e => setGf({ ...gf, kind: e.target.value as 'USER' | 'ROLE' })}>
                <option value="USER">One person</option><option value="ROLE">Everyone with a role</option>
              </select>
            </div>
            {gf.kind === 'USER' ? (
              <div>
                <label style={lbl} htmlFor="ga-user">Person</label>
                <select id="ga-user" style={field} value={gf.userId} onChange={e => setGf({ ...gf, userId: e.target.value })}>
                  <option value="">Choose…</option>{users.map(u => <option key={u.id} value={u.id}>{userName(u)}</option>)}
                </select>
              </div>
            ) : (
              <div>
                <label style={lbl} htmlFor="ga-role">Role</label>
                <select id="ga-role" style={field} value={gf.role} onChange={e => setGf({ ...gf, role: e.target.value })}>
                  <option value="">Choose…</option>{AUTHORITY_ROLES.map(r => <option key={r.id} value={r.id}>{r.label}</option>)}
                </select>
              </div>
            )}
          </div>
          <div style={{ marginTop: 12 }}><label style={lbl}>May</label><ActionChecks value={gf.actions} onChange={actions => setGf({ ...gf, actions })} /></div>
          <div style={{ marginTop: 12 }}>
            <label style={lbl}>Contract types (none ticked means all types)</label>
            <TypeChecks value={gf.types} onChange={types => setGf({ ...gf, types })} />
          </div>
          <div style={{ marginTop: 12, display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
            <div>
              <label style={lbl} htmlFor="ga-max">Most per contract (rand)</label>
              <input id="ga-max" style={field} inputMode="decimal" disabled={gf.unlimited} value={gf.maxAmount} onChange={e => setGf({ ...gf, maxAmount: e.target.value })} />
              <label style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12, marginTop: 4 }}>
                <input type="checkbox" checked={gf.unlimited} onChange={e => setGf({ ...gf, unlimited: e.target.checked })} /> No financial limit
              </label>
            </div>
            <div><label style={lbl} htmlFor="ga-from">From</label><input id="ga-from" type="date" style={field} value={gf.from} onChange={e => setGf({ ...gf, from: e.target.value })} /></div>
            <div><label style={lbl} htmlFor="ga-to">Until (optional)</label><input id="ga-to" type="date" style={field} value={gf.to} onChange={e => setGf({ ...gf, to: e.target.value })} /></div>
          </div>
          {gProblem && <p role="note" style={{ fontSize: 12, color: 'var(--hf-text-muted)', margin: '10px 0 0' }}>{gProblem}</p>}
          <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
            <button style={btn(true)} disabled={!!gProblem || grant.isPending} onClick={() => grant.mutate()}>{grant.isPending ? 'Saving…' : 'Give authority'}</button>
            <button style={btn()} onClick={() => setAdding(false)}>Cancel</button>
          </div>
        </section>
      )}

      {delegating && (
        <section aria-label="Delegate authority" style={card}>
          <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 4 }}>Delegate some of your authority</div>
          <p style={{ margin: '0 0 10px', fontSize: 12, color: 'var(--hf-text-muted)' }}>
            You hold: {delegating.actions.map(a => ACTION_WORD[a]).join(', ')} · {typesText(delegating.contractTypes, typeLabel)} · {limitText(delegating)}.
            A delegation cannot be more than this, cannot outlast it and must have an end date.
          </p>
          <div><label style={lbl} htmlFor="de-user">Delegate to</label>
            <select id="de-user" style={field} value={df.toUserId} onChange={e => setDf({ ...df, toUserId: e.target.value })}>
              <option value="">Choose…</option>{users.map(u => <option key={u.id} value={u.id}>{userName(u)}</option>)}
            </select>
          </div>
          <div style={{ marginTop: 12 }}><label style={lbl}>May</label><ActionChecks value={df.actions} allowed={delegating.actions} onChange={actions => setDf({ ...df, actions })} /></div>
          {delegating.contractTypes.length > 0 && (
            <div style={{ marginTop: 12 }}><label style={lbl}>Contract types</label><TypeChecks value={df.types} allowed={delegating.contractTypes} onChange={types => setDf({ ...df, types })} /></div>
          )}
          <div style={{ marginTop: 12, display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
            <div>
              <label style={lbl} htmlFor="de-max">Most per contract (rand)</label>
              <input id="de-max" style={field} inputMode="decimal" disabled={df.unlimited} value={df.maxAmount} onChange={e => setDf({ ...df, maxAmount: e.target.value })} />
              {delegating.unlimited && (
                <label style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12, marginTop: 4 }}>
                  <input type="checkbox" checked={df.unlimited} onChange={e => setDf({ ...df, unlimited: e.target.checked })} /> No financial limit
                </label>
              )}
            </div>
            <div><label style={lbl} htmlFor="de-from">From</label><input id="de-from" type="date" style={field} value={df.from} onChange={e => setDf({ ...df, from: e.target.value })} /></div>
            <div><label style={lbl} htmlFor="de-to">Until</label><input id="de-to" type="date" style={field} value={df.to} onChange={e => setDf({ ...df, to: e.target.value })} /></div>
          </div>
          {dProblem && <p role="note" style={{ fontSize: 12, color: 'var(--hf-text-muted)', margin: '10px 0 0' }}>{dProblem}</p>}
          <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
            <button style={btn(true)} disabled={!!dProblem || delegate.isPending} onClick={() => delegate.mutate()}>{delegate.isPending ? 'Saving…' : 'Delegate'}</button>
            <button style={btn()} onClick={() => setDelegating(null)}>Cancel</button>
          </div>
        </section>
      )}

      {revoking && (
        <section aria-label="Revoke authority" style={card}>
          <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 8 }}>Revoke authority for {who(revoking)}</div>
          <label style={lbl} htmlFor="rv-reason">Why? (kept in the audit trail)</label>
          <textarea id="rv-reason" rows={2} style={field} value={revokeReason} onChange={e => setRevokeReason(e.target.value)} />
          <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
            <button style={btn(false, true)} disabled={revokeReason.trim().length < 5 || revoke.isPending} onClick={() => revoke.mutate()}>Revoke</button>
            <button style={btn()} onClick={() => setRevoking(null)}>Cancel</button>
          </div>
        </section>
      )}

      {isAdmin && (
        <section aria-label="Audit trail" style={card}>
          <button style={btn()} onClick={() => setShowAudit(!showAudit)}>{showAudit ? 'Hide audit trail' : 'Show audit trail'}</button>
          {showAudit && (events.length === 0 ? <p style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>Nothing recorded yet.</p> : events.map(e => (
            <div key={e.id} style={{ borderTop: '1px solid var(--hf-border)', padding: '6px 0', fontSize: 12 }}>
              <strong>{e.eventType.replace(/_/g, ' ').toLowerCase()}</strong> · {fmtDT(e.createdAt)}{e.actorUserId ? ` · ${nameOf(e.actorUserId)}` : ''}
              {e.detail && <div style={{ color: 'var(--hf-text-muted)' }}>{e.detail}</div>}
            </div>
          )))}
        </section>
      )}
    </div>
  )
}
