// src/pages/catalogue/ChannelsTab.tsx — where one item is published, what stops it elsewhere, and publish / take off.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ErrorBanner } from "./CatalogueKit"
import { linkBtn, primaryBtn } from "./catalogue.styles"
import { channelKindLabel, errorMessage, type ItemChannel, type PublishResult } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

export default function ChannelsTab({ itemId, canPublish }: { itemId: string; canPublish: boolean }) {
  const qc = useQueryClient()
  const key = ["catalogue", "item-channels", itemId]
  const [error, setError] = useState("")
  const q = useQuery<ItemChannel[]>({ queryKey: key, queryFn: async () => inner<ItemChannel[]>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/channels`)) ?? [] })
  const act = useMutation({
    mutationFn: async (a: { channelId: string; verb: "publish" | "unpublish" }) =>
      inner<PublishResult>(await apiClient.post(`/api/v1/catalogue/channels/${a.channelId}/${a.verb}`, { itemIds: [itemId], dryRun: false })),
    onSuccess: r => {
      const row = r.rows[0]
      setError(row?.outcome === "BLOCKED" ? row.problems.join(" ") : "")
      qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["catalogue", "channels"] })
    },
    onError: e => setError(errorMessage(e, "Couldn't complete that.")),
  })
  return (
    <div>
      {q.isLoading && <p role="status">Loading…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't load the channels.")} />}
      {error && <ErrorBanner message={error} />}
      {q.data && q.data.length === 0 && <p style={muted}>There are no channels yet. Add one under Channels in the menu.</p>}
      {q.data && q.data.length > 0 && (
        <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
          {q.data.map(c => (
            <li key={c.channelId} style={{ padding: "10px 0", borderTop: "1px solid var(--hf-border-subtle)", display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap", alignItems: "center" }}>
              <div style={{ minWidth: 220, flex: 1 }}>
                <strong style={{ fontSize: 14 }}>{c.name}</strong> <span style={muted}>· {channelKindLabel(c.kind)}{c.channelActive ? "" : " · switched off"}</span>
                <div style={muted}>{c.published ? "Published" : c.problems.length === 0 ? "Ready to publish" : `Not ready: ${c.problems.join(" ")}`}</div>
              </div>
              {canPublish && (c.published
                ? <button type="button" style={linkBtn} disabled={act.isPending} aria-label={`Take off ${c.name}`} onClick={() => act.mutate({ channelId: c.channelId, verb: "unpublish" })}>Take off</button>
                : <button type="button" style={primaryBtn} disabled={act.isPending || c.problems.length > 0 || !c.channelActive} aria-label={`Publish to ${c.name}`} onClick={() => act.mutate({ channelId: c.channelId, verb: "publish" })}>Publish</button>)}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
