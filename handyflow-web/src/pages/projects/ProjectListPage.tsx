// src/pages/projects/ProjectListPage.tsx
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  HardHat, Plus, Search, AlertTriangle, CheckCircle,
  Clock, Building2, Calendar, TrendingUp, ChevronRight,
} from 'lucide-react'
import { apiClient } from '../../api/client'

// ── Types ─────────────────────────────────────────────────────────────────────
interface Project {
  id: string; projectNumber: string; name: string; description: string | null
  projectType: string; status: string; health: string
  clientName: string | null; siteAddress: string | null
  startDate: string | null; endDate: string | null
  budgetTotal: number; budgetSpent: number; budgetCommitted: number; budgetVariance: number
  contractValue: number | null; projectManagerName: string | null
  taskCount: number; completedTaskCount: number; openRiskCount: number
  createdAt: string
}
interface Summary {
  activeProjects: number; redProjects: number; amberProjects: number
  pendingTimeApprovals: number; openRedRisks: number
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function unwrap<T>(res: any): T[] {
  const d = res?.data?.data ?? res?.data ?? []
  if (Array.isArray(d)) return d as T[]
  if (d?.content) return d.content as T[]
  return []
}
const fmtR = (n: number) =>
  `R ${Number(n ?? 0).toLocaleString('en-ZA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString('en-ZA') : '—'
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 9, fontSize: 14, boxSizing: 'border-box' as const, outline: 'none', background: '#fff' }

const HEALTH_STYLES: Record<string, { bg: string; color: string; dot: string }> = {
  GREEN:  { bg: '#DCFCE7', color: '#166534', dot: '#16A34A' },
  AMBER:  { bg: '#FEF3C7', color: '#92400E', dot: '#D97706' },
  RED:    { bg: '#FEF2F2', color: '#DC2626', dot: '#DC2626' },
}
const STATUS_STYLES: Record<string, { bg: string; color: string }> = {
  PLANNING:   { bg: '#F1F5F9', color: '#475569' },
  ACTIVE:     { bg: '#DBEAFE', color: '#1D4ED8' },
  ON_HOLD:    { bg: '#FEF3C7', color: '#92400E' },
  COMPLETED:  { bg: '#DCFCE7', color: '#166534' },
  CANCELLED:  { bg: '#FEF2F2', color: '#DC2626' },
}
const TYPE_ICONS: Record<string, string> = {
  CONSTRUCTION: '🏗️', EARTHMOVING: '🚜', SECURITY: '🔒',
  EVENT: '🎪', IT: '💻', GENERAL: '📋',
}

const STATUSES = ['', 'PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED']

// ── Page ──────────────────────────────────────────────────────────────────────
export function ProjectListPage() {
  const qc = useQueryClient()
  const nav = useNavigate()
  const [statusFilter, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [err, setErr] = useState('')

  const initForm = () => ({
    name: '', projectType: 'CONSTRUCTION', description: '', clientName: '',
    siteAddress: '', startDate: '', endDate: '', budgetTotal: '',
    contractValue: '', contractRef: '', projectManagerName: '',
    cidbGrade: '', nhbrcNumber: '',
  })
  const [form, setForm] = useState(initForm())
  const sf = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: summary } = useQuery<Summary>({
    queryKey: ['pm-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/projects/summary'); return r.data?.data ?? r.data },
    staleTime: 30_000,
  })

  const { data: projects = [], isLoading } = useQuery<Project[]>({
    queryKey: ['pm-projects', statusFilter],
    queryFn: async () => {
      const url = statusFilter
        ? `/api/v1/projects?status=${statusFilter}&size=50`
        : '/api/v1/projects?size=50'
      const r = await apiClient.get(url)
      return unwrap<Project>(r)
    },
    staleTime: 30_000,
  })

  const filtered = projects.filter(p =>
    !search || p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.projectNumber.toLowerCase().includes(search.toLowerCase()) ||
    (p.clientName ?? '').toLowerCase().includes(search.toLowerCase())
  )

  const createMut = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/projects', body),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['pm-projects'] })
      qc.invalidateQueries({ queryKey: ['pm-summary'] })
      setShowCreate(false); setForm(initForm()); setErr('')
      const id = r.data?.data?.id ?? r.data?.id
      if (id) nav(`/projects/${id}`)
    },
    onError: (e: any) => setErr(e.response?.data?.message || 'Failed to create project'),
  })

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: '#DBEAFE', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <HardHat size={20} color="#1D4ED8" />
          </div>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: 0 }}>Projects</h1>
        </div>
        <p style={{ fontSize: 13, color: '#94A3B8', margin: 0, paddingLeft: 46 }}>
          Portfolio view · Gantt · Resources · Budget · Risk
        </p>
      </div>

      {/* KPIs */}
      {summary && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5,1fr)', gap: 12, marginBottom: 24 }}>
          {[
            { label: 'Active Projects',   value: summary.activeProjects,       color: '#1D4ED8', bg: '#DBEAFE' },
            { label: 'Red — At Risk',     value: summary.redProjects,          color: '#DC2626', bg: '#FEF2F2' },
            { label: 'Amber — Watch',     value: summary.amberProjects,        color: '#D97706', bg: '#FEF3C7' },
            { label: 'Time Approvals',    value: summary.pendingTimeApprovals, color: '#7C3AED', bg: '#EDE9FE' },
            { label: 'Open Red Risks',    value: summary.openRedRisks,         color: '#DC2626', bg: '#FEF2F2' },
          ].map(s => (
            <div key={s.label} style={{ background: '#fff', border: '1px solid #E8EDF5', borderRadius: 12, padding: '16px 18px' }}>
              <div style={{ fontSize: 11, color: '#94A3B8', fontWeight: 600, textTransform: 'uppercase' as const, letterSpacing: '0.05em', marginBottom: 6 }}>{s.label}</div>
              <div style={{ fontSize: 26, fontWeight: 800, color: s.color }}>{s.value}</div>
            </div>
          ))}
        </div>
      )}

      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, gap: 10, flexWrap: 'wrap' as const }}>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' as const }}>
          {STATUSES.map(s => (
            <button key={s} onClick={() => setStatus(s)}
              style={{ padding: '6px 14px', borderRadius: 20, border: statusFilter === s ? '1.5px solid #1B3A6B' : '1px solid #E2E8F0', background: statusFilter === s ? '#EFF6FF' : '#fff', color: statusFilter === s ? '#1B3A6B' : '#64748B', fontSize: 12, fontWeight: statusFilter === s ? 700 : 400, cursor: 'pointer' }}>
              {s || 'All'}
            </button>
          ))}
          <div style={{ position: 'relative' }}>
            <Search size={13} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search…"
              style={{ ...inp, paddingLeft: 28, width: 180, padding: '7px 12px 7px 28px' }} />
          </div>
        </div>
        <button onClick={() => { setShowCreate(true); setErr('') }}
          style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '9px 16px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> New Project
        </button>
      </div>

      {/* Project cards */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 60, color: '#94A3B8' }}>Loading…</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
          <HardHat size={40} style={{ marginBottom: 12, opacity: .3 }} />
          <div style={{ fontWeight: 600, color: '#475569', marginBottom: 4 }}>No projects</div>
          <div style={{ fontSize: 13 }}>Create your first project to start tracking</div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px,1fr))', gap: 14 }}>
          {filtered.map(p => {
            const h = HEALTH_STYLES[p.health] ?? HEALTH_STYLES.GREEN
            const st = STATUS_STYLES[p.status] ?? STATUS_STYLES.PLANNING
            const spentPct = p.budgetTotal > 0 ? Math.min(100, (p.budgetSpent / p.budgetTotal) * 100) : 0
            const taskPct = p.taskCount > 0 ? Math.round((p.completedTaskCount / p.taskCount) * 100) : 0
            return (
              <div key={p.id} onClick={() => nav(`/projects/${p.id}`)}
                style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: '20px 22px', cursor: 'pointer', transition: 'box-shadow 0.15s' }}
                onMouseEnter={e => (e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,0,0,0.09)')}
                onMouseLeave={e => (e.currentTarget.style.boxShadow = 'none')}>

                {/* Top row */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                      <span style={{ fontSize: 18 }}>{TYPE_ICONS[p.projectType] ?? '📋'}</span>
                      <span style={{ fontSize: 11, color: '#94A3B8', fontWeight: 600 }}>{p.projectNumber}</span>
                      <span style={{ background: st.bg, color: st.color, fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 20 }}>{p.status.replace('_', ' ')}</span>
                    </div>
                    <div style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', marginBottom: 2 }}>{p.name}</div>
                    {p.clientName && <div style={{ fontSize: 12, color: '#64748B' }}>{p.clientName}</div>}
                  </div>
                  {/* Health badge */}
                  <div style={{ background: h.bg, borderRadius: 10, padding: '6px 10px', textAlign: 'center', flexShrink: 0, marginLeft: 10 }}>
                    <div style={{ width: 8, height: 8, borderRadius: '50%', background: h.dot, margin: '0 auto 3px' }} />
                    <div style={{ fontSize: 10, fontWeight: 700, color: h.color }}>{p.health}</div>
                  </div>
                </div>

                {/* Progress bar */}
                <div style={{ marginBottom: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#94A3B8', marginBottom: 4 }}>
                    <span>Tasks {p.completedTaskCount}/{p.taskCount}</span>
                    <span>{taskPct}%</span>
                  </div>
                  <div style={{ height: 6, background: '#F1F5F9', borderRadius: 3, overflow: 'hidden' }}>
                    <div style={{ height: '100%', width: `${taskPct}%`, background: p.health === 'RED' ? '#EF4444' : p.health === 'AMBER' ? '#F59E0B' : '#22C55E', borderRadius: 3, transition: 'width 0.3s' }} />
                  </div>
                </div>

                {/* Budget row */}
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#64748B', marginBottom: 10 }}>
                  <div>
                    <span style={{ color: '#94A3B8' }}>Budget </span>
                    <strong style={{ color: spentPct > 100 ? '#DC2626' : '#0F172A' }}>{fmtR(p.budgetSpent)}</strong>
                    <span style={{ color: '#94A3B8' }}> / {fmtR(p.budgetTotal)}</span>
                  </div>
                  <div>
                    {p.openRiskCount > 0 && (
                      <span style={{ color: '#DC2626', fontWeight: 600 }}>⚠ {p.openRiskCount} risk{p.openRiskCount !== 1 ? 's' : ''}</span>
                    )}
                  </div>
                </div>

                {/* Footer */}
                <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 10, display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#94A3B8' }}>
                  <span>{p.projectManagerName ?? 'No manager assigned'}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Calendar size={11} />
                    {fmtDate(p.endDate)}
                    <ChevronRight size={13} color="#CBD5E1" />
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Create Project Modal */}
      {showCreate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 640, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>New Project</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', fontSize: 20 }}>×</button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <Fld label="Project Name *" span={2}><input value={form.name} onChange={e => sf('name', e.target.value)} placeholder="N2 Bridge Extension" style={inp} autoFocus /></Fld>
              <Fld label="Project Type">
                <select value={form.projectType} onChange={e => sf('projectType', e.target.value)} style={inp}>
                  {['CONSTRUCTION','EARTHMOVING','SECURITY','EVENT','IT','GENERAL'].map(t => <option key={t} value={t}>{TYPE_ICONS[t]} {t}</option>)}
                </select>
              </Fld>
              <Fld label="Client Name"><input value={form.clientName} onChange={e => sf('clientName', e.target.value)} placeholder="Zeta Earthmoving (Pty) Ltd" style={inp} /></Fld>
              <Fld label="Start Date"><input type="date" value={form.startDate} onChange={e => sf('startDate', e.target.value)} style={inp} /></Fld>
              <Fld label="End Date"><input type="date" value={form.endDate} onChange={e => sf('endDate', e.target.value)} style={inp} /></Fld>
              <Fld label="Budget (R) *"><input type="number" value={form.budgetTotal} onChange={e => sf('budgetTotal', e.target.value)} placeholder="0.00" style={inp} /></Fld>
              <Fld label="Contract Value (R)"><input type="number" value={form.contractValue} onChange={e => sf('contractValue', e.target.value)} placeholder="0.00" style={inp} /></Fld>
              <Fld label="Contract Ref"><input value={form.contractRef} onChange={e => sf('contractRef', e.target.value)} placeholder="JBCC-2026-001" style={inp} /></Fld>
              <Fld label="Project Manager"><input value={form.projectManagerName} onChange={e => sf('projectManagerName', e.target.value)} placeholder="Thabo Molefe" style={inp} /></Fld>
              <Fld label="CIDB Grade"><input value={form.cidbGrade} onChange={e => sf('cidbGrade', e.target.value)} placeholder="7CE" style={inp} /></Fld>
              <Fld label="NHBRC Number"><input value={form.nhbrcNumber} onChange={e => sf('nhbrcNumber', e.target.value)} placeholder="NHBRC-123456" style={inp} /></Fld>
              <Fld label="Site Address" span={2}><input value={form.siteAddress} onChange={e => sf('siteAddress', e.target.value)} placeholder="Erf 445 Halfway House, Midrand" style={inp} /></Fld>
              <Fld label="Description" span={2}><textarea value={form.description} onChange={e => sf('description', e.target.value)} placeholder="Brief scope description…" style={{ ...inp, minHeight: 60, resize: 'vertical' as const }} /></Fld>
            </div>
            {err && <div style={{ marginTop: 10, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, color: '#DC2626', fontSize: 13 }}>{err}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 24 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: '9px 16px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 13, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => {
                if (!form.name.trim() || !form.budgetTotal) { setErr('Name and budget are required'); return }
                createMut.mutate({
                  name: form.name.trim(), projectType: form.projectType,
                  description: form.description || null, clientName: form.clientName || null,
                  siteAddress: form.siteAddress || null,
                  startDate: form.startDate || null, endDate: form.endDate || null,
                  budgetTotal: parseFloat(form.budgetTotal),
                  contractValue: form.contractValue ? parseFloat(form.contractValue) : null,
                  contractRef: form.contractRef || null,
                  projectManagerName: form.projectManagerName || null,
                  cidbGrade: form.cidbGrade || null, nhbrcNumber: form.nhbrcNumber || null,
                })
              }} disabled={createMut.isPending}
                style={{ padding: '9px 16px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer', opacity: createMut.isPending ? .6 : 1 }}>
                {createMut.isPending ? 'Creating…' : 'Create Project'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Fld({ label, children, span }: { label: string; children: React.ReactNode; span?: number }) {
  return (
    <div style={span ? { gridColumn: `span ${span}` } : undefined}>
      <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 5 }}>{label}</label>
      {children}
    </div>
  )
}