// Eligibility set-up: which tankers and drivers a delivery site lets in, and which products a tanker compartment may carry.
// Every limit here is entered by the company; nothing is pre-filled from a regulation. Nothing set means nothing restricted.
import { useEffect, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import OverrideLog from "./OverrideLog"
import { validateComplianceFile } from "./complianceOps"
import type { CustomerAccount, CustomerSite } from "./customerOps"
import type { Tanker, Driver } from "./transportOps"
import {
  APPROVAL_STATUSES, STATE_LABEL, stateBlocks, emptySiteForm, validateSiteForm, toSiteRequest, describeSiteRule, emptyDriverForm, fromApproval, validateDriverForm, toDriverRequest,
  validateSpecs, toSpecsRequest, parseProducts, ratingText, validateChangeover, validateCleaning, certificateText,
  type SiteRule, type SiteRuleForm, type DriverApproval, type DriverForm, type SpecsForm, type CompartmentRating, type ChangeoverRule, type Cleaning,
} from "./eligibilityOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null
const row: React.CSSProperties = { display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 8 }
const field: React.CSSProperties = { flex: "1 1 140px", minWidth: 120 }

export default function EligibilityTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_TRANSPORT_MANAGE"])
  const canSeeLog = usePermissions(["FUEL_ELIGIBILITY_REVIEW", "FUEL_COMPLIANCE_MANAGE", "FUEL_TRANSPORT_MANAGE", "FUEL_MANAGE"])
  const [part, setPart] = useState<"sites" | "tankers" | "rules" | "log">("sites")
  return (
    <div>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px", maxWidth: 760 }}>
        What a customer site lets in, and what each tanker compartment may carry. These are your own and your customers' rules. A load is blocked, with no override, when a rule is broken; it needs a person with the review permission and a reason when something the rule needs is not recorded. A site or compartment with nothing set is not restricted.
      </p>
      <div role="tablist" style={{ display: "flex", gap: 8, marginBottom: 12 }}>
        {([["sites", "Customer sites"], ["tankers", "Tankers and compartments"], ["rules", "Changeover rules"], ...(canSeeLog ? [["log", "Review decisions"] as const] : [])] as const).map(([id, label]) =>
          <button key={id} role="tab" aria-selected={part === id} onClick={() => setPart(id)} style={part === id ? btnPrimary : btnGhost}>{label}</button>)}
      </div>
      {part === "sites" && <SitesPart canManage={canManage} />}
      {part === "tankers" && <TankersPart canManage={canManage} />}
      {part === "rules" && <RulesPart canManage={canManage} />}
      {part === "log" && <OverrideLog />}
    </div>
  )
}

