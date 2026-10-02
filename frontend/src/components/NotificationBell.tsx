import {useEffect} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {NavLink} from 'react-router-dom';
import {useTranslation} from 'react-i18next';

import {getUnreadCount, notificationKeys, subscribeToNotifications} from '../api/notifications';
import {getMe} from '../api/persons';

export function NotificationBell() {
  const {t} = useTranslation();
  const client = useQueryClient();
  const person = useQuery({queryKey: ['person', 'me'], queryFn: getMe});
  const count = useQuery({
    queryKey: notificationKeys.unread,
    queryFn: getUnreadCount,
    refetchInterval: 30_000,
  });

  useEffect(() => {
    if (!person.data?.id) return;
    let close: (() => void) | undefined;
    void subscribeToNotifications(person.data.id, () => {
      void client.invalidateQueries({queryKey: notificationKeys.all});
    }).then(value => close = value);
    return () => close?.();
  }, [client, person.data?.id]);

  const unread = count.data?.unreadCount ?? 0;
  return <NavLink className="notification-bell" to="/notifications" aria-label={t('notifications.title')}>
    <span aria-hidden="true">🔔</span>
    {unread > 0 && <b>{unread > 99 ? '99+' : unread}</b>}
  </NavLink>;
}
