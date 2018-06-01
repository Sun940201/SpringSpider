/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.data.jdbc.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.data.convert.Jsr310Converters;
import org.springframework.data.jdbc.core.conversion.AggregateChange;
import org.springframework.data.jdbc.core.conversion.AggregateChange.Kind;
import org.springframework.data.jdbc.core.conversion.Interpreter;
import org.springframework.data.jdbc.core.conversion.JdbcEntityDeleteWriter;
import org.springframework.data.jdbc.core.conversion.JdbcEntityWriter;
import org.springframework.data.jdbc.mapping.event.AfterDelete;
import org.springframework.data.jdbc.mapping.event.AfterSave;
import org.springframework.data.jdbc.mapping.event.BeforeDelete;
import org.springframework.data.jdbc.mapping.event.BeforeSave;
import org.springframework.data.jdbc.mapping.event.Identifier;
import org.springframework.data.jdbc.mapping.event.Identifier.Specified;
import org.springframework.data.jdbc.mapping.model.BasicJdbcPersistentEntityInformation;
import org.springframework.data.jdbc.mapping.model.JdbcMappingContext;
import org.springframework.data.jdbc.mapping.model.JdbcPersistentEntity;
import org.springframework.data.jdbc.mapping.model.JdbcPersistentEntityInformation;
import org.springframework.data.jdbc.mapping.model.JdbcPersistentProperty;
import org.springframework.data.jdbc.support.JdbcUtil;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.util.Assert;

/**
 * {@link JdbcEntityOperations} implementation, storing complete entities including references in a JDBC data store.
 *
 * @author Jens Schauder
 */
public class JdbcEntityTemplate implements JdbcEntityOperations {

 private static final String ENTITY_NEW_AFTER_INSERT = "Entity [%s] still 'new' after insert. Please set either"
   + " the id property in a BeforeInsert event handler, or ensure the database creates a value and your "
   + "JDBC driver returns it.";

 private final ApplicationEventPublisher publisher;
 private final NamedParameterJdbcOperations operations;
 private final JdbcMappingContext context;
 private final ConversionService conversions = getDefaultConversionService();
 private final Interpreter interpreter;
 private final SqlGeneratorSource sqlGeneratorSource;

 private final JdbcEntityWriter jdbcEntityWriter;
 private final JdbcEntityDeleteWriter jdbcEntityDeleteWriter;

 public JdbcEntityTemplate(ApplicationEventPublisher publisher, NamedParameterJdbcOperations operations,
   JdbcMappingContext context) {

  this.publisher = publisher;
  this.operations = operations;
  this.context = context;

  this.jdbcEntityWriter = new JdbcEntityWriter(this.context);
  this.jdbcEntityDeleteWriter = new JdbcEntityDeleteWriter(this.context);
  this.sqlGeneratorSource = new SqlGeneratorSource(this.context);
  this.interpreter = new DefaultJdbcInterpreter(this.context, this);
 }

 private static GenericConversionService getDefaultConversionService() {

  DefaultConversionService conversionService = new DefaultConversionService();
  Jsr310Converters.getConvertersToRegister().forEach(conversionService::addConverter);

  return conversionService;
 }

 @Override
 public <T> void save(T instance, Class<T> domainType) {

  JdbcPersistentEntityInformation<T, ?> entityInformation = context
    .getRequiredPersistentEntityInformation(domainType);

  AggregateChange change = createChange(instance);

  publisher.publishEvent(new BeforeSave( //
    Identifier.ofNullable(entityInformation.getId(instance)), //
    instance, //
    change //
  ));

  change.executeWith(interpreter);

  publisher.publishEvent(new AfterSave( //
    Identifier.of(entityInformation.getId(instance)), //
    instance, //
    change //
  ));
 }

 @Override
 public <T> void insert(T instance, Class<T> domainType, Map<String, Object> additionalParameters) {

  KeyHolder holder = new GeneratedKeyHolder();
  JdbcPersistentEntity<T> persistentEntity = getRequiredPersistentEntity(domainType);
  JdbcPersistentEntityInformation<T, ?> entityInformation = context
    .getRequiredPersistentEntityInformation(domainType);

  MapSqlParameterSource parameterSource = getPropertyMap(instance, persistentEntity);

  Object idValue = getIdValueOrNull(instance, persistentEntity);
  JdbcPersistentProperty idProperty = persistentEntity.getRequiredIdProperty();
  parameterSource.addValue(idProperty.getColumnName(), convert(idValue, idProperty.getColumnType()),
    JdbcUtil.sqlTypeFor(idProperty.getColumnType()));

  additionalParameters.forEach(parameterSource::addValue);

  operations.update(sql(domainType).getInsert(idValue == null, additionalParameters.keySet()), parameterSource,
    holder);

  setIdFromJdbc(instance, holder, persistentEntity);

  if (entityInformation.isNew(instance)) {
   throw new IllegalStateException(String.format(ENTITY_NEW_AFTER_INSERT, persistentEntity));
  }

 }

