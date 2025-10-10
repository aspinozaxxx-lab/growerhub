#!/home/watering-admin/smart-watering-system/server/venv/bin/python3
import requests
import subprocess
import time
import logging
import os
from pathlib import Path

class DeployAgent:
    def __init__(self):
        self.repo_path = "/home/watering-admin/smart-watering-system"
        self.github_repo = "aspinozaxxx-lab/smart-watering-system"
        self.check_interval = 60
        self.last_commit = None
        self.github_token = os.getenv('GITHUB_TOKEN')
    
        if not self.github_token:
            self.logger.error("GITHUB_TOKEN environment variable not set")
            raise SystemExit("GITHUB_TOKEN is required")

        logging.basicConfig(level=logging.INFO)
        self.logger = logging.getLogger('deploy_agent')

    def get_latest_commit(self):
        try:
            url = f"https://api.github.com/repos/{self.github_repo}/commits/main"
            headers = {
                "Accept": "application/vnd.github.v3+json",
                "Authorization": f"token {self.github_token}"
            }
            
            response = requests.get(url, headers=headers, timeout=10)
            
            if response.status_code == 200:
                data = response.json()
                return data['sha']
            elif response.status_code == 403:
                error_data = response.json()
                if 'rate limit' in error_data.get('message', '').lower():
                    self.logger.warning("GitHub API rate limit warning (but authenticated)")
                return None
            else:
                self.logger.error(f"GitHub API error: {response.status_code} - {response.text}")
                return None
                
        except Exception as e:
            self.logger.error(f"GitHub API error: {e}")
            return None

    def deploy(self):
        try:
            self.logger.info("Starting deployment...")
            result = subprocess.run([
                "bash", f"{self.repo_path}/deploy.sh"
            ], capture_output=True, text=True, cwd=self.repo_path, timeout=300)

            if result.returncode == 0:
                self.logger.info("Deployment successful")
                if result.stdout:
                    self.logger.info(f"Deploy output: {result.stdout}")
            else:
                self.logger.error(f"Deployment failed: {result.stderr}")

        except subprocess.TimeoutExpired:
            self.logger.error("Deployment timed out after 5 minutes")
        except Exception as e:
            self.logger.error(f"Deployment error: {e}")

    def run(self):
        self.logger.info("Deploy agent started")
        self.logger.info(f"Monitoring {self.github_repo} with authenticated API")
        
        # Get initial commit
        self.last_commit = self.get_latest_commit()
        if self.last_commit:
            self.logger.info(f"Initial commit: {self.last_commit[:8]}")
        
        while True:
            try:
                latest_commit = self.get_latest_commit()
                
                if latest_commit and latest_commit != self.last_commit:
                    self.logger.info(f"New commit detected: {latest_commit[:8]}")
                    self.deploy()
                    # Update last_commit only if deployment was attempted
                    self.last_commit = latest_commit
                    
                time.sleep(self.check_interval)
                
            except KeyboardInterrupt:
                self.logger.info("Deploy agent stopped by user")
                break
            except Exception as e:
                self.logger.error(f"Main loop error: {e}")
                time.sleep(self.check_interval)

if __name__ == "__main__":
    agent = DeployAgent()
    agent.run()
