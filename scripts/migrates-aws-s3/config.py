import os

# local minio S3 settings
SERVICE_ENDPOINT=os.getenv("SERVICE_ENDPOINT", 'http://localhost:8000')
BUCKET=os.getenv("BUCKET", "localbucket")
AWS_ACCESS_KEY_ID=os.getenv("AWS_ACCESS_KEY_ID", "AWS_ACCESS_KEY_ID")
AWS_SECRET_ACCESS_KEY=os.getenv("AWS_SECRET_ACCESS_KEY", "AWS_SECRET_ACCESS_KEY")
REGION=os.getenv("REGION", "us-east-1")

