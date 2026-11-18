// src/pages/supply-chain/BudgetHierarchy.tsx — budgets as a tree: what each parent has handed to its sub-budgets, and what is left.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { inp, TH, TD, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, filterPill, fmtR } from "./scm.shared"
import { asList, errorText } from "./scmOps"
import { Pill } from "./scmUi"
import { flattenTree, allocationText, parentChoices } from "./scmLimits"
import { Network } from "lucide-react"

interface B { id: string; name: string; scopeType: string; scopeValue: string; periodStart: string; periodEnd: string; amount: number; used: number; available: number; status: string; state: string
  parentBudgetId: string | null; allocated: number; unallocated: number; childCount: number }
interface Node { budget: B; children: Node[] }

const BASE = "/api/v1/supply-chain/budgets"

export function BudgetHierarchy() {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_BUDGET_MANAGE", "SCM_ADMIN"])
  const [moving, setMoving] = useState<B | null>(null)
  const [parentId, setParentId] = useState("")
  const [err, setErr] = useState("")
  const tree = useQuery({ queryKey: ["scm-budget-tree"], queryFn: () => apiClient.get(`${BASE}/tree`).then(asList<Node>) })
  const move = useMutation({
    mutationFn: () => apiClient.put(`${BASE}/${moving!.id}/allocation`, { parentBudgetId: parentId || null }),
    onSuccess: () => { setMoving(null); setErr(""); qc.invalidateQueries({ queryKey: ["scm-budget-tree"] }); qc.invalidateQueries({ queryKey: ["scm-budgets"] }) },
    onError: e => setErr(errorText(e)),
  })

  if (tree.isLoading) return <Spinner />
  if (tree.isError) return <ErrBox msg={errorText(tree.error, "Could not load the budget hierarchy")} />
  const rows = flattenTree(tree.data ?? [])
  if (rows.length === 0) return <EmptyState icon={Network} title="No budgets" sub="Create a budget, then allocate sub-budgets out of it." />
  const all = rows.map(r => r.node.budget)
  const choices = moving ? parentChoices(all.filter(b => b.id !== moving.id), moving) : []

  return (
    <div style={{ overflowX: "auto", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr>{["Budget", "Amount", "Used (incl. sub-budgets)", "Allocated to sub-budgets", "Unallocated", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
        <tbody>{rows.map(({ node, depth }) => { const b = node.budget; return (
          <tr key={b.id} style={{ borderTop: "1px solid var(--hf-border)", opacity: b.status === "CLOSED" ? 0.55 : 1 }}>
            <td style={{ ...TD, paddingLeft: 12 + depth * 22 }}><strong>{b.name}</strong><div style={{ fontSize: 12 }}>{b.scopeType.replace("_", " ").toLowerCase()}: {b.scopeValue}</div></td>
            <td style={TD}>{fmtR(b.amount)}</td>
            <td style={TD}>{fmtR(b.used)} <Pill tone={b.state === "EXCEEDED" ? "bad" : b.state === "NEAR_LIMIT" ? "warn" : "ok"}>{b.state.replace("_", " ").toLowerCase()}</Pill></td>
            <td style={TD}>{allocationText(b)}</td>
            <td style={TD}>{b.childCount ? fmtR(b.unallocated) : "—"}</td>
            <td style={TD}>{canManage && b.status === "ACTIVE" && <button style={filterPill(false)} onClick={() => { setMoving(b); setParentId(b.parentBudgetId ?? ""); setErr("") }}>{b.parentBudgetId ? "Change parent" : "Allocate from…"}</button>}</td>
          </tr>) })}</tbody>
      </table>
      <p style={{ fontSize: 12, padding: "8px 14px", color: "var(--hf-text-tertiary)", margin: 0 }}>
        Spend against a sub-budget also counts against the budget it was allocated from, once. Sub-budgets together cannot exceed their parent, and a parent cannot be reduced below what it has handed out.
      </p>
      {moving && (
        <Modal title={`Allocate ${moving.name}`} onClose={() => setMoving(null)}>
          {err && <ErrBox msg={err} />}
          <Field label="Take this budget out of">
            <select style={inp} aria-label="Parent budget" value={parentId} onChange={e => setParentId(e.target.value)}>
              <option value="">No parent (top level)</option>
              {choices.map(c => <option key={c.id} value={c.id}>{c.name} — {fmtR(c.unallocated)} unallocated</option>)}
            </select>
          </Field>
          <p style={{ fontSize: 12, opacity: 0.8 }}>Only active budgets of the same or broader scope that cover {moving.name}'s whole period are offered. The server re-checks the amount against what is unallocated.</p>
          <ModalFooter onCancel={() => setMoving(null)} onConfirm={() => move.mutate()} label="Save allocation" loading={move.isPending} />
        </Modal>)}
    </div>
  )
}
