// HandyFlow service worker. Its only job is to let the app open with no signal. It never touches the API: every /api request goes straight to the
// network, so nothing the server says is ever served from a cache. Entries made offline are kept by the app itself (see offlineQueue.ts), not here.
const VERSION = "hf-shell-v1"
const SHELL = ["/", "/index.html", "/favicon.svg", "/manifest.webmanifest"]

self.addEventListener("install", event => {
  event.waitUntil(caches.open(VERSION).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()))
})

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k)))).then(() => self.clients.claim()),
  )
})

self.addEventListener("fetch", event => {
  const req = event.request
  if (req.method !== "GET") return
  const url = new URL(req.url)
  if (url.origin !== self.location.origin || url.pathname.startsWith("/api/")) return

  // Pages: the network first (so a new release shows at once), the saved shell when there is no signal.
  if (req.mode === "navigate") {
    event.respondWith(
      fetch(req).then(res => {
        const copy = res.clone()
        caches.open(VERSION).then(c => c.put("/index.html", copy)).catch(() => {})
        return res
      }).catch(() => caches.match("/index.html").then(r => r || caches.match("/"))),
    )
    return
  }

  // Built files have a hash in their name, so a saved copy is always the right one.
  if (url.pathname.startsWith("/assets/")) {
    event.respondWith(
      caches.match(req).then(hit => hit || fetch(req).then(res => {
        if (res.ok) { const copy = res.clone(); caches.open(VERSION).then(c => c.put(req, copy)).catch(() => {}) }
        return res
      })),
    )
  }
})
