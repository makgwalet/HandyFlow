// src/pages/agriculture/AgTemporaryTag.tsx
//
// Shown on an animal born on the farm that still carries a temporary tag (<dam tag>-<yy>-<n>). The farmer types the real
// tag once it is fitted; the server keeps the old one in the tag history. PATCH /api/v1/agriculture/animals/{id}/tag.
import { useState } from "react"
import { useAgToastMutation } from "./agMutation"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { inp, btnPrimary } from "./constants"
import AgManage from "./AgManage"

export default function AgTemporaryTag({ animalId, tagNumber, onReplaced }: { animalId: string; tagNumber: string; onReplaced: () => void }) {
  const [tag, setTag] = useState("")
  const mut = useAgToastMutation({
    mutationFn: () => apiClient.patch(`/api/v1/agriculture/animals/${animalId}/tag`, { tagNumber: tag.trim() }),
    onSuccess: () => { toast.success("Tag updated."); setTag(""); onReplaced() },
  })
  const blank = tag.trim() === ""
  return (
    <div role="status" style={{ background: "var(--hf-warning-bg)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px", marginBottom: 14, maxWidth: 560 }}>
      <p style={{ fontSize: 12.5, color: "var(--hf-warning-text)", margin: 0 }}>
        <strong>{tagNumber}</strong> is a temporary tag. Enter the permanent tag once it is fitted.
      </p>
      <AgManage>
        <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
          <input aria-label="Permanent tag" value={tag} onChange={e => setTag(e.target.value)} placeholder="Permanent tag number" style={{ ...inp, maxWidth: 240 }} />
          <button disabled={blank || mut.isPending} onClick={() => mut.mutate()} style={{ ...btnPrimary, opacity: blank || mut.isPending ? 0.6 : 1 }}>
            {mut.isPending ? "Saving…" : "Save tag"}
          </button>
        </div>
      </AgManage>
    </div>
  )
}
