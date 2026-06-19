import { useMemo, useRef, useState } from 'react';
import ConfirmModal from '../components/ConfirmModal';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import FilterBar from '../components/FilterBar';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import CommunityChatPanel from '../features/community/chat/CommunityChatPanel';
import { useFeedback } from '../hooks';
import { useCommunityData } from '../features/community/hooks/useCommunityData';
import { getComunasByRegion, getLabelForComuna, getLabelForRegion } from '../data/territorioChile';

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
  regionId: '',
  comunaId: '',
  description: ''
};

const initialContactForm = {
  name: '',
  organization: '',
  phone: '',
  email: '',
  regionId: '',
  comunaId: '',
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

function contactWhatsAppHref(phone) {
  const digits = String(phone || '').replace(/\D/g, '');
  return digits ? `https://wa.me/${digits}` : null;
}

function contactMailHref(email) {
  const address = String(email || '').trim();
  return address ? `mailto:${address}` : null;
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

function formatFileSize(value) {
  if (!value) return '';
  if (value < 1024 * 1024) return `${Math.ceil(value / 1024)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function isPdfResource(resource) {
  const contentType = String(resource?.fileContentType || '').toLowerCase();
  const name = String(resource?.fileName || resource?.url || '').toLowerCase();
  return contentType.includes('pdf') || name.endsWith('.pdf');
}

function isDocxResource(resource) {
  const contentType = String(resource?.fileContentType || '').toLowerCase();
  const name = String(resource?.fileName || resource?.url || '').toLowerCase();
  return contentType.includes('wordprocessingml') || name.endsWith('.docx');
}

function normalizeRegionText(value = '') {
  return String(value)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

function resolveRegionCode(regionId = '', regionName = '') {
  const source = normalizeRegionText(`${regionId} ${regionName}`);
  if (source.includes('araucania')) return 'araucania';
  if (source.includes('nuble') || source.includes('ñuble')) return 'nuble';
  if (source.includes('biobio') || source.includes('bio-bio') || source.includes('bio bio')) return 'biobio';
  return regionId;
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
  const resourceFileInputRef = useRef(null);
  const [filterRegionId, setFilterRegionId] = useState('');
  const [boardForm, setBoardForm] = useState(initialBoardForm);
  const [resourceForm, setResourceForm] = useState(initialResourceForm);
  const [contactForm, setContactForm] = useState(initialContactForm);
  const [boardAttachment, setBoardAttachment] = useState(null);
  const [resourceFile, setResourceFile] = useState(null);
  const [boardPositions, setBoardPositions] = useState({});
  const [dragState, setDragState] = useState(null);
  const [selectedPost, setSelectedPost] = useState(null);
  const [selectedResourceId, setSelectedResourceId] = useState('');
  const [resourceCatalogRegionId, setResourceCatalogRegionId] = useState('');
  const [resourceCatalogComunaId, setResourceCatalogComunaId] = useState('');
  const [contactSearchTerm, setContactSearchTerm] = useState('');
  const [contactRegionFilterId, setContactRegionFilterId] = useState('');
  const [contactComunaFilterId, setContactComunaFilterId] = useState('');
  const [pendingDelete, setPendingDelete] = useState({ type: '', id: '' });

  const regionMap = useMemo(
    () =>
      regions.reduce((acc, region) => {
        acc[region.id] = region.nombre;
        acc[resolveRegionCode(region.id, region.nombre)] = region.nombre;
        return acc;
      }, {}),
    [regions]
  );

  const resourceRegionOptions = useMemo(
    () => {
      const unique = new Map();
      regions.forEach((region) => {
        const id = resolveRegionCode(region.id, region.nombre);
        if (!unique.has(id)) {
          unique.set(id, {
            id,
            label: region.nombre || getLabelForRegion(id)
          });
        }
      });
      return [...unique.values()];
    },
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

  const resourceComunas = useMemo(
    () => getComunasByRegion(resourceForm.regionId),
    [resourceForm.regionId]
  );

  const canSelectResourceComuna = resourceComunas.length > 0;

  const resourceCatalogComunas = useMemo(
    () => getComunasByRegion(resourceCatalogRegionId),
    [resourceCatalogRegionId]
  );

  const catalogResources = useMemo(
    () =>
      filteredResources.filter((item) => {
        const regionMatches = !resourceCatalogRegionId || item.regionId === resourceCatalogRegionId;
        const comunaMatches = !resourceCatalogComunaId || item.comunaId === resourceCatalogComunaId;
        return regionMatches && comunaMatches;
      }),
    [filteredResources, resourceCatalogRegionId, resourceCatalogComunaId]
  );

  const resourceTree = useMemo(() => {
    const grouped = new Map();

    catalogResources.forEach((resource) => {
      const regionId = resource.regionId || 'sin-region';
      const comunaId = resource.comunaId || 'sin-comuna';

      if (!grouped.has(regionId)) {
        grouped.set(regionId, {
          id: regionId,
          label: regionMap[regionId] || getLabelForRegion(regionId) || 'Sin region',
          comunas: new Map()
        });
      }

      const regionGroup = grouped.get(regionId);
      if (!regionGroup.comunas.has(comunaId)) {
        regionGroup.comunas.set(comunaId, {
          id: comunaId,
          label: getLabelForComuna(comunaId) || 'Sin comuna',
          resources: []
        });
      }

      regionGroup.comunas.get(comunaId).resources.push(resource);
    });

    return [...grouped.values()].map((region) => ({
      ...region,
      comunas: [...region.comunas.values()]
    }));
  }, [catalogResources, regionMap]);

  const selectedResource = useMemo(() => {
    if (catalogResources.length === 0) return null;
    return catalogResources.find((item) => item.id === selectedResourceId) || catalogResources[0];
  }, [catalogResources, selectedResourceId]);

  const contactFormComunas = useMemo(
    () => getComunasByRegion(contactForm.regionId),
    [contactForm.regionId]
  );

  const contactFilterComunas = useMemo(
    () => getComunasByRegion(contactRegionFilterId),
    [contactRegionFilterId]
  );

  const filteredContacts = useMemo(
    () => {
      const query = contactSearchTerm.trim().toLowerCase();
      return contacts.filter((item) => {
        const globalRegionMatches = !filterRegionId || item.regionId === filterRegionId;
        const agendaRegionMatches = !contactRegionFilterId || item.regionId === contactRegionFilterId;
        const agendaComunaMatches = !contactComunaFilterId || item.comunaId === contactComunaFilterId;
        const queryMatches = !query || [
          item.name,
          item.organization,
          item.phone,
          item.email,
          item.protocol,
          regionMap[item.regionId],
          getLabelForComuna(item.comunaId)
        ].some((value) => String(value || '').toLowerCase().includes(query));
        return globalRegionMatches && agendaRegionMatches && agendaComunaMatches && queryMatches;
      });
    },
    [contacts, filterRegionId, contactRegionFilterId, contactComunaFilterId, contactSearchTerm, regionMap]
  );

  function handleBoardInput(event) {
    const { name, value } = event.target;
    setBoardForm((prev) => ({ ...prev, [name]: value }));
  }

  function handleResourceInput(event) {
    const { name, value } = event.target;
    setResourceForm((prev) => ({
      ...prev,
      [name]: value,
      ...(name === 'regionId' ? { comunaId: '' } : {})
    }));
  }

  function handleResourceCatalogRegion(event) {
    setResourceCatalogRegionId(event.target.value);
    setResourceCatalogComunaId('');
    setSelectedResourceId('');
  }

  function handleContactInput(event) {
    const { name, value } = event.target;
    setContactForm((prev) => ({
      ...prev,
      [name]: value,
      ...(name === 'regionId' ? { comunaId: '' } : {})
    }));
  }

  function handleContactRegionFilter(event) {
    setContactRegionFilterId(event.target.value);
    setContactComunaFilterId('');
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

  function handleResourceFileChange(event) {
    const file = event.target.files?.[0] || null;
    if (!file) {
      setResourceFile(null);
      return;
    }

    const lowerName = file.name.toLowerCase();
    const isAllowed =
      file.type === 'application/pdf' ||
      file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
      lowerName.endsWith('.pdf') ||
      lowerName.endsWith('.docx');

    if (!isAllowed) {
      event.target.value = '';
      setResourceFile(null);
      feedback.showError('Solo se permiten recursos en formato PDF o DOCX.');
      return;
    }

    setResourceFile(file);
  }

  function clearResourceFile() {
    setResourceFile(null);
    if (resourceFileInputRef.current) resourceFileInputRef.current.value = '';
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
      if (!resourceFile) {
        feedback.showError('Adjunta un archivo PDF o DOCX para registrar el recurso.');
        return;
      }

      const created = await createResource({
        title: resourceForm.title.trim(),
        category: resourceForm.category,
        regionId: resourceForm.regionId,
        comunaId: resourceForm.comunaId,
        description: resourceForm.description.trim(),
        file: resourceFile
      });
      if (created?.id) setSelectedResourceId(created.id);
      setResourceForm(initialResourceForm);
      clearResourceFile();
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
        setSelectedResourceId((prev) => (prev === pendingDelete.id ? '' : prev));
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

      <article className="dashboard-card community-resource-card">
        <div className="community-resource-shell">
          <aside className="community-resource-uploader">
            <h3>Biblioteca de recursos</h3>
            <p>Sube documentos PDF o DOCX y asocialos a una region y comuna.</p>

            <form className="community-resource-form" onSubmit={submitResource}>
              <label>
                Titulo
                <input name="title" value={resourceForm.title} onChange={handleResourceInput} required />
              </label>

              <div className="community-editor-row">
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
                    {resourceRegionOptions.map((region) => (
                      <option key={region.id} value={region.id}>
                        {region.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <label>
                Comuna
                <select
                  name="comunaId"
                  value={resourceForm.comunaId}
                  onChange={handleResourceInput}
                  required
                  disabled={!canSelectResourceComuna}
                >
                  <option value="">
                    {resourceForm.regionId ? 'Seleccione comuna' : 'Seleccione una region primero'}
                  </option>
                  {resourceComunas.map((comuna) => (
                    <option key={comuna.value} value={comuna.value}>
                      {comuna.label}
                    </option>
                  ))}
                </select>
                {resourceForm.regionId && !canSelectResourceComuna ? (
                  <small className="field-error">No hay comunas configuradas para esta region.</small>
                ) : null}
              </label>

              <label>
                Descripcion
                <textarea
                  name="description"
                  rows={4}
                  value={resourceForm.description}
                  onChange={handleResourceInput}
                  required
                />
              </label>

              <div className="community-resource-filebox">
                <input
                  ref={resourceFileInputRef}
                  type="file"
                  accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  onChange={handleResourceFileChange}
                  required
                />
                {resourceFile ? (
                  <div className="community-resource-file-preview">
                    <span>{resourceFile.name.toLowerCase().endsWith('.pdf') ? 'PDF' : 'DOCX'}</span>
                    <div>
                      <strong>{resourceFile.name}</strong>
                      <small>{formatFileSize(resourceFile.size)}</small>
                    </div>
                    <button type="button" className="btn btn-secondary" onClick={clearResourceFile}>Quitar</button>
                  </div>
                ) : (
                  <p>Formatos permitidos: PDF y DOCX.</p>
                )}
              </div>

              <div className="form-actions">
                <button type="submit" className="btn">
                  Subir recurso
                </button>
              </div>
            </form>
          </aside>

          <section className="community-resource-viewer-panel">
            <aside className="community-resource-catalog" aria-label="Catalogo de recursos">
              <div className="community-resource-catalog-header">
                <div>
                  <h4>Catalogo de recursos</h4>
                  <p>Filtra por territorio y selecciona un documento.</p>
                </div>
                <span>{catalogResources.length}</span>
              </div>

              <div className="community-resource-catalog-filters">
                <label>
                  Region
                  <select value={resourceCatalogRegionId} onChange={handleResourceCatalogRegion}>
                    <option value="">Todas</option>
                    {resourceRegionOptions.map((region) => (
                      <option key={region.id} value={region.id}>
                        {region.label}
                      </option>
                    ))}
                  </select>
                </label>

                <label>
                  Comuna
                  <select
                    value={resourceCatalogComunaId}
                    onChange={(event) => {
                      setResourceCatalogComunaId(event.target.value);
                      setSelectedResourceId('');
                    }}
                    disabled={!resourceCatalogRegionId}
                  >
                    <option value="">{resourceCatalogRegionId ? 'Todas' : 'Seleccione region'}</option>
                    {resourceCatalogComunas.map((comuna) => (
                      <option key={comuna.value} value={comuna.value}>
                        {comuna.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              {loading ? <LoadingSpinner label="Cargando recursos..." /> : null}
              {!loading && catalogResources.length === 0 ? (
                <EmptyState title="Sin recursos" description="No hay documentos para los filtros seleccionados." />
              ) : null}
              {!loading && catalogResources.length > 0 ? (
                <div className="community-resource-tree">
                  {resourceTree.map((region) => (
                    <div key={region.id} className="community-resource-tree-region">
                      <div className="community-resource-tree-label">{region.label}</div>
                      {region.comunas.map((comuna) => (
                        <div key={comuna.id} className="community-resource-tree-comuna">
                          <div className="community-resource-tree-branch">{comuna.label}</div>
                          <div className="community-resource-list">
                            {comuna.resources.map((resource) => (
                              <button
                                key={resource.id}
                                type="button"
                                className={`community-resource-item ${selectedResource?.id === resource.id ? 'is-active' : ''}`}
                                onClick={() => setSelectedResourceId(resource.id)}
                              >
                                <span>{isPdfResource(resource) ? 'PDF' : isDocxResource(resource) ? 'DOCX' : 'DOC'}</span>
                                <strong>{resource.title}</strong>
                                <small>{resource.category}</small>
                              </button>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  ))}
                </div>
              ) : null}
            </aside>

            <div className="community-resource-reader">
              {selectedResource ? (
                <>
                  <header className="community-resource-reader-header">
                    <div>
                      <p>{selectedResource.category}</p>
                      <h4>{selectedResource.title}</h4>
                      <small>
                        {regionMap[selectedResource.regionId] || selectedResource.regionId || 'Sin region'} - {getLabelForComuna(selectedResource.comunaId) || 'Sin comuna'}
                      </small>
                    </div>
                    <div className="community-resource-reader-actions">
                      <a className="btn btn-secondary" href={selectedResource.fileUrl || selectedResource.url} target="_blank" rel="noreferrer">
                        Abrir
                      </a>
                      <button
                        type="button"
                        className="btn btn-danger"
                        onClick={() => setPendingDelete({ type: 'resource', id: selectedResource.id })}
                      >
                        Eliminar
                      </button>
                    </div>
                  </header>

                  <p className="community-resource-description">{selectedResource.description}</p>

                  {isPdfResource(selectedResource) ? (
                    <iframe
                      title={`Lector PDF - ${selectedResource.title}`}
                      src={selectedResource.fileUrl || selectedResource.url}
                      className="community-resource-pdf"
                    />
                  ) : (
                    <div className="community-resource-docx-placeholder">
                      <strong>Vista previa no disponible para DOCX</strong>
                      <p>El navegador no puede embeber DOCX de forma nativa. Puedes abrir o descargar el archivo desde el boton superior.</p>
                    </div>
                  )}
                </>
              ) : (
                <div className="community-resource-empty-reader">
                  <strong>Lector PDF embebido</strong>
                  <p>Selecciona un recurso del catalogo para visualizarlo aqui.</p>
                </div>
              )}
            </div>
          </section>
        </div>
      </article>

      <article className="dashboard-card community-contacts-card">
        <div className="community-contacts-layout">
          <section className="community-agenda-panel" aria-label="Agenda de contactos">
            <div className="community-agenda-header">
              <div>
                <h3>Agenda de contactos</h3>
                <p>Responsables y canales de escalamiento por territorio.</p>
              </div>
              <span>{filteredContacts.length}</span>
            </div>

            <div className="community-agenda-filters">
              <label>
                Buscar
                <input
                  type="search"
                  value={contactSearchTerm}
                  onChange={(event) => setContactSearchTerm(event.target.value)}
                  placeholder="Nombre, telefono, correo o protocolo"
                />
              </label>
              <label>
                Region
                <select value={contactRegionFilterId} onChange={handleContactRegionFilter}>
                  <option value="">Todas</option>
                  {resourceRegionOptions.map((region) => (
                    <option key={region.id} value={region.id}>
                      {region.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Comuna
                <select
                  value={contactComunaFilterId}
                  onChange={(event) => setContactComunaFilterId(event.target.value)}
                  disabled={!contactRegionFilterId}
                >
                  <option value="">{contactRegionFilterId ? 'Todas' : 'Seleccione region'}</option>
                  {contactFilterComunas.map((comuna) => (
                    <option key={comuna.value} value={comuna.value}>
                      {comuna.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="community-agenda-book">
              {loading ? <LoadingSpinner label="Cargando contactos..." /> : null}
              {!loading && filteredContacts.length === 0 ? (
                <EmptyState title="Sin contactos" description="Agrega responsables y protocolos de escalamiento." />
              ) : null}
              {!loading && filteredContacts.map((contact) => {
                const whatsappHref = contactWhatsAppHref(contact.phone);
                const mailHref = contactMailHref(contact.email);
                return (
                  <article key={contact.id} className="community-contact-card">
                    <div className="community-contact-main">
                      <strong>{contact.name}</strong>
                      <span>{contact.organization}</span>
                      <small>{regionMap[contact.regionId] || contact.regionId || 'Sin region'} · {getLabelForComuna(contact.comunaId) || 'Sin comuna'}</small>
                    </div>
                    <div className="community-contact-meta">
                      <a href={`tel:${contact.phone}`} className="community-contact-phone">{contact.phone}</a>
                      <div className="community-contact-actions" aria-label={`Canales de contacto para ${contact.name}`}>
                        {whatsappHref ? (
                          <a className="community-contact-action whatsapp" href={whatsappHref} target="_blank" rel="noreferrer" aria-label={`Abrir WhatsApp para ${contact.name}`}>
                            💬
                          </a>
                        ) : null}
                        {mailHref ? (
                          <a className="community-contact-action email" href={mailHref} aria-label={`Enviar correo a ${contact.name}`}>
                            ✉
                          </a>
                        ) : null}
                        <button
                          type="button"
                          className="community-contact-delete"
                          onClick={() => setPendingDelete({ type: 'contact', id: contact.id })}
                        >
                          Eliminar
                        </button>
                      </div>
                    </div>
                    <p>{contact.protocol}</p>
                  </article>
                );
              })}
            </div>
          </section>

          <aside className="community-contact-form-panel">
            <h3>Contactos y protocolos</h3>
            <p>Registra responsables con teléfono, WhatsApp, correo y protocolo de escalamiento.</p>
            <form className="community-contact-form" onSubmit={submitContact}>
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
                  {resourceRegionOptions.map((region) => (
                    <option key={region.id} value={region.id}>
                      {region.label}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Comuna
                <select
                  name="comunaId"
                  value={contactForm.comunaId}
                  onChange={handleContactInput}
                  required
                  disabled={!contactForm.regionId}
                >
                  <option value="">{contactForm.regionId ? 'Seleccione comuna' : 'Seleccione una region primero'}</option>
                  {contactFormComunas.map((comuna) => (
                    <option key={comuna.value} value={comuna.value}>
                      {comuna.label}
                    </option>
                  ))}
                </select>
              </label>

              <label className="full-width">
                Protocolo
                <textarea name="protocol" rows={4} value={contactForm.protocol} onChange={handleContactInput} required />
              </label>

              <div className="form-actions full-width">
                <button type="submit" className="btn">
                  Guardar contacto
                </button>
              </div>
            </form>
          </aside>
        </div>
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
