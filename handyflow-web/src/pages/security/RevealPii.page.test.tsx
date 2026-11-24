// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: unknown[]) => post(...a) } }))

import RevealPii from "./RevealPii"

function show(canReveal = true) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><RevealPii guardId="g1" field="ID_NUMBER" masked="850101*******" canReveal={canReveal} /></QueryClientProvider>)
}

describe("RevealPii", () => {
  beforeEach(() => { post.mockReset() })
  afterEach(() => cleanup())

  it("shows the masked value and no button without permission", () => {
    show(false)
    expect(screen.getByText("850101*******")).toBeTruthy()
    expect(screen.queryByText("Show")).toBeNull()
  })

  it("refuses a short reason without calling the server", async () => {
    show()
    fireEvent.click(screen.getByText("Show"))
    fireEvent.change(screen.getByLabelText(/Reason for showing/), { target: { value: "no" } })
    fireEvent.click(screen.getAllByText("Show")[0])
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("sends the reason, shows the full value, and hides it again", async () => {
    post.mockResolvedValue({ data: { data: { field: "ID_NUMBER", value: "8501015026088" } } })
    show()
    fireEvent.click(screen.getByText("Show"))
    fireEvent.change(screen.getByLabelText(/Reason for showing/), { target: { value: "PSIRA renewal paperwork" } })
    fireEvent.click(screen.getAllByText("Show")[0])
    await waitFor(() => expect(screen.getByText("8501015026088")).toBeTruthy())
    expect(post).toHaveBeenCalledWith("/api/v1/security/guards/g1/reveal", { field: "ID_NUMBER", reason: "PSIRA renewal paperwork" })
    fireEvent.click(screen.getByText("Hide"))
    expect(screen.queryByText("8501015026088")).toBeNull()
    expect(screen.getByText("850101*******")).toBeTruthy()
  })

  it("shows the server's message when it refuses", async () => {
    post.mockRejectedValue({ response: { data: { message: "Missing permission SECURITY_MANAGE" } } })
    show()
    fireEvent.click(screen.getByText("Show"))
    fireEvent.change(screen.getByLabelText(/Reason for showing/), { target: { value: "PSIRA renewal paperwork" } })
    fireEvent.click(screen.getAllByText("Show")[0])
    expect(await screen.findByText("Missing permission SECURITY_MANAGE")).toBeTruthy()
    expect(screen.queryByText("8501015026088")).toBeNull()
  })
})
