#!/bin/bash


export ENVIRONMENT=local


run_command() {
  echo "Run: $1"
  eval $1
  if [ $? -ne 0 ]; then
    echo "Fail: $1"
    exit 1
  fi
}


run_command "sudo ansible-playbook -i environments/\$ENVIRONMENT openwhisk.yml -e mode=clean"
run_command "sudo ansible-playbook -i environments/local setup.yml"
run_command "sudo systemctl stop couchdb"


run_command "cd .. && ./gradlew distDocker && cd ansible"


run_command "sudo ansible-playbook -i environments/\$ENVIRONMENT couchdb.yml"


run_command "sudo ansible-playbook -i environments/\$ENVIRONMENT initdb.yml"


run_command "sudo ansible-playbook -i environments/\$ENVIRONMENT wipe.yml"


run_command "sudo ansible-playbook -i environments/\$ENVIRONMENT openwhisk.yml"

echo "All Works Done！"