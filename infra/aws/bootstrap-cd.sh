#!/usr/bin/env bash
# One-time AWS setup for continuous deployment.
#
# Creates the ECR repository the pipeline pushes to, the GitHub OIDC trust so
# Actions can assume a role without a stored access key, and that role's
# permissions. Safe to re-run: every step tolerates the resource already existing.
#
# Run once from CloudShell in the deployment account:
#   bash infra/aws/bootstrap-cd.sh
set -euo pipefail

ACCOUNT_ID=698251583462
REGION=af-south-1
GITHUB_REPO=Kea-nhlapo/granite-field
INSTANCE_ID=i-0f2df9d19fea4b986
SITE_BUCKET=trademesh-site-698251583462
DISTRIBUTION_ID=E1VKQQWO5B99P
ECR_REPO=trademesh-backend
ROLE_NAME=trademesh-github-deploy
INSTANCE_ROLE=trademesh-ec2

export AWS_PAGER=""
export AWS_DEFAULT_REGION="$REGION"

say() { printf '\n== %s\n' "$1"; }

say "ECR repository"
aws ecr create-repository \
  --repository-name "$ECR_REPO" \
  --image-scanning-configuration scanOnPush=true \
  --query 'repository.repositoryUri' --output text 2>/dev/null \
  || aws ecr describe-repositories --repository-names "$ECR_REPO" \
       --query 'repositories[0].repositoryUri' --output text

say "Keep only the last 15 images"
aws ecr put-lifecycle-policy --repository-name "$ECR_REPO" --lifecycle-policy-text '{
  "rules":[{"rulePriority":1,"description":"keep last 15",
  "selection":{"tagStatus":"any","countType":"imageCountMoreThan","countNumber":15},
  "action":{"type":"expire"}}]}' >/dev/null

say "GitHub OIDC provider"
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 \
  --query 'OpenIDConnectProviderArn' --output text 2>/dev/null \
  || echo "already exists"

say "Deploy role trust policy"
# Restricted to this repository's main branch. A fork, a pull request, or any
# other branch cannot assume this role.
#
# The subject is NOT the documented repo:OWNER/REPO form. This repository has
# customised OIDC subject claims enabled, so GitHub sends owner and repository
# IDs inside it: repo:Kea-nhlapo@<ownerid>/granite-field@<repoid>:ref:... The
# IDs are wildcarded so a misread digit cannot break the trust; owner, repo and
# branch are all still pinned.
#
# sts:TagSession is required as well as sts:AssumeRoleWithWebIdentity:
# aws-actions/configure-aws-credentials attaches session tags by default, and
# without TagSession allowed the whole call is denied with a message that only
# mentions AssumeRoleWithWebIdentity.
cat > /tmp/trust.json <<JSON
{"Version":"2012-10-17","Statement":[{
  "Effect":"Allow",
  "Principal":{"Federated":"arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"},
  "Action":["sts:AssumeRoleWithWebIdentity","sts:TagSession"],
  "Condition":{
    "StringEquals":{"token.actions.githubusercontent.com:aud":"sts.amazonaws.com"},
    "StringLike":{"token.actions.githubusercontent.com:sub":"repo:Kea-nhlapo@*/granite-field@*:ref:refs/heads/main"}}}]}
JSON

aws iam create-role --role-name "$ROLE_NAME" \
  --assume-role-policy-document file:///tmp/trust.json \
  --description "GitHub Actions deploys to the TradeMesh cloud environment" \
  --query 'Role.Arn' --output text 2>/dev/null \
  || aws iam update-assume-role-policy --role-name "$ROLE_NAME" \
       --policy-document file:///tmp/trust.json

say "Deploy role permissions"
# Scoped to exactly the resources the pipeline touches: this ECR repository,
# this instance, this bucket, this distribution. Nothing else in the account.
cat > /tmp/perms.json <<JSON
{"Version":"2012-10-17","Statement":[
 {"Sid":"EcrLogin","Effect":"Allow","Action":"ecr:GetAuthorizationToken","Resource":"*"},
 {"Sid":"EcrPush","Effect":"Allow",
  "Action":["ecr:BatchCheckLayerAvailability","ecr:InitiateLayerUpload","ecr:UploadLayerPart",
            "ecr:CompleteLayerUpload","ecr:PutImage","ecr:BatchGetImage","ecr:GetDownloadUrlForLayer",
            "ecr:DescribeImages"],
  "Resource":"arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/${ECR_REPO}"},
 {"Sid":"RunDeployOnInstance","Effect":"Allow","Action":"ssm:SendCommand",
  "Resource":["arn:aws:ec2:${REGION}:${ACCOUNT_ID}:instance/${INSTANCE_ID}",
              "arn:aws:ssm:${REGION}::document/AWS-RunShellScript"]},
 {"Sid":"ReadCommandResult","Effect":"Allow",
  "Action":["ssm:GetCommandInvocation","ssm:ListCommandInvocations"],"Resource":"*"},
 {"Sid":"PublishFrontend","Effect":"Allow",
  "Action":["s3:PutObject","s3:DeleteObject","s3:ListBucket","s3:GetObject"],
  "Resource":["arn:aws:s3:::${SITE_BUCKET}","arn:aws:s3:::${SITE_BUCKET}/*"]},
 {"Sid":"RefreshCdn","Effect":"Allow","Action":"cloudfront:CreateInvalidation",
  "Resource":"arn:aws:cloudfront::${ACCOUNT_ID}:distribution/${DISTRIBUTION_ID}"}]}
JSON

aws iam put-role-policy --role-name "$ROLE_NAME" \
  --policy-name trademesh-deploy --policy-document file:///tmp/perms.json

say "Let the instance pull from ECR"
aws iam attach-role-policy --role-name "$INSTANCE_ROLE" \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

rm -f /tmp/trust.json /tmp/perms.json

cat <<SUMMARY

Done. Add this as the repository variable AWS_DEPLOY_ROLE in GitHub:

  arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}

SUMMARY
