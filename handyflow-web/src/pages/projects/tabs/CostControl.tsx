// src/pages/projects/tabs/CostControl.tsx
// Cost control inside the Budget tab: the cost position (original vs revised budget, committed, actual, forecast at completion,
// pending change orders, contingency, margin), the cost ledger with commitments and actuals, forecasts per line, and the history of
// every budget change with transfers between lines.
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Undo2, ArrowRightLeft, Pencil } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, ReasonDialog, SectionTitle, Stat, card, day, inp, list, money, muted, one, signedMoney, td, th, today, when } from './pmKit'
import type { BudgetLine } from './ChangeOrdersTab'

interface LineResult { id: string; category: string; budgeted: number; original: number | null; committed: number; actual: number; remaining: number
  etc: number; etcManual: boolean; eac: number; vac: number; consumedPct: number; overBudget: boolean }
interface Summary {
  baselineCaptured: boolean; originalBudget: number; revisedBudget: number; additions: number; netAdjustments: number
  committed: number; actual: number; remaining: number; etc: number; eac: number; vac: number; consumedPct: number
  pendingChangeOrderCount: number; pendingChangeOrderValue: number; eacIncludingPending: number
  contingencyBudget: number; contingencyUsed: number; contingencyRemaining: number
  contractValue: number | null; forecastMargin: number | null; forecastMarginPct: number | null
  overBudgetLineCount: number; lines: LineResult[]
}
interface CostEntry { id: string; budgetLineId: string; type: 'COMMITMENT' | 'ACTUAL'; amount: number; sourceType: string; sourceRef: string; description: string | null
  entryDate: string; reversalOf: string | null; postedByName: string | null; createdAt: string; duplicate: boolean; lineOverBudget: boolean }
interface Revision { id: string; budgetLineId: string; revisionType: string; oldAmount: number; newAmount: number; delta: number; reason: string | null
  transferId: string | null; changeOrderId: string | null; revisedByName: string | null; revisedAt: string }

const lineName = (lines: BudgetLine[], id: string) => { const l = lines.find(x => x.id === id); return l ? `${l.category} — ${l.description}` : 'Removed line' }
const pct = (n: number | null | undefined) => n == null ? '—' : `${Number(n).toFixed(1)}%`

function useLines(projectId: string) {
  return useQuery<BudgetLine[]>({ queryKey: ['pm-budget', projectId], queryFn: async () => list<BudgetLine>(await apiClient.get(`/api/v1/projects/${projectId}/budget`)) })
}
function useInvalidateCost(projectId: string) {
  const qc = useQueryClient()
  return () => ['pm-budget', 'pm-cost-summary', 'pm-ledger', 'pm-budget-revisions', 'pm-evm', 'pm-project'].forEach(k => qc.invalidateQueries({ queryKey: [k, projectId] }))
}

// ── Cost position ────────────────────────────────────────────────────────────

