// src/pages/warehousing/WhseScanTab.tsx
//
// A lookup for the floor: scan (or type) a location, item barcode or SKU, lot, ASN, order or manifest and see what it is and where the
// stock is. Built for a phone or a handheld: one big input that keeps focus, a scanner's Enter key submits, large tap targets.
// Several things can share a code (the same SKU for two clients), so the screen lists the matches and asks which one.
import { useEffect, useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ScanBarcode } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT_TEXT } from "./constants"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle } from "./whseUi"

export interface ScanMatch { type: "LOCATION" | "ITEM" | "LOT" | "SERIAL" | "INBOUND" | "OUTBOUND" | "MANIFEST"; id: string; label: string; detail: string | null; clientId: string | null }
interface StockLine {
  locationId: string; locationCode: string; itemId: string; sku: string; description: string; clientName: string
  lotNumber: string | null; expiryDate: string | null; onHand: number; allocated: number; status: string
}

const TYPE_LABEL: Record<ScanMatch["type"], string> = {
  LOCATION: "Location", ITEM: "Item", LOT: "Lot", SERIAL: "Serial number", INBOUND: "Inbound shipment", OUTBOUND: "Outbound order", MANIFEST: "Dispatch manifest",
}
const STOCK_PATH: Partial<Record<ScanMatch["type"], string>> = { LOCATION: "locations", ITEM: "items", LOT: "lots" }
const num = (n: number) => n.toLocaleString("en-ZA", { maximumFractionDigits: 3 })

function StockList({ match }: { match: ScanMatch }) {
  const { data, isLoading, error } = useQuery<StockLine[]>({
    queryKey: ["whse-scan-stock", match.type, match.id],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/scan/${STOCK_PATH[match.type]}/${match.id}/stock`)).data,
  })
  if (isLoading) return <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading stock…</p>
  if (error) return <ErrorLine error={error} fallback="Could not load the stock" />
  if (!data || data.length === 0) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "6px 0 0" }}>No stock here.</p>
  return (
    <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
      {data.map((s, i) => (
        <div key={i} style={{ ...card, padding: 12, fontSize: 13 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
            <strong>{match.type === "LOCATION" ? s.sku : s.locationCode}</strong>
            <span style={{ fontWeight: 700 }}>{num(s.onHand)}{s.allocated > 0 ? ` (${num(s.allocated)} allocated)` : ""}</span>
          </div>
          <div style={{ color: "var(--hf-text-muted)", fontSize: 12 }}>
            {match.type === "LOCATION" ? s.description : `${s.sku} · ${s.description}`} · {s.clientName}
            {s.lotNumber ? ` · lot ${s.lotNumber}` : ""}{s.expiryDate ? ` · expires ${s.expiryDate}` : ""}{s.status !== "AVAILABLE" ? ` · ${s.status.toLowerCase()}` : ""}
          </div>
        </div>
      ))}
    </div>
  )
}

interface Trace { id: string; serialNumber: string; status: string; clientName: string; sku: string; description: string; orderReference: string | null; shippedAt: string | null; returnCount: number; history: { event: string; reference: string | null; at: string }[] }

function SerialTrace({ match }: { match: ScanMatch }) {
  const { data, isLoading, error } = useQuery<Trace>({
    queryKey: ["whse-serial-trace", match.id],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/serials/${match.id}`)).data,
  })
  if (isLoading) return <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading history…</p>
  if (error || !data) return <ErrorLine error={error} fallback="Could not load this serial" />
  return (
    <div style={{ marginTop: 8, fontSize: 13 }}>
      <div>{data.sku} · {data.description} · {data.clientName}</div>
      <div style={{ color: "var(--hf-text-muted)" }}>
        {data.status.toLowerCase().replace("_", " ")}{data.orderReference ? ` on ${data.orderReference}` : ""}{data.returnCount > 0 ? ` · returned ${data.returnCount} time${data.returnCount === 1 ? "" : "s"}` : ""}
      </div>
      <ol style={{ margin: "8px 0 0", paddingLeft: 18 }}>
        {data.history.map((h, i) => <li key={i}>{h.event.toLowerCase()} · {new Date(h.at).toLocaleString("en-ZA")}</li>)}
      </ol>
    </div>
  )
}