 @Override
 public <S> void update(S instance, Class<S> domainType) {

  JdbcPersistentEntity<S> persistentEntity = getRequiredPersistentEntity(domainType);

  operations.update(sql(domainType).getUpdate(), getPropertyMap(instance, persistentEntity));
 }

 @SuppressWarnings("ConstantConditions")
 @Override
 public long count(Class<?> domainType) {
  return operations.getJdbcOperations().queryForObject(sql(domainType).getCount(), Long.class);
 }

 @Override
 public <T> T findById(Object id, Class<T> domainType) {

  String findOneSql = sql(domainType).getFindOne();
  MapSqlParameterSource parameter = createIdParameterSource(id, domainType);
  return operations.queryForObject(findOneSql, parameter, getEntityRowMapper(domainType));
 }

 @Override
 public <T> boolean existsById(Object id, Class<T> domainType) {

  String existsSql = sql(domainType).getExists();
  MapSqlParameterSource parameter = createIdParameterSource(id, domainType);
  return operations.queryForObject(existsSql, parameter, Boolean.class);
 }

 @Override
 public <T> Iterable<T> findAll(Class<T> domainType) {
  return operations.query(sql(domainType).getFindAll(), getEntityRowMapper(domainType));
 }

 @Override
 public <T> Iterable<T> findAllById(Iterable<?> ids, Class<T> domainType) {

  String findAllInListSql = sql(domainType).getFindAllInList();
  Class<?> targetType = getRequiredPersistentEntity(domainType).getRequiredIdProperty().getColumnType();

  MapSqlParameterSource parameter = new MapSqlParameterSource( //
    "ids", //
    StreamSupport.stream(ids.spliterator(), false) //
      .map(id -> convert(id, targetType)) //
      .collect(Collectors.toList()) //
  );

  return operations.query(findAllInListSql, parameter, getEntityRowMapper(domainType));
 }

 @Override
 public <T> Iterable<T> findAllByProperty(Object id, JdbcPersistentProperty property) {

  Class<?> actualType = property.getActualType();
  String findAllByProperty = sql(actualType).getFindAllByProperty(property.getReverseColumnName());

  MapSqlParameterSource parameter = new MapSqlParameterSource(property.getReverseColumnName(), id);

  return (Iterable<T>) operations.query(findAllByProperty, parameter, getEntityRowMapper(actualType));
 }

 @Override
 public <S> void delete(S entity, Class<S> domainType) {

  JdbcPersistentEntityInformation<S, ?> entityInformation = context
    .getRequiredPersistentEntityInformation(domainType);
  deleteTree(entityInformation.getRequiredId(entity), entity, domainType);
 }

 @Override
 public <S> void deleteById(Object id, Class<S> domainType) {
  deleteTree(id, null, domainType);
 }

 @Override
 public void deleteAll(Class<?> domainType) {

  AggregateChange change = createDeletingChange(domainType);
  change.executeWith(interpreter);
 }

 private void deleteTree(Object id, Object entity, Class<?> domainType) {

  AggregateChange change = createDeletingChange(id, entity, domainType);

  Specified specifiedId = Identifier.of(id);
  Optional<Object> optionalEntity = Optional.ofNullable(entity);
  publisher.publishEvent(new BeforeDelete(specifiedId, optionalEntity, change));

  change.executeWith(interpreter);

  publisher.publishEvent(new AfterDelete(specifiedId, optionalEntity, change));

 }

 void doDelete(Object rootId, PropertyPath propertyPath) {

  JdbcPersistentEntity<?> rootEntity = context.getRequiredPersistentEntity(propertyPath.getOwningType());

  JdbcPersistentProperty referencingProperty = rootEntity.getRequiredPersistentProperty(propertyPath.getSegment());
  Assert.notNull(referencingProperty, "No property found matching the PropertyPath " + propertyPath);

  String format = sql(rootEntity.getType()).createDeleteByPath(propertyPath);

  HashMap<String, Object> parameters = new HashMap<>();
  parameters.put("rootId", rootId);
  operations.update(format, parameters);

 }

 void doDelete(Object id, Class<?> domainType) {

  String deleteByIdSql = sql(domainType).getDeleteById();
  MapSqlParameterSource parameter = createIdParameterSource(id, domainType);

  operations.update(deleteByIdSql, parameter);
 }

 private <T> AggregateChange createChange(T instance) {

  AggregateChange aggregateChange = new AggregateChange(Kind.SAVE, instance.getClass(), instance);
  jdbcEntityWriter.write(instance, aggregateChange);
  return aggregateChange;
 }

