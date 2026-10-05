import { useState } from 'react';
import Modal from '../../components/ui/Modal';
import { useShop } from './ShopContext';
import { shopCopy } from './copy';
import RequestForm, { RequestReceipt } from './RequestForm';

export default function ConsultationDialog({ isOpen, onClose }) {
  const { locale } = useShop();
  const t = shopCopy[locale];
  const [receipt, setReceipt] = useState(null);
  return <div className="gh-shop-dialog">
    <Modal isOpen={isOpen} onClose={onClose} closeLabel={t.close} title={t.consultationTitle} presentation="sheet" size="sm">
      {receipt ? <><RequestReceipt receipt={receipt} kind="CONSULTATION" /><button type="button" className="gh-shop-button" onClick={onClose}>{t.close}</button></> : <RequestForm kind="CONSULTATION" onSaved={setReceipt} />}
    </Modal>
  </div>;
}
