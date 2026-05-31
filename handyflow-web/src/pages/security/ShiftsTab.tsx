// src/pages/security/ShiftsTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, Clock, CheckCircle, PlayCircle, AlertCircle,
  X, Calendar, Edit2, AlertTriangle,
} from 'lucide-react'

interface Shift {
  id: string
  siteId: string
  guardId: string
  startAt: string
  endAt: string
  status: 'SCHEDULED' | 'ACTIVE' | 'COMPLETED' | 'MISSED' | 'CANCELLED'
  notes: string | null
  createdAt: string
}

interface Guard { id: string; fullName: string }
interface Site  { id: string; name: string }

const STATUS_CONFIG = {
  SCHEDULED:  { color: '#1D4ED8', bg: '#EFF6FF', label: 'Scheduled',  icon: Calendar },
  ACTIVE:     { color: '#166534', bg: '#DCFCE7', label: 'Active',     icon: PlayCircle },
  COMPLETED:  { color: '#0D9488', bg: '#F0FDF4', label: 'Completed',  icon: CheckCircle },
  MISSED:     { color: '#DC2626', bg: '#FEF2F2', label: 'Missed',     icon: AlertCircle },
  CANCELLED:  { color: '#94A3B8', bg: '#F8FAFC', label: 'Cancelled',  icon: X },
}

const EMPTY_FORM = { siteId: '', guardId: '', startAt: '', endAt: '', notes: '' }

function validate(form: typeof EMPTY_FORM) {
  const errors: Record<string, string> = {}
  if (!form.guardId) errors.guardId = 'Please select a guard'
  if (!form.siteId)  errors.siteId  = 'Please select a site'
  if (!form.startAt) errors.startAt = 'Start time is required'
  if (!form.endAt)   errors.endAt   = 'End time is required'
  if (form.startAt && form.endAt && form.startAt >= form.endAt)
    errors.endAt = 'End time must be after start time'
  return errors
}

