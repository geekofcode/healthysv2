import {describe, expect, it, vi} from 'vitest';

vi.mock('./client', () => ({apiRequest: vi.fn()}));
vi.mock('../auth/keycloak', () => ({validAccessToken: vi.fn()}));

import {apiRequest} from './client';
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  updateNotificationPreferences,
} from './notifications';

describe('notifications API', () => {
  it('loads unread notifications with pagination', () => {
    listNotifications(true);
    expect(apiRequest).toHaveBeenCalledWith('/notifications?unreadOnly=true&size=50');
  });

  it('marks one or all notifications as read', () => {
    markNotificationRead('notification-1');
    expect(apiRequest).toHaveBeenCalledWith('/notifications/notification-1/read', {method: 'PATCH'});
    markAllNotificationsRead();
    expect(apiRequest).toHaveBeenCalledWith('/notifications/read-all', {method: 'POST'});
  });

  it('serializes delivery preferences', () => {
    const preferences = {
      inAppEnabled: true,
      emailEnabled: true,
      smsEnabled: false,
      pushEnabled: false,
      quietHoursStart: '22:00',
      quietHoursEnd: '07:00',
      locale: 'fr' as const,
    };
    updateNotificationPreferences(preferences);
    expect(apiRequest).toHaveBeenCalledWith('/notifications/preferences', {
      method: 'PUT',
      body: JSON.stringify(preferences),
    });
  });
});
