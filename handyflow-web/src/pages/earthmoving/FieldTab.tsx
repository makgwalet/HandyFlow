// src/pages/earthmoving/FieldTab.tsx
//
// Field capture for a phone. An entry is saved on the phone the moment it is typed and sent when there is a connection, so a signal drop on site loses
// nothing. Each entry shows pending, synced, rejected or conflict. The server re-checks every entry when it arrives and never overwrites a newer reading:
// a clash is kept beside it and sent for review. Deploying, releasing a machine from maintenance and approving a disposal are not captured here because
// they need a fresh check against the live record: do them while connected.
import { useCallback, useEffect, useState } from "react"
import { Smartphone } from "lucide-react"
import { apiMessage, fmtDateTime } from "./shared/format"
import { ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useAssets, useFieldConflicts, useReviewFieldConflict } from "./shared/hooks"
import { syncFieldEntries } from "./shared/api"
import {
  loadQueue, saveQueue, makeEntry, entryProblem, syncPending, discard, pendingOf,
  type QueuedEntry, type FieldKind, type QueueState,
} from "./shared/offlineQueue"

const CACHE_KEY = "hf-em-field-machines"
interface MachineOption { id: string; name: string }

const STATE_LABEL: Record<QueueState, string> = { PENDING: "Waiting to send", SYNCED: "Sent", REJECTED: "Refused", CONFLICT: "Needs review" }
const STATE_COLOR: Record<QueueState, string> = { PENDING: "var(--hf-warning)", SYNCED: "var(--hf-success)", REJECTED: "var(--hf-danger)", CONFLICT: "var(--hf-orange-text)" }

