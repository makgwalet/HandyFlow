// Past candidates: find them again, tag them, keep them in pools and put them forward for a new vacancy. The server only allows this while the
// candidate's agreement and the company's retention period still stand, and says so when it does not.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

type Pool = { id: string; name: string; description: string | null; members: number }
type Candidate = { applicantId: string; name: string; email: string; phone: string | null; location: string | null; tags: string[]; applications: number; lastActivity: string | null; note: string | null }
type Job = { id: string; title: string }
const BASE = '/api/v1/recruiter'
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const message = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
const field = { padding: '6px 9px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12 } as const
const btn = { ...field, background: 'var(--hf-surface-muted)', cursor: 'pointer' } as const

export default function RecPool() {
  const qc = useQueryClient()
  const [q, setQ] = useState('')
  const [tag, setTag] = useState('')
  const [poolId, setPoolId] = useState('')
  const [newPool, setNewPool] = useState('')
  const [tagEdit, setTagEdit] = useState<{ id: string; text: string } | null>(null)
  const [notice, setNotice] = useState<{ text: string; bad: boolean } | null>(null)
  const fail = (e: unknown, fb: string) => setNotice({ text: message(e, fb), bad: true })
  const ok = (text: string) => setNotice({ text, bad: false })

  const search = q.trim().length >= 2 ? q.trim() : ''
  const pools = useQuery<Pool[]>({ queryKey: ['rec-pools'], queryFn: async () => data(await apiClient.get(`${BASE}/pools`)) })
  const jobs = useQuery<Job[]>({
    queryKey: ['rec-pool-open-jobs'],
    queryFn: async () => { const p = data<{ content?: Job[] }>(await apiClient.get(`${BASE}/jobs?status=OPEN&size=100`)); return p.content ?? [] },
  })
  const cands = useQuery<Candidate[]>({
    queryKey: ['rec-candidates', search, tag, poolId],
    queryFn: async () => {
      const p = new URLSearchParams()
      if (search) p.set('q', search)
      if (tag.trim()) p.set('tag', tag.trim())
      if (poolId) p.set('poolId', poolId)
      return data(await apiClient.get(`${BASE}/candidates?${p}`))
    },
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['rec-candidates'] }); qc.invalidateQueries({ queryKey: ['rec-pools'] }); qc.invalidateQueries({ queryKey: ['rec-applications'] }) }

  const createPool = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/pools`, { name: newPool, description: null }),
    onSuccess: () => { setNewPool(''); refresh(); ok('Pool created.') }, onError: e => fail(e, 'The pool could not be created.'),
  })
  const deletePool = useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/pools/${id}`),
    onSuccess: () => { setPoolId(''); refresh() }, onError: e => fail(e, 'The pool could not be deleted.'),
  })
  const saveTags = useMutation({
    mutationFn: (v: { id: string; tags: string[] }) => apiClient.put(`${BASE}/candidates/${v.id}/tags`, { tags: v.tags }),
    onSuccess: () => { setTagEdit(null); refresh() }, onError: e => fail(e, 'The tags could not be saved.'),
  })
  const addToPool = useMutation({
    mutationFn: (v: { pool: string; id: string }) => apiClient.post(`${BASE}/pools/${v.pool}/members`, { applicantId: v.id, note: null }),
    onSuccess: () => { refresh(); ok('Added to the pool.') }, onError: e => fail(e, 'They could not be added.'),
  })
  const removeFromPool = useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/pools/${poolId}/members/${id}`),
    onSuccess: refresh, onError: e => fail(e, 'They could not be removed.'),
  })
  const putForward = useMutation({
    mutationFn: (v: { id: string; jobId: string }) => apiClient.post(`${BASE}/candidates/${v.id}/apply`, { jobId: v.jobId }),
    onSuccess: () => { refresh(); ok('Put forward. They are now in that job\'s applications.') }, onError: e => fail(e, 'They could not be put forward.'),
  })

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(180px, 220px) 1fr', gap: 16, fontSize: 12 }}>
      <aside>
        <strong>Pools</strong>
        <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
          <button style={{ ...btn, textAlign: 'left', fontWeight: poolId === '' ? 700 : 400 }} onClick={() => setPoolId('')}>All candidates</button>
          {(pools.data ?? []).map(p => (
            <button key={p.id} style={{ ...btn, textAlign: 'left', fontWeight: poolId === p.id ? 700 : 400 }} onClick={() => setPoolId(p.id)}>
              {p.name} ({p.members})
            </button>
          ))}
        </div>
        <div style={{ marginTop: 10, display: 'flex', gap: 4 }}>
          <input aria-label="New pool name" placeholder="New pool" maxLength={80} value={newPool} onChange={e => setNewPool(e.target.value)} style={{ ...field, width: '100%' }} />
          <button style={btn} disabled={newPool.trim().length < 2 || createPool.isPending} onClick={() => createPool.mutate()}>Add</button>
        </div>
        {poolId && <button style={{ ...btn, marginTop: 8 }} onClick={() => deletePool.mutate(poolId)}>Delete this pool</button>}
      </aside>
      <section>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 10 }}>
          <input aria-label="Search candidates" placeholder="Name, email or location" value={q} onChange={e => setQ(e.target.value)} style={{ ...field, width: 220 }} />
          <input aria-label="Tag" placeholder="Tag, e.g. code 14" value={tag} onChange={e => setTag(e.target.value)} style={{ ...field, width: 150 }} />
        </div>
        {notice && <div role={notice.bad ? 'alert' : 'status'} style={{ marginBottom: 8, color: notice.bad ? 'var(--hf-danger-text)' : 'var(--hf-success-text-strong)' }}>{notice.text}</div>}
        {cands.isLoading && <div>Loading...</div>}
        {cands.data && cands.data.length === 0 && <div style={{ color: 'var(--hf-text-muted)' }}>No candidates match.</div>}
        {(cands.data ?? []).map(c => (
          <div key={c.applicantId} style={{ border: '1px solid var(--hf-border)', borderRadius: 8, padding: 10, marginBottom: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
              <div>
                <strong>{c.name}</strong> <span style={{ color: 'var(--hf-text-muted)' }}>{c.email}{c.location ? `, ${c.location}` : ''}</span>
                <div style={{ color: 'var(--hf-text-muted)' }}>{c.applications} application{c.applications === 1 ? '' : 's'}{c.lastActivity ? `, last activity ${c.lastActivity.slice(0, 10)}` : ''}</div>
              </div>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'flex-start' }}>
                <select aria-label={`Add ${c.name} to pool`} value="" style={field} onChange={e => e.target.value && addToPool.mutate({ pool: e.target.value, id: c.applicantId })}>
                  <option value="">Add to pool...</option>
                  {(pools.data ?? []).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
                <select aria-label={`Put ${c.name} forward`} value="" style={field} onChange={e => e.target.value && putForward.mutate({ id: c.applicantId, jobId: e.target.value })}>
                  <option value="">Put forward for...</option>
                  {(jobs.data ?? []).map(j => <option key={j.id} value={j.id}>{j.title}</option>)}
                </select>
                {poolId && <button style={btn} onClick={() => removeFromPool.mutate(c.applicantId)}>Remove from pool</button>}
              </div>
            </div>
            <div style={{ marginTop: 6, display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
              {tagEdit?.id === c.applicantId ? (
                <>
                  <input aria-label="Tags, separated by commas" value={tagEdit.text} onChange={e => setTagEdit({ id: c.applicantId, text: e.target.value })} style={{ ...field, flex: 1 }} />
                  <button style={btn} onClick={() => saveTags.mutate({ id: c.applicantId, tags: tagEdit.text.split(',') })}>Save tags</button>
                  <button style={btn} onClick={() => setTagEdit(null)}>Cancel</button>
                </>
              ) : (
                <>
                  {c.tags.map(t => <span key={t} style={{ background: 'var(--hf-surface-muted)', borderRadius: 10, padding: '2px 8px' }}>{t}</span>)}
                  <button style={{ ...btn, border: 'none', background: 'none', color: 'var(--hf-primary-text)' }} onClick={() => setTagEdit({ id: c.applicantId, text: c.tags.join(', ') })}>
                    {c.tags.length ? 'Edit tags' : 'Add tags'}
                  </button>
                </>
              )}
            </div>
          </div>
        ))}
      </section>
    </div>
  )
}
