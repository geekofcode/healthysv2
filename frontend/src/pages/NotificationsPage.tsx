import {FormEvent, useEffect, useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';

import {
  getNotificationPreferences,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  notificationKeys,
  updateNotificationPreferences,
  type NotificationPreferences,
} from '../api/notifications';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

type PreferencesForm = Omit<NotificationPreferences, 'updatedAt'>;
const defaults: PreferencesForm = {
  inAppEnabled: true,
  emailEnabled: false,
  smsEnabled: false,
  pushEnabled: false,
  locale: 'en',
};

export function NotificationsPage() {
  const {t, i18n} = useTranslation();
  const client = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [showPreferences, setShowPreferences] = useState(false);
  const [form, setForm] = useState<PreferencesForm>(defaults);
  const notifications = useQuery({
    queryKey: notificationKeys.list(unreadOnly),
    queryFn: () => listNotifications(unreadOnly),
  });
  const preferences = useQuery({
    queryKey: notificationKeys.preferences,
    queryFn: getNotificationPreferences,
  });

  useEffect(() => {
    if (!preferences.data) return;
    const {updatedAt: _updatedAt, ...value} = preferences.data;
    setForm(value);
  }, [preferences.data]);

  const refresh = () => client.invalidateQueries({queryKey: notificationKeys.all});
  const read = useMutation({mutationFn: markNotificationRead, onSuccess: refresh});
  const readAll = useMutation({mutationFn: markAllNotificationsRead, onSuccess: refresh});
  const save = useMutation({
    mutationFn: () => updateNotificationPreferences(form),
    onSuccess: value => {
      client.setQueryData(notificationKeys.preferences, value);
      setShowPreferences(false);
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };

  return <section>
    <div className="page-heading">
      <div><p className="eyebrow">{t('notifications.eyebrow')}</p><h1>{t('notifications.title')}</h1></div>
      <div className="inline-actions">
        <button className="button-secondary" onClick={() => setShowPreferences(value => !value)}>{t('notifications.preferences')}</button>
        <button disabled={readAll.isPending} onClick={() => readAll.mutate()}>{t('notifications.markAllRead')}</button>
      </div>
    </div>
    <div className="notification-filters">
      <button className={!unreadOnly ? 'active button-secondary' : 'button-secondary'} onClick={() => setUnreadOnly(false)}>{t('notifications.all')}</button>
      <button className={unreadOnly ? 'active button-secondary' : 'button-secondary'} onClick={() => setUnreadOnly(true)}>{t('notifications.unread')}</button>
    </div>
    {showPreferences && <form className="entity-form notification-preferences" onSubmit={submit}>
      {(['inAppEnabled', 'emailEnabled', 'smsEnabled', 'pushEnabled'] as const).map(channel =>
        <label className="check-field" key={channel}>
          <input type="checkbox" checked={form[channel]} onChange={event => setForm({...form, [channel]: event.target.checked})}/>
          {t(`notifications.${channel}`)}
        </label>)}
      <label>{t('notifications.quietStart')}<input type="time" value={form.quietHoursStart?.slice(0, 5) ?? ''} onChange={event => setForm({...form, quietHoursStart: event.target.value || undefined})}/></label>
      <label>{t('notifications.quietEnd')}<input type="time" value={form.quietHoursEnd?.slice(0, 5) ?? ''} onChange={event => setForm({...form, quietHoursEnd: event.target.value || undefined})}/></label>
      <label>{t('notifications.language')}<select value={form.locale} onChange={event => setForm({...form, locale: event.target.value as 'en' | 'fr'})}><option value="en">English</option><option value="fr">Français</option></select></label>
      <button disabled={save.isPending}>{t('common.save')}</button>
      {save.isError && <ApiErrorMessage error={save.error}/>}
    </form>}
    {notifications.isLoading && <p>{t('common.loading')}</p>}
    {notifications.isError && <ApiErrorMessage error={notifications.error}/>}
    <div className="notification-list">
      {notifications.data?.content.map(item => <article className={`notification-card ${item.read ? '' : 'unread'} priority-${item.priority.toLowerCase()}`} key={item.id}>
        <div>
          <span className="notification-type">{item.type.replaceAll('_', ' ')}</span>
          <small>{new Date(item.createdAt).toLocaleString(i18n.language)}</small>
        </div>
        {item.title && <h2>{item.title}</h2>}
        <p>{item.body}</p>
        <div className="inline-actions">
          {item.actionUrl?.startsWith('/') && <Link className="button" to={item.actionUrl}>{t('notifications.open')}</Link>}
          {!item.read && <button className="button-secondary" disabled={read.isPending} onClick={() => read.mutate(item.id)}>{t('notifications.markRead')}</button>}
        </div>
      </article>)}
      {notifications.data?.content.length === 0 && <p>{t('notifications.empty')}</p>}
    </div>
    {(read.isError || readAll.isError) && <ApiErrorMessage error={read.error ?? readAll.error}/>}
  </section>;
}
