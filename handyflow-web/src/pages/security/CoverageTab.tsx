// src/pages/security/CoverageTab.tsx
//
// Coverage board: for each post (what a site needs) and day, whether the roster covers it, and where it falls short.
// Below it, the posts themselves. A site with no posts is not measured.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import WeeklyHoursPanel from "./WeeklyHoursPanel"
import WorkingTimePolicyPanel from "./WorkingTimePolicyPanel"
import CompanyZonePanel from "./CompanyZonePanel"
import { useAuthStore } from "../../store/auth.store"
import {
  DAY_CHOICES, STATE_TONE, WEEKDAYS, dayLabel, gapText, postBody, postProblem, rangeText, runsPastMidnight, stateLabel, summaryText, timeOnly, toggleDay, weekdaysText,
  type CoverageBoard, type SitePost,
} from "./coverageLogic"
import { useCompanyZone } from "./companyZone"

const sel: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }
const th: React.CSSProperties = { textAlign: "left", padding: 10, fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: 10, fontSize: 13, borderTop: "1px solid var(--hf-border)", verticalAlign: "top" }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const unwrap = (r: { data?: { data?: unknown } & Record<string, unknown> }) => {
  const p = (r.data?.data ?? r.data) as unknown
  return Array.isArray(p) ? p : (p as { content?: unknown[] } | undefined)?.content ?? []
}
const msg = (e: unknown, fallback: string) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

