// src/pages/fleet/TrackingSetupTab.tsx
//
// Tracking set-up: which device is fitted to which vehicle, the speeding / idling / offline rules, and the API keys providers use to send data.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { fetchAllPages } from "./apiShape"
import {
  DEVICE_STATE_LABEL, INGEST_HEADER, INGEST_PATH, PROVIDERS, SETTING_FIELDS, agoText, deviceBody, deviceProblem, deviceState, emptyDevice, keyNameProblem, keyStatusText,
  settingsBody, settingsProblem, speedLimitWarning, toDeviceForm, toSettingsForm, untracked, type Device, type DeviceForm, type IngestKey, type SettingsForm, type TrackingSettings,
} from "./trackingSetup.logic"

interface VehicleOption { id: string; registration: string; make?: string; model?: string; status?: string }
interface CreatedKey { key: IngestKey; apiKey: string }

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] }>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { ...field, cursor: "pointer", fontWeight: 700, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)" }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, marginBottom: 12 }
const STATE_TONE = { off: "var(--hf-text-faint)", never: "var(--hf-warning-text)", ok: "var(--hf-success-text-strong)", silent: "var(--hf-danger-text)" }
const useVehicles = () => useQuery<VehicleOption[]>({ queryKey: ["fleet", "vehicle-options"], queryFn: async () => (await fetchAllPages<VehicleOption>(u => apiClient.get(u), "/api/v1/fleet/vehicles")).items })
const useSettings = () => useQuery<TrackingSettings>({ queryKey: ["fleet", "telematics-settings"], queryFn: async () => unwrap<TrackingSettings>(await apiClient.get("/api/v1/fleet/telematics/settings")) })

function Devices() {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const [draft, setDraft] = useState<DeviceForm | null>(null), [error, setError] = useState<string | null>(null)
  const { data: devices = [], isLoading, isError } = useQuery<Device[]>({ queryKey: ["fleet", "telematics-devices"], queryFn: async () => list<Device>(await apiClient.get("/api/v1/fleet/telematics/devices")) })
  const { data: vehicles = [] } = useVehicles()
  const { data: settings } = useSettings()
  const refresh = () => { for (const k of ["telematics-devices", "live"]) qc.invalidateQueries({ queryKey: ["fleet", k] }) }
  const save = useMutation({
    mutationFn: (f: DeviceForm) => f.id ? apiClient.put(`/api/v1/fleet/telematics/devices/${f.id}`, deviceBody(f)) : apiClient.post("/api/v1/fleet/telematics/devices", deviceBody(f)),
    onSuccess: () => { setDraft(null); setError(null); refresh() }, onError: e => setError(errorOf(e)),
  })
  const toggle = useMutation({ mutationFn: (d: Device) => apiClient.put(`/api/v1/fleet/telematics/devices/${d.id}`, deviceBody({ ...toDeviceForm(d), active: !d.active })), onSuccess: refresh, onError: e => setError(errorOf(e)) })
  const problem = draft ? deviceProblem(draft) : null
  const offline = settings?.offlineAfterMinutes ?? 30
  const bare = untracked(vehicles, devices)
  return (
    <div>
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load devices.</div>}
      {!draft && canManage && <div style={{ marginBottom: 10 }}><button type="button" style={btn} onClick={() => { setDraft(emptyDevice()); setError(null) }}>Register device</button></div>}
      {draft && (
        <form aria-label="Device editor" style={card} onSubmit={e => { e.preventDefault(); if (!problem) save.mutate(draft) }}>
          <strong>{draft.id ? "Edit device" : "Register a device"}</strong>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", margin: "8px 0" }}>
            <input aria-label="Unit id" placeholder="Provider's unit id" style={{ ...field, minWidth: 200 }} value={draft.externalId} onChange={e => { setDraft({ ...draft, externalId: e.target.value }); setError(null) }} />
            <input aria-label="Provider" list="tracking-providers" placeholder="Provider" style={{ ...field, width: 150 }} value={draft.provider} onChange={e => setDraft({ ...draft, provider: e.target.value })} />
            <datalist id="tracking-providers">{PROVIDERS.map(p => <option key={p} value={p} />)}</datalist>
            <select aria-label="Vehicle" style={field} value={draft.vehicleId} onChange={e => setDraft({ ...draft, vehicleId: e.target.value })}><option value="">Vehicle…</option>{vehicles.map(v => <option key={v.id} value={v.id}>{v.registration}{v.make ? ` · ${v.make} ${v.model ?? ""}` : ""}</option>)}</select>
            <input aria-label="Device name" placeholder="Name (optional)" style={{ ...field, minWidth: 160 }} value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} />
            <label style={{ fontSize: 13 }}><input type="checkbox" aria-label="Device active" checked={draft.active} onChange={e => setDraft({ ...draft, active: e.target.checked })} /> Active</label>
          </div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <button type="submit" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending}>{save.isPending ? "Saving…" : "Save device"}</button>
            <button type="button" style={ghost} onClick={() => { setDraft(null); setError(null) }}>Cancel</button>
            {problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
          </div>
          {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
        </form>)}
      {!draft && error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 6 }}>{error}</div>}
      {!isLoading && devices.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No devices registered. Data from a unit that is not registered here is rejected.</p>}
      {devices.length > 0 && <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
        <thead><tr>{["Vehicle", "Unit id", "Provider", "Status", "Last report", ""].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 10px", fontSize: 11, textTransform: "uppercase", color: "var(--hf-text-faint)" }}>{h}</th>)}</tr></thead>
        <tbody>{devices.map(d => { const st = deviceState(d, offline); return (
          <tr key={d.id} aria-label={d.externalId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
            <td style={{ padding: "9px 10px" }}><strong>{d.registration ?? "—"}</strong>{d.name && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{d.name}</div>}</td>
            <td style={{ padding: "9px 10px" }}>{d.externalId}</td><td style={{ padding: "9px 10px" }}>{d.provider}</td>
            <td style={{ padding: "9px 10px", fontWeight: 700, color: STATE_TONE[st] }}>{DEVICE_STATE_LABEL[st]}</td><td style={{ padding: "9px 10px" }}>{agoText(d.lastSeenAt)}</td>
            <td style={{ padding: "9px 10px", whiteSpace: "nowrap" }}>{canManage && <>
              <button type="button" style={ghost} aria-label={`Edit ${d.externalId}`} onClick={() => { setDraft(toDeviceForm(d)); setError(null) }}>Edit</button>{" "}
              <button type="button" style={ghost} aria-label={`${d.active ? "Switch off" : "Switch on"} ${d.externalId}`} onClick={() => toggle.mutate(d)}>{d.active ? "Switch off" : "Switch on"}</button></>}</td>
          </tr>) })}</tbody></table>}
      {bare.length > 0 && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 10 }} aria-label="Untracked vehicles"><strong>{bare.length} {bare.length === 1 ? "vehicle has" : "vehicles have"} no active device</strong> and will not appear on the live map: {bare.map(v => v.registration).join(", ")}.</p>}
    </div>
  )
}

