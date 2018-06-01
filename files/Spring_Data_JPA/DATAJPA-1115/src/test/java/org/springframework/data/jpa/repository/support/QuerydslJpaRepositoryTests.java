/*
 * Copyright 2008-2017 the original author or authors.
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
package org.springframework.data.jpa.repository.support;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.sample.Address;
import org.springframework.data.jpa.domain.sample.QUser;
import org.springframework.data.jpa.domain.sample.Role;
import org.springframework.data.jpa.domain.sample.User;
import org.springframework.data.querydsl.QPageRequest;
import org.springframework.data.querydsl.QSort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.PathBuilderFactory;

/**
 * Integration test for {@link QuerydslJpaRepository}.
 * 
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({ "classpath:infrastructure.xml" })
@Transactional
public class QuerydslJpaRepositoryTests {

 @PersistenceContext EntityManager em;

 QuerydslJpaRepository<User, Integer> repository;
 QUser user = new QUser("user");
 User dave, carter, oliver;
 Role adminRole;

 @Before
 public void setUp() {

  JpaEntityInformation<User, Integer> information = new JpaMetamodelEntityInformation<User, Integer>(User.class,
    em.getMetamodel());

  repository = new QuerydslJpaRepository<User, Integer>(information, em);
  dave = repository.save(new User("Dave", "Matthews", "dave@matthews.com"));
  carter = repository.save(new User("Carter", "Beauford", "carter@beauford.com"));
  oliver = repository.save(new User("Oliver", "matthews", "oliver@matthews.com"));
  adminRole = em.merge(new Role("admin"));
 }

 @Test
 public void executesPredicatesCorrectly() throws Exception {

  BooleanExpression isCalledDave = user.firstname.eq("Dave");
  BooleanExpression isBeauford = user.lastname.eq("Beauford");

  List<User> result = repository.findAll(isCalledDave.or(isBeauford));

  assertThat(result.size(), is(2));
  assertThat(result, hasItems(carter, dave));
 }

 @Test
 public void executesStringBasedPredicatesCorrectly() throws Exception {

  PathBuilder<User> builder = new PathBuilderFactory().create(User.class);

  BooleanExpression isCalledDave = builder.getString("firstname").eq("Dave");
  BooleanExpression isBeauford = builder.getString("lastname").eq("Beauford");

  List<User> result = repository.findAll(isCalledDave.or(isBeauford));

  assertThat(result.size(), is(2));
  assertThat(result, hasItems(carter, dave));
 }

 @Test // DATAJPA-243
 public void considersSortingProvidedThroughPageable() {

  Predicate lastnameContainsE = user.lastname.contains("e");

  Page<User> result = repository.findAll(lastnameContainsE, PageRequest.of(0, 1, Direction.ASC, "lastname"));

  assertThat(result.getContent(), hasSize(1));
  assertThat(result.getContent().get(0), is(carter));

  result = repository.findAll(lastnameContainsE, PageRequest.of(0, 2, Direction.DESC, "lastname"));

  assertThat(result.getContent(), hasSize(2));
  assertThat(result.getContent().get(0), is(oliver));
  assertThat(result.getContent().get(1), is(dave));
 }

 @Test // DATAJPA-296
 public void appliesIgnoreCaseOrdering() {

  Sort sort = Sort.by(new Order(Direction.DESC, "lastname").ignoreCase(), new Order(Direction.ASC, "firstname"));

  Page<User> result = repository.findAll(user.lastname.contains("e"), PageRequest.of(0, 2, sort));

  assertThat(result.getContent(), hasSize(2));
  assertThat(result.getContent().get(0), is(dave));
  assertThat(result.getContent().get(1), is(oliver));
 }

 @Test // DATAJPA-427
 public void findBySpecificationWithSortByPluralAssociationPropertyInPageableShouldUseSortNullValuesLast() {

  oliver.getColleagues().add(dave);
  dave.getColleagues().add(oliver);

  QUser user = QUser.user;

  Page<User> page = repository.findAll(user.firstname.isNotNull(),
    PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "colleagues.firstname")));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent(), hasItems(oliver, dave, carter));
 }

 @Test // DATAJPA-427
 public void findBySpecificationWithSortBySingularAssociationPropertyInPageableShouldUseSortNullValuesLast() {

  oliver.setManager(dave);
  dave.setManager(carter);

  QUser user = QUser.user;

  Page<User> page = repository.findAll(user.firstname.isNotNull(),
    PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "manager.firstname")));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent(), hasItems(dave, oliver, carter));
 }

 @Test // DATAJPA-427
 public void findBySpecificationWithSortBySingularPropertyInPageableShouldUseSortNullValuesFirst() {

  QUser user = QUser.user;

  Page<User> page = repository.findAll(user.firstname.isNotNull(),
    PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "firstname")));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent(), hasItems(carter, dave, oliver));
 }

 @Test // DATAJPA-427
 public void findBySpecificationWithSortByOrderIgnoreCaseBySingularPropertyInPageableShouldUseSortNullValuesFirst() {

  QUser user = QUser.user;

  Page<User> page = repository.findAll(user.firstname.isNotNull(),
    PageRequest.of(0, 10, Sort.by(new Order(Sort.Direction.ASC, "firstname").ignoreCase())));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent(), hasItems(carter, dave, oliver));
 }

 @Test // DATAJPA-427
 public void findBySpecificationWithSortByNestedEmbeddedPropertyInPageableShouldUseSortNullValuesFirst() {

  oliver.setAddress(new Address("Germany", "Saarbrücken", "HaveItYourWay", "123"));

  QUser user = QUser.user;

  Page<User> page = repository.findAll(user.firstname.isNotNull(),
    PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "address.streetName")));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent(), hasItems(dave, carter, oliver));
  assertThat(page.getContent().get(2), is(oliver));
 }

 @Test // DATAJPA-12
 public void findBySpecificationWithSortByQueryDslOrderSpecifierWithQPageRequestAndQSort() {

  QUser user = QUser.user;

  Page<User> page = repository.findAll(user.firstname.isNotNull(),
    new QPageRequest(0, 10, new QSort(user.firstname.asc())));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent(), hasItems(carter, dave, oliver));
  assertThat(page.getContent().get(0), is(carter));
  assertThat(page.getContent().get(1), is(dave));
  assertThat(page.getContent().get(2), is(oliver));
 }

 @Test // DATAJPA-12
 public void findBySpecificationWithSortByQueryDslOrderSpecifierWithQPageRequest() {

  QUser user = QUser.user;

  Page<User> page = repository.findAll(user.firstname.isNotNull(), new QPageRequest(0, 10, user.firstname.asc()));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent(), hasItems(carter, dave, oliver));
  assertThat(page.getContent().get(0), is(carter));
  assertThat(page.getContent().get(1), is(dave));
  assertThat(page.getContent().get(2), is(oliver));
 }

 @Test // DATAJPA-12
 public void findBySpecificationWithSortByQueryDslOrderSpecifierForAssociationShouldGenerateLeftJoinWithQPageRequest() {

  oliver.setManager(dave);
  dave.setManager(carter);

  QUser user = QUser.user;

  Page<User> page = repository.findAll(user.firstname.isNotNull(),
    new QPageRequest(0, 10, user.manager.firstname.asc()));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent(), hasItems(carter, dave, oliver));
  assertThat(page.getContent().get(0), is(carter));
  assertThat(page.getContent().get(1), is(dave));
  assertThat(page.getContent().get(2), is(oliver));
 }

 @Test // DATAJPA-491
 public void sortByNestedAssociationPropertyWithSpecificationAndSortInPageable() {

  oliver.setManager(dave);
  dave.getRoles().add(adminRole);

  Page<User> page = repository.findAll(PageRequest.of(0, 10, Sort.by(Direction.ASC, "manager.roles.name")));

  assertThat(page.getContent(), hasSize(3));
  assertThat(page.getContent().get(0), is(dave));
 }

 @Test // DATAJPA-500, DATAJPA-635
 public void sortByNestedEmbeddedAttribite() {

  carter.setAddress(new Address("U", "Z", "Y", "41"));
  dave.setAddress(new Address("U", "A", "Y", "41"));
  oliver.setAddress(new Address("G", "D", "X", "42"));

  List<User> users = repository.findAll(QUser.user.address.streetName.asc());

  assertThat(users, hasSize(3));
  assertThat(users, hasItems(dave, oliver, carter));
 }

 @Test // DATAJPA-566, DATAJPA-635
 public void shouldSupportSortByOperatorWithDateExpressions() {

  carter.setDateOfBirth(new LocalDate(2000, 2, 1).toDate());
  dave.setDateOfBirth(new LocalDate(2000, 1, 1).toDate());
  oliver.setDateOfBirth(new LocalDate(2003, 5, 1).toDate());

  List<User> users = repository.findAll(QUser.user.dateOfBirth.yearMonth().asc());

  assertThat(users, hasSize(3));
  assertThat(users, hasItems(dave, carter, oliver));
 }

 @Test // DATAJPA-665
 public void shouldSupportExistsWithPredicate() throws Exception {

  assertThat(repository.exists(user.firstname.eq("Dave")), is(true));
  assertThat(repository.exists(user.firstname.eq("Unknown")), is(false));
  assertThat(repository.exists((Predicate) null), is(true));
 }

 @Test // DATAJPA-679
 public void shouldSupportFindAllWithPredicateAndSort() {

  List<User> users = repository.findAll(user.dateOfBirth.isNull(), Sort.by(Direction.ASC, "firstname"));

  assertThat(users, hasSize(3));
  assertThat(users.get(0).getFirstname(), is(carter.getFirstname()));
  assertThat(users.get(2).getFirstname(), is(oliver.getFirstname()));
  assertThat(users, hasItems(carter, dave, oliver));
 }

 @Test // DATAJPA-585
 public void worksWithUnpagedPageable() {
  assertThat(repository.findAll(user.dateOfBirth.isNull(), Pageable.unpaged()).getContent(), hasSize(3));
 }

 @Test // DATAJPA-912
 public void pageableQueryReportsTotalFromResult() {

  Page<User> firstPage = repository.findAll(user.dateOfBirth.isNull(), PageRequest.of(0, 10));
  assertThat(firstPage.getContent(), hasSize(3));
  assertThat(firstPage.getTotalElements(), is(3L));

  Page<User> secondPage = repository.findAll(user.dateOfBirth.isNull(), PageRequest.of(1, 2));
  assertThat(secondPage.getContent(), hasSize(1));
  assertThat(secondPage.getTotalElements(), is(3L));
 }

 @Test // DATAJPA-912
 public void pageableQueryReportsTotalFromCount() {

  Page<User> firstPage = repository.findAll(user.dateOfBirth.isNull(), PageRequest.of(0, 3));
  assertThat(firstPage.getContent(), hasSize(3));
  assertThat(firstPage.getTotalElements(), is(3L));

  Page<User> secondPage = repository.findAll(user.dateOfBirth.isNull(), PageRequest.of(10, 10));
  assertThat(secondPage.getContent(), hasSize(0));
  assertThat(secondPage.getTotalElements(), is(3L));
 }
}