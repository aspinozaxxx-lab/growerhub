#!/bin/bash
cd /home/watering-admin/growerhub

echo "$(date): Starting deployment" >> /home/watering-admin/deploy.log

git fetch origin
git reset --hard origin/main

source server/venv/bin/activate
pip install -r server/requirements.txt

cd server && python -m pytest tests/ -v

if [ $? -eq 0 ]; then
    sudo systemctl restart growerhub.service
    echo "$(date): Deployment completed" >> /home/watering-admin/deploy.log
else
    echo "$(date): Tests failed - deployment aborted" >> /home/watering-admin/deploy.log
    exit 1
fi
