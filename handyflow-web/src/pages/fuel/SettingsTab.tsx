import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnPrimary, card, inputStyle, labelStyle } from "../warehousing/whseUi"
import { ACTIVITIES, activityLabel } from "./complianceOps"
import { controlsToForm, toControlsRequest, validateControls, type Controls, type ControlsForm } from "./wholesaleOps"
import { toForm, toRequest, validateSettings, type FuelSettings, type SettingsForm } from "./costToServeOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb

const FIELDS: { key: keyof SettingsForm; label: string; hint: string; group: "cost" | "po" }[] = [
  { key: "driverCostPerHour", label: "Driver cost per hour (R)", hint: "Wages plus on-costs for the time on a delivery.", group: "cost" },
  { key: "vehicleCostPerKm", label: "Vehicle cost per km (R)", hint: "Fuel, tyres, maintenance and depreciation. Applied to the round trip (twice the site distance).", group: "cost" },
  { key: "fixedCostPerDelivery", label: "Fixed cost per delivery (R)", hint: "Admin, paperwork, anything the same for every drop.", group: "cost" },
  { key: "overheadPctOfRevenue", label: "Overhead (% of revenue)", hint: "Your share of rent, insurance and management.", group: "cost" },
  { key: "defaultTripHours", label: "Typical trip hours", hint: "Used when a delivery has no loading or delivery time.", group: "cost" },
  { key: "poOverTolerancePct", label: "Supplier delivery tolerance (%)", hint: "How far over the litres ordered a receipt may go before it is refused. 0 to 10.", group: "po" },
]

function ControlsCard() {
  const canConfigure = usePermissions(["FUEL_CONFIGURATION_MANAGE"])
  const qc = useQueryClient()
  const q = useQuery<Controls>({ queryKey: ["fuel-settings-controls"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/settings/controls")) })
  const [f, setF] = useState<ControlsForm | null>(null)
  const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  useEffect(() => { if (q.data) setF(controlsToForm(q.data)) }, [q.data])
  const save = useMutation({
    mutationFn: (body: ReturnType<typeof toControlsRequest>) => apiClient.put("/api/v1/fuel/settings/controls", body),
    onSuccess: () => { setErr(""); setSaved(true); qc.invalidateQueries({ queryKey: ["fuel-settings-controls"] }) },
    onError: (e: any) => { setSaved(false); setErr(msg(e, "Could not save the controls")) },
  })
  if (!f) return null
  return (
    <div style={{ ...card, padding: 16, marginBottom: 14 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Approvals and controls</h3>
      <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>Purchase orders are approved by the rules you set under Approvals (module: fuel, type: PURCHASE_ORDER). With no rule, orders are placed straight away.</p>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 12 }}>
        <input type="checkbox" checked={f.requireSupplierApproval} disabled={!canConfigure} onChange={e => { setSaved(false); setF(s => s && ({ ...s, requireSupplierApproval: e.target.checked })) }} />
        New suppliers must be approved by someone else before they can be ordered from
      </label>
      <label style={labelStyle} htmlFor="fs-adjThreshold">Stock adjustments needing a second person (litres)</label>
      <input id="fs-adjThreshold" inputMode="decimal" style={{ ...inputStyle, maxWidth: 240 }} value={f.threshold} disabled={!canConfigure} placeholder="No limit" onChange={e => { setSaved(false); setF(s => s && ({ ...s, threshold: e.target.value })) }} />
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "3px 0 10px" }}>An adjustment of this many litres or more, up or down, is held until a different person with approval rights accepts it. Leave empty for no limit.</div>
      <label style={labelStyle} htmlFor="fs-varTol">Load variance tolerance (%)</label>
      <input id="fs-varTol" inputMode="decimal" style={{ ...inputStyle, maxWidth: 240 }} value={f.variance} disabled={!canConfigure} placeholder="Not set" onChange={e => { setSaved(false); setF(s => s && ({ ...s, variance: e.target.value })) }} />
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "3px 0 10px" }}>A delivery whose litres delivered differ from litres loaded by more than this percentage is raised on the Load variances page for review. Leave empty and no variances are raised. There is no default: set the figure your own rules require.</div>
      <label style={labelStyle} htmlFor="fs-cashTol">Forecourt cash tolerance (R)</label>
      <input id="fs-cashTol" inputMode="decimal" style={{ ...inputStyle, maxWidth: 240 }} value={f.cashTol} disabled={!canConfigure} onChange={e => { setSaved(false); setF(s => s && ({ ...s, cashTol: e.target.value })) }} />
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "3px 0 10px" }}>A shift whose takings are short or over by more than this needs a reason to close and is held for a supervisor's review. It starts at R 5.00, which is what the system used before this was configurable.</div>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 12 }}>
        <input type="checkbox" checked={f.separateCloser} disabled={!canConfigure} onChange={e => { setSaved(false); setF(s => s && ({ ...s, separateCloser: e.target.checked })) }} />
        The person who opens a shift cannot be the person who closes it
      </label>
      <label style={labelStyle} htmlFor="fs-priceChange">Price change needing approval (%)</label>
      <input id="fs-priceChange" inputMode="decimal" style={{ ...inputStyle, maxWidth: 240 }} value={f.priceChange} disabled={!canConfigure} placeholder="Off" onChange={e => { setSaved(false); setF(s => s && ({ ...s, priceChange: e.target.value })) }} />
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "3px 0 10px" }}>A new price that moves by more than this percentage from the price it replaces is held until it is approved, and does not apply until then. Leave empty to switch the check off.</div>
      <label style={labelStyle} htmlFor="fs-minMargin">Minimum margin (%)</label>
      <input id="fs-minMargin" inputMode="decimal" style={{ ...inputStyle, maxWidth: 240 }} value={f.minMargin} disabled={!canConfigure} placeholder="Off" onChange={e => { setSaved(false); setF(s => s && ({ ...s, minMargin: e.target.value })) }} />
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "3px 0 10px" }}>A new price whose margin over the latest purchase cost of that fuel is below this percentage is held until it is approved. If no purchase has been received for that fuel the check cannot run. Leave empty to switch it off.</div>
      <fieldset style={{ border: "none", padding: 0, margin: "0 0 10px" }}>
        <legend style={labelStyle}>Licences that stop loading when expired</legend>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 12 }}>
          {ACTIVITIES.map(a => (
            <label key={a} style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}>
              <input type="checkbox" checked={f.licenceBlocking.includes(a)} disabled={!canConfigure}
                onChange={e => { setSaved(false); setF(s => s && ({ ...s, licenceBlocking: e.target.checked ? [...s.licenceBlocking, a] : s.licenceBlocking.filter(x => x !== a) })) }} />
              {activityLabel(a)}
            </label>
          ))}
        </div>
        <div style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "3px 0 0" }}>A load cannot be allocated while a licence for a ticked activity has expired for the legal entity that owns the loading depot (or is linked to that depot). Nothing is ticked by default, so nothing changes until you choose. The system does not decide which licences your business needs.</div>
      </fieldset>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {saved && <p role="status" style={{ color: "var(--hf-success-text-strong)", fontSize: 12 }}>Saved.</p>}
      {canConfigure
        ? <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateControls(f); if (v) { setSaved(false); return setErr(v) } setErr(""); save.mutate(toControlsRequest(f)) }}>Save controls</button>
        : <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Only someone who can manage Fuel configuration can change these.</p>}
    </div>
  )
}

