import { useCallback, useEffect, useRef, useState } from 'react';
import { nextDisclosureState, shouldCloseOnOutside } from './disclosure';

// Open/close state for a button + panel pair: Escape (focus back to the button)
// and outside clicks close it; listeners exist only while it is open.
export function useDisclosure() {
  const [open, setOpen] = useState(false);
  const buttonRef = useRef(null);
  const panelRef = useRef(null);

  // Mirror of `open` readable from document listeners without re-subscribing.
  const openRef = useRef(open);
  openRef.current = open;

  const dispatch = useCallback((event) => {
    const next = nextDisclosureState(openRef.current, event);
    if (next.restoreFocus) buttonRef.current?.focus();
    setOpen(next.open);
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (event) => {
      if (shouldCloseOnOutside(event.target, [buttonRef.current, panelRef.current])) dispatch('outside');
    };
    const onKeyDown = (event) => {
      if (event.key === 'Escape') dispatch('escape');
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('touchstart', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('touchstart', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open, dispatch]);

  return { open, buttonRef, panelRef, toggle: () => dispatch('toggle') };
}
