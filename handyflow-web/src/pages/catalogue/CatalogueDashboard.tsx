// src/pages/catalogue/CatalogueDashboard.tsx — the Catalogue home: counts, what needs attention, growth, items per category, recent items and activity.
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner, StatusBadge } from "./CatalogueKit"
import { primaryBtn, linkBtn } from "./catalogue.styles"
import { actionText, barPercent, errorMessage, rand, type DashboardData } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border-subtle)", borderRadius: 12, padding: 16 }
const h4: React.CSSProperties = { margin: "0 0 12px", fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

function Tile({ label, value, hint, onClick }: { label: string; value: number; hint?: string; onClick?: () => void }) {
  const body = (
    <>
      <div style={{ fontSize: 26, fontWeight: 700, color: "var(--hf-text)" }}>{value.toLocaleString("en-ZA")}</div>
      <div style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)" }}>{label}</div>
      {hint && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }}>{hint}</div>}
    </>
  )
  return onClick
    ? <button type="button" onClick={onClick} style={{ ...card, textAlign: "left", cursor: "pointer" }}>{body}</button>
    : <div style={card}>{body}</div>
}

function Bars({ rows, label }: { rows: { name: string; value: number }[]; label: string }) {
  const max = Math.max(0, ...rows.map(r => r.value))
  return (
    <ul aria-label={label} style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
      {rows.map(r => (
        <li key={r.name} style={{ display: "grid", gridTemplateColumns: "minmax(70px, 130px) 1fr 44px", gap: 8, alignItems: "center", fontSize: 12 }}>
          <span style={{ color: "var(--hf-text-secondary)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{r.name}</span>
          <span style={{ background: "var(--hf-border-subtle)", borderRadius: 4, height: 10 }}><span style={{ display: "block", height: 10, borderRadius: 4, background: "var(--hf-primary)", width: `${barPercent(r.value, max)}%` }} /></span>
          <span style={{ textAlign: "right", color: "var(--hf-text)" }}>{r.value.toLocaleString("en-ZA")}</span>
        </li>
      ))}
    </ul>
  )
}

export default function CatalogueDashboard() {
  const navigate = useNavigate()
  const goTo = (section: string, item?: string) => navigate(`/catalogue/${section}${item === "new" ? "?new=1" : item ? `?item=${item}` : ""}`)
  const canWrite = usePermissions(["SETTINGS_MANAGE", "CATALOGUE_WRITE"])
  const q = useQuery<DashboardData>({ queryKey: ["catalogue", "dashboard"], queryFn: async () => inner<DashboardData>(await apiClient.get("/api/v1/catalogue/dashboard")) })
  const d = q.data
  return (
    <div style={{ display: "grid", gap: 16 }}>
      {q.isLoading && <p role="status">Loading…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't load the dashboard.")} />}
      {d && (
        <>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {canWrite && <button type="button" style={primaryBtn} onClick={() => goTo("items", "new")}>Add item</button>}
            {d.prices && <button type="button" style={linkBtn} onClick={() => goTo("priceapprovals")}>Review price changes</button>}
          </div>
          <section aria-label="Totals" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 12 }}>
            <Tile label="Items" value={d.counts.items} hint={`${d.counts.products} products · ${d.counts.services} services`} onClick={() => goTo("items")} />
            <Tile label="Variants" value={d.counts.variants} />
            <Tile label="Active SKUs" value={d.counts.activeSkus} hint="Items you can sell now" />
            <Tile label="Drafts" value={d.counts.drafts} hint="Not yet active" />
            <Tile label="Incomplete records" value={d.counts.incomplete} hint="Missing basic details" />
            <Tile label="Discontinued" value={d.counts.discontinued} hint={`${d.counts.discontinuedRecently} in the last 30 days`} />
            {(d.attention.awaitingApproval > 0 || d.counts.drafts > 0) && <Tile label="Awaiting approval" value={d.attention.awaitingApproval} hint="Drafts submitted to go live" onClick={() => goTo("approvals")} />}
            {d.attention.importsWithProblems > 0 && <Tile label="Imports with problems" value={d.attention.importsWithProblems} hint="Last 30 days" onClick={() => goTo("import")} />}
            {d.prices && <Tile label="Price changes waiting" value={d.prices.pending} hint={`${d.prices.scheduled} scheduled`} onClick={() => goTo("priceapprovals")} />}
          </section>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 16 }}>
            <section style={card} aria-label="Growth"><h4 style={h4}>Items over the last 12 months</h4><Bars label="Items by month" rows={d.growth.map(g => ({ name: g.month, value: g.total }))} /></section>
            <section style={card} aria-label="Categories"><h4 style={h4}>Items per category</h4>
              {d.byCategory.length === 0 ? <p style={muted}>Nothing to show yet.</p> : <Bars label="Items by category" rows={d.byCategory.map(c => ({ name: c.name, value: c.count }))} />}</section>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 16 }}>
            <section style={card} aria-label="Recent items"><h4 style={h4}>Recently changed</h4>
              {d.recentItems.length === 0 && <p style={muted}>No items yet.</p>}
              {d.recentItems.map(i => (
                <div key={i.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "7px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                  <button type="button" onClick={() => goTo("items", i.id)} style={{ ...linkBtn, border: "none", padding: 0, fontSize: 13, color: "var(--hf-text)", textAlign: "left", flex: 1 }}>{i.name}</button>
                  <StatusBadge status={i.status} /><span style={{ color: "var(--hf-text-muted)" }}>{rand(i.defaultPrice)}</span>
                </div>
              ))}
            </section>
            <section style={card} aria-label="Recent activity"><h4 style={h4}>Recent activity</h4>
              {d.activity.length === 0 && <p style={muted}>Nothing recorded yet.</p>}
              {d.activity.map((a, n) => (
                <div key={n} style={{ padding: "7px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                  <strong>{actionText(a.action)}</strong>{a.entityName ? ` · ${a.entityName}` : ""}
                  <span style={{ float: "right", color: "var(--hf-text-faint)" }}>{new Date(a.at).toLocaleDateString("en-ZA")}</span>
                  {a.detail && <div style={muted}>{a.detail}</div>}
                </div>
              ))}
            </section>
          </div>
        </>
      )}
    </div>
  )
}
