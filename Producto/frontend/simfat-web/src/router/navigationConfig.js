export const primaryNavigationLinks = [
  { label: 'Territorio', to: '/territorio' },
  { label: 'Comunidad', to: '/comunidad' },
  { label: 'Reportes', to: '/reportes' },
  { label: 'Alertas', to: '/alertas' }
];

export const adminNavigationLinks = [
  { label: 'Accesos', to: '/admin/access-control' },
  { label: 'Regiones', to: '/admin/regions' },
  { label: 'Perdida Forestal', to: '/admin/forest-loss' },
  { label: 'Reglas', to: '/admin/rules' }
];

export const homeQuickLinks = [
  ...primaryNavigationLinks,
  ...adminNavigationLinks
];
