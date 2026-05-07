function EmptyState({ title = 'No data found', description = 'Try adjusting filters or creating a new record.' }) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

export default EmptyState;
