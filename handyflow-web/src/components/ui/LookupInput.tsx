// src/components/ui/LookupInput.tsx
//
// A text field with a pick-list. Suggestions appear as you type; leaving the field snaps a match to the list's own spelling
// ("sars" becomes "SARS"); something not on the list is kept as typed and marked "Custom value", so a typo is visible, not silent.
import { useId } from "react"
import { type LookupOption, settle, isCustom } from "../../lookups/southAfrica"

interface Props extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "onChange" | "value" | "list"> {
  value: string
  onChange: (value: string) => void
  options: LookupOption[]
}

export default function LookupInput({ value, onChange, options, id, style, ...rest }: Props) {
  const auto = useId()
  const listId = `${id ?? auto}-list`
  return (
    <>
      <input {...rest} id={id} list={listId} value={value} autoComplete="off" style={style}
        onChange={e => onChange(e.target.value)}
        onBlur={e => { const next = settle(e.target.value, options); if (next !== value) onChange(next); rest.onBlur?.(e) }} />
      <datalist id={listId}>{options.map(o => <option key={o.value} value={o.value}>{o.hint}</option>)}</datalist>
      {isCustom(value, options) && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>Custom value, not on the list. Check the spelling.</div>}
    </>
  )
}
