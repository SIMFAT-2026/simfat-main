/**
 * @template T
 * @typedef {Object} ApiResponse
 * @property {boolean} success
 * @property {string} message
 * @property {T} data
 * @property {string} timestamp
 */

/**
 * @typedef {Object} ApiValidationError
 * @property {string} field
 * @property {string} message
 */

/**
 * @typedef {Object} ApiErrorShape
 * @property {false} success
 * @property {number} [status]
 * @property {string} [error]
 * @property {string} message
 * @property {string} [path]
 * @property {string} [timestamp]
 * @property {ApiValidationError[]} [validationErrors]
 */

export {};
