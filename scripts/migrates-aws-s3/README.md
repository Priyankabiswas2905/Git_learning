# Script to migrate all files on disk to AWS S3 buckets.


The script will scan Clowder collections: 'logo', 'uploads', 'thumbnails', 'titles', 'textures', 'previews'. And then upload files on disk to AWS S3 buckets and update `loader` to `services.s3.S3ByteStorageService`.

## build the docker image
```
docker build -t migratefilestos3 .
```

## run as docker
Need to mount host filesystem as the volume to the docker container and specify the environment `HOSTFILESYSTEM`. 
```
docker run -it --rm --env SERVICE_ENDPOINT=http://miniopublicip:8000 --env BUCKET=localbucket --env AWS_ACCESS_KEY_ID=WF6UTMSD3ABVDJQIY3TU --env AWS_SECRET_ACCESS_KEY=obd+gg5bFCGKFhoFprmnXRrwOC+wdbDIEidng7PZ --env REGION=us-east-1 --env DBURL=mongodb://mongodbpublicip:27017 --env DBNAME=clowder  --env HOSTFILESYSTEM=/host --env OUTPUTFOLDER=/output -v /:/host -v ${PWD}/output:/output migratefilestos3
```

## run as python script

config the 'config.py' for the S3 settings. Then run the below command. You do not have to specify the host filesystem, since the script can access the files.

```
python ./main.py --dburl mongodb://mongodbpublicip:27017 --dbname clowder --outputfolder ./output 
```