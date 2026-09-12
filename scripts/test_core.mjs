import fs from 'node:fs';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));

const fail = (msg) => { console.error('FAIL: ' + msg); failures++; };

let failures = 0;
let passed = 0;
const ok = (cond, msg) => { if (cond) { passed++; } else { fail(msg); } };

function loadData() {
  const c = {}; vm.createContext(c);
  vm.runInContext(fs.readFileSync(root + 'fuentes_data.js', 'utf8'), c, { timeout: 20000 });
  return c;
}

function hoistSandbox() {
  // Evaluate only the pure helpers present in index.html's inline script that
  // do not require a browser DOM. We extract them textually and run in a VM.
  const html = fs.readFileSync(root + 'index.html', 'utf8');
  const js = html.match(/<script>([\s\S]*?)<\/script>\s*<\/body>/)[1] || '';

  const grab = (name) => {
    const re = new RegExp('function ' + name + '\\([^)]*\\)[\\s\\S]*?\\n}', '');
    return (js.match(re) || [])[0] || '';
  };

  const ctx = {};
  vm.createContext(ctx);
  vm.runInContext(
    'globalThis.__KEYS = ' + js.match(/const KEYS = (\{[^;]*\});/)[1] +
    '; globalThis.__simplify = ' + grab('simplifyPolyline') +
    '; globalThis.__serialize = ' + grab('serializeEndpoint')
  , ctx, { timeout: 20000 });
  return { KEYS: ctx.__KEYS, simplifyPolyline: ctx.__simplify, serializeEndpoint: ctx.__serialize };
}

// restoreEndpoint references FD and getPos; provide them in a small sandbox.
function serializeScenario(data) {
  const ctx = { FD: data.FD, getPos: (n) => (data.TC[n] ? { la: data.TC[n][0], lo: data.TC[n][1], p: n } : null), TC: data.TC };
  vm.createContext(ctx);
  vm.runInContext('globalThis.__restore = ' + hoistRestore(), ctx, { timeout: 20000 });
  return ctx.__restore;
}

function hoistRestore() {
  const html = fs.readFileSync(root + 'index.html', 'utf8');
  const js = html.match(/<script>([\s\S]*?)<\/script>\s*<\/body>/)[1] || '';
  const re = new RegExp('function restoreEndpoint\\([^)]*\\)[\\s\\S]*?\\n}', '');
  return (js.match(re) || [])[0] || '';
}

// Simulate the legacy->ID migration that migrateLegacyStorage performs in index.html.
function migrationScenario(data) {
  const store = new Map();
  const localStorage = { getItem: k => store.has(k) ? store.get(k) : null, setItem: (k, v) => store.set(k, String(v)), removeItem: k => store.delete(k), key: i => [...store.keys()][i] || null, get length() { return store.size; } };
  const safeLS = { get: k => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); return true; } catch { return false; } }, remove: k => { try { localStorage.removeItem(k); } catch { } }, keys: () => [...store.keys()] };
  const idKey = (type, id) => `rm:${type}:${id}`;
  const ctx = { FD: data.FD, safeLS, idKey, console };
  vm.createContext(ctx);
  const js = fs.readFileSync(root + 'index.html', 'utf8').match(/<script>([\s\S]*?)<\/script>\s*<\/body>/)[1];
  vm.runInContext(
    'var KEYS = ' + js.match(/const KEYS = (\{[^;]*\});/)[1] +
    '; globalThis.__KEYS = KEYS' +
    '; ' + js.match(/function migrateLegacyStorage\(\)[^{]*\{[\s\S]*?\n\}/)[0].replace(/function migrateLegacyStorage\(\)/, 'function __migrate()')
  , ctx, { timeout: 20000 });
  ctx._localStorage = localStorage;
  return ctx;
}

