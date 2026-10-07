import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import ModalShell from "./ModalShell"

afterEach(cleanup)

describe("ModalShell", () => {
  it("is a labelled modal dialog with the footer pinned", () => {
    render(<ModalShell title="Book" onClose={() => {}} footer={<button>Save</button>}><input aria-label="Name" /></ModalShell>)
    const d = screen.getByRole("dialog", { name: "Book" })
    expect(d.getAttribute("aria-modal")).toBe("true")
    expect(screen.getByText("Save")).toBeTruthy()
  })
  it("puts the cursor in the first field", () => {
    render(<ModalShell title="Book" onClose={() => {}}><input aria-label="Name" /></ModalShell>)
    expect(document.activeElement).toBe(screen.getByLabelText("Name"))
  })
  it("closes on Escape and on the X, but not on a backdrop click", () => {
    const onClose = vi.fn()
    const { container } = render(<ModalShell title="Book" onClose={onClose}>x</ModalShell>)
    fireEvent.mouseDown(container.firstChild as Element); fireEvent.click(container.firstChild as Element)
    expect(onClose).not.toHaveBeenCalled()
    fireEvent.keyDown(document, { key: "Escape" })
    expect(onClose).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByLabelText("Close"))
    expect(onClose).toHaveBeenCalledTimes(2)
  })
  it("locks page scroll while open and restores focus and scroll on close", () => {
    const opener = document.createElement("button"); document.body.appendChild(opener); opener.focus()
    const { unmount } = render(<ModalShell title="Book" onClose={() => {}}>x</ModalShell>)
    expect(document.body.style.overflow).toBe("hidden")
    unmount()
    expect(document.body.style.overflow).not.toBe("hidden")
    expect(document.activeElement).toBe(opener)
    opener.remove()
  })
})
