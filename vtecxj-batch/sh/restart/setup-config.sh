#!/bin/bash

source ./settings.txt

gcloud config set project $GCP_PROJECT_ID
gcloud config set container/cluster $CLUSTER
