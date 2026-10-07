// Proper in-app dialogs to replace window.confirm / window.prompt / window.alert.
//
//   const { confirm, prompt, notify, dialogs } = useDialogs()
//   if (await confirm({ title: "Discard draft?", body: "...", confirmLabel: "Discard", danger: true })) ...
//   const note = await prompt({ title: "Note for the doctor", optional: true })   // string | null (null = cancelled)
//   await notify({ title: "Not supported", body: "..." })
//   return <>{...page}{dialogs}</>
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react"
import { AlertTriangle, X } from "lucide-react"

export interface ConfirmOptions { title: string; body?: ReactNode; confirmLabel?: string; cancelLabel?: string; danger?: boolean }
export interface PromptOptions extends ConfirmOptions {
  label?: string; placeholder?: string; initial?: string
  /** When false (default) the answer must not be blank. */
  optional?: boolean; multiline?: boolean
}
export interface NotifyOptions { title: string; body?: ReactNode; okLabel?: string }

/** The trimmed value, or an error message when a required answer is blank. */
export function promptProblem(value: string, optional?: boolean): string | null {
  return !optional && !value.trim() ? "Please enter a value." : null
}

type Pending =
  | { kind: "confirm"; opts: ConfirmOptions; done: (v: boolean) => void }
  | { kind: "prompt"; opts: PromptOptions; done: (v: string | null) => void }
  | { kind: "notify"; opts: NotifyOptions; done: () => void }

export function useDialogs() {
  const [pending, setPending] = useState<Pending | null>(null)
  const confirm = useCallback((opts: ConfirmOptions) => new Promise<boolean>(res => setPending({ kind: "confirm", opts, done: res })), [])
  const prompt = useCallback((opts: PromptOptions) => new Promise<string | null>(res => setPending({ kind: "prompt", opts, done: res })), [])
  const notify = useCallback((opts: NotifyOptions) => new Promise<void>(res => setPending({ kind: "notify", opts, done: res })), [])
  const close = () => setPending(null)

  let dialogs: ReactNode = null
  if (pending?.kind === "confirm")
    dialogs = <DialogFrame title={pending.opts.title} onCancel={() => { pending.done(false); close() }}>
      <ConfirmBody opts={pending.opts} onCancel={() => { pending.done(false); close() }} onOk={() => { pending.done(true); close() }} />
    </DialogFrame>
  else if (pending?.kind === "prompt")
    dialogs = <DialogFrame title={pending.opts.title} onCancel={() => { pending.done(null); close() }}>
      <PromptBody opts={pending.opts} onCancel={() => { pending.done(null); close() }} onOk={v => { pending.done(v); close() }} />
    </DialogFrame>
  else if (pending?.kind === "notify")
    dialogs = <DialogFrame title={pending.opts.title} onCancel={() => { pending.done(); close() }}>
      {pending.opts.body && <div style={bodyStyle}>{pending.opts.body}</div>}
      <div style={footStyle}><button autoFocus style={primaryBtn(false)} onClick={() => { pending.done(); close() }}>{pending.opts.okLabel ?? "OK"}</button></div>
    </DialogFrame>
  return { confirm, prompt, notify, dialogs }
}

function DialogFrame({ title, onCancel, children }: { title: string; onCancel: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") { e.stopPropagation(); onCancel() } }
    document.addEventListener("keydown", onKey, true)   // capture: a modal underneath must not also close
    return () => document.removeEventListener("keydown", onKey, true)
  }, [onCancel])
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center",
      justifyContent: "center", zIndex: 2000, backdropFilter: "blur(3px)", padding: 16 }}
      onMouseDown={e => { if (e.target === e.currentTarget) onCancel() }}>
      <div role="dialog" aria-modal="true" aria-label={title}
        style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 24, width: 440, maxWidth: "100%",
          boxShadow: "0 24px 64px rgba(0,0,0,0.22)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
          <button aria-label="Close" onClick={onCancel} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", display: "flex" }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

function ConfirmBody({ opts, onCancel, onOk }: { opts: ConfirmOptions; onCancel: () => void; onOk: () => void }) {
  return <>
    {opts.body && <div style={{ ...bodyStyle, display: "flex", gap: 10 }}>
      {opts.danger && <AlertTriangle size={18} style={{ color: "var(--hf-danger-text)", flexShrink: 0, marginTop: 1 }} />}
      <div>{opts.body}</div></div>}
    <div style={footStyle}>
      <button style={cancelBtn} onClick={onCancel}>{opts.cancelLabel ?? "Cancel"}</button>
      <button autoFocus style={primaryBtn(!!opts.danger)} onClick={onOk}>{opts.confirmLabel ?? "Confirm"}</button>
    </div>
  </>
}

function PromptBody({ opts, onCancel, onOk }: { opts: PromptOptions; onCancel: () => void; onOk: (v: string) => void }) {
  const [value, setValue] = useState(opts.initial ?? "")
  const [touched, setTouched] = useState(false)
  const ref = useRef<HTMLInputElement & HTMLTextAreaElement>(null)
  useEffect(() => { ref.current?.focus() }, [])
  const problem = touched ? promptProblem(value, opts.optional) : null
  const submit = () => { setTouched(true); if (!promptProblem(value, opts.optional)) onOk(value.trim()) }
  const common = {
    ref, value, "aria-label": opts.label ?? opts.title, placeholder: opts.placeholder,
    onChange: (e: { target: { value: string } }) => setValue(e.target.value),
    style: { width: "100%", boxSizing: "border-box" as const, padding: "9px 12px", fontSize: 14, borderRadius: 8, fontFamily: "inherit",
      border: `1.5px solid ${problem ? "var(--hf-danger)" : "var(--hf-border)"}`, background: "var(--hf-surface)", color: "var(--hf-text)" },
  }
  return <>
    {opts.body && <div style={bodyStyle}>{opts.body}</div>}
    {opts.label && <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 6, color: "var(--hf-text-secondary)" }}>{opts.label}{opts.optional ? " (optional)" : ""}</label>}
    {opts.multiline
      ? <textarea {...common} rows={3} />
      : <input {...common} onKeyDown={e => { if (e.key === "Enter") submit() }} />}
    {problem && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{problem}</div>}
    <div style={footStyle}>
      <button style={cancelBtn} onClick={onCancel}>{opts.cancelLabel ?? "Cancel"}</button>
      <button style={primaryBtn(!!opts.danger)} onClick={submit}>{opts.confirmLabel ?? "OK"}</button>
    </div>
  </>
}

const bodyStyle = { fontSize: 14, color: "var(--hf-text-tertiary)", lineHeight: 1.5, marginBottom: 14 } as const
const footStyle = { display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 18 } as const
const cancelBtn = { padding: "9px 16px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 14, fontWeight: 600, cursor: "pointer" } as const
const primaryBtn = (danger: boolean) => ({ padding: "9px 16px", border: "none", borderRadius: 8, background: danger ? "var(--hf-danger)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 600, cursor: "pointer" }) as const
