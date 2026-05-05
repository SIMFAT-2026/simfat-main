import { useMemo, useState } from 'react';
import ConfirmModal from '../components/ConfirmModal';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import FilterBar from '../components/FilterBar';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useFeedback } from '../hooks';
import { useCommunityData } from '../features/community/hooks/useCommunityData';

const BOARD_PRIORITIES = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA'];
const RESOURCE_CATEGORIES = ['GUIA', 'PROTOCOLO', 'CAPACITACION', 'MATERIAL'];

const initialBoardForm = {
  title: '',
  message: '',
  priority: 'MEDIA',
  regionId: ''
};

const initialResourceForm = {
  title: '',
  category: 'GUIA',
  url: '',
  regionId: '',
  description: ''
};

const initialContactForm = {
  name: '',
  organization: '',
  phone: '',
  email: '',
  regionId: '',
  protocol: ''
};

function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value || '-';
  }
  return date.toLocaleString('es-CL');
}

function priorityBadge(priority) {
  const normalized = String(priority || '').toUpperCase();
  if (normalized === 'CRITICA') return 'badge badge-critical';
  if (normalized === 'ALTA') return 'badge badge-high';
  if (normalized === 'MEDIA') return 'badge badge-medium';
  if (normalized === 'BAJA') return 'badge badge-low';
  return 'badge';
}

