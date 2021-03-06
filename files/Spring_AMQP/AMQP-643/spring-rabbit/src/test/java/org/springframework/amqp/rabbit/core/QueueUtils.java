/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.amqp.rabbit.core;

import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.Channel;

public final class QueueUtils {

 private QueueUtils() {
  super();
 }

 static void declareTestQueue(RabbitTemplate template, final String routingKey) {
  // declare and bind queue
  template.execute(new ChannelCallback<String>() {
   public String doInRabbit(Channel channel) throws Exception {
    Queue.DeclareOk res = channel.queueDeclarePassive(TestConstants.QUEUE_NAME);
    String queueName = res.getQueue();
    System.out.println("Queue Name = " + queueName);
    channel.queueBind(queueName, TestConstants.EXCHANGE_NAME, routingKey);
    return queueName;
   }
  });
 }

}