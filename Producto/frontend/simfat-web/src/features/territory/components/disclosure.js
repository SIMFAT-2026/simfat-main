// Pure decision logic for the disclosure widgets (popover, info button).

// True when a pointer event landed outside every given container.
export function shouldCloseOnOutside(target, containers = []) {
  if (!target) return false;
  return !containers.some((container) => container && container.contains(target));
}

// Next state for a disclosure. `restoreFocus` asks the view to return focus to
// the trigger button (Escape only: after an outside click focus is elsewhere).
export function nextDisclosureState(open, event) {
  switch (event) {
    case 'toggle':
      return { open: !open, restoreFocus: false };
    case 'escape':
      return { open: false, restoreFocus: open };
    case 'outside':
      return { open: false, restoreFocus: false };
    default:
      return { open, restoreFocus: false };
  }
}
