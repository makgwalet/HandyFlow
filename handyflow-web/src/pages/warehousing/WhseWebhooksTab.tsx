// src/pages/warehousing/WhseWebhooksTab.tsx
//
// Webhooks: send warehousing events (goods received, picking started, order shipped or cancelled) to your own systems. Each message is signed,
// retried with growing waits, and logged. The signing secret is shown once, when a webhook is made or its secret replaced.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnDanger, btnGhost, btnPrimary, card, Chip, ErrorLine, inputStyle, labelStyle } from "./whseUi"
import { DELIVERY_STATUS, canRedeliver, chosenEvents, eventLabel, urlProblem, type DeliveryStatus } from "./webhooks"

export interface Webhook { id: string; name: string; url: string; events: string[]; active: boolean; suspended: boolean; consecutiveFailures: number; createdAt: string }
export interface Delivery {
  id: string; webhookId: string; webhookName: string; eventType: string; eventId: string; status: DeliveryStatus; attempts: number; nextAttemptAt: string
  statusCode: number | null; detail: string | null; lastAttemptAt: string | null; deliveredAt: string | null; createdAt: string
}
interface Created { webhook: Webhook; secret: string }
interface TestResult { ok: boolean; statusCode: number | null; detail: string | null }

const stamp = (iso: string | null) => (iso ? new Date(iso).toLocaleString("en-ZA", { dateStyle: "short", timeStyle: "short" }) : "–")

function WebhookForm({ events, onSaved }: { events: string[]; onSaved: (c: Created) => void }) {
  const [name, setName] = useState("")
  const [url, setUrl] = useState("")
  const [all, setAll] = useState(true)
  const [picked, setPicked] = useState<string[]>([])
  const evs = chosenEvents(all, picked, events)
  const problem = !name.trim() ? "Name the webhook." : urlProblem(url) ?? (evs.length === 0 ? "Choose at least one event." : null)
  const create = useMutation({
    mutationFn: async () => (await apiClient.post("/api/v1/warehousing/webhooks", { name: name.trim(), url: url.trim(), events: evs })).data as Created,
    onSuccess: c => { setName(""); setUrl(""); setAll(true); setPicked([]); onSaved(c) },
  })
  return (
    <div style={{ ...card, padding: 14, marginBottom: 16 }}>
      <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 10px" }}>Add a webhook</p>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
        <div><label style={labelStyle} htmlFor="wh-name">Name</label><input id="wh-name" style={{ ...inputStyle, width: 180 }} value={name} onChange={e => setName(e.target.value)} maxLength={120} /></div>
        <div style={{ flex: 1, minWidth: 260 }}><label style={labelStyle} htmlFor="wh-url">Address (https)</label>
          <input id="wh-url" style={{ ...inputStyle, width: "100%" }} placeholder="https://erp.example.com/hooks/warehouse" value={url} onChange={e => setUrl(e.target.value)} maxLength={500} /></div>
      </div>
      <fieldset style={{ border: "none", padding: 0, margin: "12px 0 0" }}>
        <legend style={{ ...labelStyle, padding: 0 }}>Events</legend>
        <label style={{ fontSize: 12.5, marginRight: 14 }}><input type="checkbox" checked={all} onChange={e => setAll(e.target.checked)} /> All events</label>
        {events.map(ev => (
          <label key={ev} style={{ fontSize: 12.5, marginRight: 14, opacity: all ? 0.5 : 1 }}>
            <input type="checkbox" disabled={all} checked={all || picked.includes(ev)} onChange={e => setPicked(p => e.target.checked ? [...p, ev] : p.filter(x => x !== ev))} /> {eventLabel(ev)}
          </label>
        ))}
      </fieldset>
      {(name || url) && problem && <p style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "8px 0 0" }}>{problem}</p>}
      <ErrorLine error={create.error} />
      <div style={{ marginTop: 12 }}>
        <button style={{ ...btnPrimary, opacity: problem ? 0.5 : 1 }} disabled={!!problem || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Saving…" : "Add webhook"}</button>
      </div>
    </div>
  )
}

