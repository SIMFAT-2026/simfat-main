import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getCitizenReports(filters = {}) {
  const response = await axiosClient.get(API_ENDPOINTS.citizenReports, {
    params: {
      regionId: filters.regionId || undefined,
      status: filters.status || undefined,
      category: filters.category || undefined
    }
  });
  return extractData(response.data);
}

export async function createCitizenReport({ payload, files = [] }) {
  const formData = new window.FormData();
  formData.append('payload', JSON.stringify(payload));
  files.forEach((file) => formData.append('files', file));

  const response = await axiosClient.post(API_ENDPOINTS.citizenReports, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
  return extractData(response.data);
}

export async function updateCitizenReportStatus(id, status) {
  const response = await axiosClient.patch(`${API_ENDPOINTS.citizenReports}/${id}/status`, { status });
  return extractData(response.data);
}

export async function deleteCitizenReport(id) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.citizenReports}/${id}`);
  return extractData(response.data);
}
