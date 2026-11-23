import { describe, it, expect, vi, beforeEach } from "vitest"
import { agMutationErrorHandler, withAgErrorToast } from "./agMutation"
import { useToastStore } from "../../store/toast.store"

beforeEach(() => { useToastStore.setState({ toasts: [] }); vi.useFakeTimers() })

describe("agMutationErrorHandler", () => {
  it("shows the server's reason as an error toast", () => {
    agMutationErrorHandler({ response: { status: 409, data: { message: "Cannot issue 50 kg: only 20 on hand" } } })
    const t = useToastStore.getState().toasts
    expect(t).toHaveLength(1)
    expect(t[0].kind).toBe("error")
    expect(t[0].message).toBe("Cannot issue 50 kg: only 20 on hand")
  })
  it("says permission for a 403", () => {
    agMutationErrorHandler({ response: { status: 403, data: {} } })
    expect(useToastStore.getState().toasts[0].message).toMatch(/permission/)
  })
  it("says the server could not be reached when there was no response", () => {
    agMutationErrorHandler({ code: "ERR_NETWORK", message: "Network Error" })
    expect(useToastStore.getState().toasts[0].message).toMatch(/reach the server/)
  })
  it("uses the save fallback when the server gave no reason", () => {
    agMutationErrorHandler({ response: { status: 400, data: {} } })
    expect(useToastStore.getState().toasts[0].message).toBe("That did not save. Please try again.")
  })
})

describe("withAgErrorToast", () => {
  const fn = async () => 1
  it("adds the toast handler when the screen has none", () => {
    expect(withAgErrorToast({ mutationFn: fn }).onError).toBe(agMutationErrorHandler)
  })
  it("keeps a screen's own handler, so the person is not told twice", () => {
    const own = () => {}
    expect(withAgErrorToast({ mutationFn: fn, onError: own }).onError).toBe(own)
  })
  it("leaves everything else alone", () => {
    const onSuccess = () => {}
    const o = withAgErrorToast({ mutationFn: fn, onSuccess })
    expect(o.onSuccess).toBe(onSuccess)
    expect(o.mutationFn).toBe(fn)
  })
})
