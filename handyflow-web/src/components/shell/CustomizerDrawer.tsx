// src/components/shell/CustomizerDrawer.tsx
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Check, Lock, Monitor, Moon, Sun, X } from 'lucide-react'
import { useTheme } from '../../theme/ThemeContext'
import { BRANDS, type BrandColor } from '../../styles/brand'
import type {
  ContainerMode, SidebarMode, ThemeMode, UpdateTenantUiPreferencesRequest,
} from '../../types/uiPreferences.types'

const BRAND_KEYS = Object.keys(BRANDS) as BrandColor[]

interface Option<T extends string> { value: T; label: string; icon?: ReactNode }

const THEME_OPTIONS: Option<ThemeMode>[] = [
  { value: 'LIGHT', label: 'Light', icon: <Sun size={15} aria-hidden="true" /> },
  { value: 'DARK', label: 'Dark', icon: <Moon size={15} aria-hidden="true" /> },
  { value: 'SYSTEM', label: 'System', icon: <Monitor size={15} aria-hidden="true" /> },
]
const SIDEBAR_OPTIONS: Option<SidebarMode>[] = [
  { value: 'FULL', label: 'Full' }, { value: 'MINI', label: 'Icons only' },
]
const CONTAINER_OPTIONS: Option<ContainerMode>[] = [
  { value: 'BOXED', label: 'Boxed' }, { value: 'FULL', label: 'Full width' },
]

function Segmented<T extends string>({ label, value, options, onChange, disabled }: {
  label: string; value: T; options: Option<T>[]; onChange: (v: T) => void; disabled?: boolean
}) {
  return (
    <fieldset className="hf-field" disabled={disabled}>
      <legend>{label}</legend>
      <div className="hf-segmented" role="radiogroup" aria-label={label}>
        {options.map(o => (
          <button key={o.value} type="button" role="radio" aria-checked={value === o.value}
            className="hf-segment" onClick={() => onChange(o.value)}>
            {o.icon}{o.label}
          </button>
        ))}
      </div>
    </fieldset>
  )
}

function Swatches({ label, value, onChange, disabled, hint, theme }: {
  label: string; value: BrandColor; onChange: (v: BrandColor) => void
  disabled?: boolean; hint?: ReactNode; theme: 'light' | 'dark'
}) {
  return (
    <fieldset className="hf-field">
      <legend>{label}</legend>
      <div className="hf-swatches" role="radiogroup" aria-label={label}>
        {BRAND_KEYS.map(key => (
          <button key={key} type="button" role="radio" aria-checked={value === key}
            aria-label={BRANDS[key].label} title={BRANDS[key].label}
            className="hf-swatch" disabled={disabled}
            style={{ background: BRANDS[key][theme].primary }}
            onClick={() => onChange(key)}>
            {value === key && <Check size={16} strokeWidth={3} aria-hidden="true" />}
          </button>
        ))}
      </div>
      {hint && <p className="hf-field-hint">{hint}</p>}
    </fieldset>
  )
}

