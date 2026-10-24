/* Native-style shell for WEB-V1.8. Reuses the mobile web engine. */
(function () {
  'use strict';
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const norm = s => ('' + s).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  const toast = (m, t) => { try { window.toast ? window.toast(m, t) : nvToast(m); } catch { nvToast(m); } };
  let activeTab = 'fuentes';
  let compassOn = false;
  let heading = 0;
  let orientHandler = null;
  let geoWatch = null;
  

  function nvToast(msg) {
    const el = document.createElement('div');
    el.className = 'nv-toast'; el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 2600);
  }

  function buildShell() {
    if ($('#nv-top')) return;
    const html = `
      <header id="nv-top">
        <button id="nv-poi" class="nv-ring nv-50" aria-label="Fuentes y puntos de interés"><img src="assets/hd_logo.png" alt=""></button>
        <button id="nv-search" aria-label="Buscar fuente, pueblo o código postal"><span class="nv-mag">🔍</span><span class="nv-ph">¿A dónde?</span></button>
        <div id="nv-right">
          <button id="nv-layers" class="nv-ring nv-50" aria-label="Capas del mapa">🗺️</button>
          <button id="nv-weather" class="nv-ring nv-38" aria-label="Clima">🌦️</button>
        </div>
      </header>
      <div id="nv-left"><button id="nv-mode" class="nv-ring nv-38" aria-label="Modo brújula">🎯</button></div>
      <div id="nv-rose" aria-hidden="true"><div id="nv-rose-deg">0°N</div><canvas id="nv-rose-canvas"></canvas></div>
      <div id="nv-zoom"><button id="nv-zoom-in" aria-label="Acercar">+</button><button id="nv-zoom-out" aria-label="Alejar">−</button></div>
      <button id="nv-locate" aria-label="Centrar en mi ubicación">🎯</button>
      <div id="nv-routebar">
        <button class="nv-nav" id="nv-nav" aria-label="Seguir ruta">▲</button>
        <div class="nv-route-main" id="nv-route-main"><strong id="nv-route-title">Ruta</strong><small id="nv-route-sub">Pulsa para ver detalles</small></div>
        <button class="nv-profile" id="nv-profile" aria-label="Perfil de altura">▥</button>
      </div>
      <nav id="nv-bottom" aria-label="Navegación principal">
        <button class="nv-tab active" data-nv-tab="ruta" aria-label="Ruta">🏍️</button>
        <button class="nv-tab" data-nv-action="settings" aria-label="Ajustes">⚙️</button>
        <button class="nv-tab" data-nv-tab="fuentes" aria-label="Fuentes">⛲</button>
        <button class="nv-tab" data-nv-action="tools" aria-label="Datos y rutas guardadas">☰</button>
        <button class="nv-tab info" data-nv-tab="info" aria-label="Información">ℹ️</button>
      </nav>
      <button id="nv-close" aria-label="Volver al mapa">←</button>
      <div id="nv-sheet-layers" class="nv-sheet">
        <div class="nv-sheet-head"><h3>Capas del mapa</h3><button class="nv-sheet-close" data-nv-close-sheet>✕</button></div>
        <div class="nv-choice">
          <button data-nv-layer="osm">OSM</button><button data-nv-layer="topo">Topo</button>
          <button data-nv-layer="cyclo">CyclOSM</button><button data-nv-layer="sat">Satélite</button>
        </div>
      </div>
      <div id="nv-sheet-settings" class="nv-sheet">
        <div class="nv-sheet-head"><h3>Ajustes</h3><button class="nv-sheet-close" data-nv-close-sheet>✕</button></div>
        <div class="nv-choice">
          <button data-nv-settings="tools">Rutas y datos</button><button data-nv-settings="layers">Capas</button>
          <button data-nv-settings="weather">Clima</button><button data-nv-settings="help">Cómo usar</button>
        </div>
      </div>
      <div id="nv-sheet-weather" class="nv-sheet">
        <div class="nv-sheet-head"><h3>Clima en el centro del mapa</h3><button class="nv-sheet-close" data-nv-close-sheet>✕</button></div>
        <div id="nv-weather-view">Consultando…</div>
      </div>
      <div id="nv-search-sheet" class="nv-sheet">
        <div class="nv-sheet-head"><h3>Buscar</h3><button class="nv-sheet-close" data-nv-close-sheet>✕</button></div>
        <div class="nv-search-row"><input id="nv-search-input" type="search" enterkeyhint="search" placeholder="Fuente, pueblo o CP…" autocomplete="off"><button id="nv-search-clear" class="nv-sheet-close" aria-label="Limpiar">✕</button></div>
        <div id="nv-search-results"></div>
      </div>`;
    document.body.insertAdjacentHTML('beforeend', html);
    bindShell();
    observePanel();
    syncRouteBar();
  }

  function openSheet(id) {
    $$('.nv-sheet').forEach(s => s.classList.remove('open'));
    const s = document.getElementById(id); if (s) s.classList.add('open');
    document.body.classList.add('nv-sheet-open');
  }
  function closeSheets() { $$('.nv-sheet').forEach(s => s.classList.remove('open')); document.body.classList.remove('nv-sheet-open'); }

  function markActive(key) {
    $$('.nv-tab').forEach(b => b.classList.toggle('active', b.dataset.nvTab === key || b.dataset.nvAction === key));
  }
  function openTab(tab) {
    if (tab === 'info') { closeSheets(); markActive('info'); const v = $('#mobile-version'); if (v) v.click(); else openSheetInfoFallback(); return; }
    closeSheets();
    activeTab = tab;
    const tabBtn = document.getElementById('tab-' + tab);
    if (tabBtn && window.selectTab) window.selectTab(tabBtn, false);
    else if (tabBtn) tabBtn.click();
    const sb = $('#sidebar');
    if (sb && !sb.classList.contains('open') && window.togglePanel) window.togglePanel();
    if (sb) setTimeout(() => { sb.scrollTop = 0; }, 30);
    markActive(tab);
  }
  function openSheetInfoFallback() {
    const h = document.getElementById('mobile-help-modal'); if (h) h.removeAttribute('hidden');
  }
  function closePanel() {
    const sb = $('#sidebar');
    if (sb && sb.classList.contains('open') && window.togglePanel) window.togglePanel();
  }
  function observePanel() {
    const sb = $('#sidebar'); if (!sb) return;
    const apply = () => {
      const open = sb.classList.contains('open');
      document.documentElement.classList.toggle('nv-panel-open', open);
      if (!open) closeSheets();
    };
    new MutationObserver(apply).observe(sb, { attributes: true, attributeFilter: ['class'] });
    apply();
  }

  function bindShell() {
    $('#nv-poi').addEventListener('click', () => openTab('fuentes'));
    $('#nv-search').addEventListener('click', () => { openSheet('nv-search-sheet'); const i = $('#nv-search-input'); if (i) setTimeout(() => i.focus(), 80); });
    $('#nv-layers').addEventListener('click', () => openSheet('nv-sheet-layers'));
    $('#nv-weather').addEventListener('click', () => { openSheet('nv-sheet-weather'); loadNativeWeather(); });
    $('#nv-close').addEventListener('click', closePanel);
    $$('[data-nv-close-sheet]').forEach(b => b.addEventListener('click', closeSheets));

    $$('.nv-tab').forEach(b => b.addEventListener('click', (e) => {
      const action = b.dataset.nvAction;
      if (action === 'settings') { markActive('settings'); openSheet('nv-sheet-settings'); return; }
      if (action === 'tools') { e.stopPropagation(); markActive('tools'); closePanel(); const t = document.getElementById('btn-tools'); if (t) t.click(); return; }
      const tab = b.dataset.nvTab;
      if (tab === 'info') { openTab('info'); return; }
      const sb = $('#sidebar');
      if (sb && sb.classList.contains('open') && activeTab === tab) { closePanel(); return; }
      openTab(tab);
    }));
    $$('[data-nv-settings]').forEach(b => b.addEventListener('click', () => {
      const a = b.dataset.nvSettings; closeSheets();
      if (a === 'tools') { closePanel(); const t = document.getElementById('btn-tools'); if (t) t.click(); }
      else if (a === 'layers') openSheet('nv-sheet-layers');
      else if (a === 'weather') { openSheet('nv-sheet-weather'); loadNativeWeather(); }
      else if (a === 'help') { const v = $('#mobile-version'); if (v) v.click(); }
    }));

    $('#nv-zoom-in').addEventListener('click', () => { if (window.map) window.map.zoomIn(); });
    $('#nv-zoom-out').addEventListener('click', () => { if (window.map) window.map.zoomOut(); });
    $('#nv-locate').addEventListener('click', centerOnMe);
    $('#nv-mode').addEventListener('click', toggleCompass);
    $('#nv-nav').addEventListener('click', () => { const b = document.getElementById('btn-follow-map'); if (b) b.click(); else toast('GPS no disponible'); });
    $('#nv-profile').addEventListener('click', openProfile);
    $('#nv-route-main').addEventListener('click', () => openTab('ruta'));

    $$('[data-nv-layer]').forEach(b => b.addEventListener('click', () => {
      if (window.setLayer) window.setLayer(b.dataset.nvLayer);
      $$('[data-nv-layer]').forEach(x => x.classList.toggle('on', x === b));
      closeSheets();
    }));

    const si = $('#nv-search-input');
    si.addEventListener('input', () => renderNativeSearch(si.value));
    si.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); const first = $('#nv-search-results .nv-result'); if (first) first.click(); } });
    $('#nv-search-clear').addEventListener('click', () => { si.value = ''; renderNativeSearch(''); si.focus(); });

    document.addEventListener('keydown', e => { if (e.key === 'Escape') { closeSheets(); closePanel(); } });
  }

  function renderNativeSearch(q) {
    const box = $('#nv-search-results'); if (!box) return;
    box.innerHTML = '';
    const query = norm((q || '').trim());
    if (!query) { box.innerHTML = '<div class="nv-result"><div class="s">Escribe para buscar fuentes, pueblos o códigos postales.</div></div>'; return; }
    const towns = window.searchTowns ? window.searchTowns(query) : [];
    const fountains = window.searchFountains ? window.searchFountains(query) : [];
    towns.slice(0, 8).forEach(name => {
      const d = document.createElement('div'); d.className = 'nv-result';
      d.innerHTML = `<div class="t">🏘️ ${escapeHtml(name)}</div><div class="s">Pueblo / pedanía</div>`;
      d.addEventListener('click', () => { closeSheets(); if (window.setDestino) window.setDestino(name); });
      box.appendChild(d);
    });
    fountains.slice(0, 20).forEach(f => {
      const d = document.createElement('div'); d.className = 'nv-result';
      d.innerHTML = `<div class="t">⛲ ${escapeHtml(f.n)}</div><div class="s">${escapeHtml(f.p || '')}</div>`;
      d.addEventListener('click', () => { closeSheets(); if (window.showFountainOnMap) window.showFountainOnMap(f.id); });
      box.appendChild(d);
    });
    if (!box.children.length) box.innerHTML = '<div class="nv-result"><div class="s">Sin coincidencias</div></div>';
  }
  function escapeHtml(s) { return ('' + s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

  function centerOnMe() {
    if (!navigator.geolocation || !window.map) { toast('GPS no disponible'); return; }
    navigator.geolocation.getCurrentPosition(p => {
      window.map.setView([p.coords.latitude, p.coords.longitude], Math.max(window.map.getZoom(), 16), { animate: true });
      $('#nv-locate').style.borderColor = '#2ecc71';
    }, e => toast('GPS: ' + (e.code === 1 ? 'permiso denegado' : 'no disponible'), 'warn'), { enableHighAccuracy: true, timeout: 12000, maximumAge: 5000 });
  }

  function openProfile() {
    openTab('ruta');
    setTimeout(() => {
      const el = document.getElementById('elevation-chart');
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 160);
  }

  function syncRouteBar() {
    const dist = document.getElementById('r-dist');
    const time = document.getElementById('r-time');
    const start = document.getElementById('r-start');
    const end = document.getElementById('r-end');
    const bar = $('#nv-routebar');
    if (!bar) return;
    const dv = (dist?.textContent || '').trim();
    const tv = (time?.textContent || '').trim();
    const valid = dv && !/^0\s*km$/i.test(dv) && !/^-$/.test(dv);
    bar.classList.toggle('show', !!valid);
    if (valid) {
      $('#nv-route-title').textContent = `${dv} · ${tv || ''}`.trim();
      $('#nv-route-sub').textContent = `${start?.textContent || ''} → ${end?.textContent || ''}`;
    }
  }

  function loadNativeWeather() {
    const box = $('#nv-weather-view');
    if (!box || !window.map) return;
    const c = window.map.getCenter();
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${c.lat.toFixed(4)}&longitude=${c.lng.toFixed(4)}&current=temperature_2m,wind_speed_10m,weather_code&timezone=auto`;
    box.textContent = 'Consultando…';
    fetch(url, { cache: 'no-store' }).then(r => r.json()).then(d => {
      if (!d.current) throw new Error('sin datos');
      const w = d.current;
      const icon = wmo(w.weather_code);
      box.innerHTML = `<div style="font-size:34px;text-align:center;margin:6px 0">${icon}</div>
        <div style="text-align:center"><strong>${Math.round(w.temperature_2m)} °C</strong> · ${wmoText(w.weather_code)}</div>
        <div style="text-align:center;color:#77808b;margin-top:6px">Viento ${Math.round(w.wind_speed_10m)} km/h</div>
        <div style="text-align:center;color:#9aa3ad;font-size:12px;margin-top:8px">${c.lat.toFixed(4)}, ${c.lng.toFixed(4)}</div>`;
    }).catch(e => { box.textContent = 'No se pudo obtener el clima: ' + e.message; });
  }
  function wmo(c) { if (c === 0) return '☀️'; if (c <= 3) return '⛅'; if (c <= 48) return '🌫️'; if (c <= 57) return '🌦️'; if (c <= 67) return '🌧️'; if (c <= 77) return '❄️'; if (c <= 82) return '🌧️'; if (c <= 86) return '🌨️'; return '⛈️'; }
  function wmoText(c) { if (c === 0) return 'Despejado'; if (c <= 3) return 'Poco nuboso'; if (c <= 48) return 'Niebla'; if (c <= 57) return 'Llovizna'; if (c <= 67) return 'Lluvia'; if (c <= 77) return 'Nieve'; if (c <= 82) return 'Chubascos'; if (c <= 86) return 'Nieve'; return 'Tormenta'; }

  function toggleCompass() {
    compassOn = !compassOn;
    const rose = $('#nv-rose'); const btn = $('#nv-mode');
    rose.classList.toggle('show', compassOn);
    btn.classList.toggle('green', compassOn);
    btn.textContent = compassOn ? '🧭' : '🎯';
    if (compassOn) startHeading(); else stopHeading();
  }
  function startHeading() {
    const go = () => {
      if (window.DeviceOrientationEvent && typeof DeviceOrientationEvent.requestPermission === 'function') {
        DeviceOrientationEvent.requestPermission().then(r => { if (r !== 'granted') { toast('Permiso de brújula denegado'); compassOn = false; $('#nv-rose').classList.remove('show'); $('#nv-mode').textContent = '🎯'; return; } attachOrientation(); }).catch(() => toast('Brújula no disponible'));
      } else attachOrientation();
    };
    go();
  }
  function attachOrientation() {
    if (orientHandler) return;
    orientHandler = e => {
      let h = null;
      if (typeof e.webkitCompassHeading === 'number' && !isNaN(e.webkitCompassHeading)) h = e.webkitCompassHeading;
      else if (typeof e.alpha === 'number') h = 360 - e.alpha;
      if (h != null) { heading = ((h % 360) + 360) % 360; drawRose(); }
    };
    window.addEventListener('deviceorientationabsolute', orientHandler, true);
    window.addEventListener('deviceorientation', orientHandler, true);
    if (navigator.geolocation) geoWatch = navigator.geolocation.watchPosition(p => {
      if (typeof p.coords.heading === 'number' && !isNaN(p.coords.heading)) { heading = p.coords.heading; drawRose(); }
    }, () => {}, { enableHighAccuracy: true, maximumAge: 3000 });
    drawRose();
  }
  function stopHeading() {
    if (orientHandler) { window.removeEventListener('deviceorientationabsolute', orientHandler, true); window.removeEventListener('deviceorientation', orientHandler, true); orientHandler = null; }
    if (geoWatch != null) { try { navigator.geolocation.clearWatch(geoWatch); } catch {} geoWatch = null; }
  }
  function drawRose() {
    const cv = $('#nv-rose-canvas'); if (!cv) return;
    const css = cv.clientWidth || 253; const dpr = Math.min(window.devicePixelRatio || 1, 2); const size = Math.round(css * dpr);
    if (cv.width !== size) { cv.width = size; cv.height = size; }
    const ctx = cv.getContext('2d'); const c = size / 2; const R = c - 3 * dpr;
    ctx.clearRect(0, 0, size, size); ctx.save(); ctx.translate(c, c);
    ctx.fillStyle = 'rgba(255,255,255,.91)'; ctx.beginPath(); ctx.arc(0, 0, R + 4 * dpr, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = 'rgba(46,204,113,.65)'; ctx.lineWidth = 2 * dpr; ctx.beginPath(); ctx.arc(0, 0, R + 2 * dpr, 0, Math.PI * 2); ctx.stroke();
    ctx.strokeStyle = 'rgba(154,166,180,.55)'; ctx.lineWidth = 1 * dpr; ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.stroke();
    ctx.strokeStyle = '#ff6a00'; ctx.lineWidth = 5 * dpr; ctx.beginPath(); ctx.arc(0, 0, R - 4 * dpr, -Math.PI / 2, -Math.PI / 2 + heading * Math.PI / 180); ctx.stroke();
    for (let a = 0; a < 360; a += 5) {
      const major = a % 30 === 0; const rad = (a - 90) * Math.PI / 180; const r1 = R - 9 * dpr; const r2 = R - (major ? 19 : 14) * dpr;
      ctx.strokeStyle = Math.abs(((a - heading + 540) % 360) - 180) <= 15 ? '#ff6a00' : 'rgba(154,166,180,.85)';
      ctx.lineWidth = (major ? 2 : 1) * dpr; ctx.beginPath(); ctx.moveTo(Math.cos(rad) * r1, Math.sin(rad) * r1); ctx.lineTo(Math.cos(rad) * r2, Math.sin(rad) * r2); ctx.stroke();
    }
    ctx.fillStyle = '#2ecc71'; ctx.font = `${20 * dpr}px system-ui,sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    [['N', 0], ['NE', 45], ['E', 90], ['SE', 135], ['S', 180], ['SO', 225], ['O', 270], ['NO', 315]].forEach(([txt, a]) => {
      const rad = (a - 90) * Math.PI / 180; const rr = R * 0.78;
      ctx.fillStyle = a === 0 ? '#ff6a00' : '#66717d'; ctx.font = `${(a % 90 === 0 ? 18 : 14) * dpr}px system-ui,sans-serif`;
      ctx.fillText(txt, Math.cos(rad) * rr, Math.sin(rad) * rr);
    });
    ctx.save(); ctx.rotate(heading * Math.PI / 180);
    ctx.beginPath(); ctx.moveTo(0, -R * .42); ctx.lineTo(7 * dpr, 5 * dpr); ctx.lineTo(0, 0); ctx.lineTo(-7 * dpr, 5 * dpr); ctx.closePath();
    ctx.fillStyle = '#e53935'; ctx.fill(); ctx.beginPath(); ctx.moveTo(0, R * .42); ctx.lineTo(7 * dpr, -5 * dpr); ctx.lineTo(0, 0); ctx.lineTo(-7 * dpr, -5 * dpr); ctx.closePath(); ctx.fillStyle = '#1976d2'; ctx.fill(); ctx.restore();
    ctx.beginPath(); ctx.arc(0, 0, 3 * dpr, 0, Math.PI * 2); ctx.fillStyle = '#fff'; ctx.fill(); ctx.strokeStyle = '#555'; ctx.lineWidth = 1 * dpr; ctx.stroke();
    ctx.restore();
    const card = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'][Math.round(heading / 45) % 8];
    $('#nv-rose-deg').textContent = `${Math.round(heading)}°${card}`;
  }

  function boot() {
    if (window.__rutasMoterasReady || window.map) { buildShell(); installRouteObserver(); return; }
    let tries = 0;
    const t = setInterval(() => {
      tries++;
      if (window.__rutasMoterasReady || window.map) { clearInterval(t); buildShell(); installRouteObserver(); }
      else if (tries > 80) clearInterval(t);
    }, 100);
  }
  function installRouteObserver() {
    const a = document.getElementById('r-dist'), b = document.getElementById('r-time');
    if (a) new MutationObserver(syncRouteBar).observe(a, { childList: true, characterData: true, subtree: true });
    if (b) new MutationObserver(syncRouteBar).observe(b, { childList: true, characterData: true, subtree: true });
    syncRouteBar();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
})();
