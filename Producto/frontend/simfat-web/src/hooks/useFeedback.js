import { useState, useEffect, useRef } from 'react';

export function useFeedback(autoDismissMs = 4000) {
  const [message, setMessage] = useState('');
  const [type, setType] = useState('');
  const timerRef = useRef(null);

  useEffect(() => {
    if (!message) return;
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      setMessage('');
      setType('');
    }, autoDismissMs);
    return () => clearTimeout(timerRef.current);
  }, [message, autoDismissMs]);

  function showSuccess(text) {
    setType('success');
    setMessage(text);
  }

  function showError(text) {
    setType('error');
    setMessage(text);
  }

  function clear() {
    clearTimeout(timerRef.current);
    setType('');
    setMessage('');
  }

  return { message, type, showSuccess, showError, clear };
}
