// src/pages/facilities/ReportsTab.tsx
//
// Reports and CSV exports: work-order trends, vendor performance, asset reliability, and downloads of the registers.
// Reports are for managers; the server refuses anyone limited to their own sites or jobs, and says so here.
import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { facilitiesApi, type ExportName } from '../../api/facilities.api'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, Card, DataTable, Notice, type Column } from './ui'
import { fmtDate, fmtMoney, inputStyle, saveBlob } from './format'

const iso = (d: Date) => d.toISOString().slice(0, 10)
const daysAgo = (n: number) => { const d = new Date(); d.setDate(d.getDate() - n); return iso(d) }
const PRESETS = [{ id: 30, label: '30 days' }, { id: 90, label: '90 days' }, { id: 180, label: '6 months' }, { id: 365, label: '12 months' }]
const REPORTS = [{ id: 'trends', label: 'Trends' }, { id: 'vendors', label: 'Vendor performance' }, { id: 'assets', label: 'Asset reliability' }]
const hours = (n: number | null) => (n == null ? '—' : `${n.toFixed(1)} h`)
const pct = (n: number | null) => (n == null ? '—' : `${n.toFixed(1)}%`)
const axis = { fontSize: 11, fill: 'var(--hf-text-muted)' }

/** Download a CSV export through the signed-in client and save it. */
function useExport() {
  return useMutation({
    mutationFn: async (a: { name: ExportName; params?: Record<string, string | undefined> }) => {
      const { blob, fileName } = await facilitiesApi.exportCsv(a.name, a.params)
      saveBlob(blob, fileName)
    },
    onError: e => toast.error(errorMessage(e, 'The export could not be created. Very large exports are refused: narrow the dates.')),
  })
}

