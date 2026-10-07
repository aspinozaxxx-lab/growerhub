import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CareEntryEditor from './CareEntryEditor';
import { careRequest, uploadCarePhoto } from '../../api/care';

vi.mock('../../api/care', () => ({ careRequest: vi.fn(), uploadCarePhoto: vi.fn() }));
vi.mock('./careModel', async importOriginal => ({ ...await importOriginal(), prepareCarePhoto: vi.fn(async file => file) }));

describe('Care entry retry', () => {
  beforeEach(() => { careRequest.mockResolvedValue({ entry: { id: 12 } }); });
  afterEach(() => { cleanup(); vi.resetAllMocks(); });

  it('posle oshibki foto povtoryaet tolko nezavershennuyu zagruzku', async () => {
    const saved = vi.fn(); const closed = vi.fn();
    uploadCarePhoto.mockResolvedValueOnce({ id: 1 }).mockRejectedValueOnce(new Error('Сеть недоступна')).mockResolvedValueOnce({ id: 2 });
    render(<CareEntryEditor plantId={42} action="photo" onSaved={saved} onClose={closed} />);
    fireEvent.change(screen.getByLabelText('Фотографии'), { target: { files: [new File(['first'], 'first.jpg', { type: 'image/jpeg' }), new File(['second'], 'second.jpg', { type: 'image/jpeg' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить', exact: true }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Запись сохранена');
    expect(closed).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Сохранить', exact: true })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить', exact: true }));
    await waitFor(() => expect(closed).toHaveBeenCalledOnce());
    expect(saved).toHaveBeenCalledOnce();
    expect(careRequest.mock.calls.filter(([, method]) => method === 'POST')).toHaveLength(1);
    expect(careRequest).toHaveBeenLastCalledWith('/entries/12', 'PATCH', expect.any(Object));
    expect(uploadCarePhoto).toHaveBeenCalledTimes(3);
    expect(uploadCarePhoto.mock.calls[2][1].name).toBe('second.jpg');
  });
});
