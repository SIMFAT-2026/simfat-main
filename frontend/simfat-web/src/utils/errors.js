export function mapValidationErrors(validationErrors = []) {
  return validationErrors.reduce((acc, item) => {
    if (item?.field) {
      acc[item.field] = item.message || 'Invalid value';
    }
    return acc;
  }, {});
}
