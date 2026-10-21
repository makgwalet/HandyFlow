// src/pages/customers/IntegrationsPanel.tsx
// Integrations: keys that let a website form send leads in, and webhooks that tell your own systems what happens. The server holds the keys and secrets; this sends and shows.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorMessage, unwrapList } from "./crmApi"
import { LEAD_SOURCES, type LeadSource } from "./pipeline.logic"
import {
  EMPTY_WEBHOOK, MAX_KEYS, MAX_WEBHOOKS, WEBHOOK_EVENTS, captureExample, eventLabel, keyProblem, keyStatus, toWebhookForm, toggledEvent, webhookBody, webhookProblem, webhookStatus,
  type CaptureKey, type Webhook, type WebhookDelivery, type WebhookEvent, type WebhookForm,
} from "./integrations.logic"
import { Loading, ErrorNote } from "./CrmKit"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const small: React.CSSProperties = { ...ghost, padding: "3px 8px", fontSize: 12 }
const box: React.CSSProperties = { padding: 10, border: "1px solid var(--hf-border)", borderRadius: 10 }
const when = (iso: string) => { const d = new Date(iso); return Number.isNaN(d.getTime()) ? iso : d.toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) }
const unwrap = <T,>(r: unknown): T => ((r as { data?: { data?: T } })?.data?.data ?? (r as { data?: T })?.data) as T
interface Secret { what: "key" | "secret"; name: string; value: string }