function Rules() {
  const qc = useQueryClient()
  const canAdmin = usePermission("FLEET_ADMIN")
  const { data } = useSettings()
  const [form, setForm] = useState<SettingsForm | null>(null), [error, setError] = useState<string | null>(null), [saved, setSaved] = useState(false)
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fleet/telematics/settings", settingsBody(form as SettingsForm)),
    onSuccess: () => { setError(null); setSaved(true); qc.invalidateQueries({ queryKey: ["fleet", "telematics-settings"] }) }, onError: e => setError(errorOf(e)),
  })
  if (!data) return <p>Loading…</p>
  const f = form ?? toSettingsForm(data)
  const set = (k: keyof SettingsForm, v: string) => { setForm({ ...f, [k]: v }); setSaved(false) }
  const problem = settingsProblem(f), warning = speedLimitWarning(f)
  return (
    <div style={card}>
      <strong>Driving rules {data.usingDefaults && <span style={{ fontSize: 12, fontWeight: 400, color: "var(--hf-text-muted)" }}>(built-in defaults)</span>}</strong>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(240px,1fr))", gap: 12, margin: "10px 0" }}>
        {SETTING_FIELDS.map(s => (
          <label key={s.key} style={{ fontSize: 12.5 }}>{s.label} ({s.unit})
            <input aria-label={s.label} type="number" min={s.min} max={s.max} disabled={!canAdmin} style={{ ...field, width: "100%", boxSizing: "border-box", display: "block" }} value={f[s.key]} onChange={e => set(s.key, e.target.value)} />
            <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{s.help}</span></label>))}
      </div>
      {warning && <div role="note" style={{ fontSize: 12.5, color: "var(--hf-warning-text)", marginBottom: 8 }}>{warning}</div>}
      {canAdmin ? <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
        <button type="button" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Save rules</button>
        {problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}{saved && <span role="status" style={{ fontSize: 12, color: "var(--hf-success-text-strong)" }}>Saved</span>}
      </div> : <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: 0 }}>Only a fleet administrator can change these rules.</p>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </div>
  )
}

