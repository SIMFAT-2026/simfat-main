import { useMemo, useState } from 'react';

const RISK_ORDER = { BAJO: 1, PREVENTIVO: 2, MEDIO: 2, ALTO: 3, CRITICO: 4 };

function compareValues(a, b) {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  if (typeof a === 'string' && typeof b === 'string') return a.localeCompare(b, 'es');
  return a < b ? -1 : a > b ? 1 : 0;
}

const PAGE_SIZES = [25, 50, 100];

function DataTable({ columns, rows, rowKey, actions, defaultSortKey, defaultSortDir = 'desc', pageSize: initialPageSize = 25 }) {
  const [sortKey, setSortKey] = useState(defaultSortKey || null);
  const [sortDir, setSortDir] = useState(defaultSortDir);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(initialPageSize);

  function handleSort(key) {
    if (sortKey === key) {
      setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
    setPage(1);
  }

  const sortedRows = useMemo(() => {
    if (!sortKey) return rows;
    const col = columns.find((c) => c.key === sortKey);
    return [...rows].sort((a, b) => {
      const aVal = col?.sortValue ? col.sortValue(a) : a[sortKey];
      const bVal = col?.sortValue ? col.sortValue(b) : b[sortKey];
      const cmp = compareValues(aVal, bVal);
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [rows, sortKey, sortDir, columns]);

  const totalPages = Math.max(1, Math.ceil(sortedRows.length / pageSize));
  const safePage = Math.min(page, totalPages);
  const pageRows = sortedRows.slice((safePage - 1) * pageSize, safePage * pageSize);

  function SortIcon({ colKey }) {
    if (sortKey !== colKey) return <span className="sort-icon sort-icon--idle">↕</span>;
    return <span className="sort-icon sort-icon--active">{sortDir === 'asc' ? '↑' : '↓'}</span>;
  }

  return (
    <div className="datatable-wrapper">
      <div className="table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={col.sortable ? 'th-sortable' : ''}
                  onClick={col.sortable ? () => handleSort(col.key) : undefined}
                >
                  {col.header}
                  {col.sortable ? <SortIcon colKey={col.key} /> : null}
                </th>
              ))}
              {actions ? <th>Acciones</th> : null}
            </tr>
          </thead>
          <tbody>
            {pageRows.map((row, index) => (
              <tr key={typeof rowKey === 'function' ? rowKey(row) : row[rowKey] || index}>
                {columns.map((col) => (
                  <td key={col.key}>{col.render ? col.render(row) : row[col.key]}</td>
                ))}
                {actions ? <td>{actions(row)}</td> : null}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="datatable-footer">
        <div className="datatable-info">
          {sortedRows.length === 0
            ? 'Sin resultados'
            : `${(safePage - 1) * pageSize + 1}–${Math.min(safePage * pageSize, sortedRows.length)} de ${sortedRows.length}`}
        </div>
        <div className="datatable-pagination">
          <button className="btn btn-secondary btn-sm" onClick={() => setPage(1)} disabled={safePage === 1}>«</button>
          <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={safePage === 1}>‹</button>
          <span className="datatable-page-label">Pág. {safePage} / {totalPages}</span>
          <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={safePage === totalPages}>›</button>
          <button className="btn btn-secondary btn-sm" onClick={() => setPage(totalPages)} disabled={safePage === totalPages}>»</button>
        </div>
        <div className="datatable-pagesize">
          <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }}>
            {PAGE_SIZES.map((s) => <option key={s} value={s}>{s} por página</option>)}
          </select>
        </div>
      </div>
    </div>
  );
}

export { RISK_ORDER };
export default DataTable;