export default function IntegrationsPanel() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE")
  const [error, setError] = useState<string | null>(null), [secret, setSecret] = useState<Secret | null>(null), [note, setNote] = useState<string | null>(null), [copied, setCopied] = useState(false)
  const [keyName, setKeyName] = useState(""), [keySource, setKeySource] = useState<LeadSource>("WEBSITE"), [addingKey, setAddingKey] = useState(false), [revoking, setRevoking] = useState<string | null>(null)
  const [edit, setEdit] = useState<string | "new" | null>(null), [form, setForm] = useState<WebhookForm>(EMPTY_WEBHOOK)
  const keys = useQuery({ queryKey: ["crm", "capture-keys"], queryFn: async () => unwrapList<CaptureKey>(await apiClient.get("/api/v1/crm/integrations/keys")) })
  const hooks = useQuery({ queryKey: ["crm", "webhooks"], queryFn: async () => unwrapList<Webhook>(await apiClient.get("/api/v1/crm/integrations/webhooks")) })
  const log = useQuery({ queryKey: ["crm", "webhook-deliveries"], retry: false, queryFn: async () => unwrapList<WebhookDelivery>(await apiClient.get("/api/v1/crm/integrations/webhooks/deliveries", { params: { limit: 50 } })) })
  const fail = (e: unknown) => { setNote(null); setError(errorMessage(e, "Something went wrong. Try again.")) }
  const reload = () => { setError(null); qc.invalidateQueries({ queryKey: ["crm", "capture-keys"] }); qc.invalidateQueries({ queryKey: ["crm", "webhooks"] }); qc.invalidateQueries({ queryKey: ["crm", "webhook-deliveries"] }) }

  const makeKey = useMutation({ mutationFn: async () => unwrap<{ key: CaptureKey; apiKey: string }>(await apiClient.post("/api/v1/crm/integrations/keys", { name: keyName.trim(), defaultSource: keySource })),
    onSuccess: r => { setSecret({ what: "key", name: r.key.name, value: r.apiKey }); setCopied(false); setAddingKey(false); setKeyName(""); reload() }, onError: fail })
  const revoke = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/integrations/keys/${id}`), onSuccess: () => { setRevoking(null); reload() }, onError: fail })
  const saveHook = useMutation({ mutationFn: async () => edit === "new"
      ? unwrap<{ webhook: Webhook; secret: string }>(await apiClient.post("/api/v1/crm/integrations/webhooks", webhookBody(form)))
      : (await apiClient.put(`/api/v1/crm/integrations/webhooks/${edit}`, webhookBody(form)), null),
    onSuccess: r => { if (r) { setSecret({ what: "secret", name: r.webhook.name, value: r.secret }); setCopied(false) } setEdit(null); reload() }, onError: fail })
  const toggleHook = useMutation({ mutationFn: (w: Webhook) => apiClient.put(`/api/v1/crm/integrations/webhooks/${w.id}`, webhookBody({ ...toWebhookForm(w), active: !w.active })), onSuccess: reload, onError: fail })
  const delHook = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/integrations/webhooks/${id}`), onSuccess: reload, onError: fail })
  const test = useMutation({ mutationFn: async (id: string) => unwrap<WebhookDelivery>(await apiClient.post(`/api/v1/crm/integrations/webhooks/${id}/test`)),
    onSuccess: d => { setError(null); setNote(`Test ${d.outcome === "DONE" ? "worked" : "failed"}: ${d.detail ?? ""}`); reload() }, onError: fail })

  const keyRows = keys.data ?? [], hookRows = hooks.data ?? [], kProblem = addingKey ? keyProblem(keyName) : null, hProblem = edit ? webhookProblem(form) : null
  const busy = makeKey.isPending || revoke.isPending || saveHook.isPending || toggleHook.isPending || delHook.isPending || test.isPending
  const copy = async (text: string) => { try { await navigator.clipboard.writeText(text); setCopied(true) } catch { setCopied(false) } }
  const origin = typeof window !== "undefined" ? window.location.origin : "https://your-handyflow-address"

  return (
    <div style={{ display: "grid", gap: 22, fontSize: 13 }}>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>{error}</div>}
      {note && <div role="status">{note}</div>}
      {secret && (
        <div role="status" aria-label="New secret" style={{ ...box, borderColor: "var(--hf-warning-text)" }}>
          <strong>{secret.what === "key" ? `Key for "${secret.name}"` : `Signing secret for "${secret.name}"`}</strong>
          <div>Copy it now. For your safety it is shown only once and cannot be shown again.</div>
          <code style={{ display: "block", margin: "6px 0", wordBreak: "break-all" }}>{secret.value}</code>
          <div style={{ display: "flex", gap: 6 }}><button type="button" style={small} onClick={() => copy(secret.value)}>{copied ? "Copied" : "Copy"}</button><button type="button" style={small} onClick={() => setSecret(null)}>I have saved it</button></div>
        </div>)}

      <section aria-label="Lead capture keys">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Send leads in</h3>
        <p style={{ margin: "0 0 8px", color: "var(--hf-text-muted)" }}>A website form or another system can add leads with a key. They are routed, scored and put through your workflows like any other lead. An email already on file is not added twice.</p>
        {keys.isLoading && <Loading />}{keys.isError && <ErrorNote>Couldn't load the keys.</ErrorNote>}
        {keys.data && keyRows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No keys yet.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 6 }}>
          {keyRows.map(k => (
            <li key={k.id} aria-label={`Key ${k.name}`} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", opacity: k.active ? 1 : 0.55 }}>
              <strong style={{ minWidth: 150 }}>{k.name}</strong><code>{k.keyPrefix}…</code>
              <span style={{ color: "var(--hf-text-muted)" }}>Source: {LEAD_SOURCES.find(s => s.id === k.defaultSource)?.label ?? k.defaultSource} · {keyStatus(k)}</span>
              {canEdit && k.active && (revoking === k.id
                ? <span style={{ display: "flex", gap: 6, alignItems: "center" }}>Revoke for good?<button type="button" style={small} disabled={busy} aria-label={`Confirm revoke ${k.name}`} onClick={() => revoke.mutate(k.id)}>Revoke</button><button type="button" style={small} onClick={() => setRevoking(null)}>Cancel</button></span>
                : <button type="button" style={small} aria-label={`Revoke ${k.name}`} onClick={() => { setRevoking(k.id); setError(null) }}>Revoke</button>)}
            </li>))}
        </ul>
        {canEdit && !addingKey && <button type="button" style={{ ...ghost, marginTop: 8 }} disabled={keyRows.filter(k => k.active).length >= MAX_KEYS} onClick={() => { setAddingKey(true); setError(null) }}>Add key</button>}
        {canEdit && addingKey && (
          <form aria-label="New key" onSubmit={e => { e.preventDefault(); if (!kProblem) makeKey.mutate() }} style={{ ...box, display: "grid", gap: 8, maxWidth: 480, marginTop: 8 }}>
            <label>Name <input aria-label="Key name" style={{ ...field, width: "100%" }} placeholder="Website contact form" value={keyName} onChange={e => setKeyName(e.target.value)} /></label>
            <label>Lead source when the sender gives none <select aria-label="Default source" style={field} value={keySource} onChange={e => setKeySource(e.target.value as LeadSource)}>{LEAD_SOURCES.map(s => <option key={s.id} value={s.id}>{s.label}</option>)}</select></label>
            {keyName.trim() && kProblem && <span style={{ color: "var(--hf-danger-text)" }}>{kProblem}</span>}
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!kProblem || busy}>{makeKey.isPending ? "Making…" : "Make key"}</button><button type="button" style={ghost} onClick={() => setAddingKey(false)}>Cancel</button></div>
          </form>)}
        <details style={{ marginTop: 8 }}><summary style={{ cursor: "pointer" }}>How a developer sends a lead</summary>
          <pre aria-label="Example call" style={{ ...box, overflowX: "auto", whiteSpace: "pre-wrap" }}>{captureExample(origin)}</pre>
          <p style={{ color: "var(--hf-text-muted)" }}>Only <code>name</code> or <code>company</code> is needed. Add a hidden <code>website</code> field to your form: people leave it empty, and leads that fill it in are quietly ignored. Calls from a browser on another site may be blocked by your browser's cross-site rules, so send from your server.</p>
        </details>
      </section>

      <section aria-label="Webhooks">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Tell your systems</h3>
        <p style={{ margin: "0 0 8px", color: "var(--hf-text-muted)" }}>A webhook sends a signed message to an address you choose when something happens. Only https addresses on the public internet are allowed. Each message has an <code>X-HandyFlow-Signature</code> header (sha256 HMAC of the body, using the secret) so you can check it came from us.</p>
        {hooks.isLoading && <Loading />}{hooks.isError && <ErrorNote>Couldn't load the webhooks.</ErrorNote>}
        {hooks.data && hookRows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No webhooks yet.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
          {hookRows.map(w => (
            <li key={w.id} aria-label={`Webhook ${w.name}`} style={{ display: "grid", gap: 2, opacity: w.active && !w.suspended ? 1 : 0.7 }}>
              <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                <strong style={{ minWidth: 150 }}>{w.name}</strong><span style={{ color: "var(--hf-text-muted)", wordBreak: "break-all" }}>{w.url}</span>
                {canEdit && <span style={{ display: "flex", gap: 6 }}>
                  <button type="button" style={small} aria-label={`Test ${w.name}`} disabled={busy} onClick={() => { setNote(null); test.mutate(w.id) }}>Send test</button>
                  <button type="button" style={small} aria-label={`Edit ${w.name}`} onClick={() => { setForm(toWebhookForm(w)); setEdit(w.id); setError(null) }}>Edit</button>
                  <button type="button" style={small} aria-label={`${w.active ? "Turn off" : "Turn on"} ${w.name}`} disabled={busy} onClick={() => toggleHook.mutate(w)}>{w.active ? "Turn off" : "Turn on"}</button>
                  <button type="button" style={small} aria-label={`Delete ${w.name}`} disabled={busy} onClick={() => delHook.mutate(w.id)}>Delete</button></span>}
              </div>
              <span style={{ color: w.suspended ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>{w.events.map(eventLabel).join(", ")} · {webhookStatus(w)}</span>
            </li>))}
        </ul>
        {canEdit && !edit && <button type="button" style={{ ...ghost, marginTop: 8 }} disabled={hookRows.length >= MAX_WEBHOOKS} onClick={() => { setForm(EMPTY_WEBHOOK); setEdit("new"); setError(null) }}>Add webhook</button>}
        {canEdit && edit && (
          <form aria-label="Webhook" onSubmit={e => { e.preventDefault(); if (!hProblem) saveHook.mutate() }} style={{ ...box, display: "grid", gap: 8, maxWidth: 560, marginTop: 8 }}>
            <label>Name <input aria-label="Webhook name" style={{ ...field, width: "100%" }} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
            <label>Address <input aria-label="Webhook address" placeholder="https://" style={{ ...field, width: "100%" }} value={form.url} onChange={e => setForm({ ...form, url: e.target.value })} /></label>
            <fieldset style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: "6px 10px" }}><legend>Tell it when</legend>
              {WEBHOOK_EVENTS.map(ev => <label key={ev.id} style={{ display: "block" }}><input type="checkbox" checked={form.events.includes(ev.id as WebhookEvent)} onChange={() => setForm({ ...form, events: toggledEvent(form.events, ev.id) })} /> {ev.label}</label>)}
            </fieldset>
            {form.name.trim() && hProblem && <span style={{ color: "var(--hf-danger-text)" }}>{hProblem}</span>}
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!hProblem || busy}>{saveHook.isPending ? "Saving…" : "Save webhook"}</button><button type="button" style={ghost} onClick={() => { setEdit(null); setError(null) }}>Cancel</button></div>
          </form>)}
      </section>

      <section aria-label="Webhook log">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Recent messages</h3>
        {log.isLoading && <Loading />}{log.isError && <ErrorNote>Couldn't load the log.</ErrorNote>}
        {log.data && log.data.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>Nothing has been sent yet.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 4 }}>
          {(log.data ?? []).map(d => (
            <li key={d.id} aria-label={`Message ${d.id}`} style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
              <span style={{ color: "var(--hf-text-muted)", minWidth: 140 }}>{when(d.sentAt)}</span><strong>{d.webhookName}</strong><span>{eventLabel(d.event)}</span>
              <span style={{ color: d.outcome === "DONE" ? "var(--hf-success-text)" : "var(--hf-danger-text)" }}>{d.outcome === "DONE" ? "Delivered" : "Failed"}{d.detail ? `: ${d.detail}` : ""}</span>
            </li>))}
        </ul>
      </section>
    </div>
  )
}
