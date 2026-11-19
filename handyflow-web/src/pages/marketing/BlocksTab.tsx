// src/pages/marketing/BlocksTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X, Layers, Pencil, Trash2 } from 'lucide-react'

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }

export default function BlocksTab() {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<any>(null)     // block, or {} for new
  const [form, setForm] = useState({ name: '', category: '', html: '' })
  const [error, setError] = useState('')
  const [msg, setMsg] = useState('')

  const { data: blocks = [], isLoading } = useQuery<any[]>({
    queryKey: ['marketing-blocks'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/blocks')).data?.data ?? [],
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ['marketing-blocks'] })

  const open = (b?: any) => { setEditing(b ?? {}); setError(''); setForm({ name: b?.name ?? '', category: b?.category ?? '', html: b?.html ?? '' }) }

  const save = useMutation({
    mutationFn: () => {
      const body = { name: form.name.trim(), category: form.category.trim() || null, html: form.html }
      return editing?.id ? apiClient.put(`/api/v1/marketing/blocks/${editing.id}`, body) : apiClient.post('/api/v1/marketing/blocks', body)
    },
    onSuccess: () => { refresh(); setEditing(null) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to save block'),
  })
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/marketing/blocks/${id}`),
    onSuccess: refresh,
    onError: (e: any) => setMsg(e.response?.data?.message || 'Delete failed'),
  })

  return (
    <div>
      <div style={{ padding: '12px 16px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 10, marginBottom: 18, fontSize: 12, color: '#0C4A6E', lineHeight: 1.6 }}>
        Blocks are reusable HTML snippets (headers, buttons, signatures). They are <strong>copied</strong> into a campaign when you insert them,
        so editing or deleting a block never changes a campaign that already uses it.
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <button onClick={() => open()} style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}><Plus size={15} /> New block</button>
      </div>
      {msg && <div style={{ marginBottom: 12, fontSize: 13, color: '#DC2626' }}>{msg}</div>}

      {isLoading ? <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading…</div>
        : blocks.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '50px 20px', color: '#94A3B8' }}>
            <Layers size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
            <div style={{ fontWeight: 700, color: '#475569', marginBottom: 6 }}>No custom blocks yet</div>
            <div style={{ fontSize: 13 }}>The editor already offers a few built-in blocks. Save your own here for your brand’s header, footer and call-to-action.</div>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 14 }}>
            {blocks.map((b: any) => (
              <div key={b.id} style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden', background: '#fff' }}>
                <div style={{ height: 110, background: '#F8FAFC', overflow: 'hidden' }}>
                  <iframe sandbox="" srcDoc={b.html} title={b.name} style={{ width: '200%', height: '200%', border: 'none', transform: 'scale(0.5)', transformOrigin: 'top left', pointerEvents: 'none' }} />
                </div>
                <div style={{ padding: '12px 14px' }}>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{b.name}</div>
                  {b.category && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 2 }}>{b.category}</div>}
                  <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                    <button onClick={() => open(b)} style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 5, padding: 6, background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 12, cursor: 'pointer' }}><Pencil size={12} /> Edit</button>
                    <button onClick={() => { if (window.confirm(`Delete block "${b.name}"? Campaigns that already used it are not affected.`)) del.mutate(b.id) }}
                      style={{ padding: '6px 10px', background: '#fff', border: '1px solid #FECACA', borderRadius: 7, color: '#DC2626', cursor: 'pointer', display: 'flex' }}><Trash2 size={12} /></button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

      {editing && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 600, maxHeight: '90vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>{editing.id ? 'Edit block' : 'New block'}</h3>
              <button onClick={() => setEditing(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 12, marginBottom: 12 }}>
              <div><label style={lbl}>Name *</label><input autoFocus value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} maxLength={100} style={inp} /></div>
              <div><label style={lbl}>Category</label><input value={form.category} onChange={e => setForm(p => ({ ...p, category: e.target.value }))} maxLength={50} placeholder="Footer" style={inp} /></div>
            </div>
            <label style={lbl}>HTML *</label>
            <textarea value={form.html} onChange={e => setForm(p => ({ ...p, html: e.target.value }))} rows={9} style={{ ...inp, fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }} />
            {form.html && <iframe sandbox="" srcDoc={form.html} title="Block preview" style={{ width: '100%', height: 120, border: '1px solid #E2E8F0', borderRadius: 8, marginTop: 10 }} />}
            {error && <div style={{ marginTop: 10, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 18 }}>
              <button onClick={() => setEditing(null)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', cursor: 'pointer' }}>Cancel</button>
              <button disabled={!form.name.trim() || !form.html.trim() || save.isPending} onClick={() => save.mutate()}
                style={{ padding: '9px 22px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontWeight: 700, cursor: 'pointer', opacity: !form.name.trim() || !form.html.trim() ? 0.5 : 1 }}>{save.isPending ? 'Saving…' : 'Save block'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}