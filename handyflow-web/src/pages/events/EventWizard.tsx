// src/pages/events/EventWizard.tsx
//
// Create-event wizard: Details, then (for people allowed to) Tickets, Vendors and Budget, then Review. Nothing is saved until Review. The event is created first
// as a draft, then the extras one by one; if one is refused the event already exists, so the screen says exactly what was and was not saved and offers to
// retry the rest or open the event. Each step's rules match the server's, and the server's own message is always shown.
import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, errText, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { CATEGORIES, categoryText } from "./budgetOps"
import { EVENT_TYPES } from "./EventDialogs"
import { rand } from "./paymentOps"
import {
  budgetTotal, buildPlan, capacityNote, createBody, emptyDraft, emptyLine, emptyTier, emptyVendor, firstBlockedStep, runPlan, STEP_LABEL, stepProblem, tierPlaces, VENDOR_TYPES, visibleSteps,
  type Draft, type LineDraft, type StepId, type TierDraft, type VendorDraft,
} from "./wizardOps"

const field: React.CSSProperties = { ...inputStyle, width: "100%" }
const label = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")
const row: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10, marginBottom: 8, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }

export default function EventWizard({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string, title: string) => void }) {
  const qc = useQueryClient()
  const perms = {
    tickets: usePermissions(["EVENTS_TICKETING_MANAGE", "EVENTS_MANAGE"]),
    vendors: usePermissions(["EVENTS_SUPPLIER_MANAGE", "EVENTS_MANAGE"]),
    budget: usePermissions(["EVENTS_BUDGET_MANAGE", "EVENTS_MANAGE"]),
  }
  const steps = visibleSteps(perms)
  const [i, setI] = useState(0)
  const [d, setD] = useState<Draft>(emptyDraft())
  const [saved, setSaved] = useState<{ id: string; title: string; done: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<unknown>(null)
  const step: StepId = steps[i]
  const plan = buildPlan(d, perms)
  const problem = step === "review" ? null : stepProblem(step, d)
  const blocked = firstBlockedStep(steps, d)
  const ev = <K extends keyof Draft["event"]>(k: K, v: Draft["event"][K]) => setD(p => ({ ...p, event: { ...p.event, [k]: v } }))
  const upd = <T,>(key: "tiers" | "vendors" | "lines", idx: number, k: keyof T, v: string) => setD(p => ({ ...p, [key]: (p[key] as any[]).map((x, n) => (n === idx ? { ...x, [k]: v } : x)) }) as Draft)
  const add = (key: "tiers" | "vendors" | "lines", item: any) => setD(p => ({ ...p, [key]: [...p[key], item] }) as Draft)
  const drop = (key: "tiers" | "vendors" | "lines", idx: number) => setD(p => ({ ...p, [key]: (p[key] as any[]).filter((_, n) => n !== idx) }) as Draft)

  async function create() {
    setBusy(true); setErr(null)
    try {
      let cur = saved
      if (!cur) {
        const r = await apiClient.post("/api/v1/events", createBody(d)); const e = r.data?.data ?? r.data
        cur = { id: String(e.id), title: d.event.title.trim(), done: 0 }; setSaved(cur)
        qc.invalidateQueries({ queryKey: ["events"] }); qc.invalidateQueries({ queryKey: ["events-kpi"] })
      }
      const res = await runPlan(plan, cur.id, cur.done, (path, body) => apiClient.post(path, body))
      setSaved({ ...cur, done: res.done })
      ;["event-tiers", "event-vendors", "event-budget-lines", "event-budget"].forEach(k => qc.invalidateQueries({ queryKey: [k] }))
      if (res.error) setErr(res.error); else onCreated(cur.id, cur.title)
    } catch (e) { setErr(e) } finally { setBusy(false) }
  }

  const L = (id: string, t: string) => <label style={labelStyle} htmlFor={id}>{t}</label>
  return (
    <Modal title="New event" onClose={onClose} width={680}>
      <ol aria-label="Steps" style={{ display: "flex", gap: 6, listStyle: "none", padding: 0, margin: "0 0 14px", flexWrap: "wrap" }}>
        {steps.map((s, n) => <li key={s} aria-current={n === i ? "step" : undefined} style={{ fontSize: 12, fontWeight: n === i ? 800 : 500, padding: "3px 10px", borderRadius: 20, background: n === i ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: n === i ? "#fff" : "var(--hf-text-muted)" }}>{n + 1}. {STEP_LABEL[s]}</li>)}
      </ol>

      {step === "details" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div style={{ gridColumn: "1/-1" }}>{L("w-title", "Event title *")}<input id="w-title" style={field} maxLength={300} value={d.event.title} onChange={e => ev("title", e.target.value)} /></div>
          <div>{L("w-type", "Event type")}<select id="w-type" style={field} value={d.event.eventType} onChange={e => ev("eventType", e.target.value)}>{EVENT_TYPES.map(t => <option key={t} value={t}>{label(t)}</option>)}</select></div>
          <div>{L("w-cap", "Venue capacity (empty = no limit)")}<input id="w-cap" style={field} inputMode="numeric" value={d.event.venueCapacity} onChange={e => ev("venueCapacity", e.target.value)} /></div>
          <div>{L("w-venue", "Venue name")}<input id="w-venue" style={field} maxLength={300} value={d.event.venueName} onChange={e => ev("venueName", e.target.value)} /></div>
          <div>{L("w-addr", "Venue address")}<input id="w-addr" style={field} maxLength={2000} value={d.event.venueAddress} onChange={e => ev("venueAddress", e.target.value)} /></div>
          <div>{L("w-start", "Starts *")}<input id="w-start" type="datetime-local" style={field} value={d.event.startDatetime} onChange={e => ev("startDatetime", e.target.value)} /></div>
          <div>{L("w-end", "Ends *")}<input id="w-end" type="datetime-local" style={field} value={d.event.endDatetime} onChange={e => ev("endDatetime", e.target.value)} /></div>
          <div>{L("w-dead", "Registration closes")}<input id="w-dead" type="datetime-local" style={field} value={d.event.registrationDeadline} onChange={e => ev("registrationDeadline", e.target.value)} /></div>
          <div style={{ display: "flex", gap: 16, alignItems: "center", paddingTop: 20, fontSize: 13 }}>
            <label><input type="checkbox" checked={d.event.isFree} onChange={e => ev("isFree", e.target.checked)} /> Free event</label>
            <label><input type="checkbox" checked={d.event.isPrivate} onChange={e => ev("isPrivate", e.target.checked)} /> Private</label>
          </div>
          <div style={{ gridColumn: "1/-1" }}>{L("w-desc", "Description")}<textarea id="w-desc" style={{ ...field, minHeight: 56 }} value={d.event.description} onChange={e => ev("description", e.target.value)} /></div>
        </div>
      )}

      {step === "tickets" && (<div>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 0 }}>Optional. Add the ticket types now or later from the Ticket types screen.{capacityNote(d) && ` ${capacityNote(d)}`}</p>
        {d.tiers.map((t, n) => (
          <div key={n} style={row} aria-label={`Ticket type ${n + 1}`} role="group">
            <div>{L(`t-name-${n}`, "Name *")}<input id={`t-name-${n}`} style={field} value={t.name} onChange={e => upd<TierDraft>("tiers", n, "name", e.target.value)} /></div>
            <div>{L(`t-price-${n}`, "Price (R, 0 = free)")}<input id={`t-price-${n}`} style={field} inputMode="decimal" value={t.price} onChange={e => upd<TierDraft>("tiers", n, "price", e.target.value)} /></div>
            <div>{L(`t-qty-${n}`, "Places *")}<input id={`t-qty-${n}`} style={field} inputMode="numeric" value={t.quantity} onChange={e => upd<TierDraft>("tiers", n, "quantity", e.target.value)} /></div>
            <div>{L(`t-desc-${n}`, "Description")}<input id={`t-desc-${n}`} style={field} value={t.description} onChange={e => upd<TierDraft>("tiers", n, "description", e.target.value)} /></div>
            <div>{L(`t-from-${n}`, "On sale from")}<input id={`t-from-${n}`} type="datetime-local" style={field} value={t.saleStart} onChange={e => upd<TierDraft>("tiers", n, "saleStart", e.target.value)} /></div>
            <div>{L(`t-to-${n}`, "On sale until")}<input id={`t-to-${n}`} type="datetime-local" style={field} value={t.saleEnd} onChange={e => upd<TierDraft>("tiers", n, "saleEnd", e.target.value)} /></div>
            <button type="button" style={btnGhost} onClick={() => drop("tiers", n)}>Remove ticket type {n + 1}</button>
          </div>))}
        <button type="button" style={btnGhost} onClick={() => add("tiers", emptyTier())}>Add ticket type</button>
      </div>)}

      {step === "vendors" && (<div>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 0 }}>Optional. Record who you plan to use; confirm them later from the Vendors screen.</p>
        {d.vendors.map((v, n) => (
          <div key={n} style={row} aria-label={`Vendor ${n + 1}`} role="group">
            <div>{L(`v-co-${n}`, "Company *")}<input id={`v-co-${n}`} style={field} value={v.companyName} onChange={e => upd<VendorDraft>("vendors", n, "companyName", e.target.value)} /></div>
            <div>{L(`v-type-${n}`, "Supplies")}<select id={`v-type-${n}`} style={field} value={v.vendorType} onChange={e => upd<VendorDraft>("vendors", n, "vendorType", e.target.value)}>{VENDOR_TYPES.map(t => <option key={t} value={t}>{label(t)}</option>)}</select></div>
            <div>{L(`v-ct-${n}`, "Contact")}<input id={`v-ct-${n}`} style={field} value={v.contactName} onChange={e => upd<VendorDraft>("vendors", n, "contactName", e.target.value)} /></div>
            <div>{L(`v-ph-${n}`, "Phone")}<input id={`v-ph-${n}`} style={field} value={v.contactPhone} onChange={e => upd<VendorDraft>("vendors", n, "contactPhone", e.target.value)} /></div>
            <div>{L(`v-em-${n}`, "Email")}<input id={`v-em-${n}`} style={field} value={v.contactEmail} onChange={e => upd<VendorDraft>("vendors", n, "contactEmail", e.target.value)} /></div>
            <div>{L(`v-q-${n}`, "Quoted amount (R)")}<input id={`v-q-${n}`} style={field} inputMode="decimal" value={v.quotedAmount} onChange={e => upd<VendorDraft>("vendors", n, "quotedAmount", e.target.value)} /></div>
            <button type="button" style={btnGhost} onClick={() => drop("vendors", n)}>Remove vendor {n + 1}</button>
          </div>))}
        <button type="button" style={btnGhost} onClick={() => add("vendors", emptyVendor())}>Add vendor</button>
      </div>)}

      {step === "budget" && (<div>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 0 }}>Optional. Plan the spend by line; the budget is submitted for approval later from Budget &amp; costs. Total so far: {rand(budgetTotal(d))}</p>
        {d.lines.map((l, n) => (
          <div key={n} style={row} aria-label={`Budget line ${n + 1}`} role="group">
            <div>{L(`b-cat-${n}`, "Category")}<select id={`b-cat-${n}`} style={field} value={l.category} onChange={e => upd<LineDraft>("lines", n, "category", e.target.value)}>{CATEGORIES.map(c => <option key={c} value={c}>{categoryText(c)}</option>)}</select></div>
            <div>{L(`b-amt-${n}`, "Planned amount (R) *")}<input id={`b-amt-${n}`} style={field} inputMode="decimal" value={l.plannedAmount} onChange={e => upd<LineDraft>("lines", n, "plannedAmount", e.target.value)} /></div>
            <div style={{ gridColumn: "1/-1" }}>{L(`b-desc-${n}`, "What is it for? *")}<input id={`b-desc-${n}`} style={field} value={l.description} onChange={e => upd<LineDraft>("lines", n, "description", e.target.value)} /></div>
            <button type="button" style={btnGhost} onClick={() => drop("lines", n)}>Remove budget line {n + 1}</button>
          </div>))}
        <button type="button" style={btnGhost} onClick={() => add("lines", emptyLine())}>Add budget line</button>
      </div>)}

      {step === "review" && (
        <div style={{ fontSize: 13 }}>
          <h4 style={{ margin: "0 0 6px" }}>{d.event.title || "Untitled event"}</h4>
          <div>{label(d.event.eventType)} · {d.event.isFree ? "Free" : "Paid"}{d.event.isPrivate ? " · Private" : ""}{d.event.venueName ? ` · ${d.event.venueName}` : ""}</div>
          <div>{d.event.startDatetime.replace("T", " ")} to {d.event.endDatetime.replace("T", " ")}{d.event.venueCapacity ? ` · capacity ${d.event.venueCapacity}` : ""}</div>
          {perms.tickets && <div style={{ marginTop: 6 }}><strong>{d.tiers.length}</strong> ticket type(s), {tierPlaces(d)} places</div>}
          {perms.vendors && <div><strong>{d.vendors.length}</strong> vendor(s)</div>}
          {perms.budget && <div><strong>{d.lines.length}</strong> budget line(s), {rand(budgetTotal(d))} planned</div>}
          {capacityNote(d) && <p role="note" style={{ color: "var(--hf-warning-text)" }}>{capacityNote(d)}</p>}
          <p style={{ color: "var(--hf-text-muted)" }}>The event is created as a draft. Nothing is published or visible to guests until you publish it.</p>
          {blocked && <p role="alert" style={{ color: "var(--hf-danger-text)" }}>{STEP_LABEL[blocked]} needs attention: {stepProblem(blocked, d)} <button type="button" style={btnGhost} onClick={() => setI(steps.indexOf(blocked))}>Go to {STEP_LABEL[blocked]}</button></p>}
          {saved && <p role="status" style={{ color: "var(--hf-success-text)" }}>The event "{saved.title}" has been created. {saved.done} of {plan.length} extra item(s) saved.</p>}
          {err != null && <p role="alert" style={{ color: "var(--hf-danger-text)", marginBottom: 4 }}>{saved && saved.done < plan.length ? `${plan[saved.done].label} was not saved: ` : ""}{errText(err, "The event could not be created")}</p>}
          {saved && err != null && <button type="button" style={btnGhost} onClick={() => onCreated(saved.id, saved.title)}>Open the event anyway</button>}
        </div>
      )}
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={() => (i === 0 || saved ? onClose() : setI(i - 1))} disabled={busy}>{i === 0 || saved ? "Close" : "Back"}</button>
        {step !== "review"
          ? <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem} onClick={() => setI(i + 1)}>Next</button>
          : <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: blocked ? 0.5 : 1 }} disabled={!!blocked || busy} onClick={create}>{busy ? "Working…" : saved && err != null ? "Retry the rest" : "Create event"}</button>}
      </div>
    </Modal>
  )
}
