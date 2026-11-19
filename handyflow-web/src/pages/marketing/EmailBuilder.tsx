// src/pages/marketing/EmailBuilder.tsx
import { useEffect, useMemo, useState } from 'react'
import { Type, Heading1, Image as ImageIcon, MousePointerClick, Minus, Share2, Code, LayoutTemplate, ChevronUp, ChevronDown, Trash2, GripVertical, AlertTriangle } from 'lucide-react'
import { C, inp, lbl } from './ui'

export type BlockType = 'header' | 'heading' | 'text' | 'image' | 'button' | 'divider' | 'social' | 'html'
export type Block = {
  id: string; type: BlockType; text?: string; align?: 'left' | 'center' | 'right'; src?: string; alt?: string
  href?: string; label?: string; color?: string; links?: { label: string; href: string }[]; code?: string
}

const MARK = /^\s*<!--hf-blocks:([A-Za-z0-9+/=]+)-->/
const MAX_BLOCKS = 40

const enc = (s: string) => { const b = new TextEncoder().encode(s); let bin = ''; b.forEach(x => (bin += String.fromCharCode(x))); return btoa(bin) }
const dec = (s: string) => new TextDecoder().decode(Uint8Array.from(atob(s), c => c.charCodeAt(0)))
const uid = () => Math.random().toString(36).slice(2, 9)

