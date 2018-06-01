/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.data.redis.core;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisKeyValueAdapter.EnableKeyspaceEvents;
import org.springframework.data.redis.core.convert.Bucket;
import org.springframework.data.redis.core.convert.KeyspaceConfiguration;
import org.springframework.data.redis.core.convert.MappingConfiguration;
import org.springframework.data.redis.core.convert.RedisData;
import org.springframework.data.redis.core.convert.SimpleIndexedPropertyValue;
import org.springframework.data.redis.core.index.IndexConfiguration;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;

/**
 * Unit tests for {@link RedisKeyValueAdapter}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@RunWith(MockitoJUnitRunner.class)
public class RedisKeyValueAdapterUnitTests {

 RedisKeyValueAdapter adapter;
 RedisTemplate<?, ?> template;
 RedisMappingContext context;
 @Mock JedisConnectionFactory jedisConnectionFactoryMock;
 @Mock RedisConnection redisConnectionMock;

 @Before
 public void setUp() throws Exception {

  template = new RedisTemplate<Object, Object>();
  template.setConnectionFactory(jedisConnectionFactoryMock);
  template.afterPropertiesSet();

  when(jedisConnectionFactoryMock.getConnection()).thenReturn(redisConnectionMock);
  when(redisConnectionMock.getConfig("notify-keyspace-events"))
    .thenReturn(Arrays.asList("notify-keyspace-events", "KEA"));

  context = new RedisMappingContext(new MappingConfiguration(new IndexConfiguration(), new KeyspaceConfiguration()));
  context.afterPropertiesSet();

  adapter = new RedisKeyValueAdapter(template, context);
  adapter.afterPropertiesSet();
 }

 @After
 public void tearDown() throws Exception {
  adapter.destroy();
 }

 /**
  * @see DATAREDIS-507
  */
 @Test
 public void destroyShouldNotDestroyConnectionFactory() throws Exception {

  adapter.destroy();

  verify(jedisConnectionFactoryMock, never()).destroy();
 }

 /**
  * @see DATAREDIS-512
  */
 @Test
 public void putShouldRemoveExistingIndexValuesWhenUpdating() {

  RedisData rd = new RedisData(Bucket.newBucketFromStringMap(Collections.singletonMap("_id", "1")));
  rd.addIndexedData(new SimpleIndexedPropertyValue("persons", "firstname", "rand"));

  when(redisConnectionMock.keys(any(byte[].class)))
    .thenReturn(new LinkedHashSet<byte[]>(Arrays.asList("persons:firstname:rand".getBytes())));
  when(redisConnectionMock.del((byte[][]) anyVararg())).thenReturn(1L);

  adapter.put("1", rd, "persons");

  verify(redisConnectionMock, times(1)).sRem(any(byte[].class), any(byte[].class));
 }

 /**
  * @see DATAREDIS-512
  */
 @Test
 public void putShouldNotTryToRemoveExistingIndexValuesWhenInsertingNew() {

  RedisData rd = new RedisData(Bucket.newBucketFromStringMap(Collections.singletonMap("_id", "1")));
  rd.addIndexedData(new SimpleIndexedPropertyValue("persons", "firstname", "rand"));

  when(redisConnectionMock.sMembers(any(byte[].class)))
    .thenReturn(new LinkedHashSet<byte[]>(Arrays.asList("persons:firstname:rand".getBytes())));
  when(redisConnectionMock.del((byte[][]) anyVararg())).thenReturn(0L);

  adapter.put("1", rd, "persons");

  verify(redisConnectionMock, never()).sRem(any(byte[].class), (byte[][]) anyVararg());
 }

 /**
  * @see DATAREDIS-491
  */
 @Test
 public void shouldInitKeyExpirationListenerOnStartup() throws Exception{

  adapter.destroy();

  adapter = new RedisKeyValueAdapter(template, context);
  adapter.setEnableKeyspaceEvents(EnableKeyspaceEvents.ON_STARTUP);
  adapter.afterPropertiesSet();

  KeyExpirationEventMessageListener listener = ((AtomicReference<KeyExpirationEventMessageListener>) getField(adapter,
    "expirationListener")).get();
  assertThat(listener, notNullValue());
 }

 /**
  * @see DATAREDIS-491
  */
 @Test
 public void shouldInitKeyExpirationListenerOnFirstPutWithTtl() throws Exception {

  adapter.destroy();

  adapter = new RedisKeyValueAdapter(template, context);
  adapter.setEnableKeyspaceEvents(EnableKeyspaceEvents.ON_DEMAND);
  adapter.afterPropertiesSet();

  KeyExpirationEventMessageListener listener = ((AtomicReference<KeyExpirationEventMessageListener>) getField(adapter,
    "expirationListener")).get();
  assertThat(listener, nullValue());

  adapter.put("should-NOT-start-listener", new WithoutTimeToLive(), "keyspace");

  listener = ((AtomicReference<KeyExpirationEventMessageListener>) getField(adapter, "expirationListener")).get();
  assertThat(listener, nullValue());

  adapter.put("should-start-listener", new WithTimeToLive(), "keyspace");

  listener = ((AtomicReference<KeyExpirationEventMessageListener>) getField(adapter, "expirationListener")).get();
  assertThat(listener, notNullValue());
 }

 /**
  * @see DATAREDIS-491
  */
 @Test
 public void shouldNeverInitKeyExpirationListener() throws Exception {

  adapter.destroy();

  adapter = new RedisKeyValueAdapter(template, context);
  adapter.afterPropertiesSet();

  KeyExpirationEventMessageListener listener = ((AtomicReference<KeyExpirationEventMessageListener>) getField(adapter,
    "expirationListener")).get();
  assertThat(listener, nullValue());

  adapter.put("should-NOT-start-listener", new WithoutTimeToLive(), "keyspace");

  listener = ((AtomicReference<KeyExpirationEventMessageListener>) getField(adapter, "expirationListener")).get();
  assertThat(listener, nullValue());

  adapter.put("should-start-listener", new WithTimeToLive(), "keyspace");

  listener = ((AtomicReference<KeyExpirationEventMessageListener>) getField(adapter, "expirationListener")).get();
  assertThat(listener, nullValue());
 }

 static class WithoutTimeToLive {
  @Id String id;
 }

 @RedisHash(timeToLive = 10)
 static class WithTimeToLive {
  @Id String id;
 }
}