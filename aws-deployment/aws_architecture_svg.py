"""
Hand-placed AWS deployment diagram for Jobber-Cloud (fixed coordinates — no auto-layout).

Why this instead of Graphviz: the `dot` engine ranks a long service chain and then staggers
the cluster boxes to minimise edge crossings, which produced a diagonal "staircase". Here every
icon and box is placed at an explicit coordinate, so the layout is exactly left-to-right and
never shifts between renders.

Output:
  * aws-architecture.svg  — self-contained (official AWS icons embedded as base64 data URIs)
  * aws-architecture.png  — rasterised from the SVG via headless Chrome (portable embed target)

Run:  python aws_architecture_svg.py
"""

import base64
import os
import subprocess
import sys
from html import escape

# --- official AWS icons bundled with the `diagrams` pip package -------------------------------
ICON_BASE = os.path.join(
    os.path.dirname(__import__("diagrams").__file__).replace("\\diagrams", ""),
    "resources", "aws",
)
ICON_BASE = os.path.join(os.path.dirname(sys.executable), "Lib", "site-packages", "resources", "aws")

ICONS = {
    "cloudfront": "network/cloudfront.png",
    "s3": "storage/simple-storage-service-s3.png",
    "cognito": "security/cognito.png",
    "apigw": "network/api-gateway.png",
    "alb": "network/elb-application-load-balancer.png",
    "nat": "network/nat-gateway.png",
    "fargate": "compute/fargate.png",
    "lambda": "compute/lambda.png",
    "cloudmap": "network/cloud-map.png",
    "aurora": "database/aurora.png",
    "rds": "database/rds.png",
    "opensearch": "analytics/amazon-opensearch-service.png",
    "documentdb": "database/documentdb-mongodb-compatibility.png",
    "msk": "analytics/managed-streaming-for-kafka.png",
    "param": "management/systems-manager-parameter-store.png",
    "appconfig": "management/systems-manager-app-config.png",
    "cloudwatch": "management/cloudwatch.png",
    "xray": "devtools/x-ray.png",
    "ecr": "compute/ec2-container-registry.png",
}

_cache = {}


def data_uri(key):
    if key not in _cache:
        with open(os.path.join(ICON_BASE, ICONS[key]), "rb") as fh:
            _cache[key] = "data:image/png;base64," + base64.b64encode(fh.read()).decode()
    return _cache[key]


# --- SVG primitives ---------------------------------------------------------------------------
W, H = 1820, 1080
parts = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
    f'viewBox="0 0 {W} {H}" font-family="Segoe UI, Arial, sans-serif">',
    f'<rect x="0" y="0" width="{W}" height="{H}" fill="#ffffff"/>',
    # arrowheads
    '<defs>'
    '<marker id="arr" markerWidth="9" markerHeight="9" refX="7" refY="3" orient="auto">'
    '<path d="M0,0 L7,3 L0,6 Z" fill="#5b6472"/></marker>'
    '<marker id="arrg" markerWidth="9" markerHeight="9" refX="7" refY="3" orient="auto">'
    '<path d="M0,0 L7,3 L0,6 Z" fill="#9aa2ad"/></marker>'
    '</defs>',
]


