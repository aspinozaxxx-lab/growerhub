.PHONY: deps ping java-backend nginx mosquitto db pgadmin \
	local-env local-deps check-local-env check-vps-env \
	legacy-ping legacy-java-backend legacy-nginx legacy-mosquitto legacy-db legacy-pgadmin \
	vps-ping vps-syntax vps-preflight vps-check vps-verify vps vps-tls

VENV ?= .venv
ANSIBLE_CORE_VERSION ?= 2.21.2
VPS_INVENTORY ?= inventory/vps/hosts.ini
VPS_VAULT_PASSWORD_FILE ?= .vault_pass.txt

LOCAL_ANSIBLE := $(VENV)/bin/ansible
LOCAL_ANSIBLE_GALAXY := $(VENV)/bin/ansible-galaxy
LOCAL_ANSIBLE_PLAYBOOK := $(VENV)/bin/ansible-playbook

deps:
	ansible-galaxy install -r requirements.yml --force

ping:
	ansible -m ping all

java-backend:
	ansible-playbook playbooks/java-backend.yml

nginx:
	ansible-playbook playbooks/nginx.yml

mosquitto:
	ansible-playbook playbooks/mosquitto.yml

db:
	ansible-playbook playbooks/gh_db_install.yml

pgadmin:
	ansible-playbook playbooks/gh_tools_pgadmin.yml

local-env:
	python3 -m venv --system-site-packages --without-pip $(VENV)
	python3 -m pip --python $(VENV)/bin/python install --upgrade pip
	$(VENV)/bin/python -m pip install --upgrade pip
	$(VENV)/bin/python -m pip install ansible-core==$(ANSIBLE_CORE_VERSION)

local-deps:
	$(LOCAL_ANSIBLE_GALAXY) collection install -r requirements.yml --force

check-local-env:
	test -x "$(LOCAL_ANSIBLE)"
	test -x "$(LOCAL_ANSIBLE_PLAYBOOK)"

check-vps-env: check-local-env
	test -f "$(VPS_VAULT_PASSWORD_FILE)"
	test -n "$$GH_VPS_SSH_KEY"
	test -f "$$GH_VPS_SSH_KEY"
	test -n "$$GH_VPS_DEPLOY_PUBLIC_KEY"
	test -f "$$GH_VPS_DEPLOY_PUBLIC_KEY"

legacy-ping: check-local-env
	$(LOCAL_ANSIBLE) -i inventory/hosts.ini -m ping all

legacy-java-backend: check-local-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i inventory/hosts.ini playbooks/java-backend.yml

legacy-nginx: check-local-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i inventory/hosts.ini playbooks/nginx.yml

legacy-mosquitto: check-local-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i inventory/hosts.ini playbooks/mosquitto.yml

legacy-db: check-local-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i inventory/hosts.ini playbooks/gh_db_install.yml

legacy-pgadmin: check-local-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i inventory/hosts.ini playbooks/gh_tools_pgadmin.yml

vps-ping: check-vps-env
	$(LOCAL_ANSIBLE) -i $(VPS_INVENTORY) \
		--private-key "$$GH_VPS_SSH_KEY" \
		--vault-password-file $(VPS_VAULT_PASSWORD_FILE) \
		-m ping vps

vps-syntax: check-vps-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i $(VPS_INVENTORY) \
		--private-key "$$GH_VPS_SSH_KEY" \
		--vault-password-file $(VPS_VAULT_PASSWORD_FILE) \
		--syntax-check playbooks/vps.yml playbooks/vps-tls.yml

vps-preflight: check-vps-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i $(VPS_INVENTORY) \
		--private-key "$$GH_VPS_SSH_KEY" \
		--vault-password-file $(VPS_VAULT_PASSWORD_FILE) \
		--check --diff --tags preflight playbooks/vps.yml

vps-check: check-vps-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i $(VPS_INVENTORY) \
		--private-key "$$GH_VPS_SSH_KEY" \
		--vault-password-file $(VPS_VAULT_PASSWORD_FILE) \
		--check --diff playbooks/vps.yml

vps-verify: check-vps-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i $(VPS_INVENTORY) \
		--private-key "$$GH_VPS_SSH_KEY" \
		--vault-password-file $(VPS_VAULT_PASSWORD_FILE) \
		--tags verify playbooks/vps.yml

vps: check-vps-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i $(VPS_INVENTORY) \
		--private-key "$$GH_VPS_SSH_KEY" \
		--vault-password-file $(VPS_VAULT_PASSWORD_FILE) \
		playbooks/vps.yml

vps-tls: check-vps-env
	$(LOCAL_ANSIBLE_PLAYBOOK) -i $(VPS_INVENTORY) \
		--private-key "$$GH_VPS_SSH_KEY" \
		--vault-password-file $(VPS_VAULT_PASSWORD_FILE) \
		playbooks/vps-tls.yml
