import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useAuth } from '../auth/AuthContext';
import { useFeedback } from '../hooks';
import { getAccessPermissions, getAccessRoles, getAccessUsers, updateAccessUserRoles } from '../services';

const PROFILE_OPTIONS = [
  { value: 'COMMUNITY', label: 'Comunidad', roles: ['ROLE_COMMUNITY_USER'] },
  { value: 'VERIFIED', label: 'Verificado', roles: ['ROLE_VERIFIED_USER', 'ROLE_COMMUNITY_USER'] },
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
  const [draftRolesByUser, setDraftRolesByUser] = useState({});

  const canManageAccess = useMemo(() => {
    const roleSet = new Set(user?.roles || []);
    return roleSet.has('ROLE_ADMIN') || roleSet.has('ROLE_SUPER_ADMIN') || roleSet.has('ADMIN');
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
        const [usersData, rolesData, permissionsData] = await Promise.all([
          getAccessUsers(),
          getAccessRoles(),
          getAccessPermissions()
        ]);

        const normalizedUsers = Array.isArray(usersData) ? usersData : [];
        const normalizedRoles = Array.isArray(rolesData) ? rolesData : [];
        setUsers(normalizedUsers);
        setRoles(normalizedRoles);
        setPermissions(Array.isArray(permissionsData) ? permissionsData : []);

        const draft = {};
        for (const row of normalizedUsers) {
          draft[row.id] = Array.isArray(row.assignedRoles) ? [...row.assignedRoles] : [];
        }
        setDraftRolesByUser(draft);
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

  async function saveRoles(userId) {
    const nextRoles = draftRolesByUser[userId] || [];
    setSavingUserId(userId);
    feedback.clear();
    try {
      const updatedUser = await updateAccessUserRoles(userId, nextRoles);
      setUsers((prev) => prev.map((row) => (row.id === userId ? updatedUser : row)));
      feedback.showSuccess(`Roles actualizados para ${updatedUser.email}`);
    } catch (err) {
      feedback.showError(err.message);
    } finally {
      setSavingUserId('');
    }
  }

  const columns = [
    { key: 'email', header: 'Email' },
    { key: 'fullName', header: 'Nombre' },
    {
      key: 'effectiveRoles',
      header: 'Roles efectivos',
      render: (row) => (row.effectiveRoles || []).join(', ') || '-'
    },
    {
      key: 'verificationStatus',
      header: 'Verificacion',
      render: (row) => row.verificationStatus || 'UNVERIFIED'
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
          <DataTable columns={columns} rows={users} rowKey="id" />

          <div className="access-grid">
            {users.map((target) => (
              <article key={target.id} className="access-user-card">
                <div className="access-user-header">
                  <h3>{target.fullName || target.email}</h3>
                  <p>{target.email}</p>
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

                <details className="access-advanced">
                  <summary>Ajustes avanzados</summary>
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

          <h3 style={{ marginTop: '1.5rem' }}>Catalogo de permisos</h3>
          <DataTable
            rowKey="code"
            rows={permissions}
            columns={[
              { key: 'code', header: 'Permiso' },
              { key: 'module', header: 'Modulo' },
              { key: 'name', header: 'Nombre' },
              { key: 'description', header: 'Descripcion' }
            ]}
          />
        </>
      ) : null}
    </section>
  );
}

export default AccessControlPage;
