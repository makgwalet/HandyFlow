// Clients: the companies you hire for. A list you can search, and for the one you open: its details, the people to talk to, and its jobs.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermission } from '../../hooks/usePermission'

type Row = { id: string; name: string; industry: string | null; location: string | null; status: string; primaryContact: string | null; openJobs: number; totalJobs: number; placements: number }
type Contact = { id: string; name: string; email: string | null; phone: string | null; roleTitle: string | null; primary: boolean }
type ClientJob = { id: string; title: string; status: string; applications: number; hires: number }
type Detail = { id: string; name: string; industry: string | null; location: string | null; status: string; notes: string | null; contacts: Contact[]; jobs: ClientJob[] }
type JobLite = { id: string; title: string; status?: string }
const BASE = '/api/v1/recruiter'
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const card = { background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 10, padding: 14 } as const
const field = { padding: '6px 9px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const
const btn = { ...field, cursor: 'pointer' } as const
const muted = { color: 'var(--hf-text-muted)' } as const
const message = (e: unknown): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message || 'That did not work. Try again.'
const th = { textAlign: 'left', padding: '6px 8px', borderBottom: '1px solid var(--hf-border)', fontWeight: 600 } as const
const td = { padding: '6px 8px', borderBottom: '1px solid var(--hf-border-subtle)' } as const

function ClientForm({ initial, onSave, onCancel, saving, error }: { initial?: Detail; onSave: (b: object) => void; onCancel: () => void; saving: boolean; error: string }) {
  const [name, setName] = useState(initial?.name ?? '')
  const [industry, setIndustry] = useState(initial?.industry ?? '')
  const [location, setLocation] = useState(initial?.location ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [status, setStatus] = useState(initial?.status ?? 'ACTIVE')
  return (
    <div style={{ ...card, display: 'grid', gap: 8 }}>
      <strong>{initial ? 'Edit client' : 'New client'}</strong>
      <label>Name <input aria-label="Client name" value={name} onChange={e => setName(e.target.value)} style={{ ...field, width: '100%' }} /></label>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <label>Industry <input value={industry} onChange={e => setIndustry(e.target.value)} style={field} /></label>
        <label>Location <input value={location} onChange={e => setLocation(e.target.value)} style={field} /></label>
        {initial && <label>Status <select value={status} onChange={e => setStatus(e.target.value)} style={field}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></select></label>}
      </div>
      <label>Notes <textarea value={notes} onChange={e => setNotes(e.target.value)} rows={3} style={{ ...field, width: '100%' }} /></label>
      {error && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{error}</div>}
      <div style={{ display: 'flex', gap: 8 }}>
        <button style={btn} disabled={saving} onClick={() => onSave({ name, industry, location, notes, status: initial ? status : undefined })}>{saving ? 'Saving...' : 'Save'}</button>
        <button style={btn} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}

function ClientDetailView({ id, onClose, onChanged }: { id: string; onClose: () => void; onChanged: () => void }) {
  const qc = useQueryClient()
  const mayManage = usePermission('RECRUITER_MANAGE')
  const mayAdmin = usePermission('RECRUITER_ADMIN')
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState('')
  const [c, setC] = useState({ name: '', email: '', phone: '', roleTitle: '' })
  const [jobToAdd, setJobToAdd] = useState('')
  const d = useQuery<Detail>({ queryKey: ['rec-client', id], queryFn: async () => data(await apiClient.get(`${BASE}/clients/${id}`)), retry: false })
  const jobs = useQuery<JobLite[]>({
    queryKey: ['rec-jobs-lite'],
    queryFn: async () => { const x = data<unknown>(await apiClient.get(`${BASE}/jobs?size=200`)) as JobLite[] | { content?: JobLite[] } | undefined; return Array.isArray(x) ? x : x?.content ?? [] },
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['rec-client', id] }); qc.invalidateQueries({ queryKey: ['rec-clients'] }); qc.invalidateQueries({ queryKey: ['rec-job-clients'] }); onChanged() }
  const done = () => { setError(''); refresh() }
  const save = useMutation({ mutationFn: (b: object) => apiClient.put(`${BASE}/clients/${id}`, b), onSuccess: () => { setEditing(false); done() }, onError: e => setError(message(e)) })
  const addContact = useMutation({ mutationFn: () => apiClient.post(`${BASE}/clients/${id}/contacts`, c), onSuccess: () => { setC({ name: '', email: '', phone: '', roleTitle: '' }); done() }, onError: e => setError(message(e)) })
  const removeContact = useMutation({ mutationFn: (cid: string) => apiClient.delete(`${BASE}/clients/${id}/contacts/${cid}`), onSuccess: done, onError: e => setError(message(e)) })
  const setJob = useMutation({ mutationFn: (v: { jobId: string; clientId: string | null }) => apiClient.put(`${BASE}/jobs/${v.jobId}/client`, { clientId: v.clientId }), onSuccess: () => { setJobToAdd(''); done() }, onError: e => setError(message(e)) })
  const remove = useMutation({ mutationFn: () => apiClient.delete(`${BASE}/clients/${id}`), onSuccess: () => { refresh(); onClose() }, onError: e => setError(message(e)) })

  if (d.isLoading) return <div style={muted}>Loading...</div>
  if (d.isError || !d.data) return <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{message(d.error)}</div>
  const x = d.data
  const free = (jobs.data ?? []).filter(j => !x.jobs.some(cj => cj.id === j.id))
  if (editing) return <ClientForm initial={x} saving={save.isPending} error={error} onCancel={() => setEditing(false)} onSave={b => save.mutate(b)} />

  return (
    <div style={{ ...card, display: 'grid', gap: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
        <div>
          <div style={{ fontSize: 16, fontWeight: 700 }}>{x.name} <span style={{ ...muted, fontSize: 12, fontWeight: 500 }}>{x.status === 'ACTIVE' ? 'Active' : 'Inactive'}</span></div>
          <div style={muted}>{[x.industry, x.location].filter(Boolean).join(', ') || 'No industry or location yet'}</div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'start' }}>
          {mayManage && <button style={btn} onClick={() => setEditing(true)}>Edit</button>}
          {mayAdmin && <button style={{ ...btn, color: 'var(--hf-danger-text)' }} disabled={remove.isPending}
            onClick={() => { if (window.confirm(`Remove ${x.name}? Its jobs stay, just no longer tied to a client.`)) remove.mutate() }}>Remove</button>}
          <button style={btn} onClick={onClose}>Close</button>
        </div>
      </div>
      {x.notes && <div style={{ whiteSpace: 'pre-wrap' }}>{x.notes}</div>}
      {error && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{error}</div>}

      <div>
        <strong>Contacts</strong>
        {x.contacts.length === 0 && <div style={{ ...muted, marginTop: 4 }}>No contacts yet.</div>}
        {x.contacts.map(k => (
          <div key={k.id} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', padding: '5px 0', borderBottom: '1px solid var(--hf-border-subtle)', alignItems: 'center' }}>
            <span style={{ minWidth: 150 }}><strong>{k.name}</strong>{k.primary && <span style={muted}> (main)</span>}<br /><span style={muted}>{k.roleTitle ?? ''}</span></span>
            <span style={{ flex: 1 }}>{[k.email, k.phone].filter(Boolean).join('  ·  ')}</span>
            {mayManage && <button style={btn} disabled={removeContact.isPending} onClick={() => removeContact.mutate(k.id)}>Remove</button>}
          </div>
        ))}
        {mayManage && (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
            <input aria-label="Contact name" placeholder="Name" value={c.name} onChange={e => setC({ ...c, name: e.target.value })} style={field} />
            <input aria-label="Contact email" placeholder="Email" value={c.email} onChange={e => setC({ ...c, email: e.target.value })} style={field} />
            <input aria-label="Contact phone" placeholder="Phone" value={c.phone} onChange={e => setC({ ...c, phone: e.target.value })} style={field} />
            <input aria-label="Contact job title" placeholder="Job title" value={c.roleTitle} onChange={e => setC({ ...c, roleTitle: e.target.value })} style={field} />
            <button style={btn} disabled={addContact.isPending} onClick={() => addContact.mutate()}>Add contact</button>
          </div>
        )}
      </div>

      <div>
        <strong>Jobs for this client</strong>
        {x.jobs.length === 0 && <div style={{ ...muted, marginTop: 4 }}>No jobs yet.</div>}
        {x.jobs.length > 0 && (
          <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 4 }}>
            <thead><tr><th style={th}>Job</th><th style={th}>Status</th><th style={{ ...th, textAlign: 'right' }}>Applications</th><th style={{ ...th, textAlign: 'right' }}>Hires</th><th style={th} /></tr></thead>
            <tbody>{x.jobs.map(j => (
              <tr key={j.id}>
                <td style={td}>{j.title}</td><td style={td}>{j.status.charAt(0) + j.status.slice(1).toLowerCase()}</td>
                <td style={{ ...td, textAlign: 'right' }}>{j.applications}</td><td style={{ ...td, textAlign: 'right' }}>{j.hires}</td>
                <td style={td}>{mayManage && <button style={btn} disabled={setJob.isPending} onClick={() => setJob.mutate({ jobId: j.id, clientId: null })}>Untie</button>}</td>
              </tr>))}</tbody>
          </table>
        )}
        {mayManage && (
          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            <select aria-label="Job to tie to this client" value={jobToAdd} onChange={e => setJobToAdd(e.target.value)} style={field}>
              <option value="">Choose a job...</option>
              {free.map(j => <option key={j.id} value={j.id}>{j.title}</option>)}
            </select>
            <button style={btn} disabled={!jobToAdd || setJob.isPending} onClick={() => setJob.mutate({ jobId: jobToAdd, clientId: id })}>Tie to this client</button>
          </div>
        )}
      </div>
    </div>
  )
}

