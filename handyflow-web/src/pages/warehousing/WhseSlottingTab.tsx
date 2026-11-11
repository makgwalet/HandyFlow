// src/pages/warehousing/WhseSlottingTab.tsx
//
// Slotting: the items picked most should sit nearest the front of the pick walk. Items are classed A, B or C by the units picked over a period;
// for the busy ones this suggests a move into an empty location nearer the front. A suggestion only becomes a move task when you ask for it,
// and the stock moves when someone does the task (Tasks > Moves).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { btnPrimary, card, Chip, ErrorLine, fmtQty, inputStyle, labelStyle } from "./whseUi"

export interface Suggestion {
  clientId: string; clientName: string; itemId: string; sku: string; description: string; abc: "A" | "B" | "C"; picked: number
  fromLocationId: string; fromCode: string; toLocationId: string; toCode: string; qty: number; improvement: number
}
export interface Overview { days: number; itemsWithStock: number; classA: number; classB: number; classC: number; suggestions: Suggestion[] }

const CLASS_STYLE = {
  A: { bg: "var(--hf-success-soft)", fg: "var(--hf-success-text-strong)" },
  B: { bg: "var(--hf-info-soft)", fg: "var(--hf-info-text)" },
  C: { bg: "var(--hf-surface-muted)", fg: "var(--hf-text-muted)" },
} as const

export const PERIODS = [30, 90, 180, 365]

function Row({ s, canWork, onDone }: { s: Suggestion; canWork: boolean; onDone: () => void }) {
  const move = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/warehousing/slotting/moves", { clientId: s.clientId, itemId: s.itemId, fromLocationId: s.fromLocationId, toLocationId: s.toLocationId }),
    onSuccess: onDone,
  })
  const st = CLASS_STYLE[s.abc]
  return (
    <div style={{ ...card, padding: "10px 14px", marginBottom: 8, display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap", alignItems: "center" }}>
      <div>
        <p style={{ fontSize: 13.5, fontWeight: 700, margin: 0 }}>{s.sku} <Chip bg={st.bg} fg={st.fg}>Class {s.abc}</Chip>
          <span style={{ fontWeight: 400, color: "var(--hf-text-muted)", marginLeft: 8 }}>{s.description}</span></p>
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "2px 0 0" }}>
          Move {fmtQty(s.qty)} from <strong>{s.fromCode}</strong> to <strong>{s.toCode}</strong> · {s.improvement} places nearer · {fmtQty(s.picked)} picked · {s.clientName}
        </p>
      </div>
      {canWork && <button style={btnPrimary} disabled={move.isPending || move.isSuccess} onClick={() => move.mutate()}>{move.isSuccess ? "Task created" : "Create move task"}</button>}
      <ErrorLine error={move.error} fallback="Could not create the move" />
    </div>
  )
}

export default function WhseSlottingTab() {
  const qc = useQueryClient()
  const canWork = usePermission("WAREHOUSING_MANAGE") || usePermission("WAREHOUSING_ADMIN")
  const [days, setDays] = useState(90)
  const { data, isLoading, error } = useQuery<Overview>({
    queryKey: ["whse-slotting", days],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/slotting/suggestions?days=${days}`)).data,
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["whse-slotting"] }); qc.invalidateQueries({ queryKey: ["whse-tasks"] }) }
  const tile = (label: string, n: number | undefined) => (
    <div style={{ ...card, padding: "10px 14px", minWidth: 110 }}>
      <p style={{ fontSize: 20, fontWeight: 800, margin: 0 }}>{n ?? "–"}</p>
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>{label}</p>
    </div>
  )
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        {tile("Items with stock", data?.itemsWithStock)}{tile("Class A (fastest)", data?.classA)}{tile("Class B", data?.classB)}{tile("Class C (slow or none)", data?.classC)}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
        <label style={{ ...labelStyle, margin: 0 }} htmlFor="sl-days">Look at picks over</label>
        <select id="sl-days" style={inputStyle} value={days} onChange={e => setDays(Number(e.target.value))}>{PERIODS.map(d => <option key={d} value={d}>the last {d} days</option>)}</select>
      </div>
      <ErrorLine error={error} fallback="Could not work out the suggestions" />
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : !data || data.suggestions.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No moves suggested. Either the busy items are already near the front, or there is no empty location nearer to move them to.</p>
        : data.suggestions.map(s => <Row key={`${s.itemId}-${s.toLocationId}`} s={s} canWork={canWork} onDone={refresh} />)}
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", marginTop: 12 }}>
        Based on pick tasks done in the period. Suggestions only use empty locations, so a move never has to swap two items. Items stored as several lots in one place are left alone.
      </p>
    </div>
  )
}
