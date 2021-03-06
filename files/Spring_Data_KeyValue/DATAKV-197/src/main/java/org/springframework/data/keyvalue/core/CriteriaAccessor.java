/*
 * Copyright 2014-2017 the original author or authors.
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
package org.springframework.data.keyvalue.core;

import org.springframework.data.keyvalue.core.query.KeyValueQuery;

/**
 * Resolves the criteria object from given {@link KeyValueQuery}.
 *
 * @author Christoph Strobl
 * @param <T>
 */
public interface CriteriaAccessor<T> {

 /**
  * Checks and reads {@link KeyValueQuery#getCriteria()} of given {@link KeyValueQuery}. Might also apply additional
  * transformation to match the desired type.
  *
  * @param query can be {@literal null}.
  * @return the criteria extracted from the query.
  * @throws IllegalArgumentException in case the criteria is not valid for usage with specific
  *           {@link CriteriaAccessor}.
  */
 T resolve(KeyValueQuery<?> query);
}