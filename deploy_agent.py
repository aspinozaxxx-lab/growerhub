#!/home/watering-admin/smart-watering-system/server/venv/bin/python3
import requests
import subprocess
import time
import logging
from pathlib import Path

class DeployAgent:
    def __init__(self):
        self.repo_path = "/home/watering-admin/smart-watering-system"
        self.github_repo = "aspinozaxxx-lab/smart-watering-system"
        self.check_interval = 60
        self.last_commit = None
        
        logging.basicConfig(level=logging.INFO)
        self.logger = logging.getLogger('deploy_agent')

    def get_latest_commit(self):
        try:
            import subprocess
            import json
            
            url = f"https://api.github.com/repos/{self.github_repo}/commits/main"
            result = subprocess.run([
                "curl", "-s", "-H", "Accept: application/vnd.github.v3+json", url
            ], capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                data = json.loads(result.stdout)
                return data['sha']
            return None
        except Exception as e:
            self.logger.error(f"GitHub API error: {e}")
            return None

    def deploy(self):
        try:
            self.logger.info("Starting deployment...")
            result = subprocess.run([
                "bash", f"{self.repo_path}/deploy.sh"
            ], capture_output=True, text=True, cwd=self.repo_path)
            
            if result.returncode == 0:
                self.logger.info("Deployment successful")
            else:
                self.logger.error(f"Deployment failed: {result.stderr}")
                
        except Exception as e:
            self.logger.error(f"Deployment error: {e}")

    def run(self):
        self.logger.info("Deploy agent started")
        while True:
            try:
                latest_commit = self.get_latest_commit()
                if latest_commit and latest_commit != self.last_commit:
                    self.logger.info(f"New commit detected: {latest_commit[:8]}")
                    self.last_commit = latest_commit
                    self.deploy()
                time.sleep(self.check_interval)
            except Exception as e:
                self.logger.error(f"Main loop error: {e}")
                time.sleep(self.check_interval)

if __name__ == "__main__":
    agent = DeployAgent()
    agent.run()
