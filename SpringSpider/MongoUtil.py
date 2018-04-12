from __future__ import division
# -*- coding:utf-8 -*-
import time
import datetime
import ssl
import json
import pymongo
import csv
import traceback
import os
import sys


# reload(sys)
# sys.setdefaultencoding('utf-8')
# csv.field_size_limit(sys.maxint)  # 设置csv的读取参数
mongo_host = "47.104.97.166"
mongo_port = 37018
mongo_db = "Bugs"
mongo_col = "SpringBugs"
mongo = pymongo.MongoClient(mongo_host, mongo_port)
# print("mongodb init...")

#  处理爬取的数据，并存入mongo
class MongoUtil:
    #headers = {'content-type': 'application/json'}

    @classmethod
    def updateOneDoc(cls, dbName, collectionName, docIndicate, updateContent):#字段不存在时会自动添加插入
        try:
            if mongo is not None:
                db = mongo.get_database(dbName)
                collection = db.get_collection(collectionName)
                collection.update_one(docIndicate, updateContent)
                return 1
            return -1
        except Exception as e:
            msg = traceback.format_exc()
            print msg
            return -1

    # db.users.update({"name": "user1"}, {"$set": {"age": 100, "sex": 0}})

    # def exeucteQuery(self, dbName, collectionName, query):
    #     try:
    #         db = self.mongoClient.get_database(dbName)
    #         collection = db.get_collection(collectionName)
    #         result = collection.find(query)
    #     except Exception, e:
    #
    #         print e.message

    @classmethod
    def getResultForQuery(cls, dbName, collectionName, query):#结果均为类似list，但无法直接打印，需要用下标访问，如果不存在的话，不会返回None，但是不可以用[0]访问
        try:
            db = mongo.get_database(dbName)
            collection = db.get_collection(collectionName)
            result = collection.find(query)#结果均为类似list，但无法直接打印并且没有长度属性，需要用下标访问，或者for循环访问
            return result
        except Exception as e:
            stds =traceback.format_exc()
            print stds
            print "fail"
            return None

    @classmethod
    def getOneResultForQuery(cls, dbName, collectionName, query):#找到一个结果，不存在的话返回None
        try:
            db = mongo.get_database(dbName)
            collection = db.get_collection(collectionName)
            result = collection.find_one(query)
            return result
        except Exception as e:
            stds =traceback.format_exc()
            print stds
            print "fail"
            return None

    @classmethod
    def insertOneDoc(cls,dbName,collectionName, docContnet):
        try:
            db = mongo.get_database(dbName)
            collection = db.get_collection(collectionName)
            collection.insert(docContnet)

            return 1
        except Exception as e:
            stds = traceback.format_exc()
            print stds
            print "fail"
            return -1

if __name__=='__main__':
    pass