export function parseBlocks(html: string): Block[] | null {
  const m = MARK.exec(html || '')
  if (!m) return null
  try { const v = JSON.parse(dec(m[1])); return Array.isArray(v) ? v : null } catch { return null }
}

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
/** Only plain http(s)/mailto links or the unsubscribe token. No quotes, angle brackets or whitespace, so it is attribute-safe. */
const safeUrl = (u?: string, httpsOnly = false) => {
  const v = (u ?? '').trim()
  if (v === '{{unsubscribe_url}}') return v
  const ok = (httpsOnly ? /^https:\/\/[^\s"<>]+$/i : /^(https?:\/\/|mailto:)[^\s"<>]+$/i).test(v)
  return ok && v.length > 8 ? v : ''
}
const hex = (c?: string, d = C.primary) => (/^#[0-9a-fA-F]{6}$/.test(c ?? '') ? (c as string) : d)
const alignOf = (a?: string) => (a === 'center' || a === 'right' ? a : 'left')

function paragraphs(t: string) {
  return t.split(/\n{2,}/).map(p => `<p style="margin:0 0 14px">${esc(p.trim()).replace(/\n/g, '<br>')}</p>`).join('')
}

function renderBlock(b: Block): string {
  switch (b.type) {
    case 'header':
      return `<tr><td style="background:${hex(b.color, C.navy)};padding:20px 24px;color:#ffffff;font:700 20px Arial,sans-serif;text-align:${alignOf(b.align)}">${esc(b.text ?? '')}</td></tr>`
    case 'heading':
      return `<tr><td style="padding:22px 24px 6px;text-align:${alignOf(b.align)};font:700 24px Arial,sans-serif;color:#0F172A">${esc(b.text ?? '')}</td></tr>`
    case 'text':
      return `<tr><td style="padding:8px 24px;text-align:${alignOf(b.align)};font:15px/1.6 Arial,sans-serif;color:#334155">${paragraphs(b.text ?? '')}</td></tr>`
    case 'image': {
      const src = safeUrl(b.src, true); if (!src) return ''
      const img = `<img src="${src}" alt="${esc(b.alt ?? '')}" width="552" style="max-width:100%;height:auto;display:block;margin:0 auto;border:0">`
      const href = safeUrl(b.href)
      return `<tr><td style="padding:12px 24px;text-align:center">${href ? `<a href="${href}">${img}</a>` : img}</td></tr>`
    }
    case 'button': {
      const href = safeUrl(b.href)
      const st = `display:inline-block;background:${hex(b.color)};color:#ffffff;padding:12px 28px;border-radius:8px;text-decoration:none;font:600 15px Arial,sans-serif`
      return `<tr><td style="padding:16px 24px;text-align:${alignOf(b.align ?? 'center')}">${href ? `<a href="${href}" style="${st}">${esc(b.label ?? '')}</a>` : `<span style="${st}">${esc(b.label ?? '')}</span>`}</td></tr>`
    }
    case 'divider':
      return `<tr><td style="padding:12px 24px"><hr style="border:none;border-top:1px solid #E2E8F0;margin:0"></td></tr>`
    case 'social': {
      const links = (b.links ?? []).map(l => ({ label: l.label, href: safeUrl(l.href) })).filter(l => l.href && l.label.trim())
      if (!links.length) return ''
      return `<tr><td style="padding:14px 24px;text-align:center;font:14px Arial,sans-serif">${links.map(l => `<a href="${l.href}" style="color:${C.primary};text-decoration:none;margin:0 10px">${esc(l.label)}</a>`).join('')}</td></tr>`
    }
    case 'html':
      return `<tr><td style="padding:12px 24px">${b.code ?? ''}</td></tr>`
  }
}

export function renderBlocks(blocks: Block[]): string {
  const rows = blocks.map(renderBlock).join('')
  return `<!--hf-blocks:${enc(JSON.stringify(blocks))}--><div style="background:#F1F5F9;padding:24px 0"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center"><table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:8px;overflow:hidden">${rows}</table></td></tr></table></div>`
}

const mk = (type: BlockType): Block => {
  const id = uid()
  switch (type) {
    case 'header':  return { id, type, text: 'Your Business', color: C.navy, align: 'left' }
    case 'heading': return { id, type, text: 'Your headline', align: 'left' }
    case 'text':    return { id, type, text: 'Hi {{first_name}},\n\nWrite your message here.', align: 'left' }
    case 'image':   return { id, type, src: '', alt: '', href: '' }
    case 'button':  return { id, type, label: 'Learn more', href: 'https://', color: C.primary, align: 'center' }
    case 'divider': return { id, type }
    case 'social':  return { id, type, links: [{ label: 'Facebook', href: 'https://' }, { label: 'Instagram', href: 'https://' }] }
    case 'html':    return { id, type, code: '' }
  }
}
const defaultBlocks = (): Block[] => [mk('header'), mk('heading'), mk('text'), mk('button')]

const PALETTE: { type: BlockType; label: string; icon: any }[] = [
  { type: 'header', label: 'Header', icon: LayoutTemplate }, { type: 'heading', label: 'Heading', icon: Heading1 },
  { type: 'text', label: 'Text', icon: Type }, { type: 'image', label: 'Image', icon: ImageIcon },
  { type: 'button', label: 'Button', icon: MousePointerClick }, { type: 'divider', label: 'Divider', icon: Minus },
  { type: 'social', label: 'Social', icon: Share2 }, { type: 'html', label: 'HTML', icon: Code },
]

function summary(b: Block): string {
  switch (b.type) {
    case 'header': case 'heading': return b.text || '(empty)'
    case 'text': return (b.text || '(empty)').replace(/\s+/g, ' ').slice(0, 90)
    case 'image': return b.src || '(no image address yet)'
    case 'button': return `${b.label || '(no label)'} → ${b.href || '(no link)'}`
    case 'divider': return 'Horizontal line'
    case 'social': return (b.links ?? []).map(l => l.label).join(' · ') || '(no links)'
    case 'html': return (b.code || '(empty)').replace(/\s+/g, ' ').slice(0, 90)
  }
}

function warning(b: Block): string | null {
  if (b.type === 'image') { if (!safeUrl(b.src, true)) return 'Needs an https:// image address'; if (!b.alt?.trim()) return 'Add alt text' }
  if (b.type === 'button' && !safeUrl(b.href)) return 'Needs a full https:// link'
  if ((b.type === 'text' || b.type === 'heading' || b.type === 'header') && !b.text?.trim()) return 'Empty'
  return null
}

export default function EmailBuilder({ value, onChange }: { value: string; onChange: (html: string) => void }) {
  const initial = useMemo(() => parseBlocks(value) ?? defaultBlocks(), []) // eslint-disable-line react-hooks/exhaustive-deps
  const [blocks, setBlocks] = useState<Block[]>(initial)
  const [sel, setSel] = useState<string | null>(initial[0]?.id ?? null)
  const [over, setOver] = useState<number | null>(null)

  useEffect(() => { if (!parseBlocks(value)) onChange(renderBlocks(blocks)) }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const commit = (b: Block[]) => { setBlocks(b); onChange(renderBlocks(b)) }
  const add = (type: BlockType, at = blocks.length) => {
    if (blocks.length >= MAX_BLOCKS) return
    const nb = mk(type); const a = [...blocks]; a.splice(at, 0, nb); commit(a); setSel(nb.id)
  }
  const upd = (id: string, patch: Partial<Block>) => commit(blocks.map(b => (b.id === id ? { ...b, ...patch } : b)))
  const move = (i: number, d: number) => { const j = i + d; if (j < 0 || j >= blocks.length) return; const a = [...blocks]; [a[i], a[j]] = [a[j], a[i]]; commit(a) }
  const remove = (id: string) => { commit(blocks.filter(b => b.id !== id)); if (sel === id) setSel(null) }

  const drop = (e: React.DragEvent, idx: number) => {
    e.preventDefault(); setOver(null)
    const d = e.dataTransfer.getData('text/plain')
    if (d.startsWith('new:')) add(d.slice(4) as BlockType, idx)
    else if (d.startsWith('move:')) {
      const from = blocks.findIndex(b => b.id === d.slice(5)); if (from < 0) return
      const a = [...blocks]; const [m] = a.splice(from, 1); a.splice(idx > from ? idx - 1 : idx, 0, m); commit(a)
    }
  }
  const zone = (idx: number) => (
    <div onDragOver={e => { e.preventDefault(); setOver(idx) }} onDragLeave={() => setOver(o => (o === idx ? null : o))} onDrop={e => drop(e, idx)}
      style={{ height: over === idx ? 26 : 8, margin: '1px 0', borderRadius: 6, background: over === idx ? '#DBEAFE' : 'transparent', border: over === idx ? `1px dashed ${C.primary}` : '1px dashed transparent', transition: 'height .1s' }} />
  )

  const cur = blocks.find(b => b.id === sel)
  const small: React.CSSProperties = { ...inp, padding: '7px 10px', fontSize: 13 }
  const alignRow = cur && ['header', 'heading', 'text', 'button'].includes(cur.type) && (
    <div><label style={lbl}>Alignment</label>
      <div style={{ display: 'flex', gap: 6 }}>{(['left', 'center', 'right'] as const).map(a => (
        <button key={a} onClick={() => upd(cur.id, { align: a })} style={{ flex: 1, padding: '5px 0', fontSize: 12, borderRadius: 7, cursor: 'pointer', border: `1px solid ${(cur.align ?? 'left') === a ? C.primary : C.line}`, background: (cur.align ?? 'left') === a ? '#EFF6FF' : '#fff', color: (cur.align ?? 'left') === a ? C.primary : C.text, textTransform: 'capitalize' }}>{a}</button>))}</div></div>)

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '120px minmax(0,1fr) 250px', gap: 14, alignItems: 'start' }}>
      {/* Palette */}
      <div style={{ border: `1px solid ${C.line}`, borderRadius: 12, padding: 10, background: C.bg }}>
        <div style={{ ...lbl, marginBottom: 8 }}>Content blocks</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {PALETTE.map(p => { const I = p.icon; return (
            <div key={p.type} draggable onDragStart={e => e.dataTransfer.setData('text/plain', 'new:' + p.type)} onClick={() => add(p.type)} title="Drag onto the email, or click to add at the end"
              style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '8px 9px', background: '#fff', border: `1px solid ${C.line}`, borderRadius: 8, fontSize: 12, color: C.text, cursor: 'grab' }}><I size={14} />{p.label}</div>) })}
        </div>
      </div>

      {/* Canvas */}
      <div style={{ border: `1px solid ${C.line}`, borderRadius: 12, padding: 12, background: '#F1F5F9', minHeight: 300 }}>
        {zone(0)}
        {blocks.map((b, i) => { const w = warning(b); return (
          <div key={b.id}>
            <div draggable onDragStart={e => e.dataTransfer.setData('text/plain', 'move:' + b.id)} onClick={() => setSel(b.id)}
              style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px', background: '#fff', borderRadius: 9, cursor: 'pointer', border: `2px solid ${sel === b.id ? C.primary : 'transparent'}`, boxShadow: '0 1px 2px rgba(0,0,0,.05)' }}>
              <GripVertical size={14} color={C.mute} style={{ cursor: 'grab', flexShrink: 0 }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: C.mute, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{PALETTE.find(p => p.type === b.type)?.label}</div>
                <div style={{ fontSize: 13, color: C.ink, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{summary(b)}</div>
                {w && <div style={{ fontSize: 11, color: C.amber, display: 'flex', alignItems: 'center', gap: 4, marginTop: 2 }}><AlertTriangle size={11} />{w}</div>}
              </div>
              <button onClick={e => { e.stopPropagation(); move(i, -1) }} style={ib}><ChevronUp size={14} /></button>
              <button onClick={e => { e.stopPropagation(); move(i, 1) }} style={ib}><ChevronDown size={14} /></button>
              <button onClick={e => { e.stopPropagation(); remove(b.id) }} style={{ ...ib, color: C.red }}><Trash2 size={14} /></button>
            </div>
            {zone(i + 1)}
          </div>) })}
        {blocks.length === 0 && <div style={{ textAlign: 'center', padding: 40, color: C.mute, fontSize: 13 }}>Drag a block here, or click one on the left.</div>}
        <div style={{ fontSize: 11, color: C.mute, textAlign: 'center', marginTop: 6 }}>The footer with your business name and the unsubscribe link is added automatically when the email is sent.</div>
      </div>

      {/* Settings */}
      <div style={{ border: `1px solid ${C.line}`, borderRadius: 12, padding: 14 }}>
        <div style={{ ...lbl, marginBottom: 10 }}>{cur ? `${PALETTE.find(p => p.type === cur.type)?.label} settings` : 'Block settings'}</div>
        {!cur ? <div style={{ fontSize: 12, color: C.mute }}>Select a block to edit it.</div> : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {(cur.type === 'header' || cur.type === 'heading') && <div><label style={lbl}>Text</label><input style={small} value={cur.text ?? ''} maxLength={200} onChange={e => upd(cur.id, { text: e.target.value })} /></div>}
            {cur.type === 'header' && <div><label style={lbl}>Background colour</label><input type="color" value={hex(cur.color, C.navy)} onChange={e => upd(cur.id, { color: e.target.value })} style={{ width: '100%', height: 34, border: `1px solid ${C.line}`, borderRadius: 7 }} /></div>}
            {cur.type === 'text' && <div><label style={lbl}>Text</label><textarea style={{ ...small, resize: 'vertical', fontFamily: 'inherit' }} rows={7} value={cur.text ?? ''} onChange={e => upd(cur.id, { text: e.target.value })} />
              <div style={{ fontSize: 10, color: C.mute, marginTop: 3 }}>Blank line = new paragraph. Placeholders: {'{{first_name}} {{name}} {{company_name}}'}</div></div>}
            {cur.type === 'image' && <>
              <div><label style={lbl}>Image address (https)</label><input style={small} value={cur.src ?? ''} placeholder="https://…/photo.jpg" onChange={e => upd(cur.id, { src: e.target.value })} /></div>
              <div><label style={lbl}>Alt text</label><input style={small} value={cur.alt ?? ''} maxLength={200} onChange={e => upd(cur.id, { alt: e.target.value })} /></div>
              <div><label style={lbl}>Link (optional)</label><input style={small} value={cur.href ?? ''} placeholder="https://" onChange={e => upd(cur.id, { href: e.target.value })} /></div></>}
            {cur.type === 'button' && <>
              <div><label style={lbl}>Label</label><input style={small} value={cur.label ?? ''} maxLength={60} onChange={e => upd(cur.id, { label: e.target.value })} /></div>
              <div><label style={lbl}>Link</label><input style={small} value={cur.href ?? ''} placeholder="https://" onChange={e => upd(cur.id, { href: e.target.value })} /></div>
              <div><label style={lbl}>Colour</label><input type="color" value={hex(cur.color)} onChange={e => upd(cur.id, { color: e.target.value })} style={{ width: '100%', height: 34, border: `1px solid ${C.line}`, borderRadius: 7 }} /></div></>}
            {cur.type === 'social' && <>
              {(cur.links ?? []).map((l, k) => (
                <div key={k} style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr auto', gap: 5 }}>
                  <input style={small} value={l.label} placeholder="Name" onChange={e => upd(cur.id, { links: cur.links!.map((x, j) => j === k ? { ...x, label: e.target.value } : x) })} />
                  <input style={small} value={l.href} placeholder="https://" onChange={e => upd(cur.id, { links: cur.links!.map((x, j) => j === k ? { ...x, href: e.target.value } : x) })} />
                  <button onClick={() => upd(cur.id, { links: cur.links!.filter((_, j) => j !== k) })} style={ib}><Trash2 size={13} /></button>
                </div>))}
              {(cur.links?.length ?? 0) < 6 && <button onClick={() => upd(cur.id, { links: [...(cur.links ?? []), { label: '', href: 'https://' }] })} style={{ padding: 6, border: `1px dashed ${C.line}`, borderRadius: 7, background: '#fff', fontSize: 12, cursor: 'pointer' }}>+ Add link</button>}</>}
            {cur.type === 'html' && <div><label style={lbl}>HTML</label><textarea style={{ ...small, fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }} rows={8} value={cur.code ?? ''} onChange={e => upd(cur.id, { code: e.target.value })} />
              <div style={{ fontSize: 10, color: C.mute, marginTop: 3 }}>Raw HTML is checked by the content checker like the rest of the email.</div></div>}
            {alignRow}
            {cur.type === 'divider' && <div style={{ fontSize: 12, color: C.mute }}>No settings.</div>}
          </div>)}
      </div>
    </div>
  )
}

const ib: React.CSSProperties = { display: 'flex', padding: 4, background: 'none', border: 'none', cursor: 'pointer', color: C.mute }