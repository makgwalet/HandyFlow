// src/pages/agriculture/agErrors.ts
// The server's own words for why a save failed ("Only a female can be bred"), with a plain fallback.
// Every Agriculture mutation used to fail silently, so a farmer believed a record was saved when it was not.
// One wording for the whole app: this is the toast store's errorMessage (403 -> no permission, 5xx -> server problem,
// no response -> can't reach the server, otherwise the server's own message).
import { errorMessage } from "../../store/toast.store"

export const apiErrorMessage = errorMessage
