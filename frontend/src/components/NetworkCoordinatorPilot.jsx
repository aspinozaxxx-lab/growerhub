import { useState } from 'react';
import { getPageContent } from '../content/pages';
import TelegramContactLink from './TelegramContactLink';
import Button from './ui/Button';
import Modal from './ui/Modal';
import HubPhotos from './HubPhotos';

function NetworkCoordinatorPilot({ placement }) {
  const { networkCoordinator: pilot } = getPageContent().platformContent;
  const [step, setStep] = useState(null);
  const close = () => setStep(null);

  return <>
    <Button onClick={() => setStep('intent')}>{pilot.button}</Button>
    <Modal isOpen={step !== null} title={pilot.dialog_title} closeLabel={pilot.close}
      onClose={close} presentation="sheet" size="sm">
      {step === 'intent' ? <>
        <HubPhotos type="networkCoordinator" compact />
        <p>{pilot.question}</p>
        <p>{pilot.requirement}</p>
        <div className="cta-row">
          <Button variant="primary" onClick={() => setStep('contact')}>{pilot.interested}</Button>
          <Button onClick={close}>{pilot.browsing}</Button>
        </div>
      </> : <>
        <p>{pilot.contact_intro}</p>
        <p>{pilot.contact_details}</p>
        <p>{pilot.contact_note}</p>
        <TelegramContactLink placement={placement} className="hero-cta">{pilot.contact_button}</TelegramContactLink>
      </>}
    </Modal>
  </>;
}

export default NetworkCoordinatorPilot;
