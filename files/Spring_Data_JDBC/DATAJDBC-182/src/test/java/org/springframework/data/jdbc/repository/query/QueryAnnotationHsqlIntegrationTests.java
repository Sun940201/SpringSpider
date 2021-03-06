/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.data.jdbc.repository.query;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jdbc.testing.TestConfiguration;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the execution of queries from {@link Query} annotations on repository methods.
 *
 * @author Jens Schauder
 * @author Kazuki Shimizu
 */
@ContextConfiguration
@Transactional
public class QueryAnnotationHsqlIntegrationTests {

 @Autowired
 DummyEntityRepository repository;

 @ClassRule
 public static final SpringClassRule classRule = new SpringClassRule();
 @Rule
 public SpringMethodRule methodRule = new SpringMethodRule();

 @Test // DATAJDBC-164
 public void executeCustomQueryWithoutParameter() {

  repository.save(dummyEntity("Example"));
  repository.save(dummyEntity("example"));
  repository.save(dummyEntity("EXAMPLE"));

  List<DummyEntity> entities = repository.findByNameContainingCapitalLetter();

  assertThat(entities) //
    .extracting(e -> e.name) //
    .containsExactlyInAnyOrder("Example", "EXAMPLE");

 }

 @Test // DATAJDBC-164
 public void executeCustomQueryWithNamedParameters() {

  repository.save(dummyEntity("a"));
  repository.save(dummyEntity("b"));
  repository.save(dummyEntity("c"));

  List<DummyEntity> entities = repository.findByNamedRangeWithNamedParameter("a", "c");

  assertThat(entities) //
    .extracting(e -> e.name) //
    .containsExactlyInAnyOrder("b");

 }

 @Test // DATAJDBC-172
 public void executeCustomQueryWithReturnTypeIsOptional() {

  repository.save(dummyEntity("a"));

  Optional<DummyEntity> entity = repository.findByNameAsOptional("a");

  assertThat(entity).map(e -> e.name).contains("a");

 }

 @Test // DATAJDBC-172
 public void executeCustomQueryWithReturnTypeIsOptionalWhenEntityNotFound() {

  repository.save(dummyEntity("a"));

  Optional<DummyEntity> entity = repository.findByNameAsOptional("x");

  assertThat(entity).isNotPresent();

 }

 @Test // DATAJDBC-172
 public void executeCustomQueryWithReturnTypeIsEntity() {

  repository.save(dummyEntity("a"));

  DummyEntity entity = repository.findByNameAsEntity("a");

  assertThat(entity).isNotNull();
  assertThat(entity.name).isEqualTo("a");

 }

 @Test // DATAJDBC-172
 public void executeCustomQueryWithReturnTypeIsEntityWhenEntityNotFound() {

  repository.save(dummyEntity("a"));

  DummyEntity entity = repository.findByNameAsEntity("x");

  assertThat(entity).isNull();

 }

 @Test // DATAJDBC-172
 public void executeCustomQueryWithReturnTypeIsEntityWhenEntityDuplicateResult() {

  repository.save(dummyEntity("a"));
  repository.save(dummyEntity("a"));

  assertThatExceptionOfType(DataAccessException.class) //
    .isThrownBy(() -> repository.findByNameAsEntity("a"));
 }

 @Test // DATAJDBC-172
 public void executeCustomQueryWithReturnTypeIsOptionalWhenEntityDuplicateResult() {

  repository.save(dummyEntity("a"));
  repository.save(dummyEntity("a"));

  assertThatExceptionOfType(DataAccessException.class) //
    .isThrownBy(() -> repository.findByNameAsOptional("a"));
 }

 @Test // DATAJDBC-172
 public void executeCustomQueryWithReturnTypeIsStream() {

  repository.save(dummyEntity("a"));
  repository.save(dummyEntity("b"));

  Stream<DummyEntity> entities = repository.findAllWithReturnTypeIsStream();

  assertThat(entities) //
    .extracting(e -> e.name) //
    .containsExactlyInAnyOrder("a", "b");

 }
 
 @Test // DATAJDBC-175
 public void executeCustomQueryWithReturnTypeIsNubmer() {

  repository.save(dummyEntity("aaa"));
  repository.save(dummyEntity("bbb"));
  repository.save(dummyEntity("cac"));

  int count = repository.countByNameContaining("a");

  assertThat(count).isEqualTo(2);

 }

 @Test // DATAJDBC-175
 public void executeCustomQueryWithReturnTypeIsBoolean() {

  repository.save(dummyEntity("aaa"));
  repository.save(dummyEntity("bbb"));
  repository.save(dummyEntity("cac"));

  assertThat(repository.existsByNameContaining("a")).isTrue();
  assertThat(repository.existsByNameContaining("d")).isFalse();

 }

 @Test // DATAJDBC-175
 public void executeCustomQueryWithReturnTypeIsDate() {

  Date now = new Date();
  assertThat(repository.nowWithDate()).isAfterOrEqualsTo(now);

 }

 @Test // DATAJDBC-175
 public void executeCustomQueryWithReturnTypeIsLocalDateTimeList() {

  LocalDateTime now = LocalDateTime.now();
  repository.nowWithLocalDateTimeList() //
    .forEach(d -> assertThat(d).isAfterOrEqualTo(now));

 }

 private DummyEntity dummyEntity(String name) {

  DummyEntity entity = new DummyEntity();
  entity.name = name;
  return entity;
 }

 @Configuration
 @Import(TestConfiguration.class)
 @EnableJdbcRepositories(considerNestedRepositories = true)
 static class Config {

  @Bean
  Class<?> testClass() {
   return QueryAnnotationHsqlIntegrationTests.class;
  }
 }

 private static class DummyEntity {

  @Id
  Long id;

  String name;
 }

 private interface DummyEntityRepository extends CrudRepository<DummyEntity, Long> {

  @Query("SELECT * FROM DUMMYENTITY WHERE lower(name) <> name")
  List<DummyEntity> findByNameContainingCapitalLetter();

  @Query("SELECT * FROM DUMMYENTITY WHERE name  < :upper and name > :lower")
  List<DummyEntity> findByNamedRangeWithNamedParameter(@Param("lower") String lower, @Param("upper") String upper);

  @Query("SELECT * FROM DUMMYENTITY WHERE name = :name")
  Optional<DummyEntity> findByNameAsOptional(@Param("name") String name);

  @Query("SELECT * FROM DUMMYENTITY WHERE name = :name")
  DummyEntity findByNameAsEntity(@Param("name") String name);

  @Query("SELECT * FROM DUMMYENTITY")
  Stream<DummyEntity> findAllWithReturnTypeIsStream();

  @Query("SELECT count(*) FROM DUMMYENTITY WHERE name like '%' || :name || '%'")
  int countByNameContaining(@Param("name") String name);

  @Query("SELECT count(*) FROM DUMMYENTITY WHERE name like '%' || :name || '%'")
  boolean existsByNameContaining(@Param("name") String name);

  @Query("VALUES (current_timestamp)")
  Date nowWithDate();

  @Query("VALUES (current_timestamp),(current_timestamp)")
  List<LocalDateTime> nowWithLocalDateTimeList();

 }
}