// Pueblos y pedanías de Andalucía
// Formato: { "Nombre": [lat, lon, "Municipio Padre"] }
var PED = {
  "Darrícal": [
    36.9196325,
    -3.0283065,
    "Alcolea"
  ],
  "Benamahoma": [
    36.7634575,
    -5.4653727,
    "Grazalema"
  ],
  "El Cañuelo": [
    37.5072249,
    -4.1628494,
    "Priego de Córdoba"
  ],
  "Zagrilla Alta": [
    37.4863471,
    -4.2426344,
    "Priego de Córdoba"
  ],
  "Benacebada": [
    37.2668046,
    -2.6808692,
    "Baza"
  ],
  "Mairena": [
    37.0038705,
    -3.044087,
    "Nevada"
  ],
  "Jabuguillo": [
    37.8592896,
    -6.5159947,
    "Aracena"
  ],
  "Charilla": [
    37.4958018,
    -3.9087039,
    "Alcalá la Real"
  ],
  "Bedmar": [
    37.8221501,
    -3.411775,
    "Bedmar y Garcíez"
  ],
  "Carchelejo": [
    37.6356068,
    -3.6405311,
    "Cárcheles"
  ],
  "Vadillo Castril": [
    37.9203077,
    -2.9295576,
    "Cazorla"
  ],
  "Valdemarín": [
    38.313603,
    -2.7267417,
    "Orcera"
  ],
  "Fontanar": [
    37.6724221,
    -2.9725099,
    "Pozo Alcón"
  ],
  "El Artuñedo": [
    38.1214834,
    -2.7078117,
    "Santiago-Pontones"
  ],
  "Santiago de la Espada": [
    38.1112962,
    -2.5513193,
    "Santiago-Pontones"
  ],
  "Casas de Carrasco": [
    38.1300471,
    -2.6904229,
    "Santiago-Pontones"
  ],
  "Pontones": [
    38.118434,
    -2.6702215,
    "Santiago-Pontones"
  ],
  "Montalvo": [
    38.1487749,
    -2.7042146,
    "Santiago-Pontones"
  ],
  "Poyotello": [
    38.1456383,
    -2.6234619,
    "Santiago-Pontones"
  ],
  "Las Gorgollitas": [
    38.2292266,
    -2.5318482,
    "Santiago-Pontones"
  ],
  "Carrasco": [
    38.267193,
    -2.718662,
    "Segura de la Sierra"
  ],
  "Setefilla": [
    37.7034212,
    -5.4975941,
    "Lora del Río"
  ],
  "Vegas de Almenara": [
    37.7097548,
    -5.3663691,
    "Peñaflor"
  ]
};

function getTownPos(name) {
  if (PED[name]) return { lat: PED[name][0], lon: PED[name][1], parent: PED[name][2] };
  if (TC[name]) return { lat: TC[name][0], lon: TC[name][1], parent: null };
  return null;
}