export default function RecClients() {
  const qc = useQueryClient()
  const mayManage = usePermission('RECRUITER_MANAGE')
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState('')
  const list = useQuery<Row[]>({
    queryKey: ['rec-clients', q, status],
    queryFn: async () => { const p = new URLSearchParams(); if (q.trim()) p.set('q', q.trim()); if (status) p.set('status', status); return data(await apiClient.get(`${BASE}/clients?${p}`)) },
    retry: false,
  })
  const create = useMutation({
    mutationFn: async (b: object) => data<Detail>(await apiClient.post(`${BASE}/clients`, b)),
    onSuccess: d => { setAdding(false); setError(''); qc.invalidateQueries({ queryKey: ['rec-clients'] }); setOpen(d.id) },
    onError: e => setError(message(e)),
  })
  const rows = list.data ?? []

  return (
    <div style={{ fontSize: 12, display: 'grid', gap: 12 }}>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <input aria-label="Search clients" placeholder="Search clients..." value={q} onChange={e => setQ(e.target.value)} style={{ ...field, width: 220 }} />
        <select aria-label="Status" value={status} onChange={e => setStatus(e.target.value)} style={field}>
          <option value="">All statuses</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
        </select>
        <span style={muted}>{rows.length} client{rows.length === 1 ? '' : 's'}</span>
        {mayManage && <button style={{ ...btn, marginLeft: 'auto' }} onClick={() => { setAdding(true); setOpen(null) }}>Add client</button>}
      </div>
      {list.isError && <div role="alert" style={{ color: 'var(--hf-danger-text)' }}>{message(list.error)}</div>}
      {adding && <ClientForm saving={create.isPending} error={error} onCancel={() => { setAdding(false); setError('') }} onSave={b => create.mutate(b)} />}
      {open && <ClientDetailView id={open} onClose={() => setOpen(null)} onChanged={() => qc.invalidateQueries({ queryKey: ['rec-clients'] })} />}
      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr>{['Company', 'Industry', 'Location', 'Main contact', 'Open jobs', 'Placements', 'Status'].map((h, i) => <th key={h} style={{ ...th, textAlign: i === 4 || i === 5 ? 'right' : 'left' }}>{h}</th>)}</tr></thead>
          <tbody>
            {list.data && rows.length === 0 && <tr><td colSpan={7} style={{ ...td, ...muted }}>{q || status ? 'No client matches.' : 'No clients yet.'}</td></tr>}
            {rows.map(r => (
              <tr key={r.id} onClick={() => { setOpen(r.id); setAdding(false) }} style={{ cursor: 'pointer', background: open === r.id ? 'var(--hf-info-soft)' : undefined }}>
                <td style={td}><button style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', fontWeight: 600, color: 'var(--hf-primary-text)', fontSize: 12 }}
                  onClick={e => { e.stopPropagation(); setOpen(r.id); setAdding(false) }}>{r.name}</button></td>
                <td style={td}>{r.industry ?? ''}</td><td style={td}>{r.location ?? ''}</td><td style={td}>{r.primaryContact ?? ''}</td>
                <td style={{ ...td, textAlign: 'right' }}>{r.openJobs}</td><td style={{ ...td, textAlign: 'right' }}>{r.placements}</td>
                <td style={{ ...td, color: r.status === 'ACTIVE' ? 'var(--hf-success-text)' : 'var(--hf-text-muted)' }}>{r.status === 'ACTIVE' ? 'Active' : 'Inactive'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
