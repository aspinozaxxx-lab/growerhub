.PHONY: deps ping site

deps:
ansible-galaxy install -r requirements.yml --force

ping:
ansible -m ping all

site:
ansible-playbook playbooks/deploy-agent.yml
