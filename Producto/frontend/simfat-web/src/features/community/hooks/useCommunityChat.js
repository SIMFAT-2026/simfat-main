import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createPrivateChatRoom,
  getChatRoomPresence,
  getCommunityChatMessages,
  getCommunityChatRooms,
  moderateCommunityChatMessage,
  sendCommunityChatMessage,
  updateCommunityChatPresence
} from '../../../services';

const POLLING_MS = 10000;

function normalizeRooms(items = []) {
  return items.map((item) => ({
    id: String(item.id || ''),
    type: item.type || 'REGIONAL',
    regionId: item.regionId || '',
    name: item.name || item.regionId || 'Sala'
  }));
}

function normalizeMessages(items = []) {
  return items.map((item) => ({
    id: String(item.id || ''),
    roomId: String(item.roomId || ''),
    authorUserId: item.authorUserId || '',
    authorName: item.authorName || 'Usuario verificado',
    content: item.content || '',
    status: item.status || 'ACTIVE',
    createdAt: item.createdAt || ''
  }));
}

function mergeMessages(current, incoming) {
  const byId = new Map(current.map((message) => [message.id, message]));
  incoming.forEach((message) => byId.set(message.id, message));
  return Array.from(byId.values()).sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
}

const PRESENCE_POLLING_MS = 30000;

export function useCommunityChat() {
  const [rooms, setRooms] = useState([]);
  const [activeRoomId, setActiveRoomId] = useState('');
  const [messages, setMessages] = useState([]);
  const [presenceState, setPresenceState] = useState('CONNECTED');
  const [presenceUsers, setPresenceUsers] = useState([]);
  const [loadingRooms, setLoadingRooms] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [error, setError] = useState('');

  const activeRoom = useMemo(
    () => rooms.find((room) => room.id === activeRoomId) || null,
    [activeRoomId, rooms]
  );

  const loadRooms = useCallback(async () => {
    setLoadingRooms(true);
    setError('');

    try {
      const data = normalizeRooms(await getCommunityChatRooms());
      setRooms(data);
      setActiveRoomId((current) => current || data[0]?.id || '');
    } catch (err) {
      setError(err.message || 'No se pudieron cargar las salas de chat.');
    } finally {
      setLoadingRooms(false);
    }
  }, []);

  const loadMessages = useCallback(async (roomId = activeRoomId, { append = false } = {}) => {
    if (!roomId) return;

    setLoadingMessages(true);
    setError('');

    try {
      const data = normalizeMessages(await getCommunityChatMessages(roomId));
      setMessages((current) => (append ? mergeMessages(current, data) : data));
    } catch (err) {
      setError(err.message || 'No se pudieron cargar los mensajes del chat.');
    } finally {
      setLoadingMessages(false);
    }
  }, [activeRoomId]);

  const changeRoom = useCallback((roomId) => {
    setActiveRoomId(roomId);
    setMessages([]);
  }, []);

  const sendMessage = useCallback(async (content) => {
    if (!activeRoomId || !content.trim()) return;
    const created = normalizeMessages([await sendCommunityChatMessage(activeRoomId, content.trim())])[0];
    setMessages((current) => mergeMessages(current, [created]));
  }, [activeRoomId]);

  const updatePresence = useCallback(async (state) => {
    setPresenceState(state);
    if (!activeRoomId) return;
    await updateCommunityChatPresence(activeRoomId, state);
  }, [activeRoomId]);

  const moderateMessage = useCallback(async (messageId, reason) => {
    const updated = normalizeMessages([
      await moderateCommunityChatMessage(messageId, { action: 'HIDE', reason })
    ])[0];
    setMessages((current) => mergeMessages(current, [updated]));
  }, []);

  const loadPresence = useCallback(async (roomId) => {
    if (!roomId) return;
    try {
      const data = await getChatRoomPresence(roomId);
      setPresenceUsers(
        (Array.isArray(data) ? data : []).map((item) => ({
          userId: item.userId || '',
          fullName: item.fullName || item.name || 'Usuario',
          state: item.state || 'OFFLINE',
          lastSeen: item.lastSeen || ''
        }))
      );
    } catch {
      setPresenceUsers([]);
    }
  }, []);

  const createPrivateRoom = useCallback(async (participantUserId) => {
    const room = await createPrivateChatRoom(participantUserId);
    if (!room) return;
    const normalized = normalizeRooms([room])[0];
    setRooms((current) => {
      if (current.some((r) => r.id === normalized.id)) return current;
      return [...current, normalized];
    });
    changeRoom(normalized.id);
  }, [changeRoom]);

  useEffect(() => {
    loadRooms();
  }, [loadRooms]);

  useEffect(() => {
    if (!activeRoomId) return undefined;

    loadMessages(activeRoomId);
    loadPresence(activeRoomId);
    updateCommunityChatPresence(activeRoomId, presenceState).catch(() => {});

    const pollingId = window.setInterval(() => {
      loadMessages(activeRoomId, { append: true });
    }, POLLING_MS);

    const presencePollingId = window.setInterval(() => {
      loadPresence(activeRoomId);
    }, PRESENCE_POLLING_MS);

    return () => {
      window.clearInterval(pollingId);
      window.clearInterval(presencePollingId);
    };
  }, [activeRoomId, loadMessages, loadPresence, presenceState]);

  return {
    rooms,
    activeRoom,
    activeRoomId,
    messages,
    presenceState,
    presenceUsers,
    loadingRooms,
    loadingMessages,
    error,
    changeRoom,
    loadRooms,
    loadMessages,
    sendMessage,
    updatePresence,
    moderateMessage,
    loadPresence,
    createPrivateRoom
  };
}
