/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.test.util;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.List;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matcher;

import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import static org.hamcrest.MatcherAssert.*;
import static org.springframework.test.util.AssertionErrors.*;

/**
 * A helper class for applying assertions via JSON path expressions.
 *
 * <p>Based on the <a href="https://github.com/jayway/JsonPath">JsonPath</a>
 * project: requiring version 0.9+, with 1.1+ strongly recommended.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.2
 */
public class JsonPathExpectationsHelper {

 private static Method compileMethod;

 private static Object emptyFilters;

 static {
  // Reflective bridging between JsonPath 0.9.x and 1.x
  for (Method candidate : JsonPath.class.getMethods()) {
   if (candidate.getName().equals("compile")) {
    Class<?>[] paramTypes = candidate.getParameterTypes();
    if (paramTypes.length == 2 && String.class == paramTypes[0] && paramTypes[1].isArray()) {
     compileMethod = candidate;
     emptyFilters = Array.newInstance(paramTypes[1].getComponentType(), 0);
     break;
    }
   }
  }
  Assert.state(compileMethod != null, "Unexpected JsonPath API - no compile(String, ...) method found");
 }


 private final String expression;

 private final JsonPath jsonPath;


 /**
  * Construct a new JsonPathExpectationsHelper.
  * @param expression the JsonPath expression
  * @param args arguments to parameterize the JSON path expression with
  * formatting specifiers defined in {@link String#format(String, Object...)}
  */
 public JsonPathExpectationsHelper(String expression, Object... args) {
  this.expression = String.format(expression, args);
  this.jsonPath = (JsonPath) ReflectionUtils.invokeMethod(
    compileMethod, null, this.expression, emptyFilters);
 }


 /**
  * Evaluate the JSON path and assert the resulting value with the given {@code Matcher}.
  * @param content the response content
  * @param matcher the matcher to assert on the resulting json path
  */
 @SuppressWarnings("unchecked")
 public <T> void assertValue(String content, Matcher<T> matcher) throws ParseException {
  T value = (T) evaluateJsonPath(content);
  assertThat("JSON path " + this.expression, value, matcher);
 }

 private Object evaluateJsonPath(String content) throws ParseException  {
  String message = "No value for JSON path: " + this.expression + ", exception: ";
  try {
   return this.jsonPath.read(content);
  }
  catch (InvalidPathException ex) {
   throw new AssertionError(message + ex.getMessage());
  }
  catch (ArrayIndexOutOfBoundsException ex) {
   throw new AssertionError(message + ex.getMessage());
  }
  catch (IndexOutOfBoundsException ex) {
   throw new AssertionError(message + ex.getMessage());
  }
 }

 /**
  * Apply the JSON path and assert the resulting value.
  */
 public void assertValue(String responseContent, Object expectedValue) throws ParseException {
  Object actualValue = evaluateJsonPath(responseContent);
  if ((actualValue instanceof List) && !(expectedValue instanceof List)) {
   @SuppressWarnings("rawtypes")
   List actualValueList = (List) actualValue;
   if (actualValueList.isEmpty()) {
    fail("No matching value for JSON path \"" + this.expression + "\"");
   }
   if (actualValueList.size() != 1) {
    fail("Got a list of values " + actualValue + " instead of the value " + expectedValue);
   }
   actualValue = actualValueList.get(0);
  }
  else if (actualValue != null && expectedValue != null) {
   assertEquals("For JSON path " + this.expression + " type of value",
     expectedValue.getClass(), actualValue.getClass());
  }
  assertEquals("JSON path " + this.expression, expectedValue, actualValue);
 }

 /**
  * Apply the JSON path and assert the resulting value is an array.
  */
 public void assertValueIsArray(String responseContent) throws ParseException {
  Object actualValue = evaluateJsonPath(responseContent);
  assertTrue("No value for JSON path \"" + this.expression + "\"", actualValue != null);
  String reason = "Expected array at JSON path " + this.expression + " but found " + actualValue;
  assertTrue(reason, actualValue instanceof List);
 }

 /**
  * Evaluate the JSON path and assert the resulting content exists.
  */
 public void exists(String content) throws ParseException {
  Object value = evaluateJsonPath(content);
  String reason = "No value for JSON path " + this.expression;
  assertTrue(reason, value != null);
  if (List.class.isInstance(value)) {
   assertTrue(reason, !((List<?>) value).isEmpty());
  }
 }

 /**
  * Evaluate the JSON path and assert it doesn't point to any content.
  */
 public void doesNotExist(String content) throws ParseException {
  Object value;
  try {
   value = evaluateJsonPath(content);
  }
  catch (AssertionError ex) {
   return;
  }
  String reason = String.format("Expected no value for JSON path: %s but found: %s", this.expression, value);
  if (List.class.isInstance(value)) {
   assertTrue(reason, ((List<?>) value).isEmpty());
  }
  else {
   assertTrue(reason, value == null);
  }
 }

}