function legacyMigrationTests() {
  const data = loadData();
  const ctx = migrationScenario(data);
  const mig = ctx.__migrate, KEYS = ctx.__KEYS;
  const f = data.FD[0];
  const oldPhoto = 'data:image/png;base64,AAAA', oldNote = 'nota legada';
  const ls = ctx._localStorage;
  // seed legacy index-based keys for the first fountain and an old cookie
  ls.setItem('fp_0', oldPhoto);
  ls.setItem('fn_0', oldNote);
  ls.setItem('cb_accepted', '1');
  ls.setItem('rm_routes', '[{"schema":1,"name":"X","startTown":0}]');
  mig();
  const migratedKey = 'rm:photo:' + f.id;
  ok(ls.getItem(migratedKey) === oldPhoto, `foto legada migrada a rm:photo:${f.id}`);
  ok(ls.getItem('rm:note:' + f.id) === oldNote, `nota legada migrada a rm:note:${f.id}`);
  ok(ls.getItem('fp_0') === null, 'clave legada fp_0 eliminada tras migrar');
  ok(ls.getItem(KEYS.cookie) === '1', 'consentimiento de cookies migrado');
  ok(ls.getItem('cb_accepted') === null, 'cb_accepted eliminado tras migrar');
  ok(ls.getItem(KEYS.migrated) === '1', 'marcador de migracion establecido');
  // idempotence: second call should not duplicate
  mig();
  ok(ls.getItem(migratedKey) === oldPhoto, 'migracion es idempotente');
}