export default function WhseWebhooksTab() {
  const qc = useQueryClient()
  const [secret, setSecret] = useState<{ name: string; value: string } | null>(null)
  const [tests, setTests] = useState<Record<string, TestResult>>({})
  const [status, setStatus] = useState("")
  const refresh = () => { qc.invalidateQueries({ queryKey: ["whse-webhooks"] }); qc.invalidateQueries({ queryKey: ["whse-webhook-deliveries"] }) }

  const { data: events = [] } = useQuery<string[]>({ queryKey: ["whse-webhook-events"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/webhooks/events")).data })
  const { data: hooks = [], isLoading } = useQuery<Webhook[]>({ queryKey: ["whse-webhooks"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/webhooks")).data })
  const { data: deliveries = [] } = useQuery<Delivery[]>({ queryKey: ["whse-webhook-deliveries", status], refetchInterval: 20000,
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/webhooks/deliveries", { params: { status: status || undefined, limit: 50 } })).data })

  const act = useMutation({
    mutationFn: async (a: { kind: "resume" | "delete" | "pause" | "resume-active"; w: Webhook }) => {
      const base = `/api/v1/warehousing/webhooks/${a.w.id}`
      if (a.kind === "resume") return apiClient.post(`${base}/resume`)
      if (a.kind === "delete") return apiClient.delete(base)
      return apiClient.put(base, { name: a.w.name, url: a.w.url, events: a.w.events, active: a.kind === "resume-active" })
    },
    onSuccess: refresh,
  })
  const rotate = useMutation({
    mutationFn: async (w: Webhook) => (await apiClient.post(`/api/v1/warehousing/webhooks/${w.id}/rotate-secret`)).data as Created,
    onSuccess: c => { setSecret({ name: c.webhook.name, value: c.secret }); refresh() },
  })
  const test = useMutation({
    mutationFn: async (w: Webhook) => ({ id: w.id, r: (await apiClient.post(`/api/v1/warehousing/webhooks/${w.id}/test`)).data as TestResult }),
    onSuccess: ({ id, r }) => setTests(t => ({ ...t, [id]: r })),
  })
  const redeliver = useMutation({
    mutationFn: async (d: Delivery) => apiClient.post(`/api/v1/warehousing/webhooks/deliveries/${d.id}/redeliver`),
    onSuccess: refresh,
  })

  return (
    <div>
      <h2 style={{ fontSize: 18, fontWeight: 700, margin: "0 0 4px" }}>Webhooks</h2>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 16px" }}>
        Tell your own systems when something happens here. Each message is signed with the webhook's secret (header <code>X-HandyFlow-Signature</code>), tried again if your server does not answer, and a webhook that keeps failing is paused.
      </p>

      {secret && (
        <div role="alert" style={{ ...card, padding: 14, marginBottom: 16, borderColor: "var(--hf-warning-border, var(--hf-border))" }}>
          <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 6px" }}>Signing secret for {secret.name}</p>
          <code data-testid="wh-secret" style={{ display: "block", wordBreak: "break-all", fontSize: 12.5, padding: 8, background: "var(--hf-surface-muted)", borderRadius: 6 }}>{secret.value}</code>
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "8px 0 10px" }}>Copy it now. It is not shown again; if you lose it, replace it.</p>
          <button style={btnGhost} onClick={() => setSecret(null)}>I have copied it</button>
        </div>
      )}

      <WebhookForm events={events} onSaved={c => { setSecret({ name: c.webhook.name, value: c.secret }); refresh() }} />

      <div style={{ ...card, marginBottom: 20 }}>
        {isLoading ? <p style={{ padding: 14, fontSize: 13 }}>Loading…</p> : hooks.length === 0 ? (
          <p style={{ padding: 14, fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>No webhooks yet.</p>
        ) : hooks.map(w => (
          <div key={w.id} style={{ padding: 14, borderTop: "1px solid var(--hf-border)" }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <strong style={{ fontSize: 13.5 }}>{w.name}</strong>
              {w.suspended ? <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Paused after repeated failures</Chip>
                : w.active ? <Chip bg="var(--hf-success-soft)" fg="var(--hf-success-text-strong)">Active</Chip>
                : <Chip bg="var(--hf-surface-muted)" fg="var(--hf-text-muted)">Switched off</Chip>}
              {!w.suspended && w.consecutiveFailures > 0 && <span style={{ fontSize: 12, color: "var(--hf-warning-text-deep)" }}>{w.consecutiveFailures} failed in a row</span>}
            </div>
            <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "4px 0 8px", wordBreak: "break-all" }}>{w.url} · {w.events.map(eventLabel).join(", ")}</p>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button style={btnGhost} disabled={test.isPending} onClick={() => test.mutate(w)}>Send a test</button>
              {w.suspended && <button style={btnPrimary} onClick={() => act.mutate({ kind: "resume", w })}>Resume</button>}
              <button style={btnGhost} onClick={() => act.mutate({ kind: w.active ? "pause" : "resume-active", w })}>{w.active ? "Switch off" : "Switch on"}</button>
              <button style={btnGhost} disabled={rotate.isPending} onClick={() => { if (window.confirm("Replace the secret? The old one stops working at once.")) rotate.mutate(w) }}>Replace secret</button>
              <button style={btnDanger} onClick={() => { if (window.confirm(`Delete ${w.name}? Messages still waiting for it will not be sent.`)) act.mutate({ kind: "delete", w }) }}>Delete</button>
            </div>
            {tests[w.id] && (
              <p role="status" style={{ fontSize: 12.5, margin: "8px 0 0", color: tests[w.id].ok ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>
                {tests[w.id].ok ? "Test delivered. " : "Test failed. "}{tests[w.id].detail}
              </p>
            )}
          </div>
        ))}
        <ErrorLine error={act.error ?? rotate.error ?? test.error} />
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
        <h3 style={{ fontSize: 14, fontWeight: 700, margin: 0 }}>Recent messages</h3>
        <select aria-label="Filter by status" style={inputStyle} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All</option>
          {(Object.keys(DELIVERY_STATUS) as DeliveryStatus[]).map(s => <option key={s} value={s}>{DELIVERY_STATUS[s].label}</option>)}
        </select>
      </div>
      <div style={card}>
        {deliveries.length === 0 ? <p style={{ padding: 14, fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Nothing sent yet.</p> : deliveries.map(d => (
          <div key={d.id} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", padding: "10px 14px", borderTop: "1px solid var(--hf-border)", fontSize: 12.5 }}>
            <Chip bg={DELIVERY_STATUS[d.status].bg} fg={DELIVERY_STATUS[d.status].fg}>{DELIVERY_STATUS[d.status].label}</Chip>
            <span style={{ fontWeight: 600 }}>{eventLabel(d.eventType)}</span>
            <span style={{ color: "var(--hf-text-muted)" }}>to {d.webhookName} · {stamp(d.createdAt)} · {d.attempts} attempt{d.attempts === 1 ? "" : "s"}</span>
            {d.detail && d.status !== "DELIVERED" && <span style={{ color: "var(--hf-text-muted)" }}>{d.detail}</span>}
            {d.status === "RETRY" && <span style={{ color: "var(--hf-text-muted)" }}>next try {stamp(d.nextAttemptAt)}</span>}
            {canRedeliver(d.status) && <button style={{ ...btnGhost, padding: "4px 10px", marginLeft: "auto" }} onClick={() => redeliver.mutate(d)}>Send again</button>}
          </div>
        ))}
        <ErrorLine error={redeliver.error} />
      </div>
    </div>
  )
}
