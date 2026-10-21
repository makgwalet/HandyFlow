// src/pages/fleet/DriversTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorMessage, fetchAllPages } from "./apiShape"
import { ProfileView, SettingsPanel, type AssignedVehicle, type ScoreRow } from "./Driver360"
import { unwrap as unwrapOne, bandText, bandToneFor, scoreText, sortDrivers, daysToDate, expiryState, expiryWords, driverDocumentCounts, driverVehicles, filterDrivers, initials, paginate, rankMap, DOC_FILTER_LABEL, SORT_LABEL, type DocFilter, type DriverSort } from "./driverScores.logic"
import {
  Plus, User, X, AlertCircle, AlertTriangle, CheckCircle, Edit2,
  Phone, Mail, UserX, UserCheck, Gauge,
} from "lucide-react"

interface Driver {
  id: string
  firstName: string; lastName: string
  phone: string | null; email: string | null; idNumber: string | null
  licenseNumber: string | null; licenseCode: string | null
  licenseExpiry: string | null; licenseExpiringSoon: boolean
  prdpRequired: boolean; prdpNumber: string | null; prdpCategory: string | null
  prdpExpiry: string | null; prdpExpiringSoon: boolean
  status: string; notes: string | null; createdAt: string; department?: string | null
}

const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

type DriverBody = Omit<Driver, "id" | "licenseExpiringSoon" | "prdpExpiringSoon" | "status" | "createdAt">

const LICENSE_CODES = ["A", "A1", "B", "C1", "C", "EB", "EC1", "EC"]
const PRDP_CATEGORIES = [
  { value: "G", label: "G — Goods" },
  { value: "P", label: "P — Passengers" },
  { value: "D", label: "D — Dangerous goods" },
]

const EMPTY_FORM = {
  firstName: "", lastName: "", phone: "", email: "", idNumber: "",
  licenseNumber: "", licenseCode: "B", licenseExpiry: "",
  prdpRequired: false, prdpNumber: "", prdpCategory: "G", prdpExpiry: "",
  notes: "", department: "",
}

const DRIVERS_KEY = ["fleet", "drivers", "all"]
/** Every list of drivers is refreshed after a change. */
const refresh = (qc: ReturnType<typeof useQueryClient>) => { qc.invalidateQueries({ queryKey: DRIVERS_KEY }); qc.invalidateQueries({ queryKey: ["fleet-drivers"] }) }

function ExpiryBadge({ date }: { date: string | null }) {
  const state = expiryState(date)
  if (state === "NOT_SET") return <span style={{ color: "var(--hf-text-faint)", fontSize: 12 }}>Not set</span>
  const days = daysToDate(date!), words = expiryWords(date)
  if (state === "EXPIRED") return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 11, fontWeight: 700, color: "var(--hf-danger-text)" }}><AlertCircle size={11} />{words}</span>
  if (state === "EXPIRING") return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 11, fontWeight: 700, color: days <= 7 ? "var(--hf-danger-text)" : "var(--hf-warning-text)" }}><AlertTriangle size={11} />{words}</span>
  return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 11, color: "var(--hf-success-text-strong)" }}><CheckCircle size={11} />Valid</span>
}

