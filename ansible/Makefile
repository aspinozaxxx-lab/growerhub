.PHONY: deps ping java-backend nginx mosquitto db pgadmin

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
