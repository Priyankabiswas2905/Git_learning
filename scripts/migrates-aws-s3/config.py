import os

# local minio S3 settings
SERVICE_ENDPOINT=os.getenv("SERVICE_ENDPOINT", 'http://localhost:8000')
BUCKET=os.getenv("BUCKET", "localbucket")
AWS_ACCESS_KEY_ID=os.getenv("AWS_ACCESS_KEY_ID", "WF6UTMSD3ABVDJQIY3TU")
AWS_SECRET_ACCESS_KEY=os.getenv("AWS_SECRET_ACCESS_KEY", "obd+gg5bFCGKFhoFprmnXRrwOC+wdbDIEidng7PZ")
REGION=os.getenv("REGION", "us-east-1")

