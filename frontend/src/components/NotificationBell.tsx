import {useQuery} from '@tanstack/react-query';
import {NavLink} from 'react-router-dom';
import {useTranslation} from 'react-i18next';

import {getUnreadCount, notificationKeys} from '../api/notifications';

export function NotificationBell() {
  const {t} = useTranslation();
  const count = useQuery({
    queryKey: notificationKeys.unread,
    queryFn: getUnreadCount,
    refetchInterval: 30_000,
  });

  const unread = count.data?.unreadCount ?? 0;
  return <NavLink className="notification-bell" to="/notifications" aria-label={t('notifications.title')}>
    <span aria-hidden="true">🔔</span>
    {unread > 0 && <b>{unread > 99 ? '99+' : unread}</b>}
  </NavLink>;
}
