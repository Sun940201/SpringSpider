/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.amqp.rabbit.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.test.BrokerRunning;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Dave Syer
 * @author Gary Russell
 * @author Gunnar Hillert
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public final class ExchangeParserIntegrationTests {

 @Rule
 public BrokerRunning brokerIsRunning = BrokerRunning.isRunning();

 @Autowired
 private ConnectionFactory connectionFactory;

 @Autowired
 private Exchange fanoutTest;

 @Autowired
 private Exchange directTest;

 @Autowired
 @Qualifier("bucket")
 private Queue queue;

 @Autowired
 @Qualifier("bucket2")
 private Queue queue2;

 @Test
 public void testBindingsDeclared() throws Exception {

  RabbitTemplate template = new RabbitTemplate(connectionFactory);
  template.convertAndSend(fanoutTest.getName(), "", "message");
  template.convertAndSend(fanoutTest.getName(), queue.getName(), "message");
  Thread.sleep(200);
  // The queue is anonymous so it will be deleted at the end of the test, but it should get the message as long as
  // we use the same connection
  String result = (String) template.receiveAndConvert(queue.getName());
  assertEquals("message", result);
  result = (String) template.receiveAndConvert(queue.getName());
  assertEquals("message", result);
 }

 @Test
 public void testDirectExchangeBindings() throws Exception {

  RabbitTemplate template = new RabbitTemplate(connectionFactory);

  template.convertAndSend(directTest.getName(), queue.getName(), "message");
  Thread.sleep(200);

  String result = (String) template.receiveAndConvert(queue.getName());
  assertEquals("message", result);

  template.convertAndSend(directTest.getName(), "", "message2");
  Thread.sleep(200);

  assertNull(template.receiveAndConvert(queue.getName()));

  result = (String) template.receiveAndConvert(queue2.getName());
  assertEquals("message2", result);

  template.convertAndSend(directTest.getName(), queue2.getName(), "message2");
  Thread.sleep(200);

  assertNull(template.receiveAndConvert(queue2.getName()));
 }

}