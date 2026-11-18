import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { SitePodField, savePodDesignation } from "./SitePodField"
import { buildCompleteBody, type CompleteForm } from "./opsPolicyOps"

const get = vi.fn(); const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })

function mount(siteId: string | undefined, onChange = vi.fn()) {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SitePodField siteId={siteId} onChange={onChange} /></QueryClientProvider>)
  return onChange
}

describe("SitePodField", () => {
  beforeEach(() => { get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("shows the site's current arrangement and reports a change", async () => {
    get.mockImplementation(() => ok("UNATTENDED_SITE"))
    const onChange = mount("s1")
    const sel = (await screen.findByLabelText("How this site receives fuel")) as HTMLSelectElement
    await waitFor(() => expect(sel.value).toBe("UNATTENDED_SITE"))
    fireEvent.change(sel, { target: { value: "AUTOMATED_RECEIVING" } })
    expect(onChange).toHaveBeenLastCalledWith("AUTOMATED_RECEIVING", true)
    fireEvent.change(sel, { target: { value: "UNATTENDED_SITE" } })
    expect(onChange).toHaveBeenLastCalledWith("UNATTENDED_SITE", false)
  })

  it("a new site starts attended and asks nothing of the server", async () => {
    const onChange = mount(undefined)
    const sel = screen.getByLabelText("How this site receives fuel") as HTMLSelectElement
    expect(sel.value).toBe("")
    expect(get).not.toHaveBeenCalled()
    fireEvent.change(sel, { target: { value: "UNATTENDED_SITE" } })
    expect(onChange).toHaveBeenLastCalledWith("UNATTENDED_SITE", true)
  })

  it("saves the designation, and clears it with null for an attended site", async () => {
    await savePodDesignation("s1", "UNATTENDED_SITE")
    expect(put).toHaveBeenCalledWith("/api/v1/fuel/customer-sites/s1/pod-exception", { exceptionType: "UNATTENDED_SITE" })
    await savePodDesignation("s1", "")
    expect(put).toHaveBeenLastCalledWith("/api/v1/fuel/customer-sites/s1/pod-exception", { exceptionType: null })
    await savePodDesignation(undefined, "UNATTENDED_SITE")
    expect(put).toHaveBeenCalledTimes(2)
  })
})

describe("buildCompleteBody", () => {
  const base: CompleteForm = { litresDelivered: "1000", receiverName: " Johannes ", receiverIdBadge: "", meterReadingStart: "", meterReadingEnd: "", signedOnBehalf: false, onBehalfOf: "", signature: "data:image/png;base64,AAAA", podExceptionType: "", podExceptionReason: "" }

  it("sends the receiver's name and signature together for an attended delivery", () => {
    const b = buildCompleteBody(base)
    expect(b.receiverName).toBe("Johannes"); expect(b.receiverSignatureUrl).toBe("data:image/png;base64,AAAA")
    expect(b.podExceptionType).toBeNull(); expect(b.podExceptionReason).toBeNull()
  })

  it("sends no receiver and no signature with an exception, and the reason with it", () => {
    const b = buildCompleteBody({ ...base, podExceptionType: "UNATTENDED_SITE", podExceptionReason: " Gate locked, tank filled. " })
    expect(b.receiverName).toBeNull(); expect(b.receiverSignatureUrl).toBeNull()
    expect(b.podExceptionType).toBe("UNATTENDED_SITE"); expect(b.podExceptionReason).toBe("Gate locked, tank filled.")
  })

  it("sends no signature when none was drawn, so the server says so", () => {
    expect(buildCompleteBody({ ...base, signature: "" }).receiverSignatureUrl).toBeNull()
  })
})
