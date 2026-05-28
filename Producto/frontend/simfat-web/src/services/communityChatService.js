import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

const CHAT_BASE = API_ENDPOINTS.communityChat;

export async function getCommunityChatRooms() {
  const response = await axiosClient.get(`${CHAT_BASE}/rooms`);
  return extractData(response.data);
}

export async function getCommunityChatMessages(roomId, { after = '', limit = 50 } = {}) {
  const response = await axiosClient.get(`${CHAT_BASE}/rooms/${roomId}/messages`, {
    params: {
      ...(after ? { after } : {}),
      limit
    }
  });
  return extractData(response.data);
}

export async function sendCommunityChatMessage(roomId, content) {
  const response = await axiosClient.post(`${CHAT_BASE}/rooms/${roomId}/messages`, { content });
  return extractData(response.data);
}

export async function updateCommunityChatPresence(roomId, state) {
  const response = await axiosClient.put(`${CHAT_BASE}/presence`, { roomId, state });
  return extractData(response.data);
}

export async function moderateCommunityChatMessage(messageId, { action = 'HIDE', reason = '' } = {}) {
  const response = await axiosClient.post(`${CHAT_BASE}/messages/${messageId}/moderate`, { action, reason });
  return extractData(response.data);
}
