/*
 * Copyright 2011-2017 the original author or authors.
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
package org.springframework.data.mapping.model;

import lombok.AccessLevel;
import lombok.Getter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.annotation.Reference;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.util.Lazy;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Simple implementation of {@link PersistentProperty}.
 *
 * @author Jon Brisbin
 * @author Oliver Gierke
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public abstract class AbstractPersistentProperty<P extends PersistentProperty<P>> implements PersistentProperty<P> {

 private static final Field CAUSE_FIELD;

 static {
  CAUSE_FIELD = ReflectionUtils.findField(Throwable.class, "cause");
 }

 private final String name;
 private final TypeInformation<?> information;
 private final Class<?> rawType;
 private final Lazy<Association<P>> association;
 private final @Getter PersistentEntity<?, P> owner;
 private final @Getter(AccessLevel.PROTECTED) Property property;
 private final Lazy<Integer> hashCode;
 private final Lazy<Boolean> usePropertyAccess;
 private final Lazy<Optional<? extends TypeInformation<?>>> entityTypeInformation;

 private final Method getter;
 private final Method setter;
 private final Field field;

 public AbstractPersistentProperty(Property property, PersistentEntity<?, P> owner,
   SimpleTypeHolder simpleTypeHolder) {

  Assert.notNull(simpleTypeHolder, "SimpleTypeHolder must not be null!");
  Assert.notNull(owner, "Owner entity must not be null!");

  this.name = property.getName();
  this.rawType = property.getType();
  this.information = PropertyPath.from(Pattern.quote(property.getName()), owner.getTypeInformation())
    .getTypeInformation();
  this.property = property;
  this.association = Lazy.of(() -> isAssociation() ? createAssociation() : null);
  this.owner = owner;

  this.hashCode = Lazy.of(property::hashCode);
  this.usePropertyAccess = Lazy.of(() -> owner.getType().isInterface() || CAUSE_FIELD.equals(getField()));
  this.entityTypeInformation = Lazy.of(() -> Optional.ofNullable(information.getActualType())//
    .filter(it -> !simpleTypeHolder.isSimpleType(it.getType()))//
    .filter(it -> !it.isCollectionLike())//
    .filter(it -> !it.isMap()));

  this.getter = property.getGetter().orElse(null);
  this.setter = property.getSetter().orElse(null);
  this.field = property.getField().orElse(null);
 }

 protected abstract Association<P> createAssociation();

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getName()
  */
 @Override
 public String getName() {
  return name;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getType()
  */
 @Override
 public Class<?> getType() {
  return information.getType();
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getRawType()
  */
 @Override
 public Class<?> getRawType() {
  return this.rawType;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getTypeInformation()
  */
 @Override
 public TypeInformation<?> getTypeInformation() {
  return information;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getPersistentEntityType()
  */
 @Override
 public Iterable<? extends TypeInformation<?>> getPersistentEntityType() {

  if (!isEntity()) {
   return Collections.emptySet();
  }

  return entityTypeInformation.get()//
    .map(Collections::singleton)//
    .orElseGet(Collections::emptySet);
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getGetter()
  */
 @Override
 public Method getGetter() {
  return getter;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getSetter()
  */
 @Override
 public Method getSetter() {
  return setter;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getField()
  */
 @Override
 public Field getField() {
  return field;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getSpelExpression()
  */
 @Override
 public String getSpelExpression() {
  return null;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#isTransient()
  */
 @Override
 public boolean isTransient() {
  return false;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#isWritable()
  */
 @Override
 public boolean isWritable() {
  return !isTransient();
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#isAssociation()
  */
 @Override
 public boolean isAssociation() {
  return isAnnotationPresent(Reference.class);
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getAssociation()
  */
 @Override
 public Association<P> getAssociation() {
  return association.get();
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#isCollectionLike()
  */
 @Override
 public boolean isCollectionLike() {
  return information.isCollectionLike();
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#isMap()
  */
 @Override
 public boolean isMap() {
  return Map.class.isAssignableFrom(getType());
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#isArray()
  */
 @Override
 public boolean isArray() {
  return getType().isArray();
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#isEntity()
  */
 @Override
 public boolean isEntity() {
  return !isTransient() && entityTypeInformation.get().isPresent();
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getComponentType()
  */
 @Override
 public Class<?> getComponentType() {
  return isMap() || isCollectionLike() ? information.getRequiredComponentType().getType() : null;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getMapValueType()
  */
 @Override
 public Class<?> getMapValueType() {

  if (isMap()) {

   TypeInformation<?> mapValueType = information.getMapValueType();
   if (mapValueType != null) {
    return mapValueType.getType();
   }
  }

  return null;
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mapping.PersistentProperty#getActualType()
  */
 @Override
 public Class<?> getActualType() {
  return information.getActualType().getType();
 }

 /*
  * (non-Javadoc)
  * @see org.springframework.data.mongodb.core.mapping.MongoPersistentProperty#usePropertyAccess()
  */
 public boolean usePropertyAccess() {
  return usePropertyAccess.get();
 }

 /*
  * (non-Javadoc)
  * @see java.lang.Object#equals(java.lang.Object)
  */
 @Override
 public boolean equals(Object obj) {

  if (this == obj) {
   return true;
  }

  if (!(obj instanceof AbstractPersistentProperty)) {
   return false;
  }

  AbstractPersistentProperty<?> that = (AbstractPersistentProperty<?>) obj;

  return this.property.equals(that.property);
 }

 /*
  * (non-Javadoc)
  * @see java.lang.Object#hashCode()
  */
 @Override
 public int hashCode() {
  return this.hashCode.get();
 }

 /*
  * (non-Javadoc)
  * @see java.lang.Object#toString()
  */
 @Override
 public String toString() {
  return property.toString();
 }
}