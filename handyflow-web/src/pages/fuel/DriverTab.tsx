// Stage 3 / C05: the driver's own loads, built for a phone. Starting a trip, recording a delivery and recording a
// location made without signal are kept on the device, each with its own request id, and sent in order when the
// connection returns. The server treats a repeat as the same record. A refused item stays visible to retry or discard.
import { useCallback, useEffect, useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnPrimary, btnGhost, card, inputStyle, labelStyle } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { enqueue, readQueue, flushQueue, remove, retry, isNetworkError, type QueueItem } from "./offlineQueue"

const unwrap = (r: any) => r.data?.data ?? r.data
const newId = () => (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`)
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

export default function DriverTab() {
  const qc = useQueryClient()
  const [completing, setCompleting] = useState<string | null>(null)
  const [litres, setLitres] = useState(""); const [receiver, setReceiver] = useState(""); const [meterEnd, setMeterEnd] = useState("")
  const [err, setErr] = useState(""); const [note, setNote] = useState("")
  const [pending, setPending] = useState<QueueItem[]>(() => readQueue())
  const loads = useQuery<any[]>({ queryKey: ["fuel-my-loads"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/driver/loads")) })

  const send = useCallback((i: QueueItem) => apiClient.post(i.path, i.body ?? undefined), [])
  // A start that was refused is fine if the load is already on the road (a repeat, or the office dispatched it).
  const alreadyDone = useCallback(async (i: QueueItem) => {
    if (i.kind !== "START") return false
    const l = unwrap(await apiClient.get("/api/v1/fuel/driver/loads")) as any[]
    const mine = l.find(x => x.id === i.deliveryId)
    return !mine || mine.status !== "SCHEDULED"
  }, [])
  const flush = useCallback(async () => {
    if (readQueue().filter(i => i.status === "PENDING").length === 0) return
    const r = await flushQueue(send, localStorage, alreadyDone)
    setPending(readQueue())
    if (r.sent.length) { setNote(`${r.sent.length} saved item${r.sent.length === 1 ? "" : "s"} sent.`); qc.invalidateQueries({ queryKey: ["fuel-my-loads"] }) }
  }, [send, alreadyDone, qc])

  useEffect(() => { flush(); window.addEventListener("online", flush); return () => window.removeEventListener("online", flush) }, [flush])

  /** Tries now. With no signal the action is saved on the phone; a refusal from the server is shown and not saved. */
  const act = async (kind: QueueItem["kind"], deliveryId: string, path: string, body: Record<string, unknown> | null, label: string, done: string) => {
    setErr(""); setNote("")
    const id = (body?.clientRequestId as string | undefined) ?? newId()
    try { await apiClient.post(path, body ?? undefined); setNote(done); qc.invalidateQueries({ queryKey: ["fuel-my-loads"] }); return true }
    catch (e: any) {
      if (isNetworkError(e)) { enqueue({ id, kind, deliveryId, path, body, label }); setPending(readQueue()); setNote(`No signal. ${label} saved on this phone: it will be sent automatically when you are back online.`); return true }
      setErr(e.response?.data?.message ?? `Could not record: ${label.toLowerCase()}`); return false
    }
  }

  const start = (id: string) => act("START", id, `/api/v1/fuel/driver/loads/${id}/dispatch`, null, "Start trip", "Trip started.")

  const complete = async (id: string) => {
    const n = Number(litres)
    if (!litres.trim() || Number.isNaN(n) || n <= 0) return setErr("Enter the litres delivered.")
    if (!receiver.trim()) return setErr("Enter the name of the person who received the fuel.")
    const body = { litresDelivered: n, receiverName: receiver.trim(), meterReadingEnd: meterEnd.trim() ? Number(meterEnd) : null, signedOnBehalf: false, clientRequestId: newId() }
    const ok = await act("COMPLETE", id, `/api/v1/fuel/driver/loads/${id}/complete`, body, "Delivery", "Delivery recorded.")
    if (ok) reset()
  }

  /** Records where the phone is now. A photo or signature needs file upload, which is not built yet. */
  const recordLocation = (id: string) => {
    setErr(""); setNote("")
    if (!("geolocation" in navigator)) return setErr("This phone cannot give its location.")
    navigator.geolocation.getCurrentPosition(
      p => { void act("EVIDENCE", id, `/api/v1/fuel/driver/loads/${id}/evidence`,
        { kind: "GPS", latitude: Number(p.coords.latitude.toFixed(6)), longitude: Number(p.coords.longitude.toFixed(6)), accuracyM: Math.round(p.coords.accuracy), clientRequestId: newId() }, "Location", "Location recorded.") },
      () => setErr("Could not read the phone's location. Check that location is switched on for this site."),
      { enableHighAccuracy: true, timeout: 15000 })
  }

  const discard = (id: string) => { remove(id); setPending(readQueue()) }
  const again = (id: string) => { retry(id); setPending(readQueue()); void flush() }
  const reset = () => { setCompleting(null); setLitres(""); setReceiver(""); setMeterEnd("") }
  const queuedKinds = (id: string) => new Set(pending.filter(p => p.deliveryId === id).map(p => p.kind))

  return (
    <div style={{ maxWidth: 520 }}>
      {pending.length > 0 && (
        <div style={{ ...card, padding: 10, fontSize: 13, marginBottom: 12 }} aria-label="Saved on this phone">
          <div style={{ fontWeight: 700, marginBottom: 4 }}>Saved on this phone ({pending.length})</div>
          {pending.map(p => (
            <div key={p.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "4px 0" }}>
              <span style={{ flex: 1 }}>{p.label} · {when(p.queuedAt)} · {p.status === "FAILED" ? <span style={{ color: "var(--hf-danger-text)" }}>Refused: {p.message}</span> : "Waiting to send"}</span>
              {p.status === "FAILED" && <><button style={btnGhost} onClick={() => again(p.id)}>Retry</button><button style={btnGhost} onClick={() => discard(p.id)}>Discard</button></>}
            </div>
          ))}
        </div>
      )}
      {note && <p role="status" style={{ color: "var(--hf-success-text-strong)", fontSize: 13 }}>{note}</p>}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</p>}
      {loads.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : loads.isError ? <p style={{ color: "var(--hf-text-muted)" }}>Could not load your loads. They will appear when you are connected.</p>
        : (loads.data ?? []).length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No loads allocated to you.</p>
        : (loads.data ?? []).map(l => (
          <div key={l.id} style={{ ...card, padding: 14, marginBottom: 12 }}>
            <div style={{ fontWeight: 700 }}>{fmtL(l.litresOrdered)} {l.fuelType}</div>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{when(l.scheduledAt)} · {l.vehicleReg ?? ""}{l.deliveryAddress?.street ? ` · ${l.deliveryAddress.street}` : ""}</div>
            <div style={{ fontSize: 13, margin: "4px 0 8px" }}>{l.status === "IN_TRANSIT" ? "On the road" : "Ready to go"}{queuedKinds(l.id).has("COMPLETE") ? " · delivery saved, waiting to send" : ""}</div>
            {l.status === "SCHEDULED" && !queuedKinds(l.id).has("START") && <button style={{ ...btnPrimary, width: "100%", padding: 12 }} onClick={() => start(l.id)}>Start trip</button>}
            {l.status === "IN_TRANSIT" && !queuedKinds(l.id).has("COMPLETE") && completing !== l.id && <button style={{ ...btnPrimary, width: "100%", padding: 12 }} onClick={() => { setCompleting(l.id); setLitres(String(l.litresOrdered)) }}>Record delivery</button>}
            {l.status === "IN_TRANSIT" && completing !== l.id && <button style={{ ...btnGhost, width: "100%", padding: 12, marginTop: 8 }} onClick={() => recordLocation(l.id)}>Record location</button>}
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