function CommunityPage() {
  const {
    regions,
    board,
    resources,
    contacts,
    source,
    loading,
    error,
    reload,
    createBoard,
    createResource,
    createContact,
    removeBoard,
    removeResource,
    removeContact
  } = useCommunityData();

  const feedback = useFeedback();
  const [filterRegionId, setFilterRegionId] = useState('');
  const [boardForm, setBoardForm] = useState(initialBoardForm);
  const [resourceForm, setResourceForm] = useState(initialResourceForm);
  const [contactForm, setContactForm] = useState(initialContactForm);
  const [pendingDelete, setPendingDelete] = useState({ type: '', id: '' });

  const regionMap = useMemo(
    () =>
      regions.reduce((acc, region) => {
        acc[region.id] = region.nombre;
        return acc;
      }, {}),
    [regions]
  );

  const filteredBoard = useMemo(
    () => board.filter((item) => !filterRegionId || item.regionId === filterRegionId),
    [board, filterRegionId]
  );

  const filteredResources = useMemo(
    () => resources.filter((item) => !filterRegionId || item.regionId === filterRegionId),
    [resources, filterRegionId]
  );

  const filteredContacts = useMemo(
    () => contacts.filter((item) => !filterRegionId || item.regionId === filterRegionId),
    [contacts, filterRegionId]
  );

  const boardColumns = useMemo(
    () => [
      { key: 'title', header: 'Titulo' },
      {
        key: 'priority',
        header: 'Prioridad',
        render: (row) => <span className={priorityBadge(row.priority)}>{row.priority}</span>
      },
      { key: 'regionId', header: 'Region', render: (row) => regionMap[row.regionId] || row.regionId || '-' },
      { key: 'publishedAt', header: 'Publicado', render: (row) => formatDate(row.publishedAt) },
      { key: 'author', header: 'Autor' },
      { key: 'message', header: 'Mensaje' }
    ],
    [regionMap]
  );

  const resourceColumns = useMemo(
    () => [
      { key: 'title', header: 'Recurso' },
      { key: 'category', header: 'Categoria' },
      { key: 'regionId', header: 'Region', render: (row) => regionMap[row.regionId] || row.regionId || '-' },
      {
        key: 'url',
        header: 'Enlace',
        render: (row) => (
          <a href={row.url} target="_blank" rel="noreferrer">
            Abrir
          </a>
        )
      },
      { key: 'description', header: 'Descripcion' }
    ],
    [regionMap]
  );

  const contactColumns = useMemo(
    () => [
      { key: 'name', header: 'Contacto' },
      { key: 'organization', header: 'Organizacion' },
      { key: 'phone', header: 'Telefono' },
      { key: 'email', header: 'Correo' },
      { key: 'regionId', header: 'Region', render: (row) => regionMap[row.regionId] || row.regionId || '-' },
      { key: 'protocol', header: 'Protocolo' }
    ],
    [regionMap]
  );

  function handleBoardInput(event) {
    const { name, value } = event.target;
    setBoardForm((prev) => ({ ...prev, [name]: value }));
  }

  function handleResourceInput(event) {
    const { name, value } = event.target;
    setResourceForm((prev) => ({ ...prev, [name]: value }));
  }

  function handleContactInput(event) {
    const { name, value } = event.target;
    setContactForm((prev) => ({ ...prev, [name]: value }));
  }

  async function submitBoard(event) {
    event.preventDefault();
    feedback.clear();

    try {
      await createBoard({
        title: boardForm.title.trim(),
        message: boardForm.message.trim(),
        priority: boardForm.priority,
        regionId: boardForm.regionId,
        author: 'Equipo comunitario'
      });
      setBoardForm(initialBoardForm);
      feedback.showSuccess('Aviso comunitario publicado.');
    } catch (err) {
      feedback.showError(err.message || 'No se pudo publicar el aviso.');
    }
  }

  async function submitResource(event) {
    event.preventDefault();
    feedback.clear();

    try {
      await createResource({
        title: resourceForm.title.trim(),
        category: resourceForm.category,
        url: resourceForm.url.trim(),
        regionId: resourceForm.regionId,
        description: resourceForm.description.trim()
      });
      setResourceForm(initialResourceForm);
      feedback.showSuccess('Recurso comunitario registrado.');
    } catch (err) {
      feedback.showError(err.message || 'No se pudo guardar el recurso.');
    }
  }

  async function submitContact(event) {
    event.preventDefault();
    feedback.clear();

    try {
      await createContact({
        name: contactForm.name.trim(),
        organization: contactForm.organization.trim(),
        phone: contactForm.phone.trim(),
        email: contactForm.email.trim(),
        regionId: contactForm.regionId,
        protocol: contactForm.protocol.trim()
      });
      setContactForm(initialContactForm);
      feedback.showSuccess('Contacto/protocolo agregado.');
    } catch (err) {
      feedback.showError(err.message || 'No se pudo guardar el contacto.');
    }
  }

  async function confirmDelete() {
    if (!pendingDelete.id || !pendingDelete.type) {
      return;
    }

    try {
      if (pendingDelete.type === 'board') {
        await removeBoard(pendingDelete.id);
      } else if (pendingDelete.type === 'resource') {
        await removeResource(pendingDelete.id);
      } else if (pendingDelete.type === 'contact') {
        await removeContact(pendingDelete.id);
      }
      feedback.showSuccess('Elemento eliminado correctamente.');
    } catch (err) {
      feedback.showError(err.message || 'No se pudo eliminar el elemento.');
    } finally {
      setPendingDelete({ type: '', id: '' });
    }
  }

  return (
    <section className="page-container">
      <SectionTitle
        title="Coordinacion comunitaria"
        subtitle="Mural operativo, biblioteca de recursos y contactos/protocolos por territorio"
      />

      <p className="community-source-note">
        Origen de datos: {source === 'backend' ? 'backend comunitario' : 'fallback local de continuidad operativa'}.
      </p>

      {feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}
      {error ? <p className="feedback feedback-error">{error}</p> : null}

      <FilterBar>
        <label>
          Filtrar por region
          <select value={filterRegionId} onChange={(event) => setFilterRegionId(event.target.value)}>
            <option value="">Todas</option>
            {regions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.nombre}
              </option>
            ))}
          </select>
        </label>

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={reload} disabled={loading}>
            {loading ? 'Recargando...' : 'Recargar modulo'}
          </button>
        </div>
      </FilterBar>

      <article className="dashboard-card">
        <h3>Mural comunitario</h3>
        <form className="form-grid" onSubmit={submitBoard}>
          <label>
            Titulo
            <input name="title" value={boardForm.title} onChange={handleBoardInput} required />
          </label>

          <label>
            Prioridad
            <select name="priority" value={boardForm.priority} onChange={handleBoardInput}>
              {BOARD_PRIORITIES.map((priority) => (
                <option key={priority} value={priority}>
                  {priority}
                </option>
              ))}
            </select>
          </label>

          <label>
            Region
            <select name="regionId" value={boardForm.regionId} onChange={handleBoardInput} required>
              <option value="">Seleccione region</option>
              {regions.map((region) => (
                <option key={region.id} value={region.id}>
                  {region.nombre}
                </option>
              ))}
            </select>
          </label>

          <label className="full-width">
            Mensaje
            <textarea name="message" rows={3} value={boardForm.message} onChange={handleBoardInput} required />
          </label>

          <div className="form-actions">
            <button type="submit" className="btn">
              Publicar aviso
            </button>
          </div>
        </form>

        {loading ? <LoadingSpinner label="Cargando mural..." /> : null}
        {!loading && filteredBoard.length === 0 ? (
          <EmptyState title="Sin avisos comunitarios" description="Publica el primer aviso para esta region." />
        ) : null}
        {!loading && filteredBoard.length > 0 ? (
          <DataTable
            columns={boardColumns}
            rows={filteredBoard}
            rowKey="id"
            actions={(row) => (
              <button
                type="button"
                className="btn btn-danger"
                onClick={() => setPendingDelete({ type: 'board', id: row.id })}
              >
                Eliminar
              </button>
            )}
          />
        ) : null}
      </article>

      <article className="dashboard-card">
        <h3>Biblioteca de recursos</h3>
        <form className="form-grid" onSubmit={submitResource}>
          <label>
            Titulo
            <input name="title" value={resourceForm.title} onChange={handleResourceInput} required />
          </label>

          <label>
            Categoria
            <select name="category" value={resourceForm.category} onChange={handleResourceInput}>
              {RESOURCE_CATEGORIES.map((category) => (
                <option key={category} value={category}>
                  {category}
                </option>
              ))}
            </select>
          </label>

          <label>
            Region
            <select name="regionId" value={resourceForm.regionId} onChange={handleResourceInput} required>
              <option value="">Seleccione region</option>
              {regions.map((region) => (
                <option key={region.id} value={region.id}>
                  {region.nombre}
                </option>
              ))}
            </select>
          </label>

          <label>
            URL
            <input name="url" type="url" value={resourceForm.url} onChange={handleResourceInput} required />
          </label>

          <label className="full-width">
            Descripcion
            <textarea
              name="description"
              rows={2}
              value={resourceForm.description}
              onChange={handleResourceInput}
              required
            />
          </label>

          <div className="form-actions">
            <button type="submit" className="btn">
              Guardar recurso
            </button>
          </div>
        </form>

        {loading ? <LoadingSpinner label="Cargando recursos..." /> : null}
        {!loading && filteredResources.length === 0 ? (
          <EmptyState title="Sin recursos" description="Registra guias o protocolos comunitarios para comenzar." />
        ) : null}
        {!loading && filteredResources.length > 0 ? (
          <DataTable
            columns={resourceColumns}
            rows={filteredResources}
            rowKey="id"
            actions={(row) => (
              <button
                type="button"
                className="btn btn-danger"
                onClick={() => setPendingDelete({ type: 'resource', id: row.id })}
              >
                Eliminar
              </button>
            )}
          />
        ) : null}
      </article>

      <article className="dashboard-card">
        <h3>Contactos y protocolos</h3>
        <form className="form-grid" onSubmit={submitContact}>
          <label>
            Nombre
            <input name="name" value={contactForm.name} onChange={handleContactInput} required />
          </label>

          <label>
            Organizacion
            <input name="organization" value={contactForm.organization} onChange={handleContactInput} required />
          </label>

          <label>
            Telefono
            <input name="phone" value={contactForm.phone} onChange={handleContactInput} required />
          </label>

          <label>
            Correo
            <input name="email" type="email" value={contactForm.email} onChange={handleContactInput} required />
          </label>

          <label>
            Region
            <select name="regionId" value={contactForm.regionId} onChange={handleContactInput} required>
              <option value="">Seleccione region</option>
              {regions.map((region) => (
                <option key={region.id} value={region.id}>
                  {region.nombre}
                </option>
              ))}
            </select>
          </label>

          <label className="full-width">
            Protocolo
            <textarea name="protocol" rows={2} value={contactForm.protocol} onChange={handleContactInput} required />
          </label>

          <div className="form-actions">
            <button type="submit" className="btn">
              Guardar contacto
            </button>
          </div>
        </form>

        {loading ? <LoadingSpinner label="Cargando contactos..." /> : null}
        {!loading && filteredContacts.length === 0 ? (
          <EmptyState title="Sin contactos" description="Agrega responsables y protocolos de escalamiento." />
        ) : null}
        {!loading && filteredContacts.length > 0 ? (
          <DataTable
            columns={contactColumns}
            rows={filteredContacts}
            rowKey="id"
            actions={(row) => (
              <button
                type="button"
                className="btn btn-danger"
                onClick={() => setPendingDelete({ type: 'contact', id: row.id })}
              >
                Eliminar
              </button>
            )}
          />
        ) : null}
      </article>

      {loading && !error ? <LoadingSpinner label="Sincronizando modulo comunitario..." /> : null}
      {!loading && error && source === 'backend' ? <ErrorMessage error={{ message: error }} onRetry={reload} /> : null}

      <ConfirmModal
        isOpen={Boolean(pendingDelete.id)}
        title="Eliminar elemento"
        message="Esta accion no se puede deshacer."
        confirmLabel="Eliminar"
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete({ type: '', id: '' })}
      />
    </section>
  );
}

export default CommunityPage;
