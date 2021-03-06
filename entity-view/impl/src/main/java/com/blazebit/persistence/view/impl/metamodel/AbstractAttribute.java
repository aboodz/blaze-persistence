/*
 * Copyright 2014 - 2020 Blazebit.
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

package com.blazebit.persistence.view.impl.metamodel;

import com.blazebit.annotation.AnnotationUtils;
import com.blazebit.lang.StringUtils;
import com.blazebit.persistence.parser.SimpleQueryGenerator;
import com.blazebit.persistence.parser.expression.Expression;
import com.blazebit.persistence.parser.expression.ExpressionFactory;
import com.blazebit.persistence.parser.expression.NullExpression;
import com.blazebit.persistence.parser.expression.NumericLiteral;
import com.blazebit.persistence.parser.expression.NumericType;
import com.blazebit.persistence.parser.expression.ParameterExpression;
import com.blazebit.persistence.parser.expression.PathExpression;
import com.blazebit.persistence.parser.expression.PropertyExpression;
import com.blazebit.persistence.parser.expression.SyntaxErrorException;
import com.blazebit.persistence.parser.predicate.Predicate;
import com.blazebit.persistence.spi.ExtendedAttribute;
import com.blazebit.persistence.spi.ExtendedManagedType;
import com.blazebit.persistence.spi.LateralStyle;
import com.blazebit.persistence.spi.ServiceProvider;
import com.blazebit.persistence.view.CorrelationProvider;
import com.blazebit.persistence.view.CorrelationProviderFactory;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.FetchStrategy;
import com.blazebit.persistence.view.IdMapping;
import com.blazebit.persistence.view.Mapping;
import com.blazebit.persistence.view.MappingCorrelated;
import com.blazebit.persistence.view.MappingCorrelatedSimple;
import com.blazebit.persistence.view.MappingParameter;
import com.blazebit.persistence.view.MappingSubquery;
import com.blazebit.persistence.view.Self;
import com.blazebit.persistence.view.SubqueryProvider;
import com.blazebit.persistence.view.SubqueryProviderFactory;
import com.blazebit.persistence.view.impl.CollectionJoinMappingGathererExpressionVisitor;
import com.blazebit.persistence.view.impl.CorrelationProviderHelper;
import com.blazebit.persistence.view.impl.PrefixingQueryGenerator;
import com.blazebit.persistence.view.impl.ScalarTargetResolvingExpressionVisitor;
import com.blazebit.persistence.view.impl.ScalarTargetResolvingExpressionVisitor.TargetType;
import com.blazebit.persistence.view.impl.StaticCorrelationProvider;
import com.blazebit.persistence.view.impl.SubqueryProviderHelper;
import com.blazebit.persistence.view.impl.UpdatableExpressionVisitor;
import com.blazebit.persistence.view.impl.collection.CollectionInstantiatorImplementor;
import com.blazebit.persistence.view.impl.collection.ListCollectionInstantiator;
import com.blazebit.persistence.view.impl.collection.MapInstantiatorImplementor;
import com.blazebit.persistence.view.impl.collection.OrderedCollectionInstantiator;
import com.blazebit.persistence.view.impl.collection.OrderedMapInstantiator;
import com.blazebit.persistence.view.impl.collection.OrderedSetCollectionInstantiator;
import com.blazebit.persistence.view.impl.collection.PluralObjectFactory;
import com.blazebit.persistence.view.impl.collection.SortedMapInstantiator;
import com.blazebit.persistence.view.impl.collection.SortedSetCollectionInstantiator;
import com.blazebit.persistence.view.impl.collection.UnorderedMapInstantiator;
import com.blazebit.persistence.view.impl.collection.UnorderedSetCollectionInstantiator;
import com.blazebit.persistence.view.metamodel.Attribute;
import com.blazebit.persistence.view.metamodel.ManagedViewType;
import com.blazebit.persistence.view.metamodel.OrderByItem;
import com.blazebit.persistence.view.metamodel.PluralAttribute;
import com.blazebit.persistence.view.metamodel.Type;
import com.blazebit.persistence.view.metamodel.ViewType;
import com.blazebit.reflection.ReflectionUtils;

import javax.persistence.metamodel.ManagedType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractAttribute<X, Y> implements Attribute<X, Y> {

    private static final String[] EMPTY = new String[0];
    private static final String THIS = "this";
    private static final Pattern PREFIX_THIS_REPLACE_PATTERN = Pattern.compile("([^a-zA-Z0-9\\.])this\\.");

    protected final ManagedViewTypeImplementor<X> declaringType;
    protected final Class<Y> javaType;
    protected final Class<?> convertedJavaType;
    protected final String mapping;
    protected final Expression mappingExpression;
    protected final String[] fetches;
    protected final FetchStrategy fetchStrategy;
    protected final int batchSize;
    protected final List<OrderByItem> orderByItems;
    protected final String limitExpression;
    protected final String offsetExpression;
    protected final SubqueryProviderFactory subqueryProviderFactory;
    protected final Class<? extends SubqueryProvider> subqueryProvider;
    protected final String subqueryExpression;
    protected final Expression subqueryResultExpression;
    protected final String subqueryAlias;
    protected final CorrelationProviderFactory correlationProviderFactory;
    protected final Class<? extends CorrelationProvider> correlationProvider;
    protected final String correlationBasis;
    protected final String correlationResult;
    protected final Class<?> correlated;
    protected final String correlationKeyAlias;
    protected final String correlationExpression;
    protected final Expression correlationBasisExpression;
    protected final Expression correlationResultExpression;
    protected final Predicate correlationPredicate;
    protected final MappingType mappingType;
    protected final boolean id;
    protected final javax.persistence.metamodel.Attribute<?, ?> updateMappableAttribute;
    private final List<TargetType> possibleTargetTypes;

    @SuppressWarnings("unchecked")
    public AbstractAttribute(ManagedViewTypeImplementor<X> declaringType, AttributeMapping mapping, MetamodelBuildingContext context, EmbeddableOwner embeddableMapping) {
        Class<Y> javaType = null;
        try {
            javaType = (Class<Y>) mapping.getJavaType(context, embeddableMapping);
            if (javaType == null) {
                context.addError("The attribute type is not resolvable at the " + mapping.getErrorLocation());
            }
        } catch (IllegalArgumentException ex) {
            context.addError("An error occurred while trying to resolve the attribute type at the " + mapping.getErrorLocation());
        }

        this.possibleTargetTypes = mapping.getPossibleTargetTypes(context);
        Integer defaultBatchSize = mapping.getDefaultBatchSize();
        int batchSize;
        if (defaultBatchSize == null || defaultBatchSize == -1) {
            batchSize = -1;
        } else if (defaultBatchSize < 1) {
            context.addError("Illegal batch fetch size lower than 1 defined at '" + mapping.getErrorLocation() + "'!");
            batchSize = Integer.MIN_VALUE;
        } else {
            batchSize = defaultBatchSize;
        }

        String limitExpression;
        String offsetExpression;
        List<OrderByItem> orderByItems;
        if (mapping.getLimitExpression() == null) {
            limitExpression = null;
            offsetExpression = null;
            orderByItems = Collections.emptyList();
        } else {
            limitExpression = mapping.getLimitExpression();
            offsetExpression = mapping.getOffsetExpression();
            if (offsetExpression == null || offsetExpression.isEmpty()) {
                offsetExpression = "0";
            }
            List<String> orderByItemExpressions = mapping.getOrderByItems();
            orderByItems = new ArrayList<>(orderByItemExpressions.size());
            for (int i = 0; i < orderByItemExpressions.size(); i++) {
                String expression = orderByItemExpressions.get(i);
                String upperExpression = expression.toUpperCase();
                boolean ascending = true;
                boolean nullsFirst = false;
                if (upperExpression.endsWith(" NULLS LAST")) {
                    upperExpression = upperExpression.substring(0, upperExpression.length() - " NULLS LAST".length());
                } else if (upperExpression.endsWith(" NULLS FIRST")) {
                    nullsFirst = true;
                    upperExpression = upperExpression.substring(0, upperExpression.length() - " NULLS FIRST".length());
                }
                if (upperExpression.endsWith(" ASC")) {
                    upperExpression = upperExpression.substring(0, upperExpression.length() - " ASC".length());
                } else if (upperExpression.endsWith(" DESC")) {
                    ascending = false;
                    upperExpression = upperExpression.substring(0, upperExpression.length() - " DESC".length());
                }
                expression = expression.substring(0, upperExpression.length());
                orderByItems.add(new OrderByItem(expression, ascending, nullsFirst));
            }
            orderByItems = Collections.unmodifiableList(orderByItems);
        }

        this.declaringType = declaringType;
        this.javaType = javaType;
        this.convertedJavaType = getConvertedType(declaringType.getJavaType(), mapping.getType(context, embeddableMapping).getConvertedType(), javaType);
        Annotation mappingAnnotation = mapping.getMapping();

        if (mappingAnnotation instanceof IdMapping) {
            this.mapping = ((IdMapping) mappingAnnotation).value();
            this.mappingExpression = createSimpleExpression(this.mapping, mapping, context, ExpressionLocation.MAPPING);
            this.fetches = EMPTY;
            this.fetchStrategy = FetchStrategy.JOIN;
            this.batchSize = -1;
            this.orderByItems = Collections.emptyList();
            this.limitExpression = null;
            this.offsetExpression = null;
            this.subqueryProviderFactory = null;
            this.subqueryProvider = null;
            this.id = true;
            this.updateMappableAttribute = getUpdateMappableAttribute(context);
            this.mappingType = MappingType.BASIC;
            this.subqueryExpression = null;
            this.subqueryResultExpression = null;
            this.subqueryAlias = null;
            this.correlationBasis = null;
            this.correlationResult = null;
            this.correlationProvider = null;
            this.correlationProviderFactory = null;
            this.correlated = null;
            this.correlationKeyAlias = null;
            this.correlationExpression = null;
            this.correlationBasisExpression = null;
            this.correlationResultExpression = null;
            this.correlationPredicate = null;
        } else if (mappingAnnotation instanceof Mapping) {
            Mapping m = (Mapping) mappingAnnotation;
            this.mapping = m.value();
            this.mappingExpression = createSimpleExpression(this.mapping, mapping, context, ExpressionLocation.MAPPING);
            this.fetches = m.fetches();
            this.fetchStrategy = m.fetch();
            this.batchSize = batchSize;
            this.orderByItems = orderByItems;
            this.limitExpression = limitExpression;
            this.offsetExpression = offsetExpression;
            this.subqueryProviderFactory = null;
            this.subqueryProvider = null;
            this.id = false;
            this.updateMappableAttribute = getUpdateMappableAttribute(context);
            this.mappingType = MappingType.BASIC;
            this.subqueryExpression = null;
            this.subqueryResultExpression = null;
            this.subqueryAlias = null;
            if (fetchStrategy == FetchStrategy.JOIN && limitExpression == null) {
                this.correlationProvider = null;
                this.correlationProviderFactory = null;
                this.correlationResult = null;
                this.correlationBasis = null;
                this.correlated = null;
                this.correlationKeyAlias = null;
                this.correlationExpression = null;
                this.correlationBasisExpression = null;
                this.correlationResultExpression = null;
                this.correlationPredicate = null;
            } else {
                ExtendedManagedType<?> managedType = context.getEntityMetamodel().getManagedType(ExtendedManagedType.class, declaringType.getJpaManagedType());
                ExtendedAttribute<?, ?> attribute = managedType.getOwnedAttributes().get(this.mapping);

                this.correlationKeyAlias = "__correlationAlias";
                // The special case when joining the association results in a different join than when doing it through entity joins
                // This might be due to a @Where annotation being present on the association
                if (fetchStrategy == FetchStrategy.SELECT && attribute != null && attribute.hasJoinCondition()) {
                    this.correlated = declaringType.getEntityClass();
                    this.correlationExpression = "this IN __correlationAlias";
                    this.correlationResult = this.mapping;
                    this.correlationResultExpression = mappingExpression;
                } else {
                    // If the mapping is a deep path expression i.e. contains a dot but no parenthesis, we try to find a mapped by attribute by a prefix
                    int index;
                    if (attribute == null && (index = this.mapping.indexOf('.')) != -1 && this.mapping.indexOf('(') == -1
                            && (attribute = managedType.getOwnedAttributes().get(this.mapping.substring(0, index))) != null && !StringUtils.isEmpty(attribute.getMappedBy()) && !attribute.hasJoinCondition()) {
                        this.correlated = attribute.getElementClass();
                        this.correlationExpression = attribute.getMappedBy() + " IN __correlationAlias";
                        this.correlationResult = this.mapping.substring(index + 1);
                        if (mappingExpression instanceof PathExpression) {
                            this.correlationResultExpression = ((PathExpression) mappingExpression).withoutFirst();
                        } else {
                            this.correlationResultExpression = new PathExpression();
                        }
                    } else if (attribute != null && !StringUtils.isEmpty(attribute.getMappedBy()) && !attribute.hasJoinCondition()) {
                        this.correlated = attribute.getElementClass();
                        this.correlationExpression = attribute.getMappedBy() + " IN __correlationAlias";
                        this.correlationResult = "";
                        this.correlationResultExpression = new PathExpression();
                    } else {
                        this.correlated = declaringType.getEntityClass();
                        this.correlationExpression = "this IN __correlationAlias";
                        this.correlationResult = this.mapping;
                        this.correlationResultExpression = mappingExpression;
                    }
                }
                this.correlationBasis = "this";
                this.correlationBasisExpression = new PathExpression(new PropertyExpression("this"));
                this.correlationPredicate = createPredicate(correlationExpression, mapping, context, ExpressionLocation.CORRELATION_EXPRESSION);
                this.correlationProvider = null;
                this.correlationProviderFactory = new StaticCorrelationProvider(correlated, correlationKeyAlias, correlationExpression, correlationPredicate);
            }
        } else if (mappingAnnotation instanceof MappingParameter) {
            this.mapping = ((MappingParameter) mappingAnnotation).value();
            this.mappingExpression = null;
            this.fetches = EMPTY;
            this.fetchStrategy = FetchStrategy.JOIN;
            this.batchSize = -1;
            this.orderByItems = Collections.emptyList();
            this.limitExpression = null;
            this.offsetExpression = null;
            this.subqueryProviderFactory = null;
            this.subqueryProvider = null;
            this.id = false;
            // Parameters are never update mappable
            this.updateMappableAttribute = null;
            this.mappingType = MappingType.PARAMETER;
            this.subqueryExpression = null;
            this.subqueryResultExpression = null;
            this.subqueryAlias = null;
            this.correlationBasis = null;
            this.correlationResult = null;
            this.correlationProvider = null;
            this.correlationProviderFactory = null;
            this.correlated = null;
            this.correlationKeyAlias = null;
            this.correlationExpression = null;
            this.correlationBasisExpression = null;
            this.correlationResultExpression = null;
            this.correlationPredicate = null;
        } else if (mappingAnnotation instanceof Self) {
            this.mapping = "NULL";
            this.mappingExpression = NullExpression.INSTANCE;
            this.fetches = EMPTY;
            this.fetchStrategy = FetchStrategy.JOIN;
            this.batchSize = -1;
            this.orderByItems = Collections.emptyList();
            this.limitExpression = null;
            this.offsetExpression = null;
            this.subqueryProviderFactory = null;
            this.subqueryProvider = null;
            this.id = false;
            this.updateMappableAttribute = null;
            this.mappingType = MappingType.PARAMETER;
            this.subqueryExpression = null;
            this.subqueryResultExpression = null;
            this.subqueryAlias = null;
            this.correlationBasis = null;
            this.correlationResult = null;
            this.correlationProvider = null;
            this.correlationProviderFactory = null;
            this.correlated = null;
            this.correlationKeyAlias = null;
            this.correlationExpression = null;
            this.correlationBasisExpression = null;
            this.correlationResultExpression = null;
            this.correlationPredicate = null;
        } else if (mappingAnnotation instanceof MappingSubquery) {
            MappingSubquery mappingSubquery = (MappingSubquery) mappingAnnotation;
            this.mapping = null;
            this.mappingExpression = null;
            this.fetches = EMPTY;
            this.subqueryProvider = mappingSubquery.value();
            this.subqueryProviderFactory = SubqueryProviderHelper.getFactory(subqueryProvider);
            this.fetchStrategy = FetchStrategy.JOIN;
            this.batchSize = -1;
            this.orderByItems = Collections.emptyList();
            this.limitExpression = null;
            this.offsetExpression = null;
            this.id = false;
            // Subqueries are never update mappable
            this.updateMappableAttribute = null;
            this.mappingType = MappingType.SUBQUERY;
            this.subqueryExpression = mappingSubquery.expression();
            this.subqueryAlias = mappingSubquery.subqueryAlias();
            this.subqueryResultExpression = createSimpleExpression(subqueryExpression, mapping, context, ExpressionLocation.SUBQUERY_EXPRESSION);
            this.correlationBasis = null;
            this.correlationResult = null;
            this.correlationProvider = null;
            this.correlationProviderFactory = null;
            this.correlated = null;
            this.correlationKeyAlias = null;
            this.correlationExpression = null;
            this.correlationBasisExpression = null;
            this.correlationResultExpression = null;
            this.correlationPredicate = null;

            if (!subqueryExpression.isEmpty() && subqueryAlias.isEmpty()) {
                context.addError("The subquery alias is empty although the subquery expression is not " + mapping.getErrorLocation());
            }
            if (subqueryProvider.getEnclosingClass() != null && !Modifier.isStatic(subqueryProvider.getModifiers())) {
                context.addError("The subquery provider is defined as non-static inner class. Make it static, otherwise it can't be instantiated: " + mapping.getErrorLocation());
            }
        } else if (mappingAnnotation instanceof MappingCorrelated) {
            MappingCorrelated mappingCorrelated = (MappingCorrelated) mappingAnnotation;
            this.mapping = null;
            this.mappingExpression = null;
            this.fetches = mappingCorrelated.fetches();
            this.fetchStrategy = mappingCorrelated.fetch();

            if (fetchStrategy == FetchStrategy.SELECT) {
                this.batchSize = batchSize;
            } else {
                this.batchSize = -1;
            }
            this.orderByItems = orderByItems;
            this.limitExpression = limitExpression;
            this.offsetExpression = offsetExpression;

            this.subqueryProviderFactory = null;
            this.subqueryProvider = null;
            this.id = false;
            this.updateMappableAttribute = null;
            this.mappingType = MappingType.CORRELATED;
            this.subqueryExpression = null;
            this.subqueryResultExpression = null;
            this.subqueryAlias = null;
            this.correlationBasis = mappingCorrelated.correlationBasis();
            this.correlationResult = mappingCorrelated.correlationResult();
            this.correlationProvider = mappingCorrelated.correlator();
            this.correlated = null;
            this.correlationKeyAlias = null;
            this.correlationExpression = null;
            this.correlationBasisExpression = createSimpleExpression(correlationBasis, mapping, context, ExpressionLocation.CORRELATION_BASIS);
            this.correlationResultExpression = createSimpleExpression(correlationResult, mapping, context, ExpressionLocation.CORRELATION_RESULT);
            this.correlationPredicate = null;

            if (correlationProvider.getEnclosingClass() != null && !Modifier.isStatic(correlationProvider.getModifiers())) {
                context.addError("The correlation provider is defined as non-static inner class. Make it static, otherwise it can't be instantiated: " + mapping.getErrorLocation());
            }
            this.correlationProviderFactory = CorrelationProviderHelper.getFactory(correlationProvider);
        } else if (mappingAnnotation instanceof MappingCorrelatedSimple) {
            MappingCorrelatedSimple mappingCorrelated = (MappingCorrelatedSimple) mappingAnnotation;
            this.mapping = null;
            this.mappingExpression = null;
            this.fetches = mappingCorrelated.fetches();
            this.fetchStrategy = mappingCorrelated.fetch();

            if (fetchStrategy == FetchStrategy.SELECT) {
                this.batchSize = batchSize;
            } else {
                this.batchSize = -1;
            }
            this.orderByItems = orderByItems;
            this.limitExpression = limitExpression;
            this.offsetExpression = offsetExpression;

            this.subqueryProviderFactory = null;
            this.subqueryProvider = null;
            this.id = false;
            this.updateMappableAttribute = null;
            this.mappingType = MappingType.CORRELATED;
            this.subqueryExpression = null;
            this.subqueryResultExpression = null;
            this.subqueryAlias = null;
            this.correlationProvider = null;
            this.correlationBasis = mappingCorrelated.correlationBasis();
            this.correlationResult = mappingCorrelated.correlationResult();
            this.correlated = mappingCorrelated.correlated();
            this.correlationKeyAlias = mappingCorrelated.correlationKeyAlias();
            this.correlationExpression = mappingCorrelated.correlationExpression();
            this.correlationBasisExpression = createSimpleExpression(correlationBasis, mapping, context, ExpressionLocation.CORRELATION_BASIS);
            this.correlationResultExpression = createSimpleExpression(correlationResult, mapping, context, ExpressionLocation.CORRELATION_RESULT);
            this.correlationPredicate = createPredicate(correlationExpression, mapping, context, ExpressionLocation.CORRELATION_EXPRESSION);
            this.correlationProviderFactory = new StaticCorrelationProvider(correlated, correlationKeyAlias, correlationExpression, correlationPredicate);

            if (mappingCorrelated.correlationBasis().isEmpty()) {
                context.addError("Illegal empty correlation basis in the " + mapping.getErrorLocation());
            }
            if (!(declaringType instanceof ViewType<?>) && (fetchStrategy == FetchStrategy.SELECT || fetchStrategy == FetchStrategy.SUBSELECT)) {
                // This check is not perfect, but good enough since we also check it at runtime
                if (mappingCorrelated.correlationExpression().toUpperCase().contains("EMBEDDING_VIEW")) {
                    context.addError("The use of EMBEDDING_VIEW in the correlation for '" + mapping.getErrorLocation() + "' is illegal because the embedding view type '" + declaringType.getJavaType().getName() + "' does not declare a @IdMapping!");
                }
            }
        } else {
            context.addError("No mapping annotation could be found " + mapping.getErrorLocation());
            this.mapping = null;
            this.mappingExpression = null;
            this.fetches = EMPTY;
            this.fetchStrategy = null;
            this.batchSize = Integer.MIN_VALUE;
            this.orderByItems = null;
            this.limitExpression = null;
            this.offsetExpression = null;
            this.subqueryProviderFactory = null;
            this.subqueryProvider = null;
            this.id = false;
            this.updateMappableAttribute = null;
            this.mappingType = null;
            this.subqueryExpression = null;
            this.subqueryResultExpression = null;
            this.subqueryAlias = null;
            this.correlationBasis = null;
            this.correlationResult = null;
            this.correlationProvider = null;
            this.correlationProviderFactory = null;
            this.correlated = null;
            this.correlationKeyAlias = null;
            this.correlationExpression = null;
            this.correlationBasisExpression = null;
            this.correlationResultExpression = null;
            this.correlationPredicate = null;
        }

        if (limitExpression != null && fetchStrategy == FetchStrategy.MULTISET && context.getDbmsDialect().getLateralStyle() == LateralStyle.NONE && !context.getDbmsDialect().supportsWindowFunctions()) {
            context.addError("The use of the MULTISET fetch strategy with a limit in the '" + mapping.getErrorLocation() + "' requires lateral joins or window functions which are unsupported by the DBMS!");
        }
    }

    private static Expression createSimpleExpression(String expression, AttributeMapping mapping, MetamodelBuildingContext context, ExpressionLocation expressionLocation) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        try {
            return context.getTypeValidationExpressionFactory().createSimpleExpression(expression, false, expressionLocation == ExpressionLocation.SUBQUERY_EXPRESSION, true);
        } catch (SyntaxErrorException ex) {
            context.addError("Syntax error in " + expressionLocation + " '" + expression + "' of the " + mapping.getErrorLocation() + ": " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            context.addError("An error occurred while trying to resolve the " + expressionLocation + " of the " + mapping.getErrorLocation() + ": " + ex.getMessage());
        }
        return null;
    }

    private static Predicate createPredicate(String expression, AttributeMapping mapping, MetamodelBuildingContext context, ExpressionLocation expressionLocation) {
        try {
            return context.getTypeValidationExpressionFactory().createBooleanExpression(expression, false);
        } catch (SyntaxErrorException ex) {
            context.addError("Syntax error in " + expressionLocation + " '" + expression + "' of the " + mapping.getErrorLocation() + ": " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            context.addError("An error occurred while trying to resolve the " + expressionLocation + " of the " + mapping.getErrorLocation() + ": " + ex.getMessage());
        }
        return null;
    }

    private static Class<?> getConvertedType(Class<?> declaringClass, java.lang.reflect.Type convertedType, Class<?> javaType) {
        if (convertedType == null) {
            return javaType;
        }
        return ReflectionUtils.resolveType(declaringClass, convertedType);
    }

    private javax.persistence.metamodel.Attribute<?, ?> getUpdateMappableAttribute(MetamodelBuildingContext context) {
        if (mappingExpression != null) {
            try {
                UpdatableExpressionVisitor visitor = new UpdatableExpressionVisitor(context.getEntityMetamodel(), declaringType.getEntityClass(), true);
                mappingExpression.accept(visitor);
                Iterator<javax.persistence.metamodel.Attribute<?, ?>> iterator = visitor.getPossibleTargets().keySet().iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                }
            } catch (Exception ex) {
                // Don't care about the actual exception as that will be thrown anyway when validating the expressions later
            }
        }

        return null;
    }

    public static String stripThisFromMapping(String mapping) {
        return replaceThisFromMapping(mapping, "");
    }

    public static String replaceThisFromMapping(String mapping, String root) {
        if (mapping == null) {
            return null;
        }
        mapping = mapping.trim();
        if (mapping.startsWith(THIS)) {
            // Special case when the mapping start with "this"
            if (mapping.length() == THIS.length()) {
                // Return the empty string if it essentially equals "this"
                return root;
            }
            if (root.isEmpty()) {
                char nextChar = mapping.charAt(THIS.length());
                if (nextChar == '.') {
                    // Only replace if it isn't a prefix
                    mapping = mapping.substring(THIS.length() + 1);
                }
            } else {
                mapping = root + mapping.substring(THIS.length());
            }
        }

        String replacement;
        if (root.isEmpty()) {
            replacement = "$1";
        } else {
            replacement = "$1" + root + ".";
        }
        mapping = PREFIX_THIS_REPLACE_PATTERN.matcher(mapping)
                .replaceAll(replacement);

        return mapping;
    }

    public final void renderSubqueryExpression(String parent, ServiceProvider serviceProvider, StringBuilder sb) {
        renderExpression(parent, subqueryResultExpression, subqueryAlias, serviceProvider, sb);
    }

    public final void renderSubqueryExpression(String parent, String subqueryExpression, String subqueryAlias, ServiceProvider serviceProvider, StringBuilder sb) {
        ExpressionFactory ef = serviceProvider.getService(ExpressionFactory.class);
        Expression expr = ef.createSimpleExpression(subqueryExpression, false, false, true);
        renderExpression(parent, expr, subqueryAlias, serviceProvider, sb);
    }

    public final void renderCorrelationBasis(String parent, ServiceProvider serviceProvider, StringBuilder sb) {
        renderExpression(parent, correlationBasisExpression, null, serviceProvider, sb);
    }

    public final void renderCorrelationResult(String parent, ServiceProvider serviceProvider, StringBuilder sb) {
        renderExpression(parent, correlationResultExpression, null, serviceProvider, sb);
    }

    public final void renderMapping(String parent, ServiceProvider serviceProvider, StringBuilder sb) {
        renderExpression(parent, mappingExpression, null, serviceProvider, sb);
    }

    private void renderExpression(String parent, Expression expression, String aliasToSkip, ServiceProvider serviceProvider, StringBuilder sb) {
        if (parent != null && !parent.isEmpty()) {
            ExpressionFactory ef = serviceProvider.getService(ExpressionFactory.class);
            SimpleQueryGenerator generator = new PrefixingQueryGenerator(ef, parent, aliasToSkip, aliasToSkip, PrefixingQueryGenerator.DEFAULT_QUERY_ALIASES, true, false);
            generator.setQueryBuffer(sb);
            expression.accept(generator);
        } else {
            sb.append(expression);
        }
    }

    /**
     * Collects all mappings that involve the use of a collection attribute for duplicate usage checks.
     *
     * @param managedType The JPA type against which to evaluate the mapping
     * @param context The metamodel context
     * @return The mappings which contain collection attribute uses
     */
    public Map<String, Boolean> getCollectionJoinMappings(ManagedType<?> managedType, MetamodelBuildingContext context) {
        if (mappingExpression == null || isQueryParameter() || getFetchStrategy() != FetchStrategy.JOIN) {
            // Subqueries and parameters can't be checked. When a collection is remapped to a singular attribute, we don't check it
            // When using a non-join fetch strategy, we also don't care about the collection join mappings
            return Collections.emptyMap();
        }
        
        CollectionJoinMappingGathererExpressionVisitor visitor = new CollectionJoinMappingGathererExpressionVisitor(managedType, context.getEntityMetamodel());
        mappingExpression.accept(visitor);
        Map<String, Boolean> mappings = new HashMap<>();
        boolean aggregate = getAttributeType() == AttributeType.SINGULAR;
        
        for (String s : visitor.getPaths()) {
            mappings.put(s, aggregate);
        }
        
        return mappings;
    }

    public boolean hasJoinFetchedCollections() {
        return getFetchStrategy() == FetchStrategy.JOIN && (
                isCollection() || getElementType() instanceof ManagedViewTypeImpl<?> && ((ManagedViewTypeImplementor<?>) getElementType()).hasJoinFetchedCollections());
    }

    public boolean hasSelectOrSubselectFetchedAttributes() {
        return getFetchStrategy() == FetchStrategy.SELECT || getFetchStrategy() == FetchStrategy.SUBSELECT || (
                getElementType() instanceof ManagedViewTypeImpl<?> && ((ManagedViewTypeImplementor<?>) getElementType()).hasSelectOrSubselectFetchedAttributes());
    }

    public boolean hasJpaManagedAttributes() {
        return getElementType() instanceof BasicTypeImpl<?> && ((BasicTypeImpl<?>) getElementType()).isJpaManaged() ||
                getElementType() instanceof ManagedViewTypeImpl<?> && ((ManagedViewTypeImplementor<?>) getElementType()).hasJpaManagedAttributes();
    }

    protected boolean determineForcedUnique(MetamodelBuildingContext context) {
        if (isCollection() && getMapping() != null && getMapping().indexOf('.') == -1) {
            ExtendedManagedType<?> managedType = context.getEntityMetamodel().getManagedType(ExtendedManagedType.class, getDeclaringType().getJpaManagedType());
            ExtendedAttribute<?, ?> attribute = managedType.getOwnedAttributes().get(getMapping());
            if (attribute != null && attribute.getAttribute() instanceof javax.persistence.metamodel.PluralAttribute<?, ?, ?>) {
                // TODO: we should add that information to ExtendedAttribute
                return (((javax.persistence.metamodel.PluralAttribute<?, ?, ?>) attribute.getAttribute()).getCollectionType() != javax.persistence.metamodel.PluralAttribute.CollectionType.MAP)
                        && (!StringUtils.isEmpty(attribute.getMappedBy()) || !attribute.isBag())
                        && (attribute.getJoinTable() == null || attribute.getJoinTable().getKeyColumnMappings() == null)
                        && !MetamodelUtils.isIndexedList(context.getEntityMetamodel(), managedType.getType().getJavaType(), mappingExpression, mapping);
            }
        }

        return false;
    }

    public javax.persistence.metamodel.Attribute<?, ?> getUpdateMappableAttribute() {
        return updateMappableAttribute;
    }

    public boolean isUpdateMappable() {
        // Since we can cascade correlated views, we consider them update mappable
        return hasDirtyStateIndex() || updateMappableAttribute != null;
    }

    public Class<?> getCorrelated() {
        return correlated;
    }

    public String getCorrelationKeyAlias() {
        return correlationKeyAlias;
    }

    public String getCorrelationExpression() {
        return correlationExpression;
    }

    public Predicate getCorrelationPredicate() {
        return correlationPredicate;
    }

    public abstract boolean needsDirtyTracker();

    public abstract boolean hasDirtyStateIndex();

    /**
     * @author Christian Beikov
     * @since 1.2.0
     */
    private static enum ExpressionLocation {
        MAPPING("mapping expression"),
        SUBQUERY_EXPRESSION("subquery expression"),
        CORRELATION_BASIS("correlation basis"),
        CORRELATION_RESULT("correlation result"),
        CORRELATION_EXPRESSION("correlation expression");

        private final String location;

        ExpressionLocation(String location) {
            this.location = location;
        }

        @Override
        public String toString() {
            return location;
        }
    }

    private static boolean isCompatible(TargetType t, Class<?> targetType, Class<?> targetElementType, boolean subtypesAllowed, boolean singular) {
        if (t.hasCollectionJoin()) {
            return isCompatible(t.getLeafBaseClass(), t.getLeafBaseValueClass(), targetType, targetElementType, subtypesAllowed, singular);
        } else {
            Class<?> entityAttributeElementType = getElementTypeOrNull(t, singular);
            return isCompatible(t.getLeafBaseClass(), entityAttributeElementType, targetType, targetElementType, subtypesAllowed, singular);
        }
    }

    private static Class<?> getElementTypeOrNull(TargetType t, boolean singular) {
        if (singular && t.getLeafBaseClass() == t.getLeafBaseValueClass() && (Collection.class.isAssignableFrom(t.getLeafBaseClass()) || Map.class.isAssignableFrom(t.getLeafBaseClass()))) {
            Member javaMember = t.getLeafMethod().getJavaMember();
            Class<?> elementClass;
            if (javaMember instanceof Field) {
                Class<?>[] resolvedFieldTypeArguments = ReflectionUtils.getResolvedFieldTypeArguments(t.getLeafMethod().getDeclaringType().getJavaType(), (Field) javaMember);
                elementClass = resolvedFieldTypeArguments[resolvedFieldTypeArguments.length - 1];
            } else if (javaMember instanceof Method) {
                Class<?>[] resolvedMethodReturnTypeArguments = ReflectionUtils.getResolvedMethodReturnTypeArguments(t.getLeafMethod().getDeclaringType().getJavaType(), (Method) javaMember);
                elementClass = resolvedMethodReturnTypeArguments[resolvedMethodReturnTypeArguments.length - 1];
            } else {
                elementClass = null;
            }
            if (elementClass != t.getLeafBaseValueClass()) {
                return elementClass;
            }
        }
        return null;
    }

    /**
     * Checks if <code>entityAttributeType</code> with an optional element type <code>entityAttributeElementType</code>
     * can be mapped to <code>targetType</code> with the optional element type <code>viewAttributeElementType</code> and the given <code>subtypesAllowed</code> config.
     *
     * A <code>entityAttributeType</code> of <code>NULL</code> represents the <i>any type</i> which makes it always compatible i.e. returning <code>true</code>.
     * A type is compatible if the source types given by <code>entityAttributeType</code>/<code>entityAttributeElementType</code>
     * are subtypes of the target types <code>targetType</code>/<code>viewAttributeElementType</code>.
     *
     * A source collection type it is also compatible with non-collection targets if the source element type is a subtype of the target type.
     * A source non-collection type is also compatible with a collection target if the source type is a subtype of the target element type.
     *
     * @param entityAttributeType The source type
     * @param entityAttributeElementType The optional source element type
     * @param viewAttributeType The target type
     * @param viewAttributeElementType The optional target element type
     * @param subtypesAllowed Whether a more specific source type is allowed to map to a general target type
     * @param singular Whether the view attribute is singular
     * @return True if mapping from <code>entityAttributeType</code>/<code>entityAttributeElementType</code> to <code>targetType</code>/<code>viewAttributeElementType</code> is possible
     */
    private static boolean isCompatible(Class<?> entityAttributeType, Class<?> entityAttributeElementType, Class<?> viewAttributeType, Class<?> viewAttributeElementType, boolean subtypesAllowed, boolean singular) {
        // Null is the marker for ANY TYPE
        if (entityAttributeType == null) {
            return true;
        }

        if (subtypesAllowed) {
            if (entityAttributeElementType != null) {
                if (viewAttributeElementType != null) {
                    // Mapping a plural entity attribute to a plural view attribute
                    // Either entityAttributeType is a subtype of target type, or it is a subtype of map and the target type a subtype of Collection
                    // This allows mapping Map<?, Entity> to List<Subview>
                    // Anyway the entityAttributeElementType must be a subtype of the viewAttributeElementType
                    return (viewAttributeType.isAssignableFrom(entityAttributeType) || !singular && Map.class.isAssignableFrom(entityAttributeType) && Collection.class.isAssignableFrom(viewAttributeType))
                            && viewAttributeElementType.isAssignableFrom(entityAttributeElementType);
                } else {
                    // Mapping a plural entity attribute to a singular view attribute
                    return viewAttributeType.isAssignableFrom(entityAttributeElementType);
                }
            } else {
                if (viewAttributeElementType != null) {
                    // Mapping a singular entity attribute to a plural view attribute
                    return viewAttributeElementType.isAssignableFrom(entityAttributeType);
                } else {
                    // Mapping a singular entity attribute to a singular view attribute
                    return viewAttributeType.isAssignableFrom(entityAttributeType);
                }
            }
        } else {
            if (entityAttributeElementType != null) {
                if (viewAttributeElementType != null) {
                    // Mapping a plural entity attribute to a plural view attribute
                    return viewAttributeType == entityAttributeType
                            && viewAttributeElementType == entityAttributeElementType;
                } else {
                    // Mapping a plural entity attribute to a singular view attribute
                    return viewAttributeType == entityAttributeElementType;
                }
            } else {
                if (viewAttributeElementType != null) {
                    // Mapping a singular entity attribute to a plural view attribute
                    return viewAttributeElementType == entityAttributeType;
                } else {
                    // Mapping a singular entity attribute to a singular view attribute
                    return viewAttributeType == entityAttributeType;
                }
            }
        }
    }

    private static void validateTypesCompatible(ManagedType<?> managedType, Expression expression, Class<?> targetType, Class<?> targetElementType, boolean subtypesAllowed, boolean singular, MetamodelBuildingContext context, ExpressionLocation expressionLocation, String location) {
        ScalarTargetResolvingExpressionVisitor visitor = new ScalarTargetResolvingExpressionVisitor(managedType, context.getEntityMetamodel(), context.getJpqlFunctions());

        try {
            expression.accept(visitor);
        } catch (IllegalArgumentException ex) {
            context.addError("An error occurred while trying to resolve the " + expressionLocation + " of the " + location + ": " + ex.getMessage());
        }

        validateTypesCompatible(visitor.getPossibleTargetTypes(), targetType, targetElementType, subtypesAllowed, singular, context, expressionLocation, location);
    }

    private static void validateTypesCompatible(List<TargetType> possibleTargets, Class<?> targetType, Class<?> targetElementType, boolean subtypesAllowed, boolean singular, MetamodelBuildingContext context, ExpressionLocation expressionLocation, String location) {
        final Class<?> expressionType = targetType;
        if (!possibleTargets.isEmpty()) {
            boolean error = true;
            for (TargetType t : possibleTargets) {
                if (isCompatible(t, targetType, targetElementType, subtypesAllowed, singular)) {
                    error = false;
                    break;
                }
            }

            if (error) {
                if (targetType.isPrimitive()) {
                    targetType = ReflectionUtils.getObjectClassOfPrimitve(targetType);
                } else {
                    targetType = ReflectionUtils.getPrimitiveClassOfWrapper(targetType);
                }

                if (targetType != null) {
                    for (TargetType t : possibleTargets) {
                        if (isCompatible(t, targetType, targetElementType, subtypesAllowed, singular)) {
                            error = false;
                            break;
                        }
                    }
                }
            }

            if (error) {
                context.addError(typeCompatibilityError(possibleTargets, expressionType, targetElementType, expressionLocation, location));
            }
        }
    }

    private static String typeCompatibilityError(List<TargetType> possibleTargets, Class<?> targetType, Class<?> targetElementType, ExpressionLocation expressionLocation, String location) {
        StringBuilder sb = new StringBuilder();
        sb.append("The resolved possible types ");
        sb.append('[');
        for (TargetType t : possibleTargets) {
            sb.append(t.getLeafBaseClass().getName());
            if (t.getLeafBaseValueClass() != null && t.getLeafBaseClass() != t.getLeafBaseValueClass()) {
                sb.append('<');
                sb.append(t.getLeafBaseValueClass().getName());
                sb.append('>');
            }
            sb.append(", ");
        }

        sb.setLength(sb.length() - 2);
        sb.append(']');
        sb.append(" are not assignable to the given expression type '");
        sb.append(targetType.getName());
        if (targetElementType != null && targetElementType != targetType) {
            sb.append('<');
            sb.append(targetElementType.getName());
            sb.append('>');
        }
        sb.append("' of the ");
        sb.append(expressionLocation);
        sb.append(" declared by the ");
        sb.append(location);
        sb.append("!");
        return sb.toString();
    }

    public void checkAttribute(ManagedType<?> managedType, MetamodelBuildingContext context) {
        Class<?> expressionType = getJavaType();
        Class<?> keyType = null;
        Class<?> elementType = null;

        if (fetches.length != 0) {
            ManagedType<?> entityType = context.getEntityMetamodel().getManagedType(getElementType().getJavaType());
            if (entityType == null) {
                context.addError("Specifying fetches for non-entity attribute type [" + Arrays.toString(fetches) + "] at the " + getLocation() + " is not allowed!");
            } else {
                ScalarTargetResolvingExpressionVisitor visitor = new ScalarTargetResolvingExpressionVisitor(entityType, context.getEntityMetamodel(), context.getJpqlFunctions());
                for (int i = 0; i < fetches.length; i++) {
                    final String fetch = fetches[i];
                    final String errorLocation;
                    if (fetches.length == 1) {
                        errorLocation = "the fetch expression";
                    } else {
                        errorLocation = "the " + (i + 1) + ". fetch expression";
                    }
                    visitor.clear();

                    try {
                        // Validate the fetch expression parses
                        context.getExpressionFactory().createPathExpression(fetch).accept(visitor);
                    } catch (SyntaxErrorException ex) {
                        try {
                            context.getExpressionFactory().createSimpleExpression(fetch, false, false, true);
                            // The used expression is not usable for fetches
                            context.addError("Invalid fetch expression '" + fetch + "' of the " + getLocation() + ". Simplify the fetch expression to a simple path expression. Encountered error: " + ex.getMessage());
                        } catch (SyntaxErrorException ex2) {
                            // This is a real syntax error
                            context.addError("Syntax error in " + errorLocation + " '" + fetch + "' of the " + getLocation() + ": " + ex.getMessage());
                        }
                    } catch (IllegalArgumentException ex) {
                        context.addError("An error occurred while trying to resolve the " + errorLocation + " '" + fetch + "' of the " + getLocation() + ": " + ex.getMessage());
                    }
                }
            }
        }

        if (limitExpression != null) {
            try {
                Expression inItemExpression = context.getTypeValidationExpressionFactory().createInItemExpression(limitExpression);
                if (!(inItemExpression instanceof ParameterExpression) && !(inItemExpression instanceof NumericLiteral) || inItemExpression instanceof NumericLiteral && ((NumericLiteral) inItemExpression).getNumericType() != NumericType.INTEGER) {
                    context.addError("Syntax error in the limit expression '" + limitExpression + "' of the " + getLocation() + ": The expression must be a integer literal or a parameter expression");
                }
            } catch (SyntaxErrorException ex) {
                context.addError("Syntax error in the limit expression '" + limitExpression + "' of the " + getLocation() + ": " + ex.getMessage());
            } catch (IllegalArgumentException ex) {
                context.addError("An error occurred while trying to resolve the limit expression of the " + getLocation() + ": " + ex.getMessage());
            }
            try {
                Expression inItemExpression = context.getTypeValidationExpressionFactory().createInItemExpression(offsetExpression);
                if (!(inItemExpression instanceof ParameterExpression) && !(inItemExpression instanceof NumericLiteral) || inItemExpression instanceof NumericLiteral && ((NumericLiteral) inItemExpression).getNumericType() != NumericType.INTEGER) {
                    context.addError("Syntax error in the offset expression '" + offsetExpression + "' of the " + getLocation() + ": The expression must be a integer literal or a parameter expression");
                }
            } catch (SyntaxErrorException ex) {
                context.addError("Syntax error in the offset expression '" + offsetExpression + "' of the " + getLocation() + ": " + ex.getMessage());
            } catch (IllegalArgumentException ex) {
                context.addError("An error occurred while trying to resolve the offset expression of the " + getLocation() + ": " + ex.getMessage());
            }
            ScalarTargetResolvingExpressionVisitor visitor = new ScalarTargetResolvingExpressionVisitor(managedType, context.getEntityMetamodel(), context.getJpqlFunctions());
            for (int i = 0; i < orderByItems.size(); i++) {
                OrderByItem orderByItem = orderByItems.get(i);
                String expression = orderByItem.getExpression();
                try {
                    visitor.clear();
                    context.getTypeValidationExpressionFactory().createSimpleExpression(expression, false, false, true).accept(visitor);
                } catch (SyntaxErrorException ex) {
                    context.addError("Syntax error in the " + (i + 1) + "th order by expression '" + expression + "' of the " + getLocation() + ": " + ex.getMessage());
                } catch (IllegalArgumentException ex) {
                    context.addError("An error occurred while trying to resolve the " + (i + 1) + "th order by expression of the " + getLocation() + ": " + ex.getMessage());
                }
            }
        }

        if (fetchStrategy == FetchStrategy.MULTISET) {
            if (getElementType() instanceof ManagedViewTypeImplementor<?> && ((ManagedViewTypeImplementor<?>) getElementType()).hasJpaManagedAttributes()) {
                context.addError("Using the MULTISET fetch strategy is not allowed when the subview contains attributes with entity types. MULTISET at the " + getLocation() + " is not allowed!");
            } else if (getElementType() instanceof BasicTypeImpl<?> && ((BasicTypeImpl<?>) getElementType()).isJpaManaged()) {
                context.addError("Using the MULTISET fetch strategy is not allowed with entity types. MULTISET at the " + getLocation() + " is not allowed!");
            }
        }

        // TODO: key fetches?

        if (isCollection()) {
            elementType = getElementType().getJavaType();

            if (isUpdatable()) {
                // Updatable collection attributes currently must have the same collection type
                // We only allow using sorted variants instead of the normal collections
                if (isSorted()) {
                    if (getCollectionType() == PluralAttribute.CollectionType.MAP) {
                        expressionType = Map.class;
                    } else if (getCollectionType() == PluralAttribute.CollectionType.SET) {
                        expressionType = Set.class;
                    }
                }
            } else {
                if (isIndexed()) {
                    if (getCollectionType() == PluralAttribute.CollectionType.MAP) {
                        // All map types can be sourced from a map
                        expressionType = Map.class;
                        keyType = getKeyType().getJavaType();
                    } else {
                        // An indexed list can only be sourced from an indexed list
                        expressionType = List.class;
                        keyType = Integer.class;
                    }
                } else {
                    // We can assign e.g. a Set to a List, so let's use the common supertype
                    expressionType = Collection.class;
                }
            }
        }

        if (isSubview()) {
            ManagedViewTypeImplementor<?> subviewType = (ManagedViewTypeImplementor<?>) getElementType();

            if (isCollection()) {
                elementType = subviewType.getEntityClass();
            } else {
                expressionType = subviewType.getEntityClass();
            }
        } else {
            // If we determined, that the java type is a basic type, let's double check if the user didn't do something wrong
            Class<?> elementJavaType = getElementType().getJavaType();
            if ((elementJavaType.getModifiers() & Modifier.ABSTRACT) != 0) {
                // If the element type has an entity view annotation, although it is considered basic, we throw an error as this means, the view was probably not registered
                if (!isQueryParameter() && AnnotationUtils.findAnnotation(elementJavaType, EntityView.class) != null && getElementType().getConvertedType() == null) {
                    context.addError("The element type '" + elementJavaType.getName() + "' is considered basic although the class is annotated with @EntityView. Add a type converter or add the java class to the entity view configuration! Problematic attribute " + getLocation());
                }
            }
        }
        if (isKeySubview()) {
            keyType = ((ManagedViewTypeImplementor<?>) getKeyType()).getEntityClass();
        }

        // TODO: Make use of the key type in type checks

        if (isCorrelated()) {
            // Validate that resolving "correlationBasis" on "managedType" is valid
            validateTypesCompatible(managedType, correlationBasisExpression, Object.class, null, true, true, context, ExpressionLocation.CORRELATION_BASIS, getLocation());

            if (correlated != null) {
                // Validate that resolving "correlationResult" on "correlated" is compatible with "expressionType" and "elementType"
                validateTypesCompatible(possibleTargetTypes, expressionType, elementType, true, !isCollection(), context, ExpressionLocation.CORRELATION_RESULT, getLocation());
                if (correlationPredicate != null) {
                    // TODO: Validate the "correlationExpression" when https://github.com/Blazebit/blaze-persistence/issues/212 is implemented
                }
            }
        } else if (isSubquery()) {
            if (subqueryExpression != null && !subqueryExpression.isEmpty()) {
                // If a converter is applied, we already know that there was a type match with the underlying type
                if (getElementType().getConvertedType() == null) {
                    validateTypesCompatible(possibleTargetTypes, expressionType, elementType, true, !isCollection(), context, ExpressionLocation.SUBQUERY_EXPRESSION, getLocation());
                }
            }
        } else if (!isQueryParameter()) {
            boolean subtypesAllowed = !isUpdatable();

            // Forcing singular via @MappingSingular
            if (!isCollection() && (Collection.class.isAssignableFrom(expressionType) || Map.class.isAssignableFrom(expressionType))) {
                Class<?>[] typeArguments = getTypeArguments();
                elementType = typeArguments[typeArguments.length - 1];
            }

            // If a converter is applied, we already know that there was a type match with the underlying type
            if (getElementType().getConvertedType() == null) {
                // Validate that resolving "mapping" on "managedType" is compatible with "expressionType" and "elementType"
                validateTypesCompatible(possibleTargetTypes, expressionType, elementType, subtypesAllowed, !isCollection(), context, ExpressionLocation.MAPPING, getLocation());
            }

            if (isMutable() && (declaringType.isUpdatable() || declaringType.isCreatable())) {
                UpdatableExpressionVisitor visitor = new UpdatableExpressionVisitor(context.getEntityMetamodel(), managedType.getJavaType(), isUpdatable());
                try {
                    // NOTE: Not supporting "this" here because it doesn't make sense to have an updatable mapping that refers to this
                    // The only thing that might be interesting is supporting "this" when we support cascading as properties could be nested
                    // But not sure yet if the embeddable attributes would then be modeled as "updatable".
                    // I guess these attributes are not "updatable" but that probably depends on the decision regarding collections as they have a similar problem
                    // A collection itself might not be "updatable" but it's elements could be. This is roughly the same problem
                    mappingExpression.accept(visitor);
                    Map<javax.persistence.metamodel.Attribute<?, ?>, javax.persistence.metamodel.Type<?>> possibleTargets = visitor.getPossibleTargets();

                    if (possibleTargets.size() > 1) {
                        context.addError("Multiple possible target type for the mapping in the " + getLocation() + ": " + possibleTargets);
                    }

                    // TODO: maybe allow to override this per-attribute?
                    if (isDisallowOwnedUpdatableSubview()) {
                        for (Type<?> updateCascadeAllowedSubtype : getUpdateCascadeAllowedSubtypes()) {
                            ManagedViewType<?> managedViewType = (ManagedViewType<?>) updateCascadeAllowedSubtype;
                            if (managedViewType.isUpdatable()) {
                                context.addError("Invalid use of @UpdatableEntityView type '" + managedViewType.getJavaType().getName() + "' for the " + getLocation() + ". Consider using a read-only view type instead or use @AllowUpdatableEntityViews! " +
                                        "For further information on this topic, please consult the documentation https://persistence.blazebit.com/documentation/entity-view/manual/en_US/index.html#updatable-mappings-subview");
                            }
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    context.addError("There is an error for the " + getLocation() + ": " + ex.getMessage());
                }
            }
        }
    }

    protected abstract boolean isDisallowOwnedUpdatableSubview();

    public void checkNestedAttribute(List<AbstractAttribute<?, ?>> parents, ManagedType<?> managedType, MetamodelBuildingContext context, boolean hasMultisetParent) {
        if (hasMultisetParent) {
            if (getElementType() instanceof BasicTypeImpl<?>) {
                context.checkMultisetSupport(parents, this, ((BasicTypeImpl<?>) getElementType()).getUserType());
            }
        } else {
            hasMultisetParent = fetchStrategy == FetchStrategy.MULTISET;
        }
        if (!parents.isEmpty()) {
            if (getDeclaringType().getMappingType() == Type.MappingType.FLAT_VIEW) {
                // When this attribute is part of a flat view
                if (isCollection() && getFetchStrategy() == FetchStrategy.JOIN) {
                    // And is a join fetched collection
                    // We need to ensure it has at least one non-embedded parent
                    // Otherwise there is no identity which we can use to correlate the collection elements to
                    for (int i = parents.size() - 1; i >= 0; i--) {
                        AbstractAttribute<?, ?> parentAttribute = parents.get(i);
                        // If a parent attribute is a non-indexed collection, we bail out because that's an error
                        if (parentAttribute.isCollection() && !parentAttribute.isIndexed()) {
                            String path = parents.get(0).getDeclaringType().getJavaType().getName();
                            for (i = 0; i < parents.size(); i++) {
                                path += " > " + parents.get(i).getLocation();
                            }
                            context.addError("Illegal mapping of join fetched collection for the " + getLocation() + " via the path: " + path + ". Join fetched collections in flat views are only allowed for when the flat view is contained in an indexed collections or in a view.");
                            break;
                        }
                        // If the parent is a view with identity, having the collection is ok and we are done here
                        if (parentAttribute.getDeclaringType().getMappingType() == Type.MappingType.VIEW) {
                            break;
                        }
                    }
                }
            }
        }

        // Go into subtypes for nested checking
        if (isSubview()) {
            Map<ManagedViewTypeImplementor<?>, String> inheritanceSubtypeMappings = elementInheritanceSubtypeMappings();
            if (inheritanceSubtypeMappings.isEmpty()) {
                context.addError("Illegal empty inheritance subtype mappings for the " + getLocation() + ". Remove the @MappingInheritance annotation, set the 'onlySubtypes' attribute to false or add a @MappingInheritanceSubtype element!");
            }
            for (ManagedViewTypeImplementor<?> subviewType : inheritanceSubtypeMappings.keySet()) {
                parents.add(this);
                subviewType.checkNestedAttributes(parents, context, hasMultisetParent);
                parents.remove(parents.size() - 1);
            }

        }
        if (isKeySubview()) {
            Map<ManagedViewTypeImplementor<?>, String> inheritanceSubtypeMappings = keyInheritanceSubtypeMappings();
            if (inheritanceSubtypeMappings.isEmpty()) {
                context.addError("Illegal empty inheritance subtype mappings for the " + getLocation() + ". Remove the @MappingInheritance annotation, set the 'onlySubtypes' attribute to false or add a @MappingInheritanceSubtype element!");
            }
            for (ManagedViewTypeImplementor<?> subviewType : inheritanceSubtypeMappings.keySet()) {
                parents.add(this);
                subviewType.checkNestedAttributes(parents, context, hasMultisetParent);
                parents.remove(parents.size() - 1);
            }
        }
    }

    protected boolean isEmbedded() {
        return getDeclaringType().getMappingType() == Type.MappingType.FLAT_VIEW && "this".equals(mapping);
    }

    @SuppressWarnings("rawtypes")
    protected abstract Class[] getTypeArguments();

    public abstract String getLocation();

    public abstract boolean isUpdatable();

    public abstract boolean isMutable();

    public abstract String getMappedBy();

    public abstract boolean isUpdateCascaded();

    public abstract Set<Type<?>> getUpdateCascadeAllowedSubtypes();

    protected abstract boolean isIndexed();

    protected abstract boolean isSorted();

    protected abstract boolean isForcedUnique();

    protected abstract PluralAttribute.CollectionType getCollectionType();

    public abstract Type<?> getElementType();

    protected abstract Map<ManagedViewTypeImplementor<?>, String> elementInheritanceSubtypeMappings();

    protected abstract Type<?> getKeyType();

    protected abstract Map<ManagedViewTypeImplementor<?>, String> keyInheritanceSubtypeMappings();

    protected abstract boolean isKeySubview();

    public abstract Set<Class<?>> getAllowedSubtypes();

    public abstract Set<Class<?>> getParentRequiringUpdateSubtypes();

    public abstract Set<Class<?>> getParentRequiringCreateSubtypes();

    public abstract boolean isOptimizeCollectionActionsEnabled();

    public abstract CollectionInstantiatorImplementor<?, ?> getCollectionInstantiator();

    public abstract MapInstantiatorImplementor<?, ?> getMapInstantiator();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected final CollectionInstantiatorImplementor<?, ?> createCollectionInstantiator(MetamodelBuildingContext context, PluralObjectFactory<? extends Collection<?>> collectionFactory, boolean indexed, boolean sorted, boolean ordered, Comparator comparator) {
        if (indexed) {
            if (isForcedUnique()) {
                context.addError("Forcing uniqueness for indexed attribute is invalid at the " + getLocation());
            }
            if (comparator != null) {
                context.addError("Comparator can't be defined for indexed attribute at the " + getLocation());
            }
            return new ListCollectionInstantiator((PluralObjectFactory<Collection<?>>) collectionFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), true, isOptimizeCollectionActionsEnabled(), false, context.isStrictCascadingCheck(), null);
        } else {
            if (sorted) {
                return new SortedSetCollectionInstantiator((PluralObjectFactory<Collection<?>>) collectionFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), isOptimizeCollectionActionsEnabled(), context.isStrictCascadingCheck(), comparator);
            } else {
                if (getCollectionType() == PluralAttribute.CollectionType.SET) {
                    if (comparator != null) {
                        context.addError("Comparator can't be defined for non-sorted set attribute at the " + getLocation());
                    }
                    if (ordered) {
                        return new OrderedSetCollectionInstantiator((PluralObjectFactory<Collection<?>>) collectionFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), isOptimizeCollectionActionsEnabled(), context.isStrictCascadingCheck());
                    } else {
                        return new UnorderedSetCollectionInstantiator((PluralObjectFactory<Collection<?>>) collectionFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), isOptimizeCollectionActionsEnabled(), context.isStrictCascadingCheck());
                    }
                } else if (getCollectionType() == PluralAttribute.CollectionType.LIST) {
                    return new ListCollectionInstantiator((PluralObjectFactory<Collection<?>>) collectionFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), false, isOptimizeCollectionActionsEnabled(), isForcedUnique(), context.isStrictCascadingCheck(), comparator);
                } else {
                    return new OrderedCollectionInstantiator((PluralObjectFactory<Collection<?>>) collectionFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), isOptimizeCollectionActionsEnabled(), isForcedUnique(), context.isStrictCascadingCheck(), comparator);
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected final MapInstantiatorImplementor<?, ?> createMapInstantiator(MetamodelBuildingContext context, PluralObjectFactory<? extends Map<?, ?>> mapFactory, boolean sorted, boolean ordered, Comparator comparator) {
        if (sorted) {
            return new SortedMapInstantiator((PluralObjectFactory<Map<?, ?>>) mapFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), isOptimizeCollectionActionsEnabled(), context.isStrictCascadingCheck(), comparator);
        } else if (ordered) {
            return new OrderedMapInstantiator((PluralObjectFactory<Map<?, ?>>) mapFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), isOptimizeCollectionActionsEnabled(), context.isStrictCascadingCheck());
        } else {
            return new UnorderedMapInstantiator((PluralObjectFactory<Map<?, ?>>) mapFactory, getAllowedSubtypes(), getParentRequiringUpdateSubtypes(), getParentRequiringCreateSubtypes(), isUpdatable(), isOptimizeCollectionActionsEnabled(), context.isStrictCascadingCheck());
        }
    }

    @Override
    public final MappingType getMappingType() {
        return mappingType;
    }

    public final boolean isQueryParameter() {
        return mappingType == MappingType.PARAMETER;
    }

    public final boolean isId() {
        return id;
    }

    public final SubqueryProviderFactory getSubqueryProviderFactory() {
        return subqueryProviderFactory;
    }

    public final Class<? extends SubqueryProvider> getSubqueryProvider() {
        return subqueryProvider;
    }

    public final String getSubqueryExpression() {
        return subqueryExpression;
    }

    public final String getSubqueryAlias() {
        return subqueryAlias;
    }

    public CorrelationProviderFactory getCorrelationProviderFactory() {
        return correlationProviderFactory;
    }

    public final Class<? extends CorrelationProvider> getCorrelationProvider() {
        return correlationProvider;
    }

    public final String getCorrelationBasis() {
        return correlationBasis;
    }

    public final String getCorrelationResult() {
        return correlationResult;
    }

    public Expression getCorrelationBasisExpression() {
        return correlationBasisExpression;
    }

    public Expression getCorrelationResultExpression() {
        return correlationResultExpression;
    }

    public final FetchStrategy getFetchStrategy() {
        return fetchStrategy;
    }

    public final int getBatchSize() {
        return batchSize;
    }

    public final List<OrderByItem> getOrderByItems() {
        return orderByItems;
    }

    public final String getLimitExpression() {
        return limitExpression;
    }

    public final String getOffsetExpression() {
        return offsetExpression;
    }

    public final String getMapping() {
        return mapping;
    }

    @Override
    public final boolean isSubquery() {
        return mappingType == MappingType.SUBQUERY;
    }

    @Override
    public final ManagedViewTypeImplementor<X> getDeclaringType() {
        return declaringType;
    }

    @Override
    public final Class<Y> getJavaType() {
        return javaType;
    }

    @Override
    public Class<?> getConvertedJavaType() {
        return convertedJavaType;
    }

    @Override
    public final String[] getFetches() {
        return fetches;
    }
}
