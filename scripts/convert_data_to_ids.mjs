import fs from 'node:fs';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));

function loadFD() {
  const c = {}; vm.createContext(c);
  vm.runInContext(fs.readFileSync(root + 'fuentes_data.js', 'utf8'), c, { timeout: 20000 });
  return c.FD;
}

function indexKeysToIds(obj, FD) {
  const out = {};
  Object.entries(obj || {}).forEach(([key, value]) => {
    const i = Number(key);
    const f = Number.isInteger(i) ? FD[i] : FD.find(x => String(x.id) === String(key));
    if (f && value !== undefined && value !== null && value !== '') out[String(f.id)] = value;
  });
  return out;
}

const FD = loadFD();
const photosSrc = fs.readFileSync(root + 'photos_data.js', 'utf8');

const photosMatch = photosSrc.match(/window\.__PHOTOS\s*=\s*(\{[\s\S]*?\});/);
const capsMatch = photosSrc.match(/window\.__CAPTIONS\s*=\s*(\{[\s\S]*?\});/);
if (!photosMatch) throw new Error('No se encontró window.__PHOTOS en photos_data.js');

const photos = indexKeysToIds(JSON.parse(photosMatch[1]), FD);
const captions = indexKeysToIds(capsMatch ? JSON.parse(capsMatch[1]) : {}, FD);

if (Object.keys(photos).length === 0) throw new Error('No se pudieron mapear fotos a IDs');

const out = `window.__PHOTO_ID_VERSION=2;window.__PHOTOS=${JSON.stringify(photos)};window.__CAPTIONS=${JSON.stringify(captions)};`;
fs.writeFileSync(root + 'photos_data.js', out, 'utf8');

const accessSrc = fs.readFileSync(root + 'access_data.js', 'utf8');
const accessMatch = accessSrc.match(/window\.__ACCESS\s*=\s*(\{[\s\S]*?\});/);
const access = accessMatch ? indexKeysToIds(JSON.parse(accessMatch[1]), FD) : {};
fs.writeFileSync(root + 'access_data.js', `window.__ACCESS_ID_VERSION=2;window.__ACCESS=${JSON.stringify(access)};`, 'utf8');

console.log(`FD=${FD.length}; fotos=${Object.keys(photos).length}; leyendas=${Object.keys(captions).length}; accesos=${Object.keys(access).length}`);
