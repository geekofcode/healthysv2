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
