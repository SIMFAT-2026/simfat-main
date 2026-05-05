import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getRegions() {
  const response = await axiosClient.get(API_ENDPOINTS.regions);
  return extractData(response.data);
}

export async function createRegion(payload) {
  const response = await axiosClient.post(API_ENDPOINTS.regions, payload);
  return extractData(response.data);
}

export async function updateRegion(id, payload) {
  const response = await axiosClient.put(`${API_ENDPOINTS.regions}/${id}`, payload);
  return extractData(response.data);
}

export async function deleteRegion(id) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.regions}/${id}`);
  return extractData(response.data);
}
