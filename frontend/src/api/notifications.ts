import {validAccessToken} from '../auth/keycloak';
import {apiRequest} from './client';
import type {Page} from './organizations';

export type NotificationItem = {
  id: string;
  type: string;
  title?: string;
  body: string;
  resourceType?: string;
  resourceId?: string;
  actionUrl?: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  createdAt: string;
  expiresAt?: string;
  status: string;
  readAt?: string;
  read: boolean;
};

export type NotificationPreferences = {
  inAppEnabled: boolean;
  emailEnabled: boolean;
  smsEnabled: boolean;
  pushEnabled: boolean;
  quietHoursStart?: string;
  quietHoursEnd?: string;
  locale: 'en' | 'fr';
  updatedAt: string;
};

export const notificationKeys = {
  all: ['notifications'] as const,
  list: (unreadOnly: boolean) => ['notifications', 'list', unreadOnly] as const,
  unread: ['notifications', 'unread-count'] as const,
  preferences: ['notifications', 'preferences'] as const,
};

export const listNotifications = (unreadOnly = false) =>
  apiRequest<Page<NotificationItem>>(`/notifications?unreadOnly=${unreadOnly}&size=50`);
export const getUnreadCount = () =>
  apiRequest<{unreadCount: number}>('/notifications/unread-count');
export const markNotificationRead = (id: string) =>
  apiRequest<NotificationItem>(`/notifications/${id}/read`, {method: 'PATCH'});
export const markAllNotificationsRead = () =>
  apiRequest<{updatedCount: number}>('/notifications/read-all', {method: 'POST'});
export const getNotificationPreferences = () =>
  apiRequest<NotificationPreferences>('/notifications/preferences');
export const updateNotificationPreferences = (
  preferences: Omit<NotificationPreferences, 'updatedAt'>,
) => apiRequest<NotificationPreferences>('/notifications/preferences', {
  method: 'PUT',
  body: JSON.stringify(preferences),
});

export async function subscribeToNotifications(
  personId: string,
  onNotification: (notification: NotificationItem) => void,
) {
  const api = new URL(import.meta.env.VITE_API_BASE_URL, window.location.origin);
  api.protocol = api.protocol === 'https:' ? 'wss:' : 'ws:';
  api.pathname = api.pathname.replace(/\/api\/v1\/?$/, '/ws');
  api.search = '';
  let socket: WebSocket | undefined;
  let stopped = false;
  let retry: number | undefined;
  const connect = async () => {
    const token = await validAccessToken();
    if (stopped) return;
    let buffer = '';
    socket = new WebSocket(api);
    socket.onopen = () => socket?.send(
      `CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer ${token}\n\n\0`,
    );
    socket.onmessage = event => {
      buffer += String(event.data);
      const frames = buffer.split('\0');
      buffer = frames.pop() ?? '';
      for (const raw of frames) {
        const frame = raw.replace(/^\n+/, '');
        if (frame.startsWith('CONNECTED')) {
          socket?.send(
            `SUBSCRIBE\nid:notifications-${personId}\ndestination:/topic/notifications/${personId}\nack:auto\n\n\0`,
          );
        } else if (frame.startsWith('MESSAGE')) {
          const separator = frame.indexOf('\n\n');
          if (separator >= 0) {
            onNotification(JSON.parse(frame.slice(separator + 2)) as NotificationItem);
          }
        }
      }
    };
    socket.onclose = () => {
      if (!stopped) retry = window.setTimeout(() => void connect(), 3000);
    };
  };
  await connect();
  return () => {
    stopped = true;
    if (retry) window.clearTimeout(retry);
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send('DISCONNECT\nreceipt:close\n\n\0');
    }
    socket?.close();
  };
}
