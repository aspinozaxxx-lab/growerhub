GrowerHub coordinator for Windows

1. Install current Node.js LTS with Corepack.
2. In GrowerHub create a connection and download configuration.yaml and secret.yaml.
3. Put both files into the data folder. Do not share secret.yaml.
4. Run setup-coordinator.bat and select COM port plus adapter:
   - zstack for SONOFF ZBDongle-P and compatible CC2652 coordinators;
   - ember for SONOFF ZBDongle-E and compatible Ember coordinators.
5. Run start-coordinator.bat. Check status with status-coordinator.bat.

The package contains no GrowerHub credentials. Telegram help is available from the onboarding screen.
