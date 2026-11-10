// src/pages/warehousing/WhseSerialPanel.tsx
//
// Serial numbers on the floor. SerialEntry is the box on the receive and return forms (one serial per unit, typed, pasted or scanned);
// SerialPanel is the order's own panel for recording which serials go on each serial-tracked line while it is being picked.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import { btnGhost, card, ErrorLine, inputStyle, labelStyle } from "./whseUi"
import { parseSerials } from "./receiving"

/** A text box for the serials of a receipt or return, with a running count against the units. */
export function SerialEntry({ value, onChange, units }: { value: string; onChange: (v: string) => void; units: number }) {
  const have = parseSerials(value).length
  return (
    <div>
      <label style={labelStyle} htmlFor="serial-entry">Serial numbers *</label>
      <textarea id="serial-entry" aria-label="Serial numbers" rows={3} style={{ ...inputStyle, width: "100%", fontFamily: "ui-monospace, monospace" }} value={value}
        onChange={e => onChange(e.target.value)} placeholder="One per line. Scan each, or paste a list." />
      <span style={{ fontSize: 11.5, color: have === units && units > 0 ? "var(--hf-success-text-strong)" : "var(--hf-text-faint)" }}>{have} of {units} unit{units === 1 ? "" : "s"}</span>
    </div>
  )
}

interface OrderLine { lineId: string; itemId: string; sku: string; description: string; toShip: number; serials: string[] }

function LineBox({ orderId, line, editable }: { orderId: string; line: OrderLine; editable: boolean }) {
  const qc = useQueryClient()
  const [text, setText] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["whse-order-serials", orderId] })
  const add = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/warehousing/serials/pick", { orderId, lineId: line.lineId, serials: parseSerials(text) }),
    onSuccess: () => { setText(""); refresh() },
  })
  const remove = useMutation({
    mutationFn: async (serial: string) => apiClient.post("/api/v1/warehousing/serials/unpick", { orderId, lineId: line.lineId, serials: [serial] }),
    onSuccess: refresh,
  })
  const done = line.serials.length >= line.toShip
  return (
    <div style={{ padding: "10px 14px", borderTop: "1px solid var(--hf-border-subtle)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13 }}>
        <strong>{line.sku}</strong>
        <span style={{ color: done ? "var(--hf-success-text-strong)" : "var(--hf-warning-text-deep)", fontWeight: 600 }}>{line.serials.length} of {line.toShip} recorded</span>
      </div>
      {line.serials.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, margin: "8px 0" }}>
          {line.serials.map(s => (
            <span key={s} style={{ display: "inline-flex", alignItems: "center", gap: 4, background: "var(--hf-surface-muted)", borderRadius: 6, padding: "3px 8px", fontSize: 12, fontFamily: "ui-monospace, monospace" }}>
              {s}
              {editable && <button aria-label={`Remove ${s}`} onClick={() => remove.mutate(s)} disabled={remove.isPending} style={{ background: "none", border: "none", cursor: "pointer", padding: 0, display: "flex" }}><X size={12} /></button>}
            </span>
          ))}
        </div>
      )}
      {editable && !done && (
        <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
          <textarea aria-label={`Serials for ${line.sku}`} rows={2} style={{ ...inputStyle, flex: 1, fontFamily: "ui-monospace, monospace" }} value={text} onChange={e => setText(e.target.value)} placeholder="Scan or paste serials" />
          <button style={{ ...btnGhost, alignSelf: "flex-start" }} disabled={add.isPending || parseSerials(text).length === 0} onClick={() => add.mutate()}>Add</button>
        </div>
      )}
      <ErrorLine error={add.error ?? remove.error} fallback="Could not record the serials" />
    </div>
  )
}

/** Shown on an order only when it has serial-tracked lines. Serials can be changed while the order is being picked; the order cannot ship until each unit has one. */
export default function SerialPanel({ orderId, status }: { orderId: string; status: string }) {
  const { data: lines = [] } = useQuery<OrderLine[]>({
    queryKey: ["whse-order-serials", orderId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/serials/orders/${orderId}`)).data,
  })
  if (lines.length === 0) return null
  return (
    <div style={{ ...card, marginTop: 16, overflow: "hidden" }}>
      <p style={{ fontSize: 13, fontWeight: 700, margin: 0, padding: "12px 14px" }}>Serial numbers</p>
      {status === "PICKING" && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "0 14px 8px" }}>Record a serial for every unit. The order cannot ship until each line is complete.</p>}
      {lines.map(l => <LineBox key={l.lineId} orderId={orderId} line={l} editable={status === "PICKING"} />)}
    </div>
  )
}
