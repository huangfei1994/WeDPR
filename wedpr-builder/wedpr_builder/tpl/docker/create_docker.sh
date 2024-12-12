#!/bin/bash
SHELL_FOLDER=$(cd $(dirname $0);pwd)

LOG_INFO() {
    content=${1}
    echo -e "\033[32m[INFO] ${content}\033[0m"
}

cd ${SHELL_FOLDER}

LOG_INFO "Ready to create docker: ${WEDPR_DOCKER_NAME}, Please enter 'Y' to confirm"
read -r confirm
if [ ${confirm} != "Y" ]
if [[ "${confirm}" == "Y" || "${confirm}" == "y" ]]; then
  LOG_INFO "Begin to create docker: ${WEDPR_DOCKER_NAME}"
  docker run -d --restart always --net host -v ${SHELL_FOLDER}/${WEDPR_CONFIG_DIR}:${DOCKER_CONF_PATH} -v ${SHELL_FOLDER}/${WEDPR_LOG_DIR}:${DOCKER_LOG_PATH} ${EXTENDED_MOUNT_CONF} --name ${WEDPR_DOCKER_NAME} ${WEDPR_IMAGE_DESC} ${WEDPR_DOCKER_EXPORSE_PORT_LIST}
  LOG_INFO "Create docker: ${WEDPR_DOCKER_NAME} success"
else
  LOG_INFO "Exit without create docker ${WEDPR_DOCKER_NAME}"
fi


