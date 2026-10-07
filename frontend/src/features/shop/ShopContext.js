import { createContext, useContext } from 'react';

export const ShopContext = createContext(null);

export function useShop() {
  const value = useContext(ShopContext);
  if (!value) throw new Error('ShopProvider is required');
  return value;
}
