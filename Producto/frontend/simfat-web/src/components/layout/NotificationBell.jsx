import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getUnreadNotifications, markNotificationRead } from '../../services';

const POLL_INTERVAL_MS = 30_000;

function NotificationBell() {
  const [data, setData] = useState({ notifications: [], unreadCount: 0 });
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef(null);
  const navigate = useNavigate();

  async function fetchUnread() {
    try {
      const result = await getUnreadNotifications();
      setData(result || { notifications: [], unreadCount: 0 });
    } catch {
      // silencioso — no interrumpir la sesion por un fallo de notificaciones
    }
  }

  useEffect(() => {
    fetchUnread();
    const timer = setInterval(fetchUnread, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    function handleClickOutside(e) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  async function handleClick(notification) {
    try {
      await markNotificationRead(notification.id);
      setData((prev) => ({
        notifications: prev.notifications.filter((n) => n.id !== notification.id),
        unreadCount: Math.max(0, prev.unreadCount - 1)
      }));
    } catch {
      // continuar aunque falle el mark-read
    }
    setOpen(false);
    if (notification.regionId) {
      navigate(`/alerts?regionId=${notification.regionId}`);
    }
  }

  const count = data.unreadCount || 0;

  return (
    <div className="notification-bell" ref={dropdownRef}>
      <button
        type="button"
        className="notification-bell-btn"
        aria-label={`Notificaciones${count > 0 ? `, ${count} no leidas` : ''}`}
        onClick={() => setOpen((prev) => !prev)}
      >
        <span className="notification-bell-icon" aria-hidden="true">&#128276;</span>
        {count > 0 && (
          <span className="notification-badge" aria-hidden="true">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="notification-dropdown" role="menu">
          <div className="notification-dropdown-header">
            <span>Notificaciones</span>
            {count > 0 && <span className="notification-dropdown-count">{count} no leidas</span>}
          </div>

          {data.notifications.length === 0 ? (
            <p className="notification-empty">Sin notificaciones pendientes</p>
          ) : (
            <ul className="notification-list">
              {data.notifications.map((n) => (
                <li key={n.id} className="notification-item">
                  <button
                    type="button"
                    className="notification-item-btn"
                    onClick={() => handleClick(n)}
                  >
                    <span className={`notification-level notification-level-${(n.alertLevel || '').toLowerCase()}`}>
                      {n.alertLevel}
                    </span>
                    <span className="notification-title">{n.title}</span>
                    <span className="notification-msg">{n.message}</span>
                    <span className="notification-time">
                      {n.createdAt ? new Date(n.createdAt).toLocaleString('es-CL') : ''}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

export default NotificationBell;
