import { describe, expect, it } from 'vitest';

describe('test tooling', () => {
  it('runs vitest in the node environment', () => {
    expect(typeof window).toBe('undefined');
    expect(1 + 1).toBe(2);
  });
});
