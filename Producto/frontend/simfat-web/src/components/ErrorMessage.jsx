function ErrorMessage({ error, onRetry }) {
  const message = error?.message || 'Ocurrió un error inesperado.';

  return (
    <div className="error-state" role="alert">
      <p>{message}</p>
      {onRetry ? (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Reintentar
        </button>
      ) : null}
    </div>
  );
}

export default ErrorMessage;
