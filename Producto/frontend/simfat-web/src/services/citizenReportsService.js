import axiosClient from '../api/axiosClient';
import publicAxiosClient from '../api/publicAxiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';
import { resizeImagesBatch } from '../utils/imageResize';

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
  const optimizedFiles = await resizeImagesBatch(files, {
    maxSide: 1024,
    quality: 0.76,
    mimeType: 'image/webp'
  });

  const formData = new window.FormData();
  formData.append('payload', JSON.stringify(payload));
  optimizedFiles.forEach((file) => formData.append('files', file));

  // Do NOT set Content-Type manually — axios must auto-set it with the multipart boundary
  const response = await axiosClient.post(API_ENDPOINTS.citizenReports, formData);
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

// Anonymous view of VALIDADO reports only (no id/status/reporter data).
export async function getPublicCitizenReports({ regionId, from, to } = {}) {
  const response = await publicAxiosClient.get(API_ENDPOINTS.citizenReportsPublic, {
    params: {
      regionId: regionId || undefined,
      from: from || undefined,
      to: to || undefined
    }
  });
  const data = extractData(response.data);
  return Array.isArray(data) ? data : [];
}
