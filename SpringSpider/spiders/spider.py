# coding:utf-8
import json

import re
import traceback

import scrapy  # 导入scrapy包
# from scrapy.http import Request  # 一个单独的request的模块，需要跟进URL的时候，需要用它
from scrapy import Request

# 使用userid进行遍历，可能出现[]情况，这是正常的，表明该用户没有任何项目。但返回仍是200，可以直至遍历到返回状态404，表明userid已经遍历完
# 但是在中途也有部分userid不存在，如269，所以还是遍历到用户id最大值，目前经验发现max <= 13000 (120499)
# from SpringSpider.SpringSpider.MongoUtil import MongoUtil  # 必须添加init.py才能使文件夹成为一个模块从而可以完成import

from SpringSpider.items import SpringspiderItem1
from selenium import webdriver
import time


class Myspider(scrapy.Spider):
    name = 'spring'
    mongo_db = "Homebrew"
    mongo_col = "Homebrew"
    # allowed_domains = ['drupal.org']
    # bash_url = 'https://www.drupal.org'  # 拼接需要继续访问的url
    handle_httpstatus_list = [302, 403, 404, 429, 500]  # 保证可以处理错误的返回链接
    url1 = "https://jira.spring.io/browse/XD-3743?jql=issuetype%20%3D%20Bug%20AND%20status%20in%20(Resolved%2C%20Closed%2C%20Done)"  # 从该页开始爬取
    # url1 = "https://rubygems.org/api/v1/owners/1/gems.json"
    project_url = "https://jira.spring.io/secure/Dashboard.jspa"  # 总项目首页
    # project_url = "https://jira.spring.io/secure/BrowseProjects.jspa?selectedCategory=all"
    url2 = "https://jira.spring.io"  # 主页情况
    test_url = "https://jira.spring.io/browse/XD-3755"
    git_url = "https://github.com/spring-projects/spring-xd/pull/1895/files"
    redirect_url = "https://jira.spring.io/browse/DATACMNS-805"

    # print("start spider")

    def start_requests(self):
        print "start1"

        url = self.project_url
        # browser = webdriver.Firefox()
        browser = webdriver.PhantomJS("/home/sx/python/phantomjs-2.1.1-linux-x86_64/bin/phantomjs")
        # browser.get("http://pythonscraping.com/pages/javascript/ajaxDemo.html")  # 测试用
        browser.get(url)  # 得到返回的页面
        time.sleep(2)  # 等待页面完全加载
        browser.switch_to.frame(browser.find_element_by_xpath("//iframe[@id='gadget-12194']"))  # 切入iframe
        project_list = browser.find_elements_by_xpath("/html/body/div[2]/div/div/ul/li/ul/li/h5/span")  # 获取项目列表

        # browser.switch_to.default_content()  # 切回主页面
        # 隐式等待5秒，可以自己调节
        # browser.implicitly_wait(5)
        # 设置10秒页面超时返回，类似于requests.get()的timeout选项，driver.get()没有timeout选项
        # browser.set_page_load_timeout(10)
        # 获取网页资源（获取到的是网页所有数据）

        for project in project_list:
            project_simple_name = project.text  # 已经是element了，不能再使用xpath方法，与selector得到的xpath不同
            project_simple_name = re.sub('[()]', "", project_simple_name)
            project_url = self.url2 + "/secure/IssueNavigator.jspa?reset=true&mode=hide&jqlQuery=project+%3D+" + project_simple_name
            # browser_temp = webdriver.PhantomJS("/home/sx/python/phantomjs-2.1.1-linux-x86_64/bin/phantomjs")
            yield Request(project_url, self.parse)
            # browser_temp.close()
        browser.close()

        # yield Request(self.redirect_url, self.parseIssues)
        # yield Request(self.git_url, self.parseGit)
        print "end"

    def parse(self, response):  # 处理单个项目首页
        if response.status in self.handle_httpstatus_list:  # 对于404的处理
            if response.status == 404:
                print "404: " + response.url
                return
            if response.status == 302:  # 访问项目首页会进行重定向,需要继续访问重定向的url
                print "302: " + response.headers.getlist("Location")[0]
                yield Request(response.headers.getlist("Location")[0], self.parse)
                return
        flag = response.xpath(
            "//*[@id='content']/div[1]/div[4]/div/div/div/div/div/div[1]/div[1]/div[2]/ol/li[1]/a/span[1]/text()")
        if len(flag) == 0:
            return
        issues_maxid = flag.extract()[0]
        issues_name = issues_maxid.split("-")[0]
        issues_maxid = int(issues_maxid.split("-")[1])
        # project_name = response.meta["project_name"]
        for i in range(1, issues_maxid + 1):
            issues_url = self.url2 + '/browse/' + issues_name + '-' + str(i)
            yield Request(issues_url, self.parseIssues, meta={"bug_id": issues_name + '-' + str(i)})

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
            # "/html/body/div[2]/div/div/ul/li/ul/li[1]/h5/a"
            # "//*[@id="content"]/div[1]/div[4]/div/div/div/div/div/div[1]/div[1]/div[2]/ol/li[1]/a/span[1]"
            # i = SpringspiderItem1()
            # if len(json.loads(response.body_as_unicode())) == 0:
            #     print response.url + "has no info"
            #     return
            # for jsr in json.loads(response.body_as_unicode()):  # responese为json数据
            #     i["project_name"] = jsr["formula"] if jsr["formula"] is not None else ""
            #     i["detail_info"] = jsr["description"].strip() if jsr["description"] is not None else ""
            #     i["project_url"] = self.url2 + jsr["formula"] if jsr["formula"] is not None else ""
            #     i["homepage"] = jsr["homepage"] if jsr["homepage"] is not None else ""
            #     query = {"project_url": i["project_url"]}
            #     flag = MongoUtil.getResultForQuery(self.mongo_col, self.mongo_db, query).count()
            #     if flag > 0:  # 进行去重
            #         print "finish: " + i["project_url"]
            #         continue
            #     yield i
            #     yield Request(i["project_url"], self.parseproject)
            # yield Request(self.url3+i['project_name']+"/owners.json", self.parseowners,meta={"project_url": i["project_url"]})
            # yield Request(i["project_url"]+"/versions", self.parseversions, meta={"project_url": i["project_url"]})

    def parseIssues(self, response):  # 处理单个issues首页
        try:
            if response.status in self.handle_httpstatus_list:  # 对于404的处理
                if response.status == 404:
                    print "404: " + response.url
                    return
                if response.status == 302:  # 有时候issues的序号不存在时会重定向到其他issues中，需要避免此情况
                    # print "302" + response.headers["Location"]  # 这样写返回的是Location list里的最后一项即‘h’
                    print "302: " + response.headers.getlist("Location")[0]
                    return
            flag = response.xpath("//*[@id='issue-content']/div/div/p[1]")
            if len(flag) != 0:
                return
            i = SpringspiderItem1()
            status = '' if len(response.xpath("//*[@id='status-val']/span/text()")) == 0 else \
                response.xpath("//*[@id='status-val']/span/text()").extract()[0]
            i["status"] = status

            priority = response.xpath("//*[@id='priority-val']")
            info = ' ' if len(priority) == 0 else priority.xpath('string(.)').extract()[0]  # 提取同一标签内的所有字符串
            priority = info.strip()  # 删除空格
            i["priority"] = priority

            project_name = response.xpath("//*[@id='project-name-val']/text()").extract()[0]  # extract之后即为unicode
            i["project_name"] = project_name

            components = response.xpath("//*[@id='components-field']/a")
            components_array = []
            for componet in components:
                href = componet.xpath("./@href").extract()[0]
                if href == '#':  # 防止出现组件个数的缩放的情况，爬取网站源码不会出现此情况
                    continue
                component_name = componet.xpath("./text()").extract()[0]
                components_array.append(component_name)
            i["components"] = components_array

            affect_versions = response.xpath("//*[@id='versions-field']/span")
            affect_array = []
            for affect in affect_versions:
                affect_array.append(affect.xpath("./text()").extract()[0])
            i["affect_versions"] = affect_array

            fixed_versions = response.xpath("//*[@id='fixVersions-field']/a")
            fixed_array = []
            for fix in fixed_versions:
                fixed_array.append(fix.xpath("./text()").extract()[0])
            i["fixed_versions"] = fixed_array

            issue_links = response.xpath("//*[@class='links-list ']/dd/div/p/span/a")  # 注意class属性中的空格！！！
            issue_array = []
            for issue in issue_links:
                issue_array.append(self.url2 + issue.xpath("./@href").extract()[0])
            i["issue_links"] = issue_array

            resloved_time = '' if len(response.xpath("//*[@id='resolved-date']/time")) == 0 else response.xpath(
                "//*[@id='resolved-date']/time/@datetime").extract()[0]
            i["resloved_time"] = resloved_time

            updated_time = '' if len(response.xpath("//*[@id='updated-date']/time")) == 0 else response.xpath(
                "//*[@id='updated-date']/time/@datetime").extract()[0]
            i["updated_time"] = updated_time

            created_time = '' if len(response.xpath("//*[@id='create-date']/time")) == 0 else response.xpath(
                "//*[@id='create-date']/time/@datetime").extract()[0]
            i["created_time"] = created_time

            bug_info = '' if len(response.xpath("//*[@id='summary-val']/text()")) == 0 else \
                response.xpath("//*[@id='summary-val']/text()").extract()[0]
            i["bug_info"] = bug_info
            bug_desc = '' if len(response.xpath("//*[@id='description-val']/div/p")) == 0 else \
                response.xpath("//*[@id='description-val']/div/p").xpath('string(.)').extract()[0].replace('\n', "")
            i["bug_desc"] = bug_desc
            bug_url = response.url
            i["bug_url"] = bug_url
            reporter = '' if len(response.xpath("//*[@id='issue_summary_reporter_grussell']")) == 0 else \
                response.xpath("//*[@id='issue_summary_reporter_grussell']").xpath('string(.)').extract()[0].replace(
                    '\n',
                    "")
            i["reporter"] = reporter
            assignee = '' if len(response.xpath("//*[@id='issue_summary_assignee_abilan']")) == 0 else \
                response.xpath("//*[@id='issue_summary_assignee_abilan']").xpath('string(.)').extract()[0].replace('\n',
                                                                                                                   "")
            i["Assignee"] = assignee
            resolution = '' if len(response.xpath("//*[@id='resolution-val']")) == 0 else \
                response.xpath("//*[@id='resolution-val']").xpath('string(.)').extract()[0].replace('\n', "")
            i["resolution"] = resolution
            vote = '' if len(response.xpath("//*[@id='vote-data']/text()")) == 0 else \
                response.xpath("//*[@id='vote-data']/text()").extract()[0]
            i["vote"] = vote
            watchers = '' if len(response.xpath("//*[@id='watcher-data']/text()")) == 0 else \
                response.xpath("//*[@id='watcher-data']/text()").extract()[0]
            i["watchers"] = watchers
            bug_id = response.meta["bug_id"]
            i["bug_id"] = bug_id
            request_url = 'None' if len(response.xpath("//*[@id='customfield_10684-val']/a/@href").extract()) == 0 \
                else response.xpath("//*[@id='customfield_10684-val']/a/@href").extract()[0]
            i["Pull_Request_URL"] = request_url

            return i
        except Exception, e:
            print Exception, ":", e
            msg = traceback.format_exc()
            print '处理出错', msg
            with open('url_bugs.txt', 'a') as f:
                f.write(response.url + '\n')
                f.write(msg + '\n')

    def parseGit(self, response):  # 处理单个issues首页
        # data = response.xpath("//*[@id='diff-0']/div[2]/div/table/tr[4]/td[3]")
        data = response.xpath("//*[@id='diff-0']/div[2]/div/table")
        info = data.xpath('string(.)').extract()[0]  # 提取同一标签内的所有字符串
        print info
