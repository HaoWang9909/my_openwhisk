#!/bin/bash

# Set environment to local
export ENVIRONMENT=local

# Function to check the success of the last command
check_success() {
    if [ $? -ne 0 ]; then
        echo -e "\033[1;31m[✗] Error: The previous command failed. Exiting.\033[0m"
        exit 1
    else
        echo -e "\033[1;32m[✓] Success!\033[0m"
    fi
}

# Function to show a progress bar
progress_bar() {
    local duration=$1
    local completed=0
    echo -ne "["
    while [ $completed -lt $duration ]; do
        echo -ne "#"
        sleep 0.1
        ((completed++))
    done
    echo -ne "]\n"
}

# Function to log actions with a cool banner
log_action() {
    local action=$1
    echo -e "\033[1;34m"
    echo "==============================="
    echo "   $action"
    echo "==============================="
    echo -e "\033[0m"
}

# Start script
log_action "Starting Deployment Process"

# Run Ansible playbooks in sequence
log_action "Running controller.yml with mode=clean"
sudo ansible-playbook -i environments/$ENVIRONMENT controller.yml -e mode=clean
check_success
progress_bar 10

log_action "Running setup.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT setup.yml
check_success
progress_bar 10

log_action "Running prereq.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT prereq.yml
check_success
progress_bar 10

# Stop services
log_action "Stopping CouchDB"
sudo systemctl stop couchdb
check_success
progress_bar 5

# Clean Gradle build
log_action "Cleaning Gradle build"
cd ..
./gradlew clean
check_success
progress_bar 10

# Build Docker image
log_action "Building Docker image"
./gradlew distDocker
check_success
progress_bar 15

# Return to ansible directory
cd ansible
check_success

# Run Ansible playbooks
log_action "Running couchdb.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT couchdb.yml
check_success
progress_bar 10

log_action "Running initdb.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT initdb.yml
check_success
progress_bar 10

log_action "Running wipe.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT wipe.yml
check_success
progress_bar 10

log_action "Running elasticsearch.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT elasticsearch.yml
check_success
progress_bar 10

log_action "Running openwhisk.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT openwhisk.yml
check_success
progress_bar 15

log_action "Running postdeploy.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT postdeploy.yml
check_success
progress_bar 10

log_action "Running apigateway.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT apigateway.yml
check_success
progress_bar 10

log_action "Running routemgmt.yml"
sudo ansible-playbook -i environments/$ENVIRONMENT routemgmt.yml
check_success
progress_bar 10

log_action "Deployment Complete!"
echo -e "\033[1;32mAll tasks completed successfully. Your environment is ready!\033[0m"