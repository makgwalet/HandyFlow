// src/components/ui/LookupInput.tsx
//
// A text field with a pick-list. Suggestions appear as you type; leaving the field snaps a match to the list's own spelling
// ("sars" becomes "SARS"); something not on the list is kept as typed and marked "Custom value", so a typo is visible, not silent.
// With `list` set, the company's own additions to that list are included, and a person who can manage compliance may add a custom
// value to the list, or remove one the company added.
import { useId, useState } from "react"
import { type LookupOption, settle, isCustom, canonical } from "../../lookups/southAfrica"
import { mergeOptions, useLookupActions, useTenantLookups } from "../../lookups/tenantLookups"
import { usePermission } from "../../hooks/usePermission"

interface Props extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "onChange" | "value" | "list"> {
  value: string
  onChange: (value: string) => void
  options: LookupOption[]
  /** The pick-list's name (for example "DOCUMENT_TYPES"); turns on the company's own additions. */
  list?: string
}

const link: React.CSSProperties = { background: "none", border: "none", padding: 0, marginLeft: 6, cursor: "pointer", fontSize: 11, fontWeight: 700, color: "var(--hf-sky-text-strong, var(--hf-text))", textDecoration: "underline" }

export default function LookupInput({ value, onChange, options: base, list, id, style, ...rest }: Props) {
  const auto = useId()
  const listId = `${id ?? auto}-list`
  const extras = useTenantLookups(!!list)
  const canManage = usePermission("COMPLIANCE_MANAGE") || usePermission("COMPLIANCE_ADMIN")
  const actions = useLookupActions()
  const [problem, setProblem] = useState<string | null>(null)
  const options = mergeOptions(base, list, extras)
  const custom = isCustom(value, options)
  const added = options.find(o => o.id && o.value === canonical(value, options))

  async function addIt() {
    setProblem(null)
    try { onChange(await actions.add(list!, value)) } catch { setProblem("Could not add it to your list. Try again.") }
  }
  async function removeIt() {
    setProblem(null)
    try { await actions.remove(added!.id!) } catch { setProblem("Could not remove it from your list. Try again.") }
  }

  return (
    <>
      <input {...rest} id={id} list={listId} value={value} autoComplete="off" style={style}
        onChange={e => onChange(e.target.value)}
        onBlur={e => { const next = settle(e.target.value, options); if (next !== value) onChange(next); rest.onBlur?.(e) }} />
      <datalist id={listId}>{options.map(o => <option key={o.value} value={o.value}>{o.hint}</option>)}</datalist>
      {custom && (
        <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>
          Custom value, not on the list. Check the spelling.
          {list && canManage && <button type="button" onClick={addIt} style={link}>Add to my list</button>}
        </div>
      )}
      {!custom && added && list && canManage && (
        <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>
          On your company's list.<button type="button" onClick={removeIt} style={link}>Remove from my list</button>
        </div>
      )}
      {problem && <div role="alert" style={{ fontSize: 11, color: "var(--hf-danger-text)", marginTop: 3 }}>{problem}</div>}
    </>
  )
}