function OrganisationSettings({ saved, onSaved }: { saved: boolean; onSaved: (v: boolean) => void }) {
  const { tenant, updateTenant, resolvedTheme } = useTheme()
  // Initialised once; the parent remounts this form (via key) when the
  // server values change, so no effect is needed to resync.
  const [draft, setDraft] = useState<UpdateTenantUiPreferencesRequest>(() => ({ ...tenant }))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const dirty = JSON.stringify(draft) !== JSON.stringify({ ...tenant })
  const set = (change: Partial<UpdateTenantUiPreferencesRequest>) => { setDraft(d => ({ ...d, ...change })); onSaved(false) }

  const save = async () => {
    setSaving(true); setError(null)
    try {
      await updateTenant(draft)
      onSaved(true)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      setError(msg || 'Could not save organisation appearance. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="hf-divider-label">Organisation defaults</div>
      <Swatches label="Brand colour" value={draft.brandColor} theme={resolvedTheme}
        onChange={brandColor => set({ brandColor })} />
      <div className="hf-field">
        <label className="hf-check">
          <input type="checkbox" checked={draft.brandLocked} onChange={e => set({ brandLocked: e.target.checked })} />
          <span>Lock brand colour for everyone<br />
            <span className="hf-field-hint">Users can still choose light or dark and their layout.</span></span>
        </label>
      </div>
      <Segmented label="Default theme" value={draft.defaultTheme} options={THEME_OPTIONS} onChange={defaultTheme => set({ defaultTheme })} />
      <Segmented label="Default sidebar" value={draft.defaultSidebar} options={SIDEBAR_OPTIONS} onChange={defaultSidebar => set({ defaultSidebar })} />
      <Segmented label="Default page width" value={draft.defaultContainer} options={CONTAINER_OPTIONS} onChange={defaultContainer => set({ defaultContainer })} />
      <button type="button" className="hf-btn-primary" disabled={!dirty || saving} onClick={save}>
        {saving ? 'Saving…' : saved && !dirty ? 'Saved' : 'Save organisation defaults'}
      </button>
      {error && <p className="hf-inline-error" role="alert">{error}</p>}
      <p className="hf-field-hint">Applies to everyone who hasn't chosen their own setting.</p>
    </>
  )
}

export function CustomizerDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { prefs, resolvedTheme, updateMine, canEditTenant, isSupportSession, isSaving, saveError, user, tenant } = useTheme()
  const closeRef = useRef<HTMLButtonElement>(null)
  // Lives here, not in the form, so it survives the form's remount after a save.
  const [orgSaved, setOrgSaved] = useState(false)

  useEffect(() => {
    if (!open) return
    closeRef.current?.focus()
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  const hasOverrides = !!user && (user.theme || user.sidebar || user.container || user.brandColor)
  const status = isSupportSession ? 'Support session: changes preview only and are not saved.'
    : saveError ? saveError
    : isSaving ? 'Saving…' : 'Saved to your account, on every device.'

  return (
    <>
      <div className="hf-backdrop hf-drawer-backdrop" onClick={onClose} aria-hidden="true" />
      <div className="hf-drawer" role="dialog" aria-modal="true" aria-labelledby="hf-customizer-title">
        <div className="hf-drawer-header">
          <h2 id="hf-customizer-title" className="hf-drawer-title">Appearance</h2>
          <button ref={closeRef} type="button" className="hf-icon-btn" onClick={onClose} aria-label="Close appearance settings">
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <div className="hf-drawer-body">
          <Segmented label="Theme" value={prefs.theme} options={THEME_OPTIONS} onChange={theme => updateMine({ theme })} />
          <Swatches label="Brand colour" value={prefs.brandColor} theme={resolvedTheme}
            disabled={prefs.brandLocked} onChange={brandColor => updateMine({ brandColor })}
            hint={prefs.brandLocked
              ? <><Lock size={12} aria-hidden="true" style={{ verticalAlign: -1 }} /> Set by your organisation.</>
              : undefined} />
          <Segmented label="Sidebar" value={prefs.sidebar} options={SIDEBAR_OPTIONS} onChange={sidebar => updateMine({ sidebar })} />
          <Segmented label="Page width" value={prefs.container} options={CONTAINER_OPTIONS} onChange={container => updateMine({ container })} />

          {hasOverrides && (
            <button type="button" className="hf-menu-item" style={{ padding: '4px 0', color: 'var(--hf-primary-text)' }}
              onClick={() => updateMine({ theme: null, sidebar: null, container: null, brandColor: null })}>
              Reset to organisation defaults
            </button>
          )}

          {canEditTenant && !isSupportSession && <OrganisationSettings key={JSON.stringify(tenant)} saved={orgSaved} onSaved={setOrgSaved} />}
        </div>

        <div className="hf-drawer-footer" role="status" aria-live="polite"
          style={saveError ? { color: 'var(--hf-danger-text)' } : undefined}>
          {status}
        </div>
      </div>
    </>
  )
}
