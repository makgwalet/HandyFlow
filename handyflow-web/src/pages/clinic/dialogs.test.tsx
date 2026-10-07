import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { useDialogs, promptProblem } from "./dialogs"

afterEach(cleanup)

function Harness({ onResult }: { onResult: (v: unknown) => void }) {
  const { confirm, prompt, notify, dialogs } = useDialogs()
  return <>
    <button onClick={async () => onResult(await confirm({ title: "Discard draft?", body: "Gone for good", confirmLabel: "Discard", danger: true }))}>c</button>
    <button onClick={async () => onResult(await prompt({ title: "Note", label: "Reason" }))}>p</button>
    <button onClick={async () => onResult(await prompt({ title: "Optional", optional: true }))}>po</button>
    <button onClick={async () => { await notify({ title: "FYI", body: "Heads up" }); onResult("ok") }}>n</button>
    {dialogs}
  </>
}
const setup = () => { const r = vi.fn(); render(<Harness onResult={r} />); return r }

describe("promptProblem", () => {
  it("only blocks blank answers when required", () => {
    expect(promptProblem("  ", false)).toBeTruthy()
    expect(promptProblem("  ", true)).toBeNull()
    expect(promptProblem("x")).toBeNull()
  })
})

describe("useDialogs", () => {
  it("confirm resolves true on confirm and false on cancel", async () => {
    const r = setup()
    fireEvent.click(screen.getByText("c"))
    expect(screen.getByRole("dialog")).toBeTruthy()
    fireEvent.click(screen.getByText("Discard"))
    await waitFor(() => expect(r).toHaveBeenLastCalledWith(true))
    expect(screen.queryByRole("dialog")).toBeNull()
    fireEvent.click(screen.getByText("c"))
    fireEvent.click(screen.getByText("Cancel"))
    await waitFor(() => expect(r).toHaveBeenLastCalledWith(false))
  })
  it("Escape cancels", async () => {
    const r = setup()
    fireEvent.click(screen.getByText("c"))
    fireEvent.keyDown(document, { key: "Escape" })
    await waitFor(() => expect(r).toHaveBeenCalledWith(false))
  })
  it("required prompt refuses a blank answer, then returns the trimmed text", async () => {
    const r = setup()
    fireEvent.click(screen.getByText("p"))
    fireEvent.click(screen.getByText("OK"))
    expect(screen.getByRole("alert")).toBeTruthy()
    expect(r).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "  wrong patient " } })
    fireEvent.click(screen.getByText("OK"))
    await waitFor(() => expect(r).toHaveBeenCalledWith("wrong patient"))
  })
  it("optional prompt accepts blank and cancel returns null", async () => {
    const r = setup()
    fireEvent.click(screen.getByText("po"))
    fireEvent.click(screen.getByText("OK"))
    await waitFor(() => expect(r).toHaveBeenLastCalledWith(""))
    fireEvent.click(screen.getByText("po"))
    fireEvent.click(screen.getByText("Cancel"))
    await waitFor(() => expect(r).toHaveBeenLastCalledWith(null))
  })
  it("notify resolves when dismissed", async () => {
    const r = setup()
    fireEvent.click(screen.getByText("n"))
    fireEvent.click(screen.getByText("OK"))
    await waitFor(() => expect(r).toHaveBeenCalledWith("ok"))
  })
})
