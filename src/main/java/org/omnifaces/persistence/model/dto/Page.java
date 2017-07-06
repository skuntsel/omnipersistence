package org.omnifaces.persistence.model.dto;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.utils.Lang.isEmpty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * This class basically defines a paged view of a database based on a given offset, limit, ordering, required criteria
 * and optional criteria. This is used by {@link BaseEntityService#getPage(Page, boolean)} methods.
 *
 * @author Bauke Scholtz
 * @see BaseEntityService
 * @see Criteria
 */
public final class Page { // This class should NOT be mutable!

	// Constants ------------------------------------------------------------------------------------------------------

	public final static Page ALL = Page.of(0, MAX_VALUE);
	public final static Page ONE = Page.of(0, 1);


	// Properties -----------------------------------------------------------------------------------------------------

	private final int offset;
	private final int limit;
	private final Map<String, Boolean> ordering;
	private final Map<String, Object> requiredCriteria;
	private final Map<String, Object> optionalCriteria;


	// Constructors ---------------------------------------------------------------------------------------------------

	/**
	 * Creates a new Page. You can for convenience also use the {@link Page#with()} builder.
	 * @param offset Zero-based offset of the page. May not be negative. Defaults to 0.
	 * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
	 * @param ordering Ordering of results. Map key represents property name and map value represents whether to sort ascending. Defaults to <code>{"id",false}</code>.
	 * @param requiredCriteria Required criteria. Map key represents property name and map value represents criteria. Each entity must match all of given criteria.
	 * @param optionalCriteria Optional criteria. Map key represents property name and map value represents criteria. Each entity must match at least one of given criteria.
	 */
	public Page(Integer offset, Integer limit, LinkedHashMap<String, Boolean> ordering, Map<String, Object> requiredCriteria, Map<String, Object> optionalCriteria) {
		this.offset = validateIntegerArgument("offset", offset, 0, 0);
		this.limit = validateIntegerArgument("limit", limit, 1, MAX_VALUE);
		this.ordering = !isEmpty(ordering) ? unmodifiableMap(ordering) : singletonMap(ID, false);
		this.requiredCriteria = requiredCriteria != null ? unmodifiableMap(requiredCriteria) : emptyMap();
		this.optionalCriteria = optionalCriteria != null ? unmodifiableMap(optionalCriteria) : emptyMap();
	}

	private static int validateIntegerArgument(String argumentName, Integer argumentValue, int minValue, int defaultValue) {
		if (argumentValue == null) {
			return defaultValue;
		}

		if (argumentValue < minValue) {
			throw new IllegalArgumentException("Argument " + argumentName + " may not be less than " + minValue);
		}

		return argumentValue;
	}


	// Getters --------------------------------------------------------------------------------------------------------

	/**
	 * Returns the offset. Defaults to 0.
	 * @return The offset.
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * Returns the limit. Defaults to {@link Integer#MAX_VALUE}.
	 * @return The limit.
	 */
	public int getLimit() {
		return limit;
	}

	/**
	 * Returns the ordering. Map key represents property name and map value represents whether to sort ascending. Defaults to <code>{"id",false}</code>.
	 * @return The ordering.
	 */
	public Map<String, Boolean> getOrdering() {
		return ordering;
	}

	/**
	 * Returns the required criteria. Map key represents property name and map value represents criteria. Each entity must match all of given criteria.
	 * @return The required criteria.
	 */
	public Map<String, Object> getRequiredCriteria() {
		return requiredCriteria;
	}

	/**
	 * Returns the optional criteria. Map key represents property name and map value represents criteria. Each entity must match at least one of given criteria.
	 * @return The optional criteria.
	 */
	public Map<String, Object> getOptionalCriteria() {
		return optionalCriteria;
	}


	// Object overrides -----------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object object) {
		if (!(object instanceof Page)) {
			return false;
		}

		if (object == this) {
			return true;
		}

		Page other = (Page) object;

		return Objects.equals(offset, other.offset)
			&& Objects.equals(limit, other.limit)
			&& Objects.equals(ordering, other.ordering)
			&& Objects.equals(requiredCriteria, other.requiredCriteria)
			&& Objects.equals(optionalCriteria, other.optionalCriteria);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Page.class, offset, limit, ordering, requiredCriteria, optionalCriteria);
	}

	/**
	 * Identity is important as this is used as value of <code>org.hibernate.cacheRegion</code> hint in
	 * {@link BaseEntityService#getPage(Page, boolean)}. Hence the criteria hashmaps are printed as treemaps.
	 */
	@Override
	public String toString() {
		return new StringBuilder("Page[")
			.append(offset).append(",")
			.append(limit).append(",")
			.append(ordering).append(",")
			.append(new TreeMap<>(requiredCriteria)).append(",")
			.append(new TreeMap<>(optionalCriteria)).append("]").toString();
	}


	// Builder --------------------------------------------------------------------------------------------------------

	/**
	 * Use this if you want to build a page.
	 * @return A new page builder.
	 */
	public static Builder with() {
		return new Builder();
	}

	/**
	 * Use this if you want a page of given offset and limit.
	 * @param offset Zero-based offset of the page. May not be negative. Defaults to 0.
	 * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
	 * @return A new page of given offset and limit.
	 */
	public static Page of(int offset, int limit) {
		return with().range(offset, limit).build();
	}

	/**
	 * The page builder. Use {@link Page#with()} to get started.
	 * @author Bauke Scholtz
	 */
	public static class Builder {

		private Integer offset;
		private Integer limit;
		private LinkedHashMap<String, Boolean> ordering = new LinkedHashMap<>(2);
		private Map<String, Object> requiredCriteria;
		private Map<String, Object> optionalCriteria;

		/**
		 * Set the range.
		 * @param offset Zero-based offset of the page. May not be negative. Defaults to 0.
		 * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
		 * @throws IllegalStateException When another offset and limit is already set in this builder.
		 * @return This builder.
		 */
		public Builder range(int offset, int limit) {
			if (this.offset != null) {
				throw new IllegalStateException("Offset and limit are already set");
			}

			this.offset = offset;
			this.limit = limit;
			return this;
		}

		/**
		 * Set the ordering. This can be invoked multiple times and will be remembered in same order. The default ordering is <code>{"id",false}</code>.
		 * @param field The field.
		 * @param ascending Whether to sort ascending.
		 * @return This builder.
		 */
		public Builder orderBy(String field, boolean ascending) {
			ordering.put(field, ascending);
			return this;
		}

		/**
		 * Set the required criteria. Map key represents property name and map value represents criteria. Each entity must match all of given criteria.
		 * @param requiredCriteria Required criteria.
		 * @return This builder.
		 * @throws IllegalStateException When another required criteria is already set in this builder.
		 * @see Criteria
		 */
		public Builder allMatch(Map<String, Object> requiredCriteria) {
			if (this.requiredCriteria != null) {
				throw new IllegalStateException("Required criteria is already set");
			}

			this.requiredCriteria = requiredCriteria;
			return this;
		}

		/**
		 * Set the optional criteria. Map key represents property name and map value represents criteria. Each entity must match at least one of given criteria.
		 * @param optionalCriteria Optional criteria.
		 * @return This builder.
		 * @throws IllegalStateException When another optional criteria is already set in this builder.
		 * @see Criteria
		 */
		public Builder anyMatch(Map<String, Object> optionalCriteria) {
			if (this.optionalCriteria != null) {
				throw new IllegalStateException("Optional criteria is already set");
			}

			this.optionalCriteria = optionalCriteria;
			return this;
		}

		/**
		 * Build the page.
		 * @return The built page.
		 */
		public Page build() {
			return new Page(offset, limit, ordering, requiredCriteria, optionalCriteria);
		}

	}

}