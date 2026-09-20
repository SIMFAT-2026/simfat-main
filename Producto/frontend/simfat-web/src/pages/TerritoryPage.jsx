import { Suspense, lazy } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import SectionTitle from '../components/SectionTitle';
import TerritoryMapPanel from '../features/territory/components/TerritoryMapPanel';
import { useTerritoryLayers } from '../features/territory/hooks/useTerritoryLayers';
import '../features/territory/territory.css';

const DashboardPage = lazy(() => import('./DashboardPage'));
const FOCUS_MAP = {
  alerts: ['ALERTS', 'REPORTS'],
  vegetation: ['NDVI', 'NDMI'],
  loss: ['ALERTS']
};

// Waits for the session bootstrap before mounting the content, so the data mode
// (public vs authenticated) is decided once with the final session state. This
// also prevents a stale stored token from triggering authenticated calls.
function TerritoryPage() {
  const { isAuthenticated, isBootstrapping } = useAuth();

  if (isBootstrapping) {
    return <div className="loading-state">Validando sesión...</div>;
  }

  // Keyed on the mode so a login/logout while mounted resets hook state.
  return <TerritoryContent key={isAuthenticated ? 'auth' : 'public'} isAuthenticated={isAuthenticated} />;
}

function TerritoryContent({ isAuthenticated }) {
  const [searchParams] = useSearchParams();
  const regionId = searchParams.get('regionId') || undefined;
  const focus = searchParams.get('focus') || '';

  const territory = useTerritoryLayers({
    initialRegionId: regionId,
    initialVisibleIndicators: FOCUS_MAP[focus] || undefined,
    publicMode: !isAuthenticated
  });

  return (
    <>
      <section className="page-container territory-page">
        <SectionTitle
          title="Monitorizacion territorial"
        />

        <TerritoryMapPanel
          regionOptions={territory.regionOptions}
          selectedRegionId={territory.selectedRegionId}
          setSelectedRegionId={territory.setSelectedRegionId}
          visibleIndicators={territory.visibleIndicators}
          toggleIndicator={territory.toggleIndicator}
          regionData={territory.selectedRegionData}
          loading={territory.loading}
          refreshing={territory.refreshing}
          error={territory.selectedRegionError}
          onRetry={territory.reloadSelectedRegion}
          // Anonymous mode still calls the risk-score and comuna history endpoints;
          // they must stay in the backend PublicEndpointPaths allowlist.
          readOnly={!isAuthenticated}
        />
      </section>

      {/* The analytics dashboard calls authenticated endpoints: signed-in users only. */}
      {isAuthenticated && (
        <div className="territory-dashboard-block">
          <Suspense fallback={<div className="loading-state">Cargando panel analitico...</div>}>
            <DashboardPage selectedRegionId={territory.selectedRegionId} />
          </Suspense>
        </div>
      )}
    </>
  );
}

export default TerritoryPage;
