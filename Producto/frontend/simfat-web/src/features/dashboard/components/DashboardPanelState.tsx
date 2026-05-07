import EmptyState from '../../../components/EmptyState';
import ErrorMessage from '../../../components/ErrorMessage';

interface DashboardPanelStateProps {
  loading: boolean;
  error: string;
  isEmpty: boolean;
  emptyTitle: string;
  emptyDescription: string;
  onRetry: () => Promise<void> | void;
  children: JSX.Element;
}

function DashboardPanelState({
  loading,
  error,
  isEmpty,
  emptyTitle,
  emptyDescription,
  onRetry,
  children
}: DashboardPanelStateProps) {
  if (loading) {
    return (
      <div className="dashboard-skeleton" role="status" aria-live="polite" aria-label="Cargando panel">
        <div className="dashboard-skeleton-line dashboard-skeleton-line-lg" />
        <div className="dashboard-skeleton-line" />
        <div className="dashboard-skeleton-line" />
      </div>
    );
  }

  if (error) {
    return <ErrorMessage error={{ message: error }} onRetry={onRetry} />;
  }

  if (isEmpty) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return children;
}

export default DashboardPanelState;
