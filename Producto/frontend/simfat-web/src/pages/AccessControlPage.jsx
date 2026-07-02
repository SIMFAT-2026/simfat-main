import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useAuth } from '../auth/AuthContext';
import { useFeedback } from '../hooks';
import {
  getAccessPermissions,
  getAccessRoles,
  getAccessUsers,
  getAccessUserById,
  getRegions,
  getPendingReview,
  getVerificationEvents,
  updateAccessUserRoles,
  updateCommunityChatAccess,
  updateCommunityModuleAccess,
  updateVerificationStatus,
  deleteUser
} from '../services';

const PROFILE_OPTIONS = [
  { value: 'COMMUNITY', label: 'Comunidad', roles: ['ROLE_COMMUNITY_USER'] },
  { value: 'MODERATOR', label: 'Moderador', roles: ['ROLE_MODERATOR', 'ROLE_VERIFIED_USER', 'ROLE_COMMUNITY_USER'] },
  { value: 'ADMIN', label: 'Administrador', roles: ['ROLE_ADMIN', 'ROLE_MODERATOR', 'ROLE_VERIFIED_USER', 'ROLE_COMMUNITY_USER'] },
  {
    value: 'SUPER_ADMIN',
    label: 'Super Admin',
    roles: ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_MODERATOR', 'ROLE_VERIFIED_USER', 'ROLE_COMMUNITY_USER']
  }
];