export default function ReportsTab() {
  const [from, setFrom] = useState(daysAgo(90))
  const [to, setTo] = useState(iso(new Date()))
  const [report, setReport] = useState('trends')
  const [bucket, setBucket] = useState('WEEK')
  const valid = !!from && !!to && from <= to
  const exp = useExport()
  const exporting = (name: ExportName) => exp.isPending && exp.variables?.name === name

  const trends = useQuery({ queryKey: ['facilities', 'report', 'trends', from, to, bucket], queryFn: () => facilitiesApi.trends(from, to, bucket), enabled: valid && report === 'trends' })
  const vendors = useQuery({ queryKey: ['facilities', 'report', 'vendors', from, to], queryFn: () => facilitiesApi.vendorPerformance(from, to), enabled: valid && report === 'vendors' })
  const assets = useQuery({ queryKey: ['facilities', 'report', 'assets', from, to], queryFn: () => facilitiesApi.assetReliability(from, to), enabled: valid && report === 'assets' })
  const active = report === 'trends' ? trends : report === 'vendors' ? vendors : assets
  const forbidden = (active.error as { response?: { status?: number } } | null)?.response?.status === 403

  const exportName: Record<string, ExportName> = { trends: 'work-orders', vendors: 'vendor-performance', assets: 'asset-reliability' }

  return (
    <div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', marginBottom: 14 }}>
        <div role="tablist" aria-label="Report" style={{ display: 'flex', gap: 6 }}>
          {REPORTS.map(r => (
            <button key={r.id} type="button" role="tab" aria-selected={report === r.id} onClick={() => setReport(r.id)}
              style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12.5, fontWeight: 600, cursor: 'pointer', border: `1px solid ${report === r.id ? 'var(--hf-accent)' : 'var(--hf-border)'}`,
                background: report === r.id ? 'var(--hf-accent-soft)' : 'var(--hf-surface)', color: report === r.id ? 'var(--hf-accent-text-strong)' : 'var(--hf-text-secondary)' }}>{r.label}</button>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginLeft: 'auto' }}>
          <select aria-label="Quick range" value="" onChange={e => { if (e.target.value) { setFrom(daysAgo(Number(e.target.value))); setTo(iso(new Date())) } }} style={{ ...inputStyle, width: 'auto' }}>
            <option value="">Quick range…</option>{PRESETS.map(p => <option key={p.id} value={p.id}>Last {p.label}</option>)}
          </select>
          <input type="date" aria-label="From" value={from} max={to} onChange={e => setFrom(e.target.value)} style={{ ...inputStyle, width: 'auto' }} />
          <span aria-hidden="true" style={{ color: 'var(--hf-text-muted)' }}>to</span>
          <input type="date" aria-label="To" value={to} min={from} onChange={e => setTo(e.target.value)} style={{ ...inputStyle, width: 'auto' }} />
          <Btn busy={exporting(exportName[report])} disabled={!valid} onClick={() => exp.mutate({ name: exportName[report], params: { from, to } })}><Download size={14} />Export CSV</Btn>
        </div>
      </div>

      {!valid && <Notice tone="warn">Choose a start date on or before the end date.</Notice>}
      {forbidden && <Notice tone="warn">Reports are for managers. Your access is limited to your own sites or jobs.</Notice>}
      {active.isError && !forbidden && <Notice tone="bad">The report could not be loaded. Try a shorter date range.</Notice>}
      {active.isLoading && <div aria-busy="true" style={{ color: 'var(--hf-text-muted)', fontSize: 13 }}>Loading…</div>}

      {report === 'trends' && trends.data && (
        <>
          <div style={{ display: 'flex', gap: 6, marginBottom: 10, alignItems: 'center' }}>
            <span style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>Group by</span>
            {['WEEK', 'MONTH'].map(b => (
              <button key={b} type="button" aria-pressed={bucket === b} onClick={() => setBucket(b)}
                style={{ padding: '4px 10px', borderRadius: 16, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: `1px solid ${bucket === b ? 'var(--hf-accent)' : 'var(--hf-border)'}`,
                  background: bucket === b ? 'var(--hf-accent-soft)' : 'var(--hf-surface)', color: 'var(--hf-text-secondary)' }}>{b === 'WEEK' ? 'Week' : 'Month'}</button>
            ))}
          </div>
          <Card title="Work orders raised and completed">
            {trends.data.points.length === 0 ? <div style={{ color: 'var(--hf-text-muted)', fontSize: 13 }}>No work in this period.</div> : (
              <div style={{ height: 260 }} role="img" aria-label="Chart of work orders raised and completed per period">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={trends.data.points.map(p => ({ ...p, label: fmtDate(p.start) }))} margin={{ top: 4, right: 8, left: -12, bottom: 0 }}>
                    <CartesianGrid stroke="var(--hf-border-subtle)" vertical={false} />
                    <XAxis dataKey="label" tick={axis} tickLine={false} axisLine={{ stroke: 'var(--hf-border)' }} />
                    <YAxis allowDecimals={false} tick={axis} tickLine={false} axisLine={false} />
                    <Tooltip contentStyle={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 12 }} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                    <Bar dataKey="raised" name="Raised" fill="var(--hf-info-text)" radius={[3, 3, 0, 0]} />
                    <Bar dataKey="completed" name="Completed" fill="var(--hf-success-text)" radius={[3, 3, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </Card>
          <div style={{ height: 14 }} />
          <Card title="Cost and downtime">
            {trends.data.points.length > 0 && (
              <div style={{ height: 220 }} role="img" aria-label="Chart of cost per period">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={trends.data.points.map(p => ({ ...p, label: fmtDate(p.start) }))} margin={{ top: 4, right: 8, left: 4, bottom: 0 }}>
                    <CartesianGrid stroke="var(--hf-border-subtle)" vertical={false} />
                    <XAxis dataKey="label" tick={axis} tickLine={false} axisLine={{ stroke: 'var(--hf-border)' }} />
                    <YAxis tick={axis} tickLine={false} axisLine={false} tickFormatter={(v: number) => `R${Math.round(v / 1000)}k`} />
                    <Tooltip formatter={(v) => fmtMoney(Number(v))} contentStyle={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 12 }} />
                    <Line dataKey="cost" name="Cost" stroke="var(--hf-accent)" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
            <div style={{ marginTop: 12 }}>
              <DataTable rows={trends.data.points} rowKey={p => p.start} empty="No work in this period."
                columns={[
                  { key: 's', header: 'Period', render: p => fmtDate(p.start) },
                  { key: 'r', header: 'Raised', align: 'right', render: p => p.raised },
                  { key: 'c', header: 'Completed', align: 'right', render: p => p.completed },
                  { key: 'h', header: 'Avg time to complete', align: 'right', render: p => hours(p.avgCompletionHours) },
                  { key: 'w', header: 'Reworked', align: 'right', render: p => p.reworked },
                  { key: 'd', header: 'Downtime', align: 'right', render: p => `${(p.downtimeMinutes / 60).toFixed(1)} h` },
                  { key: 'k', header: 'Cost', align: 'right', render: p => fmtMoney(p.cost) },
                ] satisfies Column<(typeof trends.data.points)[number]>[]} />
            </div>
          </Card>
        </>
      )}

      {report === 'vendors' && vendors.data && (
        <DataTable rows={vendors.data.vendors} rowKey={v => v.vendorId ?? v.vendorName} empty="No vendor work in this period."
          columns={[
            { key: 'n', header: 'Vendor', render: v => <strong>{v.vendorName}</strong> },
            { key: 'j', header: 'Jobs', align: 'right', render: v => v.jobs },
            { key: 'c', header: 'Completed', align: 'right', render: v => v.completed },
            { key: 'o', header: 'Open', align: 'right', render: v => v.open },
            { key: 'x', header: 'Cancelled', align: 'right', render: v => v.cancelled },
            { key: 'a', header: 'Avg time to complete', align: 'right', render: v => hours(v.avgCompletionHours) },
            { key: 'rw', header: 'Rework rate', align: 'right', render: v => pct(v.reworkRatePercent) },
            { key: 't', header: 'Total cost', align: 'right', render: v => fmtMoney(v.totalCost) },
            { key: 'av', header: 'Avg cost', align: 'right', render: v => fmtMoney(v.avgCost) },
          ] satisfies Column<(typeof vendors.data.vendors)[number]>[]} />
      )}

      {report === 'assets' && assets.data && (
        <>
          <p style={{ margin: '0 0 10px', fontSize: 12.5, color: 'var(--hf-text-muted)' }}>The {assets.data.limit} assets with the most failures in the period. MTTR is the average repair time; MTBF the average time between failures.</p>
          <DataTable rows={assets.data.assets} rowKey={a => a.assetId} empty="No failures in this period."
            columns={[
              { key: 'a', header: 'Asset', render: a => <div><strong>{a.assetTag}</strong><div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{a.assetName}</div></div> },
              { key: 'f', header: 'Failures', align: 'right', render: a => a.failures },
              { key: 'rf', header: 'Repeat', align: 'right', render: a => a.repeatFailures },
              { key: 'd', header: 'Downtime', align: 'right', render: a => `${(a.downtimeMinutes / 60).toFixed(1)} h` },
              { key: 'mt', header: 'MTTR', align: 'right', render: a => hours(a.mttrHours) },
              { key: 'mb', header: 'MTBF', align: 'right', render: a => hours(a.mtbfHours) },
              { key: 'cause', header: 'Top cause', render: a => a.topFailureCause ?? '—' },
              { key: 'k', header: 'Cost', align: 'right', render: a => fmtMoney(a.totalCost) },
            ] satisfies Column<(typeof assets.data.assets)[number]>[]} />
        </>
      )}

      <div style={{ height: 22 }} />
      <Card title="Register exports" action={<span style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>CSV, opens in Excel</span>}>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          {([['certificates', 'Compliance certificates'], ['technician-licences', 'Technician licences'], ['corrective-actions', 'Corrective actions'], ['stock-valuation', 'Stock valuation']] as [ExportName, string][]).map(([name, label]) => (
            <Btn key={name} busy={exporting(name)} onClick={() => exp.mutate({ name })}><Download size={14} />{label}</Btn>
          ))}
        </div>
      </Card>
    </div>
  )
}
