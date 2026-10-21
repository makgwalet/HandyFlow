// src/pages/catalogue/ChannelsPanel.tsx — sales channels (till, online shop, marketplace, printed list) and what is published to each.
// Publishing checks the item first and says what is missing. Other systems read a channel's catalogue from its feed.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner, Field, Modal } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, linkBtn, primaryBtn, sel, td, th } from "./catalogue.styles"
import {
  CHANNEL_KINDS, EMPTY_CHANNEL_FORM, OUTCOME_LABEL, channelBody, channelKindLabel, channelProblems, errorMessage, fieldErrors, publishText, toChannelForm,
  type Channel, type ChannelForm, type ChannelItem, type CatalogueItem, type PageOf, type PriceList, type PublishResult,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

export default function ChannelsPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["CATALOGUE_PUBLISH", "SETTINGS_MANAGE"])
  const [editing, setEditing] = useState<Channel | "new" | null>(null)
  const [managing, setManaging] = useState<Channel | null>(null)
  const [deleteError, setDeleteError] = useState("")
  const channels = useQuery<Channel[]>({ queryKey: ["catalogue", "channels"], queryFn: async () => inner<Channel[]>(await apiClient.get("/api/v1/catalogue/channels")) ?? [] })
  const refresh = () => qc.invalidateQueries({ queryKey: ["catalogue", "channels"] })
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/channels/${id}`),
    onSuccess: () => { refresh(); setDeleteError("") }, onError: e => setDeleteError(errorMessage(e, "Couldn't delete the channel.")),
  })
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12, gap: 12, flexWrap: "wrap" }}>
        <p style={{ margin: 0, ...muted, maxWidth: 640 }}>A channel is where items are sold or listed. Publish an item to a channel when it is ready; other systems read what a channel sells from its feed.</p>
        {canManage && <button type="button" style={primaryBtn} onClick={() => setEditing("new")}><Plus size={15} /> Add channel</button>}
      </div>
      {channels.isLoading && <p role="status">Loading…</p>}
      {channels.isError && <ErrorBanner message={errorMessage(channels.error, "Couldn't load the channels.")} />}
      {deleteError && <ErrorBanner message={deleteError} />}
      {channels.data && channels.data.length === 0 && <p style={muted}>No channels yet.{canManage ? " Add one to start publishing items." : ""}</p>}
      {channels.data && channels.data.length > 0 && (
        <div style={{ overflowX: "auto", border: "1px solid var(--hf-primary-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Code</th><th style={th}>Name</th><th style={th}>Kind</th><th style={th}>Price list</th><th style={th}>Items</th><th style={th}>State</th><th style={th}><span style={{ position: "absolute", left: -9999 }}>Actions</span></th></tr></thead>
            <tbody>
              {channels.data.map(c => (
                <tr key={c.id}>
                  <td style={{ ...td, fontWeight: 700 }}>{c.code}</td><td style={td}>{c.name}</td><td style={td}>{channelKindLabel(c.kind)}</td>
                  <td style={td}>{c.priceListName ?? "Default"}</td><td style={td}>{c.publishedCount}</td><td style={td}>{c.active ? "On" : "Off"}</td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    <button type="button" style={linkBtn} aria-label={`Items on ${c.code}`} onClick={() => setManaging(c)}>Items</button>{" "}
                    {canManage && <>
                      <button type="button" style={linkBtn} aria-label={`Edit ${c.code}`} onClick={() => setEditing(c)}>Edit</button>{" "}
                      <button type="button" style={linkBtn} aria-label={`Delete ${c.code}`} onClick={() => { setDeleteError(""); del.mutate(c.id) }}>Delete</button>
                    </>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p style={{ ...muted, marginTop: 14 }}>For other systems: a channel's catalogue is at <code>/api/v1/catalogue/channels/&lt;code&gt;/feed</code>, and what changed since a cursor at <code>/api/v1/catalogue/sync/changes?since=0</code>.</p>
      {editing && <ChannelModal channel={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={() => { refresh(); setEditing(null) }} />}
      {managing && <ChannelItemsModal channel={managing} canManage={canManage} onClose={() => setManaging(null)} onChanged={refresh} />}
    </div>
  )
}

function ChannelModal({ channel, onClose, onSaved }: { channel: Channel | null; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<ChannelForm>(channel ? toChannelForm(channel) : EMPTY_CHANNEL_FORM)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const set = <K extends keyof ChannelForm>(k: K, v: ChannelForm[K]) => setForm(f => ({ ...f, [k]: v }))
  const lists = useQuery<PriceList[]>({ queryKey: ["catalogue", "price-lists"], queryFn: async () => inner<PriceList[]>(await apiClient.get("/api/v1/catalogue/price-lists")) ?? [] })
  const save = useMutation({
    mutationFn: () => channel ? apiClient.put(`/api/v1/catalogue/channels/${channel.id}`, channelBody(form)) : apiClient.post("/api/v1/catalogue/channels", channelBody(form)),
    onSuccess: onSaved,
    onError: e => {
      const msg = errorMessage(e, "Couldn't save the channel."), server = fieldErrors(e)
      if (/code .* already exists/.test(msg)) { setErrors({ code: msg }); setGeneral("") } else { setErrors(server); setGeneral(Object.keys(server).length ? "" : msg) }
    },
  })
  const submit = () => { const errs = channelProblems(form, !channel); setErrors(errs); setGeneral(""); if (Object.keys(errs).length === 0) save.mutate() }
  return (
    <Modal title={channel ? `Edit — ${channel.code}` : "New channel"} onClose={onClose}>
      <Field label="Code" required error={errors.code} id="ch-code">
        <input id="ch-code" style={errors.code ? inpErr : inp} value={form.code} readOnly={!!channel} title={channel ? "The code cannot change, because other systems use it" : undefined} onChange={e => set("code", e.target.value)} placeholder="e.g. web-shop" autoFocus={!channel} />
      </Field>
      <Field label="Name" required error={errors.name} id="ch-name"><input id="ch-name" style={errors.name ? inpErr : inp} value={form.name} onChange={e => set("name", e.target.value)} placeholder="e.g. Online shop" /></Field>
      <Field label="Kind" id="ch-kind">
        <select id="ch-kind" style={sel} value={form.kind} onChange={e => set("kind", e.target.value as ChannelForm["kind"])}>{CHANNEL_KINDS.map(k => <option key={k.id} value={k.id}>{k.label}</option>)}</select>
      </Field>
      <Field label="Sells from price list" id="ch-list">
        <select id="ch-list" style={sel} value={form.priceListId} onChange={e => set("priceListId", e.target.value)}>
          <option value="">Default list</option>
          {(lists.data ?? []).filter(l => !l.defaultList && l.active).map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
        </select>
      </Field>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 16 }}>
        <input type="checkbox" checked={form.active} onChange={e => set("active", e.target.checked)} /> Switched on
      </label>
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primaryBtn} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving…" : channel ? "Save changes" : "Create channel"}</button>
      </div>
    </Modal>
  )
}

function ChannelItemsModal({ channel, canManage, onClose, onChanged }: { channel: Channel; canManage: boolean; onClose: () => void; onChanged: () => void }) {
  const qc = useQueryClient()
  const [search, setSearch] = useState("")
  const [picked, setPicked] = useState<{ id: string; name: string }[]>([])
  const [result, setResult] = useState<{ r: PublishResult; verb: "publish" | "unpublish" } | null>(null)
  const [error, setError] = useState("")
  const key = ["catalogue", "channel-items", channel.id]
  const published = useQuery<ChannelItem[]>({ queryKey: key, queryFn: async () => inner<ChannelItem[]>(await apiClient.get(`/api/v1/catalogue/channels/${channel.id}/items`)) ?? [] })
  const found = useQuery<PageOf<CatalogueItem>>({
    queryKey: ["catalogue", "channel-search", search], enabled: canManage && search.trim().length >= 2,
    queryFn: async () => inner<PageOf<CatalogueItem>>(await apiClient.get("/api/v1/catalogue/items/search", { params: { q: search.trim(), size: 8 } })),
  })
  const done = () => { qc.invalidateQueries({ queryKey: key }); onChanged() }
  const run = useMutation({
    mutationFn: async (a: { verb: "publish" | "unpublish"; ids: string[]; dryRun: boolean }) =>
      inner<PublishResult>(await apiClient.post(`/api/v1/catalogue/channels/${channel.id}/${a.verb}`, { itemIds: a.ids, dryRun: a.dryRun })),
    onSuccess: (r, a) => {
      setError(""); setResult({ r, verb: a.verb })
      if (!a.dryRun) { done(); if (a.verb === "publish") setPicked(p => p.filter(x => r.rows.find(y => y.itemId === x.id)?.outcome === "BLOCKED")) }
    },
    onError: e => setError(errorMessage(e, "Couldn't complete that.")),
  })
  const ids = picked.map(p => p.id)
  const pick = (i: CatalogueItem) => { if (!picked.some(p => p.id === i.id)) setPicked(p => [...p, { id: i.id, name: i.name }]); setSearch(""); setResult(null) }

  return (
    <Modal title={`${channel.name} — items`} onClose={onClose} wide>
      {error && <ErrorBanner message={error} />}
      {!channel.active && <p style={{ ...muted, color: "var(--hf-danger-text)" }}>This channel is switched off. Switch it on to publish more items.</p>}
      {canManage && (
        <section aria-label="Add items" style={{ marginBottom: 18 }}>
          <label htmlFor="ch-find" style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }}>Find items to publish</label>
          <input id="ch-find" style={{ ...inp, maxWidth: 360 }} value={search} onChange={e => setSearch(e.target.value)} placeholder="Name, SKU or barcode" />
          {(found.data?.content ?? []).length > 0 && (
            <ul style={{ listStyle: "none", margin: "6px 0 0", padding: 0, border: "1px solid var(--hf-border-subtle)", borderRadius: 8, maxWidth: 360 }}>
              {(found.data?.content ?? []).filter(i => !i.parentId).map(i => <li key={i.id}><button type="button" style={{ ...linkBtn, border: "none", width: "100%", textAlign: "left" }} onClick={() => pick(i)}>{i.name}{i.sku ? ` · ${i.sku}` : ""}</button></li>)}
            </ul>
          )}
          {picked.length > 0 && (
            <div style={{ marginTop: 10 }}>
              <ul style={{ margin: "0 0 10px", paddingLeft: 18, ...muted }}>
                {picked.map(p => <li key={p.id}>{p.name} <button type="button" style={{ ...linkBtn, marginLeft: 6 }} onClick={() => setPicked(x => x.filter(y => y.id !== p.id))} aria-label={`Leave out ${p.name}`}>Leave out</button></li>)}
              </ul>
              <div style={{ display: "flex", gap: 8 }}>
                <button type="button" style={linkBtn} disabled={run.isPending} onClick={() => run.mutate({ verb: "publish", ids, dryRun: true })}>Check</button>
                <button type="button" style={primaryBtn} disabled={run.isPending || !channel.active} onClick={() => run.mutate({ verb: "publish", ids, dryRun: false })}>{run.isPending ? "Working…" : `Publish ${ids.length} item${ids.length === 1 ? "" : "s"}`}</button>
              </div>
            </div>
          )}
          {result && (
            <div role="status" style={{ marginTop: 12 }}>
              <p style={{ margin: "0 0 6px", fontWeight: 600, fontSize: 13 }}>{publishText(result.r, result.verb)}</p>
              <ul style={{ margin: 0, paddingLeft: 18, ...muted }}>
                {result.r.rows.map(row => <li key={row.itemId}>{row.name ?? "Unknown item"} — {OUTCOME_LABEL[row.outcome] ?? row.outcome}{row.problems.length > 0 ? `: ${row.problems.join(" ")}` : ""}</li>)}
              </ul>
            </div>
          )}
        </section>
      )}
      <h4 style={{ margin: "0 0 8px", fontSize: 13 }}>Published ({published.data?.length ?? 0})</h4>
      {published.isLoading && <p role="status">Loading…</p>}
      {published.isError && <ErrorBanner message={errorMessage(published.error, "Couldn't load the published items.")} />}
      {published.data && published.data.length === 0 && <p style={muted}>Nothing is published to this channel yet.</p>}
      {published.data && published.data.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Item</th><th style={th}>SKU</th><th style={th}>Status</th><th style={th}><span style={{ position: "absolute", left: -9999 }}>Actions</span></th></tr></thead>
          <tbody>
            {published.data.map(p => (
              <tr key={p.itemId}>
                <td style={td}>{p.name ?? "(deleted item)"}</td><td style={td}>{p.sku ?? "—"}</td>
                <td style={td}>{p.status === "ACTIVE" ? "Live" : p.status === "DELETED" ? "Deleted" : `Not live (${p.status.toLowerCase()})`}</td>
                <td style={{ ...td, textAlign: "right" }}>{canManage && <button type="button" style={linkBtn} disabled={run.isPending} aria-label={`Take ${p.name ?? "item"} off`} onClick={() => run.mutate({ verb: "unpublish", ids: [p.itemId], dryRun: false })}>Take off</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Modal>
  )
}
