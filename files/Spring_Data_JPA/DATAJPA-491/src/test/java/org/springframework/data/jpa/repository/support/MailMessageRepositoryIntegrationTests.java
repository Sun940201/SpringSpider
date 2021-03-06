/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jpa.repository.support;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.jpa.domain.JpaSort.*;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.sample.MailMessage;
import org.springframework.data.jpa.domain.sample.MailMessage_;
import org.springframework.data.jpa.domain.sample.MailSender;
import org.springframework.data.jpa.domain.sample.MailSender_;
import org.springframework.data.jpa.domain.sample.QMailMessage;
import org.springframework.data.jpa.domain.sample.QMailSender;
import org.springframework.data.jpa.repository.sample.MailMessageRepository;
import org.springframework.data.jpa.repository.sample.SampleConfig;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link MailMessageRepository}.
 * 
 * @author Thomas Darimont
 * @author Oliver Gierke
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SampleConfig.class)
@Transactional
public class MailMessageRepositoryIntegrationTests {

 static final QMailMessage message = QMailMessage.mailMessage;
 static final QMailSender sender = QMailSender.mailSender;

 @Autowired MailMessageRepository mailMessageRepository;

 /**
  * @see DATAJPA-12
  */
 @Test
 public void shouldSortMailWithPageRequestAndJpaSortCriteriaNullsFirst() {

  MailMessage message1 = new MailMessage();
  message1.setContent("abc");
  MailSender sender1 = new MailSender("foo");
  message1.setMailSender(sender1);

  MailMessage message2 = new MailMessage();
  message2.setContent("abc");

  mailMessageRepository.save(message1);
  mailMessageRepository.save(message2);

  Page<MailMessage> results = mailMessageRepository.findAll(new PageRequest(0, 20, //
    new JpaSort(Direction.ASC, path(MailMessage_.mailSender).dot(MailSender_.name))));
  List<MailMessage> messages = results.getContent();

  assertThat(messages, hasSize(2));
  assertThat(messages.get(0).getMailSender(), is(nullValue()));
  assertThat(messages.get(1).getMailSender(), is(sender1));
 }

 /**
  * @see DATAJPA-12
  */
 @Test
 public void shouldSortMailWithQueryDslRepositoryAndDslSortCriteriaNullsFirst() {

  MailMessage message1 = new MailMessage();
  message1.setContent("abc");
  MailSender sender1 = new MailSender("foo");
  message1.setMailSender(sender1);

  MailMessage message2 = new MailMessage();
  message2.setContent("abc");

  mailMessageRepository.save(message1);
  mailMessageRepository.save(message2);

  List<MailMessage> messages = mailMessageRepository.findAll(message.content.eq("abc"), message.mailSender.name.asc());

  assertThat(messages, hasSize(2));
  assertThat(messages.get(0).getMailSender(), is(nullValue()));
  assertThat(messages.get(1).getMailSender(), is(sender1));
 }
}