function AccessControlPage() {
  const { user } = useAuth();
  const feedback = useFeedback();
  const [loading, setLoading] = useState(true);
  const [savingUserId, setSavingUserId] = useState('');
  const [error, setError] = useState(null);
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [permissions, setPermissions] = useState([]);
  const [regions, setRegions] = useState([]);
  const [draftRolesByUser, setDraftRolesByUser] = useState({});
  const [draftChatAccessByUser, setDraftChatAccessByUser] = useState({});
  const [draftModuleAccessByUser, setDraftModuleAccessByUser] = useState({});
  const [pendingReview, setPendingReview] = useState([]);
  const [eventsByUser, setEventsByUser] = useState({});
  const [expandedVerifUser, setExpandedVerifUser] = useState('');
  const [verifDraft, setVerifDraft] = useState({ newStatus: '', notes: '' });
  const [savingVerifUserId, setSavingVerifUserId] = useState('');

  const canManageAccess = useMemo(() => {
    // roleCodes es el RBAC real (/api/auth/me); user.roles es el enum legacy
    // (solo "ADMIN"/"USER") y nunca tendra "ROLE_SUPER_ADMIN".
    const roleSet = new Set(user?.roleCodes || []);
    return roleSet.has('ROLE_ADMIN') || roleSet.has('ROLE_SUPER_ADMIN');
  }, [user]);

  const isSuperAdmin = useMemo(() => {
    return new Set(user?.roleCodes || []).has('ROLE_SUPER_ADMIN');
  }, [user]);

  useEffect(() => {
    if (!canManageAccess) {
      setLoading(false);
      return;
    }

    async function loadAccessData() {
      setLoading(true);
      setError(null);
      try {
        const [usersData, rolesData, permissionsData, regionsData, pendingData] = await Promise.all([
          getAccessUsers(),
          getAccessRoles(),
          getAccessPermissions(),
          getRegions(),
          getPendingReview()
        ]);

        const normalizedUsers = Array.isArray(usersData) ? usersData : [];
        const normalizedRoles = Array.isArray(rolesData) ? rolesData : [];
        const normalizedRegions = Array.isArray(regionsData) ? regionsData : [];
        setUsers(normalizedUsers);
        setRoles(normalizedRoles);
        setPermissions(Array.isArray(permissionsData) ? permissionsData : []);
        setRegions(normalizedRegions);
        setPendingReview(Array.isArray(pendingData) ? pendingData : []);

        const draft = {};
        const chatDraft = {};
        const moduleDraft = {};
        for (const row of normalizedUsers) {
          draft[row.id] = Array.isArray(row.assignedRoles) ? [...row.assignedRoles] : [];
          chatDraft[row.id] = {
            primaryRegionId: row.communityChatAccess?.primaryRegionId || '',
            additionalRegionIds: Array.isArray(row.communityChatAccess?.additionalRegionIds)
              ? [...row.communityChatAccess.additionalRegionIds]
              : []
          };
          moduleDraft[row.id] = Array.isArray(row.communityModuleAccess?.regionIds)
            ? [...row.communityModuleAccess.regionIds]
            : [];
        }
        setDraftRolesByUser(draft);
        setDraftChatAccessByUser(chatDraft);
        setDraftModuleAccessByUser(moduleDraft);
      } catch (err) {
        setError(err);
      } finally {
        setLoading(false);
      }
    }

    loadAccessData();
  }, [canManageAccess]);

  function normalizeRoleSet(roleCodes) {
    return new Set((roleCodes || []).filter((role) => role?.startsWith('ROLE_')));
  }

  function detectProfile(roleCodes) {
    const roleSet = normalizeRoleSet(roleCodes);
    const matched = [...PROFILE_OPTIONS]
      .reverse()
      .find((profile) => profile.roles.every((role) => roleSet.has(role)));
    return matched?.value || 'COMMUNITY';
  }

  function getProfileRoles(profileValue) {
    return PROFILE_OPTIONS.find((profile) => profile.value === profileValue)?.roles || PROFILE_OPTIONS[0].roles;
  }

  function toggleRole(userId, roleCode) {
    setDraftRolesByUser((prev) => {
      const current = new Set(prev[userId] || []);
      if (current.has(roleCode)) {
        current.delete(roleCode);
      } else {
        current.add(roleCode);
      }
      return { ...prev, [userId]: [...current] };
    });
  }

  function applyProfile(userId, profileValue) {
    setDraftRolesByUser((prev) => {
      const current = new Set(prev[userId] || []);
      const nonRoleCodes = [...current].filter((code) => !code?.startsWith('ROLE_'));
      const next = [...new Set([...getProfileRoles(profileValue), ...nonRoleCodes])];
      return { ...prev, [userId]: next };
    });
  }

  function toggleVerifiedSwitch(userId, enabled) {
    setDraftRolesByUser((prev) => {
      const current = new Set(prev[userId] || []);
      if (enabled) {
        current.add('ROLE_VERIFIED_USER');
        current.add('ROLE_COMMUNITY_USER');
      } else {
        current.delete('ROLE_VERIFIED_USER');
      }
      return { ...prev, [userId]: [...current] };
    });
  }

  async function handleDeleteUser(userId, email) {
    if (!window.confirm(`¿Eliminar a ${email}? Esta acción desactiva la cuenta permanentemente.`)) return;
    feedback.clear();
    try {
      await deleteUser(userId);
      setUsers((prev) => prev.filter((u) => u.id !== userId));
      feedback.showSuccess(`Usuario ${email} eliminado`);
    } catch (err) {
      feedback.showError(err.message);
    }
  }

  async function saveRoles(userId) {
    const nextRoles = draftRolesByUser[userId] || [];
    const isVerified = nextRoles.includes('ROLE_VERIFIED_USER');
    setSavingUserId(userId);
    feedback.clear();
    try {
      const updatedUser = await updateAccessUserRoles(userId, nextRoles);
      await updateVerificationStatus(userId, {
        newStatus: isVerified ? 'IDENTITY_VERIFIED' : 'UNVERIFIED',
        notes: isVerified ? 'Verificado via panel de control de accesos' : 'Verificacion removida via panel de control de accesos'
      });
      setUsers((prev) => prev.map((row) => (row.id === userId ? updatedUser : row)));
      feedback.showSuccess(`Roles actualizados para ${updatedUser.email}`);
    } catch (err) {
      feedback.showError(err.message);
    } finally {
      setSavingUserId('');
    }
  }

  function updatePrimaryChatRegion(userId, primaryRegionId) {
    setDraftChatAccessByUser((prev) => ({
      ...prev,
      [userId]: {
        primaryRegionId,
        additionalRegionIds: prev[userId]?.additionalRegionIds || []
      }
    }));
  }

  function toggleAdditionalChatRegion(userId, regionId) {
    setDraftChatAccessByUser((prev) => {
      const current = new Set(prev[userId]?.additionalRegionIds || []);
      if (current.has(regionId)) {
        current.delete(regionId);
      } else {
        current.add(regionId);
      }
      return {
        ...prev,
        [userId]: {
          primaryRegionId: prev[userId]?.primaryRegionId || '',
          additionalRegionIds: [...current]
        }
      };
    });
  }

  async function saveChatAccess(userId) {
    const nextAccess = draftChatAccessByUser[userId] || { primaryRegionId: '', additionalRegionIds: [] };
    setSavingUserId(userId);
    feedback.clear();
    try {
      const updatedUser = await updateCommunityChatAccess(userId, nextAccess);
      setUsers((prev) => prev.map((row) => (row.id === userId ? updatedUser : row)));
      feedback.showSuccess(`Acceso regional de chat actualizado para ${updatedUser.email}`);
    } catch (err) {
      feedback.showError(err.message);
    } finally {
      setSavingUserId('');
    }
  }

  function toggleModuleRegion(userId, regionId) {
    setDraftModuleAccessByUser((prev) => {
      const current = new Set(prev[userId] || []);
      if (current.has(regionId)) {
        current.delete(regionId);
      } else {
        current.add(regionId);
      }
      return { ...prev, [userId]: [...current] };
    });
  }

  async function saveModuleAccess(userId) {
    const nextRegionIds = draftModuleAccessByUser[userId] || [];
    setSavingUserId(userId);
    feedback.clear();
    try {
      const updatedUser = await updateCommunityModuleAccess(userId, { regionIds: nextRegionIds });
      if (updatedUser) {
        setUsers((prev) => prev.map((row) => (row.id === userId ? updatedUser : row)));
        feedback.showSuccess(`Acceso modulo comunidad actualizado para ${updatedUser.email}`);
      } else {
        feedback.showSuccess('Configuracion guardada (pendiente de sincronizacion con backend).');
      }
    } catch (err) {
      feedback.showError(err.message);
    } finally {
      setSavingUserId('');
    }
  }

  async function loadVerificationEvents(userId) {
    if (eventsByUser[userId]) return;
    try {
      const events = await getVerificationEvents(userId);
      setEventsByUser((prev) => ({ ...prev, [userId]: Array.isArray(events) ? events : [] }));
    } catch {
      setEventsByUser((prev) => ({ ...prev, [userId]: [] }));
    }
  }

  function toggleVerifExpand(userId) {
    const next = expandedVerifUser === userId ? '' : userId;
    setExpandedVerifUser(next);
    setVerifDraft({ newStatus: '', notes: '' });
    if (next) loadVerificationEvents(next);
  }

  async function saveVerificationStatus(userId) {
    if (!verifDraft.newStatus || !verifDraft.notes.trim()) {
      feedback.showError('El estado y las notas son obligatorios');
      return;
    }
    setSavingVerifUserId(userId);
    feedback.clear();
    try {
      const rawUpdated = await updateVerificationStatus(userId, verifDraft);
      const updatedUser = rawUpdated?.verificationStatus !== undefined
        ? rawUpdated
        : await getAccessUserById(userId);
      setUsers((prev) => prev.map((row) => (row.id === userId ? updatedUser : row)));
      setPendingReview((prev) => prev.filter((row) => row.id !== userId));
      setEventsByUser((prev) => ({ ...prev, [userId]: undefined }));
      setExpandedVerifUser('');
      feedback.showSuccess(`Estado de verificacion actualizado para ${updatedUser.email}`);
    } catch (err) {
      feedback.showError(err.message);
    } finally {
      setSavingVerifUserId('');
    }
  }

  const dedupedRegions = useMemo(() => {
    const seen = new Set();
    return regions
      .filter((r) => r.nombre || r.name || r.id)
      .filter((r) => {
        const key = (r.nombre || r.name || r.id || '').toLowerCase().replace(/[^a-z]/g, '');
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
  }, [regions]);

  const VERIFICATION_STATUSES = [
    'EMAIL_VERIFIED',
    'PHONE_VERIFIED',
    'IDENTITY_VERIFIED',
    'FULLY_VERIFIED',
    'SUSPENDED'
  ];

  const VERIFICATION_LABEL = {
    IDENTITY_VERIFIED: 'Verificado',
    EMAIL_VERIFIED: 'Email verificado',
    FULLY_VERIFIED: 'Verificado',
    SUSPENDED: 'Suspendido',
    UNVERIFIED: 'Sin verificar'
  };

  const columns = [
    { key: 'email', header: 'Email' },
    { key: 'fullName', header: 'Nombre' },
    {
      key: 'effectiveRoles',
      header: 'Perfil',
      render: (row) => {
        const val = detectProfile(row.effectiveRoles || []);
        return PROFILE_OPTIONS.find((p) => p.value === val)?.label || 'Comunidad';
      }
    },
    {
      key: 'verificationStatus',
      header: 'Verificado',
      render: (row) => VERIFICATION_LABEL[row.verificationStatus] ?? row.verificationStatus ?? 'Sin verificar'
    }
  ];

  if (!canManageAccess) {
    return (
      <section className="page-container">
        <SectionTitle title="Control de Accesos" subtitle="Panel administrativo de roles y permisos" />
        <ErrorMessage error={{ message: 'No tienes permisos para acceder a este panel.' }} />
      </section>
    );
  }

  return (
    <section className="page-container">
      <SectionTitle title="Control de Accesos" subtitle="Gestion de roles y permisos de usuarios" />
      {feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}
      {loading ? <LoadingSpinner label="Cargando panel de accesos..." /> : null}
      {!loading && error ? <ErrorMessage error={error} /> : null}
      {!loading && !error && users.length === 0 ? <EmptyState title="Sin usuarios disponibles" /> : null}

      {!loading && !error && users.length > 0 ? (
        <>
          <h3 style={{ marginBottom: '8px', fontSize: '1rem', color: 'var(--color-text-muted, #666)' }}>
            Gestión individual de usuarios
          </h3>
          <div className="access-grid">
            {users.map((target) => (
              <article key={target.id} className="access-user-card">
                <div className="access-user-header">
                  <div>
                    <h3>{target.fullName || target.email}</h3>
                    <p>{target.email}</p>
                  </div>
                  {isSuperAdmin && target.id !== user?.id && (
                    <button
                      type="button"
                      className="btn btn-danger btn-sm"
                      onClick={() => handleDeleteUser(target.id, target.email)}
                    >
                      Eliminar
                    </button>
                  )}
                </div>

                <div className="access-controls-row">
                  <label>
                    <span>Perfil</span>
                    <select
                      value={detectProfile(draftRolesByUser[target.id] || [])}
                      onChange={(event) => applyProfile(target.id, event.target.value)}
                    >
                      {PROFILE_OPTIONS.map((profile) => (
                        <option key={profile.value} value={profile.value}>
                          {profile.label}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="access-toggle">
                    <span>Usuario verificado</span>
                    <input
                      type="checkbox"
                      checked={(draftRolesByUser[target.id] || []).includes('ROLE_VERIFIED_USER')}
                      onChange={(event) => toggleVerifiedSwitch(target.id, event.target.checked)}
                    />
                  </label>
                </div>

                <details className="access-help">
                  <summary>ⓘ Sobre los perfiles</summary>
                  <ul>
                    <li><strong>Comunidad</strong>: acceso básico al sistema, sin acceso a recursos comunitarios.</li>
                    <li><strong>Moderador</strong>: modera contenido y accede a recursos comunitarios (requiere verificación activa).</li>
                    <li><strong>Administrador</strong>: acceso completo al sistema y a todos los recursos.</li>
                    <li><strong>Super Admin</strong>: gestión total, incluida la eliminación de usuarios.</li>
                  </ul>
                </details>

                <details className="access-advanced">
                  <summary>Asignar o cambiar perfil</summary>
                  <div className="access-role-list">
                    {roles.map((role) => (
                      <label key={`${target.id}-${role.code}`} className="access-role-item">
                        <input
                          type="checkbox"
                          checked={(draftRolesByUser[target.id] || []).includes(role.code)}
                          onChange={() => toggleRole(target.id, role.code)}
                        />
                        <span>{role.code}</span>
                      </label>
                    ))}
                  </div>
                </details>

                <details className="access-help">
                  <summary>ⓘ Sobre el chat comunitario</summary>
                  <p>Define en qué salas regionales puede participar el usuario. La región principal es su sala base; las adicionales permiten coordinación entre regiones.</p>
                </details>

                <details className="access-advanced">
                  <summary>Acceso a chat comunitario</summary>
                  <label>
                    <span>Region principal</span>
                    <select
                      value={draftChatAccessByUser[target.id]?.primaryRegionId || ''}
                      onChange={(event) => updatePrimaryChatRegion(target.id, event.target.value)}
                    >
                      <option value="">Sin region asignada</option>
                      {dedupedRegions.map((region) => (
                        <option key={region.id} value={region.id}>
                          {region.nombre || region.name || region.id}
                        </option>
                      ))}
                    </select>
                  </label>

                  <p className="community-source-note">
                    Por defecto accede a la sala general y a su region principal. Marca regiones extra solo cuando
                    necesite coordinar con otras subsalas.
                  </p>

                  <div className="access-role-list">
                    {dedupedRegions.map((region) => (
                      <label key={`${target.id}-chat-${region.id}`} className="access-role-item">
                        <input
                          type="checkbox"
                          checked={(draftChatAccessByUser[target.id]?.additionalRegionIds || []).includes(region.id)}
                          onChange={() => toggleAdditionalChatRegion(target.id, region.id)}
                        />
                        <span>{region.nombre || region.name || region.id}</span>
                      </label>
                    ))}
                  </div>

                  <div className="form-actions">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={savingUserId === target.id}
                      onClick={() => saveChatAccess(target.id)}
                    >
                      {savingUserId === target.id ? 'Guardando...' : 'Guardar acceso chat'}
                    </button>
                  </div>
                </details>

                <details className="access-help">
                  <summary>ⓘ Sobre los recursos comunitarios</summary>
                  <p>Controla qué regiones puede consultar el usuario en el módulo comunitario (tablero, recursos, contactos). Los administradores ven todas las regiones sin restricción.</p>
                </details>

                <details className="access-advanced">
                  <summary>Acceso a recursos comunitarios</summary>
                  <p className="community-source-note">
                    Los administradores ven todas las regiones sin restriccion.
                  </p>
                  <div className="access-role-list">
                    {dedupedRegions.map((region) => (
                      <label key={`${target.id}-module-${region.id}`} className="access-role-item">
                        <input
                          type="checkbox"
                          checked={(draftModuleAccessByUser[target.id] || []).includes(region.id)}
                          onChange={() => toggleModuleRegion(target.id, region.id)}
                        />
                        <span>{region.nombre || region.name || region.id}</span>
                      </label>
                    ))}
                  </div>
                  <div className="form-actions">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={savingUserId === target.id}
                      onClick={() => saveModuleAccess(target.id)}
                    >
                      {savingUserId === target.id ? 'Guardando...' : 'Guardar acceso'}
                    </button>
                  </div>
                </details>

                <div className="form-actions">
                  <button
                    type="button"
                    className="btn"
                    disabled={savingUserId === target.id}
                    onClick={() => saveRoles(target.id)}
                  >
                    {savingUserId === target.id ? 'Guardando...' : 'Guardar roles'}
                  </button>
                </div>
              </article>
            ))}
          </div>

          <h3 style={{ marginTop: '2rem', marginBottom: '8px', fontSize: '1rem', color: 'var(--color-text-muted, #666)' }}>
            Resumen de usuarios ({users.length})
          </h3>
          <DataTable columns={columns} rows={users} rowKey="id" />

          <h3 style={{ marginTop: '2rem' }}>Verificaciones pendientes de revision</h3>
          {pendingReview.length === 0 ? (
            <p style={{ color: 'var(--color-text-muted, #888)', fontSize: '0.9rem' }}>
              Sin usuarios pendientes de revision de identidad.
            </p>
          ) : (
            <div className="access-grid">
              {pendingReview.map((row) => (
                <article key={row.id} className="access-user-card">
                  <div className="access-user-header">
                    <h3>{row.fullName || row.email}</h3>
                    <p>{row.email}</p>
                    <p>
                      Estado actual:{' '}
                      <strong>{row.currentStatus}</strong>
                    </p>
                    {row.lastEvent && (
                      <p style={{ fontSize: '0.8rem', color: 'var(--color-text-muted, #888)' }}>
                        Cambio de identidad:{' '}
                        {row.lastEvent.createdAt
                          ? new Date(row.lastEvent.createdAt).toLocaleString('es-CL')
                          : '-'}
                      </p>
                    )}
                  </div>

                  <details
                    open={expandedVerifUser === row.id}
                    onToggle={(e) => {
                      if (e.target.open) toggleVerifExpand(row.id);
                      else setExpandedVerifUser('');
                    }}
                  >
                    <summary>Historial de verificacion</summary>
                    {eventsByUser[row.id] === undefined ? (
                      <p>Cargando...</p>
                    ) : eventsByUser[row.id].length === 0 ? (
                      <p>Sin eventos registrados.</p>
                    ) : (
                      <table style={{ width: '100%', fontSize: '0.8rem', borderCollapse: 'collapse', marginTop: '0.5rem' }}>
                        <thead>
                          <tr>
                            <th style={{ textAlign: 'left', padding: '4px 8px' }}>Tipo</th>
                            <th style={{ textAlign: 'left', padding: '4px 8px' }}>Estado anterior</th>
                            <th style={{ textAlign: 'left', padding: '4px 8px' }}>Estado nuevo</th>
                            <th style={{ textAlign: 'left', padding: '4px 8px' }}>Notas</th>
                            <th style={{ textAlign: 'left', padding: '4px 8px' }}>Fecha</th>
                          </tr>
                        </thead>
                        <tbody>
                          {eventsByUser[row.id].map((ev) => (
                            <tr key={ev.id}>
                              <td style={{ padding: '4px 8px' }}>{ev.eventType}</td>
                              <td style={{ padding: '4px 8px' }}>{ev.oldStatus || '-'}</td>
                              <td style={{ padding: '4px 8px' }}>{ev.newStatus}</td>
                              <td style={{ padding: '4px 8px' }}>{ev.notes || '-'}</td>
                              <td style={{ padding: '4px 8px' }}>
                                {ev.createdAt ? new Date(ev.createdAt).toLocaleString('es-CL') : '-'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </details>

                  <div style={{ marginTop: '0.75rem' }}>
                    <label style={{ display: 'block', marginBottom: '0.4rem' }}>
                      <span>Nuevo estado</span>
                      <select
                        value={expandedVerifUser === row.id ? verifDraft.newStatus : ''}
                        onChange={(e) => setVerifDraft((prev) => ({ ...prev, newStatus: e.target.value }))}
                        style={{ display: 'block', width: '100%', marginTop: '0.25rem' }}
                      >
                        <option value="">Seleccionar estado...</option>
                        {VERIFICATION_STATUSES.map((s) => (
                          <option key={s} value={s}>{s}</option>
                        ))}
                      </select>
                    </label>

                    <label style={{ display: 'block', marginBottom: '0.75rem' }}>
                      <span>Notas de revision (obligatorio)</span>
                      <textarea
                        rows={2}
                        value={expandedVerifUser === row.id ? verifDraft.notes : ''}
                        onChange={(e) => setVerifDraft((prev) => ({ ...prev, notes: e.target.value }))}
                        placeholder="Descripcion de la decision..."
                        style={{ display: 'block', width: '100%', marginTop: '0.25rem', resize: 'vertical' }}
                      />
                    </label>

                    <button
                      type="button"
                      className="btn"
                      disabled={savingVerifUserId === row.id}
                      onClick={() => saveVerificationStatus(row.id)}
                    >
                      {savingVerifUserId === row.id ? 'Guardando...' : 'Guardar estado'}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}

        </>
      ) : null}
    </section>
  );
}

export default AccessControlPage;