function SitesPart({ canManage }: { canManage: boolean }) {
  const [accountId, setAccountId] = useState(""); const [siteId, setSiteId] = useState("")
  const accounts = useQuery<CustomerAccount[]>({ queryKey: ["fuel-customer-accounts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/customer-accounts")) })
  const detail = useQuery<CustomerAccount>({ queryKey: ["fuel-customer-account", accountId], enabled: !!accountId, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/customer-accounts/${accountId}`)) })
  const sites: CustomerSite[] = detail.data?.sites ?? []
  return (
    <div>
      <div style={row}>
        <div style={field}><label style={labelStyle}>Customer</label>
          <select aria-label="Customer" value={accountId} onChange={e => { setAccountId(e.target.value); setSiteId("") }} style={inputStyle}>
            <option value="">Choose…</option>{(accounts.data ?? []).map(a => <option key={a.id} value={a.id}>{a.customerName}</option>)}
          </select></div>
        <div style={field}><label style={labelStyle}>Delivery site</label>
          <select aria-label="Site" value={siteId} onChange={e => setSiteId(e.target.value)} style={inputStyle} disabled={!accountId}>
            <option value="">Choose…</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select></div>
      </div>
      {siteId && <><SiteRuleEditor key={siteId} siteId={siteId} canManage={canManage} /><DriverApprovals key={"d" + siteId} siteId={siteId} canManage={canManage} /></>}
    </div>
  )
}

function SiteRuleEditor({ siteId, canManage }: { siteId: string; canManage: boolean }) {
  const qc = useQueryClient(); const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  const q = useQuery<SiteRule>({ queryKey: ["fuel-site-rule", siteId], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/customer-sites/${siteId}/access-rule`)) })
  const tankers = useQuery<Tanker[]>({ queryKey: ["fuel-tankers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tankers")) })
  const [f, setF] = useState<SiteRuleForm>(emptySiteForm())
  useEffect(() => { if (q.data) setF(emptySiteForm(q.data)) }, [q.data])
  const set = (k: keyof SiteRuleForm, v: any) => { setSaved(false); setF(p => ({ ...p, [k]: v })) }
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/fuel/customer-sites/${siteId}/access-rule`, toSiteRequest(f)),
    onSuccess: () => { setErr(""); setSaved(true); qc.invalidateQueries({ queryKey: ["fuel-site-rule", siteId] }) }, onError: (e: any) => setErr(msg(e, "Could not save the site rule")),
  })
  const num = (label: string, k: keyof SiteRuleForm, unit: string) => <div style={field}><label style={labelStyle}>{label} ({unit})</label>
    <input aria-label={label} value={f[k] as string} disabled={!canManage} onChange={e => set(k, e.target.value)} style={inputStyle} placeholder="No limit" /></div>
  if (q.isLoading || !q.data) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading the site rule…</p>
  return (
    <div style={{ ...card, padding: 14, marginBottom: 14 }}>
      <strong style={{ fontSize: 13 }}>Tanker limits at this site</strong>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>{describeSiteRule(f)}</p>
      <div style={row}>{num("Maximum capacity", "cap", "L")}{num("Maximum gross vehicle mass", "gvm", "kg")}</div>
      <div style={row}>{num("Maximum length", "len", "m")}{num("Maximum width", "wid", "m")}{num("Maximum height", "hgt", "m")}</div>
      <label style={labelStyle}>Tanker types accepted (comma separated, empty for any)</label>
      <input aria-label="Tanker types" value={f.types} disabled={!canManage} onChange={e => set("types", e.target.value)} style={inputStyle} placeholder="e.g. RIGID, TANKER_TRAILER" />
      <label style={{ ...labelStyle, marginTop: 10 }}>Approved tankers (none ticked means any tanker)</label>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
        {(tankers.data ?? []).filter(t => t.active).map(t => <label key={t.id} style={{ fontSize: 13 }}>
          <input type="checkbox" aria-label={`Approve ${t.registration}`} disabled={!canManage} checked={f.tankerIds.includes(t.id)} onChange={e => set("tankerIds", e.target.checked ? [...f.tankerIds, t.id] : f.tankerIds.filter(x => x !== t.id))} /> {t.registration}</label>)}
      </div>
      <label style={{ ...labelStyle, marginTop: 10 }}>Special access requirements</label>
      <input aria-label="Access notes" value={f.notes} disabled={!canManage} onChange={e => set("notes", e.target.value)} style={inputStyle} />
      <label style={{ display: "block", fontSize: 13, marginTop: 10 }}>
        <input type="checkbox" aria-label="Require approved drivers" disabled={!canManage} checked={f.requireDrivers} onChange={e => set("requireDrivers", e.target.checked)} /> Only drivers inducted at this site may deliver here</label>
      <label style={{ ...labelStyle, marginTop: 8 }}>Safety training the site requires (name it, empty for none)</label>
      <input aria-label="Required training" value={f.training} disabled={!canManage || !f.requireDrivers} onChange={e => set("training", e.target.value)} style={inputStyle} />
      <Err text={err} />
      {saved && <p role="status" style={{ fontSize: 12, color: "var(--hf-success-text)" }}>Saved.</p>}
      {canManage && <div style={{ marginTop: 12 }}><button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateSiteForm(f); if (v) return setErr(v); setErr(""); save.mutate() }}>Save site rule</button></div>}
    </div>
  )
}

function DriverApprovals({ siteId, canManage }: { siteId: string; canManage: boolean }) {
  const qc = useQueryClient(); const [err, setErr] = useState(""); const [f, setF] = useState<DriverForm | null>(null)
  const q = useQuery<DriverApproval[]>({ queryKey: ["fuel-site-drivers", siteId], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/customer-sites/${siteId}/driver-approvals`)) ?? [] })
  const drivers = useQuery<Driver[]>({ queryKey: ["fuel-drivers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/drivers")) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["fuel-site-drivers", siteId] })
  const save = useMutation({
    mutationFn: (x: DriverForm) => apiClient.put(`/api/v1/fuel/customer-sites/${siteId}/driver-approvals/${x.employeeId}`, toDriverRequest(x)),
    onSuccess: () => { setErr(""); setF(null); refresh() }, onError: (e: any) => setErr(msg(e, "Could not save the approval")),
  })
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/fuel/customer-sites/${siteId}/driver-approvals/${id}`),
    onSuccess: refresh, onError: (e: any) => setErr(msg(e, "Could not remove the approval")),
  })
  const set = (k: keyof DriverForm, v: string) => setF(p => p && { ...p, [k]: v })
  return (
    <div style={{ ...card, padding: 14 }}>
      <div style={{ display: "flex", alignItems: "center" }}><strong style={{ fontSize: 13, flex: 1 }}>Drivers inducted at this site</strong>
        {canManage && !f && <button style={btnGhost} onClick={() => setF(emptyDriverForm())}>Add a driver</button>}</div>
      {(q.data ?? []).length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No drivers recorded.</p> : (
        <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 8 }}>
          <thead><tr>{["Driver", "Status", "Induction expires", "Training expires", ""].map(h => <th key={h} style={{ textAlign: "left", fontSize: 11, padding: "6px 8px", color: "var(--hf-text-muted)" }}>{h}</th>)}</tr></thead>
          <tbody>{(q.data ?? []).map(a => (
            <tr key={a.employeeId}>
              <td style={{ padding: "6px 8px", fontSize: 13 }}>{a.driverName}{a.conditions ? <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{a.conditions}</div> : null}</td>
              <td style={{ padding: "6px 8px" }}><Chip bg={stateBlocks(a.state) ? "var(--hf-danger-soft)" : a.state === "OK" ? "var(--hf-success-soft-strong)" : "var(--hf-warning-bg)"} fg={stateBlocks(a.state) ? "var(--hf-danger-text)" : a.state === "OK" ? "var(--hf-success-text-strong)" : "var(--hf-warning-text)"}>{STATE_LABEL[a.state] ?? a.state}</Chip></td>
              <td style={{ padding: "6px 8px", fontSize: 13 }}>{a.inductionExpiry ?? "–"}</td><td style={{ padding: "6px 8px", fontSize: 13 }}>{a.trainingExpiry ?? "–"}</td>
              <td style={{ padding: "6px 8px", whiteSpace: "nowrap" }}>{canManage && <><button style={btnGhost} onClick={() => setF(fromApproval(a))}>Edit</button> <button style={btnGhost} onClick={() => remove.mutate(a.employeeId)}>Remove</button></>}</td>
            </tr>))}</tbody>
        </table>)}
      {f && (
        <div style={{ marginTop: 12, borderTop: "1px solid var(--hf-border)", paddingTop: 10 }}>
          <div style={row}>
            <div style={field}><label style={labelStyle}>Driver *</label>
              <select aria-label="Approved driver" value={f.employeeId} onChange={e => set("employeeId", e.target.value)} style={inputStyle}>
                <option value="">Choose…</option>{(drivers.data ?? []).map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
            <div style={field}><label style={labelStyle}>Status</label>
              <select aria-label="Approval status" value={f.status} onChange={e => set("status", e.target.value)} style={inputStyle}>{APPROVAL_STATUSES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div>
          </div>
          <div style={row}>
            <div style={field}><label style={labelStyle}>Induction date</label><input aria-label="Induction date" type="date" value={f.inductionDate} onChange={e => set("inductionDate", e.target.value)} style={inputStyle} /></div>
            <div style={field}><label style={labelStyle}>Induction expires</label><input aria-label="Induction expires" type="date" value={f.inductionExpiry} onChange={e => set("inductionExpiry", e.target.value)} style={inputStyle} /></div>
          </div>
          <div style={row}>
            <div style={field}><label style={labelStyle}>Training reference</label><input aria-label="Training reference" value={f.trainingRef} onChange={e => set("trainingRef", e.target.value)} style={inputStyle} /></div>
            <div style={field}><label style={labelStyle}>Training expires</label><input aria-label="Training expires" type="date" value={f.trainingExpiry} onChange={e => set("trainingExpiry", e.target.value)} style={inputStyle} /></div>
          </div>
          <label style={labelStyle}>Conditions, restriction or reason</label>
          <input aria-label="Conditions" value={f.conditions} onChange={e => set("conditions", e.target.value)} style={inputStyle} />
          <Err text={err} />
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 10 }}>
            <button style={btnGhost} onClick={() => { setF(null); setErr("") }}>Cancel</button>
            <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateDriverForm(f); if (v) return setErr(v); setErr(""); save.mutate(f) }}>Save driver</button>
          </div>
        </div>)}
      {!f && <Err text={err} />}
    </div>
  )
}

function TankersPart({ canManage }: { canManage: boolean }) {
  const [tankerId, setTankerId] = useState("")
  const tankers = useQuery<Tanker[]>({ queryKey: ["fuel-tankers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tankers")) })
  return (
    <div>
      <div style={{ ...row, maxWidth: 320 }}><div style={field}><label style={labelStyle}>Tanker</label>
        <select aria-label="Tanker to set up" value={tankerId} onChange={e => setTankerId(e.target.value)} style={inputStyle}>
          <option value="">Choose…</option>{(tankers.data ?? []).map(t => <option key={t.id} value={t.id}>{t.registration}</option>)}</select></div></div>
      {tankerId && <><SpecsEditor key={tankerId} tankerId={tankerId} canManage={canManage} /><Compartments key={"c" + tankerId} tankerId={tankerId} canManage={canManage} /></>}
    </div>
  )
}

function SpecsEditor({ tankerId, canManage }: { tankerId: string; canManage: boolean }) {
  const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  const q = useQuery<any>({ queryKey: ["fuel-tanker-specs", tankerId], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/tankers/${tankerId}/specs`)) })
  const [f, setF] = useState<SpecsForm>({ type: "", gvm: "", len: "", wid: "", hgt: "" })
  useEffect(() => { const s = q.data; if (s) setF({ type: s.tankerType ?? "", gvm: s.gvmKg == null ? "" : String(s.gvmKg), len: s.lengthM == null ? "" : String(s.lengthM), wid: s.widthM == null ? "" : String(s.widthM), hgt: s.heightM == null ? "" : String(s.heightM) }) }, [q.data])
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/fuel/tankers/${tankerId}/specs`, toSpecsRequest(f)),
    onSuccess: () => { setErr(""); setSaved(true) }, onError: (e: any) => setErr(msg(e, "Could not save the specifications")),
  })
  const inp = (label: string, k: keyof SpecsForm, ph?: string) => <div style={field}><label style={labelStyle}>{label}</label>
    <input aria-label={label} value={f[k]} disabled={!canManage} placeholder={ph} onChange={e => { setSaved(false); setF(p => ({ ...p, [k]: e.target.value })) }} style={inputStyle} /></div>
  if (q.isLoading || !q.data) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading the specifications…</p>
  return (
    <div style={{ ...card, padding: 14, marginBottom: 14 }}>
      <strong style={{ fontSize: 13 }}>Tanker specifications</strong>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>A site that limits mass, size or type can only be checked against what is recorded here.</p>
      <div style={row}>{inp("Tanker type", "type", "e.g. RIGID")}{inp("Gross vehicle mass (kg)", "gvm")}</div>
      <div style={row}>{inp("Length (m)", "len")}{inp("Width (m)", "wid")}{inp("Height (m)", "hgt")}</div>
      <Err text={err} />{saved && <p role="status" style={{ fontSize: 12 }}>Saved.</p>}
      {canManage && <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateSpecs(f); if (v) return setErr(v); setErr(""); save.mutate() }}>Save specifications</button>}
    </div>
  )
}

function Compartments({ tankerId, canManage }: { tankerId: string; canManage: boolean }) {
  const qc = useQueryClient(); const [err, setErr] = useState("")
  const [products, setProducts] = useState<Record<string, string>>({}); const [open, setOpen] = useState<string | null>(null)
  const q = useQuery<CompartmentRating[]>({ queryKey: ["fuel-compartment-ratings", tankerId], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/tankers/${tankerId}/compartment-ratings`)) ?? [] })
  useEffect(() => { setProducts(Object.fromEntries((q.data ?? []).map(c => [c.compartmentId, c.fuelTypes.join(", ")]))) }, [q.data])
  const save = useMutation({
    mutationFn: (c: CompartmentRating) => apiClient.put(`/api/v1/fuel/tankers/${tankerId}/compartments/${c.compartmentId}/rating`, { fuelTypes: parseProducts(products[c.compartmentId] ?? "") }),
    onSuccess: () => { setErr(""); qc.invalidateQueries({ queryKey: ["fuel-compartment-ratings", tankerId] }) }, onError: (e: any) => setErr(msg(e, "Could not save the rating")),
  })
  return (
    <div style={{ ...card, padding: 14 }}>
      <strong style={{ fontSize: 13 }}>Compartments: products and cleaning</strong>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>List the products each compartment is approved to carry, separated by commas. Empty means it is not restricted. Product is checked per compartment, not per vehicle.</p>
      {(q.data ?? []).map(c => (
        <div key={c.compartmentId} style={{ borderTop: "1px solid var(--hf-border)", padding: "10px 0" }}>
          <div style={{ display: "flex", gap: 10, alignItems: "end", flexWrap: "wrap" }}>
            <div style={{ width: 150, fontSize: 13 }}><strong>Compartment {c.number}</strong><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{c.capacityLitres} L</div></div>
            <div style={{ flex: 1, minWidth: 200 }}><label style={labelStyle}>Approved products</label>
              <input aria-label={`Products for compartment ${c.number}`} value={products[c.compartmentId] ?? ""} disabled={!canManage} onChange={e => setProducts(p => ({ ...p, [c.compartmentId]: e.target.value }))} style={inputStyle} placeholder="e.g. DIESEL, PETROL" /></div>
            {canManage && <button style={btnGhost} onClick={() => save.mutate(c)}>Save rating</button>}
            <button style={btnGhost} onClick={() => setOpen(open === c.compartmentId ? null : c.compartmentId)}>Cleanings</button>
          </div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>
            {ratingText(c)}. {c.lastCargo ? `Last carried ${c.lastCargo}${c.lastCargoAt ? " on " + c.lastCargoAt.slice(0, 10) : ""}.` : "No previous load recorded."} {c.lastCleanedAt ? `Last cleaned ${c.lastCleanedAt.slice(0, 10)}.` : "No cleaning recorded."}
          </div>
          {open === c.compartmentId && <CleaningPanel tankerId={tankerId} compartmentId={c.compartmentId} canManage={canManage} />}
        </div>))}
      <Err text={err} />
    </div>
  )
}

