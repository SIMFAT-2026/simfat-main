import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getUnreadNotifications() {
  const response = await axiosClient.get(API_ENDPOINTS.notificationsUnread);
  return extractData(response.data);
}

export async function markNotificationRead(notificationId) {
  const response = await axiosClient.patch(`${API_ENDPOINTS.notifications}/${notificationId}/read`);
  return extractData(response.data);
}
