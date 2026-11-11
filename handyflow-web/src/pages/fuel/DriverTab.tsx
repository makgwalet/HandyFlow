// Stage 3: the driver's own loads, built for a phone. Completions made without signal are kept on the
// device with one request id and sent when the connection returns; the server treats a repeat as the same delivery.
import { useCallback, useEffect, useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnPrimary, btnGhost, card, inputStyle, labelStyle } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { enqueue, readQueue, flushQueue, isNetworkError, type QueuedCompletion } from "./transportOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const newId = () => (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`)
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

export default function DriverTab() {
  const qc = useQueryClient()
  const [completing, setCompleting] = useState<string | null>(null)
  const [litres, setLitres] = useState(""); const [receiver, setReceiver] = useState(""); const [meterEnd, setMeterEnd] = useState("")
  const [err, setErr] = useState(""); const [note, setNote] = useState("")
  const [pending, setPending] = useState<QueuedCompletion[]>(() => readQueue())
  const loads = useQuery<any[]>({ queryKey: ["fuel-my-loads"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/driver/loads")) })

  const send = useCallback((i: QueuedCompletion) => apiClient.post(`/api/v1/fuel/driver/loads/${i.deliveryId}/complete`, i.body), [])
  const flush = useCallback(async () => {
    if (readQueue().length === 0) return
    const r = await flushQueue(send)
    setPending(readQueue())
    if (r.sent.length) { setNote(`${r.sent.length} saved delivery${r.sent.length === 1 ? "" : "ies"} sent.`); qc.invalidateQueries({ queryKey: ["fuel-my-loads"] }) }
    if (r.refused.length) setErr(r.refused.map(x => x.message).join(" "))
  }, [send, qc])

  useEffect(() => { flush(); window.addEventListener("online", flush); return () => window.removeEventListener("online", flush) }, [flush])

  const start = async (id: string) => {
    setErr("")
    try { await apiClient.post(`/api/v1/fuel/driver/loads/${id}/dispatch`); qc.invalidateQueries({ queryKey: ["fuel-my-loads"] }) }
    catch (e: any) { setErr(isNetworkError(e) ? "No signal. Try again when you are connected." : (e.response?.data?.message ?? "Could not start the load")) }
  }

  const complete = async (id: string) => {
    const n = Number(litres)
    if (!litres.trim() || Number.isNaN(n) || n <= 0) return setErr("Enter the litres delivered.")
    if (!receiver.trim()) return setErr("Enter the name of the person who received the fuel.")
    setErr("")
    const requestId = newId()
    const body = { litresDelivered: n, receiverName: receiver.trim(), meterReadingEnd: meterEnd.trim() ? Number(meterEnd) : null, signedOnBehalf: false }
    try {
      await apiClient.post(`/api/v1/fuel/driver/loads/${id}/complete`, { ...body, clientRequestId: requestId })
      setNote("Delivery recorded."); reset(); qc.invalidateQueries({ queryKey: ["fuel-my-loads"] })
    } catch (e: any) {
      if (isNetworkError(e)) { enqueue(id, body, requestId); setPending(readQueue()); setNote("No signal. Saved on this phone: it will be sent automatically when you are back online."); reset() }
      else setErr(e.response?.data?.message ?? "Could not record the delivery")
    }
  }
  const reset = () => { setCompleting(null); setLitres(""); setReceiver(""); setMeterEnd("") }
  const queued = new Set(pending.map(p => p.deliveryId))

  return (
    <div style={{ maxWidth: 520 }}>
      {pending.length > 0 && <p role="status" style={{ ...card, padding: 10, fontSize: 13 }}>{pending.length} delivery{pending.length === 1 ? "" : "ies"} waiting to be sent from this phone.</p>}
      {note && <p role="status" style={{ color: "var(--hf-success-text-strong)", fontSize: 13 }}>{note}</p>}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</p>}
      {loads.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : loads.isError ? <p style={{ color: "var(--hf-text-muted)" }}>Could not load your loads. They will appear when you are connected.</p>
        : (loads.data ?? []).length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No loads allocated to you.</p>
        : (loads.data ?? []).map(l => (
          <div key={l.id} style={{ ...card, padding: 14, marginBottom: 12 }}>
            <div style={{ fontWeight: 700 }}>{fmtL(l.litresOrdered)} {l.fuelType}</div>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{when(l.scheduledAt)} · {l.vehicleReg ?? ""}{l.deliveryAddress?.street ? ` · ${l.deliveryAddress.street}` : ""}</div>
            <div style={{ fontSize: 13, margin: "4px 0 8px" }}>{l.status === "IN_TRANSIT" ? "On the road" : "Ready to go"}{queued.has(l.id) ? " · delivery saved, waiting to send" : ""}</div>
            {l.status === "SCHEDULED" && !queued.has(l.id) && <button style={{ ...btnPrimary, width: "100%", padding: 12 }} onClick={() => start(l.id)}>Start trip</button>}
            {l.status === "IN_TRANSIT" && !queued.has(l.id) && completing !== l.id && <button style={{ ...btnPrimary, width: "100%", padding: 12 }} onClick={() => { setCompleting(l.id); setLitres(String(l.litresOrdered)) }}>Record delivery</button>}
            {completing === l.id && (
              <div>
                <label style={labelStyle}>Litres delivered *</label><input aria-label="Litres delivered" type="number" inputMode="decimal" value={litres} onChange={e => setLitres(e.target.value)} style={{ ...inputStyle, fontSize: 16 }} />
                <label style={{ ...labelStyle, marginTop: 8 }}>Received by *</label><input aria-label="Received by" value={receiver} onChange={e => setReceiver(e.target.value)} style={{ ...inputStyle, fontSize: 16 }} />
                <label style={{ ...labelStyle, marginTop: 8 }}>Meter reading at end</label><input aria-label="Meter end" type="number" inputMode="decimal" value={meterEnd} onChange={e => setMeterEnd(e.target.value)} style={{ ...inputStyle, fontSize: 16 }} />
                <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
                  <button style={{ ...btnGhost, flex: 1, padding: 12 }} onClick={reset}>Back</button>
                  <button style={{ ...btnPrimary, flex: 2, padding: 12 }} onClick={() => complete(l.id)}>Confirm delivery</button>
                </div>
              </div>
            )}
          </div>
        ))}
    </div>
  )
}
