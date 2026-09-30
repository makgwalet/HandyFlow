// src/pages/tasks/BoardSettingsModal.tsx
import { useState } from 'react'
import { X, ArrowUp, ArrowDown, Trash2, Plus } from 'lucide-react'
import type { Board, Column, TaskCategory } from './tasks.types'
import { BOARD_COLORS, CATEGORIES, COLUMN_COLORS, categoryOf } from './tasks.constants'
import { ConfirmModal } from './tasks.ui'
import { inp, lbl, btnPrimary, btnSecondary } from './tasks.styles'
import { useArchiveBoard, useColumnMutations, useUpdateBoard, type ColumnInput } from './tasks.api'
import { toast } from '../../store/toast.store'

const toInput = (c: Column, patch: Partial<ColumnInput> = {}): ColumnInput => {
  const category = categoryOf(c)
  return { name: c.name, color: c.color, sortOrder: c.sortOrder, isDoneColumn: category === 'DONE', category, ...patch }
}

function ListRow({ column, first, last, onSave, onMove, onDelete, busy }: {
  column: Column; first: boolean; last: boolean; busy: boolean
  onSave: (c: ColumnInput) => void; onMove: (dir: -1 | 1) => void; onDelete: () => void
}) {
  const [name, setName] = useState(column.name)
  const [color, setColor] = useState(column.color ?? COLUMN_COLORS[0])
  const [category, setCategory] = useState<TaskCategory>(categoryOf(column))
  const dirty = name.trim() !== column.name || color !== (column.color ?? COLUMN_COLORS[0]) || category !== categoryOf(column)
  const arrow = (off: boolean): React.CSSProperties => ({ display: 'flex', background: 'none', border: 'none', padding: 3, cursor: off ? 'default' : 'pointer', color: off ? 'var(--hf-text-disabled)' : 'var(--hf-text-muted)' })
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', padding: '8px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <button type="button" aria-label={`Move ${column.name} left`} disabled={first || busy} onClick={() => onMove(-1)} style={arrow(first)}><ArrowUp size={14} /></button>
        <button type="button" aria-label={`Move ${column.name} right`} disabled={last || busy} onClick={() => onMove(1)} style={arrow(last)}><ArrowDown size={14} /></button>
      </div>
      <input type="color" value={color} onChange={e => setColor(e.target.value)} aria-label={`Colour of ${column.name}`} style={{ width: 32, height: 32, border: 'none', background: 'none', padding: 0, cursor: 'pointer' }} />
      <input value={name} maxLength={100} onChange={e => setName(e.target.value)} aria-label="List name" style={{ ...inp, flex: '1 1 140px', width: 'auto', padding: '7px 10px' }} />
      <select value={category} onChange={e => setCategory(e.target.value as TaskCategory)} aria-label={`Stage of ${column.name}`} style={{ ...inp, width: 'auto', padding: '7px 10px' }}>
        {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
      </select>
      <button type="button" disabled={!dirty || !name.trim() || busy} onClick={() => onSave({ name: name.trim(), color, sortOrder: column.sortOrder, isDoneColumn: category === 'DONE', category })}
        style={{ ...btnPrimary, padding: '7px 12px', opacity: dirty && name.trim() ? 1 : 0.45 }}>Save</button>
      <button type="button" aria-label={`Delete ${column.name}`} onClick={onDelete} disabled={busy}
        style={{ display: 'flex', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-danger-text)', padding: 4 }}><Trash2 size={15} /></button>
    </div>
  )
}

export function BoardSettingsModal({ board, columns, initialTab = 'general', onClose, onArchived }: {
  board: Board; columns: Column[]; initialTab?: 'general' | 'lists'; onClose: () => void; onArchived: () => void
}) {
  const [tab, setTab] = useState(initialTab)
  const [name, setName] = useState(board.name)
  const [description, setDescription] = useState(board.description ?? '')
  const [color, setColor] = useState(board.color ?? BOARD_COLORS[0])
  const [confirmArchive, setConfirmArchive] = useState(false)
  const [deleting, setDeleting] = useState<Column | null>(null)
  const [newName, setNewName] = useState('')
  const [newCat, setNewCat] = useState<TaskCategory>('TODO')
  const update = useUpdateBoard(board.id)
  const archive = useArchiveBoard(board.id)
  const cols = useColumnMutations(board.id)
  const ordered = [...columns].sort((a, b) => a.sortOrder - b.sortOrder)
  const busy = cols.add.isPending || cols.update.isPending || cols.remove.isPending

  const move = async (index: number, dir: -1 | 1) => {
    const next = [...ordered]; const [m] = next.splice(index, 1); next.splice(index + dir, 0, m)
    try {
      for (let i = 0; i < next.length; i++) {           // renumber 0..n-1 so positions never collide
        if (next[i].sortOrder !== i) await cols.update.mutateAsync({ id: next[i].id, c: toInput(next[i], { sortOrder: i }) })
      }
    } catch { /* toasted by the mutation */ }
  }
  const addList = async () => {
    if (!newName.trim()) return
    try {
      await cols.add.mutateAsync({ name: newName.trim(), color: COLUMN_COLORS[ordered.length % COLUMN_COLORS.length], sortOrder: ordered.length, isDoneColumn: newCat === 'DONE', category: newCat })
      setNewName(''); toast.success('List added')
    } catch { /* toasted */ }
  }
  const tabBtn = (id: 'general' | 'lists', label: string) => (
    <button type="button" role="tab" aria-selected={tab === id} onClick={() => setTab(id)}
      style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '10px 4px', marginRight: 18, fontSize: 14, fontWeight: 600,
        color: tab === id ? 'var(--hf-primary-text)' : 'var(--hf-text-muted)', borderBottom: `2px solid ${tab === id ? 'var(--hf-primary)' : 'transparent'}` }}>{label}</button>
  )
  return (
    <div role="dialog" aria-modal="true" aria-label="Board settings" style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1500, padding: 16 }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 14, width: 'min(680px, 100%)', maxHeight: '90vh', display: 'flex', flexDirection: 'column', boxShadow: 'var(--hf-shadow-md)' }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '16px 22px 0' }}>
          <h2 style={{ margin: 0, fontSize: 17, fontWeight: 800, color: 'var(--hf-text)', flex: 1 }}>Board settings</h2>
          <button type="button" onClick={onClose} aria-label="Close" style={{ display: 'flex', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-muted)' }}><X size={18} /></button>
        </div>
        <div role="tablist" style={{ padding: '0 22px', borderBottom: '1px solid var(--hf-border)' }}>{tabBtn('general', 'General')}{tabBtn('lists', 'Lists')}</div>
        <div style={{ padding: 22, overflowY: 'auto' }}>
          {tab === 'general' ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div><label style={lbl} htmlFor="bs-name">Name</label><input id="bs-name" style={inp} value={name} maxLength={100} onChange={e => setName(e.target.value)} /></div>
              <div><label style={lbl} htmlFor="bs-desc">Description</label><textarea id="bs-desc" style={{ ...inp, minHeight: 70, resize: 'vertical' }} value={description} onChange={e => setDescription(e.target.value)} /></div>
              <div><span style={lbl}>Colour</span>
                <div style={{ display: 'flex', gap: 8 }}>{BOARD_COLORS.map(c => (
                  <button key={c} type="button" aria-label={`Colour ${c}`} aria-pressed={color === c} onClick={() => setColor(c)}
                    style={{ width: 28, height: 28, borderRadius: '50%', background: c, cursor: 'pointer', border: color === c ? '3px solid var(--hf-text)' : '3px solid transparent' }} />))}</div></div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                {board.isDefault ? <span style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>The default board can't be archived.</span>
                  : <button type="button" onClick={() => setConfirmArchive(true)} style={{ ...btnSecondary, color: 'var(--hf-danger-text)' }}>Archive board</button>}
                <button type="button" disabled={!name.trim() || update.isPending} style={btnPrimary}
                  onClick={() => update.mutate({ name: name.trim(), description: description.trim() || undefined, color }, { onSuccess: () => toast.success('Board saved') })}>Save changes</button>
              </div>
            </div>
          ) : (
            <div>
              <p style={{ margin: '0 0 12px', fontSize: 13, color: 'var(--hf-text-muted)', lineHeight: 1.5 }}>
                A list's <b>stage</b> decides the status of every task in it. Put a list in "Done" and tasks dropped there are completed.
              </p>
              {ordered.map((c, i) => (
                <ListRow key={`${c.id}|${c.name}|${c.color}|${categoryOf(c)}`} column={c} first={i === 0} last={i === ordered.length - 1} busy={busy}
                  onSave={input => cols.update.mutate({ id: c.id, c: input }, { onSuccess: () => toast.success('List saved') })}
                  onMove={dir => move(i, dir)} onDelete={() => setDeleting(c)} />
              ))}
              <div style={{ display: 'flex', gap: 8, marginTop: 16, flexWrap: 'wrap' }}>
                <input value={newName} maxLength={100} placeholder="New list name" aria-label="New list name" onChange={e => setNewName(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') addList() }} style={{ ...inp, flex: '1 1 160px', width: 'auto' }} />
                <select value={newCat} onChange={e => setNewCat(e.target.value as TaskCategory)} aria-label="Stage of the new list" style={{ ...inp, width: 'auto' }}>
                  {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                </select>
                <button type="button" onClick={addList} disabled={!newName.trim() || busy} style={btnPrimary}><Plus size={14} />Add list</button>
              </div>
            </div>
          )}
        </div>
      </div>
      {confirmArchive && <ConfirmModal title="Archive this board?" message="It disappears from the boards list and its tasks stop counting in the totals. Nothing is deleted." confirmLabel="Archive" danger
        onCancel={() => setConfirmArchive(false)} onConfirm={() => archive.mutate(undefined, { onSuccess: () => { toast.success('Board archived'); onArchived() } })} />}
      {deleting && <ConfirmModal title={`Delete "${deleting.name}"?`} danger confirmLabel="Delete list"
        message={ordered.length <= 1 ? "A board needs at least one list, so this one can't be deleted." : "Tasks in this list move to another list on the board and take on its stage."}
        onCancel={() => setDeleting(null)} onConfirm={() => { const id = deleting.id; setDeleting(null); if (ordered.length > 1) cols.remove.mutate(id, { onSuccess: () => toast.success('List deleted') }) }} />}
    </div>
  )
}
