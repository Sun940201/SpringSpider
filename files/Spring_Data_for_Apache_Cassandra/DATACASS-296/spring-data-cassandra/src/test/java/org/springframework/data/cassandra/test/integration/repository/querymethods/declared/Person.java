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
package org.springframework.data.cassandra.test.integration.repository.querymethods.declared;

import java.util.Date;

import lombok.Data;

import org.springframework.cassandra.core.PrimaryKeyType;
import org.springframework.data.cassandra.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.mapping.Table;

/**
 * Sample domain class.
 */
@Table
@Data
public class Person {

 @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 0) private String lastname;

 @PrimaryKeyColumn(type = PrimaryKeyType.CLUSTERED, ordinal = 1) private String firstname;

 private String nickname;
 private Date birthDate;
 private int numberOfChildren;
 private boolean cool;
}