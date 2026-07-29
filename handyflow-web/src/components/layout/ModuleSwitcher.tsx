// src/components/layout/ModuleSwitcher.tsx
// FIX: "navbar cluttered with too many modules" — the horizontal nav
// rendered every subscribed module as an inline pill with no limit,
// becoming an unusable scrolling strip past ~8 modules (confirmed via
// screenshot: 20 modules crammed into one row). This replaces that with
// a Google-style app launcher: a small "Apps" trigger opens a searchable
// grid, and the user pins the handful they actually use to the topbar.
import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Grid3x3, Search, Pin, PinOff, X } from 'lucide-react'

export interface ModuleNavItem {
  key: string
  icon: React.ElementType
  label: string
  route: string
}

interface ModuleSwitcherProps {
  modules: ModuleNavItem[]
  pinnedKeys: string[]
  onTogglePin: (key: string) => void
  currentPath: string
}

export function ModuleSwitcher({ modules, pinnedKeys, onTogglePin, currentPath }: ModuleSwitcherProps) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const ref = useRef<HTMLDivElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) { setOpen(false); setSearch('') }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  useEffect(() => {
    if (open) setTimeout(() => searchRef.current?.focus(), 50)
  }, [open])

  const filtered = modules.filter(m => m.label.toLowerCase().includes(search.toLowerCase()))

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button onClick={() => setOpen(o => !o)}
        title="All modules"
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: open ? 'rgba(255,255,255,0.15)' : 'rgba(255,255,255,0.08)',
          border: '1px solid rgba(255,255,255,0.12)', borderRadius: 8,
          width: 30, height: 30, cursor: 'pointer', color: 'white', flexShrink: 0,
        }}>
        <Grid3x3 size={15} />
      </button>

      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 8px)', left: 0, width: 360,
          background: 'white', border: '1px solid #E2E8F0', borderRadius: 14,
          boxShadow: '0 12px 40px rgba(0,0,0,0.18)', zIndex: 300, overflow: 'hidden',
        }}>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid #F1F5F9' }}>
            <div style={{ position: 'relative' }}>
              <Search size={14} color="#94A3B8" style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }} />
              <input ref={searchRef} value={search} onChange={e => setSearch(e.target.value)}
                placeholder="Search modules..."
                style={{ width: '100%', boxSizing: 'border-box', padding: '8px 10px 8px 32px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none' }} />
            </div>
          </div>

          {pinnedKeys.length === 0 && !search && (
            <div style={{ padding: '10px 16px', fontSize: 11.5, color: '#94A3B8', background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
              Click the pin on any module to keep it in your topbar.
            </div>
          )}

          <div style={{ maxHeight: 360, overflowY: 'auto', padding: 10 }}>
            {filtered.length === 0 ? (
              <div style={{ padding: '24px 16px', textAlign: 'center', fontSize: 13, color: '#94A3B8' }}>No modules match "{search}"</div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                {filtered.map(m => {
                  const isPinned = pinnedKeys.includes(m.key)
                  const isActive = currentPath.startsWith(m.route)
                  return (
                    <div key={m.key} onClick={() => { navigate(m.route); setOpen(false); setSearch('') }}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 8, padding: '9px 8px', borderRadius: 9,
                        cursor: 'pointer', background: isActive ? '#EFF6FF' : 'transparent', position: 'relative',
                      }}
                      onMouseEnter={e => { if (!isActive) e.currentTarget.style.background = '#F8FAFC' }}
                      onMouseLeave={e => { if (!isActive) e.currentTarget.style.background = 'transparent' }}>
                      <div style={{ width: 28, height: 28, borderRadius: 7, background: isActive ? '#1B3A6B' : '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                        <m.icon size={14} color={isActive ? 'white' : '#64748B'} />
                      </div>
                      <span style={{ fontSize: 12.5, fontWeight: 500, color: '#0F172A', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{m.label}</span>
                      <button onClick={e => { e.stopPropagation(); onTogglePin(m.key) }}
                        title={isPinned ? 'Unpin' : 'Pin to topbar'}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex', padding: 3, color: isPinned ? '#0D9488' : '#CBD5E1', flexShrink: 0 }}>
                        {isPinned ? <Pin size={13} fill="currentColor" /> : <Pin size={13} />}
                      </button>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
