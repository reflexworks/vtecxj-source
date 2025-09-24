#!/bin/bash

source ./settings.txt

#echo "zone: " $ZONE
#echo "cluster: " $CLUSTER
#echo "projectId: " $GCP_PROJECT_ID

gcloud config set project $GCP_PROJECT_ID
#gcloud config set compute/region $REGION
#gcloud config set compute/zone $ZONE
gcloud config set container/cluster $CLUSTER

#gcloud config list
