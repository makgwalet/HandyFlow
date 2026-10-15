// Which Earthmoving permissions the signed-in person holds. Buttons use this to avoid showing an action that would be
// refused; the server still checks every call.
import { useAuthStore } from "../../../store/auth.store"
import { EARTHMOVING_PERMS } from "./constants"

export function useEarthPerms() {
  const perms = useAuthStore(s => s.user?.permissions)
  const has = (p: string) => perms?.includes(p) ?? false
  return {
    has,
    canWrite: has(EARTHMOVING_PERMS.WRITE),
    canDeploy: has(EARTHMOVING_PERMS.DEPLOY),
    canMaintain: has(EARTHMOVING_PERMS.MAINTAIN),
    canChangeStatus: [EARTHMOVING_PERMS.WRITE, EARTHMOVING_PERMS.DEPLOY, EARTHMOVING_PERMS.MAINTAIN,
      EARTHMOVING_PERMS.RELEASE, EARTHMOVING_PERMS.ADMIN].some(has),
  }
}
