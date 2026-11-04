// The equipment at each site: add, edit, move between working states, decommission, and keep photos.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, Row, SectionTitle, card, day, errorText, fail, inp, muted, pageOf, td, th } from "./kit"
import { BASE, useLookups, type Lookups } from "./lookups"
import { ASSET_STATUS, CRITICALITY, assetBody, assetMoves, assetProblem, canDecommission, emptyAsset, labelOf, warrantyNote, type AssetForm, type Tone } from "./fm.logic"
import FmEvidencePanel from "./FmEvidencePanel"

interface Asset { id: string; siteId: string; assetTag: string | null; name: string; assetType: string; location: string | null; manufacturer: string | null; model: string | null; serialNumber: string | null; installDate: string | null; warrantyExpiryDate: string | null; criticality: string; status: string; notes: string | null }
const MOVE_LABEL: Record<string, string> = { OPERATIONAL: "Mark working", DOWN: "Mark down", MAINTENANCE: "Send to maintenance" }

export default function AssetsTab() {
  const qc = useQueryClient(), lookups = useLookups()
  const canWrite = usePermission("FACILITIESMANAGEMENT_MANAGE") || usePermission("FACILITIESMANAGEMENT_ADMIN")
  const [page, setPage] = useState(0), [siteId, setSiteId] = useState(""), [editing, setEditing] = useState<Asset | "new" | null>(null), [open, setOpen] = useState<Asset | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["fm-assets", page, siteId], queryFn: async () => pageOf<Asset>(await apiClient.get(`${BASE}/assets`, { params: { page, size: 25, siteId: siteId || undefined } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fm-assets"] }); qc.invalidateQueries({ queryKey: ["fm-assets-site"] }) }
  const rows = data?.content ?? []
  const now = new Date()
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add asset</Btn>}>Assets</SectionTitle>
      <div style={{ marginBottom: 10 }}>
        <select aria-label="Site" value={siteId} onChange={e => { setSiteId(e.target.value); setPage(0) }} style={{ ...inp, width: "auto" }}>
          <option value="">All sites</option>{lookups.sites.map(s => <option key={s.id} value={s.id}>{lookups.clientName(s.clientId)} · {s.name}</option>)}
        </select>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the assets. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No assets yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Asset</th><th style={th}>Site</th><th style={th}>Importance</th><th style={th}>Status</th></tr></thead>
            <tbody>{rows.map(a => {
              const st = ASSET_STATUS[a.status] ?? { label: a.status, tone: "neutral" as Tone }, w = warrantyNote(a.warrantyExpiryDate, now)
              return (
                <tr key={a.id} onClick={() => setOpen(a)} style={{ cursor: "pointer", opacity: a.status === "DECOMMISSIONED" ? 0.6 : 1 }}>
                  <td style={td}><b>{a.name}</b><div style={muted}>{[a.assetTag, a.assetType, a.location].filter(Boolean).join(" · ")}</div></td>
                  <td style={td}>{lookups.siteName(a.siteId)}<div style={muted}>{lookups.clientName(lookups.sites.find(s => s.id === a.siteId)?.clientId)}</div></td>
                  <td style={td}>{labelOf(CRITICALITY, a.criticality)}</td>
                  <td style={td}><Pill tone={st.tone}>{st.label}</Pill>{w && <div style={{ marginTop: 3 }}><Pill tone={w.tone}>{w.text}</Pill></div>}</td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {open && <AssetDetail asset={open} lookups={lookups} canWrite={canWrite} onClose={() => setOpen(null)} onEdit={() => { setEditing(open); setOpen(null) }} onChanged={a => { setOpen(a); refresh() }} />}
      {editing && <AssetDialog asset={editing === "new" ? null : editing} lookups={lookups} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function AssetDetail({ asset: a, lookups, canWrite, onClose, onEdit, onChanged }: { asset: Asset; lookups: Lookups; canWrite: boolean; onClose: () => void; onEdit: () => void; onChanged: (a: Asset) => void }) {
  const [confirm, setConfirm] = useState(false)
  const st = ASSET_STATUS[a.status] ?? { label: a.status, tone: "neutral" as Tone }, w = warrantyNote(a.warrantyExpiryDate, new Date())
  const move = useMutation({
    mutationFn: async (status: string) => (await apiClient.patch<Asset>(`${BASE}/assets/${a.id}/status`, { status })).data,
    onSuccess: r => { toast.success("Status updated"); setConfirm(false); onChanged(r) },
    onError: e => { setConfirm(false); void fail(e, "Could not change the status") },
  })
  return (
    <Dialog title={<>{a.name} <Pill tone={st.tone}>{st.label}</Pill></>} onClose={onClose} width={680}
      footer={canWrite ? <>
        {assetMoves(a.status).map(m => <Btn key={m} disabled={move.isPending} onClick={() => move.mutate(m)}>{MOVE_LABEL[m]}</Btn>)}
        {canDecommission(a.status) && <Btn onClick={() => setConfirm(true)}>Decommission</Btn>}
        {a.status !== "DECOMMISSIONED" && <Btn kind="primary" onClick={onEdit}>Edit</Btn>}
      </> : undefined}>
      <Row k="Tag">{a.assetTag ?? ""}</Row>
      <Row k="Kind">{a.assetType}</Row>
      <Row k="Site">{`${lookups.clientName(lookups.sites.find(s => s.id === a.siteId)?.clientId)} · ${lookups.siteName(a.siteId)}`}</Row>
      <Row k="Where on site">{a.location ?? ""}</Row>
      <Row k="Make and model">{[a.manufacturer, a.model].filter(Boolean).join(" ")}</Row>
      <Row k="Serial number">{a.serialNumber ?? ""}</Row>
      <Row k="Installed">{a.installDate ? day(a.installDate) : ""}</Row>
      <Row k="Warranty ends">{a.warrantyExpiryDate ? <>{day(a.warrantyExpiryDate)} {w && <Pill tone={w.tone}>{w.text}</Pill>}</> : ""}</Row>
      <Row k="Importance">{labelOf(CRITICALITY, a.criticality)}</Row>
      {a.notes && <Row k="Notes">{a.notes}</Row>}
      <FmEvidencePanel owner="assets" id={a.id} canWrite={canWrite && a.status !== "DECOMMISSIONED"} />
      {confirm && (
        <Dialog title="Decommission this asset?" onClose={() => setConfirm(false)}
          footer={<><Btn onClick={() => setConfirm(false)}>Not yet</Btn><Btn kind="danger" disabled={move.isPending} onClick={() => move.mutate("DECOMMISSIONED")}>{move.isPending ? "Working…" : "Decommission"}</Btn></>}>
          <div style={{ fontSize: 13, lineHeight: 1.5 }}>A decommissioned asset can no longer be changed or moved back into service.</div>
        </Dialog>)}
    </Dialog>
  )
}

function AssetDialog({ asset, lookups, onClose, onSaved }: { asset: Asset | null; lookups: Lookups; onClose: () => void; onSaved: () => void }) {
  const creating = !asset
  const [f, setF] = useState<AssetForm>(() => asset ? { siteId: asset.siteId, assetTag: asset.assetTag ?? "", name: asset.name, assetType: asset.assetType, location: asset.location ?? "", manufacturer: asset.manufacturer ?? "", model: asset.model ?? "", serialNumber: asset.serialNumber ?? "", installDate: asset.installDate ?? "", warrantyExpiryDate: asset.warrantyExpiryDate ?? "", criticality: asset.criticality, notes: asset.notes ?? "" } : emptyAsset())
  const [err, setErr] = useState("")
  const set = (k: keyof AssetForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = assetProblem(f, creating)
  const save = useMutation({
    mutationFn: () => asset ? apiClient.put(`${BASE}/assets/${asset.id}`, assetBody(f, false)) : apiClient.post(`${BASE}/assets`, assetBody(f, true)),
    onSuccess: () => { toast.success(asset ? "Asset saved" : "Asset added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the asset")),
  })
  return (
    <Dialog title={asset ? `Edit ${asset.name}` : "Add an asset"} onClose={onClose} width={640}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      {creating && <Pick label="Site" value={f.siteId} onChange={set("siteId")} options={lookups.sites.filter(s => s.status !== "CLOSED").map(s => [s.id, `${lookups.clientName(s.clientId)} · ${s.name}`] as const)} blank="Choose a site" />}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Name" value={f.name} onChange={set("name")} autoFocus maxLength={255} />
        {creating ? <Input label="Kind of asset" value={f.assetType} onChange={set("assetType")} hint="For example HVAC, generator, lift" /> : <div />}
        {creating && <Input label="Asset tag" value={f.assetTag} onChange={set("assetTag")} hint="Leave empty to skip" />}
        <Input label="Where on site" value={f.location} onChange={set("location")} />
        <Input label="Manufacturer" value={f.manufacturer} onChange={set("manufacturer")} />
        <Input label="Model" value={f.model} onChange={set("model")} />
        <Input label="Serial number" value={f.serialNumber} onChange={set("serialNumber")} />
        <Pick label="Importance" value={f.criticality} onChange={set("criticality")} options={CRITICALITY} />
        {creating && <Input label="Installed" type="date" value={f.installDate} onChange={set("installDate")} />}
        <Input label="Warranty ends" type="date" value={f.warrantyExpiryDate} onChange={set("warrantyExpiryDate")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && f.name.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
