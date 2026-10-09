// @vitest-environment jsdom
import {afterEach, describe, expect, it, vi} from 'vitest';
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {MainLayout} from './MainLayout';
const logout = vi.fn();
vi.mock('../api/persons', () => ({getMe:vi.fn()}));
vi.mock('../auth/AuthContext', () => ({useAuth: () => ({roles:['PLATFORM_ADMIN'], username:'ada', logout})}));
vi.mock('../auth/keycloak', () => ({keycloak:{tokenParsed:{}}}));
vi.mock('@tanstack/react-query', () => ({useQuery: () => ({data:{firstName:'Ada', lastName:'Lovelace'}})}));
vi.mock('../components/NotificationBell', () => ({NotificationBell: () => <a href="/notifications">Notifications</a>}));
vi.mock('react-i18next', () => ({useTranslation: () => ({t:(key:string) => key})}));
afterEach(() => {cleanup(); vi.clearAllMocks();});
describe('application shell', () => {
 it('keeps navigation in the sidebar and opens the account actions', () => {
  render(<MemoryRouter><MainLayout /></MemoryRouter>);
  expect(document.querySelector('header nav')).toBeNull();
  expect(document.querySelector('aside nav')).not.toBeNull();
  const avatar = screen.getByRole('button', {name:'shell.accountMenu'});
  expect(avatar.textContent).toBe('A');
  fireEvent.click(avatar);
  expect(document.querySelector('.profile-summary')?.textContent).toContain('Ada Lovelace');
  fireEvent.click(screen.getByRole('button', {name:'auth.logout'}));
  expect(logout).toHaveBeenCalledOnce();
 });
 it('closes dropdown and mobile navigation using Escape', () => {
  render(<MemoryRouter><MainLayout /></MemoryRouter>);
  fireEvent.click(screen.getByRole('button', {name:'shell.accountMenu'}));
  fireEvent.keyDown(document, {key:'Escape'});
  expect(document.querySelector('.profile-dropdown')).toBeNull();
  const toggle = screen.getByRole('button', {name:'shell.toggleNavigation'});
  fireEvent.click(toggle);
  expect(document.querySelector('.sidebar.is-open')).not.toBeNull();
  fireEvent.keyDown(document, {key:'Escape'});
  expect(toggle.getAttribute('aria-expanded')).toBe('false');
 });
});
