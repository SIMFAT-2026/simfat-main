/**
 * @typedef {Object} ApiErrorOptions
 * @property {number} [status]
 * @property {string} [path]
 * @property {string} [error]
 * @property {{ field: string, message: string }[]} [validationErrors]
 * @property {string} [timestamp]
 * @property {unknown} [raw]
 */

export class ApiError extends Error {
  /**
   * @param {string} message
   * @param {ApiErrorOptions} [options]
   */
  constructor(message, options = {}) {
    super(message || 'Unexpected API error');
    this.name = 'ApiError';
    this.status = options.status || 0;
    this.path = options.path || '';
    this.error = options.error || '';
    this.validationErrors = options.validationErrors || [];
    this.timestamp = options.timestamp || '';
    this.raw = options.raw;
  }
}

/**
 * @param {any} error
 * @returns {ApiError}
 */
export function toApiError(error) {
  const payload = error?.response?.data;

  if (payload && typeof payload === 'object') {
    return new ApiError(payload.message || 'API request failed', {
      status: payload.status || error?.response?.status,
      path: payload.path,
      error: payload.error,
      validationErrors: Array.isArray(payload.validationErrors) ? payload.validationErrors : [],
      timestamp: payload.timestamp,
      raw: payload
    });
  }

  return new ApiError(error?.message || 'Network error', {
    status: error?.response?.status || 0,
    raw: error
  });
}
