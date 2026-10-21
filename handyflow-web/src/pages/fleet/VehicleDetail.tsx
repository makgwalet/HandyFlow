// src/pages/fleet/VehicleDetail.tsx
//
// Tabs inside the vehicle panel: Overview, Location, Maintenance, Fuel & Usage, Compliance, Documents, Insurance, Accidents, Lifecycle and History.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { COMPLIANCE_LABEL, DOCUMENT_TYPE_LABEL, complianceTone, expiryText, identityRows, uploadProblem, type Identity } from "./vehicleDetail.logic"
import VehicleSummary from "./VehicleSummary"
import LifecyclePanel from "./LifecyclePanel"
import { LocationTab, MaintenanceTab, FuelUsageTab, HistoryTab } from "./VehicleTabs"
import VehicleEditForm from "./VehicleEditForm"
import InsuranceForm from "./InsuranceForm"
import { POLICY_TYPE_LABEL, type InsuranceData } from "./insurance.logic"
import type { VehicleFull } from "./vehicleEdit.logic"
import { STATUS_LABEL, blocksVehicle } from "./accidents.logic"

interface ComplianceItem { key: string; label: string; status: string; daysRemaining: number | null; detail: string | null }
interface Compliance { overallStatus: string; items: ComplianceItem[] }
interface Doc { id: string; type: string; documentNumber: string | null; expiryDate: string | null; version: number; fileName: string; expired: boolean; daysToExpiry: number | null }
interface Accident { id: string; referenceNumber: string; occurredAt: string; description: string; status: string; vehicleDrivable: boolean }

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] }>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const R = (n: number | null | undefined) => (n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA")}`)
const TONE: Record<string, string> = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", bad: "var(--hf-danger-text)", none: "var(--hf-text-faint)" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5 }
const ghostBtn: React.CSSProperties = { padding: "6px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5, cursor: "pointer", marginBottom: 10 }
const TABS = ["Overview", "Location", "Maintenance", "Fuel & Usage", "Compliance", "Documents", "Insurance", "Accidents", "Lifecycle", "History"] as const

function ComplianceTab({ id }: { id: string }) {
  const { data, isLoading } = useQuery<Compliance>({ queryKey: ["fleet", "compliance", id], queryFn: async () => unwrap<Compliance>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/compliance`)) })
  if (isLoading) return <p>Loading…</p>
  return (
    <table style={{ width: "100%", fontSize: 13 }}><tbody>{(data?.items ?? []).map(i => (
      <tr key={i.key} aria-label={i.label}>
        <td style={{ padding: "6px 4px" }}>{i.label}</td>
        <td style={{ padding: "6px 4px", color: TONE[complianceTone(i.status)], fontWeight: 700 }}>● {COMPLIANCE_LABEL[i.status] ?? i.status}</td>
        <td style={{ padding: "6px 4px", color: "var(--hf-text-muted)" }}>{i.daysRemaining != null ? expiryText(i.daysRemaining) : i.detail}</td>
      </tr>))}</tbody></table>
  )
}

function DocumentsTab({ id }: { id: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const [type, setType] = useState(""), [expiry, setExpiry] = useState(""), [number, setNumber] = useState("")
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { data: docs = [] } = useQuery<Doc[]>({ queryKey: ["fleet", "documents", id], queryFn: async () => list<Doc>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/documents`)) })
  const upload = useMutation({
    mutationFn: () => {
      const f = new FormData(); f.append("file", file as File); f.append("type", type)
      if (expiry) f.append("expiryDate", expiry)
      if (number.trim()) f.append("documentNumber", number.trim())
      return apiClient.post(`/api/v1/fleet/vehicles/${id}/documents`, f)
    },
    onSuccess: () => { setError(null); setType(""); setExpiry(""); setNumber(""); setFile(null); for (const k of ["documents", "compliance"]) qc.invalidateQueries({ queryKey: ["fleet", k, id] }) },
    onError: e => setError(errorOf(e)),
  })
  const problem = uploadProblem(type, file, expiry)
  return (
    <div>
      {docs.length === 0 && <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No documents uploaded yet.</p>}
      <ul style={{ listStyle: "none", padding: 0, margin: 0, fontSize: 13 }}>{docs.map(d => (
        <li key={d.id} style={{ padding: "6px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
          <strong>{DOCUMENT_TYPE_LABEL[d.type] ?? d.type}</strong>{d.documentNumber ? ` · ${d.documentNumber}` : ""} · v{d.version}
          <span style={{ marginLeft: 8, color: d.expired ? TONE.bad : TONE.none }}>{d.expiryDate ? expiryText(d.daysToExpiry) : ""}</span>
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{d.fileName}</div>
        </li>))}</ul>
      {canManage && (
        <div style={{ marginTop: 10, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <select aria-label="Document type" style={field} value={type} onChange={e => { setType(e.target.value); setError(null) }}>
            <option value="">Type…</option>{Object.entries(DOCUMENT_TYPE_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          <input aria-label="Document number" placeholder="Number" style={{ ...field, width: 110 }} value={number} onChange={e => setNumber(e.target.value)} />
          <input aria-label="Expiry date" type="date" style={field} value={expiry} onChange={e => setExpiry(e.target.value)} />
          <input aria-label="Document file" type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} />
          <button type="button" disabled={!!problem || upload.isPending} style={{ ...field, cursor: "pointer", fontWeight: 700 }} onClick={() => upload.mutate()}>Upload</button>
          {problem && <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{problem}</span>}
          {error && <span role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>{error}</span>}
        </div>)}
    </div>
  )
}

function InsuranceTab({ id }: { id: string }) {
  const canManage = usePermission("FLEET_MANAGE")
  const [editing, setEditing] = useState(false)
  const { data, isLoading } = useQuery<InsuranceData | null>({ queryKey: ["fleet", "insurance", id], queryFn: async () => unwrap<InsuranceData | null>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/insurance`)) })
  if (isLoading) return <p>Loading…</p>
  if (editing) return <InsuranceForm vehicleId={id} current={data ?? null} onDone={() => setEditing(false)} />
  const edit = canManage && <div><button type="button" style={ghostBtn} onClick={() => setEditing(true)}>{data ? "Edit insurance" : "Record insurance"}</button></div>
  if (!data || !data.insured) return <div>{edit}<p style={{ fontSize: 13 }}>{data ? "This vehicle is recorded as not insured." : "No insurance recorded for this vehicle."}</p></div>
  const rows: [string, string][] = [["Insurer", data.insurer ?? "—"], ["Policy number", data.policyNumber ?? "—"], ["Policy type", data.policyType ? POLICY_TYPE_LABEL[data.policyType] ?? data.policyType : "—"],
    ["Starts", data.policyStart ?? "—"], ["Expiry", data.policyExpiry ? `${data.policyExpiry} (${expiryText(data.daysToExpiry)})` : "—"], ["Premium", R(data.premium)], ["Excess", R(data.excess)], ["Cover", R(data.coverAmount)],
    ["Insured value", R(data.vehicleValue)], ["Third-party cover", R(data.thirdPartyCoverAmount)], ["Windscreen", data.windscreenCover ? "Covered" : "Not covered"],
    ["Broker", [data.broker, data.brokerContact].filter(Boolean).join(" · ") || "—"],
    ["Tracking", data.trackingRequired ? `Required${data.trackingProvider ? ` · ${data.trackingProvider}` : ""}${data.trackingDeviceNumber ? ` · ${data.trackingDeviceNumber}` : ""}` : "Not required"],
    ["Special conditions", data.specialConditions ?? "—"]]
  return <div>{edit}<dl style={{ display: "grid", gridTemplateColumns: "150px 1fr", gap: "6px 12px", fontSize: 13, margin: 0 }}>{rows.map(([l, v]) => <div key={l} style={{ display: "contents" }}><dt style={{ color: "var(--hf-text-faint)" }}>{l}</dt><dd style={{ margin: 0 }}>{v}</dd></div>)}</dl></div>
}

function AccidentsTab({ id }: { id: string }) {
  const { data = [] } = useQuery<Accident[]>({ queryKey: ["fleet", "accidents", "vehicle", id], queryFn: async () => list<Accident>(await apiClient.get(`/api/v1/fleet/accidents?vehicleId=${id}`)) })
  if (data.length === 0) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No accidents on record.</p>
  return <ul style={{ listStyle: "none", padding: 0, margin: 0, fontSize: 13 }}>{data.map(a => (
    <li key={a.id} style={{ padding: "6px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
      <strong>{a.referenceNumber}</strong> · {STATUS_LABEL[a.status] ?? a.status} · {new Date(a.occurredAt).toLocaleDateString("en-ZA")}
      {blocksVehicle(a) && <span style={{ marginLeft: 8, color: TONE.bad, fontWeight: 700 }}>Cannot be driven</span>}
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{a.description}</div></li>))}</ul>
}

export default function VehicleDetail({ vehicleId, identity }: { vehicleId: string; identity: Identity & VehicleFull & { currentOdometer?: number | null; lastServiceKm?: number | null; serviceIntervalKm?: number | null } }) {
  const [tab, setTab] = useState<(typeof TABS)[number]>("Overview")
  const [editing, setEditing] = useState(false)
  const canManage = usePermission("FLEET_MANAGE")
  return (
    <div style={{ marginBottom: 16 }}>
      <VehicleSummary vehicleId={vehicleId} name={(identity as { registration?: string }).registration ?? "this vehicle"} vehicle={identity as never} />
      <div role="tablist" style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--hf-border)", marginBottom: 10, flexWrap: "wrap" }}>
        {TABS.map(t => <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
          style={{ padding: "6px 12px", border: "none", background: "none", cursor: "pointer", fontSize: 13, fontWeight: tab === t ? 800 : 500, borderBottom: tab === t ? "2px solid var(--hf-brand)" : "2px solid transparent", color: "var(--hf-text)" }}>{t}</button>)}
      </div>
      {tab === "Overview" && editing && <VehicleEditForm vehicleId={vehicleId} vehicle={identity} onDone={() => setEditing(false)} />}
      {tab === "Overview" && !editing && canManage && <div><button type="button" style={ghostBtn} onClick={() => setEditing(true)}>Edit details</button></div>}
      {tab === "Overview" && !editing && <dl style={{ display: "grid", gridTemplateColumns: "170px 1fr", gap: "5px 12px", fontSize: 13, margin: 0 }}>
        {identityRows(identity).map(r => <div key={r.label} style={{ display: "contents" }}><dt style={{ color: "var(--hf-text-faint)" }}>{r.label}</dt><dd style={{ margin: 0, color: r.missing ? "var(--hf-warning-text)" : undefined }}>{r.value}</dd></div>)}</dl>}
      {tab === "Location" && <LocationTab id={vehicleId} />}
      {tab === "Maintenance" && <MaintenanceTab id={vehicleId} facts={identity} />}
      {tab === "Fuel & Usage" && <FuelUsageTab id={vehicleId} />}
      {tab === "Compliance" && <ComplianceTab id={vehicleId} />}
      {tab === "Documents" && <DocumentsTab id={vehicleId} />}
      {tab === "Insurance" && <InsuranceTab id={vehicleId} />}
      {tab === "Accidents" && <AccidentsTab id={vehicleId} />}
      {tab === "Lifecycle" && <LifecyclePanel vehicleId={vehicleId} />}
      {tab === "History" && <HistoryTab id={vehicleId} />}
    </div>
  )
}
