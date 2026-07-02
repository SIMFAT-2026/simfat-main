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

export async function updateCommunityChatAccess(userId, payload) {
  const response = await axiosClient.put(`${API_ENDPOINTS.adminAccessUsers}/${userId}/community-chat-access`, payload);
  return extractData(response.data);
}

export async function getVerificationEvents(userId) {
  const response = await axiosClient.get(`${API_ENDPOINTS.adminAccessUsers}/${userId}/verification-events`);
  return extractData(response.data);
}

export async function updateVerificationStatus(userId, payload) {
  const response = await axiosClient.put(`${API_ENDPOINTS.adminAccessUsers}/${userId}/verification`, payload);
  return extractData(response.data);
}

export async function getPendingReview() {
  const response = await axiosClient.get(API_ENDPOINTS.adminAccessPendingReview);
  return extractData(response.data);
}

export async function getAccessUserById(userId) {
  const response = await axiosClient.get(`${API_ENDPOINTS.adminAccessUsers}/${userId}`);
  return extractData(response.data);
}

export async function deleteUser(userId) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.adminAccessUsers}/${userId}`);
  return extractData(response.data);
}

export async function updateCommunityModuleAccess(userId, payload) {
  try {
    const response = await axiosClient.put(
      `${API_ENDPOINTS.communityModuleAccess}/${userId}/community-module-access`,
      payload
    );
    return extractData(response.data);
  } catch (err) {
    if (err?.response?.status === 404) return null;
    throw err;
  }
}
