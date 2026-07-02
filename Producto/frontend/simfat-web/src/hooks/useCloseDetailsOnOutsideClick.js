import { useEffect } from 'react';

export function useCloseDetailsOnOutsideClick(selector = '.metric-help[open], .access-help[open]') {
  useEffect(() => {
    function handleClick(e) {
      document.querySelectorAll(selector).forEach((el) => {
        if (!el.contains(e.target)) {
          el.removeAttribute('open');
        }
      });
    }
    document.addEventListener('click', handleClick, true);
    return () => document.removeEventListener('click', handleClick, true);
  }, [selector]);
}