 private AggregateChange createDeletingChange(Object id, Object entity, Class<?> domainType) {

  AggregateChange aggregateChange = new AggregateChange(Kind.DELETE, domainType, entity);
  jdbcEntityDeleteWriter.write(id, aggregateChange);
  return aggregateChange;
 }

 private AggregateChange createDeletingChange(Class<?> domainType) {

  AggregateChange aggregateChange = new AggregateChange(Kind.DELETE, domainType, null);
  jdbcEntityDeleteWriter.write(null, aggregateChange);
  return aggregateChange;
 }

 private <T> MapSqlParameterSource createIdParameterSource(Object id, Class<T> domainType) {
  return new MapSqlParameterSource("id",
    convert(id, getRequiredPersistentEntity(domainType).getRequiredIdProperty().getColumnType()));
 }

 private <S> MapSqlParameterSource getPropertyMap(final S instance, JdbcPersistentEntity<S> persistentEntity) {

  MapSqlParameterSource parameters = new MapSqlParameterSource();

  persistentEntity.doWithProperties((PropertyHandler<JdbcPersistentProperty>) property -> {
   if (!property.isEntity()) {
    Object value = persistentEntity.getPropertyAccessor(instance).getProperty(property);

    Object convertedValue = convert(value, property.getColumnType());
    parameters.addValue(property.getColumnName(), convertedValue, JdbcUtil.sqlTypeFor(property.getColumnType()));
   }
  });

  return parameters;
 }

 private <S, ID> ID getIdValueOrNull(S instance, JdbcPersistentEntity<S> persistentEntity) {

  EntityInformation<S, ID> entityInformation = new BasicJdbcPersistentEntityInformation<>(persistentEntity);

  ID idValue = entityInformation.getId(instance);

  return isIdPropertySimpleTypeAndValueZero(idValue, persistentEntity) ? null : idValue;
 }

 private <S> void setIdFromJdbc(S instance, KeyHolder holder, JdbcPersistentEntity<S> persistentEntity) {

  JdbcPersistentEntityInformation<S, ?> entityInformation = new BasicJdbcPersistentEntityInformation<>(
    persistentEntity);

  try {

   getIdFromHolder(holder, persistentEntity).ifPresent(it -> {

    Class<?> targetType = persistentEntity.getRequiredIdProperty().getType();
    Object converted = convert(it, targetType);
    entityInformation.setId(instance, converted);
   });

  } catch (NonTransientDataAccessException e) {
   throw new UnableToSetId("Unable to set id of " + instance, e);
  }
 }

 private <S> Optional<Object> getIdFromHolder(KeyHolder holder, JdbcPersistentEntity<S> persistentEntity) {

  try {
   // MySQL just returns one value with a special name
   return Optional.ofNullable(holder.getKey());
  } catch (InvalidDataAccessApiUsageException e) {
   // Postgres returns a value for each column
   return Optional.ofNullable(holder.getKeys().get(persistentEntity.getIdColumn()));
  }
 }

 private <V> V convert(Object from, Class<V> to) {

  if (from == null) {
   return null;
  }

  JdbcPersistentEntity<?> persistentEntity = context.getPersistentEntity(from.getClass());

  Object id = persistentEntity == null ? null : persistentEntity.getIdentifierAccessor(from).getIdentifier();

  return conversions.convert(id == null ? from : id, to);
 }

 private <S, ID> boolean isIdPropertySimpleTypeAndValueZero(ID idValue, JdbcPersistentEntity<S> persistentEntity) {

  JdbcPersistentProperty idProperty = persistentEntity.getIdProperty();
  return idValue == null //
    || idProperty == null //
    || (idProperty.getType() == int.class && idValue.equals(0)) //
    || (idProperty.getType() == long.class && idValue.equals(0L));
 }

 @SuppressWarnings("unchecked")
 private <S> JdbcPersistentEntity<S> getRequiredPersistentEntity(Class<S> domainType) {
  return (JdbcPersistentEntity<S>) context.getRequiredPersistentEntity(domainType);
 }

 private SqlGenerator sql(Class<?> domainType) {
  return sqlGeneratorSource.getSqlGenerator(domainType);
 }

 private <T> EntityRowMapper<T> getEntityRowMapper(Class<T> domainType) {
  return new EntityRowMapper<>(getRequiredPersistentEntity(domainType), conversions, context, this);
 }

 <T> void doDeleteAll(Class<T> domainType, PropertyPath propertyPath) {

  operations.getJdbcOperations()
    .update(sql(propertyPath == null ? domainType : propertyPath.getOwningType().getType())
      .createDeleteAllSql(propertyPath));
 }

 public NamedParameterJdbcOperations getOperations() {
  return operations;
 }
}