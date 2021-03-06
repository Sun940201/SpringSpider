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
package org.springframework.data.mongodb.core

import kotlin.reflect.KClass

/**
 * Extension for [ExecutableFindOperation.query] providing a [KClass] based variant.
 *
 * @author Sebastien Deleuze
 * @since 2.0
 */
fun <T : Any> ExecutableFindOperation.query(entityClass: KClass<T>): ExecutableFindOperation.FindOperation<T> =
  query(entityClass.java)

/**
 * Extension for [ExecutableFindOperation.query] leveraging reified type parameters.
 *
 * @author Sebastien Deleuze
 * @since 2.0
 */
inline fun <reified T : Any> ExecutableFindOperation.query(): ExecutableFindOperation.FindOperation<T> =
  query(T::class.java)


/**
 * Extension for [ExecutableFindOperation.FindOperationWithProjection.as] providing a [KClass] based variant.
 *
 * @author Sebastien Deleuze
 * @since 2.0
 */
fun <T : Any> ExecutableFindOperation.FindOperationWithProjection<T>.asType(resultType: KClass<T>): ExecutableFindOperation.FindOperationWithQuery<T> =
  `as`(resultType.java)

/**
 * Extension for [ExecutableFindOperation.FindOperationWithProjection.as] leveraging reified type parameters.
 *
 * @author Sebastien Deleuze
 * @since 2.0
 */
inline fun <reified T : Any> ExecutableFindOperation.FindOperationWithProjection<T>.asType(): ExecutableFindOperation.FindOperationWithQuery<T> =
  `as`(T::class.java)

