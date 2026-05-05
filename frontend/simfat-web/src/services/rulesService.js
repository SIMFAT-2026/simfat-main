import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getRules() {
  const response = await axiosClient.get(API_ENDPOINTS.rules);
  return extractData(response.data);
}

export async function createRule(payload) {
  const response = await axiosClient.post(API_ENDPOINTS.rules, payload);
  return extractData(response.data);
}

export async function updateRule(id, payload) {
  const response = await axiosClient.put(`${API_ENDPOINTS.rules}/${id}`, payload);
  return extractData(response.data);
}

export async function deleteRule(id) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.rules}/${id}`);
  return extractData(response.data);
}
