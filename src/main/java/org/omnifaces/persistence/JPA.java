/*
 * Copyright 2015 OmniFaces.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence;

import static java.util.stream.Collectors.toList;
import static org.omnifaces.persistence.Database.POSTGRESQL;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.utils.reflect.Reflections.findClass;
import static org.omnifaces.utils.stream.Collectors.toMap;
import static org.omnifaces.utils.stream.Streams.stream;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CollectionJoin;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.SetJoin;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.CollectionAttribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;

public final class JPA {

	public static final String LOAD_GRAPH_HINT_KEY = "javax.persistence.loadgraph";
	public static final String FETCH_GRAPH_HINT_KEY = "javax.persistence.fetchgraph";
	public static final String CACHE_RETRIEVE_MODE_HINT_KEY = "javax.persistence.cache.retrieveMode";

	private static final Optional<Class<Object>> HIBERNATE_PROXY_CLASS = findClass("org.hibernate.proxy.HibernateProxy");

	private JPA() {
		//
	}

	public static <T> T getOptionalSingleResult(TypedQuery<T> query) {
		try {
			query.setMaxResults(1);
			return query.getSingleResult();
		}
		catch (NoResultException e) {
			return null;
		}
	}

	public static <T> Optional<T> getOptional(TypedQuery<T> query) {
		try {
			query.setMaxResults(1);
			return Optional.of(query.getSingleResult());
		}
		catch (NoResultException e) {
			return Optional.empty();
		}
	}

	public static <K, T> Map<K, T> getResultMap(TypedQuery<T> query, Function<? super T, ? extends K> keyMapper) {
		return query.getResultList()
					.stream()
					.collect(toMap(keyMapper));
	}

	public static <K, T, V> Map<K, V> getResultMap(TypedQuery<T> query, Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
		return query.getResultList()
					.stream()
					.collect(Collectors.toMap(keyMapper, valueMapper));
	}

	public static <T> T getOptionalSingleResult(Query query, Class<T> type) {
		try {
			query.setMaxResults(1);
			return type.cast(query.getSingleResult());
		}
		catch (NoResultException e) {
			return null;
		}
	}

	public static <T, I> Long getForeignKeyReferences(Class<T> entityClass, Class<I> idType, I entityId, EntityManager entityManager) {
		Metamodel metamodel = entityManager.getMetamodel();
		EntityType<T> entityType = metamodel.entity(entityClass);
		SingularAttribute<? super T, I> idAttribute = entityType.getId(idType);

		return metamodel.getEntities()
						.stream()
						.flatMap(entity -> getAttributesOfType(entity, entityClass))
						.distinct()
						.mapToLong(attribute -> countReferencesTo(entityManager, attribute, idAttribute, entityId))
						.sum();
	}

	private static <T, E> Stream<Attribute<?, ?>> getAttributesOfType(EntityType<T> entityType, Class<E> entityClass) {
		return entityType.getAttributes()
						 .stream()
						 .filter(attribute -> {
							 if (attribute instanceof PluralAttribute) {
								 return entityClass.equals(((PluralAttribute<?, ?, ?>) attribute).getElementType().getJavaType());
							 }

							 return entityClass.equals(attribute.getJavaType());
						 })
						 .map(attribute -> attribute);
	}

	@SuppressWarnings("unchecked")
	private static <R, T, I> Long countReferencesTo(EntityManager entityManager, Attribute<R, ?> attribute, SingularAttribute<? super T, I> idAttribute, I id) {
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
		Root<R> root = query.from(attribute.getDeclaringType().getJavaType());

		if (attribute instanceof SingularAttribute) {
			Join<R, T> relation = root.join((SingularAttribute<R, T>) attribute);
			query.where(criteriaBuilder.equal(relation.get(idAttribute), id));
		}
		else if (attribute instanceof ListAttribute) {
			ListJoin<R, T> relation = root.join((ListAttribute<R, T>) attribute);
			query.where(criteriaBuilder.equal(relation.get(idAttribute), id));
		}
		else if (attribute instanceof SetAttribute) {
			SetJoin<R, T> relation = root.join((SetAttribute<R, T>) attribute);
			query.where(criteriaBuilder.equal(relation.get(idAttribute), id));
		}
		else if (attribute instanceof MapAttribute) {
			MapJoin<R, ?, T> relation = root.join((MapAttribute<R, ?, T>) attribute);
			query.where(criteriaBuilder.equal(relation.get(idAttribute), id));
		}
		else if (attribute instanceof CollectionAttribute) {
			CollectionJoin<R, T> relation = root.join((CollectionAttribute<R, T>) attribute);
			query.where(criteriaBuilder.equal(relation.get(idAttribute), id));
		}
		else {
			// Unknown attribute type, just return 0L.
			return 0L;
		}

		query.select(criteriaBuilder.count(root));
		return entityManager.createQuery(query).getSingleResult();
	}

	@SuppressWarnings("unchecked")
	public static Expression<String> concat(CriteriaBuilder builder, Object... expressionsOrStrings) {
		if (expressionsOrStrings.length < 2) {
			throw new IllegalArgumentException("There must be at least 2 expressions or strings");
		}

		List<Expression<? extends Object>> expressions = stream(expressionsOrStrings).map(expressionOrString -> {
			if (expressionOrString instanceof Expression) {
				return castAsString(builder, (Expression<?>) expressionOrString);
			}
			else {
				return builder.literal(expressionOrString);
			}
		}).collect(toList());

		return builder.function("CONCAT", String.class, expressions.toArray(new Expression[expressions.size()]));
	}

	@SuppressWarnings("unchecked")
	public static Expression<String> castAsString(CriteriaBuilder builder, Expression<?> expression) {
		boolean numeric = Number.class.isAssignableFrom(expression.getJavaType());

		if (!numeric || Provider.is(HIBERNATE)) { // EclipseLink and OpenJPA have a broken Path#as() implementation, need to delegate to DB specific function.
			return expression.as(String.class);
		}
		else if (Database.is(POSTGRESQL)) {
			return builder.function("TO_CHAR", String.class, expression, builder.literal("FM999999999999999999"));
		}
		else { // H2 and MySQL are more lenient in this, no function call necessary.
			return (Expression<String>) expression;
		}
	}

}
