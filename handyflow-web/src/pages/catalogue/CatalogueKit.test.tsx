import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent } from "@testing-library/react"
import { Modal } from "./CatalogueKit"

afterEach(cleanup)

describe("Modal", () => {
  it("is named by its title", () => {
    render(<Modal title="Edit unit" onClose={() => undefined}><input aria-label="Name" /></Modal>)
    expect(screen.getByRole("dialog", { name: "Edit unit" })).toBeTruthy()
  })
  it("puts focus on the first field, not on Close", () => {
    render(<Modal title="T" onClose={() => undefined}><input aria-label="Name" /></Modal>)
    expect(document.activeElement).toBe(screen.getByLabelText("Name"))
  })
  it("closes on Escape and on a press outside the box, but not inside it", () => {
    const onClose = vi.fn()
    render(<Modal title="T" onClose={onClose}><input aria-label="Name" /></Modal>)
    fireEvent.mouseDown(screen.getByLabelText("Name"))
    expect(onClose).not.toHaveBeenCalled()
    fireEvent.keyDown(document, { key: "Escape" })
    expect(onClose).toHaveBeenCalledTimes(1)
    fireEvent.mouseDown(screen.getByRole("dialog").parentElement as HTMLElement)
    expect(onClose).toHaveBeenCalledTimes(2)
  })
  it("keeps Tab inside the dialog", () => {
    render(<Modal title="T" onClose={() => undefined}><input aria-label="Name" /><button type="button">Save</button></Modal>)
    screen.getByRole("button", { name: "Save" }).focus()
    fireEvent.keyDown(document, { key: "Tab" })
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "Close" }))
    fireEvent.keyDown(document, { key: "Tab", shiftKey: true })
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "Save" }))
  })
  it("locks page scrolling while open and gives focus back afterwards", () => {
    const opener = document.createElement("button"); document.body.appendChild(opener); opener.focus()
    const { unmount } = render(<Modal title="T" onClose={() => undefined}><input aria-label="Name" /></Modal>)
    expect(document.body.style.overflow).toBe("hidden")
    unmount()
    expect(document.body.style.overflow).toBe("")
    expect(document.activeElement).toBe(opener)
    opener.remove()
  })
})
