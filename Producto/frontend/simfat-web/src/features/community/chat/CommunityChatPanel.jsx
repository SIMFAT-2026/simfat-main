import { useMemo, useState } from 'react';
import { useAuth } from '../../../auth/AuthContext';
import EmptyState from '../../../components/EmptyState';
import LoadingSpinner from '../../../components/LoadingSpinner';
import { useCommunityChat } from '../hooks/useCommunityChat';
import ChatUserPanel from './ChatUserPanel';

const FALLBACK_GENERAL_ROOM = { id: 'general', type: 'GENERAL', regionId: '', name: 'Sala general' };

const PRESENCE_STATES = [
  { value: 'CONNECTED', label: 'Conectado' },
  { value: 'AWAY', label: 'Ausente' },
  { value: 'UNAVAILABLE', label: 'No disponible' },
  { value: 'OFFLINE', label: 'No conectado' }
];

const MODERATION_ROLES = new Set(['ROLE_MODERATOR', 'ROLE_ADMIN', 'ROLE_SUPER_ADMIN']);

function formatMessageTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' });
}

function roomLabel(room) {
  if (!room) return 'Sala';
  if (room.type === 'GENERAL') return 'Sala general';
  return room.name || room.regionId || 'Sala regional';
}

function tabLabel(room) {
  if (!room) return 'Sala';
  if (room.type === 'PRIVATE') return `@ ${room.name}`;
  return roomLabel(room);
}

function CommunityChatPanel() {
  const { user } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [moderationReason, setModerationReason] = useState('');
  const [sending, setSending] = useState(false);
  const [localError, setLocalError] = useState('');

  const {
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
    sendMessage,
    updatePresence,
    moderateMessage,
    createPrivateRoom
  } = useCommunityChat();

  const currentUserId = user?.id || user?.userId || '';

  // Ensure there's always at least a GENERAL room available even when the API
  // returns an empty list (e.g. no room configured for this region yet).
  const visibleRooms = useMemo(() => {
    if (rooms.length === 0) return [FALLBACK_GENERAL_ROOM];
    const hasGeneral = rooms.some((r) => r.type === 'GENERAL' || r.id === 'general');
    return hasGeneral ? rooms : [FALLBACK_GENERAL_ROOM, ...rooms];
  }, [rooms]);

  const canModerate = useMemo(() => {
    // user.roles es el enum legacy (solo "ADMIN"/"USER", nunca "ROLE_*"
    // ni SUPER_ADMIN) — roleCodes es el RBAC real expuesto por /api/auth/me.
    const roleCodes = user?.roleCodes || [];
    return roleCodes.some((role) => MODERATION_ROLES.has(role));
  }, [user]);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!draft.trim()) return;

    setSending(true);
    setLocalError('');

    try {
      await sendMessage(draft);
      setDraft('');
    } catch (err) {
      setLocalError(err.message || 'No se pudo enviar el mensaje.');
    } finally {
      setSending(false);
    }
  }

  async function handlePresenceChange(event) {
    try {
      await updatePresence(event.target.value);
    } catch (err) {
      setLocalError(err.message || 'No se pudo actualizar la presencia.');
    }
  }

  async function handleModerate(messageId) {
    try {
      await moderateMessage(messageId, moderationReason.trim() || 'Moderacion operativa');
      setModerationReason('');
    } catch (err) {
      setLocalError(err.message || 'No se pudo moderar el mensaje.');
    }
  }

  return (
    <aside className={`community-chat ${isOpen ? 'community-chat-open' : ''}`}>
      <button type="button" className="community-chat-toggle" onClick={() => setIsOpen((current) => !current)}>
        <span>Chat comunitario</span>
        <strong>{isOpen ? 'Ocultar' : 'Abrir'}</strong>
      </button>

      {isOpen ? (
        <div className="community-chat-panel" aria-live="polite">
          <div className="community-chat-header">
            <div>
              <h3>{roomLabel(activeRoom)}</h3>
              <p>Coordinacion territorial para prevencion de incendios.</p>
            </div>

            <label>
              Estado
              <select value={presenceState} onChange={handlePresenceChange} disabled={!activeRoomId}>
                {PRESENCE_STATES.map((state) => (
                  <option key={state.value} value={state.value}>
                    {state.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="community-chat-body" style={{ display: 'flex', gap: 0 }}>
            <ChatUserPanel
              presenceUsers={presenceUsers}
              onOpenPrivateRoom={createPrivateRoom}
              currentUserId={currentUserId}
            />
            <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
              {loadingRooms ? <LoadingSpinner label="Cargando salas..." /> : null}

              {!loadingRooms && visibleRooms.length === 0 ? (
                <p className="feedback feedback-info" style={{ margin: '0.5rem 0', fontSize: '0.85rem' }}>
                  No hay salas disponibles. Contacta al administrador.
                </p>
              ) : null}

              {visibleRooms.length > 0 ? (
                <div className="community-chat-rooms" role="tablist" aria-label="Salas de chat comunitario">
                  {visibleRooms.map((room) => (
                    <button
                      key={room.id}
                      type="button"
                      className={room.id === activeRoomId ? 'active' : ''}
                      onClick={() => changeRoom(room.id)}
                    >
                      {tabLabel(room)}
                    </button>
                  ))}
                </div>
              ) : null}

              {error || localError ? <p className="feedback feedback-error">{localError || error}</p> : null}

              <div className="community-chat-messages">
                {loadingMessages && messages.length === 0 ? <LoadingSpinner label="Cargando mensajes..." /> : null}
                {!loadingMessages && messages.length === 0 ? (
                  <EmptyState title="Sin mensajes" description="Inicia la coordinacion en esta sala." />
                ) : null}

                {messages.map((message) => (
                  <article key={message.id} className={`community-chat-message ${message.status !== 'ACTIVE' ? 'moderated' : ''}`}>
                    <header>
                      <strong>{message.authorName}</strong>
                      <span>{formatMessageTime(message.createdAt)}</span>
                    </header>
                    <p>{message.status === 'ACTIVE' ? message.content : 'Mensaje moderado.'}</p>
                    {canModerate && message.status === 'ACTIVE' ? (
                      <button type="button" className="btn btn-secondary" onClick={() => handleModerate(message.id)}>
                        Moderar
                      </button>
                    ) : null}
                  </article>
                ))}
              </div>

              {canModerate ? (
                <label className="community-chat-moderation-reason">
                  Motivo de moderacion
                  <input
                    value={moderationReason}
                    onChange={(event) => setModerationReason(event.target.value)}
                    placeholder="Opcional"
                  />
                </label>
              ) : null}

              <form className="community-chat-form" onSubmit={handleSubmit}>
                <label className="sr-only" htmlFor="community-chat-message">
                  Mensaje
                </label>
                <textarea
                  id="community-chat-message"
                  rows={2}
                  maxLength={800}
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder="Escribe una actualizacion operativa..."
                  disabled={!activeRoomId || sending}
                />
                <button type="submit" className="btn" disabled={!activeRoomId || sending || !draft.trim()}>
                  {sending ? 'Enviando...' : 'Enviar'}
                </button>
              </form>
            </div>
          </div>
        </div>
      ) : null}
    </aside>
  );
}

export default CommunityChatPanel;
