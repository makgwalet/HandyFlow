// src/pages/customers/ContactsPanel.tsx
// The people at a customer: add, edit, choose the main contact, remove. The server holds the rules (one main contact, no repeated email).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { EMPTY_CONTACT, contactBody, contactProblem, initials, sortContacts, toForm, type Contact, type ContactForm } from "./contacts.logic"
import { ErrorNote } from "./CrmKit"

const inner = (r: { data: unknown }): Contact[] => { const o = r.data as { data?: unknown } | null; const p = o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data; return Array.isArray(p) ? (p as Contact[]) : [] }
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, width: "100%" }
const btn: React.CSSProperties = { padding: "4px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12, cursor: "pointer" }

export function ContactsPanel({ customerId }: { customerId: string }) {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE"), base = `/api/v1/crm/customers/${customerId}/contacts`
  const [editing, setEditing] = useState<string | "new" | null>(null), [form, setForm] = useState<ContactForm>(EMPTY_CONTACT), [error, setError] = useState<string | null>(null), [confirmId, setConfirmId] = useState<string | null>(null)
  const { data = [], isLoading, isError } = useQuery<Contact[]>({ queryKey: ["crm", "contacts", customerId], queryFn: async () => inner(await apiClient.get(base)) })
  const done = () => { setError(null); setEditing(null); setConfirmId(null); qc.invalidateQueries({ queryKey: ["crm", "contacts", customerId] }) }
  const save = useMutation({ mutationFn: () => (editing === "new" ? apiClient.post(base, contactBody(form)) : apiClient.put(`${base}/${editing}`, contactBody(form))), onSuccess: done, onError: e => setError(errorText(e)) })
  const makeMain = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/primary`), onSuccess: done, onError: e => setError(errorText(e)) })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/${id}`), onSuccess: done, onError: e => setError(errorText(e)) })
  const contacts = sortContacts(data), problem = editing ? contactProblem(form, data, editing === "new" ? null : editing) : null
  const busy = save.isPending || makeMain.isPending || remove.isPending
  const set = (patch: Partial<ContactForm>) => setForm({ ...form, ...patch })

  return (
    <div>
      {isLoading && <p style={{ fontSize: 12 }}>Loading…</p>}
      {isError && <ErrorNote>Couldn't load the contacts.</ErrorNote>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 8 }}>{error}</div>}
      {!isLoading && !isError && contacts.length === 0 && editing !== "new" && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>No contacts yet. Add the people you deal with at this customer. The first one becomes the main contact.</p>}
      {contacts.map(c => editing === c.id
        ? <ContactEditor key={c.id} label={`Edit ${c.fullName}`} form={form} set={set} problem={problem} saving={save.isPending} onSave={() => save.mutate()} onCancel={() => { setEditing(null); setError(null) }} lockMain={c.primary} />
        : (
          <article key={c.id} aria-label={c.fullName} style={{ display: "flex", gap: 10, padding: "8px 0", borderBottom: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
            <div aria-hidden style={{ width: 32, height: 32, borderRadius: "50%", background: "var(--hf-info-soft)", color: "var(--hf-info-text)", display: "grid", placeItems: "center", fontWeight: 700, fontSize: 12, flexShrink: 0 }}>{initials(c)}</div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div><strong>{c.fullName}</strong>{c.primary && <span style={{ marginLeft: 8, fontSize: 11, color: "var(--hf-success-text)", fontWeight: 700 }}>Main contact</span>}</div>
              {c.jobTitle && <div style={{ color: "var(--hf-text-muted)" }}>{c.jobTitle}</div>}
              <div style={{ color: "var(--hf-text-muted)" }}>{[c.email, c.phone].filter(Boolean).join(" · ") || "No contact details"}</div>
              {c.notes && <div style={{ marginTop: 2 }}>{c.notes}</div>}
              {canEdit && (
                <div style={{ display: "flex", gap: 6, marginTop: 6, flexWrap: "wrap" }}>
                  <button type="button" style={btn} aria-label={`Edit ${c.fullName}`} onClick={() => { setEditing(c.id); setForm(toForm(c)); setError(null) }}>Edit</button>
                  {!c.primary && <button type="button" style={btn} disabled={busy} aria-label={`Make ${c.fullName} the main contact`} onClick={() => makeMain.mutate(c.id)}>Make main contact</button>}
                  {confirmId === c.id
                    ? <><button type="button" style={{ ...btn, color: "var(--hf-danger-text)", fontWeight: 700 }} disabled={busy} aria-label={`Confirm remove ${c.fullName}`} onClick={() => remove.mutate(c.id)}>Confirm remove</button><button type="button" style={btn} onClick={() => setConfirmId(null)}>Cancel</button></>
                    : <button type="button" style={btn} aria-label={`Remove ${c.fullName}`} onClick={() => setConfirmId(c.id)}>Remove</button>}
                </div>)}
            </div>
          </article>))}
      {canEdit && editing === "new" && <ContactEditor label="New contact" form={form} set={set} problem={problem} saving={save.isPending} onSave={() => save.mutate()} onCancel={() => { setEditing(null); setError(null) }} lockMain={false} />}
      {canEdit && editing === null && !isLoading && !isError && <button type="button" style={{ ...btn, marginTop: 10, fontWeight: 700 }} onClick={() => { setEditing("new"); setForm({ ...EMPTY_CONTACT, primary: data.length === 0 }); setError(null) }}>Add contact</button>}
    </div>
  )
}

function ContactEditor({ label, form, set, problem, saving, onSave, onCancel, lockMain }: { label: string; form: ContactForm; set: (p: Partial<ContactForm>) => void; problem: string | null; saving: boolean; onSave: () => void; onCancel: () => void; lockMain: boolean }) {
  const touched = form.firstName || form.lastName || form.email || form.phone
  return (
    <form aria-label={label} onSubmit={e => { e.preventDefault(); if (!problem) onSave() }} style={{ display: "grid", gap: 6, padding: 10, margin: "8px 0", border: "1px solid var(--hf-border)", borderRadius: 10, maxWidth: 480 }}>
      <div style={{ display: "flex", gap: 6 }}>
        <input aria-label="First name" placeholder="First name" maxLength={100} style={field} value={form.firstName} onChange={e => set({ firstName: e.target.value })} />
        <input aria-label="Last name" placeholder="Last name" maxLength={100} style={field} value={form.lastName} onChange={e => set({ lastName: e.target.value })} />
      </div>
      <input aria-label="Job title" placeholder="Job title" maxLength={100} style={field} value={form.jobTitle} onChange={e => set({ jobTitle: e.target.value })} />
      <div style={{ display: "flex", gap: 6 }}>
        <input aria-label="Email" placeholder="Email" type="email" maxLength={255} style={field} value={form.email} onChange={e => set({ email: e.target.value })} />
        <input aria-label="Phone" placeholder="Phone" maxLength={50} style={field} value={form.phone} onChange={e => set({ phone: e.target.value })} />
      </div>
      <textarea aria-label="Notes" placeholder="Notes" rows={2} maxLength={2000} style={field} value={form.notes} onChange={e => set({ notes: e.target.value })} />
      <label style={{ fontSize: 12.5 }}><input type="checkbox" aria-label="Main contact" checked={form.primary} disabled={lockMain} onChange={e => set({ primary: e.target.checked })} /> Main contact{lockMain ? " (hand this over by making someone else main)" : ""}</label>
      {problem && touched && <span style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{problem}</span>}
      <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...btn, fontWeight: 700 }} disabled={!!problem || saving}>{saving ? "Saving…" : "Save contact"}</button><button type="button" style={btn} onClick={onCancel}>Cancel</button></div>
    </form>
  )
}
