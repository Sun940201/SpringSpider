/*
 * Copyright (c)  [2011-2016] "Pivotal Software, Inc." / "Neo Technology" / "Graph Aware Ltd."
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with
 * separate copyright notices and license terms. Your use of the source
 * code for these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 *
 */
package org.springframework.data.neo4j.repository;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.testutil.MultiDriverTestClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.domain.sample.SampleEntity;
import org.springframework.data.neo4j.repository.config.EnableExperimentalNeo4jRepositories;
import org.springframework.data.neo4j.repository.support.Neo4jRepositoryFactory;
import org.springframework.data.neo4j.repository.support.TransactionalRepositoryIT;
import org.springframework.data.neo4j.transaction.Neo4jTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Mark Angrish
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = Neo4jRepositoryIT.Config.class)
@Transactional
public class Neo4jRepositoryIT extends MultiDriverTestClass {

 @Autowired Session session;

 Neo4jRepository<SampleEntity> repository;

 @Before
 public void setUp() {

  repository = new Neo4jRepositoryFactory(session).getRepository(SampleEntityRepository.class);
 }

 @Test
 public void testCrudOperationsForCompoundKeyEntity() throws Exception {

  SampleEntity entity = new SampleEntity("foo", "bar");
  repository.save(entity);
  assertThat(repository.exists(entity.getId()), is(true));
  assertThat(repository.count(), is(1L));
  assertThat(repository.findOne(entity.getId()), is(entity));

  repository.delete(Arrays.asList(entity));
  assertThat(repository.count(), is(0L));
 }


 private interface SampleEntityRepository extends Neo4jRepository<SampleEntity> {

 }

 @Configuration
 @EnableExperimentalNeo4jRepositories
 @EnableTransactionManagement
 public static class Config {

  @Bean
  public TransactionalRepositoryIT.DelegatingTransactionManager transactionManager() throws Exception {
   return new TransactionalRepositoryIT.DelegatingTransactionManager(new Neo4jTransactionManager(sessionFactory()));
  }

  @Bean
  public SessionFactory sessionFactory() {
   return new SessionFactory("org.springframework.data.neo4j.domain.sample");
  }
 }
}