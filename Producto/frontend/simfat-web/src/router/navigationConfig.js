export const primaryNavigationLinks = [
  { label: 'Territorio', to: '/territorio' },
  { label: 'Comunidad', to: '/comunidad' },
  { label: 'Reportes', to: '/reportes' },
  { label: 'Alertas', to: '/alertas' }
];

export const adminNavigationLinks = [
  { label: 'Accesos', to: '/admin/access-control' },
  { label: 'Reglas', to: '/admin/rules' },
  { label: 'Perfil', to: '/account' }
  // /admin/regions oculto: lógica de pérdida forestal obsoleta.
  // Pendiente rediseño como panel de monitorización por región.
];

export const homeQuickLinks = [
  ...primaryNavigationLinks,
  ...adminNavigationLinks
];
