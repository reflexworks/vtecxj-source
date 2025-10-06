#!/bin/bash

source ./settings.txt

./setup-config.sh

gcloud container clusters get-credentials $CLUSTER $CLUSTERS_OPTION_NAME $CLUSTERS_OPTION_VALUE --quiet
