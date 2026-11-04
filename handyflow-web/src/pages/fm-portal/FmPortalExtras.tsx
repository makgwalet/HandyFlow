// Client portal, part two: following a job (steps and photos), the client's equipment, and reports with documents.
import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { ExternalLink } from "lucide-react"
import { fmPortalApi, type FmPortalPhoto, type FmPortalSite } from "../../api/fmPortal.api"
import { color, space } from "../accountant-portal/portal-theme"
import { ASSET_STATUS, PORTAL_CATEGORIES, labelOf } from "../facilitiesmanagement/fm.logic"
import { Chip, Modal, btn, day, empty, errText, money, panel, sub, td, th } from "./portalKit"
import { PHOTO_KIND, mapsUrl, orderPhotos, serviceReport } from "./portalStats"

export function MapLink({ address }: { address: FmPortalSite["address"] }) {
  const url = mapsUrl(address)
  return url ? <a href={url} target="_blank" rel="noopener noreferrer" style={{ fontSize: 12.5, color: "var(--hf-primary-text)", display: "inline-flex", gap: 4, alignItems: "center" }}>Open in maps<ExternalLink size={12} aria-hidden /></a> : null
}

function Photo({ cid, woId, photo }: { cid: string; woId: string; photo: FmPortalPhoto }) {
  const [url, setUrl] = useState(""), [failed, setFailed] = useState(false)
  useEffect(() => {
    let made = "", gone = false
    fmPortalApi.photoBlob(cid, woId, photo.id).then(b => { if (gone) return; made = URL.createObjectURL(b); setUrl(made) }).catch(() => { if (!gone) setFailed(true) })
    return () => { gone = true; if (made) URL.revokeObjectURL(made) }
  }, [cid, woId, photo.id])
  return (
    <figure style={{ margin: 0, width: 150 }}>
      <div style={{ width: 150, height: 112, borderRadius: 8, overflow: "hidden", background: color.border, display: "flex", alignItems: "center", justifyContent: "center" }}>
        {url ? <a href={url} target="_blank" rel="noopener noreferrer"><img src={url} alt={`${PHOTO_KIND[photo.kind] ?? "Photo"}: ${photo.fileName}`} style={{ width: 150, height: 112, objectFit: "cover", display: "block" }} /></a>
          : <span style={sub}>{failed ? "Could not load" : "Loading…"}</span>}
      </div>
      <figcaption style={{ ...sub, marginTop: 4 }}><Chip tone={photo.kind === "AFTER_PHOTO" ? "success" : "neutral"}>{PHOTO_KIND[photo.kind] ?? "Photo"}</Chip> {day(photo.createdAt)}</figcaption>
    </figure>
  )
}

/** Where one job has got to, step by step, with its photos. */
export function TrackModal({ cid, woId, title, onClose }: { cid: string; woId: string; title: string; onClose: () => void }) {
  const prog = useQuery({ queryKey: ["fm-portal-progress", cid, woId], queryFn: () => fmPortalApi.progress(cid, woId) })
  const photos = useQuery({ queryKey: ["fm-portal-photos", cid, woId], queryFn: () => fmPortalApi.photos(cid, woId) })
  const shots = orderPhotos(photos.data ?? [])
  return (
    <Modal title={title} onClose={onClose} footer={<button style={btn()} onClick={onClose}>Close</button>}>
      {prog.isError ? <p style={empty}>{errText(prog.error, "We could not load the progress of this job.")}</p> : prog.isLoading ? <p style={empty}>Loading…</p> : (
        <ol aria-label="Progress" style={{ listStyle: "none", margin: 0, padding: 0 }}>
          {prog.data!.steps.map(s => {
            const bad = s.key === "DECLINED" || s.key === "CANCELLED"
            const dot = s.state === "DONE" ? color.navy : s.state === "CURRENT" ? (bad ? color.redText : "var(--hf-warning)") : color.border
            return (
              <li key={s.key} aria-current={s.state === "CURRENT" ? "step" : undefined} style={{ display: "flex", gap: space(3), padding: "7px 0", alignItems: "flex-start" }}>
                <span aria-hidden style={{ width: 14, height: 14, borderRadius: 14, background: dot, marginTop: 3, flexShrink: 0 }} />
                <div><b style={{ color: s.state === "PENDING" ? color.muted : color.ink }}>{s.label}</b>
                  {s.state === "CURRENT" && !bad && <span style={{ ...sub, marginLeft: 8 }}>Now</span>}
                  {s.at && <div style={sub}>{day(s.at)}</div>}{s.note && <div style={sub}>{s.note}</div>}</div>
              </li>)
          })}
        </ol>)}
      <div style={{ margin: `${space(4)} 0 ${space(2)}`, fontWeight: 700 }}>Photos</div>
      {photos.isError ? <p style={sub}>We could not load the photos.</p> : photos.isLoading ? <p style={sub}>Loading…</p>
        : shots.length === 0 ? <p style={sub}>No photos yet. Photos of the work appear here as the team adds them.</p>
          : <div style={{ display: "flex", gap: space(3), flexWrap: "wrap" }}>{shots.map(p => <Photo key={p.id} cid={cid} woId={woId} photo={p} />)}</div>}
    </Modal>
  )
}

