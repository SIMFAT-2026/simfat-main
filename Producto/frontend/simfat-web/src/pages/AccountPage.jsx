import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useFeedback } from '../hooks/useFeedback';
import { getAccountProfile, updateAccountProfile, changePassword } from '../services/accountService';
import { REGIONES, getComunasByRegion } from '../data/territorioChile';

const PASSWORD_POLICY = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{12,72}$/;

const VERIFICATION_LABELS = {
  UNVERIFIED: 'Sin verificar',
  EMAIL_VERIFIED: 'Email verificado',
  PHONE_VERIFIED: 'Telefono verificado',
  IDENTITY_VERIFIED: 'Identidad verificada',
  FULLY_VERIFIED: 'Completamente verificado',
  REJECTED: 'Rechazado',
  SUSPENDED: 'Suspendido',
};

function AccountPage() {
  const navigate = useNavigate();
  const profileFeedback = useFeedback();
  const passwordFeedback = useFeedback();

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);

  const [profileForm, setProfileForm] = useState({
    fullName: '',
    phone: '',
    regionCode: '',
    comunaCode: '',
  });
  const [savingProfile, setSavingProfile] = useState(false);

  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [savingPassword, setSavingPassword] = useState(false);
  const [showPasswords, setShowPasswords] = useState(false);

  useEffect(() => {
    let mounted = true;
    getAccountProfile()
      .then((data) => {
        if (!mounted) return;
        setProfile(data);
        setProfileForm({
          fullName: data.fullName || '',
          phone: data.phone || '',
          regionCode: data.regionCode || '',
          comunaCode: data.comunaCode || '',
        });
      })
      .catch(() => {
        if (mounted) profileFeedback.showError('No fue posible cargar el perfil.');
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => { mounted = false; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function onProfileChange(e) {
    const { name, value } = e.target;
    // Al cambiar región, limpiar comuna si ya no pertenece a ella
    if (name === 'regionCode') {
      setProfileForm((prev) => ({ ...prev, regionCode: value, comunaCode: '' }));
    } else {
      setProfileForm((prev) => ({ ...prev, [name]: value }));
    }
  }

  async function onProfileSubmit(e) {
    e.preventDefault();
    profileFeedback.clear();
    if (!profileForm.fullName.trim()) {
      profileFeedback.showError('El nombre no puede estar en blanco.');
      return;
    }
    setSavingProfile(true);
    try {
      const updated = await updateAccountProfile({
        fullName: profileForm.fullName.trim() || undefined,
        phone: profileForm.phone || undefined,
        regionCode: profileForm.regionCode || undefined,
        comunaCode: profileForm.comunaCode || undefined,
      });
      setProfile(updated);
      setProfileForm({
        fullName: updated.fullName || '',
        phone: updated.phone || '',
        regionCode: updated.regionCode || '',
        comunaCode: updated.comunaCode || '',
      });
      if (
        updated.verificationStatus === 'EMAIL_VERIFIED' &&
        profile?.verificationStatus &&
        ['IDENTITY_VERIFIED', 'FULLY_VERIFIED'].includes(profile.verificationStatus)
      ) {
        profileFeedback.showSuccess(
          'Perfil actualizado. Tu verificacion de identidad fue reiniciada por el cambio de nombre — un admin puede restaurarla.'
        );
      } else {
        profileFeedback.showSuccess('Perfil actualizado correctamente.');
      }
    } catch (error) {
      profileFeedback.showError(error.message || 'No fue posible actualizar el perfil.');
    } finally {
      setSavingProfile(false);
    }
  }

  function onPasswordChange(e) {
    const { name, value } = e.target;
    setPasswordForm((prev) => ({ ...prev, [name]: value }));
  }

  async function onPasswordSubmit(e) {
    e.preventDefault();
    passwordFeedback.clear();
    if (!PASSWORD_POLICY.test(passwordForm.newPassword)) {
      passwordFeedback.showError('La contrasena debe tener 12-72 caracteres, mayuscula, minuscula, numero y simbolo.');
      return;
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      passwordFeedback.showError('La confirmacion de contrasena no coincide.');
      return;
    }
    setSavingPassword(true);
    try {
      await changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
        confirmPassword: passwordForm.confirmPassword,
      });
      passwordFeedback.showSuccess(
        'Contrasena actualizada. Se cerraron todas las sesiones activas. Volveras al login en unos segundos.'
      );
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      window.setTimeout(() => navigate('/login', { replace: true }), 2500);
    } catch (error) {
      passwordFeedback.showError(error.message || 'No fue posible actualizar la contrasena.');
    } finally {
      setSavingPassword(false);
    }
  }

  if (loading) {
    return (
      <div className="page-container account-page">
        <p className="loading-state">Cargando perfil...</p>
      </div>
    );
  }

  const comunasDisponibles = getComunasByRegion(profileForm.regionCode);

  return (
    <div className="page-container account-page">

      <div className="account-header">
        <h2>Mi cuenta</h2>
        {profile && (
          <span className="badge badge-info">
            {VERIFICATION_LABELS[profile.verificationStatus] || profile.verificationStatus || 'Sin verificar'}
          </span>
        )}
      </div>

      {profile && (
        <p className="account-meta">
          {profile.email}
          {profile.organizationName ? <> &nbsp;·&nbsp; {profile.organizationName}</> : null}
        </p>
      )}

      {/* Datos personales */}
      <section className="dashboard-card account-section">
        <h3>Datos personales</h3>

        <form onSubmit={onProfileSubmit}>
          <div className="form-grid">
            <label>
              <span>Nombre completo</span>
              <input
                name="fullName"
                type="text"
                required
                maxLength={120}
                value={profileForm.fullName}
                onChange={onProfileChange}
              />
            </label>

            <label>
              <span>Telefono <span className="field-optional">(opcional)</span></span>
              <input
                name="phone"
                type="tel"
                maxLength={20}
                value={profileForm.phone}
                onChange={onProfileChange}
                placeholder="+56912345678"
              />
            </label>

            <label>
              <span>Region <span className="field-optional">(opcional)</span></span>
              <select name="regionCode" value={profileForm.regionCode} onChange={onProfileChange}>
                <option value="">— Selecciona una region —</option>
                {REGIONES.map((r) => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </label>

            <label>
              <span>Comuna <span className="field-optional">(opcional)</span></span>
              <select
                name="comunaCode"
                value={profileForm.comunaCode}
                onChange={onProfileChange}
                disabled={!profileForm.regionCode}
              >
                <option value="">
                  {profileForm.regionCode ? '— Selecciona una comuna —' : '— Primero elige region —'}
                </option>
                {comunasDisponibles.map((c) => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </label>
          </div>

          {profileFeedback.message
            ? <p className={`feedback feedback-${profileFeedback.type}`}>{profileFeedback.message}</p>
            : null}

          <div className="form-actions">
            <button type="submit" className="btn" disabled={savingProfile}>
              {savingProfile ? 'Guardando...' : 'Guardar cambios'}
            </button>
          </div>
        </form>
      </section>

      {/* Cambiar contraseña */}
      <section className="dashboard-card account-section">
        <h3>Cambiar contrasena</h3>

        <form onSubmit={onPasswordSubmit} className="auth-form">
          <label>
            Contrasena actual
            <input
              name="currentPassword"
              type={showPasswords ? 'text' : 'password'}
              required
              autoComplete="current-password"
              value={passwordForm.currentPassword}
              onChange={onPasswordChange}
            />
          </label>

          <label>
            Nueva contrasena
            <input
              name="newPassword"
              type={showPasswords ? 'text' : 'password'}
              required
              autoComplete="new-password"
              value={passwordForm.newPassword}
              onChange={onPasswordChange}
            />
          </label>

          <p className="field-hint">12-72 caracteres, mayuscula, minuscula, numero y simbolo.</p>

          <label>
            Confirmar nueva contrasena
            <input
              name="confirmPassword"
              type={showPasswords ? 'text' : 'password'}
              required
              autoComplete="new-password"
              value={passwordForm.confirmPassword}
              onChange={onPasswordChange}
            />
          </label>

          <label className="auth-inline-option">
            <input
              type="checkbox"
              checked={showPasswords}
              onChange={(e) => setShowPasswords(e.target.checked)}
            />
            Mostrar contrasenas
          </label>

          {passwordFeedback.message
            ? <p className={`feedback feedback-${passwordFeedback.type}`}>{passwordFeedback.message}</p>
            : null}

          <div className="form-actions">
            <button type="submit" className="btn btn-danger" disabled={savingPassword}>
              {savingPassword ? 'Actualizando...' : 'Cambiar contrasena'}
            </button>
          </div>
        </form>
      </section>

    </div>
  );
}

export default AccountPage;