export default function ShiftsTab() {
  const qc = useQueryClient()

  const [showAdd, setShowAdd]         = useState(false)
  const [editing, setEditing]         = useState<Shift | null>(null)
  const [filterStatus, setFilterStatus] = useState<string>('ALL')
  const [form, setForm]               = useState(EMPTY_FORM)
  const [editForm, setEditForm]       = useState({ notes: '', endAt: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError]       = useState('')

  // ── Queries ──────────────────────────────────────────────────────────────────

  const { data: shifts = [], isLoading } = useQuery({
    queryKey: ['shifts'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/security/shifts?size=100&sort=startAt,desc')
      const payload = res.data?.data ?? res.data
      return (payload?.content ?? payload) as Shift[]
    },
  })

  const { data: guards = [] } = useQuery({
    queryKey: ['guards-list'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/security/guards?size=100')
      const payload = res.data?.data ?? res.data
      return (payload?.content ?? payload) as Guard[]
    },
  })

  const { data: sites = [] } = useQuery({
    queryKey: ['sites-list'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/security/sites?size=100')
      const payload = res.data?.data ?? res.data
      return (payload?.content ?? payload) as Site[]
    },
  })

  // ── Mutations ─────────────────────────────────────────────────────────────────

  const createShift = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/security/shifts', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['shifts'] })
      closeAdd()
    },
    onError: (e: any) => {
      const d = e.response?.data
      if (d?.errors && typeof d.errors === 'object') {
        setFieldErrors(d.errors)
      } else {
        setApiError(d?.message ?? 'Failed to schedule shift')
      }
    },
  })

  const updateShift = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.put(`/api/v1/security/shifts/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['shifts'] })
      setEditing(null)
      setApiError('')
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? 'Failed to update shift'),
  })

  const startShift = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/shifts/${id}/start`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shifts'] }),
    onError: (e: any) => setApiError(e.response?.data?.message ?? 'Failed to start shift'),
  })

  const completeShift = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/shifts/${id}/complete`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shifts'] }),
    onError: (e: any) => setApiError(e.response?.data?.message ?? 'Failed to complete shift'),
  })

  // ── Helpers ───────────────────────────────────────────────────────────────────

  const closeAdd = () => {
    setShowAdd(false)
    setForm(EMPTY_FORM)
    setFieldErrors({})
    setApiError('')
  }

  const handleCreate = () => {
    const errors = validate(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    createShift.mutate({
      guardId: form.guardId,
      siteId:  form.siteId,
      startAt: new Date(form.startAt).toISOString(),
      endAt:   new Date(form.endAt).toISOString(),
      notes:   form.notes || null,
    })
  }

  const handleUpdate = () => {
    if (!editing) return
    const body: any = { notes: editForm.notes || null }
    if (editForm.endAt) body.endAt = new Date(editForm.endAt).toISOString()
    updateShift.mutate({ id: editing.id, body })
  }

  const openEdit = (shift: Shift) => {
    setEditing(shift)
    setEditForm({
      notes: shift.notes ?? '',
      endAt: shift.endAt
        ? new Date(shift.endAt).toISOString().slice(0, 16)
        : '',
    })
    setApiError('')
  }

  const filtered = filterStatus === 'ALL'
    ? shifts
    : shifts.filter(s => s.status === filterStatus)

  const guardName = (id: string) =>
    guards.find(g => g.id === id)?.fullName ?? id.slice(0, 8) + '...'
  const siteName = (id: string) =>
    sites.find(s => s.id === id)?.name ?? id.slice(0, 8) + '...'

  const fmtDate = (iso: string) =>
    new Date(iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
  const fmtTime = (iso: string) =>
    new Date(iso).toLocaleTimeString('en-ZA', { hour: '2-digit', minute: '2-digit' })

  const inpStyle = (key: string): React.CSSProperties => ({
    width: '100%', padding: '9px 12px', boxSizing: 'border-box' as const,
    border: `1.5px solid ${fieldErrors[key] ? '#DC2626' : '#E2E8F0'}`,
    borderRadius: 8, fontSize: 14,
    background: fieldErrors[key] ? '#FFF5F5' : '#fff', outline: 'none',
  })

  const FieldErr = ({ name }: { name: string }) =>
    fieldErrors[name] ? (
      <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#DC2626', marginTop: 4 }}>
        <AlertCircle size={12} />{fieldErrors[name]}
      </div>
    ) : null

  // ── Stats ─────────────────────────────────────────────────────────────────────

  const stats = [
    { label: 'Scheduled', value: shifts.filter(s => s.status === 'SCHEDULED').length, color: '#1D4ED8' },
    { label: 'Active Now', value: shifts.filter(s => s.status === 'ACTIVE').length,   color: '#166534' },
    { label: 'Completed',  value: shifts.filter(s => s.status === 'COMPLETED').length, color: '#0D9488' },
    { label: 'Missed',     value: shifts.filter(s => s.status === 'MISSED').length,    color: '#DC2626' },
  ]

  // ── Render ────────────────────────────────────────────────────────────────────

  return (
    <div>

      {/* Stats */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 10, padding: '12px 16px' }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 1 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18, flexWrap: 'wrap', gap: 10 }}>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {['ALL', 'SCHEDULED', 'ACTIVE', 'COMPLETED', 'MISSED', 'CANCELLED'].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)} style={{
              padding: '6px 12px', borderRadius: 20, border: '1px solid',
              borderColor: filterStatus === s ? '#1B3A6B' : '#E2E8F0',
              background: filterStatus === s ? '#1B3A6B' : '#fff',
              color: filterStatus === s ? '#fff' : '#64748B',
              fontSize: 12, fontWeight: filterStatus === s ? 600 : 400, cursor: 'pointer',
            }}>
              {s === 'ALL' ? 'All' : STATUS_CONFIG[s as keyof typeof STATUS_CONFIG]?.label ?? s}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError('') }}
          style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> Schedule Shift
        </button>
      </div>

      {/* Global error banner */}
      {apiError && (
        <div style={{ marginBottom: 16, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
          <AlertCircle size={14} />{apiError}
          <button onClick={() => setApiError('')} style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: '#DC2626', display: 'flex' }}><X size={14} /></button>
        </div>
      )}

      {/* Shift list */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading shifts...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
          <Clock size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: '#475569' }}>
            {filterStatus === 'ALL' ? 'No shifts yet' : `No ${STATUS_CONFIG[filterStatus as keyof typeof STATUS_CONFIG]?.label.toLowerCase() ?? filterStatus} shifts`}
          </div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Schedule a shift to assign a guard to a site.</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {filtered.map(shift => {
            const cfg = STATUS_CONFIG[shift.status] ?? STATUS_CONFIG.SCHEDULED
            const Icon = cfg.icon
            const isScheduled = shift.status === 'SCHEDULED'
            const isActive    = shift.status === 'ACTIVE'
            const canEdit     = ['SCHEDULED', 'ACTIVE'].includes(shift.status)

            return (
              <div key={shift.id} style={{
                background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12,
                padding: '16px 20px', display: 'flex', alignItems: 'center',
                justifyContent: 'space-between', gap: 14,
              }}>
                {/* Left — icon + details */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 14, flex: 1, minWidth: 0 }}>
                  <div style={{ width: 44, height: 44, borderRadius: 10, background: cfg.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <Icon size={20} color={cfg.color} />
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 3, flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 700, color: '#0F172A', fontSize: 14 }}>
                        {guardName(shift.guardId)}
                      </span>
                      <span style={{ color: '#94A3B8', fontSize: 13 }}>→</span>
                      <span style={{ color: '#475569', fontSize: 14 }}>
                        {siteName(shift.siteId)}
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: '#94A3B8', marginBottom: shift.notes ? 3 : 0 }}>
                      {fmtDate(shift.startAt)} · {fmtTime(shift.startAt)} – {fmtTime(shift.endAt)}
                    </div>
                    {shift.notes && (
                      <div style={{ fontSize: 12, color: '#64748B', fontStyle: 'italic' }}>
                        {shift.notes}
                      </div>
                    )}
                  </div>
                </div>

                {/* Right — badge + actions */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                  <span style={{ background: cfg.bg, color: cfg.color, padding: '4px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>
                    {cfg.label}
                  </span>

                  {canEdit && (
                    <button onClick={() => openEdit(shift)} title="Edit shift"
                      style={{ display: 'flex', alignItems: 'center', gap: 4, background: '#F0F9FF', color: '#0369A1', border: '1px solid #BAE6FD', borderRadius: 7, padding: '7px 12px', fontSize: 12, cursor: 'pointer', fontWeight: 600 }}>
                      <Edit2 size={12} /> Edit
                    </button>
                  )}

                  {isScheduled && (
                    <button onClick={() => startShift.mutate(shift.id)} disabled={startShift.isPending}
                      style={{ display: 'flex', alignItems: 'center', gap: 5, background: '#DCFCE7', color: '#166534', border: '1px solid #86EFAC', borderRadius: 7, padding: '7px 14px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
                      <PlayCircle size={13} /> Start
                    </button>
                  )}

                  {isActive && (
                    <button onClick={() => completeShift.mutate(shift.id)} disabled={completeShift.isPending}
                      style={{ display: 'flex', alignItems: 'center', gap: 5, background: '#F0FDF4', color: '#0D9488', border: '1px solid #99F6E4', borderRadius: 7, padding: '7px 14px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
                      <CheckCircle size={13} /> Complete
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* ── Schedule Shift Modal ─────────────────────────────────────────── */}
      {showAdd && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 520, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>

            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{ width: 36, height: 36, borderRadius: 9, background: '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Clock size={18} color="#1D4ED8" />
                </div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Schedule Shift</h3>
              </div>
              <button onClick={closeAdd} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

              {/* Guard */}
              <div>
                <label style={lbl}>Guard *</label>
                {guards.length === 0 ? (
                  <div style={{ padding: '10px 12px', background: '#FEF3C7', border: '1px solid #FCD34D', borderRadius: 8, fontSize: 13, color: '#92400E' }}>
                    No guards found. Add guards first in the Guards tab.
                  </div>
                ) : (
                  <select value={form.guardId}
                    onChange={e => { setForm(f => ({ ...f, guardId: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.guardId; return n }) }}
                    style={inpStyle('guardId')}>
                    <option value="">Select a guard...</option>
                    {guards.map(g => <option key={g.id} value={g.id}>{g.fullName}</option>)}
                  </select>
                )}
                <FieldErr name="guardId" />
              </div>

              {/* Site */}
              <div>
                <label style={lbl}>Site *</label>
                {sites.length === 0 ? (
                  <div style={{ padding: '10px 12px', background: '#FEF3C7', border: '1px solid #FCD34D', borderRadius: 8, fontSize: 13, color: '#92400E' }}>
                    No sites registered. Add sites first in the Sites tab.
                  </div>
                ) : (
                  <select value={form.siteId}
                    onChange={e => { setForm(f => ({ ...f, siteId: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.siteId; return n }) }}
                    style={inpStyle('siteId')}>
                    <option value="">Select a site...</option>
                    {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                )}
                <FieldErr name="siteId" />
              </div>

              {/* Date/time grid */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <div>
                  <label style={lbl}>Start *</label>
                  <input type="datetime-local" value={form.startAt}
                    onChange={e => { setForm(f => ({ ...f, startAt: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.startAt; return n }) }}
                    style={inpStyle('startAt')} />
                  <FieldErr name="startAt" />
                </div>
                <div>
                  <label style={lbl}>End *</label>
                  <input type="datetime-local" value={form.endAt}
                    onChange={e => { setForm(f => ({ ...f, endAt: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.endAt; return n }) }}
                    style={inpStyle('endAt')} />
                  <FieldErr name="endAt" />
                </div>
              </div>

              {/* Duration preview */}
              {form.startAt && form.endAt && new Date(form.endAt) > new Date(form.startAt) && (
                <div style={{ padding: '8px 14px', background: '#F0FDF4', border: '1px solid #86EFAC', borderRadius: 8, fontSize: 13, color: '#166534', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <CheckCircle size={13} />
                  Duration: {Math.round((new Date(form.endAt).getTime() - new Date(form.startAt).getTime()) / 3600000 * 10) / 10} hours
                </div>
              )}

              {/* Notes */}
              <div>
                <label style={lbl}>Notes <span style={{ fontWeight: 400, color: '#94A3B8' }}>(optional)</span></label>
                <input value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                  placeholder="e.g. Armed response required, access code 1234" style={inpStyle('_')} />
              </div>
            </div>

            {/* Overlap warning */}
            <div style={{ marginTop: 16, padding: '10px 14px', background: '#FEF3C7', border: '1px solid #FCD34D', borderRadius: 8, fontSize: 12, color: '#92400E', display: 'flex', gap: 8 }}>
              <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: 1 }} />
              The system will reject overlapping shifts for the same guard automatically.
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={closeAdd} style={cancelBtn}>Cancel</button>
              <button onClick={handleCreate} disabled={createShift.isPending} style={submitBtn}>
                {createShift.isPending ? 'Scheduling...' : 'Schedule Shift'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Edit Shift Modal ─────────────────────────────────────────────── */}
      {editing && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 460, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: '0 0 4px', fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Edit Shift</h3>
                <div style={{ fontSize: 13, color: '#94A3B8' }}>
                  {guardName(editing.guardId)} → {siteName(editing.siteId)}
                </div>
              </div>
              <button onClick={() => { setEditing(null); setApiError('') }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

              {/* Current times info */}
              <div style={{ padding: '12px 14px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase' as const, marginBottom: 4 }}>Current schedule</div>
                <div style={{ fontSize: 13, color: '#0F172A' }}>
                  {fmtDate(editing.startAt)} · {fmtTime(editing.startAt)} – {fmtTime(editing.endAt)}
                </div>
                <div style={{ marginTop: 4 }}>
                  <span style={{ background: STATUS_CONFIG[editing.status]?.bg, color: STATUS_CONFIG[editing.status]?.color, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
                    {STATUS_CONFIG[editing.status]?.label}
                  </span>
                </div>
              </div>

              {/* Extend end time */}
              <div>
                <label style={lbl}>
                  Extend end time <span style={{ fontWeight: 400, color: '#94A3B8' }}>(leave blank to keep current)</span>
                </label>
                <input type="datetime-local" value={editForm.endAt}
                  onChange={e => setEditForm(f => ({ ...f, endAt: e.target.value }))}
                  style={{ ...inpStyle('_') }} />
                {editForm.endAt && editing.endAt && (
                  <div style={{ fontSize: 12, color: '#0D9488', marginTop: 4 }}>
                    New end: {fmtTime(new Date(editForm.endAt).toISOString())} on {fmtDate(new Date(editForm.endAt).toISOString())}
                  </div>
                )}
              </div>

              {/* Notes */}
              <div>
                <label style={lbl}>Notes</label>
                <input value={editForm.notes}
                  onChange={e => setEditForm(f => ({ ...f, notes: e.target.value }))}
                  placeholder="Update shift notes..."
                  style={inpStyle('_')} />
              </div>
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => { setEditing(null); setApiError('') }} style={cancelBtn}>Cancel</button>
              <button onClick={handleUpdate} disabled={updateShift.isPending} style={submitBtn}>
                {updateShift.isPending ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  )
}

// ── Styles ─────────────────────────────────────────────────────────────────────
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5,
}
const cancelBtn: React.CSSProperties = {
  padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9,
  background: '#fff', fontSize: 14, cursor: 'pointer', color: '#374151',
}
const submitBtn: React.CSSProperties = {
  padding: '9px 22px', background: '#1B3A6B', color: '#fff',
  border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: 'pointer',
}