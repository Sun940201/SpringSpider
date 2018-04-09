# -*- coding: utf-8 -*-

# Define here the models for your scraped items
#
# See documentation in:
# http://doc.scrapy.org/en/latest/topics/items.html

import scrapy


class SpringspiderItem1(scrapy.Item):
    # define the fields for your item here like:
    # name = scrapy.Field()
    status = scrapy.Field()
    priority = scrapy.Field()
    project_name = scrapy.Field()
    components = scrapy.Field()  # array
    affect_versions = scrapy.Field()  # array
    resloved_time = scrapy.Field()
    reporter = scrapy.Field()
    fixed_versions = scrapy.Field()  # array
    vote = scrapy.Field()
    updated_time = scrapy.Field()
    created_time = scrapy.Field()
    bug_id = scrapy.Field()
    bug_info = scrapy.Field()
    bug_desc = scrapy.Field()
    bug_url = scrapy.Field()
    watchers = scrapy.Field()
    Assignee = scrapy.Field()
    issue_links = scrapy.Field()  # array
    resolution = scrapy.Field()
    pass
