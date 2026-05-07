import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getForestLossRecords(filters = {}) {
  if (filters.regionId) {
    const response = await axiosClient.get(`${API_ENDPOINTS.forestLoss}/region/${filters.regionId}`);
    return extractData(response.data);
  }

  if (filters.year) {
    const response = await axiosClient.get(`${API_ENDPOINTS.forestLoss}/year/${filters.year}`);
    return extractData(response.data);
  }

  const response = await axiosClient.get(API_ENDPOINTS.forestLoss);
  return extractData(response.data);
}

export async function createForestLossRecord(payload) {
  const response = await axiosClient.post(API_ENDPOINTS.forestLoss, payload);
  return extractData(response.data);
}

export async function updateForestLossRecord(id, payload) {
  const response = await axiosClient.put(`${API_ENDPOINTS.forestLoss}/${id}`, payload);
  return extractData(response.data);
}

export async function deleteForestLossRecord(id) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.forestLoss}/${id}`);
  return extractData(response.data);
}
