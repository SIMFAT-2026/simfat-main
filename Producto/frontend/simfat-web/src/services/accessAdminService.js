import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getAccessUsers() {
  const response = await axiosClient.get(API_ENDPOINTS.adminAccessUsers);
  return extractData(response.data);
}

export async function getAccessRoles() {
  const response = await axiosClient.get(API_ENDPOINTS.adminAccessRoles);
  return extractData(response.data);
}

export async function getAccessPermissions() {
  const response = await axiosClient.get(API_ENDPOINTS.adminAccessPermissions);
  return extractData(response.data);
}

export async function updateAccessUserRoles(userId, roleCodes) {
  const response = await axiosClient.put(`${API_ENDPOINTS.adminAccessUsers}/${userId}/roles`, { roleCodes });
  return extractData(response.data);
}
