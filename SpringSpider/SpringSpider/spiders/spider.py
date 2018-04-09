#coding:utf-8
import json
import scrapy #导入scrapy包
# from scrapy.http import Request  # 一个单独的request的模块，需要跟进URL的时候，需要用它
from scrapy import Request

# 使用userid进行遍历，可能出现[]情况，这是正常的，表明该用户没有任何项目。但返回仍是200，可以直至遍历到返回状态404，表明userid已经遍历完
# 但是在中途也有部分userid不存在，如269，所以还是遍历到用户id最大值，目前经验发现max <= 13000 (120499)
from homebrew.MongoUtil import MongoUtil
from homebrew.items import HomebrewItem, HomebrewItem1


class Myspider(scrapy.Spider):
    name = 'spring'
    mongo_db = "Homebrew"
    mongo_col = "Homebrew"
    # allowed_domains = ['drupal.org']
    # bash_url = 'https://www.drupal.org'  # 拼接需要继续访问的url
    handle_httpstatus_list = [403, 404, 429, 500]  # 保证可以处理错误的返回链接
    url1 = "https://jira.spring.io/browse/XD-3743?jql=issuetype%20%3D%20Bug%20AND%20status%20in%20(Resolved%2C%20Closed%2C%20Done)"  # 从该页开始爬取
    # url1 = "https://rubygems.org/api/v1/owners/1/gems.json"
    project_url = "https://jira.spring.io/secure/Dashboard.jspa"  # 总项目首页
    url2 = "https://jira.spring.io"  # 主页情况
    # print("start spider")

    def start_requests(self):
        print "start1"
        # while True:
        #     # if self.count < 5:
        #     #     self.count += 1
        #     #     continue
        #     if self.flag and self.count > 120499:
        #         print "finish count"
        #         break
        #     else:
        #         self.flag = False
        #     url = self.url1 + str(self.count) + self.url2
        #     yield Request(url, self.parse)  # 出现MissValue时有可能是网址没有加上http
        #     with open('urls_visited.txt', 'a') as f:  # 文件需放在与entrypoint同目录下
        #         f.write(url + '\n')
        #     time.sleep(1)
        #     print "爬取至" + str(self.count)
        #     self.count += 1
        yield Request(self.project_url, self.parse)  # 出现MissValue时有可能是网址没有加上http
        print "end"

    def parse(self, response): #处理项目首页
        # print(response.text)
        # if response.status in self.handle_httpstatus_list:
        #     if response.status == 404:
        #         self.flag = True
        #         print "404" + response.url
        #         return
        #     if response.status == 429:  # ip被封
        #         print "ip problem：" + response.url
        #         time.sleep(1)
        #         yield Request(response.url, self.parse)
        #         return
        i = HomebrewItem()
        "/html/body/div[2]/div/div/ul/li/ul/li[1]/h5/a"
        if len(json.loads(response.body_as_unicode())) == 0:
            print response.url + "has no info"
            return
        for jsr in json.loads(response.body_as_unicode()):  # responese为json数据
            i["project_name"] = jsr["formula"] if jsr["formula"] is not None else ""
            i["detail_info"] = jsr["description"].strip() if jsr["description"] is not None else ""
            i["project_url"] = self.url2+jsr["formula"] if jsr["formula"] is not None else ""
            i["homepage"] = jsr["homepage"] if jsr["homepage"] is not None else ""
            query = {"project_url": i["project_url"]}
            flag = MongoUtil.getResultForQuery(self.mongo_col, self.mongo_db, query).count()
            if flag > 0:  # 进行去重
                print "finish: " + i["project_url"]
                continue
            yield i
            yield Request(i["project_url"], self.parseproject)
            # yield Request(self.url3+i['project_name']+"/owners.json", self.parseowners,meta={"project_url": i["project_url"]})
            # yield Request(i["project_url"]+"/versions", self.parseversions, meta={"project_url": i["project_url"]})

    def parseproject(self, response):  # 处理单个项目首页
        i = HomebrewItem1()
        full_name = response.xpath("/html/body/div[4]/div[1]/div/h1/text()").extract()
        i["full_name"] = full_name[0] if len(full_name) > 0 else ""
        i["other_urls"] = []
        urls = response.xpath("/html/body/div[4]/div[2]/div[2]/dl/dd[3]/a/@href").extract()
        urls = urls[0] if len(urls) > 0 else ""
        i["other_urls"].append(urls)
        i["project_url"] = response.url
        return i

    # def parseowners(self, response):  # 处理项目owners
    #     i = RubySpiderItem3()
    #     i["owners"] = []
    #     i["project_url"] = response.meta["project_url"]
    #     for jsr in json.loads(response.body_as_unicode()):  # responese为json数据
    #         i["owners"].append(jsr["handle"])
    #     # print(i)
    #     return i

    # def parseversions(self, response):  # 处理单个项目的所有版本
    #     i = RubySpiderItem4()
    #     i["project_url"] = response.meta["project_url"]
    #     i["versions"] = []
    #     versions = response.xpath("//ul[@class='t-list__items']/li")
    #     for version in versions:
    #         temp = {}
    #         temp["version_name"] = version.xpath("./a/text()").extract()
    #         if len(temp["version_name"]) > 0:
    #             temp["version_name"] = temp["version_name"][0]
    #         else:
    #             temp["version_name"] = ""
    #         temp["version_url"] = version.xpath("./a/@href").extract()
    #         if len(temp["version_url"]) > 0:
    #             temp["version_url"] = temp["version_url"][0]
    #         else:
    #             temp["version_url"] = ""
    #         t = version.xpath("./small/text()").extract()
    #         temp["version_time"] = time.ctime() if len(t) == 0 else t[0]
    #         time_string = temp["version_time"]
    #         datetime_struct = parser.parse(time_string)
    #         temp["version_time"] = datetime_struct.strftime('%Y-%m-%dT%H:%M:%SZ')
    #         # print(temp["version_time"])
    #         i["versions"].append(temp)
    #     i["version_num"] = len(i["versions"])
    #     i["latest_version"] = i["versions"][0]["version_name"]
    #     i["last_version_time"] = i["versions"][0]["version_time"]
    #     return i
