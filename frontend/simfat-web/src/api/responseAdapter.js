/**
 * @param {any} payload
 */
export function isWrappedApiResponse(payload) {
  return Boolean(payload) && typeof payload === 'object' && 'success' in payload && 'data' in payload;
}

/**
 * Keep retrocompatibility with previous plain payloads.
 * @template T
 * @param {import('./types').ApiResponse<T> | T} payload
 * @returns {T}
 */
export function extractData(payload) {
  if (isWrappedApiResponse(payload)) {
    return payload.data;
  }

  return payload;
}

/**
 * @param {any} payload
 * @returns {string}
 */
export function extractMessage(payload) {
  if (payload && typeof payload === 'object' && 'message' in payload && typeof payload.message === 'string') {
    return payload.message;
  }

  return '';
}
