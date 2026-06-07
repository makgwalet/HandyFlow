// src/store/auth.ts

export interface AdminUser {
  adminId: string
  email:   string
  fullName: string
  role:    string
  token:   string
  expiresAt: string
}

export const authStore = {
  get(): AdminUser | null {
    try {
      return JSON.parse(localStorage.getItem('hf_admin_user') ?? 'null')
    } catch {
      return null
    }
  },

  set(user: AdminUser) {
    localStorage.setItem('hf_admin_token', user.token)
    localStorage.setItem('hf_admin_user', JSON.stringify(user))
  },

  clear() {
    localStorage.removeItem('hf_admin_token')
    localStorage.removeItem('hf_admin_user')
  },

  isLoggedIn(): boolean {
    const u = this.get()
    if (!u?.token || !u?.expiresAt) return false
    return new Date(u.expiresAt) > new Date()
  },
}
