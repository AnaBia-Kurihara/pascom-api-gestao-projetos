// Service worker da Pascom: só cuida das notificações no celular. Não guarda páginas em cache de
// propósito (o front tem que chegar sempre na versão nova, ver ETag no WebConfig).
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));

self.addEventListener('push', e => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch (_) { d = { texto: e.data ? e.data.text() : '' }; }
  e.waitUntil(self.registration.showNotification(d.titulo || 'Pascom Fátima', {
    body: d.texto || 'Você tem um aviso novo.',
    icon: '/icons/icon-192.png',
    badge: '/icons/icon-192.png',
    lang: 'pt-BR',
    data: { url: d.url || '/?abrir=avisos' }
  }));
});

// Tocar na notificação: volta pro app aberto (e abre os avisos) ou abre o app.
self.addEventListener('notificationclick', e => {
  e.notification.close();
  const url = (e.notification.data && e.notification.data.url) || '/';
  e.waitUntil((async () => {
    const abertos = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const c of abertos) {
      if (new URL(c.url).origin === self.location.origin) {
        await c.focus();
        c.postMessage({ tipo: 'abrirAvisos' });
        return;
      }
    }
    await self.clients.openWindow(url);
  })());
});
