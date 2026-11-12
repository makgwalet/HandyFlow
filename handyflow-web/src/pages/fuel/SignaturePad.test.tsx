import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import SignaturePad from "./SignaturePad"

const ctx = { lineWidth: 0, lineCap: "", strokeStyle: "", beginPath: vi.fn(), moveTo: vi.fn(), lineTo: vi.fn(), stroke: vi.fn(), clearRect: vi.fn() }

describe("SignaturePad", () => {
  beforeEach(() => {
    Object.values(ctx).forEach(f => typeof f === "function" && (f as any).mockClear?.())
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(ctx as any)
    vi.spyOn(HTMLCanvasElement.prototype, "toDataURL").mockReturnValue("data:image/png;base64,sig")
  })
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it("will not save an empty pad", () => {
    const onSave = vi.fn(); render(<SignaturePad onSave={onSave} onCancel={() => {}} />)
    fireEvent.click(screen.getByText("Save signature"))
    expect(screen.getByRole("alert").textContent).toMatch(/sign first/); expect(onSave).not.toHaveBeenCalled()
  })
  it("saves what was drawn as a PNG", () => {
    const onSave = vi.fn(); render(<SignaturePad onSave={onSave} onCancel={() => {}} />)
    const c = screen.getByLabelText("Signature pad")
    fireEvent.pointerDown(c, { clientX: 5, clientY: 5, pointerId: 1 }); fireEvent.pointerMove(c, { clientX: 40, clientY: 30, pointerId: 1 }); fireEvent.pointerUp(c, { pointerId: 1 })
    expect(ctx.lineTo).toHaveBeenCalled()
    fireEvent.click(screen.getByText("Save signature"))
    expect(onSave).toHaveBeenCalledWith("data:image/png;base64,sig")
  })
  it("clear wipes the pad and it must be signed again", () => {
    const onSave = vi.fn(); render(<SignaturePad onSave={onSave} onCancel={() => {}} />)
    const c = screen.getByLabelText("Signature pad")
    fireEvent.pointerDown(c, { clientX: 5, clientY: 5, pointerId: 1 }); fireEvent.pointerMove(c, { clientX: 40, clientY: 30, pointerId: 1 })
    fireEvent.click(screen.getByText("Clear")); expect(ctx.clearRect).toHaveBeenCalled()
    fireEvent.click(screen.getByText("Save signature")); expect(onSave).not.toHaveBeenCalled()
  })
  it("moving without pressing draws nothing", () => {
    render(<SignaturePad onSave={() => {}} onCancel={() => {}} />)
    fireEvent.pointerMove(screen.getByLabelText("Signature pad"), { clientX: 40, clientY: 30 })
    expect(ctx.lineTo).not.toHaveBeenCalled()
  })
})
