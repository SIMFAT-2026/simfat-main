function EmptyState({ title = 'Sin datos', description = 'Prueba ajustando los filtros o creando un nuevo registro.' }) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

export default EmptyState;
