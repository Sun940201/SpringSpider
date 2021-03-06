/*
 * Copyright 2011-2014 the original author or authors.
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

package org.springframework.data.redis.listener;

import static org.hamcrest.core.Is.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.ConnectionFactoryTracker;
import org.springframework.data.redis.RedisTestProfileValueSource;
import org.springframework.data.redis.SettingsUtils;
import org.springframework.data.redis.connection.ConnectionUtils;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.jredis.JredisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.srp.SrpConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * @author Costin Leau
 * @author Jennifer Hickey
 * @author Christoph Strobl
 */
@RunWith(Parameterized.class)
public class PubSubResubscribeTests {

 private static final String CHANNEL = "pubsub::test";

 private final BlockingDeque<String> bag = new LinkedBlockingDeque<String>(99);
 private final Object handler = new MessageHandler("handler1", bag);
 private final MessageListenerAdapter adapter = new MessageListenerAdapter(handler);

 private RedisMessageListenerContainer container;
 private RedisConnectionFactory factory;

 @SuppressWarnings("rawtypes")//
 private RedisTemplate template;

 public PubSubResubscribeTests(RedisConnectionFactory connectionFactory) {

  this.factory = connectionFactory;
  ConnectionFactoryTracker.add(factory);
 }

 @BeforeClass
 public static void shouldRun() {
  assumeTrue(RedisTestProfileValueSource.matches("runLongTests", "true"));
 }

 @AfterClass
 public static void cleanUp() {
  ConnectionFactoryTracker.cleanUp();
 }

 @Parameters
 public static Collection<Object[]> testParams() {

  int port = SettingsUtils.getPort();
  String host = SettingsUtils.getHost();
  
  // Jedis
  JedisConnectionFactory jedisConnFactory = new JedisConnectionFactory();
  jedisConnFactory.setUsePool(false);
  jedisConnFactory.setPort(port);
  jedisConnFactory.setHostName(host);
  jedisConnFactory.setDatabase(2);
  jedisConnFactory.afterPropertiesSet();

  // Lettuce
  LettuceConnectionFactory lettuceConnFactory = new LettuceConnectionFactory();
  lettuceConnFactory.setPort(port);
  lettuceConnFactory.setHostName(host);
  lettuceConnFactory.setDatabase(2);
  lettuceConnFactory.setValidateConnection(true);
  lettuceConnFactory.afterPropertiesSet();

  // SRP
  SrpConnectionFactory srpConnFactory = new SrpConnectionFactory();
  srpConnFactory.setPort(port);
  srpConnFactory.setHostName(host);
  srpConnFactory.afterPropertiesSet();

  // JRedis
  JredisConnectionFactory jRedisConnectionFactory = new JredisConnectionFactory();
  jRedisConnectionFactory.setPort(port);
  jRedisConnectionFactory.setHostName(host);
  jRedisConnectionFactory.setDatabase(2);
  jRedisConnectionFactory.afterPropertiesSet();

  return Arrays.asList(new Object[][] { { jedisConnFactory }, { lettuceConnFactory }, { srpConnFactory },
    { jRedisConnectionFactory } });
 }

 @Before
 public void setUp() throws Exception {

  // JredisConnection#publish is currently not supported -> tests would fail
  assumeThat(ConnectionUtils.isJredis(factory), is(false));

  template = new StringRedisTemplate(factory);

  adapter.setSerializer(template.getValueSerializer());
  adapter.afterPropertiesSet();

  container = new RedisMessageListenerContainer();
  container.setConnectionFactory(template.getConnectionFactory());
  container.setBeanName("container");
  container.addMessageListener(adapter, new ChannelTopic(CHANNEL));
  container.setTaskExecutor(new SyncTaskExecutor());
  container.setSubscriptionExecutor(new SimpleAsyncTaskExecutor());
  container.afterPropertiesSet();
  container.start();

  Thread.sleep(1000);
 }

 @After
 public void tearDown() {
  bag.clear();
 }