export function CostPosition({ projectId }: { projectId: string }) {
  const canAdmin = usePermission('PM_ADMIN')
  const { data: lines = [] } = useLines(projectId)
  const [forecastFor, setForecastFor] = useState<LineResult | null>(null)
  const { data, isLoading, isError, error } = useQuery<Summary>({
    queryKey: ['pm-cost-summary', projectId],
    queryFn: async () => one<{ summary: Summary }>(await apiClient.get(`/api/v1/projects/${projectId}/budget/cost-summary`)).summary,
  })
  if (isLoading) return <Empty>Loading…</Empty>
  if (isError || !data) return <ErrorLine text={(error as any)?.response?.status === 403 ? 'You do not have access to this project’s costs.' : 'Could not load the cost position.'} />
  const s = data
  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 16 }}>
        <Stat label="Original budget" value={s.baselineCaptured ? money(s.originalBudget) : '—'} sub={s.baselineCaptured ? undefined : 'captured when the project is approved'} />
        <Stat label="Revised budget" value={money(s.revisedBudget)} sub={`${signedMoney(s.additions)} added · ${signedMoney(s.netAdjustments)} adjusted`} />
        <Stat label="Committed" value={money(s.committed)} tone="warn" />
        <Stat label="Actual" value={money(s.actual)} sub={`${pct(s.consumedPct)} of budget`} tone={s.actual > s.revisedBudget ? 'bad' : undefined} />
        <Stat label="Forecast at completion" value={money(s.eac)} sub={`${money(s.etc)} still to come`} tone={s.vac < 0 ? 'bad' : 'good'} />
        <Stat label="Variance at completion" value={signedMoney(s.vac)} tone={s.vac < 0 ? 'bad' : 'good'} />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(240px,1fr))', gap: 12 }}>
        <div style={card}>
          <Stat label="Pending change orders" value={signedMoney(s.pendingChangeOrderValue)} sub={`${s.pendingChangeOrderCount} draft or submitted — forecast with them ${money(s.eacIncludingPending)}`}
            tone={s.pendingChangeOrderCount ? 'warn' : undefined} />
        </div>
        <div style={card}>
          <Stat label="Contingency left" value={money(s.contingencyRemaining)} sub={`${money(s.contingencyUsed)} used of ${money(s.contingencyBudget)}`}
            tone={s.contingencyRemaining < 0 ? 'bad' : undefined} />
        </div>
        <div style={card}>
          <Stat label="Forecast margin" value={s.forecastMargin == null ? '—' : money(s.forecastMargin)}
            sub={s.contractValue == null ? 'set the contract value to see margin' : `${pct(s.forecastMarginPct)} of ${money(s.contractValue)}`}
            tone={s.forecastMargin != null && s.forecastMargin < 0 ? 'bad' : undefined} />
        </div>
      </div>

      <CostCharts projectId={projectId} summary={s} lines={lines} />

      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        <div style={{ padding: '12px 14px 0' }}><SectionTitle right={s.overBudgetLineCount > 0 && <Pill tone="danger">{s.overBudgetLineCount} over budget</Pill>}>By line</SectionTitle></div>
        {s.lines.length === 0 ? <Empty>No budget lines yet.</Empty> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr>{['Line', 'Original', 'Budget', 'Committed', 'Actual', 'To come', 'At completion', 'Variance', ''].map(h =>
              <th key={h} style={{ ...th, textAlign: h === 'Line' || !h ? 'left' : 'right' }}>{h}</th>)}</tr></thead>
            <tbody>{s.lines.map(l => (
              <tr key={l.id} style={{ background: l.overBudget ? 'var(--hf-danger-soft)' : undefined }}>
                <td style={td}>{lineName(lines, l.id)}</td>
                <td style={{ ...td, textAlign: 'right' }}>{l.original == null ? <span style={muted}>added</span> : money(l.original)}</td>
                <td style={{ ...td, textAlign: 'right', fontWeight: 600 }}>{money(l.budgeted)}</td>
                <td style={{ ...td, textAlign: 'right' }}>{money(l.committed)}</td>
                <td style={{ ...td, textAlign: 'right' }}>{money(l.actual)}</td>
                <td style={{ ...td, textAlign: 'right' }}>{money(l.etc)}{l.etcManual && <div style={muted}>estimate</div>}</td>
                <td style={{ ...td, textAlign: 'right', fontWeight: 600 }}>{money(l.eac)}</td>
                <td style={{ ...td, textAlign: 'right', color: l.vac < 0 ? 'var(--hf-danger-text)' : 'var(--hf-success-text)', fontWeight: 700 }}>{signedMoney(l.vac)}</td>
                <td style={td}>{canAdmin && <Btn small kind="ghost" title="Set the cost still to come" onClick={() => setForecastFor(l)}><Pencil size={12} /> Forecast</Btn>}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
        <div style={{ ...muted, padding: '8px 14px 12px' }}>“To come” is your estimate when one is set, otherwise the unspent budget. At completion = actual + committed + to come.</div>
      </div>
      {forecastFor && <ForecastDialog projectId={projectId} line={forecastFor} name={lineName(lines, forecastFor.id)} onClose={() => setForecastFor(null)} />}
    </div>
  )
}

function ForecastDialog({ projectId, line, name, onClose }: { projectId: string; line: LineResult; name: string; onClose: () => void }) {
  const invalidate = useInvalidateCost(projectId)
  const [v, setV] = useState(line.etcManual ? String(line.etc) : '')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const save = async (value: number | null) => {
    setBusy(true); setErr('')
    try { await apiClient.put(`/api/v1/projects/${projectId}/budget/lines/${line.id}/forecast`, { forecastToComplete: value }); toast.success('Forecast updated'); invalidate(); onClose() }
    catch (e) { setErr(await errorText(e, 'Could not update the forecast')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Forecast to complete" onClose={onClose}
      footer={<>{line.etcManual && <Btn kind="ghost" disabled={busy} onClick={() => save(null)}>Clear estimate</Btn>}<span style={{ flex: 1 }} />
        <Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy || v === ''} onClick={() => save(Number(v))}>Save</Btn></>}>
      <div style={{ ...muted, marginBottom: 12 }}>{name} — {money(line.actual)} actual and {money(line.committed)} committed so far.</div>
      <Field label="Additional cost still expected (R)" hint="Beyond what is already actual or committed. Clear it to assume the unspent budget will be spent.">
        <input autoFocus type="number" min={0} step="0.01" value={v} onChange={e => setV(e.target.value)} style={{ ...inp, maxWidth: 220 }} />
      </Field>
      {v !== '' && <div style={muted}>At completion this line would cost <b>{money(line.actual + line.committed + Number(v))}</b> against {money(line.budgeted)}.</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ── Ledger ───────────────────────────────────────────────────────────────────

export function CostLedger({ projectId }: { projectId: string }) {
  const canAdmin = usePermission('PM_ADMIN')
  const invalidate = useInvalidateCost(projectId)
  const { data: lines = [] } = useLines(projectId)
  const [lineId, setLineId] = useState('')
  const [posting, setPosting] = useState(false)
  const [reversing, setReversing] = useState<CostEntry | null>(null)
  const { data = [], isLoading, isError } = useQuery<CostEntry[]>({
    queryKey: ['pm-ledger', projectId, lineId],
    queryFn: async () => list<CostEntry>(await apiClient.get(`/api/v1/projects/${projectId}/budget/ledger`, { params: { lineId: lineId || undefined, limit: 500 } })),
  })
  const reversedIds = new Set(data.filter(e => e.reversalOf).map(e => e.reversalOf))
  return (
    <div>
      <SectionTitle right={<>
        <select value={lineId} onChange={e => setLineId(e.target.value)} style={{ ...inp, width: 260 }} aria-label="Budget line">
          <option value="">All budget lines</option>
          {lines.map(l => <option key={l.id} value={l.id}>{l.category} — {l.description}</option>)}
        </select>
        {canAdmin && <Btn kind="primary" disabled={!lines.length} onClick={() => setPosting(true)}><Plus size={13} /> Post cost</Btn>}
      </>}>Cost ledger</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        {isLoading ? <Empty>Loading…</Empty> : isError ? <ErrorLine text="Could not load the ledger." /> : data.length === 0 ? <Empty>No commitments or actual costs posted yet.</Empty> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Type</th><th style={th}>Line</th><th style={th}>Source</th><th style={th}>Description</th>
              <th style={{ ...th, textAlign: 'right' }}>Amount</th><th style={th}>Posted</th><th style={th} /></tr></thead>
            <tbody>{data.map(e => (
              <tr key={e.id} style={{ opacity: reversedIds.has(e.id) ? 0.55 : 1 }}>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(e.entryDate)}</td>
                <td style={td}><Pill tone={e.type === 'ACTUAL' ? 'info' : 'warning'}>{e.type === 'ACTUAL' ? 'Actual' : 'Commitment'}</Pill>{e.reversalOf && <div style={muted}>reversal</div>}</td>
                <td style={td}>{lineName(lines, e.budgetLineId)}</td>
                <td style={td}>{e.sourceType}<div style={muted}>{e.sourceRef}</div></td>
                <td style={td}>{e.description ?? '—'}</td>
                <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 600 }}>{signedMoney(e.amount)}</td>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{when(e.createdAt)}<div style={muted}>{e.postedByName}</div></td>
                <td style={td}>{canAdmin && !e.reversalOf && !reversedIds.has(e.id) && <Btn small kind="ghost" onClick={() => setReversing(e)}><Undo2 size={12} /> Reverse</Btn>}
                  {reversedIds.has(e.id) && <span style={muted}>reversed</span>}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
      </div>
      <div style={{ ...muted, marginTop: 6 }}>Entries are never edited or deleted; a mistake is corrected by reversing it, which posts an offsetting entry.</div>
      {posting && <PostCostDialog projectId={projectId} lines={lines} defaultLine={lineId} onClose={() => setPosting(false)} onPosted={invalidate} />}
      {reversing && <ReasonDialog title="Reverse ledger entry" confirm="Reverse" danger max={400} onClose={() => setReversing(null)}
        intro={<>Posts {signedMoney(-reversing.amount)} against {lineName(lines, reversing.budgetLineId)} to cancel this {reversing.type.toLowerCase()}. An entry can be reversed once.</>}
        onSubmit={async reason => { await apiClient.post(`/api/v1/projects/${projectId}/budget/ledger/${reversing.id}/reverse`, { reason }); toast.success('Entry reversed'); invalidate() }} />}
    </div>
  )
}

function PostCostDialog({ projectId, lines, defaultLine, onClose, onPosted }: { projectId: string; lines: BudgetLine[]; defaultLine: string; onClose: () => void; onPosted: () => void }) {
  const [f, setF] = useState({ budgetLineId: defaultLine || lines[0]?.id || '', type: 'ACTUAL', amount: '', sourceType: 'MANUAL', sourceRef: '', description: '', entryDate: today() })
  // one key per dialog: a double click or a retry after a timeout returns the first posting instead of posting twice
  const [key] = useState(() => (globalThis.crypto?.randomUUID?.() ?? `k-${Date.now()}-${Math.random()}`))
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const line = lines.find(l => l.id === f.budgetLineId)
  const save = async () => {
    const amount = Number(f.amount)
    if (!f.budgetLineId) { setErr('Choose a budget line'); return }
    if (!amount) { setErr('Enter a non-zero amount'); return }
    setBusy(true); setErr('')
    try {
      const r = await apiClient.post(`/api/v1/projects/${projectId}/budget/ledger`, { ...f, amount, sourceRef: f.sourceRef.trim() || null, description: f.description.trim() || null, idempotencyKey: key })
      const e = one<CostEntry>(r)
      if (e.duplicate) toast.info('This was already posted — nothing was added')
      else if (e.lineOverBudget) toast.error('Posted — this line is now over budget')
      else toast.success('Posted')
      onPosted(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not post the cost')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Post a cost" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Posting…' : 'Post'}</Btn></>}>
      <Field label="Budget line">
        <select value={f.budgetLineId} onChange={e => set('budgetLineId', e.target.value)} style={inp}>{lines.map(l => <option key={l.id} value={l.id}>{l.category} — {l.description}</option>)}</select>
      </Field>
      {line && <div style={{ ...muted, marginTop: -6, marginBottom: 12 }}>{money(line.budgetedAmount)} budget · {money(line.committedAmount)} committed · {money(line.actualAmount)} actual</div>}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
        <Field label="Type"><select value={f.type} onChange={e => set('type', e.target.value)} style={inp}><option value="ACTUAL">Actual cost</option><option value="COMMITMENT">Commitment</option></select></Field>
        <Field label="Amount (R)" hint="Negative for a credit or a released commitment"><input type="number" step="0.01" value={f.amount} onChange={e => set('amount', e.target.value)} style={inp} /></Field>
        <Field label="Date"><input type="date" value={f.entryDate} onChange={e => set('entryDate', e.target.value)} style={inp} /></Field>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 10 }}>
        <Field label="Source"><select value={f.sourceType} onChange={e => set('sourceType', e.target.value)} style={inp}>
          {['MANUAL', 'PURCHASE_ORDER', 'SUPPLIER_INVOICE', 'SUBCONTRACT', 'TIMESHEET', 'PETTY_CASH'].map(s => <option key={s} value={s}>{s.replace(/_/g, ' ').toLowerCase()}</option>)}</select></Field>
        <Field label="Reference" hint="PO, invoice or order number"><input maxLength={100} value={f.sourceRef} onChange={e => set('sourceRef', e.target.value)} style={inp} /></Field>
      </div>
      <Field label="Description"><input maxLength={500} value={f.description} onChange={e => set('description', e.target.value)} style={inp} /></Field>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ── Revisions and transfers ─────────────────────────────────────────────────

const REV_LABEL: Record<string, string> = { REVISION: 'Revised', ADDITION: 'Line added', TRANSFER_OUT: 'Transfer out', TRANSFER_IN: 'Transfer in' }

export function BudgetRevisions({ projectId }: { projectId: string }) {
  const canAdmin = usePermission('PM_ADMIN')
  const { data: lines = [] } = useLines(projectId)
  const [transferring, setTransferring] = useState(false)
  const { data = [], isLoading } = useQuery<Revision[]>({
    queryKey: ['pm-budget-revisions', projectId],
    queryFn: async () => list<Revision>(await apiClient.get(`/api/v1/projects/${projectId}/budget/revisions`)),
  })
  return (
    <div>
      <SectionTitle right={canAdmin && <Btn kind="primary" disabled={lines.length < 2} onClick={() => setTransferring(true)}><ArrowRightLeft size={13} /> Transfer budget</Btn>}>Budget history</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        {isLoading ? <Empty>Loading…</Empty> : data.length === 0 ? <Empty>No budget changes recorded yet.</Empty> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>When</th><th style={th}>Line</th><th style={th}>Change</th><th style={{ ...th, textAlign: 'right' }}>From</th><th style={{ ...th, textAlign: 'right' }}>To</th>
              <th style={{ ...th, textAlign: 'right' }}>Δ</th><th style={th}>Reason</th></tr></thead>
            <tbody>{data.map(r => (
              <tr key={r.id}>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{when(r.revisedAt)}<div style={muted}>{r.revisedByName}</div></td>
                <td style={td}>{lineName(lines, r.budgetLineId)}</td>
                <td style={td}><Pill tone={r.revisionType.startsWith('TRANSFER') ? 'violet' : r.changeOrderId ? 'info' : 'neutral'}>{REV_LABEL[r.revisionType] ?? r.revisionType}</Pill>
                  {r.changeOrderId && <div style={muted}>by change order</div>}</td>
                <td style={{ ...td, textAlign: 'right' }}>{money(r.oldAmount)}</td>
                <td style={{ ...td, textAlign: 'right' }}>{money(r.newAmount)}</td>
                <td style={{ ...td, textAlign: 'right', fontWeight: 600, color: r.delta < 0 ? 'var(--hf-danger-text)' : 'var(--hf-success-text)' }}>{signedMoney(r.delta)}</td>
                <td style={td}>{r.reason ?? '—'}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
      </div>
      {transferring && <TransferDialog projectId={projectId} lines={lines} onClose={() => setTransferring(false)} />}
    </div>
  )
}

function TransferDialog({ projectId, lines, onClose }: { projectId: string; lines: BudgetLine[]; onClose: () => void }) {
  const invalidate = useInvalidateCost(projectId)
  const [f, setF] = useState({ fromLineId: lines[0]?.id ?? '', toLineId: lines[1]?.id ?? '', amount: '', reason: '' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const from = lines.find(l => l.id === f.fromLineId)
  const free = from ? Math.max(0, from.budgetedAmount - from.actualAmount - from.committedAmount) : 0
  const save = async () => {
    if (f.fromLineId === f.toLineId) { setErr('Choose two different lines'); return }
    if (!(Number(f.amount) > 0)) { setErr('Enter an amount greater than zero'); return }
    if (!f.reason.trim()) { setErr('A reason is required'); return }
    setBusy(true); setErr('')
    try { await apiClient.post(`/api/v1/projects/${projectId}/budget/transfers`, { ...f, amount: Number(f.amount), reason: f.reason.trim() }); toast.success('Budget transferred'); invalidate(); onClose() }
    catch (e) { setErr(await errorText(e, 'Could not transfer')) } finally { setBusy(false) }
  }
  const opts = lines.map(l => <option key={l.id} value={l.id}>{l.category} — {l.description}</option>)
  return (
    <Dialog title="Transfer budget between lines" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Transferring…' : 'Transfer'}</Btn></>}>
      <div style={{ ...muted, marginBottom: 12 }}>The total budget does not change. Only budget that is neither spent nor committed can move.</div>
      <Field label="From" hint={from ? `${money(free)} free to move` : undefined}><select value={f.fromLineId} onChange={e => set('fromLineId', e.target.value)} style={inp}>{opts}</select></Field>
      <Field label="To"><select value={f.toLineId} onChange={e => set('toLineId', e.target.value)} style={inp}>{opts}</select></Field>
      <Field label="Amount (R)"><input type="number" min={0.01} step="0.01" value={f.amount} onChange={e => set('amount', e.target.value)} style={{ ...inp, maxWidth: 220 }} /></Field>
      <Field label="Reason"><textarea rows={3} maxLength={2000} value={f.reason} onChange={e => set('reason', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ── Charts ───────────────────────────────────────────────────────────────────

/** Budget against cost by line, and cumulative spend over time from the ledger. */
function CostCharts({ projectId, summary: s, lines }: { projectId: string; summary: Summary; lines: BudgetLine[] }) {
  const { data: ledger = [] } = useQuery<CostEntry[]>({
    queryKey: ['pm-ledger', projectId, ''],
    queryFn: async () => list<CostEntry>(await apiClient.get(`/api/v1/projects/${projectId}/budget/ledger`, { params: { limit: 500 } })),
  })
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(380px,1fr))', gap: 12 }}>
      <div style={card}>
        <SectionTitle>Budget against cost</SectionTitle>
        <LineBars rows={s.lines.map(l => ({ id: l.id, label: lineName(lines, l.id), budget: Number(l.budgeted), actual: Number(l.actual), committed: Number(l.committed), eac: Number(l.eac) }))} />
        <div style={{ ...muted, display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 8 }}>
          <span><Swatch c="var(--hf-info)" /> actual</span><span><Swatch c="var(--hf-warning)" /> committed</span>
          <span><Swatch c="transparent" border /> budget</span><span><b style={{ color: 'var(--hf-text)' }}>|</b> forecast at completion</span>
        </div>
      </div>
      <div style={card}>
        <SectionTitle>Spend over time</SectionTitle>
        <SpendLine entries={ledger} budget={Number(s.revisedBudget)} />
      </div>
    </div>
  )
}

function Swatch({ c, border }: { c: string; border?: boolean }) {
  return <span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: c, border: border ? '1.5px solid var(--hf-text-muted)' : 'none', marginRight: 4, verticalAlign: -1 }} />
}

function LineBars({ rows }: { rows: { id: string; label: string; budget: number; actual: number; committed: number; eac: number }[] }) {
  if (!rows.length) return <Empty>No budget lines yet.</Empty>
  const max = Math.max(1, ...rows.flatMap(r => [r.budget, r.actual + r.committed, r.eac]))
  const pc = (v: number) => `${Math.max(0, v) / max * 100}%`
  return (
    <div style={{ display: 'grid', gap: 9 }} role="img" aria-label="Budget, actual, committed and forecast by budget line">
      {rows.map(r => {
        const over = r.eac > r.budget
        return (
          <div key={r.id} title={`${r.label}: budget ${money(r.budget)}, actual ${money(r.actual)}, committed ${money(r.committed)}, forecast ${money(r.eac)}`}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11.5, marginBottom: 3 }}>
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '60%' }}>{r.label}</span>
              <span style={{ color: over ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)', fontWeight: over ? 700 : 500 }}>{money(r.eac)} of {money(r.budget)}</span>
            </div>
            <div style={{ position: 'relative', height: 14, background: 'var(--hf-surface-sunken)', borderRadius: 3 }}>
              <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: pc(r.actual), background: 'var(--hf-info)', borderRadius: 3 }} />
              <div style={{ position: 'absolute', left: pc(r.actual), top: 0, bottom: 0, width: pc(r.committed), background: 'var(--hf-warning)', opacity: 0.8 }} />
              <div style={{ position: 'absolute', left: 0, top: -2, bottom: -2, width: pc(r.budget), border: '1.5px solid var(--hf-text-muted)', borderRadius: 3, pointerEvents: 'none' }} />
              <div style={{ position: 'absolute', left: pc(r.eac), top: -4, bottom: -4, width: 2, background: over ? 'var(--hf-danger)' : 'var(--hf-text)' }} />
            </div>
          </div>
        )
      })}
    </div>
  )
}

/** Cumulative actual cost by entry date (reversals included), with commitments on top, against the revised budget. */
export function spendSeries(entries: { type: string; amount: number; entryDate: string }[]) {
  const byDay = new Map<string, { actual: number; committed: number }>()
  for (const e of entries) {
    const d = byDay.get(e.entryDate) ?? { actual: 0, committed: 0 }
    if (e.type === 'ACTUAL') d.actual += Number(e.amount); else d.committed += Number(e.amount)
    byDay.set(e.entryDate, d)
  }
  let a = 0, c = 0
  return [...byDay.keys()].sort().map(k => { a += byDay.get(k)!.actual; c += byDay.get(k)!.committed; return { date: k, actual: a, withCommitted: a + c } })
}

function SpendLine({ entries, budget }: { entries: CostEntry[]; budget: number }) {
  const pts = spendSeries(entries)
  if (pts.length < 2) return <Empty>The chart appears once two or more days of costs are posted.</Empty>
  const W = 520, H = 200, L = 64, B = 24, T = 10, R = 10
  const t0 = new Date(pts[0].date).getTime(), t1 = new Date(pts[pts.length - 1].date).getTime() || t0 + 1
  const maxY = Math.max(budget, ...pts.map(p => p.withCommitted)) * 1.05 || 1
  const x = (d: string) => L + (new Date(d).getTime() - t0) / Math.max(1, t1 - t0) * (W - L - R)
  const y = (v: number) => T + (1 - v / maxY) * (H - T - B)
  const path = (k: 'actual' | 'withCommitted') => pts.map((p, i) => `${i ? 'L' : 'M'}${x(p.date).toFixed(1)},${y(p[k]).toFixed(1)}`).join(' ')
  const ticks = [0, 0.25, 0.5, 0.75, 1].map(f => maxY * f)
  const last = pts[pts.length - 1]
  return (
    <div>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: 'auto' }} role="img" aria-label={`Spend over time: ${money(last.actual)} actual, ${money(last.withCommitted)} with commitments, against a budget of ${money(budget)}`}>
        {ticks.map((v, i) => <g key={i}><line x1={L} x2={W - R} y1={y(v)} y2={y(v)} stroke="var(--hf-border)" strokeWidth={0.6} />
          <text x={L - 6} y={y(v) + 3} fontSize={9} textAnchor="end" fill="var(--hf-text-faint)">{v >= 1e6 ? `R${(v / 1e6).toFixed(1)}m` : `R${Math.round(v / 1000)}k`}</text></g>)}
        {budget > 0 && <g><line x1={L} x2={W - R} y1={y(budget)} y2={y(budget)} stroke="var(--hf-danger)" strokeDasharray="5 3" strokeWidth={1.2} />
          <text x={W - R} y={y(budget) - 4} fontSize={9} textAnchor="end" fill="var(--hf-danger-text)">budget {money(budget)}</text></g>}
        <path d={path('withCommitted')} fill="none" stroke="var(--hf-warning)" strokeWidth={1.6} strokeDasharray="4 2" />
        <path d={path('actual')} fill="none" stroke="var(--hf-info)" strokeWidth={2.2} />
        <text x={L} y={H - 6} fontSize={9} fill="var(--hf-text-faint)">{day(pts[0].date)}</text>
        <text x={W - R} y={H - 6} fontSize={9} textAnchor="end" fill="var(--hf-text-faint)">{day(last.date)}</text>
      </svg>
      <div style={{ ...muted, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <span><Swatch c="var(--hf-info)" /> actual {money(last.actual)}</span><span><Swatch c="var(--hf-warning)" /> with commitments {money(last.withCommitted)}</span>
        {entries.length >= 500 && <span>Only the latest 500 entries are drawn.</span>}
      </div>
    </div>
  )
}
