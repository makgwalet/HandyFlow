// src/pages/supply-chain/ContractsTab.tsx — supplier contracts: Active / Expiring / Expired, renewal due, activate / terminate.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from "recharts"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useThemeColors } from "../../theme/ThemeContext"
import { FileSignature } from "lucide-react"
import { TH, TD, Modal, ModalFooter, ErrBox, Spinner, EmptyState, filterPill, fmtR, fmtDate } from "./scm.shared"
import { asList, errorText, contractBucket, contractCounts, daysUntil, dueText, type ContractBucket } from "./scmOps"
import { StatCard, Panel, Pill, grid, type Tone } from "./scmUi"

export interface Contract { id: string; contractNumber: string; supplierName: string; title: string; status: string; startDate: string; endDate: string; valueCap: number | null; autoRenew: boolean; renewalNoticeDays: number; renewalNoticeDue: boolean; lines: unknown[] }
const BASE = "/api/v1/supply-chain/contracts"
type Tab = "ALL" | ContractBucket
const TABS: { id: Tab; label: string }[] = [{ id: "ALL", label: "All" }, { id: "ACTIVE", label: "Active" }, { id: "EXPIRING", label: "Expiring" }, { id: "EXPIRED", label: "Expired" }, { id: "DRAFT", label: "Draft" }]
const TONE_OF: Record<ContractBucket, Tone> = { ACTIVE: "ok", EXPIRING: "warn", EXPIRED: "bad", DRAFT: "neutral", TERMINATED: "neutral" }
const SLICE_VAR: Record<ContractBucket, string> = { ACTIVE: "--hf-success", EXPIRING: "--hf-warning", EXPIRED: "--hf-danger", DRAFT: "--hf-text-faint", TERMINATED: "--hf-border" }

export function ContractsTab() {
  const qc = useQueryClient()
  const color = useThemeColors()
  const canManage = usePermissions(["SCM_CONTRACT_MANAGE", "SCM_ADMIN"])
  const [tab, setTab] = useState<Tab>("ALL")
  const [ending, setEnding] = useState<Contract | null>(null)
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")

  const q = useQuery({ queryKey: ["scm-contracts"], queryFn: () => apiClient.get(BASE).then(asList<Contract>) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["scm-contracts"] })
  const activate = useMutation({ mutationFn: (id: string) => apiClient.post(`${BASE}/${id}/activate`), onSuccess: () => { setErr(""); refresh() }, onError: e => setErr(errorText(e)) })
  const terminate = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/${ending!.id}/terminate`, { reason }),
    onSuccess: () => { setEnding(null); setReason(""); setErr(""); refresh() }, onError: e => setErr(errorText(e)),
  })

  const list = q.data ?? []
  const counts = contractCounts(list)
  const rows = list.filter(c => tab === "ALL" || contractBucket(c) === tab)
  const attention = list.filter(c => ["EXPIRING", "EXPIRED"].includes(contractBucket(c)) && c.status !== "TERMINATED")
    .sort((a, b) => (daysUntil(a.endDate) ?? 0) - (daysUntil(b.endDate) ?? 0)).slice(0, 6)
  const slices = (Object.keys(counts) as ContractBucket[]).filter(k => counts[k] > 0).map(k => ({ name: k, value: counts[k] }))

  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <div style={grid(170)}>
        <StatCard label="Active" value={counts.ACTIVE} tone="ok" icon={FileSignature} />
        <StatCard label="Expiring ≤ 60 days" value={counts.EXPIRING} tone="warn" />
        <StatCard label="Expired" value={counts.EXPIRED} tone="bad" />
        <StatCard label="Draft" value={counts.DRAFT} />
      </div>
      {err && <ErrBox msg={err} />}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 14 }}>
        <Panel title="Contract status">
          {slices.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No contracts yet.</div> : (
            <div style={{ height: 180 }}>
              <ResponsiveContainer><PieChart><Pie data={slices} dataKey="value" nameKey="name" innerRadius={48} outerRadius={72} paddingAngle={2}>
                {slices.map(s => <Cell key={s.name} fill={color(SLICE_VAR[s.name as ContractBucket])} />)}</Pie><Tooltip /></PieChart></ResponsiveContainer>
            </div>)}
        </Panel>
        <Panel title="Needs attention">
          {attention.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing expiring or expired.</div> : (
            <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
              {attention.map(c => { const b = contractBucket(c); return (
                <li key={c.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 13 }}>
                  <span><strong>{c.contractNumber}</strong> · {c.supplierName}</span>
                  <Pill tone={TONE_OF[b]}>{b === "EXPIRED" ? "Expired" : "Ends"} {dueText(daysUntil(c.endDate))}</Pill>
                </li>) })}
            </ul>)}
        </Panel>
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {TABS.map(t => <button key={t.id} style={filterPill(tab === t.id)} onClick={() => setTab(t.id)}>{t.label}{t.id !== "ALL" ? ` (${counts[t.id as ContractBucket]})` : ` (${list.length})`}</button>)}
      </div>

      {q.isLoading ? <Spinner /> : q.isError ? <ErrBox msg={errorText(q.error, "Could not load contracts")} /> : rows.length === 0 ? (
        <EmptyState icon={FileSignature} title="No contracts" sub="Contracts agreed with suppliers appear here." />
      ) : (
        <div style={{ overflowX: "auto", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={TH}>Contract</th><th style={TH}>Supplier</th><th style={TH}>Term</th><th style={TH}>Value cap</th><th style={TH}>Status</th><th style={TH}>Renewal</th><th style={TH} /></tr></thead>
            <tbody>{rows.map(c => { const b = contractBucket(c); return (
              <tr key={c.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={TD}><strong>{c.contractNumber}</strong><div style={{ fontSize: 12 }}>{c.title}</div></td>
                <td style={TD}>{c.supplierName}</td>
                <td style={TD}>{fmtDate(c.startDate)} → {fmtDate(c.endDate)}</td>
                <td style={TD}>{c.valueCap != null ? fmtR(c.valueCap) : "—"}</td>
                <td style={TD}><Pill tone={TONE_OF[b]}>{c.status === "TERMINATED" ? "Terminated" : b[0] + b.slice(1).toLowerCase()}</Pill></td>
                <td style={TD}>{c.renewalNoticeDue ? <Pill tone="warn">Notice due</Pill> : c.autoRenew ? "Auto-renews" : "—"}</td>
                <td style={{ ...TD, textAlign: "right", whiteSpace: "nowrap" }}>
                  {canManage && c.status === "DRAFT" && <button style={filterPill(false)} onClick={() => activate.mutate(c.id)}>Activate</button>}
                  {canManage && c.status === "ACTIVE" && <button style={filterPill(false)} onClick={() => { setEnding(c); setReason("") }}>Terminate</button>}
                </td>
              </tr>) })}</tbody>
          </table>
        </div>
      )}

      {ending && (
        <Modal title={`Terminate ${ending.contractNumber}`} onClose={() => setEnding(null)}>
          <label style={{ fontSize: 12, fontWeight: 600 }}>Reason (required)</label>
          <textarea aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} rows={3} style={{ width: "100%", marginTop: 6, padding: 10, border: "1.5px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)" }} />
          <ModalFooter onCancel={() => setEnding(null)} label="Terminate" loading={terminate.isPending} accent="var(--hf-danger)"
            onConfirm={() => { if (!reason.trim()) { setErr("Give a reason."); return } terminate.mutate() }} />
        </Modal>
      )}
    </div>
  )
}
