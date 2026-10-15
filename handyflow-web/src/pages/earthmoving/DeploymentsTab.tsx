// src/pages/earthmoving/DeploymentsTab.tsx
import { useState } from "react"
import { Plus, MapPin, Clock } from "lucide-react"
import { EMOJI } from "./shared/constants"
import { apiMessage, meterUnitOf } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useAssets, useDeployAsset, useReturnToYard, useSites, useCustomerOptions } from "./shared/hooks"

const EMPTY_FORM = {
  assetId: "", siteId: "", customerId: "", siteName: "", clientName: "", startDate: "", expectedEndDate: "",
  contactName: "", contactPhone: "", notes: "",
}

export default function DeploymentsTab() {
  const perms = useEarthPerms()
  const [showDeploy, setShowDeploy] = useState(false)
  const [apiError, setApiError]     = useState("")
  const [form, setForm]             = useState(EMPTY_FORM)

  const { data: assets = [] } = useAssets()
  const deployedAssets  = assets.filter(a => ["DEPLOYED", "HIRED_OUT"].includes(a.status))
  const availableAssets = assets.filter(a => a.status === "AVAILABLE")

  const { data: sites = [] } = useSites(false)
  const { data: customers = [] } = useCustomerOptions(showDeploy)
  const chosenSite = sites.find(s => s.id === form.siteId)
  const deploy = useDeployAsset(() => { setShowDeploy(false); setForm(EMPTY_FORM); setApiError("") })
  const returnToYard = useReturnToYard()

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {[
          { label: "Currently deployed", value: deployedAssets.length, color: "var(--hf-info-text)" },
          { label: "Available to deploy", value: availableAssets.length, color: "var(--hf-success-text-strong)" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
        <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>Active Deployments</span>
        {perms.canDeploy && <button onClick={() => { setShowDeploy(true); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Deploy Equipment
        </button>}
      </div>

      {deployedAssets.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
          <MapPin size={36} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No active deployments</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Deploy available equipment to a site to track it here.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {deployedAssets.map(a => (
            <div key={a.id} style={{ border: "1px solid var(--hf-info-border)", borderLeft: "4px solid var(--hf-info)", borderRadius: 10, padding: "16px 20px", background: "var(--hf-surface)" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ width: 44, height: 44, borderRadius: 10, background: "var(--hf-info-soft)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 22, flexShrink: 0 }}>
                    {EMOJI[a.assetType] ?? "🚧"}
                  </div>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                      {a.fleetNumber && <span style={{ fontWeight: 800, color: "var(--hf-warning-text)", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", padding: "1px 8px", borderRadius: 6, fontSize: 12 }}>{a.fleetNumber}</span>}
                      <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{a.name}</span>
                    </div>
                    <div style={{ fontSize: 13, color: "var(--hf-text-muted)", display: "flex", gap: 14, flexWrap: "wrap" }}>
                      {a.currentSite && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><MapPin size={11} style={{ color: 'var(--hf-info-text)' }} /> {a.currentSite}</span>}
                      {a.currentClient && <span>Client: {a.currentClient}</span>}
                      <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Clock size={11} /> {(a.currentHours ?? 0).toLocaleString()} {meterUnitOf(a)}</span>
                    </div>
                  </div>
                </div>
                {perms.canDeploy && <button
                  onClick={() => returnToYard.mutate(a.id, { onError: (e: unknown) => setApiError(apiMessage(e, "Failed to return asset")) })}
                  disabled={returnToYard.isPending}
                  style={{ padding: "7px 14px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                  Return to Yard
                </button>}
              </div>
            </div>
          ))}
          {apiError && <ErrBanner msg={apiError} />}
        </div>
      )}

      {showDeploy && (
        <Overlay onClose={() => setShowDeploy(false)} width={560}>
          <ModalHead title="Deploy Equipment" onClose={() => setShowDeploy(false)} />

          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={lbl}>Equipment *</label>
              {availableAssets.length === 0 ? (
                <div style={{ padding: "10px 12px", background: "var(--hf-warning-soft-strong)", border: "1px solid var(--hf-warning-border-strong)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text-deep)" }}>
                  No equipment available. All assets are deployed or in maintenance.
                </div>
              ) : (
                <select value={form.assetId} onChange={e => setForm(f => ({ ...f, assetId: e.target.value }))} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
                  <option value="">Select equipment...</option>
                  {availableAssets.map(a => (
                    <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name} ({a.assetType})</option>
                  ))}
                </select>
              )}
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label htmlFor="dep-site" style={lbl}>Site *</label>
                <select id="dep-site" value={form.siteId} onChange={e => setForm(f => ({ ...f, siteId: e.target.value }))} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
                  <option value="">Type a site name instead…</option>
                  {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
                {!form.siteId && <input aria-label="Site name" value={form.siteName} onChange={e => setForm(f => ({ ...f, siteName: e.target.value }))} placeholder="Sandton Excavation Site" style={{ ...inputStyle(), marginTop: 6 }} />}
              </div>
              <div>
                <label htmlFor="dep-customer" style={lbl}>Client / Company</label>
                <select id="dep-customer" value={form.customerId} onChange={e => setForm(f => ({ ...f, customerId: e.target.value }))} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
                  <option value="">{chosenSite?.customerName ? `Site's customer: ${chosenSite.customerName}` : "Type a client instead…"}</option>
                  {customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
                {!form.customerId && !chosenSite?.customerName && <input aria-label="Client name" value={form.clientName} onChange={e => setForm(f => ({ ...f, clientName: e.target.value }))} placeholder="Zeta Construction" style={{ ...inputStyle(), marginTop: 6 }} />}
              </div>
              <div>
                <label style={lbl}>Start Date</label>
                <input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Expected Return Date</label>
                <input type="date" value={form.expectedEndDate} onChange={e => setForm(f => ({ ...f, expectedEndDate: e.target.value }))} style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Site Contact</label>
                <input value={form.contactName} onChange={e => setForm(f => ({ ...f, contactName: e.target.value }))} placeholder="John Smith" style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Contact Phone</label>
                <input value={form.contactPhone} onChange={e => setForm(f => ({ ...f, contactPhone: e.target.value }))} placeholder="+27 82 111 2233" style={inputStyle()} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Notes</label>
                <input value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} placeholder="Any deployment notes..." style={inputStyle()} />
              </div>
            </div>
          </div>

          {apiError && <ErrBanner msg={apiError} />}

          <ModalFoot
            onCancel={() => setShowDeploy(false)}
            onSubmit={() => deploy.mutate({
              assetId: form.assetId, siteId: form.siteId || null, customerId: form.customerId || null,
              siteName: form.siteId ? null : form.siteName, clientName: form.clientName || null,
              startDate: form.startDate || null, expectedEndDate: form.expectedEndDate || null,
              contactName: form.contactName || null, contactPhone: form.contactPhone || null,
              notes: form.notes || null,
            }, {
              onError: (e: unknown) => setApiError(apiMessage(e, "Failed to deploy asset")),
            })}
            loading={deploy.isPending}
            label="Deploy Equipment"
            disabled={!form.assetId || (!form.siteId && !form.siteName.trim())}
          />
        </Overlay>
      )}
    </div>
  )
}