 @Test
 public void testContainerPatternResubscribe() throws Exception {

  String payload1 = "do";
  String payload2 = "re mi";

  final String PATTERN = "p*";
  final String ANOTHER_CHANNEL = "pubsub::test::extra";

  BlockingDeque<String> bag2 = new LinkedBlockingDeque<String>(99);
  MessageListenerAdapter anotherListener = new MessageListenerAdapter(new MessageHandler("handler2", bag2));
  anotherListener.setSerializer(template.getValueSerializer());
  anotherListener.afterPropertiesSet();

  // remove adapter from all channels
  container.addMessageListener(anotherListener, new PatternTopic(PATTERN));
  container.removeMessageListener(adapter);

  // test no messages are sent just to patterns
  template.convertAndSend(CHANNEL, payload1);
  template.convertAndSend(ANOTHER_CHANNEL, payload2);

  // anotherListener receives both messages
  List<String> msgs = new ArrayList<String>();
  msgs.add(bag2.poll(1, TimeUnit.SECONDS));
  msgs.add(bag2.poll(1, TimeUnit.SECONDS));

  assertEquals(2, msgs.size());
  assertTrue(msgs.contains(payload1));
  assertTrue(msgs.contains(payload2));
  msgs.clear();

  // unsubscribed adapter did not receive message
  assertNull(bag.poll(1, TimeUnit.SECONDS));

  // bind original listener on another channel
  container.addMessageListener(adapter, new ChannelTopic(ANOTHER_CHANNEL));

  template.convertAndSend(CHANNEL, payload1);
  template.convertAndSend(ANOTHER_CHANNEL, payload2);

  // original listener received only one message on another channel
  assertEquals(payload2, bag.poll(1, TimeUnit.SECONDS));
  assertNull(bag.poll(1, TimeUnit.SECONDS));

  // another listener receives messages on both channels
  msgs.add(bag2.poll(1, TimeUnit.SECONDS));
  msgs.add(bag2.poll(1, TimeUnit.SECONDS));
  assertEquals(2, msgs.size());
  assertTrue(msgs.contains(payload1));
  assertTrue(msgs.contains(payload2));
 }

 @Test
 public void testContainerChannelResubscribe() throws Exception {

  String payload1 = "do";
  String payload2 = "re mi";

  String anotherPayload1 = "od";
  String anotherPayload2 = "mi er";

  String ANOTHER_CHANNEL = "pubsub::test::extra";

  // bind listener on another channel
  container.addMessageListener(adapter, new ChannelTopic(ANOTHER_CHANNEL));
  container.removeMessageListener(null, new ChannelTopic(CHANNEL));

  // Listener removed from channel
  template.convertAndSend(CHANNEL, payload1);
  template.convertAndSend(CHANNEL, payload2);

  // Listener receives messages on another channel
  template.convertAndSend(ANOTHER_CHANNEL, anotherPayload1);
  template.convertAndSend(ANOTHER_CHANNEL, anotherPayload2);

  Set<String> set = new LinkedHashSet<String>();
  set.add(bag.poll(1, TimeUnit.SECONDS));
  set.add(bag.poll(1, TimeUnit.SECONDS));

  assertFalse(set.contains(payload1));
  assertFalse(set.contains(payload2));

  assertTrue(set.contains(anotherPayload1));
  assertTrue(set.contains(anotherPayload2));
 }

 /**
  * Validates the behavior of {@link RedisMessageListenerContainer} when it needs to spin up a thread executing its
  * PatternSubscriptionTask
  * 
  * @throws Exception
  */
 @Test
 public void testInitializeContainerWithMultipleTopicsIncludingPattern() throws Exception {

  container.removeMessageListener(adapter);
  container.stop();
  container.addMessageListener(adapter,
    Arrays.asList(new Topic[] { new ChannelTopic(CHANNEL), new PatternTopic("s*") }));
  container.start();

  // Wait for async subscription tasks to setup
  Thread.sleep(1000);

  template.convertAndSend("somechannel", "HELLO");
  template.convertAndSend(CHANNEL, "WORLD");

  Set<String> set = new LinkedHashSet<String>();
  set.add(bag.poll(1, TimeUnit.SECONDS));
  set.add(bag.poll(1, TimeUnit.SECONDS));

  assertEquals(new HashSet<String>(Arrays.asList(new String[] { "HELLO", "WORLD" })), set);
 }

 private class MessageHandler {

  private final BlockingDeque<String> bag;
  private final String name;

  public MessageHandler(String name, BlockingDeque<String> bag) {

   this.bag = bag;
   this.name = name;
  }

  @SuppressWarnings("unused")
  public void handleMessage(String message) {
   bag.add(message);
  }
 }
}