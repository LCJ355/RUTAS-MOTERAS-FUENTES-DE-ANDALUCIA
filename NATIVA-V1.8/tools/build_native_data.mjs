import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolDir = path.dirname(fileURLToPath(import.meta.url));
const nativeRoot = path.resolve(toolDir, '..');
const webRoot = path.resolve(nativeRoot, '..', 'WEB-V3.40');
const dataPath = path.join(nativeRoot, 'app', 'src', 'main', 'assets', 'data.json');
const overridesPath = path.join(nativeRoot, 'data_overrides.json');

const WEB_DATA_FILES = [
  'fuentes_data.js',
  'municipios_andalucia.js',
  'postal_andalucia.js',
  'pueblos_andalucia.js'
];

function loadWebData() {
  const ctx = {};
  vm.createContext(ctx);
  for (const file of WEB_DATA_FILES) {
    vm.runInContext(fs.readFileSync(path.join(webRoot, file), 'utf8'), ctx, { timeout: 30000 });
  }
  const required = ['FD', 'TC', 'TP', 'PROVS', 'MUN', 'PED', 'POSTAL'];
  for (const name of required) {
    if (!(name in ctx)) throw new Error(`No se encontro ${name} en los datos web`);
  }
  return {
    FD: ctx.FD, TC: ctx.TC, TP: ctx.TP, PROVS: ctx.PROVS,
    MUN: ctx.MUN, PED: ctx.PED, POSTAL: ctx.POSTAL
  };
}

function provinceCode(name, data) {
  const { TC, TP, MUN, PED } = data;
  if (TC[name]) return TP[name] || '';
  if (MUN[name]) {
    const code = MUN[name][2];
    if (!code) throw new Error(`Municipio sin provincia: ${name}`);
    return code;
  }
  if (PED[name]) {
    const parent = PED[name][2];
    const code = TP[parent] || (MUN[parent] && MUN[parent][2]);
    if (!code) throw new Error(`Pedania sin provincia: ${name} (padre ${parent})`);
    return code;
  }
  throw new Error(`Pueblo sin coordenadas: ${name}`);
}

export function loadNativeOverrides() {
  if (!fs.existsSync(overridesPath)) return {};
  return JSON.parse(fs.readFileSync(overridesPath, 'utf8'));
}

export function buildNativeData(data, overrides = loadNativeOverrides()) {
  const { FD, TC, MUN, PED, POSTAL, PROVS } = data;

  const towns = {};
  for (const name of Object.keys(TC)) towns[name] = [TC[name][0], TC[name][1], provinceCode(name, data)];
  for (const name of Object.keys(MUN)) towns[name] = [MUN[name][0], MUN[name][1], provinceCode(name, data)];
  for (const name of Object.keys(PED)) towns[name] = [PED[name][0], PED[name][1], provinceCode(name, data)];

  // Correcciones propias de la app nativa (coordenadas/provincia) que no
  // deben perderse al regenerar desde los datos web.
  for (const [name, value] of Object.entries(overrides.towns || {})) {
    if (!(name in towns)) throw new Error(`Override de pueblo desconocido: ${name}`);
    if (!Array.isArray(value) || value.length !== 3) throw new Error(`Override invalido: ${name}`);
    towns[name] = [value[0], value[1], value[2]];
  }

  const postal = {};
  for (const name of Object.keys(POSTAL)) postal[name] = POSTAL[name];

  const provs = Object.values(PROVS);
  const fd = FD.map(f => [f.id, f.n, f.p, f.la, f.lo, f.t]);

  return { version: '1.0', fd, towns, postal, provs };
}

export function serializeNativeData(native) {
  return JSON.stringify(native);
}

function main() {
  const check = process.argv.includes('--check');
  const data = loadWebData();
  const native = buildNativeData(data);
  const serialized = serializeNativeData(native);

  const expectedFd = native.fd.length;
  const expectedTowns = Object.keys(native.towns).length;
  const expectedPostal = Object.keys(native.postal).length;
  console.log(`Web: ${expectedFd} fuentes, ${expectedTowns} pueblos, ${expectedPostal} CP, ${native.provs.length} provincias`);

  if (check) {
    if (!fs.existsSync(dataPath)) {
      console.error(`Falta ${path.relative(nativeRoot, dataPath)}; ejecuta node tools/build_native_data.mjs`);
      process.exit(1);
    }
    const current = fs.readFileSync(dataPath, 'utf8');
    if (current !== serialized) {
      console.error(`data.json esta desactualizado (${current.length} bytes, esperado ${serialized.length})`);
      process.exit(1);
    }
    console.log('data.json coincide con los datos web');
    return;
  }

  fs.mkdirSync(path.dirname(dataPath), { recursive: true });
  fs.writeFileSync(dataPath, serialized, 'utf8');
  console.log(`Escrito ${path.relative(nativeRoot, dataPath)} (${serialized.length} bytes)`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  main();
}
