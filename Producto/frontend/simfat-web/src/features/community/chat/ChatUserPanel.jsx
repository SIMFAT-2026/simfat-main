import { useState } from 'react';
import './ChatUserPanel.css';

const PRESENCE_GROUPS = [
  { state: 'CONNECTED', label: 'En línea', color: '#22c55e', dotColor: '#22c55e' },
  { state: 'AWAY', label: 'Ausente', color: '#f59e0b', dotColor: '#f59e0b' },
  { state: 'UNAVAILABLE', label: 'No disponible', color: '#94a3b8', dotColor: '#94a3b8' },
  { state: 'OFFLINE', label: 'Desconectado', color: '#64748b', dotColor: '#e2e8f0' }
];

function initials(name) {
  if (!name) return '?';
  const words = name.trim().split(/\s+/);
  return words
    .slice(0, 2)
    .map((w) => w[0] || '')
    .join('')
    .toUpperCase();
}

function ChatUserPanel({ presenceUsers, onOpenPrivateRoom, currentUserId }) {
  const [open, setOpen] = useState(true);

  const grupos = PRESENCE_GROUPS.map((group) => ({
    ...group,
    users: presenceUsers.filter((u) => u.state === group.state)
  }));

  return (
    <aside className="chat-user-panel" data-open={open}>
      <button type="button" className="chat-user-panel-toggle" onClick={() => setOpen((p) => !p)}>
        {open ? '◀' : '▶'} {open ? `Usuarios (${presenceUsers.length})` : ''}
      </button>

      {open && grupos.map((grupo) =>
        grupo.users.length > 0 ? (
          <div key={grupo.state} className="chat-presence-group">
            <span className="chat-presence-group-label">{grupo.label}</span>
            {grupo.users.map((user) => (
              <button
                key={user.userId}
                type="button"
                className="chat-presence-user"
                onClick={() => user.userId !== currentUserId && onOpenPrivateRoom(user.userId)}
                disabled={user.userId === currentUserId}
                title={user.userId === currentUserId ? 'Tú' : `Abrir chat privado con ${user.fullName}`}
              >
                <span className="chat-presence-avatar" style={{ background: grupo.color }}>
                  {initials(user.fullName)}
                </span>
                <span className="chat-presence-name">{user.fullName}</span>
                <span className="chat-presence-dot" style={{ background: grupo.dotColor }} />
              </button>
            ))}
          </div>
        ) : null
      )}

      {open && presenceUsers.length === 0 && (
        <p className="chat-presence-empty">Sin usuarios activos en esta sala.</p>
      )}
    </aside>
  );
}

export default ChatUserPanel;
