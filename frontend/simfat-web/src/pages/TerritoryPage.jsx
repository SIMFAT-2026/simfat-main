import { Suspense, lazy } from 'react';
import { useSearchParams } from 'react-router-dom';
import SectionTitle from '../components/SectionTitle';
import TerritoryMapPanel from '../features/territory/components/TerritoryMapPanel';
import { useTerritoryLayers } from '../features/territory/hooks/useTerritoryLayers';
import '../features/territory/territory.css';

const DashboardPage = lazy(() => import('./DashboardPage'));
const FOCUS_MAP = {
  alerts: ['ALERTS', 'REPORTS'],
  vegetation: ['NDVI', 'NDMI'],
  loss: ['LOSS', 'ALERTS']
};

function TerritoryPage() {
  const [searchParams] = useSearchParams();
  const regionId = searchParams.get('regionId') || undefined;
  const focus = searchParams.get('focus') || '';

  const territory = useTerritoryLayers({
    initialRegionId: regionId,
    initialVisibleIndicators: FOCUS_MAP[focus] || undefined
  });

  return (
    <>
      <section className="page-container territory-page">
        <SectionTitle
          title="Monitorizacion territorial"
          subtitle="Mapa principal con capas NDVI, NDMI, perdida forestal, alertas y reportes ciudadanos"
        />

        <p className="territory-page-note">
          Carga progresiva por region con cache temporal para minimizar consumo de APIs y mantener bajo costo
          operacional.
        </p>

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
        />
      </section>

      <div className="territory-dashboard-block">
        <Suspense fallback={<div className="loading-state">Cargando panel analitico...</div>}>
          <DashboardPage />
        </Suspense>
      </div>
    </>
  );
}

export default TerritoryPage;
