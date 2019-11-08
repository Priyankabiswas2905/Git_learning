# Script to migrate all files on disk to AWS S3 buckets.


The script will scan Clowder collections: 'logo', 'uploads', 'thumbnails', 'titles', 'textures', 'previews'. And then upload files on disk to AWS S3 buckets and update `loader` to `services.s3.S3ByteStorageService` in db.

## build the docker image
```
docker build -t migratefilestos3 .
```

## run as docker
Need to mount host filesystem as the volume to the docker container and specify the environment `HOSTFILESYSTEM`. 
```
docker run -it --rm --env SERVICE_ENDPOINT=http://s3service_endpoint --env BUCKET=localbucket --env AWS_ACCESS_KEY_ID=yourawsid --env AWS_SECRET_ACCESS_KEY=yourawssecretkey --env REGION=us-east-1 --env DBURL=mongodb://mongodbpublicip --env DBNAME=clowder  --env HOSTFILESYSTEM=/host --env OUTPUTFOLDER=/output -v /:/host -v ${PWD}/output:/output migratefilestos3
```

## run as python script

Run the below command. You do not have to specify the host filesystem, since the script can access the files.

```
python ./main.py --dburl mongodb://mongodbpublicip:27017 --dbname clowder --s3endpoint s3-endpoint --s3bucket s3bucketname --s3ID yourawsid --s3KEY yourawssecretkey --s3REGION s3region --outputfolder ./output 
```

## Test with minIO
Before migrating files to AWS S3 bucket, you can use minIO to simulate the S3 bucket in your local machine.

To start minIO, just run the below command to start the minIO docker container on your machine. In the terminal, minIO will print out service endpoint, access secrets, region, etc. You can use the access secrets to login minIO on the machine and setup the bucket.

```
docker run --name=minio -dit -p 8000:9000 -v $(pwd)/minio-buckets:/data minio/minio server /data --compat && docker logs -f minio
```