function CleaningPanel({ tankerId, compartmentId, canManage }: { tankerId: string; compartmentId: string; canManage: boolean }) {
  const qc = useQueryClient(); const [err, setErr] = useState("")
  const canVerify = usePermissions(["FUEL_COMPLIANCE_VERIFY"])
  const canSeeFile = usePermissions(["FUEL_MANAGE", "FUEL_TRANSPORT_MANAGE", "FUEL_COMPLIANCE_MANAGE", "FUEL_COMPLIANCE_VERIFY"])
  const base = `/api/v1/fuel/tankers/${tankerId}/compartments/${compartmentId}/cleanings`
  const refreshList = () => { qc.invalidateQueries({ queryKey: ["fuel-cleanings", compartmentId] }); qc.invalidateQueries({ queryKey: ["fuel-compartment-ratings", tankerId] }) }
  const upload = useMutation({
    mutationFn: (a: { id: string; file: File }) => { const f = new FormData(); f.append("file", a.file); return apiClient.post(`${base}/${a.id}/certificate`, f) },
    onSuccess: () => { setErr(""); refreshList() }, onError: (e: any) => setErr(msg(e, "Could not upload the certificate")),
  })
  const verify = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/verify`), onSuccess: () => { setErr(""); refreshList() }, onError: (e: any) => setErr(msg(e, "Could not verify it")) })
  const view = async (id: string) => {
    try {
      const r = await apiClient.get(`${base}/${id}/certificate`, { responseType: "blob" })
      const url = URL.createObjectURL(r.data as Blob); window.open(url, "_blank", "noopener"); setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (e: any) { setErr(msg(e, "Could not open the file")) }
  }
  const [at, setAt] = useState(""); const [method, setMethod] = useState(""); const [cert, setCert] = useState(""); const [evid, setEvid] = useState("")
  const q = useQuery<Cleaning[]>({ queryKey: ["fuel-cleanings", compartmentId], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/tankers/${tankerId}/compartments/${compartmentId}/cleanings`)) ?? [] })
  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/tankers/${tankerId}/compartments/${compartmentId}/cleanings`, { cleanedAt: new Date(at).toISOString(), method: method.trim() || null, certificateRef: cert.trim() || null, evidenceNote: evid.trim() || null }),
    onSuccess: () => { setErr(""); setAt(""); setMethod(""); setCert(""); setEvid(""); qc.invalidateQueries({ queryKey: ["fuel-cleanings", compartmentId] }); qc.invalidateQueries({ queryKey: ["fuel-compartment-ratings", tankerId] }) },
    onError: (e: any) => setErr(msg(e, "Could not record the cleaning")),
  })
  return (
    <div style={{ background: "var(--hf-surface-alt, transparent)", padding: "8px 0 0 12px" }}>
      {(q.data ?? []).length === 0 ? <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>No cleanings recorded.</p>
        : <ul style={{ fontSize: 12.5, margin: "0 0 8px", paddingLeft: 18 }}>{(q.data ?? []).map(c => <li key={c.id} style={{ marginBottom: 4 }}>{c.cleanedAt.slice(0, 16).replace("T", " ")}{c.method ? ` · ${c.method}` : ""}{c.certificateRef ? ` · certificate ${c.certificateRef}` : ""}{c.evidenceNote ? ` · ${c.evidenceNote}` : ""}
          {" "}<Chip bg={c.fileStored ? (c.verifiedAt ? "var(--hf-success-soft-strong)" : "var(--hf-warning-soft)") : "var(--hf-border)"} fg={c.fileStored ? (c.verifiedAt ? "var(--hf-success-text-strong)" : "var(--hf-warning-text)") : "var(--hf-text-muted)"}>{certificateText(c)}</Chip>
          {c.fileStored && canSeeFile && <button style={{ ...btnGhost, marginLeft: 6 }} onClick={() => view(c.id)}>{`View certificate for ${c.cleanedAt.slice(0, 10)}`}</button>}
          {c.fileStored && !c.verifiedAt && canVerify && <button style={{ ...btnGhost, marginLeft: 6 }} disabled={verify.isPending} onClick={() => verify.mutate(c.id)}>{`Verify certificate for ${c.cleanedAt.slice(0, 10)}`}</button>}
          {!c.fileStored && canManage && <label style={{ marginLeft: 6, fontSize: 12 }}>Upload certificate <input type="file" aria-label={`Certificate file for ${c.cleanedAt.slice(0, 10)}`} accept="image/jpeg,image/png,image/webp,application/pdf" onChange={e => { const file = e.target.files?.[0] ?? null; const v = validateComplianceFile(file); if (v) { setErr(v); return } setErr(""); upload.mutate({ id: c.id, file: file! }) }} /></label>}
        </li>)}</ul>}
      {canManage && <>
        <div style={row}>
          <div style={field}><label style={labelStyle}>Cleaned at *</label><input aria-label="Cleaned at" type="datetime-local" value={at} onChange={e => setAt(e.target.value)} style={inputStyle} /></div>
          <div style={field}><label style={labelStyle}>Method</label><input aria-label="Cleaning method" value={method} onChange={e => setMethod(e.target.value)} style={inputStyle} /></div>
        </div>
        <div style={row}>
          <div style={field}><label style={labelStyle}>Certificate reference</label><input aria-label="Certificate reference" value={cert} onChange={e => setCert(e.target.value)} style={inputStyle} /></div>
          <div style={field}><label style={labelStyle}>Or describe the evidence</label><input aria-label="Evidence" value={evid} onChange={e => setEvid(e.target.value)} style={inputStyle} /></div>
        </div>
        <Err text={err} />
        <button style={btnPrimary} disabled={add.isPending} onClick={() => { const v = validateCleaning(at, cert, evid); if (v) return setErr(v); setErr(""); add.mutate() }}>Record cleaning</button>
        <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>A cleaning cannot be edited once recorded. If it was wrong, record the correct one after it.</p>
      </>}
    </div>
  )
}

function RulesPart({ canManage }: { canManage: boolean }) {
  const qc = useQueryClient(); const [err, setErr] = useState("")
  const [from, setFrom] = useState(""); const [to, setTo] = useState(""); const [note, setNote] = useState("")
  const rules = useQuery<ChangeoverRule[]>({ queryKey: ["fuel-changeover-rules"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/changeover-rules")) ?? [] })
  const settings = useQuery<{ requireCompartmentRating: boolean; requireVerifiedCleaning?: boolean }>({ queryKey: ["fuel-eligibility-settings"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/eligibility/settings")) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["fuel-changeover-rules"] })
  const add = useMutation({
    mutationFn: () => apiClient.post("/api/v1/fuel/changeover-rules", { fromFuel: from.trim(), toFuel: to.trim(), note: note.trim() || null }),
    onSuccess: () => { setErr(""); setFrom(""); setTo(""); setNote(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not add the rule")),
  })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/fuel/changeover-rules/${id}`), onSuccess: refresh, onError: (e: any) => setErr(msg(e, "Could not remove the rule")) })
  const toggle = useMutation({
    mutationFn: (on: boolean) => apiClient.put("/api/v1/fuel/eligibility/settings", { requireCompartmentRating: on }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fuel-eligibility-settings"] }), onError: (e: any) => setErr(msg(e, "Could not save the setting")),
  })
  const toggleVerified = useMutation({
    mutationFn: (on: boolean) => apiClient.put("/api/v1/fuel/eligibility/settings", { requireCompartmentRating: settings.data?.requireCompartmentRating ?? false, requireVerifiedCleaning: on }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fuel-eligibility-settings"] }), onError: (e: any) => setErr(msg(e, "Could not save the setting")),
  })
  return (
    <div>
      <div style={{ ...card, padding: 14, marginBottom: 14 }}>
        <strong style={{ fontSize: 13 }}>Product changeover</strong>
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>Say which changes of product need a cleaning in between, for example diesel then paraffin. A load is blocked until a cleaning, with its certificate or evidence, is recorded after the compartment's previous load. Pairs you do not list need nothing.</p>
        {(rules.data ?? []).length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No changeover rules, so no cleaning is required.</p>
          : <ul style={{ paddingLeft: 18, fontSize: 13 }}>{(rules.data ?? []).map(r => <li key={r.id}>{r.fromFuel} then {r.toFuel} needs a cleaning{r.note ? ` (${r.note})` : ""} {canManage && <button style={btnGhost} onClick={() => remove.mutate(r.id)}>Remove</button>}</li>)}</ul>}
        {canManage && <>
          <div style={row}>
            <div style={field}><label style={labelStyle}>Carried before</label><input aria-label="From product" value={from} onChange={e => setFrom(e.target.value)} style={inputStyle} placeholder="DIESEL" /></div>
            <div style={field}><label style={labelStyle}>Going in next</label><input aria-label="To product" value={to} onChange={e => setTo(e.target.value)} style={inputStyle} placeholder="PARAFFIN" /></div>
            <div style={{ ...field, flex: "2 1 220px" }}><label style={labelStyle}>Note</label><input aria-label="Rule note" value={note} onChange={e => setNote(e.target.value)} style={inputStyle} /></div>
          </div>
          <Err text={err} />
          <button style={btnPrimary} disabled={add.isPending} onClick={() => { const v = validateChangeover(from, to); if (v) return setErr(v); setErr(""); add.mutate() }}>Add rule</button>
        </>}
      </div>
      <div style={{ ...card, padding: 14 }}>
        <label style={{ fontSize: 13 }}>
          <input type="checkbox" aria-label="Require ratings" disabled={!canManage || settings.isLoading} checked={settings.data?.requireCompartmentRating ?? false} onChange={e => toggle.mutate(e.target.checked)} /> Require every compartment to have a product rating</label>
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 0" }}>When on, a load that uses a compartment with no rating needs a review by someone with the review permission. Off by default.</p>
      </div>
      <div style={{ ...card, padding: 14, marginTop: 14 }}>
        <label style={{ fontSize: 13 }}>
          <input type="checkbox" aria-label="Require verified certificates" disabled={!canManage || settings.isLoading} checked={settings.data?.requireVerifiedCleaning ?? false} onChange={e => toggleVerified.mutate(e.target.checked)} /> Require cleaning certificates to be uploaded and verified</label>
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 0" }}>When on, a cleaning clears a product changeover only once its certificate file is uploaded and a second person has verified it. A certificate reference or note alone is no longer enough. Off by default, so cleanings recorded today keep working.</p>
      </div>
    </div>
  )
}
