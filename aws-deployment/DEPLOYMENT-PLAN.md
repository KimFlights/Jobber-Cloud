# Deployment Checklist & Tutorial — AWS (Route 1)

Hands-on, build-it-by-hand companion to [`DEPLOYMENT.md`](./DEPLOYMENT.md). The goal is to
learn the **dependency graph** by provisioning each piece yourself, then codify it as IaC in
Phase 9. Build **bottom-up**: nothing that needs a network exists before the network; nothing
that needs a permission works before the IAM role.

**How to use this doc:** each phase has a checklist (tick as you go) followed by a tutorial with
the exact CLI commands and the console path. Commands are AWS CLI v2, Bash-style (works in Git
Bash / WSL on your Windows box). PowerShell users: the `$VARS` become `$env:` or just paste the
literal value.

> ⚠ **Cost reality (DEPLOYMENT.md note 9):** NAT, MSK, OpenSearch, RDS, and DocumentDB bill
> **per hour whether or not you use them**. Do Phase 0 first, run single-AZ / single-NAT, and
> keep the Phase 8 teardown runbook handy. A stack left up over a weekend is real money.

## Conventions — shared shell variables

Set these once per terminal session; every command below reuses them.

```bash
export AWS_REGION=us-east-1
export AWS_PAGER=""                     # stop the CLI opening a pager
export PROJECT=jobber
aws configure set region $AWS_REGION
aws sts get-caller-identity            # sanity check: who am I / is auth working
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account: $ACCOUNT_ID  Region: $AWS_REGION"
```

Keep a scratch file (`jobber-ids.env`) and append every resource ID the commands print
(`vpc-...`, `subnet-...`, `sg-...`). You'll paste them into later phases. This is the manual
stand-in for what Terraform state does automatically.

---

## Phase 0 — Prerequisites (local)

- [ ] AWS account with admin access for yourself
- [ ] **Budget + billing alarm** created (email before surprises)
- [ ] AWS CLI v2 installed and `aws sts get-caller-identity` works
- [ ] One region chosen and set (`$AWS_REGION`)
- [ ] App still builds locally (`docker compose up --build` is green)

### Tutorial

**Install / verify CLI**

```bash
aws --version                          # want aws-cli/2.x
```
If missing, install AWS CLI v2 (Windows: the MSI from the AWS docs), then `aws configure` and
paste an access key from an IAM admin user (not root).

**Budget (do this before anything bills).** Console: **Billing and Cost Management → Budgets →
Create budget → Cost budget → Monthly**, set e.g. `$50`, add an alert at 80% to your email.
CLI version:

```bash
cat > /tmp/budget.json <<EOF
{ "BudgetName": "$PROJECT-monthly", "BudgetLimit": {"Amount":"50","Unit":"USD"},
  "TimeUnit":"MONTHLY", "BudgetType":"COST" }
EOF
cat > /tmp/notify.json <<EOF
[ { "Notification": {"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN",
    "Threshold":80,"ThresholdType":"PERCENTAGE"},
    "Subscribers":[{"SubscriptionType":"EMAIL","Address":"budhilthijm@gmail.com"}] } ]
EOF
aws budgets create-budget --account-id $ACCOUNT_ID \
  --budget file:///tmp/budget.json --notifications-with-subscribers file:///tmp/notify.json
```

**Confirm the app is healthy locally** so any AWS failure later is infra, not code:

```bash
cd /c/Users/budhi/Desktop/repos/Jobber && docker compose up --build -d && docker compose ps
```

---

## Phase 1 — Foundation: networking

*The "free in Compose" layer. Your biggest new-effort area — everything lands inside it.*

- [ ] VPC created (2 AZs, public + private subnets)
- [ ] Internet Gateway attached + public route table
- [ ] One NAT Gateway + private route table (single-AZ for demo)
- [ ] Security groups created (empty rules for now)
- [x] **S3 Gateway endpoint** — free, done via the wizard. *Skip the interface endpoints* (ECR/SSM/Logs/KMS): they bill ~$0.01/hr per AZ, which exceeds the NAT savings at demo scale — add them only for production (a good point to raise in the presentation).

### Tutorial

**Fastest path — console.** **VPC → Create VPC → "VPC and more".** This one wizard screen makes
the VPC, 2 public + 2 private subnets, IGW, route tables, and (tick the box) **1 NAT gateway "In
1 AZ"**. Name tag `jobber`, IPv4 CIDR `10.0.0.0/16`. This is genuinely the sane way — the CLI
equivalent is ~15 calls. Do it in the console, then record the IDs it created.

**CLI path (if you want to feel each object).** Abbreviated — create VPC, subnets, IGW, NAT:

```bash
# VPC
export VPC_ID=$(aws ec2 create-vpc --cidr-block 10.0.0.0/16 \
  --tag-specifications 'ResourceType=vpc,Tags=[{Key=Name,Value=jobber}]' \
  --query Vpc.VpcId --output text)
aws ec2 modify-vpc-attribute --vpc-id $VPC_ID --enable-dns-hostnames

# Two public + two private subnets across 2 AZs
export SUBNET_PUB_A=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block 10.0.1.0/24 \
  --availability-zone ${AWS_REGION}a --query Subnet.SubnetId --output text)
export SUBNET_PUB_B=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block 10.0.2.0/24 \
  --availability-zone ${AWS_REGION}b --query Subnet.SubnetId --output text)
export SUBNET_PRV_A=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block 10.0.11.0/24 \
  --availability-zone ${AWS_REGION}a --query Subnet.SubnetId --output text)
export SUBNET_PRV_B=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block 10.0.12.0/24 \
  --availability-zone ${AWS_REGION}b --query Subnet.SubnetId --output text)

# Internet gateway + public route
export IGW_ID=$(aws ec2 create-internet-gateway --query InternetGateway.InternetGatewayId --output text)
aws ec2 attach-internet-gateway --internet-gateway-id $IGW_ID --vpc-id $VPC_ID
export RT_PUB=$(aws ec2 create-route-table --vpc-id $VPC_ID --query RouteTable.RouteTableId --output text)
aws ec2 create-route --route-table-id $RT_PUB --destination-cidr-block 0.0.0.0/0 --gateway-id $IGW_ID
aws ec2 associate-route-table --route-table-id $RT_PUB --subnet-id $SUBNET_PUB_A
aws ec2 associate-route-table --route-table-id $RT_PUB --subnet-id $SUBNET_PUB_B

# NAT gateway (in public subnet A) — SINGLE NAT for demo cost
export EIP_ALLOC=$(aws ec2 allocate-address --domain vpc --query AllocationId --output text)
export NAT_ID=$(aws ec2 create-nat-gateway --subnet-id $SUBNET_PUB_A --allocation-id $EIP_ALLOC \
  --query NatGateway.NatGatewayId --output text)
aws ec2 wait nat-gateway-available --nat-gateway-ids $NAT_ID   # ~2 min

# Private route table -> NAT
export RT_PRV=$(aws ec2 create-route-table --vpc-id $VPC_ID --query RouteTable.RouteTableId --output text)
aws ec2 create-route --route-table-id $RT_PRV --destination-cidr-block 0.0.0.0/0 --nat-gateway-id $NAT_ID
aws ec2 associate-route-table --route-table-id $RT_PRV --subnet-id $SUBNET_PRV_A
aws ec2 associate-route-table --route-table-id $RT_PRV --subnet-id $SUBNET_PRV_B
```

