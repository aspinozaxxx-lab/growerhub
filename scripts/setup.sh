#!/bin/bash
# Setup script for GrowerHub

echo "🚀 Setting up GrowerHub..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running as root
if [ "$EUID" -eq 0 ]; then
    echo -e "${RED}Error: Do not run this script as root. Run as normal user.${NC}"
    exit 1
fi

echo -e "${YELLOW}Installing system dependencies...${NC}"
sudo apt update
sudo apt install -y python3-pip python3-venv nginx

echo -e "${YELLOW}Creating Python virtual environment...${NC}"
python3 -m venv venv
source venv/bin/activate

echo -e "${YELLOW}Installing Python dependencies...${NC}"
pip install -r requirements.txt

echo -e "${YELLOW}Setting up systemd service...${NC}"
sudo cp configs/growerhub.service /etc/systemd/system/
sudo sed -i "s|/home/watering-admin/growerhub|$(pwd)|g" /etc/systemd/system/growerhub.service
sudo systemctl daemon-reload
sudo systemctl enable growerhub

echo -e "${YELLOW}Setting up Nginx...${NC}"
sudo cp configs/nginx.conf /etc/nginx/sites-available/growerhub
sudo ln -sf /etc/nginx/sites-available/growerhub /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

echo -e "${YELLOW}Testing Nginx configuration...${NC}"
sudo nginx -t

echo -e "${YELLOW}Setting up firewall...${NC}"
sudo mkdir -p /etc/iptables
sudo cp configs/iptables-rules.v4 /etc/iptables/rules.v4

echo -e "${YELLOW}Starting services...${NC}"
sudo systemctl reload nginx
sudo systemctl start growerhub

echo -e "${GREEN}✅ Setup complete!${NC}"
echo -e "${GREEN}📝 Service status: sudo systemctl status growerhub${NC}"
echo -e "${GREEN}🌐 Access: http://$(hostname -I | awk '{print $1}')/${NC}"
echo -e "${GREEN}📋 Logs: sudo journalctl -u growerhub -f${NC}"
