// src/pages/agriculture/AgStockImport.tsx
//
// Bring stock items in from a spreadsheet. Choose a CSV, the screen reads it and says what it could not, "Check file" sends it to
// POST /api/v1/agriculture/farms/{id}/inventory-items/import with dryRun (nothing is created), and "Import" does it for real. The server
// checks every row again. An item name already on the farm is skipped, never overwritten. An opening quantity is posted as a receipt.
import { useState } from "react"
import { Download, Upload } from "lucide-react"
import { useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { useAgToastMutation } from "./agMutation"
import AgManage from "./AgManage"
import { btnGhost, btnPrimary } from "./constants"
import {
  readStockCsv, stockDoneMessage, stockImportButtonLabel, stockResultSummary, stockRowsNeedingAttention, STOCK_TEMPLATE_CSV,
  type ReadStockFile, type StockImportResult, type StockImportRow,
} from "./agStockImport.logic"

export default function AgStockImport({ farmId, onClose }: { farmId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [fileName, setFileName] = useState("")
  const [read, setRead] = useState<ReadStockFile | null>(null)
  const [result, setResult] = useState<StockImportResult | null>(null)

  const send = useAgToastMutation({
    mutationFn: async (v: { rows: StockImportRow[]; dryRun: boolean }) =>
      (await apiClient.post<StockImportResult>(`/api/v1/agriculture/farms/${farmId}/inventory-items/import`, v)).data,
    onSuccess: (r, v) => {
      if (v.dryRun) { setResult(r); return }
      toast.success(stockDoneMessage(r))
      qc.invalidateQueries({ queryKey: ["ag-inventory", farmId] })
      onClose()
    },
  })

  const choose = async (file: File | undefined) => {
    setResult(null)
    if (!file) { setRead(null); setFileName(""); return }
    setFileName(file.name)
    setRead(readStockCsv(await file.text()))
  }

  const downloadTemplate = () => {
    const url = URL.createObjectURL(new Blob([STOCK_TEMPLATE_CSV], { type: "text/csv" }))
    const a = document.createElement("a")
    a.href = url; a.download = "stock-template.csv"; a.click()
    URL.revokeObjectURL(url)
  }

  const canCheck = !!read && read.problems.length === 0 && read.rows.length > 0
  const label = stockImportButtonLabel(result)
  const attention = result ? stockRowsNeedingAttention(result) : []

  return (
    <AgManage>
    <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
      <p style={{ fontSize: 13, color: "var(--hf-text)", fontWeight: 700, margin: "0 0 4px" }}>Import stock items from a spreadsheet</p>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>
        Save your sheet as CSV. Needed columns: Item name, Category (feed, seed, fertiliser, chemical, veterinary or other), Unit (kg, g, t, L, mL, bags, bales, crates, units, doses, head).
        Optional: Quantity (opening stock), Unit cost, Reorder level, Supplier, Kg per bag (bags and bales only). An item already on this farm, by name, is skipped, never changed.
      </p>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 10 }}>
        <label style={{ ...btnGhost, display: "inline-flex", alignItems: "center", cursor: "pointer" }}>
          <Upload size={13} style={{ marginRight: 5 }} />Choose CSV file
          <input type="file" accept=".csv,.txt,text/csv" onChange={e => void choose(e.target.files?.[0])} style={{ display: "none" }} aria-label="CSV file" />
        </label>
        <button onClick={downloadTemplate} style={btnGhost}><Download size={13} style={{ marginRight: 5, verticalAlign: -2 }} />Download template</button>
        {fileName && <span style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>{fileName}{read && read.problems.length === 0 ? ` · ${read.rows.length} rows` : ""}</span>}
      </div>

      {read && read.problems.map(p => <p key={p} role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "0 0 6px" }}>{p}</p>)}
      {read && read.ignored.length > 0 && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "0 0 6px" }}>Not used: {read.ignored.join(", ")}.</p>}

      {result && (
        <div style={{ marginBottom: 10 }}>
          <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 6px" }}>{stockResultSummary(result)}</p>
          {attention.length > 0 && (
            <div style={{ border: "1px solid var(--hf-border)", borderRadius: 8, overflow: "hidden", maxHeight: 240, overflowY: "auto", background: "var(--hf-surface)" }}>
              {attention.map((r, i) => (
                <div key={r.line} style={{ display: "flex", gap: 10, padding: "6px 10px", fontSize: 12.5, borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                  <span style={{ width: 54, color: "var(--hf-text-faint)", flexShrink: 0 }}>Row {r.line}</span>
                  <span style={{ width: 140, fontWeight: 600, color: "var(--hf-text)", flexShrink: 0, overflow: "hidden", textOverflow: "ellipsis" }}>{r.itemName || "(no name)"}</span>
                  <span style={{ color: r.status === "ERROR" ? "var(--hf-danger-text)" : "var(--hf-warning-text)" }}>{r.message}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div style={{ display: "flex", gap: 8 }}>
        <button disabled={!canCheck || send.isPending} onClick={() => read && send.mutate({ rows: read.rows, dryRun: true })}
          style={{ ...(label ? btnGhost : btnPrimary), opacity: !canCheck || send.isPending ? 0.6 : 1 }}>
          {send.isPending && send.variables?.dryRun ? "Checking…" : result ? "Check again" : "Check file"}
        </button>
        {label && read && (
          <button disabled={send.isPending} onClick={() => send.mutate({ rows: read.rows, dryRun: false })} style={{ ...btnPrimary, opacity: send.isPending ? 0.6 : 1 }}>
            {send.isPending && !send.variables?.dryRun ? "Importing…" : label}
          </button>
        )}
        <button onClick={onClose} style={btnGhost}>Close</button>
      </div>
    </div>
    </AgManage>
  )
}
