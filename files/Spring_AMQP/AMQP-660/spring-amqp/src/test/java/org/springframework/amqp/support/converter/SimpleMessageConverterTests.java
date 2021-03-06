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

package org.springframework.amqp.support.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * @author Mark Fisher
 * @author Gary Russell
 */
public class SimpleMessageConverterTests extends WhiteListDeserializingMessageConverterTests {

 @Test
 public void bytesAsDefaultMessageBodyType() throws Exception {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  Message message = new Message("test".getBytes(), new MessageProperties());
  Object result = converter.fromMessage(message);
  assertEquals(byte[].class, result.getClass());
  assertEquals("test", new String((byte[]) result, "UTF-8"));
 }

 @Test
 public void noMessageIdByDefault() throws Exception {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  Message message = converter.toMessage("foo", null);
  assertNull(message.getMessageProperties().getMessageId());
 }

 @Test
 public void optionalMessageId() throws Exception {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  converter.setCreateMessageIds(true);
  Message message = converter.toMessage("foo", null);
  assertNotNull(message.getMessageProperties().getMessageId());
 }

 @Test
 public void messageToString() {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  Message message = new Message("test".getBytes(), new MessageProperties());
  message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
  Object result = converter.fromMessage(message);
  assertEquals(String.class, result.getClass());
  assertEquals("test", result);
 }

 @Test
 public void messageToBytes() {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  Message message = new Message(new byte[] { 1, 2, 3 }, new MessageProperties());
  message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_BYTES);
  Object result = converter.fromMessage(message);
  assertEquals(byte[].class, result.getClass());
  byte[] resultBytes = (byte[]) result;
  assertEquals(3, resultBytes.length);
  assertEquals(1, resultBytes[0]);
  assertEquals(2, resultBytes[1]);
  assertEquals(3, resultBytes[2]);
 }

 @Test
 public void messageToSerializedObject() throws Exception {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  MessageProperties properties = new MessageProperties();
  properties.setContentType(MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT);
  ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
  ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
  TestBean testBean = new TestBean("foo");
  objectStream.writeObject(testBean);
  objectStream.flush();
  objectStream.close();
  byte[] bytes = byteStream.toByteArray();
  Message message = new Message(bytes, properties);
  Object result = converter.fromMessage(message);
  assertEquals(TestBean.class, result.getClass());
  assertEquals(testBean, result);
 }

 @Test
 public void stringToMessage() throws Exception {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  Message message = converter.toMessage("test", new MessageProperties());
  String contentType = message.getMessageProperties().getContentType();
  String content = new String(message.getBody(),
    message.getMessageProperties().getContentEncoding());
  assertEquals("text/plain", contentType);
  assertEquals("test", content);
 }

 @Test
 public void bytesToMessage() throws Exception {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  Message message = converter.toMessage(new byte[] { 1, 2, 3 }, new MessageProperties());
  String contentType = message.getMessageProperties().getContentType();
  byte[] body = message.getBody();
  assertEquals("application/octet-stream", contentType);
  assertEquals(3, body.length);
  assertEquals(1, body[0]);
  assertEquals(2, body[1]);
  assertEquals(3, body[2]);
 }

 @Test
 public void serializedObjectToMessage() throws Exception {
  SimpleMessageConverter converter = new SimpleMessageConverter();
  TestBean testBean = new TestBean("foo");
  Message message = converter.toMessage(testBean, new MessageProperties());
  String contentType = message.getMessageProperties().getContentType();
  byte[] body = message.getBody();
  assertEquals("application/x-java-serialized-object", contentType);
  ByteArrayInputStream bais = new ByteArrayInputStream(body);
  Object deserializedObject = new ObjectInputStream(bais).readObject();
  assertEquals(testBean, deserializedObject);
 }

}