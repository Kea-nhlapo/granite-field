#!/usr/bin/env bash
# One-time AWS setup for the Cloud Readiness job's object storage.
#
# Creates a scratch bucket and a user whose only permission is that bucket, then
# prints the access key. CI points the deployment stack at these so the storage
# path is exercised against real S3 before a change reaches main: the storage
# client is lazy, so a wrong endpoint, region, bucket or key is invisible until
# somebody uploads a file, and placeholder credentials cannot catch that.
#
# The bucket holds nothing of value. Objects expire after a day, and a leaked key
# buys an attacker the ability to write rubbish into it and nothing else.
#
# Run once from CloudShell in the deployment account:
#   bash infra/aws/bootstrap-ci-storage.sh
set -euo pipefail

ACCOUNT_ID=698251583462
REGION=af-south-1
BUCKET="trademesh-ci-storage-${ACCOUNT_ID}"
USER_NAME=trademesh-ci-storage

say() { printf '\n== %s\n' "$1"; }

say "Bucket ${BUCKET}"
aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
  --create-bucket-configuration "LocationConstraint=${REGION}" >/dev/null 2>&1 \
  || echo "already exists"

aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

# Nothing here is worth keeping, and an unbounded scratch bucket is a slow leak
# of both storage cost and anything a test happened to upload.
aws s3api put-bucket-lifecycle-configuration --bucket "$BUCKET" \
  --lifecycle-configuration '{"Rules":[{"ID":"expire-ci-objects","Status":"Enabled",
    "Filter":{"Prefix":""},"Expiration":{"Days":1},
    "AbortIncompleteMultipartUpload":{"DaysAfterInitiation":1}}]}'

say "User ${USER_NAME}"
aws iam create-user --user-name "$USER_NAME" >/dev/null 2>&1 || echo "already exists"

cat > /tmp/ci-storage.json <<JSON
{"Version":"2012-10-17","Statement":[
 {"Sid":"UseScratchBucket","Effect":"Allow",
  "Action":["s3:ListBucket","s3:GetBucketLocation"],
  "Resource":"arn:aws:s3:::${BUCKET}"},
 {"Sid":"ReadWriteScratchObjects","Effect":"Allow",
  "Action":["s3:PutObject","s3:GetObject","s3:DeleteObject"],
  "Resource":"arn:aws:s3:::${BUCKET}/*"}]}
JSON

aws iam put-user-policy --user-name "$USER_NAME" \
  --policy-name trademesh-ci-storage --policy-document file:///tmp/ci-storage.json

say "Access key"
# One key at a time: a user carrying two live keys makes it impossible to tell
# which one a leak came from, and rotation stops being a decision anybody makes.
for existing in $(aws iam list-access-keys --user-name "$USER_NAME" \
                    --query 'AccessKeyMetadata[].AccessKeyId' --output text); do
  echo "deleting previous key ${existing}"
  aws iam delete-access-key --user-name "$USER_NAME" --access-key-id "$existing"
done

aws iam create-access-key --user-name "$USER_NAME" \
  --query 'AccessKey.[AccessKeyId,SecretAccessKey]' --output text \
  | while read -r key secret; do
      cat <<SUMMARY

Add these three repository secrets in GitHub
(Settings -> Secrets and variables -> Actions -> New repository secret):

  CI_OBJECT_STORAGE_BUCKET      ${BUCKET}
  CI_OBJECT_STORAGE_ACCESS_KEY  ${key}
  CI_OBJECT_STORAGE_SECRET_KEY  ${secret}

The secret is shown once. Close this shell when the secrets are saved, and do not
paste them anywhere else - CloudShell keeps scrollback.
SUMMARY
    done
