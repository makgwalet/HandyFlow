import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DriverTab from "./DriverTab"
import { readQueue } from "./offlineQueue"
import * as capture from "./evidenceCapture"

const get = vi.fn(); const post = vi.fn()
const photo = { dataUrl: "data:image/jpeg;base64,aGk=", name: "IMG_1.jpg" }
const pick = (name = "Photo file", f = new File(["x"], "IMG_1.jpg", { type: "image/jpeg" })) => fireEvent.change(screen.getByLabelText(name), { target: { files: [f] } })
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const load = (o: any = {}) => ({ id: "d1", status: "IN_TRANSIT", litresOrdered: 12000, fuelType: "DIESEL", scheduledAt: "2026-11-12T08:00:00Z", vehicleReg: "GP 12 AB", deliveryAddress: { street: "Gate 3" }, ...o })
let loads: any[]
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><DriverTab /></QueryClientProvider>)
}

describe("DriverTab", () => {
  beforeEach(() => {
    localStorage.clear(); get.mockReset(); post.mockReset()
    loads = [load()]
    get.mockImplementation(async () => ({ data: { data: loads } }))
    post.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(() => cleanup())

  it("starts a scheduled load", async () => {
    loads = [load({ status: "SCHEDULED" })]
    mount()
    await waitFor(() => screen.getByText("Start trip"))
    fireEvent.click(screen.getByText("Start trip"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/driver/loads/d1/dispatch", undefined))
  })

  it("a start with no signal is kept on the phone and sent when back online", async () => {
    loads = [load({ status: "SCHEDULED" })]
    post.mockRejectedValueOnce(new Error("Network Error"))
    mount()
    await waitFor(() => screen.getByText("Start trip"))
    fireEvent.click(screen.getByText("Start trip"))
    await waitFor(() => expect(readQueue()).toHaveLength(1))
    expect(readQueue()[0].kind).toBe("START")
    expect(screen.getByText(/Saved on this phone \(1\)/)).toBeTruthy()
    window.dispatchEvent(new Event("online"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(readQueue()).toHaveLength(0))
  })

  it("records the phone's location as GPS evidence with a request id", async () => {
    const getCurrentPosition = vi.fn((ok: any) => ok({ coords: { latitude: -26.2041234, longitude: 28.0473, accuracy: 12.4 } }))
    Object.defineProperty(navigator, "geolocation", { value: { getCurrentPosition }, configurable: true })
    mount()
    await waitFor(() => screen.getByText("Record location"))
    fireEvent.click(screen.getByText("Record location"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/fuel/driver/loads/d1/evidence")
    expect(body).toMatchObject({ kind: "GPS", latitude: -26.204123, longitude: 28.0473, accuracyM: 12 }); expect(typeof body.clientRequestId).toBe("string")
  })

  it("a refused saved item stays listed with Retry and Discard", async () => {
    localStorage.setItem("fuel-driver-queue-v2", JSON.stringify([{ id: "q1", kind: "COMPLETE", deliveryId: "d1", path: "/api/v1/fuel/driver/loads/d1/complete", body: {}, queuedAt: "2026-11-12T08:00:00Z", status: "PENDING", message: null, attempts: 0, label: "Delivery" }]))
    post.mockRejectedValueOnce({ response: { data: { message: "Delivery is cancelled" } } })
    mount()
    await waitFor(() => screen.getByText(/Refused: Delivery is cancelled/))
    fireEvent.click(screen.getByText("Discard"))
    await waitFor(() => expect(readQueue()).toHaveLength(0))
  })

  it("needs litres and a receiver, then sends one request with a client id", async () => {
    mount()
    await waitFor(() => screen.getByText("Record delivery"))
    fireEvent.click(screen.getByText("Record delivery"))
    fireEvent.change(screen.getByLabelText("Litres delivered"), { target: { value: "" } })
    fireEvent.click(screen.getByText("Confirm delivery"))
    expect(screen.getByRole("alert").textContent).toMatch(/litres/)
    fireEvent.change(screen.getByLabelText("Litres delivered"), { target: { value: "11950" } })
    fireEvent.click(screen.getByText("Confirm delivery"))
    expect(screen.getByRole("alert").textContent).toMatch(/received/)
    fireEvent.change(screen.getByLabelText("Received by"), { target: { value: "P Botha" } })
    fireEvent.click(screen.getByText("Confirm delivery"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/fuel/driver/loads/d1/complete")
    expect(body.litresDelivered).toBe(11950); expect(body.receiverName).toBe("P Botha"); expect(typeof body.clientRequestId).toBe("string")
    expect(readQueue()).toHaveLength(0)
  })

  it("with no signal the delivery is kept on the phone with the same request id, then sent when back online", async () => {
    post.mockRejectedValueOnce(new Error("Network Error"))
    mount()
    await waitFor(() => screen.getByText("Record delivery"))
    fireEvent.click(screen.getByText("Record delivery"))
    fireEvent.change(screen.getByLabelText("Received by"), { target: { value: "P Botha" } })
    fireEvent.click(screen.getByText("Confirm delivery"))
    await waitFor(() => expect(readQueue()).toHaveLength(1))
    const firstId = (post.mock.calls[0][1] as any).clientRequestId
    expect(readQueue()[0].body?.clientRequestId).toBe(firstId)
    expect(screen.getAllByRole("status").some(e => /saved on this phone/.test(e.textContent ?? ""))).toBe(true)
    window.dispatchEvent(new Event("online"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(2))
    expect((post.mock.calls[1][1] as any).clientRequestId).toBe(firstId)
    await waitFor(() => expect(readQueue()).toHaveLength(0))
  })

  it("a server refusal is shown and is not queued", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Cannot complete a delivery that is cancelled." } } })
    mount()
    await waitFor(() => screen.getByText("Record delivery"))
    fireEvent.click(screen.getByText("Record delivery"))
    fireEvent.change(screen.getByLabelText("Received by"), { target: { value: "P Botha" } })
    fireEvent.click(screen.getByText("Confirm delivery"))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/cancelled/))
    expect(readQueue()).toHaveLength(0)
  })

  it("records a failed delivery with a reason and the litres brought back", async () => {
    loads = [load({ loadedLitres: 12000, windowStart: "2026-11-12T08:00:00Z", windowEnd: "2026-11-12T10:00:00Z", failedAttempts: 1 })]
    mount()
    await waitFor(() => screen.getByText("Could not deliver"))
    expect(screen.getByText(/window/)).toBeTruthy(); expect(screen.getByText(/1 failed attempt$/, { exact: false })).toBeTruthy()
    fireEvent.click(screen.getByText("Could not deliver"))
    fireEvent.click(screen.getByText("Confirm not delivered"))
    expect(screen.getByRole("alert").textContent).toMatch(/reason/i)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "SITE_CLOSED" } })
    fireEvent.change(screen.getByLabelText("Litres returned"), { target: { value: "13000" } })
    fireEvent.click(screen.getByText("Confirm not delivered"))
    expect(screen.getByRole("alert").textContent).toMatch(/more than/)
    fireEvent.change(screen.getByLabelText("Litres returned"), { target: { value: "12000" } })
    fireEvent.click(screen.getByText("Confirm not delivered"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/fuel/driver/loads/d1/failed-attempt")
    expect(body).toMatchObject({ reasonCode: "SITE_CLOSED", litresReturned: 12000 }); expect(typeof body.clientRequestId).toBe("string")
  })

  it("a failed delivery with no signal is kept and the delivery buttons are hidden meanwhile", async () => {
    post.mockRejectedValueOnce(new Error("Network Error"))
    mount()
    await waitFor(() => screen.getByText("Could not deliver"))
    fireEvent.click(screen.getByText("Could not deliver"))
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "NO_ACCESS" } })
    fireEvent.click(screen.getByText("Confirm not delivered"))
    await waitFor(() => expect(readQueue()).toHaveLength(1))
    expect(readQueue()[0].kind).toBe("FAILED")
    expect(screen.queryByText("Record delivery")).toBeNull()
    expect(screen.getByText(/failed delivery saved/)).toBeTruthy()
  })

  it("shows which stop on the route a load is", async () => {
    loads = [load({ routeId: "r1", routeSequence: 2 })]
    mount()
    await waitFor(() => screen.getByText(/stop 2 on your route/))
  })

  it("says when nothing is allocated", async () => {
    loads = []; mount()
    await waitFor(() => screen.getByText(/No loads allocated to you/))
  })

  describe("photos and signatures", () => {
    beforeEach(() => { vi.spyOn(capture, "shrinkImage").mockResolvedValue(photo) })
    afterEach(() => vi.restoreAllMocks())

    it("sends a photo as an upload to the driver's file endpoint", async () => {
      mount(); await waitFor(() => screen.getByText("Take photo"))
      pick()
      await waitFor(() => expect(post).toHaveBeenCalled())
      const [path, body] = post.mock.calls[0]
      expect(path).toBe("/api/v1/fuel/driver/loads/d1/evidence/file")
      expect(body).toBeInstanceOf(FormData); expect((body as FormData).get("kind")).toBe("PHOTO"); expect((body as FormData).get("clientRequestId")).toBeTruthy()
      expect(((body as FormData).get("file") as File).name).toBe("IMG_1.jpg")
      await waitFor(() => screen.getByText("Photo saved."))
    })

    it("a photo taken with no signal is kept with its file, and sent as an upload when back online", async () => {
      post.mockRejectedValueOnce(new Error("Network Error"))
      mount(); await waitFor(() => screen.getByText("Take photo"))
      pick()
      await waitFor(() => expect(readQueue()).toHaveLength(1))
      expect(readQueue()[0].file).toEqual(photo); expect(readQueue()[0].label).toBe("Photo")
      window.dispatchEvent(new Event("online"))
      await waitFor(() => expect(readQueue()).toHaveLength(0))
      const last = post.mock.calls[post.mock.calls.length - 1]
      expect(last[1]).toBeInstanceOf(FormData)
    })

    it("says so when the phone has no room to keep a photo, and does not pretend it was saved", async () => {
      post.mockRejectedValueOnce(new Error("Network Error"))
      const set = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("QuotaExceededError") })
      mount(); await waitFor(() => screen.getByText("Take photo"))
      pick()
      await waitFor(() => screen.getByRole("alert"))
      expect(screen.getByRole("alert").textContent).toMatch(/no room to save the photo/)
      expect(screen.queryByText(/saved on this phone/i)).toBeNull()
      set.mockRestore()
    })

    it("a photo the browser cannot read is reported and nothing is sent", async () => {
      vi.spyOn(capture, "shrinkImage").mockRejectedValue(new Error("bad"))
      mount(); await waitFor(() => screen.getByText("Take photo"))
      pick()
      await waitFor(() => screen.getByRole("alert"))
      expect(screen.getByRole("alert").textContent).toMatch(/Could not read that photo/); expect(post).not.toHaveBeenCalled()
    })

    it("a signature is drawn, then sent as a SIGNATURE upload", async () => {
      vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({ beginPath() {}, moveTo() {}, lineTo() {}, stroke() {}, clearRect() {} } as any)
      vi.spyOn(HTMLCanvasElement.prototype, "toDataURL").mockReturnValue("data:image/png;base64,aGk=")
      mount(); await waitFor(() => screen.getByText("Get signature"))
      fireEvent.click(screen.getByText("Get signature"))
      const c = screen.getByLabelText("Signature pad")
      fireEvent.pointerDown(c, { clientX: 5, clientY: 5, pointerId: 1 }); fireEvent.pointerMove(c, { clientX: 30, clientY: 20, pointerId: 1 })
      fireEvent.click(screen.getByText("Save signature"))
      await waitFor(() => expect(post).toHaveBeenCalled())
      const [path, body] = post.mock.calls[0]
      expect(path).toBe("/api/v1/fuel/driver/loads/d1/evidence/file"); expect((body as FormData).get("kind")).toBe("SIGNATURE")
      await waitFor(() => expect(screen.queryByLabelText("Signature pad")).toBeNull())
    })

    it("photo and signature are not offered before the trip starts", async () => {
      loads = [load({ status: "SCHEDULED" })]
      mount(); await waitFor(() => screen.getByText("Start trip"))
      expect(screen.queryByText("Take photo")).toBeNull(); expect(screen.queryByText("Get signature")).toBeNull()
    })
  })
})
