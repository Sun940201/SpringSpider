/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springframework.data.elasticsearch.repository.cdi;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.support.ElasticsearchRepositoryFactory;
import org.springframework.data.repository.cdi.CdiRepositoryBean;
import org.springframework.util.Assert;

/**
 * Uses CdiRepositoryBean to create ElasticsearchRepository instances.
 *
 * @author Rizwan Idrees
 * @author Mohsin Husen
 * @author Mark Paluch
 */
public class ElasticsearchRepositoryBean<T> extends CdiRepositoryBean<T> {

 private final Bean<ElasticsearchOperations> elasticsearchOperationsBean;

 /**
  * Creates a new {@link ElasticsearchRepositoryBean}.
  *
  * @param operations must not be {@literal null}.
  * @param qualifiers must not be {@literal null}.
  * @param repositoryType must not be {@literal null}.
  * @param beanManager must not be {@literal null}.
  * @param customImplementationBean the bean for the custom implementation of the
  * {@link org.springframework.data.repository.Repository}, can be {@literal null}.
  */
 public ElasticsearchRepositoryBean(Bean<ElasticsearchOperations> operations, Set<Annotation> qualifiers,
            Class<T> repositoryType, BeanManager beanManager, Bean<?> customImplementationBean) {
  super(qualifiers, repositoryType, beanManager, customImplementationBean);

  Assert.notNull(operations, "Cannot create repository with 'null' for ElasticsearchOperations.");
  this.elasticsearchOperationsBean = operations;
 }

 @Override
 protected T create(CreationalContext<T> creationalContext, Class<T> repositoryType, Object customImplementation) {
  ElasticsearchOperations elasticsearchOperations = getDependencyInstance(elasticsearchOperationsBean,
    ElasticsearchOperations.class);
  return new ElasticsearchRepositoryFactory(elasticsearchOperations).getRepository(repositoryType, customImplementation);
 }

 @Override
 public Class<? extends Annotation> getScope() {
  return elasticsearchOperationsBean.getScope();
 }
}