#!/bin/bash

source ./settings.txt

./setup-config.sh

gcloud container clusters get-credentials $CLUSTER --region $REGION --quiet
