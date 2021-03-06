/*
 * Copyright 2014-2016 the original author or authors.
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
package org.springframework.data.cassandra.test.integration.repository.cdi;

import java.util.HashMap;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.springframework.cassandra.core.cql.CqlIdentifier;
import org.springframework.cassandra.core.keyspace.CreateKeyspaceSpecification;
import org.springframework.cassandra.core.keyspace.DropKeyspaceSpecification;
import org.springframework.cassandra.support.RandomKeySpaceName;
import org.springframework.cassandra.test.integration.support.CassandraConnectionProperties;
import org.springframework.data.cassandra.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.CassandraAdminTemplate;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.test.integration.repository.simple.User;

import com.datastax.driver.core.Cluster;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Service;

/**
 * @author Mark Paluch
 */
class CassandraOperationsProducer {

 public final static String KEYSPACE_NAME = RandomKeySpaceName.create();

 @Produces
 @Singleton
 public Cluster createCluster() throws Exception {
  CassandraConnectionProperties properties = new CassandraConnectionProperties();

  Cluster cluster = Cluster.builder().addContactPoint(properties.getCassandraHost())
    .withPort(properties.getCassandraPort()).build();
  return cluster;
 }

 @Produces
 @ApplicationScoped
 public CassandraOperations createCassandraOperations(Cluster cluster) throws Exception {

  MappingCassandraConverter cassandraConverter = new MappingCassandraConverter();

  CassandraAdminTemplate cassandraTemplate = new CassandraAdminTemplate(cluster.connect(), cassandraConverter);

  CreateKeyspaceSpecification createKeyspaceSpecification = new CreateKeyspaceSpecification(KEYSPACE_NAME)
    .ifNotExists();
  cassandraTemplate.execute(createKeyspaceSpecification);
  cassandraTemplate.execute("USE " + KEYSPACE_NAME);

  cassandraTemplate.createTable(true, CqlIdentifier.cqlId("users"), User.class, new HashMap<String, Object>());

  for (CassandraPersistentEntity<?> entity : cassandraTemplate.getConverter().getMappingContext()
    .getPersistentEntities()) {
   cassandraTemplate.truncate(entity.getTableName());
  }

  return cassandraTemplate;
 }

 @OtherQualifier
 @UserDB
 @Produces
 @ApplicationScoped
 public CassandraOperations createQualifiedCassandraOperations(CassandraOperations cassandraOperations) {
  return cassandraOperations;
 }

 public void close(@Disposes CassandraOperations cassandraOperations) {

  cassandraOperations.execute(DropKeyspaceSpecification.dropKeyspace(KEYSPACE_NAME));
  cassandraOperations.getSession().close();
 }

 public void close(@Disposes Cluster cluster) {
  cluster.close();
 }

 @Produces
 public Set<Service> producerToSatisfyGuavaDependenciesWhenTesting() {
  return Sets.newHashSet();
 }

}