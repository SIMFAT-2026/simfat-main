function LoadingSpinner({ label = 'Loading...' }) {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <div className="spinner" />
      <span>{label}</span>
    </div>
  );
}

export default LoadingSpinner;