const btn = (primary = false): React.CSSProperties => ({
  padding: "10px 16px", borderRadius: 10, fontSize: 15, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }

function readCache(): MachineOption[] {
  try {
    const v: unknown = JSON.parse(localStorage.getItem(CACHE_KEY) ?? "[]")
    return Array.isArray(v) ? (v as MachineOption[]).filter(m => typeof m?.id === "string" && typeof m?.name === "string") : []
  } catch { return [] }
}

export default function FieldTab() {
  const { canWrite, canMaintain } = useEarthPerms()
  const assets = useAssets()
  const conflicts = useFieldConflicts(canMaintain)
  const review = useReviewFieldConflict()

  const [queue, setQueue] = useState<QueuedEntry[]>(() => loadQueue())
  const [online, setOnline] = useState(() => (typeof navigator === "undefined" ? true : navigator.onLine))
  const [sending, setSending] = useState(false)
  const [kind, setKind] = useState<FieldKind>("METER_READING")
  const [assetId, setAssetId] = useState("")
  const [meter, setMeter] = useState("")
  const [lat, setLat] = useState("")
  const [lon, setLon] = useState("")
  const [note, setNote] = useState("")
  const [err, setErr] = useState("")
  const [info, setInfo] = useState("")

  // Remember the machine list so the form still works with no signal.
  const fresh: MachineOption[] = (assets.data ?? []).filter(a => a.status !== "RETIRED").map(a => ({ id: a.id, name: `${a.fleetNumber ? a.fleetNumber + " · " : ""}${a.name}` }))
  useEffect(() => {
    if (fresh.length > 0) { try { localStorage.setItem(CACHE_KEY, JSON.stringify(fresh)) } catch { /* the phone would not keep it: the list just won't work offline */ } }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [assets.data])
  const machines = fresh.length > 0 ? fresh : readCache()

  const persist = useCallback((next: QueuedEntry[]) => {
    setQueue(next)
    if (!saveQueue(next)) setErr("This phone would not keep the entry. Free up space or send what is waiting now.")
  }, [])

  const sendNow = useCallback(async (current: QueuedEntry[]) => {
    if (pendingOf(current).length === 0) return
    setSending(true)
    setInfo("")
    const r = await syncPending(current, batch => syncFieldEntries(batch.map(e => ({
      submissionId: e.submissionId, kind: e.kind, assetId: e.assetId, capturedAt: e.capturedAt,
      meterValue: e.meterValue, latitude: e.latitude, longitude: e.longitude, note: e.note,
    }))))
    persist(r.entries)
    setSending(false)
    if (r.error) {
      if (r.offline) setInfo("No connection. Your entries are saved on this phone and will be sent when it is back.")
      else setErr(apiMessage(r.error, "The server did not accept the entries. They are still saved on this phone."))
    }
  }, [persist])

  useEffect(() => {
    const on = () => { setOnline(true); void sendNow(loadQueue()) }
    const off = () => setOnline(false)
    window.addEventListener("online", on)
    window.addEventListener("offline", off)
    return () => { window.removeEventListener("online", on); window.removeEventListener("offline", off) }
  }, [sendNow])

  const useMyLocation = () => {
    setErr("")
    if (!navigator.geolocation) { setErr("This phone cannot give its location."); return }
    navigator.geolocation.getCurrentPosition(
      p => { setLat(p.coords.latitude.toFixed(6)); setLon(p.coords.longitude.toFixed(6)) },
      () => setErr("Could not get the location. Allow location access, or type the position."),
    )
  }

  const save = () => {
    setErr(""); setInfo("")
    const name = machines.find(m => m.id === assetId)?.name ?? ""
    const entry = {
      kind, assetId, assetName: name, note,
      meterValue: meter.trim() === "" ? null : Number(meter),
      latitude: lat.trim() === "" ? null : Number(lat), longitude: lon.trim() === "" ? null : Number(lon),
    }
    const problem = entryProblem(entry)
    if (problem) { setErr(problem); return }
    const next = [...queue, makeEntry(entry)]
    persist(next)
    setMeter(""); setLat(""); setLon(""); setNote("")
    if (navigator.onLine) void sendNow(next)
    else setInfo("Saved on this phone. It will be sent when there is a connection.")
  }

  const pending = pendingOf(queue)
  const shown = [...queue].reverse()

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 560 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, justifyContent: "space-between", flexWrap: "wrap" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Smartphone size={18} aria-hidden />
          <h2 style={{ margin: 0, fontSize: 18 }}>Field capture</h2>
        </div>
        <span role="status" style={{ fontSize: 13, fontWeight: 600, color: online ? "var(--hf-success)" : "var(--hf-warning)" }}>
          {online ? "Connected" : "Offline"}{pending.length > 0 ? ` · ${pending.length} waiting to send` : ""}
        </span>
      </div>
      <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>
        Entries are saved on this phone and sent when there is signal. Deploying a machine, releasing it from maintenance and approving a disposal need a live check, so do those while connected.
      </p>

      {err && <ErrBanner msg={err} />}
      {info && <p role="status" style={{ margin: 0, fontSize: 13 }}>{info}</p>}

      {canWrite ? (
        <section style={{ ...card, display: "flex", flexDirection: "column", gap: 10 }} aria-label="New entry">
          <div>
            <label style={lbl} htmlFor="fd-kind">What are you recording?</label>
            <select id="fd-kind" style={{ ...inputStyle(), fontSize: 16 }} value={kind} onChange={e => setKind(e.target.value as FieldKind)}>
              <option value="METER_READING">Meter reading</option>
              <option value="POSITION_READING">Where the machine is</option>
            </select>
          </div>
          <div>
            <label style={lbl} htmlFor="fd-asset">Machine</label>
            <select id="fd-asset" style={{ ...inputStyle(), fontSize: 16 }} value={assetId} onChange={e => setAssetId(e.target.value)}>
              <option value="">Choose…</option>
              {machines.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
            </select>
          </div>
          {kind === "METER_READING" ? (
            <div>
              <label style={lbl} htmlFor="fd-meter">Meter reading</label>
              <input id="fd-meter" type="number" inputMode="decimal" min="0" style={{ ...inputStyle(), fontSize: 16 }} value={meter} onChange={e => setMeter(e.target.value)} />
            </div>
          ) : (
            <>
              <button style={btn()} onClick={useMyLocation} type="button">Use my location</button>
              <div>
                <label style={lbl} htmlFor="fd-lat">Latitude</label>
                <input id="fd-lat" inputMode="decimal" style={{ ...inputStyle(), fontSize: 16 }} value={lat} onChange={e => setLat(e.target.value)} />
              </div>
              <div>
                <label style={lbl} htmlFor="fd-lon">Longitude</label>
                <input id="fd-lon" inputMode="decimal" style={{ ...inputStyle(), fontSize: 16 }} value={lon} onChange={e => setLon(e.target.value)} />
              </div>
            </>
          )}
          <div>
            <label style={lbl} htmlFor="fd-note">Note (optional)</label>
            <input id="fd-note" style={{ ...inputStyle(), fontSize: 16 }} value={note} onChange={e => setNote(e.target.value)} />
          </div>
          <button style={btn(true)} onClick={save}>Save entry</button>
        </section>
      ) : (
        <p style={{ color: "var(--hf-text-muted)" }}>You can see field entries but not make them.</p>
      )}

      {pending.length > 0 && (
        <button style={btn()} disabled={sending || !online} onClick={() => void sendNow(queue)}>{sending ? "Sending…" : `Send ${pending.length} now`}</button>
      )}

      <section aria-label="Entries on this phone" style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {shown.length === 0 ? <p style={{ color: "var(--hf-text-muted)", margin: 0 }}>Nothing recorded on this phone yet.</p> : shown.map(e => (
          <div key={e.submissionId} style={card}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
              <strong>{e.assetName || "Machine"}</strong>
              <span style={{ color: STATE_COLOR[e.state], fontWeight: 700, fontSize: 13 }}>{STATE_LABEL[e.state]}</span>
            </div>
            <div style={{ fontSize: 13 }}>
              {e.kind === "METER_READING" ? `Meter ${e.meterValue}` : `Position ${e.latitude}, ${e.longitude}`} · taken {fmtDateTime(e.capturedAt)}
            </div>
            {e.note && <div style={{ fontSize: 12 }}>{e.note}</div>}
            {e.reason && <div style={{ fontSize: 12, color: STATE_COLOR[e.state] }}>{e.reason}</div>}
            {e.state === "PENDING" && <button style={{ ...btn(), padding: "4px 10px", fontSize: 12, marginTop: 6 }} onClick={() => persist(discard(queue, e.submissionId))}>Discard</button>}
          </div>
        ))}
      </section>

      {canMaintain && (conflicts.data ?? []).length > 0 && (
        <section aria-label="Entries needing review" style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 15 }}>Entries needing review</h3>
          {(conflicts.data ?? []).map(c => (
            <div key={c.id} style={card}>
              <strong>{c.assetName ?? "Machine"}</strong>{" "}
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>taken {fmtDateTime(c.capturedAt)}</span>
              <div style={{ fontSize: 13 }}>{c.reason}</div>
              <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
                <button style={btn()} onClick={() => review.mutate({ id: c.id, decision: "KEPT_SERVER", note: null }, { onError: (e: unknown) => setErr(apiMessage(e, "Could not save the decision")) })}>Keep the server's reading</button>
                <button style={btn()} onClick={() => review.mutate({ id: c.id, decision: "APPLIED", note: null }, { onError: (e: unknown) => setErr(apiMessage(e, "Could not apply the entry")) })}>Apply this entry</button>
              </div>
            </div>
          ))}
        </section>
      )}
    </div>
  )
}
