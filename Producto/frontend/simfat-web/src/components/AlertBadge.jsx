const CLASS_BY_LEVEL = {
  BAJO: 'badge badge-low',
  MEDIO: 'badge badge-medium',
  ALTO: 'badge badge-high',
  CRITICO: 'badge badge-critical'
};

function AlertBadge({ level }) {
  return <span className={CLASS_BY_LEVEL[level] || 'badge'}>{level || 'N/A'}</span>;
}

export default AlertBadge;
