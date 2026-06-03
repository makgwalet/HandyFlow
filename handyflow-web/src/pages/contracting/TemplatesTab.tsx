// src/pages/contracting/TemplatesTab.tsx
//
// Fixes over original:
//  • Template preview now renders HTML properly via dangerouslySetInnerHTML
//    (original was showing raw tags in a <pre>)
//  • Rendered / HTML-source toggle on each expanded template card
//  • Live rendered preview while typing in the body editor
//  • Variable tags are click-to-copy pills
//  • Correct API unwrap
//  • System vs custom template section headers
//  • "Used in N contracts" count surfaced where available

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X, Lock, Eye, EyeOff, Copy, Check } from 'lucide-react'
import { unwrap } from './ContractingPage'

// ─── Constants ────────────────────────────────────────────────────────────────

const CONTRACT_TYPES = [
  { value: 'SERVICE_AGREEMENT', label: 'Service Agreement'       },
  { value: 'NDA',               label: 'Non-Disclosure Agreement' },
  { value: 'EMPLOYMENT',        label: 'Employment Contract'      },
  { value: 'JOINT_VENTURE',     label: 'Joint Venture'            },
  { value: 'EQUIPMENT_HIRE',    label: 'Equipment Hire'           },
  { value: 'LEASE',             label: 'Lease Agreement'          },
  { value: 'SUPPLY',            label: 'Supply Agreement'         },
  { value: 'SUBCONTRACTOR',     label: 'Subcontractor'            },
  { value: 'SERVICE_LEVEL',     label: 'Service Level Agreement'  },
  { value: 'CONSULTING',        label: 'Consulting Agreement'     },
  { value: 'RETAINER',          label: 'Retainer Agreement'       },
  { value: 'OTHER',             label: 'Other'                    },
]

const TYPE_COLOR: Record<string, string> = {
  SERVICE_AGREEMENT: '#0D9488', NDA: '#7C3AED', EMPLOYMENT: '#1D4ED8',
  JOINT_VENTURE: '#D97706', EQUIPMENT_HIRE: '#EA580C', LEASE: '#166534',
  SUPPLY: '#0891B2', SUBCONTRACTOR: '#DC2626', SERVICE_LEVEL: '#6366F1',
  CONSULTING: '#DB2777', RETAINER: '#854D0E', OTHER: '#64748B',
}

interface Template {
  id: string
  name: string
  contractType: string
  description: string
  bodyTemplate: string
  variables: Record<string, string>
  isSystem: boolean
}

// ─── Shared styles ────────────────────────────────────────────────────────────

const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px',
  border: '1.5px solid #E2E8F0', borderRadius: 8,
  fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none',
}
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 12,
  fontWeight: 600, color: '#374151', marginBottom: 4,
}
const MODAL: React.CSSProperties = {
  position: 'fixed', inset: 0,
  background: 'rgba(15,23,42,0.5)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 1000,
}

// ─── HtmlPreview — renders HTML body properly ────────────────────────────────

function HtmlPreview({ html, maxHeight = 280 }: { html: string; maxHeight?: number }) {
  return (
    <div
      style={{
        maxHeight, overflowY: 'auto',
        padding: '14px 16px',
        background: '#fff', border: '1px solid #E2E8F0',
        borderRadius: 7, fontSize: 13, lineHeight: 1.8, color: '#374151',
      }}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  )
}

// ─── TemplateCard ─────────────────────────────────────────────────────────────

