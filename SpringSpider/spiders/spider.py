# coding:utf-8
import json
import os

import re
import traceback

import scrapy  # 导入scrapy包
# from scrapy.http import Request  # 一个单独的request的模块，需要跟进URL的时候，需要用它
from scrapy import Request

# 使用userid进行遍历，可能出现[]情况，这是正常的，表明该用户没有任何项目。但返回仍是200，可以直至遍历到返回状态404，表明userid已经遍历完
# 但是在中途也有部分userid不存在，如269，所以还是遍历到用户id最大值，目前经验发现max <= 13000 (120499)
# from SpringSpider.SpringSpider.MongoUtil import MongoUtil  # 必须添加init.py才能使文件夹成为一个模块从而可以完成import
from selenium.common.exceptions import StaleElementReferenceException, NoSuchElementException

from SpringSpider.MongoUtil import MongoUtil
from SpringSpider.items import SpringspiderItem1, SpringspiderItem2
from selenium import webdriver
import time


class Myspider(scrapy.Spider):
    name = 'spring'
    mongo_db = "Bugs"
    mongo_col = "SpringBugs"
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

        # test_url = "https://github.com/spring-projects/spring-data-commons/pull/281"  # test
        # yield Request(test_url, self.parseGit, meta={"project_name": "project_name", "bug_id": "bug_id"})
        query1 = {"$ne": "None"}
        query = {"Pull_Request_URL": query1}
        time = True  # 取消超时时间
        docs = MongoUtil.getResultForQuery(self.mongo_db, self.mongo_col, query, time)
        for doc in docs:
            yield Request(doc["Pull_Request_URL"], self.parseGit,
                          meta={"project_name": doc["project_name"], "bug_id": doc["bug_id"]})
        docs.close()

        # 使用PhantomJS爬取spring io网址的所有issues信息
        # url = self.project_url
        # # browser = webdriver.Firefox()
        # browser = webdriver.PhantomJS("/home/sx/python/phantomjs-2.1.1-linux-x86_64/bin/phantomjs")
        # # browser.get("http://pythonscraping.com/pages/javascript/ajaxDemo.html")  # 测试用
        # browser.get(url)  # 得到返回的页面
        # time.sleep(2)  # 等待页面完全加载
        # browser.switch_to.frame(browser.find_element_by_xpath("//iframe[@id='gadget-12194']"))  # 切入iframe
        # project_list = browser.find_elements_by_xpath("/html/body/div[2]/div/div/ul/li/ul/li/h5/span")  # 获取项目列表
        #
        # # browser.switch_to.default_content()  # 切回主页面
        # # 隐式等待5秒，可以自己调节
        # # browser.implicitly_wait(5)
        # # 设置10秒页面超时返回，类似于requests.get()的timeout选项，driver.get()没有timeout选项
        # # browser.set_page_load_timeout(10)
        # # 获取网页资源（获取到的是网页所有数据）
        #
        # for project in project_list:
        #     project_simple_name = project.text  # 与selector得到的xpath相同，可以继续使用xpath获取后续元素
        #     project_simple_name = re.sub('[()]', "", project_simple_name)
        #     project_url = self.url2 + "/secure/IssueNavigator.jspa?reset=true&mode=hide&jqlQuery=project+%3D+" + project_simple_name
        #     # browser_temp = webdriver.PhantomJS("/home/sx/python/phantomjs-2.1.1-linux-x86_64/bin/phantomjs")
        #     yield Request(project_url, self.parse)
        #     # browser_temp.close()
        # browser.close()

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

    def parseGit(self, response):  # 处理单个issues首页,需要增加对404的处理
        # browser = webdriver.Chrome()  # 可以模拟浏览器的各种响应，获取最终动态加载的数据。
        # # 其page_source属性即为最终响应结果，注意:此时浏览器中右键查看网页源码，不一定能够得到最终加载的数据
        # # browser = webdriver.PhantomJS("/home/sx/python/phantomjs-2.1.1-linux-x86_64/bin/phantomjs")  # phantomjs
        # # 对于部分网页如github的点击操作，无法完成，导致无法获取最终响应
        # url = "https://github.com/spring-projects/spring-xd/pull/1895/files"
        # browser.get(url)  # 得到返回的页面
        # time.sleep(3)  # 等待页面完全加载
        # #browser.find_element_by_xpath("//*[@class='btn btn-sm btn-outline BtnGroup-item tooltipped tooltipped-s']").click()
        # browser.find_element_by_xpath('//*[@class="js-expandable-line"][@data-position="0"]/td').click()
        # #js = 'document.getElementsByClassName("btn btn-sm btn-outline BtnGroup-item tooltipped tooltipped-s")[0].click();'
        # #browser.execute_script(js)
        # time.sleep(3)  # 等待页面完全加载
        # print browser.page_source
        # data = browser.find_element_by_xpath("//*[@id='diff-0']/div[2]/div/table")
        # #info = data.xpath('string(.)').extract()[0]  # 提取同一标签内的所有字符串
        # print data
        # print 'end1'

        # data = response.xpath("//*[@id='diff-0']/div[2]/div/table")
        # info = data.xpath('string(.)').extract()[0]  # 提取同一标签内的所有字符串
        # print info

        option = webdriver.ChromeOptions()

        option.add_argument('headless')  # 隐藏浏览器，加快载入速度

        # 打开chrome浏览器

        # driver = webdriver.Chrome(chrome_options=option)
        #
        # driver.get("https://www.baidu.com")
        #
        # print driver.page_source
        browser = webdriver.Chrome(chrome_options=option)  # 可以模拟浏览器的各种响应，获取最终动态加载的数据。
        # browser = webdriver.Chrome()
        # 其page_source属性即为最终响应结果，注意:此时浏览器中右键查看网页源码，不一定能够得到最终加载的数据
        # browser = webdriver.PhantomJS("/home/sx/python/phantomjs-2.1.1-linux-x86_64/bin/phantomjs")  # phantomjs
        # 对于部分网页如github的点击操作，无法完成，导致无法获取最终响应
        # url = "https://github.com/spring-projects/spring-xd/pull/1895/files"  # 单个file
        # url = "https://github.com/spring-projects/spring-data-jdbc/pull/58/files"  # 多个file
        url = response.url+'/files' if response.url.split("/")[-1] != 'files' else response.url
        browser.get(url)  # 得到返回的页面
        time.sleep(3)  # 等待页面完全加载
        bug_line = 0  # debug
        file_name = ""
        try:
            item = SpringspiderItem2()
            click_text = browser.find_element_by_xpath(
                "//*[@class='btn btn-sm btn-outline BtnGroup-item tooltipped tooltipped-s']").text
            if click_text == "Split":
                click_flag = self.retryFindClick(
                    "//*[@class='btn btn-sm btn-outline BtnGroup-item tooltipped tooltipped-s']", browser)
                if not click_flag:
                    with open('pull_url_bugs.txt', 'a') as f:
                        f.write(url + '\n')
                    return
            time.sleep(3)  # 需要时间等待加载完成
            # 将滚动条移动到页面的顶部。使用chrome浏览器，模拟点击事件时，需要保证滚动条处于最顶部，不能自行移动。否则可能报元素在某某位置不可以点击
            while len(browser.find_elements_by_xpath("//*[@class='js-diff-load-button-container']")) != 0:  # 点击load-diff，会导致浏览器滚动条变化，需要手动置顶
                self.retryFindClick("//*[@class='js-diff-load-button-container']", browser)  # 点开后还可能存在expandable扩展情况，所以接下来还需要进行循环
                time.sleep(0.5)
                print len(browser.find_elements_by_xpath("//*[@class='js-diff-load-button-container']"))
            container = browser.find_elements_by_xpath("//*[@class='js-diff-load-button-container']")
            js = "var q=document.body.scrollTop=0"  # 将滚动条移动到页面的顶部。
            browser.execute_script(js)
            time.sleep(3)
            while len(browser.find_elements_by_xpath('//*[@class="js-expandable-line"]/td')) != 0:  # 点击expandable扩展
                # browser.find_element_by_xpath('//*[@class="js-expandable-line"][@data-position="0"]/td').click()
                self.retryFindClick('//*[@class="js-expandable-line"]/td', browser)
                # browser.find_element_by_xpath('//*[@class="diff-expander js-expand"]').click()  # 模拟点击的时候不要控制浏览器否则会出错
                time.sleep(0.5)  # 等待页面完全加载
            # elements_pre = browser.find_elements_by_xpath("//*[@id='diff-0']/div[2]/div/table/tbody/tr/td[2]")
            # elements_cur = browser.find_elements_by_xpath("//*[@id='diff-0']/div[2]/div/table/tbody/tr/td[4]")
            # containers = browser.find_elements_by_xpath("//div[@class='js-diff-load-button-container']")
            # containers = browser.find_elements_by_xpath("//*[@class='js-diff-load-container']")
            elements_pres = browser.find_elements_by_xpath("//*[@class='js-diff-progressive-container']/div")
            # 目的是获取div[@id='diff-x'],反复观察github网页特点，发现需要以div[class=js-diff-progressive-container]为基准点方可，
            # 并且此基准点div会乱插在页面中
            file_list = []
            # file_path = './' + 'file'
            file_path = './' + 'files/' + re.sub('\s', "_", response.meta["project_name"]) + '/' + response.meta[
                "bug_id"]
            path_flag = os.path.exists(file_path)
            # print self.retryFindClick("//*[class='js-diff-load-container']", elements)
            # elements.find_element_by_xpath("//*[class='js-diff-load-container']").click()
            print 'ok'
            if path_flag is False:
                os.makedirs(file_path)  # 建立 ./项目名/bug_id的目录，里面存储可能存在的bug文件。linux下的结构
            for elements in elements_pres:
                if elements.get_attribute("class") == 'js-diff-progressive-container':  # 嵌套情况直接跳过
                    print '嵌套'
                    continue
                file_dict = {}
                elements_pre = elements.find_elements_by_xpath(".//tbody/tr/td[2]")  # 为兼容load-diff的文件需要如此提取
                file_name = elements.find_element_by_xpath("./div/div[2]/a").get_attribute("title")  # spring-data-cassandra/src/test/java/org/springframework/data/cassandra/repository/query/StringBasedCassandraQueryIntegrationUnitTests.java → spring-data-cassandra/src/test/java/org/springframework/data/cassandra/repository/query/StringBasedCassandraQueryUnitTests.java
                file_name = file_name.split(" ")[0]  # 去除奇葩情况
                if len(file_name.split('/')) >= 2:
                    files_path = ""
                    for file_pre in file_name.split('/')[:-1]:
                        files_path += file_pre + '/'
                    files_path = file_path + '/' + files_path
                    isExists = os.path.exists(files_path)
                    if not isExists:
                        os.makedirs(files_path)  # 创建多层路径
                    file_name = file_name.split('/')[-1]
                    file_name = files_path + file_name
                else:  # 只有一个文件名
                    file_name = file_name.split('/')[-1]
                    file_name = file_path + "/" + file_name
                file_dict['path'] = file_name[1:]  # 去除当前的.
                lines = []
                for i, element in enumerate(elements_pre[1:]):
                    text = element.text.encode('utf-8')  # 消除ascii无法编码的异常
                    with open(file_name, 'a') as f:  # 内容为空的文件表示其实际不存在
                        if i == len(elements_pre[1:]) - 1:
                            f.write(text[1:])  # 去除最后一行多产生的换行
                            if len(text) != 0:  # 防止最后一行是空的情况
                                if text[0] == '-':  # 将修改的行号进行记录
                                    lines.append(i + 1)
                        else:
                            if len(text) != 0:
                                bug_line = i + 1
                                f.write(text[1:] + '\n')  # 当element.text=''时，element.text[1:]不会报异常，结果还是''
                                if text[0] == '-':  # 将修改的行号进行记录
                                    lines.append(i + 1)
                file_dict['lines'] = lines
                file_list.append(file_dict)
            item["file_list"] = file_list
            item["bug_id"] = response.meta["bug_id"]
            browser.close()
            return item
        except Exception, e:
            print Exception, ":", e
            msg = traceback.format_exc()
            print '处理出错', msg
            print bug_line, file_name
            with open('pull_url_bugs.txt', 'a') as f:
                f.write(url + '\n')
                f.write(msg + '\n')
            browser.close()
        # try:
        #     item = SpringspiderItem2()
        #     print 'click:', browser.find_element_by_xpath(
        #         "//*[@class='btn btn-sm btn-outline BtnGroup-item tooltipped tooltipped-s']").text
        #     browser.find_element_by_xpath(
        #         "//*[@class='btn btn-sm btn-outline BtnGroup-item tooltipped tooltipped-s']").click()  # 首先点击split
        #     while len(browser.find_elements_by_xpath('//*[@class="js-expandable-line"]/td')) != 0:  # 点击expandable扩展
        #         # browser.find_element_by_xpath('//*[@class="js-expandable-line"][@data-position="0"]/td').click()
        #         browser.find_element_by_xpath('//*[@class="js-expandable-line"]/td').click()  # 模拟点击的时候不要控制浏览器否则会出错
        #         time.sleep(1)  # 等待页面完全加载
        #     # elements_pre = browser.find_elements_by_xpath("//*[@id='diff-0']/div[2]/div/table/tbody/tr/td[2]")
        #     # elements_cur = browser.find_elements_by_xpath("//*[@id='diff-0']/div[2]/div/table/tbody/tr/td[4]")
        #     elements_pres = browser.find_elements_by_xpath("//*[@id='files']/div/div")  # 获取div[@id='diff-x']
        #     file_list = []
        #     file_path = './' + 'files/' + re.sub('\s', "_", response.meta["project_name"]) + '/' + response.meta[
        #         "bug_id"]
        #     # file_path = './' + 'files'
        #     path_flag = os.path.exists(file_path)
        #     if path_flag is False:
        #         os.makedirs(file_path)  # 建立 ./项目名/bug_id的目录，里面存储可能存在的bug文件。linux下的结构
        #     for elements in elements_pres:
        #         file_dict = {}
        #         elements_pre = elements.find_elements_by_xpath("./div[2]/div/table/tbody/tr/td[2]")
        #         file_name = elements.find_element_by_xpath("./div/div[2]/a").text
        #         file_name = file_name.split('/')[-1]
        #         file_name = file_path + '/' + file_name
        #         file_dict['path'] = file_name[1:]  # 去除当前的.
        #         lines = []
        #         for i, element in enumerate(elements_pre[1:]):
        #             text = element.text.encode('utf-8')  # 消除ascii无法编码的异常
        #             with open(file_name, 'a') as f:
        #                 if i == len(elements_pre[1:]) - 1:
        #                     f.write(text[1:])  # 去除最后一行多产生的换行
        #                     if len(text) != 0:  # 防止最后一行是空的情况
        #                         if text[0] == '-':  # 将修改的行号进行记录
        #                             lines.append(i + 1)
        #                 else:
        #                     if len(text) != 0:
        #                         bug_line = i+1
        #                         f.write(text[1:] + '\n')  # 当element.text=''时，element.text[1:]不会报异常，结果还是''
        #                         if text[0] == '-':  # 将修改的行号进行记录
        #                             lines.append(i + 1)
        #         file_dict['lines'] = lines
        #         file_list.append(file_dict)
        #     item["file_list"] = file_list
        #     item["bug_id"] = response.meta["bug_id"]
        #     browser.close()
        #     return item
        # except Exception, e:
        #     print Exception, ":", e
        #     msg = traceback.format_exc()
        #     print '处理出错', msg
        #     print bug_line
        #     with open('pull_url_bugs.txt', 'a') as f:
        #         f.write(response.url + '\n')
        #         f.write(msg + '\n')
        #     browser.close()

    def retryFindClick(self, by, browser):
        result = False
        attempt = 0
        while attempt <= 10:
            try:
                browser.find_element_by_xpath(by).click()
                result = True
                break
            except StaleElementReferenceException:
                print 'click failed'
            except NoSuchElementException:  # 由于延迟实际上此时已经点击了此按钮
                print "no such element"
                break
        return result
