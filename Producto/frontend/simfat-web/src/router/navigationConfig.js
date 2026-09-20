// requiresAuth: true means the destination needs a session. Anonymous visitors
// still see the item, rendered locked and linking to /login.
export const primaryNavigationLinks = [
  { label: 'Territorio', to: '/territorio', requiresAuth: false },
  { label: 'Comunidad', to: '/comunidad', requiresAuth: true },
  { label: 'Reportes', to: '/reportes', requiresAuth: true },
  // TODO(S3b): flip to false once AlertsPage has its anonymous read-only view.
  // Until then /alertas would call authenticated endpoints, so it stays locked.
  { label: 'Alertas', to: '/alertas', requiresAuth: true }
];

export const adminNavigationLinks = [
  { label: 'Accesos', to: '/admin/access-control', requiresAuth: true },
  { label: 'Reglas', to: '/admin/rules', requiresAuth: true },
  { label: 'Perfil', to: '/account', requiresAuth: true }
  // /admin/regions oculto: lógica de pérdida forestal obsoleta.
  // Pendiente rediseño como panel de monitorización por región.
];

export const homeQuickLinks = [
  ...primaryNavigationLinks,
  ...adminNavigationLinks
];

// Pure helper (unit-testable once a runner exists): an item is locked only for
// anonymous sessions and only when it requires authentication.
export function isNavItemLocked(item, isAuthenticated) {
  return !isAuthenticated && item.requiresAuth === true;
}

export const LOCKED_NAV_TITLE = 'Requiere iniciar sesión';