export default function DriversTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd]     = useState(false)
  const [editing, setEditing]     = useState<Driver | null>(null)
  const [filterStatus, setFilterStatus] = useState("ACTIVE")
  const [openId, setOpenId]       = useState<string | null>(null)
  const [showSettings, setShowSettings] = useState(false)
  const [sort, setSort]           = useState<DriverSort>("name")
  const [search, setSearch] = useState(""), [docs, setDocs] = useState<DocFilter>("ALL"), [page, setPage] = useState(0)
  const { data: scoreRows = [] } = useQuery<ScoreRow[]>({ queryKey: ["fleet", "driver-scores"], queryFn: async () => unwrapOne<ScoreRow[]>(await apiClient.get("/api/v1/fleet/driver-scores")) })
  const scoreOf = new Map(scoreRows.map(r => [r.driverId, r]))
  const [form, setForm]           = useState(EMPTY_FORM)
  const [apiError, setApiError]   = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  // Same key and shape as the Vehicles screen's list, so the two share one fetch.
  const { data: vehicles = [] } = useQuery<AssignedVehicle[]>({ queryKey: ["fleet", "list", "vehicles"], queryFn: async () => (await fetchAllPages<AssignedVehicle>(u => apiClient.get(u), "/api/v1/fleet/vehicles")).items })
  // Its own key: the assign-driver list caches a plain array under ["fleet-drivers"], this one also says whether the list was cut short.
  const { data: loaded, isLoading } = useQuery<{ items: Driver[]; truncated: boolean }>({
    queryKey: DRIVERS_KEY,
    queryFn: () => fetchAllPages<Driver>(u => apiClient.get(u), "/api/v1/fleet/drivers"),
  })
  const drivers = loaded?.items ?? [], truncated = loaded?.truncated ?? false

  const createDriver = useMutation({
    mutationFn: (body: DriverBody) => apiClient.post("/api/v1/fleet/drivers", body),
    onSuccess: () => { refresh(qc); closeModal() },
    onError: (e: unknown) => setApiError(errorMessage(e, "Failed to register driver")),
  })

  const updateDriver = useMutation({
    mutationFn: ({ id, body }: { id: string; body: DriverBody }) => apiClient.put(`/api/v1/fleet/drivers/${id}`, body),
    onSuccess: () => { refresh(qc); closeModal() },
    onError: (e: unknown) => setApiError(errorMessage(e, "Failed to update driver")),
  })

  const setStatus = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      apiClient.post(`/api/v1/fleet/drivers/${id}/${active ? "reactivate" : "deactivate"}`),
    onSuccess: () => refresh(qc),
  })

  const closeModal = () => {
    setShowAdd(false); setEditing(null); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("")
  }

  const openEdit = (d: Driver) => {
    setEditing(d)
    setForm({
      firstName: d.firstName, lastName: d.lastName, phone: d.phone ?? "", email: d.email ?? "",
      idNumber: d.idNumber ?? "", licenseNumber: d.licenseNumber ?? "", licenseCode: d.licenseCode ?? "B",
      licenseExpiry: d.licenseExpiry ?? "", prdpRequired: d.prdpRequired,
      prdpNumber: d.prdpNumber ?? "", prdpCategory: d.prdpCategory ?? "G", prdpExpiry: d.prdpExpiry ?? "",
      notes: d.notes ?? "", department: d.department ?? "",
    })
    setApiError(""); setFieldErrors({})
  }

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.firstName.trim()) errs.firstName = "First name is required"
    if (!form.lastName.trim()) errs.lastName = "Last name is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const buildBody = () => ({
    firstName: form.firstName, lastName: form.lastName,
    phone: form.phone || null, email: form.email || null, idNumber: form.idNumber || null,
    licenseNumber: form.licenseNumber || null, licenseCode: form.licenseCode || null,
    licenseExpiry: form.licenseExpiry || null,
    prdpRequired: form.prdpRequired,
    prdpNumber: form.prdpRequired ? (form.prdpNumber || null) : null,
    prdpCategory: form.prdpRequired ? form.prdpCategory : null,
    prdpExpiry: form.prdpRequired ? (form.prdpExpiry || null) : null,
    notes: form.notes || null,
    department: form.department.trim() || null,
  })

  const opened = openId ? drivers.find(d => d.id === openId) ?? null : null
  const ordered = sortDrivers(drivers.filter(d => filterStatus === "ALL" || d.status === filterStatus), sort, scoreOf)
  const rankOf = sort === "name" ? new Map<string, number>() : rankMap(ordered, scoreOf)   // ranks among the status group, not among search results
  const shown = filterDrivers(ordered, { search, status: "ALL", docs })
  const paged = paginate(shown, page)
  const counts = driverDocumentCounts(drivers)
  const stats = [
    { label: "Total drivers", value: drivers.length, color: "var(--hf-primary-text)", filter: undefined as DocFilter | undefined },
    { label: "Active drivers", value: drivers.filter(d => d.status === "ACTIVE").length, color: "var(--hf-success-text-strong)", filter: undefined as DocFilter | undefined },
    { label: "Active drivers with an expired document", value: counts.expired, color: "var(--hf-danger-text)", filter: "EXPIRED" as DocFilter | undefined },
    { label: "Active drivers with a document expiring in 30 days", value: counts.expiring, color: "var(--hf-warning-text)", filter: "EXPIRING" as DocFilter | undefined },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "var(--hf-danger)" : "var(--hf-border)"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "var(--hf-danger-soft)" : "var(--hf-surface)", outline: "none",
  })
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }

  return (
    <div>
      {opened ? <ProfileView id={opened.id} vehicles={vehicles} onClose={() => setOpenId(null)} onEdit={() => openEdit(opened)} /> : <>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => {
          const body = <><div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div><div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 2 }}>{s.label}</div></>
          const box: React.CSSProperties = { flex: 1, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", textAlign: "left" }
          return s.filter
            ? <button key={s.label} type="button" aria-label={s.label} aria-pressed={docs === s.filter} onClick={() => { setDocs(docs === s.filter ? "ALL" : s.filter!); setFilterStatus("ACTIVE"); setPage(0) }} style={{ ...box, cursor: "pointer", outline: docs === s.filter ? "2px solid var(--hf-primary)" : undefined }}>{body}</button>
            : <div key={s.label} aria-label={s.label} style={box}>{body}</div>
        })}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6 }}>
          {["ACTIVE", "INACTIVE", "ALL"].map(s => (
            <button key={s} onClick={() => { setFilterStatus(s); setPage(0) }}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: filterStatus === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {s === "ALL" ? "All" : s.charAt(0) + s.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
        <input aria-label="Search drivers" placeholder="Search name, phone, email, ID or licence number" value={search} onChange={e => { setSearch(e.target.value); setPage(0) }} style={{ padding: "7px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, minWidth: 260, background: "var(--hf-surface)" }} />
        <select aria-label="Documents" value={docs} onChange={e => { setDocs(e.target.value as DocFilter); setPage(0) }} style={{ padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)" }}>
          {(Object.keys(DOC_FILTER_LABEL) as DocFilter[]).map(k => <option key={k} value={k}>{DOC_FILTER_LABEL[k]}</option>)}
        </select>
        <label style={{ fontSize: 12.5, color: "var(--hf-text-muted)", display: "flex", alignItems: "center", gap: 6 }}>Sort by
          <select aria-label="Sort drivers" value={sort} onChange={e => { setSort(e.target.value as DriverSort); setPage(0) }} style={{ padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)" }}>
            {(Object.keys(SORT_LABEL) as DriverSort[]).map(k => <option key={k} value={k}>{SORT_LABEL[k]}</option>)}
          </select>
        </label>
        <button type="button" onClick={() => setShowSettings(s => !s)}
          style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "9px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer", color: "var(--hf-text)" }}>
          {showSettings ? "Hide score settings" : "Score settings"}
        </button>
        <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Register Driver
        </button>
      </div>

      {showSettings && <SettingsPanel />}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading drivers...</div>
      ) : shown.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <User size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>{drivers.length === 0 ? "No drivers yet" : "No drivers match"}</div>
        </div>
      ) : (
        <>
          <div style={{ overflowX: "auto", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
            <table style={{ width: "100%", borderCollapse: "collapse", background: "var(--hf-surface)" }}>
              <thead><tr style={{ textAlign: "left", background: "var(--hf-surface-muted)" }}>{["Driver", "Licence", "PrDP", "Score", "Vehicle", ""].map((h, i) => <th key={i} style={{ padding: "8px 12px", fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)" }}>{h || <span style={{ position: "absolute", left: -9999 }}>Actions</span>}</th>)}</tr></thead>
              <tbody>{paged.rows.map(d => {
                const s = scoreOf.get(d.id), car = driverVehicles(vehicles, d.id)
                return (
                  <tr key={d.id} aria-label={`${d.firstName} ${d.lastName}`} style={{ borderTop: "1px solid var(--hf-border-subtle)", opacity: d.status === "INACTIVE" ? 0.6 : 1, verticalAlign: "middle" }}>
                    <td style={{ padding: "8px 12px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <div aria-hidden="true" style={{ width: 34, height: 34, borderRadius: "50%", background: "var(--hf-info-soft)", border: "2px solid var(--hf-info-border)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 12, color: "var(--hf-info-text)", flexShrink: 0 }}>{initials(d.firstName, d.lastName)}</div>
                        <div style={{ minWidth: 0 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
                            {rankOf.has(d.id) && <span aria-label="Rank" style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-primary-text)" }}>#{rankOf.get(d.id)}</span>}
                            <span style={{ fontWeight: 700, fontSize: 13.5 }}>{d.firstName} {d.lastName}</span>
                            {d.status === "INACTIVE" && <span style={{ fontSize: 10, fontWeight: 700, background: "var(--hf-surface-sunken)", color: "var(--hf-text-muted)", padding: "1px 7px", borderRadius: 20 }}>INACTIVE</span>}
                            {d.notes && <span title={d.notes} aria-label={`Note: ${d.notes}`} style={{ fontSize: 11, cursor: "help" }}>📝</span>}
                          </div>
                          <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", display: "flex", gap: 10, flexWrap: "wrap" }}>
                            {d.phone && <span style={{ display: "inline-flex", alignItems: "center", gap: 3 }}><Phone size={10} />{d.phone}</span>}
                            {d.email && <span style={{ display: "inline-flex", alignItems: "center", gap: 3 }}><Mail size={10} />{d.email}</span>}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: "8px 12px", fontSize: 12.5 }}><div style={{ color: "var(--hf-text-faint)", fontSize: 11 }}>{d.licenseCode ? `Code ${d.licenseCode}` : "—"} · {fmtDate(d.licenseExpiry)}</div><ExpiryBadge date={d.licenseExpiry} /></td>
                    <td style={{ padding: "8px 12px", fontSize: 12.5 }}>{d.prdpRequired ? <><div style={{ color: "var(--hf-text-faint)", fontSize: 11 }}>{d.prdpCategory ? `PrDP ${d.prdpCategory}` : "PrDP"} · {fmtDate(d.prdpExpiry)}</div><ExpiryBadge date={d.prdpExpiry} /></> : <span style={{ color: "var(--hf-text-faint)" }}>Not required</span>}</td>
                    <td style={{ padding: "8px 12px" }}>{s && (
                      <span aria-label="Score" title={s.reliable ? "Safety and performance score" : "Indicative only: less than half of the score had data"} style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)" }}>
                        {scoreText(s.overall)}{!s.reliable && s.overall != null ? "*" : ""} · <span style={{ color: { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", bad: "var(--hf-danger-text)", none: "var(--hf-text-faint)" }[bandToneFor(s.band, s.reliable)] }}>{bandText(s.band, s.reliable)}</span>
                      </span>)}</td>
                    <td style={{ padding: "8px 12px", fontSize: 12.5 }}>{car.length === 0 ? <span style={{ color: "var(--hf-text-faint)" }}>—</span> : car.map(v => v.registration).join(", ")}</td>
                    <td style={{ padding: "8px 12px", whiteSpace: "nowrap" }}>
                      <div style={{ display: "flex", gap: 5 }}>
                        <button type="button" onClick={() => setOpenId(d.id)} title="Driver 360" style={{ background: "var(--hf-violet-soft)", border: "none", borderRadius: 6, padding: "6px 10px", cursor: "pointer", color: "var(--hf-violet-text)", fontSize: 12, fontWeight: 600, display: "flex", alignItems: "center", gap: 4 }}><Gauge size={13} />360 view</button>
                        <button onClick={() => openEdit(d)} title="Edit" aria-label={`Edit ${d.firstName} ${d.lastName}`} style={{ background: "var(--hf-warning-soft-strong)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-warning-text)" }}><Edit2 size={13} /></button>
                        {d.status === "ACTIVE" ? (
                          <button onClick={() => setStatus.mutate({ id: d.id, active: false })} title="Deactivate" aria-label={`Deactivate ${d.firstName} ${d.lastName}`} style={{ background: "var(--hf-danger-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-danger-text)" }}><UserX size={13} /></button>
                        ) : (
                          <button onClick={() => setStatus.mutate({ id: d.id, active: true })} title="Reactivate" aria-label={`Reactivate ${d.firstName} ${d.lastName}`} style={{ background: "var(--hf-success-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-success-text-strong)" }}><UserCheck size={13} /></button>
                        )}
                      </div>
                    </td>
                  </tr>)
              })}</tbody>
            </table>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 10, fontSize: 12.5, color: "var(--hf-text-muted)", flexWrap: "wrap", gap: 8 }}>
            <span aria-label="Showing">Showing {paged.from}–{paged.to} of {paged.total}</span>
            {paged.pages > 1 && <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <button type="button" disabled={paged.page === 0} onClick={() => setPage(paged.page - 1)} style={{ padding: "5px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", cursor: paged.page === 0 ? "default" : "pointer" }}>Previous</button>
              <span>Page {paged.page + 1} of {paged.pages}</span>
              <button type="button" disabled={paged.page >= paged.pages - 1} onClick={() => setPage(paged.page + 1)} style={{ padding: "5px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", cursor: paged.page >= paged.pages - 1 ? "default" : "pointer" }}>Next</button>
            </div>}
          </div>
        </>
      )}
      {truncated && <p role="status" style={{ fontSize: 12, color: "var(--hf-warning-text)" }}>Only the first {drivers.length.toLocaleString("en-ZA")} drivers are shown. Narrow the list with the search.</p>}
      {scoreRows.some(r => !r.reliable && r.overall != null) && <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>* Indicative only: less than half of the configured score had data, so it is not rated.</p>}

      </>}

      {(showAdd || editing) && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 600, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{editing ? "Edit Driver" : "Register Driver"}</h3>
              <button onClick={closeModal} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 20 }}>
              <div>
                <label style={lbl}>First Name *</label>
                <input autoFocus value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} placeholder="Sipho" style={inp("firstName")} />
                {fieldErrors.firstName && <div style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{fieldErrors.firstName}</div>}
              </div>
              <div>
                <label style={lbl}>Last Name *</label>
                <input value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} placeholder="Ndlovu" style={inp("lastName")} />
                {fieldErrors.lastName && <div style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{fieldErrors.lastName}</div>}
              </div>
              <div>
                <label style={lbl}>Phone</label>
                <input value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} placeholder="+27 82 111 0001" style={inp("phone")} />
              </div>
              <div>
                <label style={lbl}>Email</label>
                <input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} placeholder="sipho@example.co.za" style={inp("email")} />
                <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>Used to notify the driver directly of expiring compliance documents</div>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>ID / Passport Number</label>
                <input value={form.idNumber} onChange={e => setForm(f => ({ ...f, idNumber: e.target.value }))} placeholder="8501015800080" style={inp("idNumber")} />
              </div>
            </div>

            <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid var(--hf-border-subtle)" }}>
              Driving Licence
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14, marginBottom: 20 }}>
              <div>
                <label style={lbl}>Licence Number</label>
                <input value={form.licenseNumber} onChange={e => setForm(f => ({ ...f, licenseNumber: e.target.value }))} placeholder="SN-2019-00123" style={inp("licenseNumber")} />
              </div>
              <div>
                <label style={lbl}>Code</label>
                <select value={form.licenseCode} onChange={e => setForm(f => ({ ...f, licenseCode: e.target.value }))} style={{ ...inp("licenseCode"), background: "var(--hf-surface)" }}>
                  {LICENSE_CODES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Expiry Date</label>
                <input type="date" value={form.licenseExpiry} onChange={e => setForm(f => ({ ...f, licenseExpiry: e.target.value }))} style={inp("licenseExpiry")} />
              </div>
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid var(--hf-border-subtle)" }}>
              <input type="checkbox" id="prdpRequired" checked={form.prdpRequired}
                onChange={e => setForm(f => ({ ...f, prdpRequired: e.target.checked }))}
                style={{ width: 16, height: 16, cursor: "pointer" }} />
              <label htmlFor="prdpRequired" style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-violet-text)", cursor: "pointer", textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>
                Requires Professional Driving Permit (PrDP)
              </label>
            </div>
            {form.prdpRequired && (
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14, marginBottom: 20, padding: 14, background: "var(--hf-violet-soft)", borderRadius: 10 }}>
                <div>
                  <label style={lbl}>PrDP Number</label>
                  <input value={form.prdpNumber} onChange={e => setForm(f => ({ ...f, prdpNumber: e.target.value }))} placeholder="PRDP-P-00456" style={inp("prdpNumber")} />
                </div>
                <div>
                  <label style={lbl}>Category</label>
                  <select value={form.prdpCategory} onChange={e => setForm(f => ({ ...f, prdpCategory: e.target.value }))} style={{ ...inp("prdpCategory"), background: "var(--hf-surface)" }}>
                    {PRDP_CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Expiry Date</label>
                  <input type="date" value={form.prdpExpiry} onChange={e => setForm(f => ({ ...f, prdpExpiry: e.target.value }))} style={inp("prdpExpiry")} />
                </div>
              </div>
            )}

            <div style={{ marginBottom: 14 }}>
              <label style={lbl} htmlFor="driver-department">Department</label>
              <input id="driver-department" value={form.department} maxLength={100} onChange={e => setForm(f => ({ ...f, department: e.target.value }))} style={inp("department")} placeholder="Operations, Logistics…" />
            </div>
            <div>
              <label style={lbl}>Notes</label>
              <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inp("notes"), resize: "vertical" as const }} placeholder="Any additional notes..." />
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={closeModal} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
              <button
                onClick={() => {
                  if (!validate()) return
                  const body = buildBody()
                  if (editing) updateDriver.mutate({ id: editing.id, body })
                  else createDriver.mutate(body)
                }}
                disabled={createDriver.isPending || updateDriver.isPending}
                style={{ padding: "9px 22px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createDriver.isPending || updateDriver.isPending ? "Saving..." : editing ? "Save Changes" : "Register Driver"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
