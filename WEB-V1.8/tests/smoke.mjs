import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const exists = rel => fs.existsSync(path.join(root, rel));

const html = read('index.html');
const manifest = JSON.parse(read('manifest.json'));
const sw = read('sw.js');

assert.match(html, /<title>Rutas Moteras V-1\.8/);
assert.match(html, />V-1\.8<\/button>/);
assert.match(html, /href="native\.css"/);
assert.match(html, /src="native\.js"/);
assert.match(html, /vendor\/leaflet\.js/);
assert.doesNotMatch(html, /unpkg\.com/);
assert.match(html, /searchTowns, searchFountains/);
assert.match(html, /map, tileLayers/);
assert.equal(manifest.version, '1.8.0');
assert.equal(manifest.short_name, 'Rutas Moteras');
assert.match(sw, /rutas-moteras-v1\.8-3/);

for (const rel of [
  'native.css', 'native.js', 'server.js', 'sw.js', 'manifest.json',
  'fuentes_data.js', 'photos_data.js', 'access_data.js',
  'vendor/leaflet.js', 'vendor/leaflet.css',
  'vendor/leaflet.markercluster.js',
  'assets/hd_logo.png', 'assets/brujula_needle.png'
]) assert.ok(exists(rel), `falta ${rel}`);

console.log('WEB-V1.8 smoke: OK');
