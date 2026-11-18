// src/pages/contracting/RichTextEditor.tsx
// A small rich-text editor for contract text: headings, bold, italic, underline, lists, quotes, rules and tables. It produces the
// same HTML subset the server keeps (anything else is removed there anyway). "HTML" switches to the raw markup, which is the way to
// keep markup the editor cannot show, such as div or span wrappers.
import { useState, type CSSProperties, type ReactNode } from 'react'
import { useEditor, EditorContent, type Editor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { Table, TableRow, TableCell, TableHeader } from '@tiptap/extension-table'
import { normalizeEditorHtml } from './richText'

const bar: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 4, padding: '6px 8px', borderBottom: '1px solid var(--hf-border)', background: 'var(--hf-surface-muted)' }
const tb = (active = false): CSSProperties => ({
  padding: '3px 9px', border: '1px solid var(--hf-border)', borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: 'pointer',
  background: active ? 'var(--hf-primary)' : 'var(--hf-surface)', color: active ? 'var(--hf-text-on-solid)' : 'var(--hf-text-secondary)',
})

function Button({ label, title, active, disabled, onClick }: { label: ReactNode; title: string; active?: boolean; disabled?: boolean; onClick: () => void }) {
  return <button type="button" title={title} aria-label={title} aria-pressed={active} disabled={disabled} onMouseDown={e => e.preventDefault()} onClick={onClick}
    style={{ ...tb(active), opacity: disabled ? 0.45 : 1 }}>{label}</button>
}

function Toolbar({ editor }: { editor: Editor }) {
  const c = () => editor.chain().focus()
  const inTable = editor.isActive('table')
  return (
    <div style={bar}>
      <Button label="B" title="Bold" active={editor.isActive('bold')} onClick={() => c().toggleBold().run()} />
      <Button label={<em>I</em>} title="Italic" active={editor.isActive('italic')} onClick={() => c().toggleItalic().run()} />
      <Button label={<u>U</u>} title="Underline" active={editor.isActive('underline')} onClick={() => c().toggleUnderline().run()} />
      <span style={{ width: 8 }} />
      <Button label="H1" title="Heading 1" active={editor.isActive('heading', { level: 1 })} onClick={() => c().toggleHeading({ level: 1 }).run()} />
      <Button label="H2" title="Heading 2" active={editor.isActive('heading', { level: 2 })} onClick={() => c().toggleHeading({ level: 2 }).run()} />
      <Button label="H3" title="Heading 3" active={editor.isActive('heading', { level: 3 })} onClick={() => c().toggleHeading({ level: 3 }).run()} />
      <Button label="¶" title="Paragraph" active={editor.isActive('paragraph')} onClick={() => c().setParagraph().run()} />
      <span style={{ width: 8 }} />
      <Button label="• List" title="Bulleted list" active={editor.isActive('bulletList')} onClick={() => c().toggleBulletList().run()} />
      <Button label="1. List" title="Numbered list" active={editor.isActive('orderedList')} onClick={() => c().toggleOrderedList().run()} />
      <Button label="❝" title="Quote" active={editor.isActive('blockquote')} onClick={() => c().toggleBlockquote().run()} />
      <Button label="―" title="Horizontal rule" onClick={() => c().setHorizontalRule().run()} />
      <span style={{ width: 8 }} />
      <Button label="Table" title="Insert a 3 by 3 table" onClick={() => c().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()} />
      <Button label="+Row" title="Add a row below" disabled={!inTable} onClick={() => c().addRowAfter().run()} />
      <Button label="+Col" title="Add a column after" disabled={!inTable} onClick={() => c().addColumnAfter().run()} />
      <Button label="−Row" title="Delete the row" disabled={!inTable} onClick={() => c().deleteRow().run()} />
      <Button label="−Col" title="Delete the column" disabled={!inTable} onClick={() => c().deleteColumn().run()} />
      <Button label="✕Table" title="Delete the table" disabled={!inTable} onClick={() => c().deleteTable().run()} />
      <span style={{ width: 8 }} />
      <Button label="↶" title="Undo" disabled={!editor.can().undo()} onClick={() => c().undo().run()} />
      <Button label="↷" title="Redo" disabled={!editor.can().redo()} onClick={() => c().redo().run()} />
    </div>
  )
}

export function RichTextEditor({ value, onChange, minHeight = 220 }: { value: string; onChange: (html: string) => void; minHeight?: number }) {
  const [raw, setRaw] = useState(false)

  // The editor starts from `value` and then owns the text; callers remount it (a new key) to load different text.
  const editor = useEditor({
    extensions: [
      // Only what the server keeps: no code, strike or links.
      StarterKit.configure({ code: false, codeBlock: false, strike: false, link: false, heading: { levels: [1, 2, 3] } }),
      Table, TableRow, TableHeader, TableCell,
    ],
    content: value,
    onUpdate: ({ editor: e }) => onChange(normalizeEditorHtml(e.getHTML())),
    editorProps: { attributes: { style: `min-height:${minHeight}px;padding:12px 14px;outline:none;font-size:14px;line-height:1.7;color:var(--hf-text-secondary)` } },
  })

  const toggleRaw = () => {
    // Leaving the HTML view loads whatever was typed there back into the editor.
    if (raw && editor) editor.commands.setContent(value, { emitUpdate: false })
    setRaw(!raw)
  }

  return (
    <div style={{ border: '1.5px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '4px 8px', borderBottom: raw ? '1px solid var(--hf-border)' : 'none', background: 'var(--hf-surface-muted)' }}>
        <button type="button" onClick={toggleRaw} style={tb(raw)}>{raw ? 'Back to editor' : 'HTML'}</button>
      </div>
      {raw ? (
        <textarea value={value} onChange={e => onChange(e.target.value)} rows={12} aria-label="HTML"
          style={{ width: '100%', boxSizing: 'border-box', border: 'none', padding: 12, fontFamily: 'monospace', fontSize: 12, lineHeight: 1.6, background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', resize: 'vertical' }} />
      ) : editor ? (
        <>
          <Toolbar editor={editor} />
          <EditorContent editor={editor} />
        </>
      ) : null}
    </div>
  )
}
