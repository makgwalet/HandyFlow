// src/pages/catalogue/DataQualityPanel.tsx — records that need attention: missing basics, odd prices, unfinished drafts, possible duplicates.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { ErrorBanner } from "./CatalogueKit"
import { linkBtn } from "./catalogue.styles"
import { errorMessage, qualityText, type DataQuality } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border-subtle)", borderRadius: 12, padding: 16, marginBottom: 12 }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

export default function DataQualityPanel() {
  const navigate = useNavigate()
  const [open, setOpen] = useState<string | null>(null)
  const q = useQuery<DataQuality>({ queryKey: ["catalogue", "quality"], queryFn: async () => inner<DataQuality>(await apiClient.get("/api/v1/catalogue/data-quality")) })
  const d = q.data
  return (
    <div>
      {q.isLoading && <p role="status">Checking your catalogue…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't run the check.")} />}
      {d && (
        <>
          <p role="status" style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)", margin: "0 0 14px" }}>{qualityText(d)}</p>
          {d.groups.map(g => (
            <section key={g.kind} style={card} aria-label={g.label}>
              <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
                <div style={{ flex: 1, minWidth: 220 }}>
                  <strong style={{ fontSize: 14, color: "var(--hf-text)" }}>{g.label}</strong> <span style={muted}>({g.count})</span>
                  <div style={{ ...muted, marginTop: 2 }}>{g.advice}</div>
                </div>
                <button type="button" style={linkBtn} aria-expanded={open === g.kind} onClick={() => setOpen(open === g.kind ? null : g.kind)}>{open === g.kind ? "Hide" : "Show items"}</button>
              </div>
              {open === g.kind && (
                <ul style={{ listStyle: "none", margin: "12px 0 0", padding: 0 }}>
                  {g.items.map(i => (
                    <li key={`${i.itemId}-${i.detail}`} style={{ display: "flex", gap: 10, alignItems: "baseline", padding: "7px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                      <button type="button" style={{ ...linkBtn, border: "none", padding: 0, color: "var(--hf-text)", fontSize: 13, textAlign: "left" }} onClick={() => navigate(`/catalogue/items?item=${i.itemId}`)}>{i.name}</button>
                      {i.sku && <span style={{ fontFamily: "monospace", color: "var(--hf-text-faint)" }}>{i.sku}</span>}
                      <span style={{ ...muted, flex: 1 }}>{i.detail}</span>
                    </li>
                  ))}
                  {g.count > g.items.length && <li style={{ ...muted, paddingTop: 8 }}>Showing {g.items.length} of {g.count}.</li>}
                </ul>
              )}
            </section>
          ))}
        </>
      )}
    </div>
  )
}
