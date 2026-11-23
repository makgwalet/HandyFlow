// src/pages/security/ReportRecipientsPanel.tsx
//
// Who gets this site's monthly report by email, and the button that sends it. Managers only (client email addresses are personal data).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Mail, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import {
  MAX_RECIPIENTS, canAdd, canSaveContact, confirmText, errorText, sentText, sendBlock, validContact, validEmail, validPhone,
  type Contact, type Recipient,
} from "./reportEmailLogic"

const muted: React.CSSProperties = { fontSize: 12, color: "var(--hf-text-muted)" }
const input: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0 }
const btn: React.CSSProperties = { padding: "6px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13, display: "inline-flex", alignItems: "center", gap: 6 }

export default function ReportRecipientsPanel({ siteId, month, monthTitle, inProgress, reportLoaded }:
  { siteId: string; month: string; monthTitle: string; inProgress: boolean; reportLoaded: boolean }) {
  const qc = useQueryClient()
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [asking, setAsking] = useState(false)
  const [cName, setCName] = useState("")
  const [cEmail, setCEmail] = useState("")
  const [cPhone, setCPhone] = useState("")
  const [editing, setEditing] = useState(false)
  const [note, setNote] = useState<{ ok: boolean; text: string } | null>(null)

  const { data, isLoading, error } = useQuery<Recipient[]>({
    queryKey: ["report-recipients", siteId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/report-recipients`)).data?.data,
  })
  const list = Array.isArray(data) && data.every(r => r && typeof r.id === "string" && typeof r.email === "string") ? data : null

  const settings = useQuery<Contact | null>({
    queryKey: ["report-settings"],
    queryFn: async () => (await apiClient.get("/api/v1/security/report-settings")).data?.data ?? null,
  })
  const contact = validContact(settings.data) ? settings.data : null
  const saveContact = useMutation({
    mutationFn: async () => { await apiClient.put("/api/v1/security/report-settings", { name: cName.trim() || null, email: cEmail.trim(), phone: cPhone.trim() || null }) },
    onSuccess: () => { setEditing(false); setNote(null); qc.invalidateQueries({ queryKey: ["report-settings"] }) },
    onError: e => setNote({ ok: false, text: errorText(e, "The contact could not be saved.") }),
  })
  const contactOk = canSaveContact(cName, cEmail, cPhone) && cEmail.trim() !== ""
  const showContactForm = editing || (settings.isSuccess && contact === null)

  const add = useMutation({
    mutationFn: async () => { await apiClient.post(`/api/v1/security/sites/${siteId}/report-recipients`, { name: name.trim(), email: email.trim() }) },
    onSuccess: () => { setName(""); setEmail(""); setNote(null); qc.invalidateQueries({ queryKey: ["report-recipients", siteId] }) },
    onError: e => setNote({ ok: false, text: errorText(e, "The recipient could not be added.") }),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => { await apiClient.delete(`/api/v1/security/sites/${siteId}/report-recipients/${id}`) },
    onSuccess: () => { setNote(null); qc.invalidateQueries({ queryKey: ["report-recipients", siteId] }) },
    onError: e => setNote({ ok: false, text: errorText(e, "The recipient could not be removed.") }),
  })
  const send = useMutation({
    mutationFn: async () => (await apiClient.post(`/api/v1/security/sites/${siteId}/monthly-pack/email`, null, { params: { month } })).data?.data as { queued?: number; failed?: number } | undefined,
    onSuccess: r => { setAsking(false); setNote({ ok: true, text: sentText(typeof r?.queued === "number" ? r.queued : list?.length ?? 0, monthTitle, typeof r?.failed === "number" ? r.failed : 0) }) },
    onError: e => { setAsking(false); setNote({ ok: false, text: errorText(e, "The report could not be sent.") }) },
  })

  const blocked = sendBlock({ reportLoaded, inProgress, contactSet: contact !== null, recipients: list?.length ?? 0, busy: send.isPending })
  const addOk = list !== null && canAdd(name, email, list)

  return (
    <div aria-label="Email this report" style={{ marginTop: 14, paddingTop: 12, borderTop: "1px solid var(--hf-border-subtle)" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 600, fontSize: 13, marginBottom: 6 }}><Mail size={14} /> Email to client</div>

      {isLoading && <div style={muted}>Loading recipients…</div>}
      {(error || (data !== undefined && list === null)) && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The recipients could not be loaded.</div>}

      {settings.isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The report contact could not be loaded.</div>}
      {settings.isSuccess && contact && !editing && (
        <div style={{ ...muted, marginBottom: 6, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <span style={{ overflowWrap: "anywhere" }}>Questions go to: {contact.name ? `${contact.name}, ` : ""}{contact.email}{contact.phone ? `, ${contact.phone}` : ""}</span>
          <button type="button" style={btn} onClick={() => { setCName(contact.name ?? ""); setCEmail(contact.email); setCPhone(contact.phone ?? ""); setEditing(true) }}>Change contact</button>
        </div>
      )}
      {showContactForm && (
        <form aria-label="Report contact" style={{ marginBottom: 8 }} onSubmit={e => { e.preventDefault(); if (contactOk && !saveContact.isPending) saveContact.mutate() }}>
          <div style={{ ...muted, marginBottom: 4 }}>Every emailed report ends with a contact for questions. Use an address your team monitors; replies to the report itself are not read.</div>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            <input aria-label="Contact name" placeholder="Name or team (optional)" maxLength={100} value={cName} onChange={e => setCName(e.target.value)} style={{ ...input, flex: "1 1 120px" }} />
            <input aria-label="Contact email" placeholder="support@example.com" type="email" maxLength={254} value={cEmail} onChange={e => setCEmail(e.target.value)} style={{ ...input, flex: "2 1 180px" }} />
            <input aria-label="Contact phone" placeholder="Phone (optional)" maxLength={40} value={cPhone} onChange={e => setCPhone(e.target.value)} style={{ ...input, flex: "1 1 120px" }} />
            <button type="submit" style={{ ...btn, opacity: contactOk ? 1 : 0.5 }} disabled={!contactOk || saveContact.isPending}>Save contact</button>
            {editing && <button type="button" style={btn} onClick={() => setEditing(false)}>Cancel</button>}
          </div>
          {cEmail.trim() !== "" && !validEmail(cEmail) && <div style={{ ...muted, marginTop: 4 }}>Enter one email address.</div>}
          {!validPhone(cPhone) && <div style={{ ...muted, marginTop: 4 }}>Phone may contain digits, spaces, + ( ) and -.</div>}
        </form>
      )}

      {list && (
        <>
          {list.length === 0 && <div style={muted}>No recipients yet. Counts only are sent; no guard names or incident text.</div>}
          <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {list.map(r => (
              <li key={r.id} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, padding: "3px 0" }}>
                <span style={{ flex: 1, minWidth: 0, overflowWrap: "anywhere" }}>{r.name} · {r.email}</span>
                <button type="button" aria-label={`Remove ${r.name}`} style={btn} disabled={remove.isPending} onClick={() => remove.mutate(r.id)}><Trash2 size={13} /></button>
              </li>
            ))}
          </ul>

          <form style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8 }} onSubmit={e => { e.preventDefault(); if (addOk && !add.isPending) add.mutate() }}>
            <input aria-label="Recipient name" placeholder="Name" maxLength={100} value={name} onChange={e => setName(e.target.value)} style={{ ...input, flex: "1 1 120px" }} />
            <input aria-label="Recipient email" placeholder="email@example.com" type="email" maxLength={254} value={email} onChange={e => setEmail(e.target.value)} style={{ ...input, flex: "2 1 180px" }} />
            <button type="submit" style={{ ...btn, opacity: addOk ? 1 : 0.5 }} disabled={!addOk || add.isPending}>Add</button>
          </form>
          {email.trim() !== "" && !validEmail(email) && <div style={{ ...muted, marginTop: 4 }}>Enter one email address.</div>}
          {list.length >= MAX_RECIPIENTS && <div style={{ ...muted, marginTop: 4 }}>A site can have at most {MAX_RECIPIENTS} recipients.</div>}

          <div style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            {!asking && <button type="button" style={{ ...btn, opacity: blocked ? 0.5 : 1 }} disabled={blocked !== null} onClick={() => { setNote(null); setAsking(true) }}>Email this report</button>}
            {asking && (
              <>
                <span style={{ fontSize: 13 }}>{confirmText(monthTitle, list.length)}</span>
                <button type="button" style={btn} disabled={send.isPending} onClick={() => send.mutate()}>Send</button>
                <button type="button" style={btn} disabled={send.isPending} onClick={() => setAsking(false)}>Cancel</button>
              </>
            )}
            {blocked && !asking && blocked !== "Sending…" && <span style={muted}>{blocked}</span>}
          </div>
        </>
      )}

      {note && <div role={note.ok ? "status" : "alert"} style={{ marginTop: 8, fontSize: 13, color: note.ok ? "var(--hf-text)" : "var(--hf-danger-text)" }}>{note.text}</div>}
    </div>
  )
}