function LocationBarcode({ match }: { match: ScanMatch }) {
  const qc = useQueryClient()
  const [value, setValue] = useState("")
  const save = useMutation({
    mutationFn: async (barcode: string) => apiClient.put(`/api/v1/warehousing/scan/locations/${match.id}/barcode`, { barcode }),
    onSuccess: () => { setValue(""); qc.invalidateQueries({ queryKey: ["whse-locations"] }) },
  })
  return (
    <div style={{ marginTop: 12 }}>
      <label htmlFor="loc-barcode" style={labelStyle}>Label barcode for {match.label} (its code scans too)</label>
      <div style={{ display: "flex", gap: 8 }}>
        <input id="loc-barcode" style={{ ...inputStyle, flex: 1 }} value={value} onChange={e => setValue(e.target.value)} placeholder="Scan the label to set it" />
        <button style={btnGhost} disabled={save.isPending || value.trim() === ""} onClick={() => save.mutate(value)}>Save barcode</button>
        <button style={btnGhost} disabled={save.isPending} onClick={() => save.mutate("")}>Clear</button>
      </div>
      <ErrorLine error={save.error} fallback="Could not save the barcode" />
      {save.isSuccess && <p role="status" style={{ fontSize: 12, color: "var(--hf-success-text-strong)", margin: "6px 0 0" }}>Barcode saved.</p>}
    </div>
  )
}

export default function WhseScanTab() {
  const input = useRef<HTMLInputElement>(null)
  const [code, setCode] = useState("")
  const [asked, setAsked] = useState<string | null>(null)
  const [picked, setPicked] = useState<ScanMatch | null>(null)

  const lookup = useMutation({
    mutationFn: async (c: string) => (await apiClient.get("/api/v1/warehousing/scan/resolve", { params: { code: c } })).data as ScanMatch[],
    onSuccess: (m, c) => { setAsked(c); setPicked(m.length === 1 ? m[0] : null) },
  })
  useEffect(() => { input.current?.focus() }, [])

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const c = code.trim()
    if (!c) return
    setPicked(null)
    lookup.mutate(c, { onSettled: () => { setCode(""); input.current?.focus() } })
  }
  const matches = lookup.data ?? []

  return (
    <div style={{ maxWidth: 560, margin: "0 auto" }}>
      <form onSubmit={submit} style={{ display: "flex", gap: 8 }}>
        <input ref={input} aria-label="Scan or type a code" value={code} onChange={e => setCode(e.target.value)} autoComplete="off" autoCapitalize="characters" inputMode="text"
          placeholder="Scan a location, item, lot, order…" style={{ ...inputStyle, flex: 1, fontSize: 18, padding: "14px 14px" }} />
        <button type="submit" style={{ ...btnPrimary, minWidth: 56, justifyContent: "center", fontSize: 14 }} disabled={lookup.isPending}><ScanBarcode size={18} aria-hidden /> Find</button>
      </form>
      <ErrorLine error={lookup.error} fallback="Could not look that up" />

      {asked && !lookup.isPending && matches.length === 0 && !lookup.error && (
        <p role="status" style={{ fontSize: 14, color: "var(--hf-text-muted)", marginTop: 14 }}>Nothing matches “{asked}”. Check the label, or the item may not have a barcode yet.</p>
      )}

      {matches.length > 1 && !picked && (
        <div style={{ marginTop: 14 }}>
          <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 8px" }}>“{asked}” could be more than one thing. Which one?</p>
          <div style={{ display: "grid", gap: 8 }}>
            {matches.map(m => (
              <button key={`${m.type}-${m.id}`} onClick={() => setPicked(m)} style={{ ...card, padding: 14, textAlign: "left", cursor: "pointer", minHeight: 56 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: WHSE_ACCENT_TEXT }}>{TYPE_LABEL[m.type].toUpperCase()}</span>
                <div style={{ fontSize: 14, fontWeight: 700 }}>{m.label}</div>
                {m.detail && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{m.detail}</div>}
              </button>
            ))}
          </div>
        </div>
      )}

      {picked && (
        <div style={{ ...card, padding: 16, marginTop: 14 }}>
          <span style={{ fontSize: 11, fontWeight: 700, color: WHSE_ACCENT_TEXT }}>{TYPE_LABEL[picked.type].toUpperCase()}</span>
          <div style={{ fontSize: 18, fontWeight: 800 }}>{picked.label}</div>
          {picked.detail && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{picked.detail}</div>}
          {STOCK_PATH[picked.type] && <StockList match={picked} />}
          {picked.type === "SERIAL" && <SerialTrace match={picked} />}
          {picked.type === "LOCATION" && <LocationBarcode match={picked} />}
          {matches.length > 1 && <button style={{ ...btnGhost, marginTop: 12 }} onClick={() => setPicked(null)}>Back to the matches</button>}
        </div>
      )}
    </div>
  )
}