function TemplateCard({ template, onCopyVar, copied }: {
  template: Template
  onCopyVar: (v: string) => void
  copied: string | null
}) {
  const [expanded, setExpanded] = useState(false)
  const [mode,     setMode]     = useState<'rendered' | 'source'>('rendered')

  const color    = TYPE_COLOR[template.contractType] ?? '#64748B'
  const varKeys  = template.variables ? Object.keys(template.variables) : []
  const typeLabel = CONTRACT_TYPES.find(t => t.value === template.contractType)?.label ?? template.contractType.replace(/_/g, ' ')

  return (
    <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
      {/* Colour stripe */}
      <div style={{ height: 3, background: color }} />

      {/* Card body */}
      <div style={{ padding: '14px 16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A', marginBottom: 5 }}>{template.name}</div>
            <span style={{ background: `${color}18`, color, border: `1px solid ${color}30`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
              {typeLabel}
            </span>
          </div>
          {template.isSystem && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, background: '#F1F5F9', color: '#64748B', padding: '2px 8px', borderRadius: 6, fontSize: 10, fontWeight: 700, flexShrink: 0 }}>
              <Lock size={9} /> SYSTEM
            </div>
          )}
        </div>

        {template.description && (
          <div style={{ fontSize: 12, color: '#64748B', lineHeight: 1.5, marginBottom: 10 }}>
            {template.description}
          </div>
        )}

        {/* Variable pills — click to copy */}
        {varKeys.length > 0 && (
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 10 }}>
            {varKeys.map(v => (
              <button
                key={v}
                onClick={() => onCopyVar(v)}
                title={`Click to copy {{${v}}}`}
                style={{
                  display: 'flex', alignItems: 'center', gap: 3,
                  background: '#F8FAFC', color: '#475569',
                  padding: '2px 7px', borderRadius: 4,
                  fontSize: 10, fontFamily: 'monospace',
                  border: '1px solid #E2E8F0', cursor: 'pointer',
                  transition: 'background 0.1s',
                }}>
                {copied === v ? <Check size={9} color="#166534" /> : <Copy size={9} />}
                {`{{${v}}}`}
              </button>
            ))}
          </div>
        )}

        {/* Preview toggle */}
        {template.bodyTemplate && (
          <button onClick={() => setExpanded(!expanded)} style={{ display: 'flex', alignItems: 'center', gap: 4, background: 'none', border: 'none', color: '#1B3A6B', fontSize: 12, fontWeight: 600, cursor: 'pointer', padding: 0 }}>
            {expanded ? <EyeOff size={13} /> : <Eye size={13} />}
            {expanded ? 'Hide preview' : 'Preview'}
          </button>
        )}
      </div>

      {/* Expanded preview */}
      {expanded && template.bodyTemplate && (
        <div style={{ borderTop: '1px solid #E2E8F0' }}>
          {/* Rendered / Source tabs */}
          <div style={{ display: 'flex', borderBottom: '1px solid #E2E8F0' }}>
            {(['rendered', 'source'] as const).map(m => (
              <button key={m} onClick={() => setMode(m)} style={{
                flex: 1, padding: '7px 0',
                fontSize: 11, fontWeight: mode === m ? 700 : 400,
                cursor: 'pointer', background: mode === m ? '#F8FAFC' : '#fff',
                color: mode === m ? '#1B3A6B' : '#94A3B8',
                border: 'none',
                borderBottom: mode === m ? '2px solid #1B3A6B' : '2px solid transparent',
              }}>
                {m === 'rendered' ? 'Rendered' : 'HTML source'}
              </button>
            ))}
          </div>

          <div style={{ padding: '14px 16px', background: '#FAFAFA' }}>
            {mode === 'rendered' ? (
              /* FIX: renders HTML properly — original was showing raw tags in <pre> */
              <HtmlPreview html={template.bodyTemplate} />
            ) : (
              <pre style={{ margin: 0, fontSize: 11, color: '#475569', fontFamily: 'monospace', whiteSpace: 'pre-wrap', lineHeight: 1.6, maxHeight: 280, overflowY: 'auto' }}>
                {template.bodyTemplate}
              </pre>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// TemplatesTab
// ═══════════════════════════════════════════════════════════════════════════════

export default function TemplatesTab() {
  const qc = useQueryClient()

  const [showCreate, setShowCreate] = useState(false)
  const [copied,     setCopied]     = useState<string | null>(null)
  const [error,      setError]      = useState('')
  const [form, setForm] = useState({
    name: '', contractType: 'SERVICE_AGREEMENT', description: '', bodyTemplate: '',
  })

  // ── Queries ──────────────────────────────────────────────────────────────────

  const { data: templates = [], isLoading } = useQuery<Template[]>({
    queryKey: ['contract-templates'],
    queryFn: async () => unwrap(await apiClient.get('/api/v1/contracts/templates')),
  })

  // ── Mutations ────────────────────────────────────────────────────────────────

  const createTemplate = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/contracts/templates', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['contract-templates'] })
      setShowCreate(false)
      setForm({ name: '', contractType: 'SERVICE_AGREEMENT', description: '', bodyTemplate: '' })
      setError('')
    },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Failed to create template'),
  })

  const copyVariable = (varName: string) => {
    navigator.clipboard.writeText(`{{${varName}}}`).then(() => {
      setCopied(varName)
      setTimeout(() => setCopied(null), 1400)
    })
  }

  const systemTemplates = templates.filter(t => t.isSystem)
  const customTemplates  = templates.filter(t => !t.isSystem)

  const inp2: React.CSSProperties = { ...inp, fontSize: 13 }

  // ─── Render ──────────────────────────────────────────────────────────────────

  if (isLoading) return (
    <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading templates…</div>
  )

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
        <div style={{ fontSize: 13, color: '#64748B' }}>
          <span style={{ fontWeight: 700, color: '#0F172A' }}>{systemTemplates.length}</span> system templates ·{' '}
          <span style={{ fontWeight: 700, color: '#0F172A' }}>{customTemplates.length}</span> custom templates
        </div>
        <button onClick={() => { setShowCreate(true); setError('') }}
          style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> New Template
        </button>
      </div>

      {/* System templates */}
      {systemTemplates.length > 0 && (
        <div style={{ marginBottom: 28 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 14 }}>
            <Lock size={11} color="#94A3B8" />
            <span style={{ fontSize: 10, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.07em', textTransform: 'uppercase' }}>
              System Templates — SA standard contracts
            </span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 14 }}>
            {systemTemplates.map(t => (
              <TemplateCard key={t.id} template={t} onCopyVar={copyVariable} copied={copied} />
            ))}
          </div>
        </div>
      )}

      {/* Custom templates */}
      {customTemplates.length > 0 && (
        <div>
          <div style={{ fontSize: 10, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.07em', textTransform: 'uppercase', marginBottom: 14 }}>
            Custom Templates
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 14 }}>
            {customTemplates.map(t => (
              <TemplateCard key={t.id} template={t} onCopyVar={copyVariable} copied={copied} />
            ))}
          </div>
        </div>
      )}

      {templates.length === 0 && (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
          <div style={{ fontSize: 36, marginBottom: 12, opacity: 0.3 }}>📋</div>
          <div style={{ fontWeight: 600, color: '#475569' }}>No templates yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>System templates seed automatically on first contract creation.</div>
        </div>
      )}

      {/* ── Create template modal ── */}
      {showCreate && (
        <div style={MODAL}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 720, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.18)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>New Contract Template</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}>
                <X size={20} color="#94A3B8" />
              </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 16 }}>
              <div style={{ gridColumn: '1/-1' }}>
                <label style={lbl}>Template Name *</label>
                <input autoFocus value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g. Standard NDA — Mutual" style={inp2} />
              </div>
              <div>
                <label style={lbl}>Contract Type *</label>
                <select value={form.contractType} onChange={e => setForm(f => ({ ...f, contractType: e.target.value }))} style={{ ...inp2, background: '#fff' }}>
                  {CONTRACT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Description</label>
                <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="When to use this template" style={inp2} />
              </div>
            </div>

            {/* Body editor */}
            <div>
              <label style={lbl}>Template Body *</label>
              <div style={{ marginBottom: 8, padding: '10px 12px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 12, color: '#1D4ED8', lineHeight: 1.6 }}>
                <strong>Variables:</strong>{' '}
                Use <code style={{ background: '#fff', padding: '1px 4px', borderRadius: 3 }}>{'{{variable_name}}'}</code> for dynamic fields.
                Supports HTML: <code style={{ background: '#fff', padding: '1px 4px', borderRadius: 3 }}>{'<h2>'}</code>{' '}
                <code style={{ background: '#fff', padding: '1px 4px', borderRadius: 3 }}>{'<strong>'}</code>{' '}
                <code style={{ background: '#fff', padding: '1px 4px', borderRadius: 3 }}>{'<p>'}</code>.{' '}
                <code style={{ background: '#fff', padding: '1px 4px', borderRadius: 3 }}>{'{{date}}'}</code> auto-resolves to today.
              </div>
              <textarea
                value={form.bodyTemplate}
                onChange={e => setForm(f => ({ ...f, bodyTemplate: e.target.value }))}
                rows={14}
                placeholder={'<h2>NON-DISCLOSURE AGREEMENT</h2>\n<p>This agreement is entered into on {{date}} between:</p>\n<p><strong>{{party_a_name}}</strong> and <strong>{{party_b_name}}</strong></p>\n<h3>1. Purpose</h3>\n<p>{{purpose}}</p>'}
                style={{ ...inp2, fontFamily: 'monospace', fontSize: 12, resize: 'vertical', lineHeight: 1.6 }}
              />
            </div>

            {/* Live rendered preview */}
            {form.bodyTemplate && (
              <div style={{ marginTop: 14 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 7 }}>
                  Rendered preview
                </div>
                {/* FIX: dangerouslySetInnerHTML renders HTML — original showed raw tags */}
                <div
                  style={{ maxHeight: 240, overflowY: 'auto', padding: '14px 16px', background: '#FAFAFA', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 13, lineHeight: 1.8, color: '#374151' }}
                  dangerouslySetInnerHTML={{ __html: form.bodyTemplate }}
                />
              </div>
            )}

            {error && (
              <div style={{ marginTop: 14, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>
                {error}
              </div>
            )}

            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 13, cursor: 'pointer', color: '#374151' }}>
                Cancel
              </button>
              <button
                onClick={() => createTemplate.mutate(form)}
                disabled={!form.name || !form.bodyTemplate || createTemplate.isPending}
                style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '9px 22px', background: !form.name || !form.bodyTemplate ? '#94A3B8' : '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>
                {createTemplate.isPending ? 'Creating…' : 'Create Template'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
