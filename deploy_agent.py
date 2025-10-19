#!/usr/bin/env python3
import os, time, logging, subprocess, requests

class DeployAgent:
    def __init__(self):
        logging.basicConfig(level=logging.INFO)
        self.logger = logging.getLogger('deploy_agent')

        self.repo_path = "/home/watering-admin/growerhub"
        self.github_repo = "aspinozaxxx-lab/growerhub"
        self.check_interval = 60
        self.last_commit = None
        self.github_token = os.getenv('GITHUB_TOKEN')
        if not self.github_token:
            self.logger.error("GITHUB_TOKEN environment variable not set")
            raise SystemExit(1)

    def get_latest_commit(self):
        try:
            url = f"https://api.github.com/repos/{self.github_repo}/commits/main"
            headers = {"Accept":"application/vnd.github.v3+json","Authorization":f"token {self.github_token}"}
            r = requests.get(url, headers=headers, timeout=10)
            if r.status_code == 200: return r.json()['sha']
            if r.status_code == 403 and 'rate limit' in r.json().get('message','').lower():
                self.logger.warning("GitHub rate limit (auth)"); return None
            self.logger.error(f"GitHub error: {r.status_code} - {r.text}"); return None
        except Exception as e:
            self.logger.error(f"GitHub API error: {e}"); return None

    def get_local_commit(self):
        try:
            out = subprocess.check_output(["git","-C",self.repo_path,"rev-parse","HEAD"], text=True, timeout=10)
            return out.strip()
        except Exception as e:
            self.logger.warning(f"Local repo HEAD unknown: {e}")
            return None

    def deploy(self):
        try:
            self.logger.info("Starting deployment...")
            res = subprocess.run(["bash", f"{self.repo_path}/deploy.sh"],
                                 capture_output=True, text=True, cwd=self.repo_path, timeout=300)
            if res.returncode == 0:
                self.logger.info("Deployment successful")
                if res.stdout: self.logger.info(f"Deploy output: {res.stdout}")
            else:
                self.logger.error(f"Deployment failed: {res.stderr}")
        except subprocess.TimeoutExpired:
            self.logger.error("Deployment timed out (5m)")
        except Exception as e:
            self.logger.error(f"Deployment error: {e}")

    def run(self):
        self.logger.info("Deploy agent started")
        self.logger.info(f"Monitoring {self.github_repo} with authenticated API")

        remote = self.get_latest_commit()
        if remote: self.logger.info(f"Initial commit: {remote[:8]}")
        local = self.get_local_commit()

        # ВАЖНО: если локальный HEAD != удалённому — деплоим сразу
        if remote and local != remote:
            self.logger.info(f"Local HEAD != remote ({(local or 'none')[:8]} -> {remote[:8]}), deploying...")
            self.deploy()
        self.last_commit = remote

        while True:
            try:
                latest = self.get_latest_commit()
                if latest and latest != self.last_commit:
                    self.logger.info(f"New commit detected: {latest[:8]}")
                    self.deploy()
                    self.last_commit = latest
                time.sleep(self.check_interval)
            except KeyboardInterrupt:
                self.logger.info("Stopped by user"); break
            except Exception as e:
                self.logger.error(f"Main loop error: {e}"); time.sleep(self.check_interval)

if __name__ == "__main__":
    DeployAgent().run()
