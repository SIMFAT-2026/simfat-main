// Regiones y comunas cubiertas por NoFires (Biobío, Ñuble, Araucanía).
// comunaId coincide con el gadmGid usado en el choropleth.

function fmt(nombre) {
  return nombre
    .replace(/([ÁÉÍÓÚÑÜA-Z])/g, ' $1')
    .trim()
    .replace(/\b(De|La|Las|Los|Del)\b/g, (m) => m.toLowerCase());
}

const BIOBIO_COMUNAS = [
  'CHL.6.1.1_1', 'CHL.6.1.2_1', 'CHL.6.1.3_1', 'CHL.6.1.4_1',
  'CHL.6.1.5_1', 'CHL.6.1.6_1', 'CHL.6.1.7_1', 'CHL.6.2.1_1',
  'CHL.6.2.2_1', 'CHL.6.2.3_1', 'CHL.6.2.4_1', 'CHL.6.2.5_1',
  'CHL.6.2.6_1', 'CHL.6.2.7_1', 'CHL.6.2.8_1', 'CHL.6.2.9_1',
  'CHL.6.2.10_1', 'CHL.6.2.11_1', 'CHL.6.2.12_1', 'CHL.6.2.13_1',
  'CHL.6.2.14_1', 'CHL.6.3.1_1', 'CHL.6.3.2_1', 'CHL.6.3.3_1',
  'CHL.6.3.4_1', 'CHL.6.3.5_1', 'CHL.6.3.6_1', 'CHL.6.3.7_1',
  'CHL.6.3.8_1', 'CHL.6.3.9_1', 'CHL.6.3.10_1', 'CHL.6.3.11_1',
  'CHL.6.3.12_1',
];

const NUBLE_COMUNAS = [
  'CHL.13.1.1_1', 'CHL.13.1.2_1', 'CHL.13.1.3_1', 'CHL.13.1.4_1',
  'CHL.13.1.5_1', 'CHL.13.1.6_1', 'CHL.13.1.7_1', 'CHL.13.1.8_1',
  'CHL.13.1.9_1', 'CHL.13.2.1_1', 'CHL.13.2.2_1', 'CHL.13.2.3_1',
  'CHL.13.2.4_1', 'CHL.13.2.5_1', 'CHL.13.2.6_1', 'CHL.13.2.7_1',
  'CHL.13.3.1_1', 'CHL.13.3.2_1', 'CHL.13.3.3_1', 'CHL.13.3.4_1',
  'CHL.13.3.5_1',
];

const ARAUCANIA_COMUNAS = [
  'CHL.3.1.1_1', 'CHL.3.1.2_1', 'CHL.3.1.3_1', 'CHL.3.1.4_1',
  'CHL.3.1.5_1', 'CHL.3.1.6_1', 'CHL.3.1.7_1', 'CHL.3.1.8_1',
  'CHL.3.1.9_1', 'CHL.3.1.10_1', 'CHL.3.1.11_1', 'CHL.3.1.12_1',
  'CHL.3.1.13_1', 'CHL.3.1.14_1', 'CHL.3.1.15_1', 'CHL.3.1.16_1',
  'CHL.3.1.17_1', 'CHL.3.1.18_1', 'CHL.3.1.19_1', 'CHL.3.1.20_1',
  'CHL.3.1.21_1', 'CHL.3.2.1_1', 'CHL.3.2.2_1', 'CHL.3.2.3_1',
  'CHL.3.2.4_1', 'CHL.3.2.5_1', 'CHL.3.2.6_1', 'CHL.3.2.7_1',
  'CHL.3.2.8_1', 'CHL.3.2.9_1', 'CHL.3.2.10_1', 'CHL.3.2.11_1',
];

