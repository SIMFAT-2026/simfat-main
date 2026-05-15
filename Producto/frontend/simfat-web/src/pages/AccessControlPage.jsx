import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useAuth } from '../auth/AuthContext';
import { useFeedback } from '../hooks';
import { getAccessPermissions, getAccessRoles, getAccessUsers, updateAccessUserRoles } from '../services';

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

          <div className="form-grid" style={{ marginTop: '1rem' }}>
            {users.map((target) => (
              <div key={target.id} className="card">
                <h3>{target.email}</h3>
                <p style={{ marginBottom: '0.5rem' }}>Selecciona roles asignados:</p>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
                  {roles.map((role) => (
                    <label key={`${target.id}-${role.code}`} style={{ display: 'inline-flex', gap: '0.35rem' }}>
                      <input
                        type="checkbox"
                        checked={(draftRolesByUser[target.id] || []).includes(role.code)}
                        onChange={() => toggleRole(target.id, role.code)}
                      />
                      <span>{role.code}</span>
                    </label>
                  ))}
                </div>
                <div className="form-actions" style={{ marginTop: '0.75rem' }}>
                  <button
                    type="button"
                    className="btn"
                    disabled={savingUserId === target.id}
                    onClick={() => saveRoles(target.id)}
                  >
                    {savingUserId === target.id ? 'Guardando...' : 'Guardar roles'}
                  </button>
                </div>
              </div>
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
