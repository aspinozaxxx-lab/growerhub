import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Link, MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Layout from './Layout';

vi.mock('../../features/auth/AuthContext', () => ({
  useAuth: () => ({ accountStatus: 'unauthorized', demoActive: false }),
}));
vi.mock('../../features/shop', () => ({
  useShop: () => ({ cartCount: 0, openConsultation: vi.fn() }),
}));

let scrollTo;
beforeEach(() => { scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {}); });
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

function Navigation() {
  const navigate = useNavigate();
  return <>
    <Link to="/novosti/">Читать новости</Link>
    <Link to="/articles/uchet-vody-rashodomer-growerhub/">Открыть материал</Link>
    <Link to="/novosti/#release-2026-10-02">Перейти к выпуску</Link>
    <Link to="/app/settings/">В кабинет</Link>
    <button onClick={() => navigate(-1)}>Назад</button>
  </>;
}

const open = () => render(<MemoryRouter initialEntries={['/articles/']}>
  <Layout><Navigation /></Layout>
</MemoryRouter>);

it('nachinaet chtenie novostej i materiala sverhu pri perehode po ssylke', () => {
  open();
  expect(scrollTo).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('link', { name: 'Читать новости', exact: true }));
  expect(scrollTo).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'instant' });
  scrollTo.mockClear();
  fireEvent.click(screen.getByRole('link', { name: 'Открыть материал', exact: true }));
  expect(scrollTo).toHaveBeenCalledOnce();
});

it('ne sbivaet vozvrat po istorii ili adresnyj yakor', () => {
  open();
  fireEvent.click(screen.getByRole('link', { name: 'Читать новости', exact: true }));
  scrollTo.mockClear();
  fireEvent.click(screen.getByRole('button', { name: 'Назад', exact: true }));
  expect(scrollTo).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('link', { name: 'Перейти к выпуску', exact: true }));
  expect(scrollTo).not.toHaveBeenCalled();
});

it('ne menyaet prokrutku kabineta', () => {
  open();
  fireEvent.click(screen.getByRole('link', { name: 'В кабинет', exact: true }));
  expect(scrollTo).not.toHaveBeenCalled();
});