const NOMBRE_POR_ID = {
  'CHL.6.1.1_1': 'Arauco',
  'CHL.6.1.2_1': 'Cañete',
  'CHL.6.1.3_1': 'Contulmo',
  'CHL.6.1.4_1': 'Curanilahue',
  'CHL.6.1.5_1': 'Lebu',
  'CHL.6.1.6_1': 'Los Álamos',
  'CHL.6.1.7_1': 'Tirúa',
  'CHL.6.2.1_1': 'Alto Biobío',
  'CHL.6.2.2_1': 'Antuco',
  'CHL.6.2.3_1': 'Cabrero',
  'CHL.6.2.4_1': 'Laja',
  'CHL.6.2.5_1': 'Los Ángeles',
  'CHL.6.2.6_1': 'Mulchén',
  'CHL.6.2.7_1': 'Nacimiento',
  'CHL.6.2.8_1': 'Negrete',
  'CHL.6.2.9_1': 'Quilaco',
  'CHL.6.2.10_1': 'Quilleco',
  'CHL.6.2.11_1': 'San Rosendo',
  'CHL.6.2.12_1': 'Santa Bárbara',
  'CHL.6.2.13_1': 'Tucapel',
  'CHL.6.2.14_1': 'Yumbel',
  'CHL.6.3.1_1': 'Chiguayante',
  'CHL.6.3.2_1': 'Concepción',
  'CHL.6.3.3_1': 'Coronel',
  'CHL.6.3.4_1': 'Florida',
  'CHL.6.3.5_1': 'Hualpén',
  'CHL.6.3.6_1': 'Hualqui',
  'CHL.6.3.7_1': 'Lota',
  'CHL.6.3.8_1': 'Penco',
  'CHL.6.3.9_1': 'San Pedro de la Paz',
  'CHL.6.3.10_1': 'Santa Juana',
  'CHL.6.3.11_1': 'Talcahuano',
  'CHL.6.3.12_1': 'Tomé',
  'CHL.13.1.1_1': 'Bulnes',
  'CHL.13.1.2_1': 'Chillán',
  'CHL.13.1.3_1': 'Chillán Viejo',
  'CHL.13.1.4_1': 'El Carmen',
  'CHL.13.1.5_1': 'Pemuco',
  'CHL.13.1.6_1': 'Pinto',
  'CHL.13.1.7_1': 'Quillón',
  'CHL.13.1.8_1': 'San Ignacio',
  'CHL.13.1.9_1': 'Yungay',
  'CHL.13.2.1_1': 'Cobquecura',
  'CHL.13.2.2_1': 'Coelemu',
  'CHL.13.2.3_1': 'Ninhue',
  'CHL.13.2.4_1': 'Portezuelo',
  'CHL.13.2.5_1': 'Quirihue',
  'CHL.13.2.6_1': 'Ranquil',
  'CHL.13.2.7_1': 'Treguaco',
  'CHL.13.3.1_1': 'Coihueco',
  'CHL.13.3.2_1': 'Ñiquén',
  'CHL.13.3.3_1': 'San Carlos',
  'CHL.13.3.4_1': 'San Fabián',
  'CHL.13.3.5_1': 'San Nicolás',
  'CHL.3.1.1_1': 'Carahue',
  'CHL.3.1.2_1': 'Cholchol',
  'CHL.3.1.3_1': 'Cunco',
  'CHL.3.1.4_1': 'Curarrehue',
  'CHL.3.1.5_1': 'Freire',
  'CHL.3.1.6_1': 'Galvarino',
  'CHL.3.1.7_1': 'Gorbea',
  'CHL.3.1.8_1': 'Lautaro',
  'CHL.3.1.9_1': 'Loncoche',
  'CHL.3.1.10_1': 'Melipeuco',
  'CHL.3.1.11_1': 'Nueva Imperial',
  'CHL.3.1.12_1': 'Padre Las Casas',
  'CHL.3.1.13_1': 'Perquenco',
  'CHL.3.1.14_1': 'Pitrufquén',
  'CHL.3.1.15_1': 'Pucón',
  'CHL.3.1.16_1': 'Saavedra',
  'CHL.3.1.17_1': 'Temuco',
  'CHL.3.1.18_1': 'Teodoro Schmidt',
  'CHL.3.1.19_1': 'Toltén',
  'CHL.3.1.20_1': 'Vilcún',
  'CHL.3.1.21_1': 'Villarrica',
  'CHL.3.2.1_1': 'Angol',
  'CHL.3.2.2_1': 'Collipulli',
  'CHL.3.2.3_1': 'Curacautín',
  'CHL.3.2.4_1': 'Ercilla',
  'CHL.3.2.5_1': 'Lonquimay',
  'CHL.3.2.6_1': 'Los Sauces',
  'CHL.3.2.7_1': 'Lumaco',
  'CHL.3.2.8_1': 'Purén',
  'CHL.3.2.9_1': 'Renaico',
  'CHL.3.2.10_1': 'Traiguén',
  'CHL.3.2.11_1': 'Victoria',
};

function toComunas(ids) {
  return ids
    .map((id) => ({ value: id, label: NOMBRE_POR_ID[id] || fmt(id) }))
    .sort((a, b) => a.label.localeCompare(b.label, 'es'));
}

export const REGIONES = [
  { value: 'biobio',    label: 'Bío-Bío',   comunas: toComunas(BIOBIO_COMUNAS) },
  { value: 'nuble',     label: 'Ñuble',      comunas: toComunas(NUBLE_COMUNAS) },
  { value: 'araucania', label: 'Araucanía',  comunas: toComunas(ARAUCANIA_COMUNAS) },
];

export function getComunasByRegion(regionCode) {
  return REGIONES.find((r) => r.value === regionCode)?.comunas ?? [];
}

export function getLabelForComuna(comunaId) {
  return NOMBRE_POR_ID[comunaId] ?? comunaId;
}

export function getLabelForRegion(regionCode) {
  return REGIONES.find((r) => r.value === regionCode)?.label ?? regionCode;
}
