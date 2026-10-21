// Reading a message out of whatever an API call threw. Shared by the CRM panels.
interface ApiErrorShape { message?: string; response?: { data?: { message?: string } } }

/** The server's own message, if it sent one. */
export const responseMessage = (e: unknown): string | undefined => (e as ApiErrorShape | null)?.response?.data?.message
/** The server's message, else the error's own text. */
export const errorMessage = (e: unknown): string | undefined => responseMessage(e) ?? (e as ApiErrorShape | null)?.message
