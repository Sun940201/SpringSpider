/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.data.cassandra.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.cassandra.core.cql.CqlIdentifier;
import org.springframework.data.cassandra.core.cql.generator.CreateTableCqlGenerator;
import org.springframework.data.cassandra.core.cql.generator.CreateUserTypeCqlGenerator;
import org.springframework.data.cassandra.core.cql.keyspace.CreateTableSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.CreateUserTypeSpecification;
import org.springframework.data.cassandra.core.mapping.BasicCassandraPersistentEntity;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentProperty;
import org.springframework.util.Assert;

/**
 * Schema creation support for Cassandra based on {@link CassandraMappingContext} and {@link CassandraPersistentEntity}.
 * This class generates CQL to create user types (UDT) and tables.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 * @since 1.5
 * @see org.springframework.data.cassandra.core.mapping.Table
 * @see org.springframework.data.cassandra.core.mapping.UserDefinedType
 * @see org.springframework.data.cassandra.core.mapping.CassandraType
 */
public class CassandraPersistentEntitySchemaCreator {

 private final CassandraAdminOperations cassandraAdminOperations;

 private final CassandraMappingContext mappingContext;

 /**
  * Create a new {@link CassandraPersistentEntitySchemaCreator} for the given {@link CassandraMappingContext} and
  * {@link CassandraAdminOperations}.
  *
  * @param mappingContext must not be {@literal null}.
  * @param cassandraAdminOperations must not be {@literal null}.
  */
 public CassandraPersistentEntitySchemaCreator(CassandraMappingContext mappingContext,
   CassandraAdminOperations cassandraAdminOperations) {

  Assert.notNull(cassandraAdminOperations, "CassandraAdminOperations must not be null");
  Assert.notNull(mappingContext, "CassandraMappingContext must not be null");

  this.cassandraAdminOperations = cassandraAdminOperations;
  this.mappingContext = mappingContext;
 }

 /**
  * Create tables from types known to {@link CassandraMappingContext}.
  *
  * @param ifNotExists {@literal true} to create tables using {@code IF NOT EXISTS}.
  */
 public void createTables(boolean ifNotExists) {

  createTableSpecifications(ifNotExists).forEach(specification -> cassandraAdminOperations.getCqlOperations()
    .execute(CreateTableCqlGenerator.toCql(specification)));
 }

 /**
  * Create {@link List} of {@link CreateTableSpecification}.
  *
  * @param ifNotExists {@literal true} to create tables using {@code IF NOT EXISTS}.
  * @return {@link List} of {@link CreateTableSpecification}.
  */
 protected List<CreateTableSpecification> createTableSpecifications(boolean ifNotExists) {

  return mappingContext.getTableEntities().stream()
    .map(entity -> mappingContext.getCreateTableSpecificationFor(entity).ifNotExists(ifNotExists))
    .collect(Collectors.toList());
 }

 /**
  * Create user types from types known to {@link CassandraMappingContext}.
  *
  * @param ifNotExists {@literal true} to create types using {@code IF NOT EXISTS}.
  */
 public void createUserTypes(boolean ifNotExists) {

  createUserTypeSpecifications(ifNotExists) //
    .forEach(specification -> cassandraAdminOperations.getCqlOperations() //
      .execute(CreateUserTypeCqlGenerator.toCql(specification)));
 }

 /**
  * Create {@link List} of {@link CreateUserTypeSpecification}.
  *
  * @param ifNotExists {@literal true} to create types using {@code IF NOT EXISTS}.
  * @return {@link List} of {@link CreateUserTypeSpecification}.
  */
 protected List<CreateUserTypeSpecification> createUserTypeSpecifications(boolean ifNotExists) {

  Collection<? extends CassandraPersistentEntity<?>> entities = new ArrayList<>(
    mappingContext.getUserDefinedTypeEntities());

  Map<CqlIdentifier, CassandraPersistentEntity<?>> byTableName = entities.stream()
    .collect(Collectors.toMap(CassandraPersistentEntity::getTableName, entity -> entity));

  List<CreateUserTypeSpecification> specifications = new ArrayList<>();

  Set<CqlIdentifier> created = new HashSet<>();
  entities.forEach(entity -> {

   Set<CqlIdentifier> seen = new LinkedHashSet<>();

   seen.add(entity.getTableName());
   visitUserTypes(entity, seen);

   List<CqlIdentifier> ordered = new ArrayList<>(seen);
   Collections.reverse(ordered);

   specifications.addAll(ordered.stream()
     .filter(created::add).map(identifier -> mappingContext
       .getCreateUserTypeSpecificationFor(byTableName.get(identifier)).ifNotExists(ifNotExists))
     .collect(Collectors.toList()));
  });

  return specifications;
 }

 private void visitUserTypes(CassandraPersistentEntity<?> entity, final Set<CqlIdentifier> seen) {

  for (CassandraPersistentProperty property : entity) {

   BasicCassandraPersistentEntity<?> persistentEntity = mappingContext.getPersistentEntity(property);

   if (persistentEntity == null) {
    continue;
   }

   if (persistentEntity.isUserDefinedType() && seen.add(persistentEntity.getTableName())) {
    visitUserTypes(persistentEntity, seen);
   }
  }
 }
}