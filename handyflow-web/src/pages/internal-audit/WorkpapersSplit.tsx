// Workpapers of one engagement: the list on the left, the chosen workpaper on the right.

import { useState } from "react"
import { useQueries, useQuery } from "@tanstack/react-query"
import { Download } from "lucide-react"
import { auditEngagementApi } from "../../api/auditEngagementSummary.api"
import type { UserOption } from "./auditShared"
import ReviewNotesPanel from "./ReviewNotesPanel"
import { FilterBar, Pill } from "./auditUi"
import { filterField } from "./auditUi.styles"
import { shortDate } from "./auditUi.logic"
import { fileSize, filterWorkpapers, nameMap, personName, workpaperRows, workpaperStatus, WORKPAPER_STATUS, type WorkpaperRow } from "./engagementDetail.logic"

const MUTED = "var(--hf-text-muted)"
const label: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }

export function WorkpapersSplit({ engagementId, users }: { engagementId: string; users: UserOption[] }) {
  const folders = useQuery({ queryKey: ["ia-wp-folders", engagementId], queryFn: () => auditEngagementApi.folders(engagementId) })
  const list = folders.data ?? []
  const fileQs = useQueries({ queries: list.map(f => ({ queryKey: ["ia-wp-files", engagementId, f.id], queryFn: () => auditEngagementApi.files(engagementId, f.id) })) })
  const [filter, setFilter] = useState({ status: "all", folder: "all", q: "" })
  const [selected, setSelected] = useState<string | null>(null)
  const [downloadError, setDownloadError] = useState("")

  if (folders.isLoading) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  if (folders.isError) return <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>Workpapers could not be loaded. Refresh the page; if it keeps failing, contact support.</div>

  const names = nameMap(users)
  const byFolder: Record<string, NonNullable<(typeof fileQs)[number]["data"]>> = {}
  list.forEach((f, i) => { byFolder[f.id] = fileQs[i]?.data ?? [] })
  const rows = workpaperRows(list, byFolder, names)
  const shown = filterWorkpapers(rows, filter)
  const chosen = rows.find(r => r.id === selected) ?? null
  const loadingFiles = fileQs.some(q => q.isLoading)
  const failedFiles = fileQs.some(q => q.isError)

  async function download(r: WorkpaperRow) {
    setDownloadError("")
    try { await auditEngagementApi.download(r.id, r.fileName) } catch { setDownloadError("The file could not be downloaded. Try again; if it keeps failing, contact support.") }
  }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      {failedFiles && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>Some folders could not be loaded, so this list may be incomplete.</div>}
      <FilterBar>
        <input aria-label="Search workpapers" placeholder="Search workpapers…" value={filter.q} onChange={e => setFilter({ ...filter, q: e.target.value })} style={{ ...filterField, minWidth: 200 }} />
        <select aria-label="Filter by status" value={filter.status} onChange={e => setFilter({ ...filter, status: e.target.value })} style={filterField}>
          <option value="all">All statuses</option>
          {Object.entries(WORKPAPER_STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
        <select aria-label="Filter by section" value={filter.folder} onChange={e => setFilter({ ...filter, folder: e.target.value })} style={filterField}>
          <option value="all">All sections</option>
          {list.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
        </select>
      </FilterBar>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 14, alignItems: "start" }}>
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
          {loadingFiles && rows.length === 0 ? <p style={{ padding: 14, fontSize: 13, color: MUTED }}>Loading…</p>
            : shown.length === 0 ? <p style={{ padding: 14, fontSize: 13, color: MUTED }}>{rows.length === 0 ? "No workpapers have been added to this engagement yet. Add them from the Engagements list." : "No workpapers match these filters."}</p>
            : (
              <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
                {shown.map(r => {
                  const st = workpaperStatus(r.reviewStatus)
                  const active = r.id === selected
                  return (
                    <li key={r.id}>
                      <button type="button" onClick={() => setSelected(r.id)} aria-current={active}
                        style={{ width: "100%", textAlign: "left", padding: "10px 12px", border: "none", borderBottom: "1px solid var(--hf-border-subtle)", background: active ? "var(--hf-info-soft)" : "transparent", cursor: "pointer", display: "grid", gap: 4 }}>
                        <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center" }}>
                          <span style={{ fontWeight: 600, fontSize: 13, color: "var(--hf-text)", overflowWrap: "anywhere" }}>{r.fileName}</span>
                          <Pill label={st.label} tone={st.tone} />
                        </div>
                        <div style={{ fontSize: 12, color: MUTED }}>{r.folder}{r.preparer ? ` · ${r.preparer}` : ""} · {shortDate(r.updatedAt)}</div>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
        </div>

        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, minHeight: 120 }}>
          {!chosen ? <p style={{ fontSize: 13, color: MUTED, margin: 0 }}>Choose a workpaper to see its details and review notes.</p> : (
            <div style={{ display: "grid", gap: 12 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "flex-start" }}>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)", overflowWrap: "anywhere" }}>{chosen.fileName}</div>
                  <div style={{ fontSize: 12, color: MUTED }}>{chosen.folder} · version {chosen.versionNumber}{chosen.fileSizeBytes != null ? ` · ${fileSize(chosen.fileSizeBytes)}` : ""}</div>
                </div>
                <button type="button" onClick={() => download(chosen)} aria-label={`Download ${chosen.fileName}`}
                  style={{ display: "inline-flex", alignItems: "center", gap: 6, padding: "6px 10px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                  <Download size={14} /> Download
                </button>
              </div>
              {downloadError && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{downloadError}</div>}
              <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "6px 14px", margin: 0, fontSize: 13 }}>
                <dt style={label}>Status</dt><dd style={{ margin: 0 }}><Pill label={workpaperStatus(chosen.reviewStatus).label} tone={workpaperStatus(chosen.reviewStatus).tone} /></dd>
                <dt style={label}>Prepared</dt><dd style={{ margin: 0 }}>{personName(chosen.preparedBy, names)}{chosen.preparedAt ? `, ${shortDate(chosen.preparedAt)}` : ""}</dd>
                <dt style={label}>Reviewed</dt><dd style={{ margin: 0 }}>{chosen.reviewedBy ? `${personName(chosen.reviewedBy, names)}${chosen.reviewedAt ? `, ${shortDate(chosen.reviewedAt)}` : ""}` : "Not yet"}</dd>
                <dt style={label}>Signed off</dt><dd style={{ margin: 0 }}>{chosen.signedOffBy ? `${personName(chosen.signedOffBy, names)}${chosen.signedOffAt ? `, ${shortDate(chosen.signedOffAt)}` : ""}` : "Not yet"}</dd>
              </dl>
              <div>
                <div style={{ ...label, marginBottom: 6 }}>Comments</div>
                <ReviewNotesPanel workpaperId={chosen.id} />
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
