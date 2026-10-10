import { beforeEach, describe, expect, it, vi } from 'vitest';

const { authGet, publicGet } = vi.hoisted(() => ({ authGet: vi.fn(), publicGet: vi.fn() }));

vi.mock('../../../api/axiosClient', () => ({ default: { get: authGet } }));
vi.mock('../../../api/publicAxiosClient', () => ({ default: { get: publicGet } }));

import { fetchComunaMapbiomas, fetchMapbiomasLayer } from './territoryApiService';

const burned = {
  indicator: 'BURNED',
  unit: 'fraction',
  year: 2025,
  minYear: 2013,
  maxYear: 2025,
  dataVersion: 'v1',
  citation: 'MapBiomas',
  values: { a: { available: true, burnedFraction: 0.1, burnedHa: 5 } }
};

beforeEach(() => {
  authGet.mockReset();
  publicGet.mockReset();
});

describe('fetchMapbiomasLayer', () => {
  it('uses the tokenless public client and endpoint in public mode', async () => {
    publicGet.mockResolvedValue({ data: { data: { layers: { BURNED: burned } } } });
    const layer = await fetchMapbiomasLayer('BURNED', { regionId: 'CL-BI', year: 2024, publicMode: true });
    expect(authGet).not.toHaveBeenCalled();
    expect(publicGet).toHaveBeenCalledTimes(1);
    expect(publicGet.mock.calls[0][0]).toBe('/api/territory/public/layers');
    expect(publicGet.mock.calls[0][1].params).toEqual({ regionId: 'CL-BI', indicators: 'BURNED', year: 2024 });
    expect(layer).toEqual(burned);
  });

  it('uses the authenticated client and endpoint otherwise', async () => {
    authGet.mockResolvedValue({ data: { layers: { BURNED: burned } } });
    await fetchMapbiomasLayer('BURNED', { regionId: 'CL-BI', year: 2020 });
    expect(publicGet).not.toHaveBeenCalled();
    expect(authGet.mock.calls[0][0]).toBe('/api/territory/layers');
  });

  it('omits year so the backend default applies', async () => {
    authGet.mockResolvedValue({ data: { layers: { BURNED: burned } } });
    await fetchMapbiomasLayer('BURNED', { regionId: 'CL-BI' });
    expect('year' in authGet.mock.calls[0][1].params).toBe(false);
  });

  it('forwards the abort signal', async () => {
    authGet.mockResolvedValue({ data: { layers: { BURNED: burned } } });
    const signal = { aborted: false };
    await fetchMapbiomasLayer('BURNED', { regionId: 'CL-BI', signal });
    expect(authGet.mock.calls[0][1].signal).toBe(signal);
  });

  it('returns null when the response has no such layer', async () => {
    authGet.mockResolvedValue({ data: { layers: {} } });
    expect(await fetchMapbiomasLayer('LANDCOVER', { regionId: 'CL-BI', year: 2024 })).toBeNull();
  });

  it('rejects non-MapBiomas indicators without calling the network', async () => {
    await expect(fetchMapbiomasLayer('WIND', { regionId: 'CL-BI' })).rejects.toThrow(/MapBiomas/);
    expect(authGet).not.toHaveBeenCalled();
    expect(publicGet).not.toHaveBeenCalled();
  });
});

describe('fetchComunaMapbiomas', () => {
  const detail = { comunaId: 'CHL.6.1.1_1', landCover: { available: true } };

  it('reads the public detail route with the tokenless client and the year', async () => {
    publicGet.mockResolvedValue({ data: { success: true, data: detail } });
    const signal = new AbortController().signal;
    const result = await fetchComunaMapbiomas('CHL.6.1.1_1', { year: 2020, signal });
    expect(authGet).not.toHaveBeenCalled();
    expect(publicGet).toHaveBeenCalledWith('/api/territory/public/mapbiomas/CHL.6.1.1_1', {
      params: { year: 2020 },
      signal
    });
    expect(result).toEqual(detail);
  });

  it('omits the year param when not chosen and encodes the id', async () => {
    publicGet.mockResolvedValue({ data: { data: detail } });
    await fetchComunaMapbiomas('a/b');
    expect(publicGet.mock.calls[0][0]).toBe('/api/territory/public/mapbiomas/a%2Fb');
    expect(publicGet.mock.calls[0][1].params).toEqual({});
  });
});