def box(x, y, w, h, label, stroke, fill="none", dash=None, lblcolor=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    parts.append(
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" '
        f'fill="{fill}" stroke="{stroke}" stroke-width="2"{d}/>'
    )
    if label:
        parts.append(
            f'<text x="{x+14}" y="{y+22}" font-size="15" font-weight="600" '
            f'fill="{lblcolor or stroke}">{escape(label)}</text>'
        )


def icon(cx, cy, key, lines, size=54):
    parts.append(
        f'<image href="{data_uri(key)}" x="{cx-size/2}" y="{cy-size/2}" '
        f'width="{size}" height="{size}"/>'
    )
    ty = cy + size / 2 + 15
    for i, ln in enumerate(lines):
        parts.append(
            f'<text x="{cx}" y="{ty + i*13}" font-size="11.5" text-anchor="middle" '
            f'fill="#232f3e">{escape(ln)}</text>'
        )


def arrow(x1, y1, x2, y2, dash=None, gray=False):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    color = "#9aa2ad" if gray else "#5b6472"
    head = "arrg" if gray else "arr"
    parts.append(
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
        f'stroke-width="1.6"{d} marker-end="url(#{head})"/>'
    )


def elbow(x1, y1, x2, y2, dash=None, gray=False):
    """Orthogonal 2-segment connector (horizontal then vertical)."""
    d = f' stroke-dasharray="{dash}"' if dash else ""
    color = "#9aa2ad" if gray else "#5b6472"
    head = "arrg" if gray else "arr"
    parts.append(
        f'<path d="M{x1},{y1} L{x2},{y1} L{x2},{y2}" fill="none" stroke="{color}" '
        f'stroke-width="1.6"{d} marker-end="url(#{head})"/>'
    )


def badge(x, y, n):
    parts.append(f'<circle cx="{x}" cy="{y}" r="11" fill="#16202e"/>')
    parts.append(
        f'<text x="{x}" y="{y+4}" font-size="12" font-weight="700" '
        f'text-anchor="middle" fill="#ffffff">{n}</text>'
    )


def label(x, y, text, size=11, color="#4a5260", weight="400", anchor="middle"):
    parts.append(
        f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" '
        f'text-anchor="{anchor}" fill="{color}">{escape(text)}</text>'
    )


# ============================================================================================
# LAYOUT  (all coordinates fixed)
# ============================================================================================
label(W / 2, 34, "Jobber-Cloud — AWS Deployment Architecture", size=20, color="#16202e", weight="700")

# --- external actors (left column, outside the AWS Cloud) ---
# Users glyph
parts.append('<g transform="translate(70,470)">'
             '<circle cx="20" cy="10" r="9" fill="#4a5260"/>'
             '<path d="M2,38 a18,16 0 0 1 36,0 Z" fill="#4a5260"/>'
             '<circle cx="44" cy="14" r="7" fill="#7b8492"/>'
             '<path d="M32,38 a13,12 0 0 1 26,0 Z" fill="#7b8492"/></g>')
label(115, 540, "Users", size=12, color="#232f3e", weight="600")
label(115, 555, "(Browser)")

# OpenAI (external)
box(60, 700, 150, 90, "", "#7b8492", fill="#f5f6f8", dash="4 3")
label(135, 738, "OpenAI API", size=12, color="#232f3e", weight="600")
label(135, 755, "external call")
label(135, 770, "(Bedrock later)")

# --- AWS Cloud frame ---
box(250, 60, 1540, 990, "AWS Cloud", "#232f3e")

# --- Shared services & Observability (top band, regional) ---
box(285, 95, 800, 175, "Shared services & Observability (regional)", "#5A6B86",
    fill="#f4f7fb", dash="5 4")
icon(365, 175, "param", ["Parameter Store", "config · SecureString"])
icon(525, 175, "appconfig", ["AppConfig", "feature flags"])
icon(685, 175, "cloudwatch", ["CloudWatch", "logs · metrics"])
icon(845, 175, "xray", ["X-Ray (ADOT)", "tracing"])
icon(1010, 175, "ecr", ["Amazon ECR", "images"])

# --- Edge & Identity (regional) ---
box(285, 320, 360, 300, "Edge & Identity (regional)", "#5A6B86", fill="#ffffff", dash="5 4")
icon(370, 400, "cloudfront", ["CloudFront", "(CDN)"])
icon(560, 400, "cognito", ["Cognito", "User Pool · JWT"])
icon(370, 540, "s3", ["S3", "SPA assets (OAC)"])
icon(560, 540, "apigw", ["API Gateway", "JWT authz + VPC Link"])

# --- VPC ---
box(700, 300, 1055, 725, "VPC (10.0.0.0/16)", "#1a9c1a", fill="#f0f8f0")

#   Public subnets
box(735, 345, 250, 255, "Public subnets (AZ-a / AZ-b)", "#8C6FD6", fill="#efeaf9", dash="5 4")
icon(860, 430, "alb", ["Application", "Load Balancer"])
icon(860, 540, "nat", ["NAT Gateway", "egress"])

#   Private subnets
box(735, 640, 985, 360, "Private subnets (AZ-a / AZ-b)", "#4a90d9", fill="#e9f2fb", dash="5 4")

#     ECS Fargate tasks
box(760, 685, 300, 290, "ECS Fargate tasks", "#d9a441", fill="#fff8ef", dash="5 4")
icon(840, 765, "fargate", ["ResumeService"])
icon(985, 765, "fargate", ["SearchService"])
icon(840, 895, "fargate", ["ScraperService"])
icon(985, 895, "lambda", ["JobCompression", "(Lambda planned)"])

#     Cloud Map
icon(1150, 770, "cloudmap", ["Cloud Map", "API discovery"])

#     Managed data & messaging
box(1250, 675, 445, 305, "Managed data & messaging", "#d9a441", fill="#fff8ef", dash="5 4")
icon(1335, 750, "aurora", ["Aurora PostgreSQL", "+ pgvector · resume"])
icon(1500, 750, "rds", ["RDS PostgreSQL", "saved jobs / ratings"])
icon(1640, 750, "opensearch", ["OpenSearch", "job index"])
icon(1370, 905, "documentdb", ["DocumentDB", "scraped jobs"])
icon(1560, 905, "msk", ["Amazon MSK", "raw → enriched"])

# ============================================================================================
# FLOWS — intentionally omitted (icons + grouping boxes only, no connector clutter)
# ============================================================================================

parts.append("</svg>")
svg = "\n".join(parts)

out_svg = os.path.join(os.path.dirname(os.path.abspath(__file__)), "aws-architecture.svg")
with open(out_svg, "w", encoding="utf-8") as fh:
    fh.write(svg)
print("wrote", out_svg)

# --- rasterise to PNG via headless Chrome ------------------------------------------------------
chrome = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
out_png = os.path.join(os.path.dirname(os.path.abspath(__file__)), "aws-architecture.png")
if os.path.exists(chrome):
    subprocess.run(
        [chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
         f"--screenshot={out_png}", f"--window-size={W},{H}",
         "--default-background-color=FFFFFFFF", "file:///" + out_svg.replace("\\", "/")],
        check=False,
    )
    print("wrote", out_png)
