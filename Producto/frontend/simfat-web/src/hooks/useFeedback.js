import { useState } from 'react';

export function useFeedback() {
  const [message, setMessage] = useState('');
  const [type, setType] = useState('');

  function showSuccess(text) {
    setType('success');
    setMessage(text);
  }

  function showError(text) {
    setType('error');
    setMessage(text);
  }

  function clear() {
    setType('');
    setMessage('');
  }

  return { message, type, showSuccess, showError, clear };
}
