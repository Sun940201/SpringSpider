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
package org.springframework.data.cassandra.repository.query;

import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.ParametersParameterAccessor;

import com.datastax.driver.core.DataType;

/**
 * Cassandra-specific {@link ParameterAccessor} exposing a Cassandra {@link DataType types} that are supported by the
 * driver and parameter type.
 *
 * @author Mark Paluch
 */
public class CassandraParametersParameterAccessor extends ParametersParameterAccessor
  implements CassandraParameterAccessor {

 /**
  * Creates a new {@link CassandraParametersParameterAccessor}.
  * 
  * @param method must not be {@literal null}.
  * @param values must not be {@literal null}.
  */
 public CassandraParametersParameterAccessor(CassandraQueryMethod method, Object... values) {
  super(method.getParameters(), values);
 }

 /**
  * Returns the Cassandra {@link DataType} for the declared parameter if the type is a
  * {@link org.springframework.data.cassandra.mapping.CassandraSimpleTypeHolder simple type}. Parameter types may be
  * specified using {@link org.springframework.data.cassandra.mapping.CassandraType}.
  *
  * @param index parameter index
  * @return the Cassandra {@link DataType} or {@literal null} if the parameter type cannot be determined from
  *         {@link org.springframework.data.cassandra.mapping.CassandraSimpleTypeHolder}
  */
 public DataType getDataType(int index) {
  return getParameters().getParameter(index).getCassandraType();
 }

 /* (non-Javadoc)
  * @see org.springframework.data.cassandra.repository.query.CassandraParameterAccessor#getParameterType(int)
  */
 @Override
 public Class<?> getParameterType(int index) {
  return getParameters().getParameter(index).getType();
 }

 /* (non-Javadoc)
  * @see org.springframework.data.repository.query.ParametersParameterAccessor#getParameters()
  */
 @Override
 public CassandraParameters getParameters() {
  return (CassandraParameters) super.getParameters();
 }

}