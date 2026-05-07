function ErrorMessage({ error, onRetry }) {
  const message = error?.message || 'An unexpected error occurred.';

  return (
    <div className="error-state" role="alert">
      <p>{message}</p>
      {onRetry ? (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Retry
        </button>
      ) : null}
    </div>
  );
}

export default ErrorMessage;
