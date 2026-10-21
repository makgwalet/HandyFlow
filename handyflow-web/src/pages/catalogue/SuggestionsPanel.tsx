// src/pages/catalogue/SuggestionsPanel.tsx — suggestions for filling gaps (category, brand, SKU) worked out from the tenant's own catalogue.
// Fixed rules, no outside service. Nothing changes until someone presses Apply, and applying goes through the ordinary item edit.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner } from "./CatalogueKit"
import { linkBtn } from "./catalogue.styles"
import { SUGGESTION_KIND, confidenceText, enrichmentText, errorMessage, type Enrichment, type Suggestion, type SuggestionEntry } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

/** The suggestions of one item, with Apply for those that can be applied. Used here and on Product 360. */
export function SuggestionList({ itemId, suggestions, canWrite, onApplied }: { itemId: string; suggestions: Suggestion[]; canWrite: boolean; onApplied: () => void }) {
  const [error, setError] = useState("")
  const apply = useMutation({
    mutationFn: (s: Suggestion) => apiClient.post(`/api/v1/catalogue/items/${itemId}/enrichment/apply`, { kind: s.kind, value: s.value }),
    onSuccess: () => { setError(""); onApplied() }, onError: e => setError(errorMessage(e, "Couldn't apply the suggestion.")),
  })
  return (
    <div>
      {error && <ErrorBanner message={error} />}
      <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
        {suggestions.map(s => (
          <li key={s.kind} style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center", padding: "6px 0" }}>
            <div style={{ flex: 1, minWidth: 220 }}>
              <strong style={{ fontSize: 13 }}>{SUGGESTION_KIND[s.kind] ?? s.kind}: {s.kind === "PRICE" ? `R ${s.label}` : s.label}</strong>{" "}
              <span style={muted}>· {confidenceText(s.confidence)} ({s.confidence}%)</span>
              <div style={muted}>{s.reason}</div>
            </div>
            {s.applicable && canWrite && <button type="button" style={linkBtn} disabled={apply.isPending} aria-label={`Apply ${SUGGESTION_KIND[s.kind] ?? s.kind} ${s.label}`} onClick={() => apply.mutate(s)}>Apply</button>}
          </li>
        ))}
      </ul>
    </div>
  )
}

/** The suggestions block of one item (Product 360 overview); shows nothing when there are none. */
export function ItemSuggestions({ itemId, canWrite, onApplied }: { itemId: string; canWrite: boolean; onApplied: () => void }) {
  const qc = useQueryClient()
  const q = useQuery<SuggestionEntry>({ queryKey: ["catalogue", "item-suggestions", itemId], queryFn: async () => inner<SuggestionEntry>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/enrichment`)) })
  if (!q.data || !Array.isArray(q.data.suggestions) || q.data.suggestions.length === 0) return null
  return (
    <section aria-label="Suggestions" style={{ marginTop: 16 }}>
      <h4 style={{ margin: "0 0 4px", fontSize: 13, color: "var(--hf-text)" }}>Suggestions</h4>
      <SuggestionList itemId={itemId} suggestions={q.data.suggestions} canWrite={canWrite} onApplied={() => { qc.invalidateQueries({ queryKey: ["catalogue", "item-suggestions", itemId] }); onApplied() }} />
    </section>
  )
}

export default function SuggestionsPanel() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const canWrite = usePermissions(["CATALOGUE_WRITE", "SETTINGS_MANAGE"])
  const q = useQuery<Enrichment>({ queryKey: ["catalogue", "suggestions"], queryFn: async () => inner<Enrichment>(await apiClient.get("/api/v1/catalogue/enrichment")) })
  const d = q.data
  return (
    <div>
      <p style={{ ...muted, maxWidth: 680, marginTop: 0 }}>Suggestions come from your own catalogue: items with similar names, the brands and SKU pattern you already use, and the usual margin of a category. They are fixed rules, not a guess from outside, and nothing changes until you press Apply.</p>
      {q.isLoading && <p role="status">Looking through your catalogue…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't work out suggestions.")} />}
      {d && (
        <>
          <p role="status" style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)", margin: "0 0 14px" }}>{enrichmentText(d)}</p>
          {d.items.map(e => (
            <section key={e.itemId} aria-label={e.name} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border-subtle)", borderRadius: 12, padding: 14, marginBottom: 10 }}>
              <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6 }}>
                <button type="button" style={{ ...linkBtn, border: "none", padding: 0, fontSize: 14, color: "var(--hf-text)" }} onClick={() => navigate(`/catalogue/items?item=${e.itemId}`)}>{e.name}</button>
                <span style={muted}>{e.sku ?? "No SKU"}</span>
              </div>
              <SuggestionList itemId={e.itemId} suggestions={e.suggestions} canWrite={canWrite} onApplied={() => qc.invalidateQueries({ queryKey: ["catalogue", "suggestions"] })} />
            </section>
          ))}
          {d.truncated && <p style={muted}>Showing the 100 most confident. Apply some and open this screen again for the rest.</p>}
        </>
      )}
    </div>
  )
}
