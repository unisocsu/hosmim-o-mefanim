/* חוסמים את הציר — Service Worker (משחק עצמאי מלא: כל הנכסים מקומיים) */
const CACHE = 'hosmim-v3';
const CORE = [
  '/', '/index.html', '/manifest.json',
  '/three.min.js', '/fonts.css',
  '/fonts/heebo-400-hebrew.woff2',
  '/fonts/heebo-400-latin.woff2',
  '/fonts/heebo-400-latin-ext.woff2',
  '/fonts/heebo-400-math.woff2',
  '/fonts/heebo-400-symbols.woff2',
  '/fonts/frank-ruhl-libre-700-hebrew.woff2',
  '/fonts/frank-ruhl-libre-700-latin.woff2',
  '/fonts/frank-ruhl-libre-700-latin-ext.woff2',
  '/intro.mp4', '/cops.mp4',
  '/icon-192.png', '/icon-512.png', '/icon-512-maskable.png',
  '/privacy.html'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  // Network-first: תמיד גרסה עדכנית, נפילה למטמון כשאין רשת
  e.respondWith(
    fetch(req).then(res => {
      const copy = res.clone();
      caches.open(CACHE).then(c => c.put(req, copy)).catch(() => {});
      return res;
    }).catch(() => caches.match(req).then(r => {
      if (r) return r;
      // נפילת index רק לניווטים — נכס חסר לא יקבל HTML במקום תמונה/מדיה
      if (req.mode === 'navigate') return caches.match('/index.html');
      return new Response('', { status: 504, statusText: 'offline' });
    }))
  );
});