export default function CoverageTab() {
  useCompanyZone()
  const [siteId, setSiteId] = useState("")
  const [days, setDays] = useState(7)
  const canManage = useAuthStore(s => s.hasPermission("SECURITY_MANAGE"))

  const { data: sites = [] } = useQuery<{ id: string; name: string }[]>({ queryKey: ["coverage-sites"], queryFn: async () => unwrap(await apiClient.get("/api/v1/security/sites?size=200")) })
  const { data, isLoading, error } = useQuery<CoverageBoard>({
    queryKey: ["coverage", siteId, days],
    queryFn: async () => (await apiClient.get("/api/v1/security/coverage", { params: { days, ...(siteId ? { siteId } : {}) } })).data?.data,
    refetchInterval: 60000,
  })
  const rows = Array.isArray(data?.rows) ? data!.rows : null

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <select aria-label="Site" value={siteId} onChange={e => setSiteId(e.target.value)} style={sel}><option value="">All sites</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <select aria-label="Days ahead" value={days} onChange={e => setDays(Number(e.target.value))} style={sel}>{DAY_CHOICES.map(d => <option key={d} value={d}>Next {d} days</option>)}</select>
      </div>

      {isLoading && <div style={muted}>Loading…</div>}
      {(error || (data && !rows)) && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The coverage board could not be loaded.</div>}
      {data && rows && (
        <div style={card}>
          <div style={{ fontWeight: 700, marginBottom: 10, color: data.shortCount + data.uncoveredCount > 0 ? "var(--hf-danger-text)" : "var(--hf-text-primary)" }}>{summaryText(data)}</div>
          {rows.length > 0 && (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Day</th><th style={th}>Time</th><th style={th}>Site</th><th style={th}>Post</th><th style={th}>Needed</th><th style={th}>Status</th><th style={th}>Where it is short</th></tr></thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={`${r.postId}-${r.day}`}>
                      <td style={td}>{dayLabel(r.day)}</td>
                      <td style={td}>{rangeText(r.start, r.end)}</td>
                      <td style={td}>{r.siteName}</td>
                      <td style={td}>{r.postName}</td>
                      <td style={td}>{r.needed}</td>
                      <td style={td}><Chip tone={STATE_TONE[r.state] ?? "neutral"}>{stateLabel(r.state)}</Chip></td>
                      <td style={td}>{r.gaps.length === 0 ? <span style={muted}>-</span> : r.gaps.map((g, i) => <div key={i}>{gapText(g)}</div>)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      <WeeklyHoursPanel />

      <WorkingTimePolicyPanel />

      <CompanyZonePanel />

      <PostsEditor sites={sites} siteId={siteId} canManage={canManage} />
    </div>
  )
}

function PostsEditor({ sites, siteId, canManage }: { sites: { id: string; name: string }[]; siteId: string; canManage: boolean }) {
  const qc = useQueryClient()
  const [pick, setPick] = useState("")
  const site = siteId || pick
  const [name, setName] = useState("")
  const [dayList, setDayList] = useState<number[]>([1, 2, 3, 4, 5])
  const [start, setStart] = useState("06:00")
  const [end, setEnd] = useState("18:00")
  const [guards, setGuards] = useState("1")
  const [err, setErr] = useState("")

  const { data: posts, error } = useQuery<SitePost[]>({
    queryKey: ["site-posts", site],
    enabled: !!site,
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${site}/coverage-posts`)).data?.data,
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["site-posts", site] }); qc.invalidateQueries({ queryKey: ["coverage"] }) }
  const add = useMutation({
    mutationFn: async () => { await apiClient.post(`/api/v1/security/sites/${site}/coverage-posts`, postBody(name, dayList, start, end, guards)) },
    onSuccess: () => { setName(""); setErr(""); refresh() },
    onError: e => setErr(msg(e, "Could not add the post. Please try again.")),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => { await apiClient.delete(`/api/v1/security/sites/${site}/coverage-posts/${id}`) },
    onSuccess: () => { setErr(""); refresh() },
    onError: e => setErr(msg(e, "Could not remove the post. Please try again.")),
  })
  const submit = () => {
    const p = postProblem(name, dayList, start, end, guards)
    if (p) { setErr(p); return }
    setErr(""); add.mutate()
  }

  return (
    <section aria-label="Site posts" style={card}>
      <div style={{ fontWeight: 700, marginBottom: 6 }}>What each site needs (posts)</div>
      <div style={{ ...muted, marginBottom: 10 }}>A post is a place and time that must be staffed, for example "Main gate, nights, 2 guards". Coverage is measured against posts; a site with none is not measured.</div>
      {!siteId && <select aria-label="Posts for site" value={pick} onChange={e => setPick(e.target.value)} style={{ ...sel, marginBottom: 10 }}><option value="">Choose a site…</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>}
      {!site && <div style={muted}>Choose a site to see or set its posts.</div>}
      {site && error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The posts could not be loaded.</div>}
      {site && Array.isArray(posts) && (
        posts.length === 0 ? <div style={muted}>No posts for this site yet.</div> : (
          <div style={{ marginBottom: 8 }}>
            {posts.map(p => (
              <div key={p.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
                <span style={{ flex: 1 }}><b>{p.name}</b> · {weekdaysText(p.weekdays)} · {timeOnly(p.start)}–{timeOnly(p.end)}{runsPastMidnight(p.start, p.end) ? " (past midnight)" : ""} · {p.minGuards} {p.minGuards === 1 ? "guard" : "guards"}</span>
                {canManage && <button type="button" style={btn} disabled={remove.isPending} onClick={() => remove.mutate(p.id)} aria-label={`Remove ${p.name}`}>Remove</button>}
              </div>
            ))}
          </div>
        )
      )}
      {site && canManage && (
        <div style={{ marginTop: 10, paddingTop: 10, borderTop: "1px solid var(--hf-border)" }}>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 8 }}>
            <input aria-label="Post name" placeholder="Post name, e.g. Main gate" value={name} maxLength={80} onChange={e => setName(e.target.value)} style={{ ...sel, width: 220 }} />
            <input aria-label="Starts" type="time" value={start} onChange={e => setStart(e.target.value)} style={sel} />
            <input aria-label="Ends" type="time" value={end} onChange={e => setEnd(e.target.value)} style={sel} />
            <input aria-label="Guards needed" type="number" min={1} max={50} value={guards} onChange={e => setGuards(e.target.value)} style={{ ...sel, width: 80 }} />
          </div>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 8 }}>
            {WEEKDAYS.map(w => <label key={w.n} style={{ fontSize: 13, display: "flex", gap: 4, alignItems: "center" }}><input type="checkbox" aria-label={w.label} checked={dayList.includes(w.n)} onChange={() => setDayList(d => toggleDay(d, w.n))} />{w.label}</label>)}
          </div>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <button type="button" style={btn} disabled={add.isPending} onClick={submit}>{add.isPending ? "Adding…" : "Add post"}</button>
            <span style={muted}>Times are South African time. An end time at or before the start runs past midnight.</span>
          </div>
        </div>
      )}
      {err && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginTop: 6 }}>{err}</div>}
    </section>
  )
}
