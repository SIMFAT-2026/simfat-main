import { useMemo, useRef, useState } from 'react';
import ConfirmModal from '../components/ConfirmModal';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import FilterBar from '../components/FilterBar';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import CommunityChatPanel from '../features/community/chat/CommunityChatPanel';
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

const BOARD_CARD_POSITIONS = [
  { x: 5, y: 8, rotate: -2 },
  { x: 36, y: 6, rotate: 1.5 },
  { x: 18, y: 43, rotate: 2 },
  { x: 58, y: 36, rotate: -1.5 },
  { x: 68, y: 10, rotate: 2.5 },
  { x: 8, y: 62, rotate: -1 }
];

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

function priorityClass(priority) {
  return String(priority || 'MEDIA').toLowerCase();
}

function fallbackBoardPosition(index) {
  return BOARD_CARD_POSITIONS[index % BOARD_CARD_POSITIONS.length];
}

function attachmentKind(file) {
  if (!file) return 'file';
  if (file.type?.startsWith('image/')) return 'image';
  if (file.type === 'application/pdf') return 'pdf';
  return 'file';
}

function postAttachment(post) {
  if (!post?.attachmentUrl) return null;
  return {
    url: post.attachmentUrl,
    name: post.attachmentName || 'Adjunto',
    type: post.attachmentContentType || '',
    size: post.attachmentSize || 0,
    kind: post.attachmentImage ? 'image' : 'file'
  };
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
  const boardRef = useRef(null);
  const fileInputRef = useRef(null);
  const [filterRegionId, setFilterRegionId] = useState('');
  const [boardForm, setBoardForm] = useState(initialBoardForm);
  const [resourceForm, setResourceForm] = useState(initialResourceForm);
  const [contactForm, setContactForm] = useState(initialContactForm);
  const [boardAttachment, setBoardAttachment] = useState(null);
  const [boardPositions, setBoardPositions] = useState({});
  const [dragState, setDragState] = useState(null);
  const [selectedPost, setSelectedPost] = useState(null);
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

  function handleAttachmentChange(event) {
    const file = event.target.files?.[0] || null;
    if (!file) {
      setBoardAttachment(null);
      return;
    }

    if (boardAttachment?.url) URL.revokeObjectURL(boardAttachment.url);
    setBoardAttachment({
      file,
      name: file.name,
      type: file.type,
      size: file.size,
      kind: attachmentKind(file),
      url: URL.createObjectURL(file)
    });
  }

  function clearBoardAttachment() {
    if (boardAttachment?.url) URL.revokeObjectURL(boardAttachment.url);
    setBoardAttachment(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  }

  async function submitBoard(event) {
    event.preventDefault();
    feedback.clear();

    try {
      const submittedAttachment = boardAttachment;
      const created = await createBoard({
        title: boardForm.title.trim(),
        message: boardForm.message.trim(),
        priority: boardForm.priority,
        regionId: boardForm.regionId,
        author: 'Equipo comunitario',
        attachmentFile: submittedAttachment?.file,
        attachmentPreviewUrl: submittedAttachment?.url,
        attachmentName: submittedAttachment?.name,
        attachmentContentType: submittedAttachment?.type,
        attachmentSize: submittedAttachment?.size,
        attachmentImage: submittedAttachment?.kind === 'image'
      });

      if (created?.id) {
        setBoardPositions((prev) => ({ ...prev, [created.id]: { x: 4, y: 8, rotate: -1 } }));
      }

      if (submittedAttachment?.url) URL.revokeObjectURL(submittedAttachment.url);
      if (fileInputRef.current) fileInputRef.current.value = '';
      setBoardAttachment(null);
      setBoardForm(initialBoardForm);
      feedback.showSuccess('Aviso comunitario publicado en el mural.');
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

  function startPostDrag(event, post, index) {
    if (!boardRef.current) return;
    const boardRect = boardRef.current.getBoundingClientRect();
    const current = boardPositions[post.id] || fallbackBoardPosition(index);
    setDragState({
      id: post.id,
      pointerId: event.pointerId,
      boardRect,
      offsetX: event.clientX - (boardRect.left + (current.x / 100) * boardRect.width),
      offsetY: event.clientY - (boardRect.top + (current.y / 100) * boardRect.height),
      startedAt: { x: event.clientX, y: event.clientY }
    });
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function movePost(event) {
    if (!dragState) return;
    const x = ((event.clientX - dragState.boardRect.left - dragState.offsetX) / dragState.boardRect.width) * 100;
    const y = ((event.clientY - dragState.boardRect.top - dragState.offsetY) / dragState.boardRect.height) * 100;
    setBoardPositions((prev) => ({
      ...prev,
      [dragState.id]: {
        ...(prev[dragState.id] || {}),
        x: Math.max(2, Math.min(78, x)),
        y: Math.max(2, Math.min(74, y))
      }
    }));
  }

  function endPostDrag(event, post) {
    if (!dragState) return;
    const moved = Math.abs(event.clientX - dragState.startedAt.x) + Math.abs(event.clientY - dragState.startedAt.y);
    setDragState(null);
    if (moved < 6) setSelectedPost(post);
  }

  async function confirmDelete() {
    if (!pendingDelete.id || !pendingDelete.type) {
      return;
    }

    try {
      if (pendingDelete.type === 'board') {
        await removeBoard(pendingDelete.id);
        setSelectedPost((prev) => (prev?.id === pendingDelete.id ? null : prev));
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

      <article className="dashboard-card community-mural-card">
        <div className="community-mural-layout">
          <section className="community-board-panel">
            <div className="community-board-header">
              <div>
                <h3>Mural comunitario</h3>
                <p>Arrastra las notas para ordenar el tablero. Presiona una tarjeta para ver el detalle.</p>
              </div>
              <span>{filteredBoard.length} publicaciones</span>
            </div>

            <div ref={boardRef} className="community-board-surface" aria-label="Mural comunitario interactivo">
              <div className="community-board-pin top-left" />
              <div className="community-board-pin top-right" />
              <div className="community-board-pin bottom-left" />
              <div className="community-board-pin bottom-right" />

              {loading ? <LoadingSpinner label="Cargando mural..." /> : null}
              {!loading && filteredBoard.length === 0 ? (
                <EmptyState title="Sin avisos comunitarios" description="Publica el primer aviso para esta region." />
              ) : null}
              {!loading && filteredBoard.map((post, index) => {
                const position = boardPositions[post.id] || fallbackBoardPosition(index);
                const attachment = postAttachment(post);
                return (
                  <button
                    key={post.id}
                    type="button"
                    className={`community-sticky-note ${priorityClass(post.priority)}`}
                    style={{ left: `${position.x}%`, top: `${position.y}%`, transform: `rotate(${position.rotate ?? fallbackBoardPosition(index).rotate}deg)` }}
                    onPointerDown={(event) => startPostDrag(event, post, index)}
                    onPointerMove={movePost}
                    onPointerUp={(event) => endPostDrag(event, post)}
                    onPointerCancel={() => setDragState(null)}
                  >
                    <span className="community-note-tape" />
                    <strong>{post.title}</strong>
                    <small>{regionMap[post.regionId] || post.regionId || 'Sin region'} · {formatDate(post.publishedAt)}</small>
                    <p>{post.message}</p>
                    <span className={priorityBadge(post.priority)}>{post.priority}</span>
                    {attachment ? <span className="community-note-attachment">📎 {attachment.kind === 'image' ? 'Imagen' : 'Archivo'}</span> : null}
                  </button>
                );
              })}
            </div>
          </section>

          <aside className="community-editor-panel">
            <h3>Nueva publicación</h3>
            <p>Crea una nota breve para pegarla en el mural comunitario.</p>
            <form className="community-editor-form" onSubmit={submitBoard}>
              <label>
                Titulo
                <input name="title" value={boardForm.title} onChange={handleBoardInput} required />
              </label>

              <div className="community-editor-row">
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
              </div>

              <label>
                Mensaje
                <textarea name="message" rows={5} value={boardForm.message} onChange={handleBoardInput} required />
              </label>

              <div className="community-attachment-box">
                <input ref={fileInputRef} type="file" onChange={handleAttachmentChange} />
                {boardAttachment ? (
                  <div className="community-attachment-preview">
                    {boardAttachment.kind === 'image' ? <img src={boardAttachment.url} alt="Vista previa adjunta" /> : <span>📎</span>}
                    <div>
                      <strong>{boardAttachment.name}</strong>
                      <small>{Math.ceil(boardAttachment.size / 1024)} KB</small>
                    </div>
                    <button type="button" className="btn btn-secondary" onClick={clearBoardAttachment}>Quitar</button>
                  </div>
                ) : (
                  <p>Adjunta una imagen o archivo de apoyo para la publicación.</p>
                )}
              </div>

              <div className="form-actions">
                <button type="submit" className="btn">
                  Pegar en mural
                </button>
              </div>
            </form>
          </aside>
        </div>
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

      {selectedPost ? (
        <div className="community-post-modal-backdrop" role="presentation" onClick={() => setSelectedPost(null)}>
          <article className="community-post-modal" role="dialog" aria-modal="true" aria-label="Publicación comunitaria" onClick={(event) => event.stopPropagation()}>
            <button type="button" className="panel-close" onClick={() => setSelectedPost(null)} aria-label="Cerrar">×</button>
            <span className={priorityBadge(selectedPost.priority)}>{selectedPost.priority}</span>
            <h3>{selectedPost.title}</h3>
            <p className="community-post-meta">
              {regionMap[selectedPost.regionId] || selectedPost.regionId || 'Sin region'} · {formatDate(selectedPost.publishedAt)} · {selectedPost.author}
            </p>
            <p>{selectedPost.message}</p>
            {postAttachment(selectedPost) ? (
              <div className="community-post-attachment">
                <h4>Adjunto</h4>
                {postAttachment(selectedPost).kind === 'image' ? (
                  <img src={postAttachment(selectedPost).url} alt={postAttachment(selectedPost).name} />
                ) : null}
                <a href={postAttachment(selectedPost).url} target="_blank" rel="noreferrer">
                  📎 {postAttachment(selectedPost).name}
                </a>
              </div>
            ) : (
              <p className="community-post-empty-attachment">Esta publicación no contiene adjuntos.</p>
            )}
            <div className="form-actions">
              <button type="button" className="btn btn-danger" onClick={() => setPendingDelete({ type: 'board', id: selectedPost.id })}>
                Eliminar publicación
              </button>
            </div>
          </article>
        </div>
      ) : null}

      <ConfirmModal
        isOpen={Boolean(pendingDelete.id)}
        title="Eliminar elemento"
        message="Esta accion no se puede deshacer."
        confirmLabel="Eliminar"
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete({ type: '', id: '' })}
      />

      <CommunityChatPanel />
    </section>
  );
}

export default CommunityPage;
