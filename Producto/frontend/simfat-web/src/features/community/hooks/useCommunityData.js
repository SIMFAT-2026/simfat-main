import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createCommunityBoardPost,
  createCommunityContact,
  createCommunityResource,
  deleteCommunityBoardPost,
  deleteCommunityContact,
  deleteCommunityResource,
  getCommunityBoard,
  getCommunityContacts,
  getCommunityResources,
  getRegions
} from '../../../services';

function createId(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

const MOCK_BOARD = [
  {
    id: 'board-1',
    title: 'Vigilancia preventiva fin de semana',
    message: 'Se solicita reforzar turnos vecinales en zonas de interfaz urbano-rural.',
    priority: 'ALTA',
    regionId: 'biobio',
    publishedAt: '2026-04-20T10:30:00Z',
    author: 'Unidad territorial SIMFAT'
  },
  {
    id: 'board-2',
    title: 'Capacitacion comunitaria',
    message: 'Jornada de uso de extintores y protocolo de evacuacion local.',
    priority: 'MEDIA',
    regionId: 'araucania',
    publishedAt: '2026-04-21T12:00:00Z',
    author: 'Municipalidad'
  }
];

const MOCK_RESOURCES = [
  {
    id: 'resource-1',
    title: 'Protocolo inicial de respuesta',
    category: 'PROTOCOLO',
    url: 'https://example.com/protocolo-respuesta',
    regionId: 'biobio',
    description: 'Flujo recomendado para coordinacion durante las primeras 2 horas.'
  },
  {
    id: 'resource-2',
    title: 'Guia de prevencion vecinal',
    category: 'GUIA',
    url: 'https://example.com/guia-prevencion',
    regionId: 'araucania',
    description: 'Checklist comunitario previo a temporada de alto riesgo.'
  }
];

const MOCK_CONTACTS = [
  {
    id: 'contact-1',
    name: 'Central emergencias regional',
    organization: 'Proteccion Civil',
    phone: '+56 9 5555 1111',
    email: 'emergencias@simfat.cl',
    regionId: 'biobio',
    protocol: 'Escalamiento inmediato ante humo persistente > 20 minutos.'
  },
  {
    id: 'contact-2',
    name: 'Coordinacion brigadas',
    organization: 'Bomberos',
    phone: '+56 9 5555 2222',
    email: 'brigadas@simfat.cl',
    regionId: 'araucania',
    protocol: 'Activacion con prioridad alta ante alerta critica confirmada.'
  }
];

function normalizeRegions(rawRegions = []) {
  return rawRegions.map((region) => ({
    id: String(region.id || ''),
    nombre: region.nombre || region.name || '-'
  }));
}

function fallbackRegions() {
  return [
    { id: 'biobio', nombre: 'Biobio' },
    { id: 'araucania', nombre: 'La Araucania' }
  ];
}

function normalizeBoard(items = []) {
  return items.map((item, index) => ({
    id: String(item.id || createId(`board-${index}`)),
    title: item.title || item.titulo || 'Sin titulo',
    message: item.message || item.mensaje || '',
    priority: String(item.priority || item.prioridad || 'MEDIA').toUpperCase(),
    regionId: String(item.regionId || item.region_id || ''),
    publishedAt: item.publishedAt || item.fechaPublicacion || new Date().toISOString(),
    author: item.author || item.autor || 'Equipo comunitario',
    attachmentUrl: item.attachmentUrl || '',
    attachmentName: item.attachmentName || '',
    attachmentContentType: item.attachmentContentType || '',
    attachmentSize: item.attachmentSize || null,
    attachmentImage: Boolean(item.attachmentImage)
  }));
}

function normalizeResources(items = []) {
  return items.map((item, index) => ({
    id: String(item.id || createId(`resource-${index}`)),
    title: item.title || item.titulo || 'Recurso',
    category: String(item.category || item.categoria || 'GUIA').toUpperCase(),
    url: item.url || item.enlace || '',
    regionId: String(item.regionId || item.region_id || ''),
    comunaId: String(item.comunaId || item.comuna_id || ''),
    description: item.description || item.descripcion || '',
    fileUrl: item.fileUrl || item.url || '',
    fileName: item.fileName || '',
    fileContentType: item.fileContentType || '',
    fileSize: item.fileSize || null
  }));
}

function normalizeContacts(items = []) {
  return items.map((item, index) => ({
    id: String(item.id || createId(`contact-${index}`)),
    name: item.name || item.nombre || 'Contacto',
    organization: item.organization || item.organizacion || '-',
    phone: item.phone || item.telefono || '-',
    email: item.email || item.correo || '-',
    regionId: String(item.regionId || item.region_id || ''),
    protocol: item.protocol || item.protocolo || ''
  }));
}

export function useCommunityData() {
  const [regions, setRegions] = useState([]);
  const [board, setBoard] = useState([]);
  const [resources, setResources] = useState([]);
  const [contacts, setContacts] = useState([]);
  const [source, setSource] = useState('backend');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError('');

    try {
      const [regionsData, boardData, resourcesData, contactsData] = await Promise.all([
        getRegions(),
        getCommunityBoard(),
        getCommunityResources(),
        getCommunityContacts()
      ]);

      setRegions(normalizeRegions(Array.isArray(regionsData) ? regionsData : []));
      setBoard(normalizeBoard(Array.isArray(boardData) ? boardData : []));
      setResources(normalizeResources(Array.isArray(resourcesData) ? resourcesData : []));
      setContacts(normalizeContacts(Array.isArray(contactsData) ? contactsData : []));
      setSource('backend');
    } catch {
      setRegions(fallbackRegions());
      setBoard(normalizeBoard(MOCK_BOARD));
      setResources(normalizeResources(MOCK_RESOURCES));
      setContacts(normalizeContacts(MOCK_CONTACTS));
      setSource('fallback');
      setError('Backend comunitario no disponible. Se muestran datos locales de prueba.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const createBoard = useCallback(
    async (payload) => {
      if (source === 'backend') {
        try {
          const created = await createCommunityBoardPost(payload, payload.attachmentFile);
          const normalized = normalizeBoard([created])[0];
          setBoard((prev) => [normalized, ...prev]);
          return normalized;
        } catch {
          // fallback local controlado
        }
      }

      const createdLocal = {
        id: createId('board'),
        title: payload.title,
        message: payload.message,
        priority: payload.priority,
        regionId: payload.regionId,
        publishedAt: new Date().toISOString(),
        author: payload.author || 'Equipo comunitario',
        attachmentUrl: payload.attachmentPreviewUrl || '',
        attachmentName: payload.attachmentName || '',
        attachmentContentType: payload.attachmentContentType || '',
        attachmentSize: payload.attachmentSize || null,
        attachmentImage: Boolean(payload.attachmentImage)
      };
      setBoard((prev) => [createdLocal, ...prev]);
      return createdLocal;
    },
    [source]
  );

  const createResource = useCallback(
    async (payload) => {
      if (source === 'backend') {
        try {
          const created = await createCommunityResource(payload);
          const normalized = normalizeResources([created])[0];
          setResources((prev) => [normalized, ...prev]);
          return normalized;
        } catch {
          // fallback local controlado
        }
      }

      const localFileUrl = payload.file ? URL.createObjectURL(payload.file) : payload.url;
      const createdLocal = {
        id: createId('resource'),
        title: payload.title,
        category: payload.category,
        url: localFileUrl,
        regionId: payload.regionId,
        comunaId: payload.comunaId,
        description: payload.description,
        fileUrl: localFileUrl,
        fileName: payload.file?.name || '',
        fileContentType: payload.file?.type || '',
        fileSize: payload.file?.size || null
      };
      setResources((prev) => [createdLocal, ...prev]);
      return createdLocal;
    },
    [source]
  );

  const createContact = useCallback(
    async (payload) => {
      if (source === 'backend') {
        try {
          const created = await createCommunityContact(payload);
          const normalized = normalizeContacts([created])[0];
          setContacts((prev) => [normalized, ...prev]);
          return;
        } catch {
          // fallback local controlado
        }
      }

      const createdLocal = {
        id: createId('contact'),
        name: payload.name,
        organization: payload.organization,
        phone: payload.phone,
        email: payload.email,
        regionId: payload.regionId,
        protocol: payload.protocol
      };
      setContacts((prev) => [createdLocal, ...prev]);
    },
    [source]
  );

  const removeBoard = useCallback(
    async (id) => {
      if (source === 'backend') {
        try {
          await deleteCommunityBoardPost(id);
        } catch {
          // fallback local controlado
        }
      }
      setBoard((prev) => prev.filter((item) => item.id !== id));
    },
    [source]
  );

  const removeResource = useCallback(
    async (id) => {
      if (source === 'backend') {
        try {
          await deleteCommunityResource(id);
        } catch {
          // fallback local controlado
        }
      }
      setResources((prev) => prev.filter((item) => item.id !== id));
    },
    [source]
  );

  const removeContact = useCallback(
    async (id) => {
      if (source === 'backend') {
        try {
          await deleteCommunityContact(id);
        } catch {
          // fallback local controlado
        }
      }
      setContacts((prev) => prev.filter((item) => item.id !== id));
    },
    [source]
  );

  return useMemo(
    () => ({
      regions,
      board,
      resources,
      contacts,
      source,
      loading,
      error,
      reload: loadAll,
      createBoard,
      createResource,
      createContact,
      removeBoard,
      removeResource,
      removeContact
    }),
    [
      regions,
      board,
      resources,
      contacts,
      source,
      loading,
      error,
      loadAll,
      createBoard,
      createResource,
      createContact,
      removeBoard,
      removeResource,
      removeContact
    ]
  );
}
