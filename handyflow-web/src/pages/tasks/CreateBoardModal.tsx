// src/pages/tasks/CreateBoardModal.tsx
import { errorMessage } from '../../store/toast.store'
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, Loader2,
} from 'lucide-react'
import { inp, lbl, btnSecondary, btnPrimary } from './tasks.styles'

export function CreateBoardModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  // A board's colour is DATA saved to tasks.color VARCHAR(20): it must stay a #RRGGBB
  // literal (never a CSS variable). Only displaying it may use tokens.
  const [form, setForm] = useState({ name: '', description: '', color: '#1B3A6B' })
  const [error, setError] = useState('')
  const create = useMutation({
    mutationFn: () => apiClient.post('/api/v1/tasks/boards', form),
    onSuccess: () => { onSaved(); onClose() },
    onError: e => setError(errorMessage(e, 'Failed to create board')),
  })
  const BOARD_COLORS = ['#1B3A6B','#0D9488','#D97706','#7C3AED','#DC2626','#0284C7','#166534','#374151']
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20 }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 16, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--hf-border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>New Board</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={lbl}>Board name *</label>
            <input value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} placeholder="e.g. Product Roadmap" style={inp} autoFocus />
          </div>
          <div>
            <label style={lbl}>Description</label>
            <input value={form.description} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} placeholder="What is this board for?" style={inp} />
          </div>
          <div>
            <label style={lbl}>Color</label>
            <div style={{ display: 'flex', gap: 8 }}>
              {BOARD_COLORS.map(c => (
                <div key={c} onClick={() => setForm(p => ({ ...p, color: c }))}
                  style={{ width: 28, height: 28, borderRadius: '50%', background: c, cursor: 'pointer', border: form.color === c ? '3px solid var(--hf-primary)' : '2px solid transparent', boxSizing: 'border-box', transition: 'transform 0.1s' }} />
              ))}
            </div>
          </div>
          {error && <div style={{ background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, padding: '9px 12px', fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
        </div>
        <div style={{ padding: '0 24px 22px', display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button onClick={onClose} style={btnSecondary}>Cancel</button>
          <button onClick={() => create.mutate()} disabled={!form.name.trim() || create.isPending}
            style={{ ...btnPrimary, opacity: !form.name.trim() ? 0.5 : 1 }}>
            {create.isPending ? <Loader2 size={13} /> : <><Plus size={13} /> Create Board</>}
          </button>
        </div>
      </div>
    </div>
  )
}
