function MetricCard({ label, value }) {
  return (
    <article className="metric-card">
      <p>{label}</p>
      <strong>{value}</strong>
    </article>
  );
}

export default MetricCard;
