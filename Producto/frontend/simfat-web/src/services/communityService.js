import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getCommunityBoard({ regionId } = {}) {
  const response = await axiosClient.get(API_ENDPOINTS.communityBoard, {
    params: regionId ? { regionId } : undefined
  });
  return extractData(response.data);
}

export async function createCommunityBoardPost(payload, file) {
  const {
    attachmentFile,
    attachmentPreviewUrl,
    attachmentName,
    attachmentContentType,
    attachmentSize,
    attachmentImage,
    ...body
  } = payload;

  if (file) {
    const formData = new FormData();
    formData.append('payload', JSON.stringify(body));
    formData.append('file', file);

    // Do NOT set Content-Type manually — axios must auto-set it with the multipart boundary
    const response = await axiosClient.post(API_ENDPOINTS.communityBoard, formData);
    return extractData(response.data);
  }

  const response = await axiosClient.post(API_ENDPOINTS.communityBoard, body);
  return extractData(response.data);
}

export async function deleteCommunityBoardPost(id) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.communityBoard}/${id}`);
  return extractData(response.data);
}

export async function getCommunityResources({ regionId } = {}) {
  const response = await axiosClient.get(API_ENDPOINTS.communityResources, {
    params: regionId ? { regionId } : undefined
  });
  return extractData(response.data);
}

export async function createCommunityResource(payload, file) {
  const { file: payloadFile, ...body } = payload;

  if (file || payloadFile) {
    const formData = new FormData();
    formData.append('payload', JSON.stringify(body));
    formData.append('file', file || payloadFile);

    // Do NOT set Content-Type manually — axios must auto-set it with the multipart boundary
    const response = await axiosClient.post(API_ENDPOINTS.communityResources, formData);
    return extractData(response.data);
  }

  const response = await axiosClient.post(API_ENDPOINTS.communityResources, body);
  return extractData(response.data);
}

export async function deleteCommunityResource(id) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.communityResources}/${id}`);
  return extractData(response.data);
}

export async function getCommunityContacts({ regionId } = {}) {
  const response = await axiosClient.get(API_ENDPOINTS.communityContacts, {
    params: regionId ? { regionId } : undefined
  });
  return extractData(response.data);
}

export async function createCommunityContact(payload) {
  const response = await axiosClient.post(API_ENDPOINTS.communityContacts, payload);
  return extractData(response.data);
}

export async function deleteCommunityContact(id) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.communityContacts}/${id}`);
  return extractData(response.data);
}
