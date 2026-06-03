import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getAccountProfile() {
  const response = await axiosClient.get(API_ENDPOINTS.accountMe);
  return extractData(response.data);
}

export async function updateAccountProfile(payload) {
  const response = await axiosClient.patch(API_ENDPOINTS.accountMe, payload);
  return extractData(response.data);
}

export async function changePassword(payload) {
  const response = await axiosClient.post(API_ENDPOINTS.accountChangePassword, payload);
  return extractData(response.data);
}
