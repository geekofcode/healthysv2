import {afterEach, describe, expect, it, vi} from 'vitest';
vi.mock('./client', () => ({apiRequest: vi.fn().mockResolvedValue({content: []})}));
import {apiRequest} from './client';
import {listAppointments} from './appointments';

describe('agenda loading', () => {
  afterEach(() => vi.clearAllMocks());
  it.each([
    [false, '/appointments?size=100'],
    [true, '/appointments/me?size=100'],
  ] as const)('uses server chronological order for mine=%s', async (mine, path) => {
    await listAppointments(mine);
    expect(apiRequest).toHaveBeenCalledWith(path);
  });
});