function retirementSWTest() {
  const sw = fs.readFileSync(root + 'sw.js', 'utf8');
  ok(!/precache|addAll|URLS/.test(sw), 'SW de retiro ya no precachea archivos');
  ok(/rm-cache-v8/.test(sw), 'SW de retiro borra solo rm-cache-v8');
  ok(/unregister/.test(sw), 'SW de retiro se desregistra en activate');
  ok(!/caches\.keys\(\)\.then\(keys => Promise\.all\(keys\.filter\(k => k !== CACHE\)/.test(sw), 'SW de retiro no borra todas las cache del origen');
}

function publishedDataTest() {
  // Published data must be ID-versioned so it survives fountain reordering.
  const fdById = new Map(loadData().FD.map(f => [String(f.id), f]));
  const photosCtx = { window: {} }; vm.createContext(photosCtx);
  vm.runInContext(fs.readFileSync(root + 'photos_data.js', 'utf8'), photosCtx, { timeout: 20000 });
  ok(photosCtx.window.__PHOTO_ID_VERSION === 2, 'photos_data.js debe declarar __PHOTO_ID_VERSION=2');
  const photoIds = Object.keys(photosCtx.window.__PHOTOS || {});
  ok(photoIds.length > 0, 'photos_data.js debe tener fotos');
  ok(photoIds.every(k => fdById.has(k)), 'Todas las claves de __PHOTOS deben ser ids reales de fontaneria');
  const capIds = Object.keys(photosCtx.window.__CAPTIONS || {});
  ok(capIds.every(k => fdById.has(k)), 'Todas las claves de __CAPTIONS deben ser ids reales');
  const accessCtx = { window: {} }; vm.createContext(accessCtx);
  vm.runInContext(fs.readFileSync(root + 'access_data.js', 'utf8'), accessCtx, { timeout: 20000 });
  ok(accessCtx.window.__ACCESS_ID_VERSION === 2, 'access_data.js debe declarar __ACCESS_ID_VERSION=2');
  const accessIds = Object.keys(accessCtx.window.__ACCESS || {});
  ok(accessIds.every(k => fdById.has(k)), 'Todas las claves de __ACCESS deben ser ids reales');
}

const data = loadData();
const pure = hoistSandbox();
legacyMigrationTests();
retirementSWTest();
publishedDataTest();

console.log('== Datos: IDs estables y conteos reales ==');
ok(data.FD.length === 13719, 'FD debe ser 13719, es ' + data.FD.length);
ok(new Set(data.FD.map(x => x.id)).size === data.FD.length, 'Todos los id deben ser unicos');
ok(data.FD.every(x => Number.isInteger(x.id) && x.id > 0), 'Todos los id deben ser enteros positivos');
ok(data.FD.every(x => Number.isFinite(x.la) && Number.isFinite(x.lo)), 'Todas las coordenadas deben ser finitas');
ok(Object.keys(data.TC).length === 712, 'TC debe ser 712, es ' + Object.keys(data.TC).length);
ok(Object.keys(data.TP).length === 711, 'TP debe ser 711, es ' + Object.keys(data.TP).length);

console.log('== Municipios anomalos corregidos (TC cerca de sus fuentes) ==');
for (const [name, maxKm] of [['Viso, El', 60], ['Victoria, La', 5], ['Villares, Los', 40]]) {
  const fslist = data.FD.filter(x => x.p === name);
  const [la, lo] = data.TC[name];
  const maxDevKm = Math.max(...fslist.map(x => Math.hypot(x.la - la, x.lo - lo) * 111));
  ok(maxDevKm <= maxKm, `${name}: desviacion maxima ${maxDevKm.toFixed(1)}km > ${maxKm}km`);
}

console.log('== simplifyPolyline: nunca excede el maximo y conserva extremos ==');
const pts = Array.from({ length: 300 }, (_, i) => ({ lat: i / 10, lng: Math.sin(i / 20) }));
const simplified = pure.simplifyPolyline(pts, 10);
ok(simplified.length === 10, 'Debe devolver exactamente 10 puntos, es ' + simplified.length);
ok(simplified[0].lat === pts[0].lat && simplified[simplified.length - 1].lat === pts[pts.length - 1].lat, 'Debe conservar el primer y ultimo punto');
const small = pure.simplifyPolyline(pts.slice(0, 5), 10);
ok(small.length === 5, 'Listas menores que el maximo no deben cambiar');

console.log('== KEYS namespaced (evitan colision con otras apps del origen) ==');
ok(pure.KEYS.routes === 'rm:routes', 'KEYS.routes debe ser rm:routes');
ok(pure.KEYS.token === 'rm:github-token', 'KEYS.token debe ser rm:github-token');
ok(pure.KEYS.migrated === 'rm:migration:v2', 'KEYS.migrated debe ser rm:migration:v2');

console.log('== serializeEndpoint: extremos tipados ==');
const serialize = pure.serializeEndpoint;
const serTown = serialize({ kind: 'town', n: 'Abla', p: 'Abla', la: 37.1, lo: -2.7 });
ok(serTown.kind === 'town' && serTown.n === 'Abla', 'Pueblo se serializa como kind town con nombre');
const firstFountain = data.FD[0];
const serFountain = serialize(firstFountain);
ok(serFountain.kind === 'fountain' && serFountain.id === firstFountain.id, 'Fuente se serializa como kind fountain con id estable');
const serGps = serialize({ kind: 'gps', n: '📍 Mi ubicación', la: 37.2, lo: -3.1, p: '📍 Mi ubicación' });
ok(serGps.kind === 'gps' && Number.isFinite(serGps.la) && Number.isFinite(serGps.lo), 'GPS serializa lat/lon');
ok(serialize(null) === null, 'Extremo nulo se serializa como null');

console.log('== restoreEndpoint: extremos tipados se restauran sin buscar en FD ==');
const restore = serializeScenario(data);
const town = data.FD.find(x => x.p === 'Abla');
const restoredTown = restore({ kind: 'town', n: 'Abla', p: 'Abla', la: data.TC['Abla'][0], lo: data.TC['Abla'][1] });
ok(restoredTown && restoredTown.kind === 'town' && restoredTown.p === 'Abla', 'Pueblo se restaura por coordenadas/nombre');
const restoredFountain = restore({ kind: 'fountain', id: firstFountain.id });
ok(restoredFountain && restoredFountain.id === firstFountain.id, 'Fuente se restaura por id estable (cualquier orden)');
const restoredGps = restore({ kind: 'gps', n: 'GPS', la: 36.9, lo: -4.2, p: '' });
ok(restoredGps && restoredGps.kind === 'gps' && restoredGps.la === 36.9, 'GPS se restaura por coordenadas');
ok(restore(null) === null, 'Extremo nulo se restaura como null');
const unknownFountain = restore({ kind: 'fountain', id: 99999999 });
ok(unknownFountain === null, 'Fuente con id inexistente se restaura como null (no rompe)');

console.log(`\nResultado: ${passed} pasadas, ${failures} fallidas`);
process.exit(failures ? 1 : 0);
