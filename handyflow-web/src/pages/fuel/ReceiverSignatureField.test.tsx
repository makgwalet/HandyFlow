import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import ReceiverSignatureField from "./ReceiverSignatureField"

const ctx = { lineWidth: 0, lineCap: "", strokeStyle: "", beginPath: vi.fn(), moveTo: vi.fn(), lineTo: vi.fn(), stroke: vi.fn(), clearRect: vi.fn() }

describe("ReceiverSignatureField", () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  function stub() {
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, "toDataURL").mockReturnValue("data:image/png;base64,AAAA")
  }

  it("gives back a PNG data URI when the receiver lifts off after signing", () => {
    stub(); const onChange = vi.fn()
    render(<ReceiverSignatureField onChange={onChange} />)
    const pad = screen.getByLabelText("Receiver signature")
    fireEvent.pointerDown(pad, { clientX: 10, clientY: 10 }); fireEvent.pointerMove(pad, { clientX: 40, clientY: 30 }); fireEvent.pointerUp(pad)
    expect(onChange).toHaveBeenCalledWith("data:image/png;base64,AAAA")
  })

  it("clears the signature and says so", () => {
    stub(); const onChange = vi.fn()
    render(<ReceiverSignatureField onChange={onChange} />)
    fireEvent.click(screen.getByRole("button", { name: "Clear signature" }))
    expect(onChange).toHaveBeenLastCalledWith("")
  })

  it("does not draw while disabled", () => {
    stub(); const onChange = vi.fn()
    render(<ReceiverSignatureField onChange={onChange} disabled />)
    const pad = screen.getByLabelText("Receiver signature")
    fireEvent.pointerDown(pad, { clientX: 10, clientY: 10 }); fireEvent.pointerUp(pad)
    expect(onChange).not.toHaveBeenCalled()
  })
})
