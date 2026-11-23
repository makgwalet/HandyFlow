// src/pages/agriculture/AgManage.tsx
// Shows its children only to a person who can change Agriculture data (AGRICULTURE_MANAGE). Wrap create, edit, delete and
// status buttons so a read-only user is not offered actions the server will refuse.
import type { ReactNode } from "react"
import { usePermission } from "../../hooks/usePermission"

export default function AgManage({ children }: { children: ReactNode }) {
  return usePermission("AGRICULTURE_MANAGE") ? <>{children}</> : null
}