**Security groups — create empty, fill rules as each service appears.** This "add the rule when
something can't connect" loop is itself the lesson in least-privilege networking.

```bash
mksg () { aws ec2 create-security-group --group-name "$1" --description "$1" --vpc-id $VPC_ID \
  --query GroupId --output text; }
export SG_ALB=$(mksg jobber-alb-sg)
export SG_SVC=$(mksg jobber-service-sg)
export SG_RDS=$(mksg jobber-rds-sg)
export SG_OS=$(mksg jobber-opensearch-sg)
export SG_MSK=$(mksg jobber-msk-sg)
export SG_DOCDB=$(mksg jobber-docdb-sg)

# ALB accepts HTTPS from the internet; services accept traffic only from the ALB SG
aws ec2 authorize-security-group-ingress --group-id $SG_ALB --protocol tcp --port 443 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id $SG_SVC --protocol tcp --port 8080-8084 \
  --source-group $SG_ALB
# Each data-store SG lets in only the service SG, on that store's port:
aws ec2 authorize-security-group-ingress --group-id $SG_RDS   --protocol tcp --port 5432 --source-group $SG_SVC
aws ec2 authorize-security-group-ingress --group-id $SG_OS    --protocol tcp --port 443  --source-group $SG_SVC
aws ec2 authorize-security-group-ingress --group-id $SG_MSK   --protocol tcp --port 9098 --source-group $SG_SVC
aws ec2 authorize-security-group-ingress --group-id $SG_DOCDB --protocol tcp --port 27017 --source-group $SG_SVC
```

**Append every ID to `jobber-ids.env` now** — you'll need `$VPC_ID`, all four subnets, and every
`$SG_*` in Phases 3–6.

---

## Phase 2 — Identity & crypto

*No local equivalent whatsoever. Start roles minimal; expand on the first `AccessDenied`.*

- [ ] KMS key for encryption
- [ ] IAM **execution role** (shared) — ECR pull + logs
- [ ] IAM **task role** per service (minimal, grow later)

### Tutorial

**KMS key.** Console: **KMS → Customer managed keys → Create key → Symmetric**. CLI:

```bash
export KMS_ID=$(aws kms create-key --description "jobber encryption" --query KeyMetadata.KeyId --output text)
aws kms create-alias --alias-name alias/jobber --target-key-id $KMS_ID
```

**Execution role** — identical for every task; lets ECS pull images and ship logs.

```bash
cat > /tmp/ecs-trust.json <<'EOF'
{ "Version":"2012-10-17","Statement":[{"Effect":"Allow",
  "Principal":{"Service":"ecs-tasks.amazonaws.com"},"Action":"sts:AssumeRole"}]}
EOF
aws iam create-role --role-name jobber-ecs-execution --assume-role-policy-document file:///tmp/ecs-trust.json
aws iam attach-role-policy --role-name jobber-ecs-execution \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
export EXEC_ROLE_ARN=$(aws iam get-role --role-name jobber-ecs-execution --query Role.Arn --output text)
```

**Task role, per service.** Start with just the trust policy (no permissions). Add inline
policies the moment a service logs `AccessDenied` — that failure *is* the tutorial for what each
service actually needs (Parameter Store read, MSK IAM connect, etc.).

```bash
for svc in resume search scraper compressor; do
  aws iam create-role --role-name jobber-task-$svc --assume-role-policy-document file:///tmp/ecs-trust.json
done
# Example: grant ResumeService read of its parameters + the KMS key, when it needs it:
cat > /tmp/param-read.json <<EOF
{ "Version":"2012-10-17","Statement":[
  {"Effect":"Allow","Action":["ssm:GetParameter","ssm:GetParametersByPath"],
   "Resource":"arn:aws:ssm:$AWS_REGION:$ACCOUNT_ID:parameter/jobber/*"},
  {"Effect":"Allow","Action":["kms:Decrypt"],"Resource":"*"}]}
EOF
aws iam put-role-policy --role-name jobber-task-resume --policy-name param-read \
  --policy-document file:///tmp/param-read.json
```

> **Learning note:** resist attaching `AdministratorAccess` to task roles to "make it work". The
> whole point of Phase 2 is that you discover each permission by hitting the wall without it.

---

## Phase 3 — Stateful backing services

