// Shared empty list so "no assigned regions" keeps the same reference across
// renders; a fresh [] would change the loader's dependencies on every render.
const NO_REGIONS = Object.freeze([]);

// Regions a user may see in the community module: null means unrestricted.
export function resolveAccessibleRegions(isAdmin, regionIds) {
  if (isAdmin) return null;
  return regionIds ?? NO_REGIONS;
}
