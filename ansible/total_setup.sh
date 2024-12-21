#!/bin/bash

# Set environment to local
export ENVIRONMENT=local

# Function to check the success of the last command
check_success() {
    if [ $? -ne 0 ]; then
        echo "Error: The previous command failed. Exiting."
        exit 1
    fi
}

# Run Ansible playbooks in sequence
echo "Running setup.yml"
sudo ansible-playbook -i environments/local setup.yml
check_success

echo "Running prereq.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT prereq.yml
check_success

# Stop services
echo "Stopping CouchDB"
sudo systemctl stop couchdb
check_success

# Go back to previous directory
cd ..
check_success

# Build Docker image
echo "Building Docker image"
./gradlew distDocker
check_success

# Go back to ansible directory
cd ansible
check_success

# Run Ansible playbooks
echo "Running couchdb.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT couchdb.yml
check_success

echo "Running initdb.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT initdb.yml
check_success

echo "Running wipe.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT wipe.yml
check_success

echo "Running elasticsearch.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT elasticsearch.yml
check_success

echo "Running openwhisk.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT openwhisk.yml
check_success

echo "Running postdeploy.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT postdeploy.yml
check_success

echo "Running apigateway.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT apigateway.yml
check_success

echo "Running routemgmt.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT routemgmt.yml
check_success

echo "Deployment complete!"