*Slowest to provision (10–20 min each) — kick these off before compute. Your two biggest risks
live here: RDS bootstrapping (note 1) and the OpenSearch client compatibility (the doc's ⚠).*

- [x] DB subnet group (private subnets) — `jobber-db-subnets`, 2 private subnets
- [x] RDS PostgreSQL up — `jobber-pg`, PG 17.10, `db.t4g.micro`, single-AZ, private, encrypted with
  `alias/jobber`, initial DB `resume`. Endpoint: `jobber-pg.ci7msoa4sdul.us-east-1.rds.amazonaws.com`
- [x] `search` DB created (2026-08-12, via CloudShell-in-VPC — note 1; no Flyway needed, tables auto-created)
- [x] **ES backend decided: self-managed Elasticsearch 9.0.0 on ECS Fargate** (NOT Amazon
  OpenSearch — the ES 9.x client's product-header check rejects OpenSearch; see note below)
- [x] Elasticsearch service up & HEALTHY — `jobber-es` (ES 9.0.0) on ECS cluster `jobber`, Cloud Map
  DNS `elasticsearch.jobber.local:9200`, SG `SG_OS`, private subnets (verified 2026-08-12)
- [x] DocumentDB cluster up — `jobber-docdb` + instance `jobber-docdb-1` (`db.t4g.medium`, 5.0.0),
  private subnets, `SG_DOCDB`, encrypted with `alias/jobber` (verified available 2026-08-12).
  Endpoint: `jobber-docdb.cluster-ci7msoa4sdul.us-east-1.docdb.amazonaws.com`. Master user
  `jobberadmin`, password in Parameter Store `/jobber/docdb/password`. *(Phase 5 RESOLVED: ScraperService
  switched to a single `MONGODB_URI` secret carrying `tls=true&tlsAllowInvalidCertificates=true&retryWrites=false&authSource=admin`
  — see AppConfig/MSK edits section.)*
- [x] ~~**Kafka: self-managed on Fargate**~~ **REVERSED 2026-08-12 → MSK Serverless** (teammate: prefer
  AWS-native). `jobber-msk` Serverless, SASL/IAM auth, bootstrap `boot-uwyp6snu.c2.kafka-serverless.us-east-1.amazonaws.com:9098`.
  The self-managed `jobber-kafka` Fargate service **was removed 2026-08-12** — Scraper, Compressor, and Search
  confirmed running against MSK (`…:9098`, `SASL_SSL`) via their AppConfig profiles; ECS service + Cloud Map
  `kafka` registration + task def `jobber-kafka:1` all deleted. See revised decision below.
- [x] Secrets/config in Parameter Store — `/jobber/openai/api-key`, `/jobber/openai/chat-model`,
  `/jobber/db/password`, `/jobber/docdb/password`, `/jobber/docdb/username`; 3 SecureStrings
  encrypted with `alias/jobber` (verified 2026-08-12)

### Tutorial

**DB subnet group** (shared by RDS and DocumentDB):

```bash
aws rds create-db-subnet-group --db-subnet-group-name jobber-db-subnets \
  --db-subnet-group-description "jobber private" --subnet-ids $SUBNET_PRV_A $SUBNET_PRV_B
```

**RDS PostgreSQL.** Console: **RDS → Create database → PostgreSQL → Dev/Test → db.t3.micro**,
VPC = jobber, subnet group = jobber-db-subnets, SG = `$SG_RDS`, **not publicly accessible**. CLI:

```bash
aws rds create-db-instance --db-instance-identifier jobber-pg \
  --engine postgres --db-instance-class db.t3.micro --allocated-storage 20 \
  --master-username jobber --master-user-password 'CHANGE_ME_strong' \
  --db-subnet-group-name jobber-db-subnets --vpc-security-group-ids $SG_RDS \
  --no-publicly-accessible --storage-encrypted --kms-key-id $KMS_ID
aws rds wait db-instance-available --db-instance-identifier jobber-pg   # ~10 min
export PG_HOST=$(aws rds describe-db-instances --db-instance-identifier jobber-pg \
  --query 'DBInstances[0].Endpoint.Address' --output text)
```

**Note 1 — schema is auto-managed; the only gap is the second database.** *(Corrected after
reading the code.)* Both services create their own **tables** on boot via Hibernate
`ddl-auto: update`, and ResumeService creates the **pgvector extension + vector-store table**
itself via Spring AI `initialize-schema: true`. So **no Flyway/Liquibase is needed** — the
original "add migrations" advice over-stated the problem. What RDS *doesn't* give you: it creates
only **one** initial database (`resume`), whereas the local `postgres-init.sql` created two. So the
single manual step is:
```sql
CREATE DATABASE search;
```
run once against `jobber-pg`. SearchService's `search` DB needs **no** pgvector extension (its job
index lives in OpenSearch; only `user_job_state` is in Postgres).

**How to run it — must be from *inside* the VPC.** The instance is private and its subnets route
`0.0.0.0/0` to the **NAT gateway**, not an Internet Gateway. Toggling "Publicly accessible = Yes"
therefore does **not** work: it assigns a public IP with no inbound route, so the port stays
unreachable from the internet (verified 2026-08-12). This is *correct* — a DB should never be
internet-facing — so seed it from within the VPC. Cleanest path (no bastion, no cleanup):
> **AWS CloudShell → Create environment → VPC.** VPC `vpc-0938b478000c871f4`, a private subnet
> (`subnet-06e3849582a1161cf`), and the **service SG** (`sg-0d9dd3aae079fa44d`) — which the RDS SG
> already admits on 5432, so no new firewall rule is needed. CloudShell's AL2023 already ships
> `psql` (v16, talks to the v17 server fine). Then:
> ```
> psql -h jobber-pg.ci7msoa4sdul.us-east-1.rds.amazonaws.com -U jobber -d resume
> CREATE DATABASE search;
> ```
> (In psql 16, `\l` errors on the PG-17 `daticulocale` column rename — cosmetic; verify instead
> with `SELECT datname FROM pg_database;`.)

> **Status: DONE (2026-08-12).** Both `resume` (RDS initial DB) and `search` now exist on
> `jobber-pg`, created via CloudShell-in-VPC as above.

**Elasticsearch backend — DECISION: self-managed ES 9.0.0 on Fargate (not Amazon OpenSearch).**
*(Decided 2026-08-12.)* SearchService uses `spring-boot-starter-data-elasticsearch` (the ES 9.x
Java client). Since ES 7.14 that client enforces a mandatory **product check** — it requires an
`X-Elastic-Product: Elasticsearch` header on every response. **Amazon OpenSearch forked from ES
7.10, doesn't send that header, and legally can't identify as Elasticsearch**, so the client
throws on connect; OpenSearch's `override_main_response_version` compat mode only spoofs the
version number, not the header, so it doesn't fix it. Building an OpenSearch domain would bill
immediately (~$25/mo) just to prove it can't connect.

Options weighed: **Elastic Cloud trial** (free 14 days, real ES 9.x, but off-AWS) vs
**self-managed ES on Fargate** (real ES 9.x, in-VPC, AWS-native, ~$0.05/hr while up) vs
**Amazon OpenSearch** (bills + likely fails). **Chose self-managed on Fargate** — keeps everything
AWS-native and in-VPC, uses the exact `elasticsearch:9.0.0` image from docker-compose (guaranteed
compatibility), and pulls the ECS/Cloud Map scaffolding forward from Phase 5 (reused by every app
service). Trade-off: single node, ephemeral storage (data resets on task restart — fine for a
demo), no HA/snapshots. `discovery.type=single-node` puts ES in dev mode so it skips the
`vm.max_map_count` bootstrap check that otherwise breaks ES on Fargate.

What was built (all via CLI):
- ECS cluster `jobber` (FARGATE); CloudWatch log group `/ecs/jobber-es`
- Cloud Map private DNS namespace `jobber.local` → service `elasticsearch` →
  **`elasticsearch.jobber.local:9200`** (this is SearchService's `ELASTICSEARCH_URIS` in Phase 5)
- Task def `jobber-es` (1 vCPU / 2 GB, heap 512m, single-node, security off, container health check
  `curl http://localhost:9200`)
- Service `jobber-es` (1 task, private subnets, `SG_OS`); added 9200 ingress on `SG_OS` from `SG_SVC`
  (it previously only allowed 443)

> **Presentation point:** "The ES 9.x client's product-header check rules out Amazon OpenSearch, so
> I ran real Elasticsearch as a self-managed Fargate workload inside the VPC" — shows you tested the
> managed-service constraint rather than assuming drop-in compatibility.

**DocumentDB.** ⚠️ **No micro tier** — unlike RDS, DocumentDB's smallest instance is
**`db.t4g.medium`** (verified via `describe-orderable-db-instance-options`). Console:
**DocumentDB → Create cluster → db.t4g.medium**, SG `$SG_DOCDB`. CLI (note: DocumentDB keeps its
own subnet groups — create one if the console doesn't offer `jobber-db-subnets`):

```bash
aws docdb create-db-cluster --db-cluster-identifier jobber-docdb \
  --engine docdb --master-username jobber --master-user-password 'CHANGE_ME_strong' \
  --db-subnet-group-name jobber-db-subnets --vpc-security-group-ids $SG_DOCDB \
  --storage-encrypted --kms-key-id $KMS_ID
aws docdb create-db-instance --db-instance-identifier jobber-docdb-1 \
  --db-cluster-identifier jobber-docdb --engine docdb --db-instance-class db.t4g.medium
```
Check the Mongo API-gap list for anything ScraperService relies on (e.g. `$out`, some index
types) — DocumentDB is Mongo-*compatible*, not Mongo. **Cheaper alternative:** MongoDB Atlas
**M0 free tier** ($0, external to AWS) — already listed as the fallback in `DEPLOYMENT.md`.

> **Status:** deferred to Phase 5 (chosen 2026-08-12). ScraperService doesn't deploy until then,
> so DocumentDB stays uncreated to keep cost at zero. Cheapest option when created: `db.t4g.medium`
> (or Atlas M0 free).

**Kafka — DECISION (REVISED 2026-08-12): MSK Serverless with IAM auth.**
Originally self-managed Kafka on Fargate (for cost/consistency/zero-app-change). **Reversed** at the
teammate's request to *use AWS-native managed services as much as possible*. Since we were already
editing the apps (AppConfig), the IAM-auth code cost was now marginal, which tipped the balance.

| Option | Cost | Rebuild time | App changes? |
|---|---|---|---|
| ~~Self-managed on Fargate~~ (original) | ~$0.05/hr | ~2 min | none (PLAINTEXT) |
| MSK provisioned | ~$0.09/hr + storage | ~15–30 min | none if PLAINTEXT; manage brokers |
| **MSK Serverless** ✅ | ~$0.75/hr base | ~few min | **yes** — IAM-auth-only → `aws-msk-iam-auth` + SASL config |

**Chose MSK Serverless** — most AWS-native, no brokers to size, provisions in a few minutes. Auth is
SASL/IAM only, so the two Kafka clients (ScraperService producer, JobCompressionService consumer)
gained the `software.amazon.msk:aws-msk-iam-auth:2.3.7` dependency and SASL config. Crucially the
config is **env-driven with local-friendly defaults** — `security.protocol` defaults to `PLAINTEXT`
(sasl.* ignored) so local docker-compose is unchanged; in AWS the task def sets
`KAFKA_SECURITY_PROTOCOL=SASL_SSL` + `KAFKA_BOOTSTRAP=...:9098` and the app authenticates with its
**task-role** credentials (no keys in code). `aws-msk-iam-auth` declares `kafka-clients` as `provided`
scope, so it binds to Boot 4.1's Kafka 4.0 client — verified compatible at `mvn package` build time.

What was built (via CLI): `jobber-msk` Serverless cluster (SASL/IAM, 2 private subnets, `SG_MSK`);
bootstrap `boot-uwyp6snu.c2.kafka-serverless.us-east-1.amazonaws.com:9098`; scoped `kafka-cluster:*`
IAM policy (`jobber-msk-access`) on `jobber-task-scraper` + `jobber-task-compressor`.

**Demo-day handling (chosen):** *rebuild MSK demo morning* via IaC (Serverless provisions fast enough).
MSK Serverless auto-creates topics on first publish given the IAM `CreateTopic` action (granted).

> **Presentation point:** "Kafka is Amazon MSK Serverless with IAM authentication — the services
> authenticate with their ECS task-role credentials via `aws-msk-iam-auth`, no secrets in code. Local
> dev still runs plain Kafka because the security protocol is env-driven, so the same image runs both
> places." Depth point: the IAM lib uses `provided`-scope kafka-clients so it tracks Boot's version.

**AWS AppConfig — DECISION (2026-08-12): AppConfig is the CONFIG SERVER for every service.**
Teammate goal (AWS-native as much as possible) extended to *all* application configuration. Spring Cloud
AWS **4.0.0 GA does support Boot 4** (corrected an earlier wrong note — that was the 3.4.x line), but
even 4.0.0 has **no native AppConfig `spring.config.import`** (only Parameter Store / Secrets Manager /
S3). So the mechanism is AWS's own ECS pattern plus a small bootstrap in each app:

- **AppConfig Agent sidecar** (`public.ecr.aws/aws-appconfig/aws-appconfig-agent:2.x`) in every task —
  polls AppConfig, serves the config on `http://localhost:2772`. No SDK in the app.
- **`AppConfigEnvironmentPostProcessor`** in each service — at startup (before any bean) it HTTP-GETs the
  service's document from the sidecar and injects it as a Spring `PropertySource` (with retry for the
  sidecar race). Registered via `META-INF/spring.factories`. Inserted just below `systemEnvironment` so
  it overrides `application.yaml` while injected env vars still win for `${...}` resolution.
- The IAM `appconfig:StartConfigurationSession` + `GetLatestConfiguration` lands on the **task role**
  (not execution role) — granted to all 4.

**What lives where (the core talking point):**
- **The app** ships an almost-empty `application.yaml` — just `spring.application.name`. The cloud
  coordinates come from one env var, `APPCONFIG_PROFILE` (resume/search/compressor/scraper).
- **AppConfig** holds each service's ENTIRE config — datasource, Elasticsearch, Kafka SASL/IAM, Mongo,
  scraper tuning, actuator — as a per-service freeform YAML profile.
- **Parameter Store** still holds the secrets; the AppConfig documents reference them as `${ENV}`
  placeholders (e.g. `password: ${RESUME_DB_PASSWORD}`), and ECS injects those from Parameter Store as
  env vars that the placeholders resolve against. Scraper's DocDB URI lives in AppConfig with only
  `${DOCDB_PASSWORD}` sourced from Parameter Store.

**Consul removed entirely** (2026-08-12): with config in AppConfig and discovery on Cloud Map DNS,
`spring-cloud-starter-consul-discovery` was dead weight and is dropped from all 4 poms. (Removing it
surfaced an implicit dependency — the scrapers' `RestClient.Builder` had been provided transitively by
spring-cloud-commons — so ScraperService now declares that bean explicitly.)

Built (via CLI): AppConfig app `jobber` (kl76m49) / env `prod` (o82rj6c). Profiles: full-config docs
`resume` (yy7evgb) / `search` (22ajavm) / `compressor` (ihuzcjv) / `scraper` (m44gzco), plus the demo
`features` (flag `verboseLogging`) + `settings` profiles. All deployed with a custom **zero-bake**
strategy `jobber-instant` (`044zm1d`) — the predefined `AppConfig.AllAtOnce` locks the environment for a
10-min bake, blocking back-to-back deploys; zero-bake avoids that and suits demo rebuilds. Each service
also still exposes **`GET /appconfig`** (the `features`/`settings` demo endpoint).

> **Presentation centerpiece:** "Every service is 12-factor externalized to AWS AppConfig — the container
> image carries no environment-specific config, only its name and which AppConfig profile to load. At
> boot an EnvironmentPostProcessor pulls the whole config from the AppConfig sidecar; secrets stay in
> Parameter Store and are referenced by `${...}` placeholders inside the AppConfig document. I can change
> config in AppConfig and roll it (with automatic rollback on a CloudWatch alarm) without touching the
> image. Consul is gone — discovery is Cloud Map DNS."

**Parameter Store — secrets + config (notes 7).** SecureString is KMS-encrypted, IAM-gated, free
tier. Store the OpenAI key **and** the DB passwords here — not just the API key.

```bash
aws ssm put-parameter --name /jobber/openai/api-key --type SecureString --key-id $KMS_ID \
  --value 'sk-...'
aws ssm put-parameter --name /jobber/rds/password   --type SecureString --key-id $KMS_ID \
  --value 'CHANGE_ME_strong'
aws ssm put-parameter --name /jobber/docdb/password  --type SecureString --key-id $KMS_ID \
  --value 'CHANGE_ME_strong'
# non-secret config as plain String:
aws ssm put-parameter --name /jobber/openai/chat-model --type String --value gpt-4o-mini
```

---

## Phase 4 — Discovery + images

- [x] Cloud Map namespace `jobber.local` created (done in Phase 3 for ES/Kafka); app services will
  use **DNS-based** discovery (`<svc>.jobber.local:<port>`), Consul disabled via env var
- [x] ECR repos created + all 4 images pushed `:latest` (2026-08-12) — `jobber/resume` (~259 MB),
  `jobber/scraper` (~193 MB), `jobber/compressor` (~252 MB), `jobber/search` (~230 MB).
  Built from `Jobber-Cloud` copy, `--platform linux/amd64` (matches Fargate X86_64)

### Tutorial

**Cloud Map namespace.** This backs `lb://` discovery, replacing Consul.

```bash
aws servicediscovery create-http-namespace --name jobber.local
# (An HTTP namespace supports the API-based DiscoverInstances lookups you want.)
```
**Critical config detail (DEPLOYMENT.md, repeated):** configure the Spring Cloud AWS Cloud Map
integration for **API-based** discovery (`DiscoverInstances`), **not DNS-based**. The JVM caches
DNS, so DNS-based discovery silently load-balances against a stale instance list. This is the one
subtlety the whole Discovery decision rests on.

**ECR — one repo per service, then build & push.**

```bash
for svc in resumeservice searchservice scraperservice jobcompressionservice; do
  aws ecr create-repository --repository-name jobber/$svc
done
aws ecr get-login-password | docker login --username AWS --password-stdin \
  $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Example for one service (repeat per module):
cd /c/Users/budhi/Desktop/repos/Jobber/ResumeService
docker build -t jobber/resumeservice .
docker tag  jobber/resumeservice $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/jobber/resumeservice:latest
docker push $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/jobber/resumeservice:latest
```
Remember: the local `Gateway` module is **not** built or pushed — AWS API Gateway replaces it
(Phase 6).

---

## Phase 5 — Compute + routing (north-south)

*Deploy ONE service, get it green, then add the next. Don't launch all four at once.*

- [ ] ECS cluster (Fargate)
- [ ] CloudWatch log groups
- [ ] Task definitions (task role, env from Parameter Store, ADOT sidecar, `/actuator/health`)
- [ ] Internal ALB + target groups + listeners
- [ ] ECS services, one at a time, healthy behind the ALB

### Task-def ingredients (READY 2026-08-12 — images pushed, roles + params + AppConfig live)

**Every** app task def gets the **AppConfig agent sidecar** (app reads it at `localhost:2772`):
```json
{ "name": "appconfig-agent",
  "image": "public.ecr.aws/aws-appconfig/aws-appconfig-agent:2.x",
  "essential": false,
  "environment": [{ "name": "AWS_REGION", "value": "us-east-1" }],
  "portMappings": [{ "containerPort": 2772, "protocol": "tcp" }] }
```
Common to all: `SPRING_CLOUD_CONSUL_ENABLED=false`; `executionRoleArn=jobber-ecs-execution`;
`taskRoleArn=jobber-task-<svc>`; awslogs → `/ecs/jobber-<svc>`; health check `/actuator/health`.

| Service | Port | Env vars | `secrets:` (Parameter Store) |
|---|---|---|---|
| **resume** | 8081 | `RESUME_DB_URL=jdbc:postgresql://$PG_HOST:5432/resume`, `RESUME_DB_USER=jobber`, `OPENAI_CHAT_MODEL` | `RESUME_DB_PASSWORD`←`/jobber/db/password`, `OPENAI_API_KEY`←`/jobber/openai/api-key` |
| **search** | 8084 | `ELASTICSEARCH_URIS=http://elasticsearch.jobber.local:9200`, `SEARCH_DB_URL=...:5432/search`, `SEARCH_DB_USER=jobber`, `RESUME_CLIENT_LB=false`, `RESUME_SERVICE_URL=http://resume.jobber.local:8081`, **`KAFKA_BOOTSTRAP=...:9098`**, **`KAFKA_SECURITY_PROTOCOL=SASL_SSL`** (also a Kafka consumer!) | `SEARCH_DB_PASSWORD`←`/jobber/db/password` |
| **compressor** | 8083 | `KAFKA_BOOTSTRAP=boot-uwyp6snu.c2.kafka-serverless.us-east-1.amazonaws.com:9098`, `KAFKA_SECURITY_PROTOCOL=SASL_SSL`, `OPENAI_CHAT_MODEL` | `OPENAI_API_KEY`←`/jobber/openai/api-key` |
| **scraper** | 8082 | `KAFKA_BOOTSTRAP=...:9098`, `KAFKA_SECURITY_PROTOCOL=SASL_SSL` | `MONGODB_URI`←`/jobber/docdb/uri` (create it: `mongodb://jobberadmin:<PW>@$DOCDB_ENDPOINT:27017/scraper?tls=true&tlsAllowInvalidCertificates=true&retryWrites=false&authSource=admin`) |

**Kafka clients = scraper, compressor, AND search** (Resume is not). MSK IAM auth uses the **task role**
(`jobber-msk-access` policy attached to all three: scraper, compressor, search).
AppConfig read uses the **task role** too (`jobber-appconfig-access` on all 4). `tlsAllowInvalidCertificates=true`
is a demo shortcut; the secure alternative is importing the RDS global CA bundle into the image truststore.

### Tutorial

**Cluster + log groups.**

```bash
aws ecs create-cluster --cluster-name jobber
for svc in resume search scraper compressor; do
  aws logs create-log-group --log-group-name /ecs/jobber-$svc
done
```

**Task definition** (ResumeService shown; the shape repeats). Note the health check on
`/actuator/health` (**note 5** — pointing it at `/` fails and ECS restart-loops the task), the
ADOT sidecar for tracing, secrets pulled from Parameter Store, and both roles set.

```bash
cat > /tmp/resume-taskdef.json <<EOF
{
  "family": "jobber-resume",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512", "memory": "1024",
  "executionRoleArn": "$EXEC_ROLE_ARN",
  "taskRoleArn": "arn:aws:iam::$ACCOUNT_ID:role/jobber-task-resume",
  "containerDefinitions": [
    {
      "name": "resume", "image": "$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/jobber/resumeservice:latest",
      "portMappings": [{"containerPort": 8081}],
      "environment": [
        {"name":"RESUME_DB_URL","value":"jdbc:postgresql://$PG_HOST:5432/resume"},
        {"name":"OPENAI_CHAT_MODEL","value":"gpt-4o-mini"}
      ],
      "secrets": [
        {"name":"OPENAI_API_KEY","valueFrom":"arn:aws:ssm:$AWS_REGION:$ACCOUNT_ID:parameter/jobber/openai/api-key"},
        {"name":"RESUME_DB_PASSWORD","valueFrom":"arn:aws:ssm:$AWS_REGION:$ACCOUNT_ID:parameter/jobber/rds/password"}
      ],
      "healthCheck": {
        "command": ["CMD-SHELL","curl -f http://localhost:8081/actuator/health || exit 1"],
        "interval": 30, "timeout": 5, "retries": 3, "startPeriod": 60
      },
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {"awslogs-group":"/ecs/jobber-resume","awslogs-region":"$AWS_REGION","awslogs-stream-prefix":"resume"}
      }
    },
    {
      "name": "adot", "image": "public.ecr.aws/aws-observability/aws-otel-collector:latest",
      "logConfiguration": {"logDriver":"awslogs",
        "options":{"awslogs-group":"/ecs/jobber-resume","awslogs-region":"$AWS_REGION","awslogs-stream-prefix":"adot"}}
    }
  ]
}
EOF
aws ecs register-task-definition --cli-input-json file:///tmp/resume-taskdef.json
```

**Internal ALB + a target group.** API Gateway will reach this ALB via VPC Link in Phase 6, so
it's **internal** (scheme `internal`), not internet-facing.

```bash
export ALB_ARN=$(aws elbv2 create-load-balancer --name jobber-alb --scheme internal \
  --type application --subnets $SUBNET_PRV_A $SUBNET_PRV_B --security-groups $SG_ALB \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)
export TG_RESUME=$(aws elbv2 create-target-group --name jobber-tg-resume \
  --protocol HTTP --port 8081 --vpc-id $VPC_ID --target-type ip \
  --health-check-path /actuator/health --query 'TargetGroups[0].TargetGroupArn' --output text)
aws elbv2 create-listener --load-balancer-arn $ALB_ARN --protocol HTTP --port 80 \
  --default-actions Type=forward,TargetGroupArn=$TG_RESUME
# Add path-based rules per service (/resume/*, /search/*, /api/scrape) as you add each.
```

**ECS service — deploy one, watch it go healthy.**

```bash
aws ecs create-service --cluster jobber --service-name resume \
  --task-definition jobber-resume --desired-count 1 --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_PRV_A,$SUBNET_PRV_B],securityGroups=[$SG_SVC],assignPublicIp=DISABLED}" \
  --load-balancers "targetGroupArn=$TG_RESUME,containerName=resume,containerPort=8081"

# Watch it:
aws ecs describe-services --cluster jobber --services resume \
  --query 'services[0].{running:runningCount,desired:desiredCount,events:events[0].message}'
```
When `runningCount=1` and the target group shows **healthy**, repeat the taskdef+TG+service
block for `search`, `scraper`, `compressor`. Register each service's task role permissions as it
errors (Parameter Store, MSK, etc. — Phase 2's failure-driven loop).

> **Cloud Map registration:** either attach `serviceRegistries` to the ECS service (Service
> Connect / Cloud Map integration) or have Spring register itself via the Cloud Map starter. The
> east-west call is only **Search → Resume**, so Resume must be discoverable before Search's
> circuit-breaker call succeeds.

---

## Phase 6 — Edge (Cognito + API Gateway + HTTPS)

- [x] Cognito User Pool + App Client + hosted-UI domain (2026-08-12) — pool `us-east-1_Gkq8hu7cm`,
  SPA client `qkl4tdmvrg7k3gabljs14pche` (no secret), domain `us-east-1gkq8hu7cm.auth.us-east-1.amazoncognito.com`,
  issuer `https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Gkq8hu7cm`. AppConfig resume/search/scraper
  → v2 with `security.jwt.enabled: true` + issuer-uri; 3 images rebuilt/pushed/redeployed, all healthy.
- [x] API Gateway with Cognito authorizer (2026-08-12) — HTTP API `jobber` (`1vd47m4a2c`),
  invoke `https://1vd47m4a2c.execute-api.us-east-1.amazonaws.com`. JWT authorizer `cognito` (issuer =
  pool, audience = SPA client id). Route `ANY /api/{proxy+}` → HTTP_PROXY integration.
- [x] **VPC Link** to the internal ALB (note 4) — `jobber-link` (`fffpo4`), AVAILABLE, private subnets +
  `jobber-alb-sg`; integration URI = ALB HTTP:80 listener. Verified: no-token/bad-token → **401**,
  no-route → 404.
- [x] `sub` claim mapped into a request header (note 3) — **OBVIATED by the defense-in-depth decision.**
  Services no longer trust an injected header: `JwtSubHeaderFilter` derives `X-User-Sub` from the JWT
  each service validates itself. API Gateway just forwards `Authorization` (HTTP_PROXY default); no
  parameter mapping needed.
- [x] Dedicated route for `/api/scrape` — covered by `ANY /api/{proxy+}` + the ALB's `/api/scrape/*`
  rule; no separate route required.
- [ ] ACM cert + Route 53 record for HTTPS — **optional for demo**; the `execute-api` URL is already
  HTTPS. Add a custom domain only if the presentation needs a branded URL.

> **Phase 6 status (2026-08-12): functionally COMPLETE.** Edge auth boundary verified (401 without a
> valid token). The **200 path (real token → API Gateway authorizer → ALB → service re-validates)** is
> **deferred to Phase 7**: the user will create a Cognito user via the deployed UI's hosted-UI signup and
> exercise the real login flow, rather than minting a synthetic CLI token.

> **DECISION (2026-08-12): defense-in-depth — services validate the Cognito JWT too, not edge-only.**
> Per assignment requirement (stronger than the course's "Cognito at the edge" framing). resume/search/
> scraper gained `spring-boot-starter-oauth2-resource-server` + a `SecurityConfig` that, when
> `security.jwt.enabled=true`, requires a valid Cognito JWT on `/api/**` (validated against the pool
> JWKS **by issuer**, since Cognito access tokens carry `client_id` not `aud`). A `JwtSubHeaderFilter`
> overwrites `X-User-Sub` from the verified token's `sub` so controllers are unchanged and the header is
> no longer spoofable. `permitAll`: `/actuator/**`, `/appconfig`, and (resume only) the internal
> `GET /api/resumes/*/embedding` S2S read. **Toggle is env-driven** (property absent locally →
> permit-all chain → docker-compose stub unchanged). Compressor is excluded (no user HTTP endpoints).
> **This reorders the phase:** Cognito is created first (services need the issuer URI), then AppConfig
> gets `security.jwt.enabled: true` + `spring.security.oauth2.resourceserver.jwt.issuer-uri`, then the
> 3 images are rebuilt/redeployed, then API Gateway. Code done + compiles (Boot 4.1 / Java 25);
> Cognito/AppConfig/redeploy pending.

### Tutorial

**Cognito.** Console is clearer here: **Cognito → Create user pool**, app client (no secret, for
SPA), add a **hosted UI domain**, note the **Pool ID** and **App Client ID**. CLI skeleton:

```bash
export POOL_ID=$(aws cognito-idp create-user-pool --pool-name jobber --query UserPool.Id --output text)
export APP_CLIENT_ID=$(aws cognito-idp create-user-pool-client --user-pool-id $POOL_ID \
  --client-name jobber-spa --no-generate-secret \
  --query UserPoolClient.ClientId --output text)
aws cognito-idp create-user-pool-domain --user-pool-id $POOL_ID --domain jobber-$ACCOUNT_ID
```

**HTTP API + JWT authorizer + VPC Link.** HTTP API (v2) is cheaper/simpler than REST API and has
native JWT authorizers and VPC Link to an ALB.

```bash
export API_ID=$(aws apigatewayv2 create-api --name jobber --protocol-type HTTP --query ApiId --output text)

# JWT authorizer validating Cognito tokens:
export ISSUER=https://cognito-idp.$AWS_REGION.amazonaws.com/$POOL_ID
export AUTHZ_ID=$(aws apigatewayv2 create-authorizer --api-id $API_ID --authorizer-type JWT \
  --name cognito --identity-source '$request.header.Authorization' \
  --jwt-configuration Audience=$APP_CLIENT_ID,Issuer=$ISSUER --query AuthorizerId --output text)

# VPC Link to reach the INTERNAL ALB (note 4 — without this, routing just times out):
export VPCLINK_ID=$(aws apigatewayv2 create-vpc-link --name jobber-link \
  --subnet-ids $SUBNET_PRV_A $SUBNET_PRV_B --security-group-ids $SG_ALB --query VpcLinkId --output text)

# Integration -> ALB listener via the VPC link, then routes:
export LISTENER_ARN=$(aws elbv2 describe-listeners --load-balancer-arn $ALB_ARN \
  --query 'Listeners[0].ListenerArn' --output text)
export INTEG_ID=$(aws apigatewayv2 create-integration --api-id $API_ID \
  --integration-type HTTP_PROXY --integration-uri $LISTENER_ARN \
  --integration-method ANY --connection-type VPC_LINK --connection-id $VPCLINK_ID \
  --payload-format-version 1.0 --query IntegrationId --output text)

aws apigatewayv2 create-route --api-id $API_ID --route-key 'ANY /gw/{proxy+}' \
  --target integrations/$INTEG_ID --authorization-type JWT --authorizer-id $AUTHZ_ID
# note 3: /api/scrape is NOT gateway-routed locally — it needs its own route:
aws apigatewayv2 create-route --api-id $API_ID --route-key 'ANY /scraper/{proxy+}' \
  --target integrations/$INTEG_ID --authorization-type JWT --authorizer-id $AUTHZ_ID
```

**Note 3 — forward the `sub`.** The whole app keys off the `X-User-Sub` header the Spring Gateway
used to inject. With API Gateway, add a **parameter mapping** so the validated JWT's `sub` claim
becomes a request header the services read:

```bash
aws apigatewayv2 update-integration --api-id $API_ID --integration-id $INTEG_ID \
  --request-parameters 'overwrite:header.X-User-Sub=$context.authorizer.claims.sub'
```
Then `aws apigatewayv2 create-stage --api-id $API_ID --stage-name '$default' --auto-deploy`.

**HTTPS.** Request an **ACM certificate** for your domain (must be in the API's region), add the
DNS-validation CNAME in **Route 53**, then create a **custom domain name** in API Gateway and an
A/ALIAS record pointing at it. (For a class demo you can also just present over the default
`execute-api` HTTPS URL and skip the custom domain.)

---

## Phase 7 — Frontend (S3 + CloudFront + Cognito auth)

- [ ] S3 bucket + Origin Access Control
- [ ] CloudFront distribution
- [ ] Cognito auth client added to the UI (the one new frontend lib)
- [ ] Runtime config (Cognito/API URL) injected; CORS enabled if calling API GW directly

### Tutorial

**Add the auth library (note 6 + the frontend table).** The UI currently **stubs** auth with a
hand-set `X-User-Sub` header. Replace it:

```bash
cd /c/Users/budhi/Desktop/repos/Jobber-UI
npm install aws-amplify        # or an OIDC/Cognito client
```
Configure Amplify with the Pool ID / App Client ID / hosted-UI domain, run the Cognito login, and
send `Authorization: Bearer <JWT>` instead of the stub. API Gateway's authorizer validates it and
maps `sub` to the header (Phase 6) — the stub disappears.

**Inject runtime config (note 6).** There's no `.env`/Vite proxy on AWS. Either bake the API
Gateway URL + Cognito IDs at build (`VITE_*` env) or serve a runtime `config.json`. If the SPA
calls API Gateway directly (not through a same-origin CloudFront behavior), enable **CORS** on the
API.

**Build + host.**

```bash
npm run build                                   # -> dist/
aws s3 mb s3://jobber-ui-$ACCOUNT_ID
aws s3 sync dist/ s3://jobber-ui-$ACCOUNT_ID/
```
Console (easiest for OAC): **CloudFront → Create distribution → Origin = the S3 bucket →
"Origin access control settings"**, let it update the bucket policy, set **Default root object**
`index.html`, and add a custom-error-response mapping 403/404 → `/index.html` (SPA routing). Note
the distribution domain — that's your app URL.

---

## Phase 8 — Observability + teardown

- [ ] CloudWatch dashboards/alarms; log groups receiving logs
- [ ] X-Ray traces visible (async Kafka hops need manual propagation, note 8)
- [ ] **Teardown runbook** written and tested

### Tutorial

**Confirm logs + traces.**

```bash
aws logs tail /ecs/jobber-resume --since 10m --follow
```
Open **CloudWatch → X-Ray traces**. HTTP spans (client → API GW → ALB → service, and Search →
Resume) link automatically via ADOT. **Note 8:** the **Scraper → Compressor → Search** Kafka hops
only appear as one connected trace if you propagate trace context through **Kafka headers**. If
observability is graded, either wire that propagation or state the caveat honestly.

**Teardown runbook (note 9 — do this nightly).** Delete in reverse dependency order:

```bash
# 1. Services & compute
aws ecs update-service --cluster jobber --service resume --desired-count 0
aws ecs delete-service  --cluster jobber --service resume --force   # repeat per service
aws ecs delete-cluster --cluster jobber
# 2. Edge
aws apigatewayv2 delete-api --api-id $API_ID
# 3. Stateful (the big billers)
aws elbv2 delete-load-balancer --load-balancer-arn $ALB_ARN
aws rds delete-db-instance --db-instance-identifier jobber-pg --skip-final-snapshot
aws ecs update-service --cluster jobber --service jobber-es --desired-count 0   # stop ES billing
aws ecs delete-service --cluster jobber --service jobber-es --force
aws docdb delete-db-instance --db-instance-identifier jobber-docdb-1
aws docdb delete-db-cluster --db-cluster-identifier jobber-docdb --skip-final-snapshot
aws kafka delete-cluster --cluster-arn "$MSK_ARN"   # MSK Serverless (~$0.75/hr) — the priciest per-hour item
# 4. Networking (NAT + EIP are hourly)
aws ec2 delete-nat-gateway --nat-gateway-id $NAT_ID
aws ec2 release-address --allocation-id $EIP_ALLOC
# ...then subnets, IGW, VPC.
```
The NAT gateway, RDS, DocumentDB, and **MSK Serverless (~$0.75/hr)** are the ones that hurt if left
running. (Elasticsearch is a self-managed Fargate task — `desired-count 0` stops its billing instantly;
MSK has no scale-to-zero, so overnight it must be **deleted** and recreated demo-morning, matching the
chosen "rebuild MSK demo morning" strategy.)

**Cheaper than full teardown: scale/stop instead of delete.** For overnight, you don't have to
destroy everything. Set every ECS service to `desired-count 0` (ES and the five app
services all stop billing in seconds), **delete the MSK Serverless cluster** (no idle-stop option),
and **stop** the databases (`aws rds stop-db-instance
--db-instance-identifier jobber-pg`; `aws docdb stop-db-cluster --db-cluster-identifier
jobber-docdb` — both can stay stopped up to 7 days). Only the NAT gateway keeps ticking (~$1/day).
This avoids the risky cold rebuild while cutting ~90% of compute cost.

---

## Demo-day rebuild strategy — keep the edge, rebuild the backend *(decided 2026-08-12)*

The goal: practice everything manually now, then on presentation morning have a **fast, low-risk**
path to a running system. The key insight is that resources split into two groups by rebuild cost:

**Keep running overnight (slow to rebuild, cheap to leave up):**
- **S3 + CloudFront** — a new CloudFront distribution takes ~15–20 min to deploy globally; storage
  + idle traffic is near-zero cost. Never cold-start this on demo morning.
- **Cognito** — basically free; tearing it down invalidates the user-pool/client IDs the frontend
  embeds and would force re-creating users.
- **API Gateway** — keep the edge stable so the frontend's API URL doesn't change on rebuild.

**Disposable — rebuild via IaC (fast to build, expensive to leave up):**
- ECS services (ES, Kafka, five app services), RDS, DocumentDB, ALB.

**The coupling to respect:** the frontend is wired to (1) the **API URL** and (2) the **Cognito
IDs**. Keep API Gateway + Cognito stable and you can rebuild the compute/data behind them with no
frontend change. Otherwise, budget a 1–2 min frontend re-config (S3 sync + CloudFront
invalidation) after each backend rebuild.

**Timing:** don't rebuild from zero at 9:00 for a 9:45 slot — some pieces need 10–15 min each.
Either rebuild early (~7:30 AM) and leave it up, or use the scale-to-zero/stop approach above so
there's nothing to rebuild. Do one full IaC deploy as a **rehearsal** a day or two before.

**Rebuild seam for IaC:** design the Terraform/CDK so the *edge + identity* stack (S3, CloudFront,
Cognito, API Gateway) is a **separate stack** from the *compute + data* stack — so you can
`destroy`/`apply` the backend without touching the frontend. `former2` can generate a first-draft
template from the resources already built by hand.

---

## Phase 9 — Codify as IaC

*Now that it works by hand, translate it — you'll understand every resource because you built it.*

- [ ] Choose CDK or Terraform
- [ ] Reproduce Phases 1–8 as code
- [ ] `destroy` and re-provision from code (the real payoff)
- [ ] CI/CD: build → push to ECR → deploy ECS

### Tutorial

Pick one:
- **CDK** (TypeScript/Java) — closest to app code; good if you want typed constructs and you're
  already a Java shop. `cdk init app`, model the VPC/ECS/ALB with L2 constructs, `cdk deploy`.
- **Terraform** — provider-agnostic, huge module ecosystem (`terraform-aws-modules/vpc`,
  `.../ecs`). `terraform init && plan && apply`.

Work phase by phase: VPC module first, confirm `plan` matches what you built by hand, `apply`,
then layer on IAM, data stores, ECS. The moment you can `destroy` and `apply` to get an identical
stack back is when the manual effort pays off — and it becomes your nightly cost-control switch.

**Then CI/CD:** a pipeline (GitHub Actions / CodePipeline) that on push builds each service,
pushes to ECR, and runs `aws ecs update-service --force-new-deployment`. That replaces
`docker compose up --build` as your source of truth.

---

## Quick reference — build order & why

| Phase | Builds | Depends on | Why this order |
|---|---|---|---|
| 0 | Budget, CLI | — | Cost guardrail before anything bills |
| 1 | VPC, subnets, NAT, SGs | 0 | Everything lands inside the network |
| 2 | KMS, IAM roles | 1 | Permissions must exist before compute uses them |
| 3 | RDS, ES-on-Fargate, DocDB, MSK, Params | 1, 2 | Slowest to boot; biggest risks (notes 1, ⚠) |
| 4 | Cloud Map, ECR images | 1, 2 | Registry + images ready for tasks |
| 5 | ECS, ALB, services | 1–4 | Compute needs net, roles, images, stores |
| 6 | Cognito, API GW, VPC Link | 5 | Edge points at healthy internal ALB |
| 7 | S3, CloudFront, UI auth | 6 | Frontend needs the API URL + Cognito IDs |
| 8 | CloudWatch, X-Ray, teardown | 5–7 | Observe the running system; tear it down |
| 9 | CDK/Terraform, CI/CD | all | Codify what you now understand |

---

## Appendix — Architecture Q&A / presentation talking points *(2026-08-12)*

*The migration's guiding principle: **the service images stay plain and portable; everything
AWS-specific lives in the ECS task definitions and at the edge.** These are the questions a grader
is likely to ask, with the honest answers.*

**Q: Do the services need AWS SDK libraries / IAM credentials in the code?**
No. The services call Postgres, Kafka, Elasticsearch, MongoDB, and OpenAI — **none are AWS APIs** —
so they need no AWS SDK and never touch credentials. The **task role** attaches to the task and ECS
auto-injects rotating credentials at a metadata endpoint, but the app never uses them. The AWS work
is done by the **execution role** (pull image, read Parameter Store, write logs), not the app.

**Q: How is Parameter Store read without an AWS library? (and why not Spring Cloud AWS?)**
ECS reads the SSM SecureStrings using the **execution role** and injects them as plain env vars via
the task def's `secrets:` block; the app reads `OPENAI_API_KEY` etc. from the environment like it
already does. The "cloud-native" alternative — **Spring Cloud AWS** (`spring-cloud-aws-starter-
parameter-store`, app reads SSM itself via `spring.config.import=aws-parameterstore:/jobber/`) — was
**evaluated and rejected**: its latest release (`3.4.0`) targets **Spring Boot 3.x**, but this app
is on **Spring Boot 4.1 / Framework 7 / Java 25**, which it doesn't support yet. Key insight: had we
used it, the app *would* call `ssm:GetParameters` + `kms:Decrypt`, so **that** is when the task role
would need IAM permissions. Keeping injection in ECS is why the services stay AWS-agnostic.
*(Trade-off table: app-reads-SSM = config refresh + hierarchy but AWS-coupled + needs task IAM;
ECS-injects = portable + zero app IAM but static at task start.)*

**Q: Where does Cognito go — in each service?**
No — at the **edge**. The code already commits to this: `AuthenticationStubFilter` is a deliberate
pass-through placeholder, and controllers read `@RequestHeader("X-User-Sub")` — services **trust the
header, never see a JWT**. On AWS, a **Cognito JWT authorizer on API Gateway** (or ALB
`authenticate-cognito`) validates the token and injects the `sub` as `X-User-Sub`. No backend code
change; Cognito is Phase 6/7 infra + the frontend login.

**Q: How do the apps get service ports from Cloud Map?**
They don't — Cloud Map **A records return only the IP**. The **port is a constant we supply in the
URL env var** (`http://resume.jobber.local:8081`, `...:9200` for ES, `...:9092` for Kafka). We
control each port via `SERVER_PORT` + the task-def `containerPort`.

**Q: Logging vs tracing — and how does X-Ray work with no logging code?**
Two different things. **Logs** → CloudWatch, automatic via the `awslogs` driver (Spring logs to
stdout; no code). **Tracing (X-Ray)** = spans across services, *not* logs. It's picked up with **no
code** via the **OpenTelemetry/ADOT Java agent** (`-javaagent` through `JAVA_TOOL_OPTIONS`), which
rewrites the bytecode of the libraries you already use (Spring MVC, HTTP client, JDBC, Kafka client)
to emit spans and propagate `traceparent` — an **ADOT Collector sidecar** forwards them to X-Ray.
With the agent on every service, **both HTTP and Kafka hops link automatically**; only *custom*
business spans need code. *(Note: our Elasticsearch is the app's **search index**, not an ELK
logging stack — logs never go there.)*

**Q: The Consul dependency is now dead weight — shouldn't it be removed?**
For the demo it's **neutralized by config** (`SPRING_CLOUD_CONSUL_ENABLED=false`); the one
service-to-service call uses the app's built-in `RESUME_CLIENT_LB=false` + `RESUME_SERVICE_URL`
override → Cloud Map DNS. Zero code change, reversible. A proper hardening pass **would remove**
`spring-cloud-starter-consul-discovery` + the `spring.cloud.consul` block. Good distinction to
draw: **migration (config) ≠ cloud-native refactor (code)**.

> **Repo hygiene:** all AWS-oriented code changes (if any arise, e.g., a DocumentDB
> `MongoClientSettingsBuilderCustomizer` for `retryWrites=false`) go in the **`Jobber-Cloud`** copy;
> the original **`Jobber`** stays read-only.
