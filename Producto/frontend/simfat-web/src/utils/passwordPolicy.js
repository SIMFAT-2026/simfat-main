const PASSWORD_MIN_LENGTH = 12;

const requirements = [
  {
    id: 'length',
    label: `Minimo ${PASSWORD_MIN_LENGTH} caracteres`,
    test: (password) => password.length >= PASSWORD_MIN_LENGTH
  },
  {
    id: 'uppercase',
    label: 'Al menos 1 letra mayuscula',
    test: (password) => /[A-Z]/.test(password)
  },
  {
    id: 'lowercase',
    label: 'Al menos 1 letra minuscula',
    test: (password) => /[a-z]/.test(password)
  },
  {
    id: 'number',
    label: 'Al menos 1 numero',
    test: (password) => /\d/.test(password)
  },
  {
    id: 'symbol',
    label: 'Al menos 1 simbolo (!@#$...)',
    test: (password) => /[^A-Za-z0-9]/.test(password)
  }
];

export function getPasswordRequirementsStatus(password) {
  return requirements.map((requirement) => ({
    ...requirement,
    passed: requirement.test(password)
  }));
}

export function getPasswordStrength(password) {
  const result = getPasswordRequirementsStatus(password);
  const score = result.filter((item) => item.passed).length;
  const ratio = score / requirements.length;

  if (!password) {
    return { label: 'Sin evaluar', tone: 'neutral', percent: 0, score, max: requirements.length };
  }

  if (ratio <= 0.4) {
    return { label: 'Debil', tone: 'weak', percent: 25, score, max: requirements.length };
  }

  if (ratio <= 0.8) {
    return { label: 'Media', tone: 'medium', percent: 60, score, max: requirements.length };
  }

  return { label: 'Fuerte', tone: 'strong', percent: 100, score, max: requirements.length };
}

export { PASSWORD_MIN_LENGTH };
