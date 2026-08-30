import SectionTitle from '../components/SectionTitle';
import TerritoryMapPanel from '../features/territory/components/TerritoryMapPanel';
import { useTerritoryLayers } from '../features/territory/hooks/useTerritoryLayers';
import '../features/territory/territory.css';
import './PublicMonitoringPage.css';

// Ruta publica de portafolio (spec: Fase 1B) — sin login, sin AuthContext ni
// acciones de escritura. Reusa el mismo hook/panel que /territorio pero en
// publicMode (endpoints /api/territory/public/*) y readOnly (sin botones de
// sincronizacion ni acciones que requieran sesion).
function PublicMonitoringPage() {
  const territory = useTerritoryLayers({ publicMode: true });

  return (
    <div className="public-monitoring-page">
      <header className="public-monitoring-header">
        <img src="/logo-aifbn.png" alt="AIFBN" className="public-monitoring-logo" />
        <div className="public-monitoring-brand-text">
          <h1>NoFires</h1>
          <span>Monitoreo territorial en vivo — vista pública de demostración</span>
        </div>
      </header>

      <section className="page-container territory-page">
        <SectionTitle title="Monitorización territorial" />

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
          readOnly
        />
      </section>
    </div>
  );
}

export default PublicMonitoringPage;
