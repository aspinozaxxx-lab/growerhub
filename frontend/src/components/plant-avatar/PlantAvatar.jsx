import React from 'react';
import plantPot from '../../assets/plant-pot.svg';
import './PlantAvatar.css';

// Bazovyj komponent avatara rastenij bez slozhnoj logiki stadij
function PlantAvatar({
  // Identifikator i nazvanie nuzhny dlya svyazki s dannymi
  plantId,
  plantName,
  // Rezerv pod budushchee ispol'zovanie (vozrast, tip, sensery)
  plantedAt,
  plantType,
  environment,
  // Vneshnie varianty i razmery obolochki
  variant = 'card',
  size = 'md',
  // fillContainer — zapolnit' shirinu obshchego kontejnenera
  fillContainer = true,
}) {
  // Props rezerv pod budushie sloi (sroki posadki, tip, okruzhenie)
  void plantedAt;
  void plantType;
  void environment;

  // Klassovaya setka dlya variantov, razmerov i zapolneniya shiriny
  const className = [
    'plant-avatar',
    `plant-avatar--variant-${variant}`,
    `plant-avatar--size-${size}`,
    fillContainer ? 'plant-avatar--fill' : '',
  ]
    .filter(Boolean)
    .join(' ');

  const title = plantName || (plantId ? `Plant ${plantId}` : 'Plant avatar');

  return (
    <div className={className} aria-label={title}>
      {/* plant-avatar__frame — ramka s aspect ratio 3:4 i fonovymi gradientami */}
      <div className="plant-avatar__frame">
        <img
          src={plantPot}
          alt={plantName || 'Plant avatar'}
          className="plant-avatar__image"
        />
      </div>
    </div>
  );
}

export default PlantAvatar;
