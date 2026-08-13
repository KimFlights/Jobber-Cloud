"""
Generates the AWS deployment architecture diagram (aws-architecture.png) for the
Jobber-Cloud stack, using the official AWS service icons.

Run:  python aws_architecture.py
Deps: pip install diagrams   +   Graphviz `dot` on PATH.

Style mirrors an AWS reference architecture: an "AWS Cloud" frame, dashed component
groupings, a green VPC with public/private subnets, and numbered request flows.
"""

from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.client import Users, Client
from diagrams.aws.network import CloudFront, APIGateway, ALB, NATGateway, CloudMap
from diagrams.aws.storage import S3
from diagrams.aws.security import Cognito
from diagrams.aws.compute import Fargate, Lambda, EC2ContainerRegistry
from diagrams.aws.database import Aurora, RDS, DocumentDB
from diagrams.aws.analytics import ElasticsearchService, ManagedStreamingForKafka
from diagrams.aws.management import (
    SystemsManagerParameterStore,
    SystemsManagerAppConfig,
    Cloudwatch,
)
from diagrams.aws.devtools import XRay

graph_attr = {
    "fontsize": "20",
    "labelloc": "t",
    "pad": "0.6",
    "splines": "spline",
    "nodesep": "0.6",
    "ranksep": "1.1",
    "bgcolor": "white",
}

with Diagram(
    "Jobber-Cloud — AWS Deployment Architecture",
    filename="aws-architecture",
    show=False,
    direction="LR",
    graph_attr=graph_attr,
    outformat="png",
):
    users = Users("Users\n(Browser)")
    openai = Client("OpenAI API\n(external · Bedrock later)")

    with Cluster("AWS Cloud"):

        with Cluster("Edge & Identity (regional)"):
            cf = CloudFront("Amazon CloudFront\n(CDN)")
            spa = S3("Amazon S3\nSPA assets (OAC)")
            cognito = Cognito("Amazon Cognito\nUser Pool · JWT")
            apigw = APIGateway("Amazon API Gateway\nCognito authorizer + VPC Link")

        with Cluster("Shared services & Observability (regional)"):
            param = SystemsManagerParameterStore("Parameter Store\nconfig + SecureString")
            appconf = SystemsManagerAppConfig("AppConfig\nfeature flags")
            cw = Cloudwatch("CloudWatch\nlogs + metrics")
            xray = XRay("X-Ray (via ADOT)\ntracing")
            ecr = EC2ContainerRegistry("Amazon ECR\ncontainer images")

        with Cluster("VPC (10.0.0.0/16)", graph_attr={"bgcolor": "#e8f6e8", "pencolor": "#2e8b2e"}):

            with Cluster("Public subnets (AZ-a / AZ-b)"):
                alb = ALB("Application\nLoad Balancer")
                nat = NATGateway("NAT Gateway")

            with Cluster("Private subnets (AZ-a / AZ-b)", graph_attr={"bgcolor": "#e6f2fb"}):

                with Cluster("ECS Fargate tasks"):
                    resume = Fargate("ResumeService")
                    search = Fargate("SearchService")
                    scraper = Fargate("ScraperService")
                    compressor = Lambda("JobCompression\n(Lambda planned)")

                cmap = CloudMap("Cloud Map\nAPI-based discovery")

                with Cluster("Managed data & messaging"):
                    rdsr = Aurora("RDS/Aurora PostgreSQL\n+ pgvector · resume")
                    rdss = RDS("RDS PostgreSQL\nsaved jobs / ratings")
                    oss = ElasticsearchService("OpenSearch Service\njob index")
                    docdb = DocumentDB("DocumentDB\nscraped jobs")
                    msk = ManagedStreamingForKafka("Amazon MSK\nraw → enriched")

        # --- North-south request flow (numbered) ---
        users >> Edge(label="1  load SPA (HTTPS)") >> cf
        cf >> Edge(label="2  origin", style="dashed") >> spa
        users >> Edge(label="3  login → JWT") >> cognito
        users >> Edge(label="4  API call + Bearer JWT") >> apigw
        apigw >> Edge(label="5  VPC Link") >> alb
        alb >> Edge(label="6  route") >> resume
        alb >> search
        alb >> scraper

        # --- Service -> data (in-VPC) ---
        resume >> rdsr
        search >> oss
        search >> rdss
        scraper >> docdb

        # --- Discovery + the one east-west sync call ---
        resume >> Edge(style="dotted", label="register") >> cmap
        search >> Edge(style="dotted") >> cmap
        scraper >> Edge(style="dotted") >> cmap
        search >> Edge(label="7  RestClient @LB\n+ Resilience4j", style="dotted") >> cmap
        cmap >> Edge(style="dotted", label="resolve") >> resume

        # --- Async pipeline via MSK ---
        scraper >> Edge(label="8  raw jobs") >> msk
        msk >> Edge(label="consume") >> compressor
        compressor >> Edge(label="enriched") >> msk
        msk >> Edge(label="index") >> search

        # --- Egress to external AI via NAT ---
        resume >> Edge(style="dashed") >> nat
        compressor >> Edge(style="dashed") >> nat
        nat >> Edge(label="9  egress") >> openai

        # --- Cross-cutting (drawn from Resume for readability; all tasks attach) ---
        resume >> Edge(style="dashed", color="gray") >> param
        resume >> Edge(style="dashed", color="gray") >> appconf
        resume >> Edge(style="dashed", color="gray") >> cw
        resume >> Edge(style="dashed", color="gray") >> xray
        ecr >> Edge(style="dashed", color="gray", label="pull image") >> resume
