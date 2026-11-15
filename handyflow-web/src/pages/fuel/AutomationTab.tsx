// Phase 6b: reorder rules and alerts the company sets up itself. A rule can only draft a requisition; people submit, approve and order.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { emptyRule, fromRule, validateRule, toRuleRequest, runText, validateReviewDays, reviewDaysValue, type Rule, type RuleForm, type LogRow, type RunResult } from "./automationOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }

export default function AutomationTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_PURCHASING_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<{ rule?: Rule } | null>(null)
  const [err, setErr] = useState(""); const [ran, setRan] = useState<RunResult | null>(null)
  const rules = useQuery<Rule[]>({ queryKey: ["fuel-automation-rules"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/automation/rules")) })
  const log = useQuery<LogRow[]>({ queryKey: ["fuel-automation-log"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/automation/log")) })
  const settings = useQuery<{ alertOverduePurchaseOrders: boolean; varianceReviewDays?: number | null }>({ queryKey: ["fuel-automation-settings"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/automation/settings")) })
  const refresh = () => { for (const k of ["fuel-automation-rules", "fuel-automation-log", "fuel-requisitions"]) qc.invalidateQueries({ queryKey: [k] }) }
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/fuel/automation/rules/${id}`), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not remove the rule")) })
  const toggle = useMutation({ mutationFn: (r: Rule) => apiClient.put(`/api/v1/fuel/automation/rules/${r.id}`, { ...toRuleRequest(fromRule(r), false), enabled: !r.enabled }), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not change the rule")) })
  const run = useMutation({ mutationFn: async () => unwrap(await apiClient.post("/api/v1/fuel/automation/run")) as RunResult, onSuccess: r => { setErr(""); setRan(r); refresh() }, onError: (e: any) => setErr(msg(e, "Could not run the rules")) })
  const [days, setDays] = useState<string | null>(null)
  const daysText = days ?? (settings.data?.varianceReviewDays == null ? "" : String(settings.data.varianceReviewDays))
  const saveSettings = useMutation({
    mutationFn: (v: { alert: boolean; days: number | null }) => apiClient.put("/api/v1/fuel/automation/settings", { alertOverduePurchaseOrders: v.alert, varianceReviewDays: v.days }),
    onSuccess: () => { setErr(""); setDays(null); qc.invalidateQueries({ queryKey: ["fuel-automation-settings"] }) }, onError: (e: any) => setErr(msg(e, "Could not save")),
  })
  const currentDays = settings.data?.varianceReviewDays ?? null
  const rows = rules.data ?? []
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14, flexWrap: "wrap" }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1, minWidth: 260 }}>
          Rules you set. Every morning, any tank below the level you chose gets a draft purchase requisition. The draft is only a draft: nothing is submitted, approved or ordered for you, and a tank that already has a requisition or order on the way is left alone. Licence and certificate expiry reminders are already sent by Compliance.
        </p>
        {canManage && <>
          <button style={btnGhost} disabled={run.isPending} onClick={() => { if (window.confirm("Check the rules now? Any tank below its level will get a real draft requisition. Nothing is submitted or ordered.")) run.mutate() }}>Check now</button>
          <button style={btnPrimary} onClick={() => setDialog({})}>Add rule</button>
        </>}
      </div>
      {ran && <p role="status" style={{ fontSize: 12.5, margin: "0 0 10px" }}>{runText(ran)}</p>}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {rules.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No rules yet. Add one to have a requisition drafted when a tank runs low.</p> : (
        <div style={{ ...card, overflowX: "auto", marginBottom: 14 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Tank</th><th style={th}>Now</th><th style={th}>Draft below</th><th style={th}>Ask for</th><th style={th}>Supplier</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>{rows.map(r => (
              <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={td}>{r.tankName}<div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{r.fuelType}</div></td>
                <td style={td}>{fmtL(r.currentLitres)}</td>
                <td style={td}>{fmtL(r.reorderBelowLitres)}</td>
                <td style={td}>{fmtL(r.orderLitres)}{r.estimatedPrice != null && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>at about {fmtR(r.estimatedPrice)} per litre</div>}</td>
                <td style={td}>{r.supplierName ?? "Not chosen"}</td>
                <td style={td}>{!r.enabled ? <Chip bg="var(--hf-surface-alt)" fg="var(--hf-text)">Off</Chip> : r.belowLevel ? <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">Below level</Chip> : <Chip bg="var(--hf-success-soft-strong)" fg="var(--hf-success-text-strong)">On</Chip>}</td>
                <td style={td}>{canManage && <span style={{ display: "flex", gap: 6 }}>
                  <button style={btnGhost} disabled={toggle.isPending} onClick={() => toggle.mutate(r)}>{r.enabled ? "Switch off" : "Switch on"}</button>
                  <button style={btnGhost} onClick={() => setDialog({ rule: r })}>Edit</button>
                  <button style={btnGhost} onClick={() => { if (window.confirm(`Remove the rule for ${r.tankName}?`)) remove.mutate(r.id) }}>Remove</button>
                </span>}</td>
              </tr>))}</tbody>
          </table>
        </div>
      )}

      <div style={{ ...card, padding: 14, marginBottom: 14 }}>
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13 }}>
          <input type="checkbox" checked={!!settings.data?.alertOverduePurchaseOrders} disabled={!canManage || !settings.data || saveSettings.isPending} onChange={e => saveSettings.mutate({ alert: e.target.checked, days: currentDays })} />
          Tell the administrators when a purchase order passes its expected date without being fully received
        </label>
        <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>Off by default. Each order is mentioned once. Orders with no expected date are never flagged.</div>
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end", flexWrap: "wrap", marginTop: 12 }}>
          <div>
            <label style={labelStyle} htmlFor="au-vdays">Warn the administrators when a delivery variance is still unresolved after this many days</label>
            <input id="au-vdays" inputMode="numeric" value={daysText} placeholder="No alert" disabled={!canManage || !settings.data} onChange={e => { setDays(e.target.value); setErr("") }} style={{ ...inputStyle, width: 120 }} />
          </div>
          {canManage && <button style={btnGhost} disabled={saveSettings.isPending || days === null || !settings.data} onClick={() => { const m = validateReviewDays(daysText); if (m) return setErr(m); setErr(""); saveSettings.mutate({ alert: !!settings.data?.alertOverduePurchaseOrders, days: reviewDaysValue(daysText) }) }}>Save days</button>}
        </div>
        <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>The number is your own; there is no default, so no alert is sent until you set one. Counted from when the variance was raised, until it is closed. Each variance is mentioned once.</div>
      </div>

      <p style={{ fontSize: 12.5, fontWeight: 700, margin: "0 0 6px" }}>What the rules did</p>
      {(log.data ?? []).length === 0 ? <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>Nothing yet.</p> : (log.data ?? []).map(l => (
        <div key={l.id} style={{ fontSize: 12.5, padding: "4px 0", borderTop: "1px solid var(--hf-border)" }}>
          <Chip bg={l.outcome === "FAILED" ? "var(--hf-danger-soft)" : "var(--hf-success-soft-strong)"} fg={l.outcome === "FAILED" ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)"}>{l.outcome === "FAILED" ? "Failed" : "Drafted"}</Chip>{" "}
          {l.detail} <span style={{ color: "var(--hf-text-muted)" }}>{new Date(l.ranAt).toLocaleString("en-ZA")}</span>
        </div>
      ))}
      {dialog && <RuleDialog rule={dialog.rule} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function RuleDialog({ rule, onDone, onClose }: { rule?: Rule; onDone: () => void; onClose: () => void }) {
  const adding = !rule
  const [f, setF] = useState<RuleForm>(rule ? fromRule(rule) : emptyRule()); const [err, setErr] = useState("")
  const tanks = useQuery<any[]>({ queryKey: ["fuel-automation-tanks"], enabled: adding, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tanks")) })
  const suppliers = useQuery<any[]>({ queryKey: ["fuel-automation-suppliers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/suppliers")) })
  const set = (k: keyof RuleForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const save = useMutation({
    mutationFn: () => adding ? apiClient.post("/api/v1/fuel/automation/rules", toRuleRequest(f, true)) : apiClient.put(`/api/v1/fuel/automation/rules/${rule!.id}`, toRuleRequest(f, false)),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the rule")),
  })
  return (
    <Modal title={adding ? "Add reorder rule" : `Edit rule: ${rule!.tankName}`} onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {adding && <div><label style={labelStyle} htmlFor="ar-tank">Tank *</label>
          <select id="ar-tank" value={f.tankId} onChange={set("tankId")} style={inputStyle}><option value="">Choose…</option>{(Array.isArray(tanks.data) ? tanks.data : []).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></div>}
        <div><label style={labelStyle} htmlFor="ar-below">Draft a requisition when the tank is below (litres) *</label><input id="ar-below" inputMode="decimal" value={f.below} onChange={set("below")} style={inputStyle} /></div>
        <div><label style={labelStyle} htmlFor="ar-qty">Litres to ask for *</label><input id="ar-qty" inputMode="decimal" value={f.qty} onChange={set("qty")} style={inputStyle} /></div>
        <div><label style={labelStyle} htmlFor="ar-supplier">Supplier</label>
          <select id="ar-supplier" value={f.supplierId} onChange={set("supplierId")} style={inputStyle}><option value="">Decide later</option>{(Array.isArray(suppliers.data) ? suppliers.data : []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="ar-price">Estimated price per litre (R)</label><input id="ar-price" inputMode="decimal" value={f.price} onChange={set("price")} style={inputStyle} />
          <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 3 }}>A requisition needs a price before it can be submitted for approval. Leave it empty to add it yourself.</div></div>
        {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{err}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
          <button style={btnGhost} onClick={onClose}>Cancel</button>
          <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateRule(f, adding); if (v) return setErr(v); setErr(""); save.mutate() }}>Save</button>
        </div>
      </div>
    </Modal>
  )
}