export default function SettingsTab() {
  const canManage = usePermissions(["FUEL_MANAGE"])
  const qc = useQueryClient()
  const q = useQuery<FuelSettings>({ queryKey: ["fuel-settings"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/settings")) })
  const [f, setF] = useState<SettingsForm | null>(null)
  const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  useEffect(() => { if (q.data) setF(toForm(q.data)) }, [q.data])
  const save = useMutation({
    mutationFn: (body: ReturnType<typeof toRequest>) => apiClient.put("/api/v1/fuel/settings", body),
    onSuccess: () => { setErr(""); setSaved(true); qc.invalidateQueries({ queryKey: ["fuel-settings"] }); qc.invalidateQueries({ queryKey: ["fuel-cost-to-serve"] }) },
    onError: (e: any) => { setSaved(false); setErr(msg(e, "Could not save the settings")) },
  })
  if (q.isLoading || !f) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  const set = (k: keyof SettingsForm) => (e: React.ChangeEvent<HTMLInputElement>) => { setSaved(false); setF(s => s ? { ...s, [k]: e.target.value } : s) }
  const block = (group: "cost" | "po", title: string, intro: string) => (
    <div style={{ ...card, padding: 16, marginBottom: 14 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>{title}</h3>
      <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>{intro}</p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 12 }}>
        {FIELDS.filter(x => x.group === group).map(x => (
          <div key={x.key}>
            <label style={labelStyle} htmlFor={`fs-${x.key}`}>{x.label}</label>
            <input id={`fs-${x.key}`} inputMode="decimal" value={f[x.key]} onChange={set(x.key)} disabled={!canManage} style={inputStyle} />
            <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 3 }}>{x.hint}</div>
          </div>
        ))}
      </div>
    </div>
  )
  return (
    <div>
      <ControlsCard />
      {block("cost", "Cost to serve", "Rates used to turn margin into net margin on the Cost to serve page. Leave a rate at 0 to leave that cost out.")}
      {block("po", "Purchasing", "Applies to receipts against purchase orders.")}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {saved && <p role="status" style={{ color: "var(--hf-success-text-strong)", fontSize: 12 }}>Saved.</p>}
      {canManage
        ? <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateSettings(f); if (v) { setSaved(false); return setErr(v) } setErr(""); save.mutate(toRequest(f)) }}>Save settings</button>
        : <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>You can view these settings but not change them.</p>}
    </div>
  )
}
