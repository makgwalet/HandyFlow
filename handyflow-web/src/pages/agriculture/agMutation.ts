// src/pages/agriculture/agMutation.ts
// useMutation for the Agriculture livestock and farm screens (the crop and cost screens use useAgMutation in agCrops.api.ts, which already toasts). If a save fails and the screen has not said what to do about it (no onError of its
// own), the person gets a red toast with the reason instead of nothing. A screen that handles errors itself passes its own
// onError, which replaces the toast.
import { useMutation, type UseMutationOptions, type UseMutationResult } from "@tanstack/react-query"
import { toast } from "../../store/toast.store"
import { apiErrorMessage } from "./agErrors"

export function agMutationErrorHandler(e: unknown): void {
  toast.error(apiErrorMessage(e, "That did not save. Please try again."))
}

export function withAgErrorToast<TData, TError, TVariables, TContext>(
  options: UseMutationOptions<TData, TError, TVariables, TContext>,
): UseMutationOptions<TData, TError, TVariables, TContext> {
  return options.onError ? options : { ...options, onError: agMutationErrorHandler }
}

export function useAgToastMutation<TData = unknown, TError = unknown, TVariables = void, TContext = unknown>(
  options: UseMutationOptions<TData, TError, TVariables, TContext>,
): UseMutationResult<TData, TError, TVariables, TContext> {
  return useMutation(withAgErrorToast(options))
}
