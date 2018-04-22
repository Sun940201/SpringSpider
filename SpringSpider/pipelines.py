# -*- coding: utf-8 -*-

# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: http://doc.scrapy.org/en/latest/topics/item-pipeline.html
from SpringSpider.MongoUtil import MongoUtil

from SpringSpider.items import SpringspiderItem1, SpringspiderItem2

mongo_db = "Bugs"
mongo_col = "SpringBugs"


class SpringspiderPipeline(object):
    def process_item(self, item, spider):
        if isinstance(item, SpringspiderItem1):
            MongoUtil.insertOneDoc(mongo_db, mongo_col, {"status": item["status"], "priority": item["priority"],
                                                         "project_name": item["project_name"],
                                                         "components": item["components"],
                                                         "affect_versions": item["affect_versions"],
                                                         "resloved_time": item["resloved_time"],
                                                         "reporter": item["reporter"],
                                                         "fixed_versions": item["fixed_versions"],
                                                         "vote": item["vote"], "updated_time": item["updated_time"],
                                                         "created_time": item["created_time"], "bug_id": item["bug_id"],
                                                         "bug_info": item["bug_info"], "bug_desc": item["bug_desc"],
                                                         "bug_url": item["bug_url"], "watchers": item["watchers"],
                                                         "Assignee": item["Assignee"],
                                                         "issue_links": item["issue_links"],
                                                         "resolution": item["resolution"],
                                                         "Pull_Request_URL": item["Pull_Request_URL"]})
        if isinstance(item, SpringspiderItem2):
            MongoUtil.updateOneDoc(mongo_db, mongo_col, {"bug_id": item["bug_id"]},
                                   {"$set": {"file_list": item["file_list"]}})
