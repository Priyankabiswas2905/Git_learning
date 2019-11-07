# Script to migrate all files on disk to AWS S3 buckets.



```
docker run -it --rm --env SERVICE_ENDPOINT=http://miniopublicip:8000 --env BUCKET=localbucket --env AWS_ACCESS_KEY_ID=WF6UTMSD3ABVDJQIY3TU --env AWS_SECRET_ACCESS_KEY=obd+gg5bFCGKFhoFprmnXRrwOC+wdbDIEidng7PZ --env REGION=us-east-1 --env DBURL=mongodb://mongodbpublicip:27017 --env DBNAME=clowder  -v ${PWD}/output:/output -v /:/host migratefilestos3
```