function Keys() {
  const qc = useQueryClient()
  const [name, setName] = useState(""), [created, setCreated] = useState<CreatedKey | null>(null), [copied, setCopied] = useState(false)
  const [confirmId, setConfirmId] = useState<string | null>(null), [error, setError] = useState<string | null>(null)
  const { data: keys = [], isLoading } = useQuery<IngestKey[]>({ queryKey: ["fleet", "ingest-keys"], queryFn: async () => list<IngestKey>(await apiClient.get("/api/v1/fleet/telematics/ingest-keys")) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["fleet", "ingest-keys"] })
  const create = useMutation({ mutationFn: () => apiClient.post("/api/v1/fleet/telematics/ingest-keys", { name: name.trim() }), onSuccess: r => { setCreated(unwrap<CreatedKey>(r)); setName(""); setCopied(false); setError(null); refresh() }, onError: e => setError(errorOf(e)) })
  const revoke = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/fleet/telematics/ingest-keys/${id}`), onSuccess: () => { setConfirmId(null); setError(null); refresh() }, onError: e => setError(errorOf(e)) })
  const problem = keyNameProblem(name)
  const copy = async () => { try { await navigator.clipboard.writeText(created?.apiKey ?? ""); setCopied(true) } catch { setCopied(false); setError("Copy didn't work here. Select the key and copy it by hand.") } }
  return (
    <div>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 0 }}>A provider sends GPS data to <code>POST {INGEST_PATH}</code> with the key in the <code>{INGEST_HEADER}</code> header. Make one key per provider so you can switch one off without touching the others.</p>
      {created && (
        <div role="status" aria-label="New key" style={{ ...card, background: "var(--hf-warning-soft)", borderColor: "var(--hf-warning-border)" }}>
          <strong>Key for {created.key.name}. Copy it now: it will not be shown again.</strong>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 8 }}>
            <code aria-label="New API key" style={{ ...field, userSelect: "all", wordBreak: "break-all" }}>{created.apiKey}</code>
            <button type="button" style={btn} onClick={copy}>{copied ? "Copied" : "Copy key"}</button>
            <button type="button" style={ghost} onClick={() => { setCreated(null); setCopied(false) }}>I have saved it</button>
          </div>
        </div>)}
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 10 }}>
        <input aria-label="Key name" placeholder="Provider (e.g. Cartrack production)" style={{ ...field, minWidth: 260 }} value={name} onChange={e => { setName(e.target.value); setError(null) }} />
        <button type="button" style={{ ...btn, opacity: problem || create.isPending ? 0.5 : 1 }} disabled={!!problem || create.isPending} onClick={() => create.mutate()}>Create key</button>
        {problem && name && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
      </div>
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 6 }}>{error}</div>}
      {!isLoading && keys.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No keys yet. A provider cannot send data until you create one.</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 13 }}>{keys.map(k => (
        <li key={k.id} aria-label={k.name} style={{ padding: "8px 0", borderBottom: "1px solid var(--hf-border-subtle)", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", opacity: k.active ? 1 : 0.6 }}>
          <strong>{k.name}</strong><code style={{ fontSize: 12 }}>{k.keyPrefix}…</code>
          <span style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>Created {new Date(k.createdAt).toLocaleDateString("en-ZA")} · {keyStatusText(k)}</span>
          {k.active && <span style={{ marginLeft: "auto", display: "flex", gap: 6 }}>{confirmId === k.id
            ? <><button type="button" style={{ ...ghost, color: "var(--hf-danger-text)", fontWeight: 700 }} aria-label={`Confirm revoke ${k.name}`} onClick={() => revoke.mutate(k.id)}>Confirm: provider is cut off now</button><button type="button" style={ghost} onClick={() => setConfirmId(null)}>Keep</button></>
            : <button type="button" style={ghost} aria-label={`Revoke ${k.name}`} onClick={() => setConfirmId(k.id)}>Revoke</button>}</span>}
        </li>))}</ul>
    </div>
  )
}

export default function TrackingSetupTab() {
  const canAdmin = usePermission("FLEET_ADMIN")
  const tabs = ["Devices", "Rules", ...(canAdmin ? ["Ingest keys"] : [])] as const
  const [tab, setTab] = useState<string>("Devices")
  return (
    <div>
      <div role="tablist" style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--hf-border)", marginBottom: 12 }}>
        {tabs.map(t => <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)} style={{ padding: "6px 12px", border: "none", background: "none", cursor: "pointer", fontSize: 13, fontWeight: tab === t ? 800 : 500, borderBottom: tab === t ? "2px solid var(--hf-brand)" : "2px solid transparent", color: "var(--hf-text)" }}>{t}</button>)}
      </div>
      {tab === "Devices" && <Devices />}{tab === "Rules" && <Rules />}{tab === "Ingest keys" && canAdmin && <Keys />}
    </div>
  )
}
