// src/pages/earthmoving/SitesTab.tsx
//
// Sites: the places machines are sent to. A site can exist without a project or a customer. A site's supervisor is
// told first when a machine at that site breaks down. Administrators also see "Links to review": older deployments
// that only carry typed site and client text, matched to records only where the match is exact and unambiguous.
import { useState } from "react"
import { MapPin } from "lucide-react"
import { apiMessage } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import {
  useSites, useSaveSite, useDeactivateSite, useAssignableUsers, useCustomerOptions, useProjectOptions,
  useReconciliation, useReconcileActions,
} from "./shared/hooks"
import type { Site, ReconRow } from "./shared/types"

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 18px", background: "var(--hf-surface)" }

const EMPTY = { name: "", address: "", latitude: "", longitude: "", customerId: "", projectId: "", supervisorUserId: "", notes: "" }
type SiteForm = typeof EMPTY

const STATE_LABEL: Record<string, string> = {
  MATCHED: "One exact match", AMBIGUOUS: "More than one match", UNMATCHED: "No match", NONE: "Nothing to link",
}

export default function SitesTab() {
  const perms = useEarthPerms()
  const [view, setView] = useState<"sites" | "links">("sites")
  const [showInactive, setShowInactive] = useState(false)
  const [editing, setEditing] = useState<Site | "new" | null>(null)
  const [form, setForm] = useState<SiteForm>(EMPTY)
  const [err, setErr] = useState("")
  const [notice, setNotice] = useState("")
  const [resolving, setResolving] = useState<ReconRow | null>(null)
  const [pick, setPick] = useState({ siteId: "", customerId: "", projectId: "" })

  const { data: sites = [], isLoading } = useSites(showInactive)
  const { data: users = [] } = useAssignableUsers()
  const { data: customers = [] } = useCustomerOptions(editing !== null || resolving !== null)
  const { data: projects = [] } = useProjectOptions(editing !== null || resolving !== null)
  const { data: recon } = useReconciliation(perms.canAdmin && view === "links")
  const { data: allSites = [] } = useSites(false)

  const save = useSaveSite(() => setEditing(null))
  const deactivate = useDeactivateSite()
  const { apply, resolve } = useReconcileActions(setNotice)

  const openEditor = (s: Site | "new") => {
    setErr("")
    setEditing(s)
    setForm(s === "new" ? EMPTY : {
      name: s.name, address: s.address ?? "", latitude: s.latitude == null ? "" : String(s.latitude),
      longitude: s.longitude == null ? "" : String(s.longitude), customerId: s.customerId ?? "", projectId: s.projectId ?? "",
      supervisorUserId: s.supervisorUserId ?? "", notes: s.notes ?? "",
    })
  }

  const submit = () => {
    const lat = form.latitude.trim(), lng = form.longitude.trim()
    if ((lat === "") !== (lng === "")) { setErr("Give both latitude and longitude, or neither."); return }
    save.mutate({
      id: editing === "new" || editing === null ? null : editing.id,
      body: {
        name: form.name.trim(), address: form.address.trim() || null,
        latitude: lat === "" ? null : Number(lat), longitude: lng === "" ? null : Number(lng),
        customerId: form.customerId || null, projectId: form.projectId || null,
        supervisorUserId: form.supervisorUserId || null, notes: form.notes.trim() || null,
      },
    }, { onError: (e: unknown) => setErr(apiMessage(e, "Could not save the site")) })
  }

  const userName = (id: string | null) => users.find(u => u.id === id)?.name

  const renderSite = (s: Site) => (
    <div key={s.id} style={{ ...card, opacity: s.active ? 1 : 0.6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <div>
          <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{s.name}{!s.active && " (inactive)"}</div>
          {s.address && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>{s.address}</div>}
          <div style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 6 }}>
            Customer: {s.customerName ?? "none"} · Project: {s.projectName ?? "none"} · Supervisor: {userName(s.supervisorUserId) ?? (s.supervisorUserId ? "a user" : "none")}
          </div>
        </div>
        {perms.canWrite && s.active && (
          <div style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
            <button style={btn()} onClick={() => openEditor(s)}>Edit</button>
            <button style={btn()} onClick={() => { if (window.confirm(`Deactivate ${s.name}? Past deployments keep it.`)) deactivate.mutate(s.id) }}>Deactivate</button>
          </div>
        )}
      </div>
    </div>
  )

  const renderSites = () => (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <label style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
          <input type="checkbox" checked={showInactive} onChange={e => setShowInactive(e.target.checked)} /> Show inactive
        </label>
        {perms.canWrite && <button style={btn(true)} onClick={() => openEditor("new")}>Add site</button>}
      </div>
      {isLoading ? <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>
        : sites.length === 0 ? (
          <div style={{ textAlign: "center", padding: "50px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
            <MapPin size={34} style={{ marginBottom: 10, opacity: 0.4 }} />
            <div style={{ fontWeight: 600 }}>No sites yet</div>
            <div style={{ fontSize: 13, marginTop: 4 }}>Add the places you send machines to. A project or customer is optional.</div>
          </div>
        ) : <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>{sites.map(renderSite)}</div>}
    </div>
  )

  const renderRow = (r: ReconRow, i: number) => (
    <div key={i} style={{ ...card, display: "flex", justifyContent: "space-between", gap: 12 }}>
      <div style={{ fontSize: 13 }}>
        <div style={{ fontWeight: 600, color: "var(--hf-text)" }}>{r.legacySite || "(no site typed)"} · {r.legacyClient || "(no client typed)"}</div>
        <div style={{ color: "var(--hf-text-muted)", marginTop: 2 }}>
          {r.deployments} deployment{r.deployments === 1 ? "" : "s"} · Site: {STATE_LABEL[r.siteState]} · Customer: {STATE_LABEL[r.customerState]}
        </div>
      </div>
      <button style={btn()} onClick={() => { setPick({ siteId: "", customerId: "", projectId: "" }); setErr(""); setResolving(r) }}>Choose links</button>
    </div>
  )

  const renderLinks = () => {
    if (!recon) return <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>
    const openRows = recon.rows.filter(r => r.siteState !== "NONE" || r.customerState !== "NONE")
    return (
      <div>
        <div style={{ ...card, marginBottom: 14, fontSize: 13, color: "var(--hf-text-secondary)" }}>
          {recon.totalDeployments} deployments in all · {recon.withSite} linked to a site · {recon.withCustomer} of {recon.withClientText} with a client typed are linked to a customer.
          <br />Sites: {recon.siteMatched} exact, {recon.siteAmbiguous} ambiguous, {recon.siteUnmatched} unmatched.
          {" "}Customers: {recon.customerMatched} exact, {recon.customerAmbiguous} ambiguous, {recon.customerUnmatched} unmatched.
          <br />Typed text is kept as it was, and an existing link is never replaced.
        </div>
        {notice && <div role="status" style={{ ...card, marginBottom: 12, color: "var(--hf-success-text-strong)" }}>{notice}</div>}
        <div style={{ marginBottom: 12 }}>
          <button style={btn(true)} disabled={apply.isPending || (recon.siteMatched + recon.customerMatched === 0)}
            onClick={() => { setNotice(""); apply.mutate() }}>Apply exact matches</button>
        </div>
        {openRows.length === 0 ? <div style={{ color: "var(--hf-text-muted)" }}>Nothing left to link.</div>
          : <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{openRows.map(renderRow)}</div>}
      </div>
    )
  }

  const renderEditor = () => (
    <Overlay onClose={() => setEditing(null)} width={560}>
      <ModalHead title={editing === "new" ? "Add site" : "Edit site"} onClose={() => setEditing(null)} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, padding: "4px 0 10px" }}>
        <div style={{ gridColumn: "1 / -1" }}>
          <label htmlFor="site-name" style={lbl}>Site name *</label>
          <input id="site-name" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} style={inputStyle()} />
        </div>
        <div style={{ gridColumn: "1 / -1" }}>
          <label htmlFor="site-address" style={lbl}>Address</label>
          <input id="site-address" value={form.address} onChange={e => setForm(f => ({ ...f, address: e.target.value }))} style={inputStyle()} />
        </div>
        <div><label htmlFor="site-lat" style={lbl}>Latitude</label>
          <input id="site-lat" value={form.latitude} onChange={e => setForm(f => ({ ...f, latitude: e.target.value }))} style={inputStyle()} /></div>
        <div><label htmlFor="site-lng" style={lbl}>Longitude</label>
          <input id="site-lng" value={form.longitude} onChange={e => setForm(f => ({ ...f, longitude: e.target.value }))} style={inputStyle()} /></div>
        <div><label htmlFor="site-customer" style={lbl}>Customer (optional)</label>
          <select id="site-customer" value={form.customerId} onChange={e => setForm(f => ({ ...f, customerId: e.target.value }))} style={inputStyle()}>
            <option value="">None</option>{customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
        <div><label htmlFor="site-project" style={lbl}>Project (optional)</label>
          <select id="site-project" value={form.projectId} onChange={e => setForm(f => ({ ...f, projectId: e.target.value }))} style={inputStyle()}>
            <option value="">None</option>{projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></div>
        <div style={{ gridColumn: "1 / -1" }}><label htmlFor="site-supervisor" style={lbl}>Site supervisor (told first about a breakdown here)</label>
          <select id="site-supervisor" value={form.supervisorUserId} onChange={e => setForm(f => ({ ...f, supervisorUserId: e.target.value }))} style={inputStyle()}>
            <option value="">None</option>{users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}</select></div>
        <div style={{ gridColumn: "1 / -1" }}><label htmlFor="site-notes" style={lbl}>Notes</label>
          <input id="site-notes" value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} style={inputStyle()} /></div>
      </div>
      {err && <ErrBanner msg={err} />}
      <ModalFoot onCancel={() => setEditing(null)} onSubmit={submit} loading={save.isPending} label="Save site" disabled={!form.name.trim()} />
    </Overlay>
  )

  const renderResolver = () => resolving && (
    <Overlay onClose={() => setResolving(null)} width={520}>
      <ModalHead title="Choose links" onClose={() => setResolving(null)} />
      <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginBottom: 12 }}>
        Links every unlinked deployment typed as “{resolving.legacySite}” for “{resolving.legacyClient ?? ""}”. The typed text stays as it is.
      </div>
      <div style={{ display: "grid", gap: 12 }}>
        <div><label htmlFor="rs-site" style={lbl}>Site</label>
          <select id="rs-site" value={pick.siteId} onChange={e => setPick(p => ({ ...p, siteId: e.target.value }))} style={inputStyle()}>
            <option value="">Leave as is</option>{allSites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></div>
        <div><label htmlFor="rs-customer" style={lbl}>Customer</label>
          <select id="rs-customer" value={pick.customerId} onChange={e => setPick(p => ({ ...p, customerId: e.target.value }))} style={inputStyle()}>
            <option value="">Leave as is</option>{customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
        <div><label htmlFor="rs-project" style={lbl}>Project</label>
          <select id="rs-project" value={pick.projectId} onChange={e => setPick(p => ({ ...p, projectId: e.target.value }))} style={inputStyle()}>
            <option value="">Leave as is</option>{projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></div>
      </div>
      {err && <ErrBanner msg={err} />}
      <ModalFoot onCancel={() => setResolving(null)} loading={resolve.isPending} label="Link deployments"
        disabled={!pick.siteId && !pick.customerId && !pick.projectId}
        onSubmit={() => resolve.mutate({
          legacySite: resolving.legacySite, legacyClient: resolving.legacyClient,
          siteId: pick.siteId || null, customerId: pick.customerId || null, projectId: pick.projectId || null,
        }, { onSuccess: () => setResolving(null), onError: (e: unknown) => setErr(apiMessage(e, "Could not link")) })} />
    </Overlay>
  )

  return (
    <div>
      {perms.canAdmin && (
        <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
          <button style={btn(view === "sites")} onClick={() => setView("sites")}>Sites</button>
          <button style={btn(view === "links")} onClick={() => setView("links")}>Links to review</button>
        </div>
      )}
      {view === "sites" ? renderSites() : renderLinks()}
      {editing !== null && renderEditor()}
      {renderResolver()}
    </div>
  )
}
