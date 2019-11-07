import os
import argparse
import pymongo
from bson import ObjectId
from pymongo.mongo_client import MongoClient
import traceback
from datetime import datetime

import config
from s3 import S3Bucket


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='migrate Clowder files to S3')

    parser.add_argument('--dburl', '-u', default=os.getenv("DBURL", None),
                        help='Clowder databse url')

    parser.add_argument('--dbname', '-d', default=os.getenv("DBNAME", None),
                        help='Clowder databse name')

    parser.add_argument('--hostfs', '-f', default=os.getenv("HOSTFILESYSTEM", None),
                        help='the mount point of host filesystem')
    parser.add_argument('--outputfolder', '-o', default=os.getenv("OUTPUTFOLDER", None),
                        help='the output folder where a file contains the information'
                             ' of a list of all files that have been migrated to S3')

    args = parser.parse_args()

    print('migrate disk storage files to s3')
    print('Clowder dburl: %s, dbname: %s' % (args.dburl, args.dbname))
    print('upload files to S3: region: %s, service endpoint: %s' % (config.REGION, config.SERVICE_ENDPOINT))
    print('S3 bucket: %s' % config.BUCKET)
    hostfilesystem = args.hostfs
    if not hostfilesystem:
        hostfilesystem = ""
    f = None
    total_bytes_uploaded = 0
    collections = ['logo', 'uploads', 'thumbnails', 'titles', 'textures', 'previews']
    try:
        now = datetime.now()
        dt_string = now.strftime("%d-%m-%YT%H:%M:%S")
        file_path = "%s/migrates-filelist-%s.txt" % (args.outputfolder, dt_string)
        directory = os.path.dirname(file_path)
        if not os.path.exists(directory):
            os.mkdir(directory)
        f = open(file_path, "w")
        client = MongoClient(args.dburl)
        db = client.get_database(name=args.dbname)

        for collection in collections:
            try:
                num = db[collection].count_documents({})
                nuum_not_disk_storage = 0
                ndiskfiles = 0
                nfails = 0
                nsuccess = 0
                for data_tuple in db[collection].find({}, {'_id': 1, 'loader_id': 1, 'loader': 1}):
                    try:
                        record_id = str(data_tuple.get('_id'))
                        file_bytes = 0
                        loader = data_tuple.get('loader')
                        if loader == 'services.filesystem.DiskByteStorageService':
                            ndiskfiles += 1
                            loader_id = data_tuple.get('loader_id')
                            statinfo = os.stat(hostfilesystem+loader_id)
                            file_bytes = statinfo.st_size
                            print(statinfo.st_size)
                            S3Bucket().upload(hostfilesystem+loader_id, loader_id)
                            # MinioBucket().upload(loader_id)
                            # update record loader to 'services.s3.S3ByteStorageService'
                            update_data = dict()
                            update_data['loader'] = 'services.s3.S3ByteStorageService'
                            # either the same loader_id or convert to UUID.
                            # update_data['loader_id'] = loader_id
                            status = db[collection].update_one({'_id': ObjectId(record_id)}, {"$set": update_data})
                            if status.modified_count != 1:
                                raise Exception("failed to update db %d" % record_id)
                            nsuccess += 1
                            f.write(loader_id + "\n")
                        else:
                            nuum_not_disk_storage += 1
                    except Exception as ex:
                        traceback.print_exc()
                        print("record: %s failed" % record_id)
                        nfails += 1
                    total_bytes_uploaded += file_bytes
            except Exception as ex:
                traceback.print_exc()
            print("working on collection: %s, total records: %d, total on disk files %d, success: %d, failed: %d" %
                  (collection, num, ndiskfiles, nsuccess, nfails))
    except Exception as ex:
        traceback.print_exc()
    finally:
        if f:
            f.close()

    print("upload total bytes: " + str(total_bytes_uploaded))
    print("Done")

