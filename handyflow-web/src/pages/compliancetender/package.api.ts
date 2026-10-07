// src/pages/compliancetender/package.api.ts
//
// Submission packages (ADR-005): the server plans, validates, merges and stores; the screen only sends what the person chose and shows what comes back.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface LimitsRequest {
  name: string
  allowedExtensions: string[] | null
  maxFileBytes: number | null
  maxTotalBytes: number | null
  maxFileCount: number | null
  zipAllowed: boolean | null
  maxFileNameLength: number | null
}

export interface BuildRequest {
  sectionKeys: string[]
  coverLetterText: string | null
  companyProfileText: string | null     // null = use the current company profile
  documentIds: string[]
  submissionProfileId: string | null
  limits: LimitsRequest | null          // tender-specific overrides on top of the saved profile
  pricingRequired: boolean
}

export interface SectionStatus { key: string; title: string; available: boolean; unavailableReason: string | null; fileCount: number }
export interface PlanIssue { severity: "BLOCKING" | "WARNING"; code: string; message: string; fileName: string | null }
export interface PackageFileEntry { position: number; sectionKey: string; fileName: string; source: "GENERATED" | "ATTACHED" | "ORIGINAL"; sizeBytes: number; sha256: string; pages: number | null }

export interface TenderPackage {
  id: string; tenderId: string; versionNo: number; submissionReady: boolean; includesPricing: boolean; profileName: string | null
  packageHash: string; fileName: string; sizeBytes: number; pageCount: number | null; createdAt: string; createdByName: string | null
  files: PackageFileEntry[]
  /** The tender has changed since this version was built. Older servers do not send these. */
  stale?: boolean; staleReasons?: string[]
}

export interface PackagePlan {
  canBuild: boolean; submissionReady: boolean
  sections: SectionStatus[]; issues: PlanIssue[]; limitNotes: string[]
  built: TenderPackage | null
}

export interface SubmissionProfile {
  id: string; name: string; allowedExtensions: string[] | null
  maxFileBytes: number | null; maxTotalBytes: number | null; maxFileCount: number | null
  zipAllowed: boolean | null; maxFileNameLength: number | null
}

export interface ComplianceDoc {
  id: string; documentType: string; evidenceId: string; issueDate: string | null; expiryDate: string | null; verified: boolean
}

const base = (tenderId: string) => `/api/v1/compliance/tenders/${tenderId}`

/** What the build would contain right now. Asked again whenever a choice changes; nothing is stored. */
export function usePackagePreview(tenderId: string | undefined, request: BuildRequest, enabled = true) {
  return useQuery<PackagePlan>({
    queryKey: ["ct-package-preview", tenderId, request],
    enabled: !!tenderId && enabled,
    placeholderData: previous => previous,         // keep showing the last answer while the next one loads, so the page does not flash empty
    queryFn: async () => (await apiClient.post(`${base(tenderId!)}/packages/preview`, request)).data,
  })
}

export function usePackages(tenderId: string | undefined) {
  return useQuery<TenderPackage[]>({
    queryKey: ["ct-packages", tenderId], enabled: !!tenderId,
    queryFn: async () => (await apiClient.get(`${base(tenderId!)}/packages`)).data,
  })
}

export function useBuildPackage(tenderId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (request: BuildRequest) => (await apiClient.post(`${base(tenderId)}/packages`, request)).data as PackagePlan,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ct-packages", tenderId] }),
  })
}

export function useSubmissionProfiles() {
  return useQuery<SubmissionProfile[]>({
    queryKey: ["ct-submission-profiles"],
    queryFn: async () => (await apiClient.get("/api/v1/compliance/submission-profiles")).data,
  })
}

export function useComplianceDocuments() {
  return useQuery<ComplianceDoc[]>({
    queryKey: ["ct-documents"],
    queryFn: async () => {
      const d = (await apiClient.get("/api/v1/compliance/documents")).data as ComplianceDoc[] | { content?: ComplianceDoc[] } | null
      return Array.isArray(d) ? d : d?.content ?? []
    },
  })
}

/** Downloads the stored combined PDF. The server refuses it when the stored bytes no longer match the hash recorded at build time. */
export async function downloadPackage(packageId: string, fileName: string): Promise<void> {
  const res = await apiClient.get(`/api/v1/compliance/tender-packages/${packageId}/download`, { responseType: "blob" })
  const blob = new Blob([res.data as BlobPart], { type: "application/pdf" })
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a"); a.href = url; a.download = fileName; a.click(); URL.revokeObjectURL(url)
}

// ---- the saved draft: what was typed and ticked on this screen, kept so a reload does not lose it

export interface SavedDraft { data: unknown; updatedAt: string; updatedByName: string | null }

/** null when nothing has been saved yet (the server answers 204 with no body). */
export function usePackageDraft(tenderId: string | undefined) {
  return useQuery<SavedDraft | null>({
    queryKey: ["ct-package-draft", tenderId], enabled: !!tenderId, staleTime: Infinity, refetchOnWindowFocus: false,
    queryFn: async () => { const d = (await apiClient.get(`${base(tenderId!)}/package-draft`)).data as SavedDraft | "" | null; return d ? d : null },
  })
}

export async function savePackageDraft(tenderId: string, data: unknown): Promise<SavedDraft> {
  return (await apiClient.put(`${base(tenderId)}/package-draft`, { data })).data as SavedDraft
}

export async function discardPackageDraft(tenderId: string): Promise<void> {
  await apiClient.delete(`${base(tenderId)}/package-draft`)
}

// ---- documents picked automatically from what the tender requires

export interface SuggestedDocument { requirement: string; documentType: string; outcome: "CHOSEN" | "NOT_VERIFIED" | "EXPIRED" | "MISSING"; documentId: string | null; message: string }

export async function fetchSuggestedDocuments(tenderId: string): Promise<SuggestedDocument[]> {
  return (await apiClient.get(`${base(tenderId)}/package/suggested-documents`)).data as SuggestedDocument[]
}