export function AssetsPanel({ cid }: { cid: string }) {
  const q = useQuery({ queryKey: ["fm-portal-assets", cid], queryFn: () => fmPortalApi.assets(cid) })
  const sites = useQuery({ queryKey: ["fm-portal-sites", cid], queryFn: () => fmPortalApi.sites(cid) })
  const [siteF, setSiteF] = useState("")
  const siteName = (id: string) => sites.data?.content.find(s => s.id === id)?.name ?? "—"
  const rows = (q.data ?? []).filter(a => !siteF || a.siteId === siteF)
  return (
    <div>
      <div style={{ marginBottom: space(3) }}><select aria-label="Site" value={siteF} onChange={e => setSiteF(e.target.value)} style={{ padding: "9px 12px", borderRadius: 8, border: `1px solid ${color.border}`, background: color.surface, font: "inherit" }}><option value="">All sites</option>{(sites.data?.content ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></div>
      <div style={panel}>
        {q.isError ? <p style={empty}>We could not load your equipment.</p> : q.isLoading ? <p style={empty}>Loading…</p> : rows.length === 0 ? <p style={empty}>{siteF ? "No equipment is listed for this site." : "No equipment is listed yet."}</p> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Equipment</th><th style={th}>Site</th><th style={th}>Warranty</th><th style={th}>Status</th></tr></thead>
            <tbody>{rows.map(a => { const s = ASSET_STATUS[a.status] ?? { label: a.status, tone: "neutral" as const }
              return <tr key={a.id}><td style={td}><b>{a.name}</b><div style={sub}>{[a.assetTag, a.assetType && labelOf([], a.assetType), [a.manufacturer, a.model].filter(Boolean).join(" ")].filter(Boolean).join(" · ")}</div></td>
                <td style={td}>{siteName(a.siteId)}{a.location && <div style={sub}>{a.location}</div>}</td>
                <td style={td}>{a.warrantyExpiryDate ? (a.warrantyExpiryDate < new Date().toISOString().slice(0, 10) ? <span style={{ color: color.amberText }}>Ended {day(a.warrantyExpiryDate)}</span> : `Until ${day(a.warrantyExpiryDate)}`) : "—"}</td>
                <td style={td}><Chip tone={s.tone}>{s.label}</Chip></td></tr> })}</tbody>
          </table>)}
      </div>
    </div>
  )
}

export function ReportsPanel({ cid }: { cid: string }) {
  const orders = useQuery({ queryKey: ["fm-portal-orders-all", cid], queryFn: () => fmPortalApi.ordersAll(cid) })
  const inv = useQuery({ queryKey: ["fm-portal-invoices", cid], queryFn: () => fmPortalApi.invoices(cid) })
  const cn = useQuery({ queryKey: ["fm-portal-credits", cid], queryFn: () => fmPortalApi.credits(cid) })
  const [err, setErr] = useState("")
  const dl = async (f: () => Promise<void>) => { setErr(""); try { await f() } catch (e) { setErr(errText(e, "Could not download the file")) } }
  const r = serviceReport(orders.data?.content ?? [], new Date())
  const tile = (label: string, value: string, note?: string) => <div key={label} style={{ ...panel, padding: space(4), flex: "1 1 150px" }}><div style={{ ...sub, fontWeight: 700, textTransform: "uppercase", fontSize: 11, letterSpacing: "0.04em" }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800 }}>{value}</div>{note && <div style={sub}>{note}</div>}</div>
  const docs = [...(inv.data ?? []).map(i => ({ key: "i" + i.id, name: i.invoiceNumber, kind: "Invoice", date: i.issueDate ?? i.periodEnd, total: i.total, get: () => fmPortalApi.invoicePdf(cid, i.id, `${i.invoiceNumber}.pdf`) })),
    ...(cn.data ?? []).map(c => ({ key: "c" + c.id, name: c.creditNoteNumber, kind: "Credit note", date: c.issueDate, total: c.total, get: () => fmPortalApi.creditPdf(cid, c.id, `${c.creditNoteNumber}.pdf`) }))]
    .sort((a, b) => (b.date ?? "").localeCompare(a.date ?? ""))
  return (
    <div>
      <div style={{ fontWeight: 700, marginBottom: space(2) }}>Service summary</div>
      {orders.isError ? <p style={empty}>We could not load your service summary.</p> : orders.isLoading ? <p style={empty}>Loading…</p> : r.total === 0 ? <p style={empty}>There is nothing to report yet. Once you have service requests, a summary appears here.</p> : <>
        <div style={{ display: "flex", gap: space(3), flexWrap: "wrap" }}>
          {tile("Requests", String(r.total), `${r.open} still open`)}
          {tile("Fixed", String(r.completed), `${r.completedLast90} in the last 90 days`)}
          {tile("Average time to fix", r.avgDaysToFix === null ? "—" : `${r.avgDaysToFix} days`, "From request to done")}
          {tile("Fixed on time", r.slaMetPct === null ? "—" : `${r.slaMetPct}%`, "Against the agreed target")}
        </div>
        {r.byCategory.length > 0 && <div style={{ ...sub, marginTop: space(2) }}>By kind: {r.byCategory.map(c => `${labelOf(PORTAL_CATEGORIES, c.category)} ${c.count}`).join(" · ")}</div>}
        {(orders.data?.totalElements ?? 0) > (orders.data?.content.length ?? 0) && <div style={sub}>Based on your most recent {orders.data!.content.length} requests.</div>}
      </>}
      <div style={{ fontWeight: 700, margin: `${space(5)} 0 ${space(2)}` }}>Documents</div>
      {err && <div role="alert" style={{ margin: `${space(2)} 0`, padding: "8px 12px", background: color.redBg, color: color.redText, borderRadius: 8, fontSize: 13 }}>{err}</div>}
      <div style={panel}>
        {inv.isError || cn.isError ? <p style={empty}>We could not load your documents.</p> : inv.isLoading ? <p style={empty}>Loading…</p> : docs.length === 0 ? <p style={empty}>No documents yet. Invoices and credit notes appear here once they have been sent to you.</p> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr><th style={th}>Document</th><th style={th}>Date</th><th style={{ ...th, textAlign: "right" }}>Amount</th><th style={th} /></tr></thead>
            <tbody>{docs.map(d => <tr key={d.key}><td style={td}><b>{d.name}</b><div style={sub}>{d.kind}</div></td><td style={td}>{d.date ? day(d.date) : "—"}</td><td style={{ ...td, textAlign: "right" }}>{money(d.total)}</td>
              <td style={{ ...td, textAlign: "right" }}><button style={btn()} onClick={() => dl(d.get)}>Download</button></td></tr>)}</tbody></table>)}
      </div>
    </div>
  )
}
