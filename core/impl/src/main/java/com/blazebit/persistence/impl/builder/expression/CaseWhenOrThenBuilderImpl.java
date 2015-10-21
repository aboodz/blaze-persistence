/*
 * Copyright 2014 Blazebit.
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
package com.blazebit.persistence.impl.builder.expression;

import com.blazebit.persistence.CaseWhenAndBuilder;
import com.blazebit.persistence.CaseWhenBuilder;
import com.blazebit.persistence.CaseWhenOrThenBuilder;
import com.blazebit.persistence.RestrictionBuilder;
import com.blazebit.persistence.SubqueryInitiator;
import com.blazebit.persistence.impl.ParameterManager;
import com.blazebit.persistence.impl.SubqueryBuilderListenerImpl;
import com.blazebit.persistence.impl.SubqueryInitiatorFactory;
import com.blazebit.persistence.impl.builder.predicate.LeftHandsideSubqueryPredicateBuilderListener;
import com.blazebit.persistence.impl.builder.predicate.PredicateBuilderEndedListenerImpl;
import com.blazebit.persistence.impl.builder.predicate.RestrictionBuilderImpl;
import com.blazebit.persistence.impl.builder.predicate.RightHandsideSubqueryPredicateBuilder;
import com.blazebit.persistence.impl.builder.predicate.SuperExpressionLeftHandsideSubqueryPredicateBuilder;
import com.blazebit.persistence.impl.expression.Expression;
import com.blazebit.persistence.impl.expression.ExpressionFactory;
import com.blazebit.persistence.impl.expression.WhenClauseExpression;
import com.blazebit.persistence.impl.predicate.ExistsPredicate;
import com.blazebit.persistence.impl.predicate.OrPredicate;
import com.blazebit.persistence.impl.predicate.PredicateBuilder;

/**
 *
 * @author Christian Beikov
 * @author Moritz Becker
 * @since 1.0
 */
public class CaseWhenOrThenBuilderImpl<T extends CaseWhenBuilder<?>> extends PredicateBuilderEndedListenerImpl implements CaseWhenOrThenBuilder<T>, ExpressionBuilder {

    private final T result;
    private final SubqueryInitiatorFactory subqueryInitFactory;
    private final ExpressionFactory expressionFactory;
    private final ParameterManager parameterManager;
    private final OrPredicate predicate = new OrPredicate();
    private final ExpressionBuilderEndedListener listener;
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final SubqueryBuilderListenerImpl<RestrictionBuilder<CaseWhenOrThenBuilder<T>>> leftSubqueryPredicateBuilderListener = new LeftHandsideSubqueryPredicateBuilderListener();
    private WhenClauseExpression whenClause;

    public CaseWhenOrThenBuilderImpl(T result, ExpressionBuilderEndedListener listener, SubqueryInitiatorFactory subqueryInitFactory, ExpressionFactory expressionFactory, ParameterManager parameterManager) {
        this.result = result;
        this.subqueryInitFactory = subqueryInitFactory;
        this.expressionFactory = expressionFactory;
        this.parameterManager = parameterManager;
        this.listener = listener;
    }

    @Override
    public RestrictionBuilder<CaseWhenOrThenBuilder<T>> or(String expression) {
        Expression expr = expressionFactory.createSimpleExpression(expression);
        return startBuilder(new RestrictionBuilderImpl<CaseWhenOrThenBuilder<T>>(this, this, expr, subqueryInitFactory, expressionFactory, parameterManager));
    }

    @Override
    public SubqueryInitiator<RestrictionBuilder<CaseWhenOrThenBuilder<T>>> orSubquery() {
        RestrictionBuilder<CaseWhenOrThenBuilder<T>> restrictionBuilder = startBuilder(new RestrictionBuilderImpl<CaseWhenOrThenBuilder<T>>(this, this, subqueryInitFactory, expressionFactory, parameterManager));
        return subqueryInitFactory.createSubqueryInitiator(restrictionBuilder, leftSubqueryPredicateBuilderListener);
    }

    @Override
    public SubqueryInitiator<RestrictionBuilder<CaseWhenOrThenBuilder<T>>> orSubquery(String subqueryAlias, String expression) {
        @SuppressWarnings({ "rawtypes", "unchecked" })
        SubqueryBuilderListenerImpl<RestrictionBuilder<CaseWhenOrThenBuilder<T>>> superExprLeftSubqueryPredicateBuilderListener = new SuperExpressionLeftHandsideSubqueryPredicateBuilder(subqueryAlias, expressionFactory.createSimpleExpression(expression));
        RestrictionBuilder<CaseWhenOrThenBuilder<T>> restrictionBuilder = startBuilder(new RestrictionBuilderImpl<CaseWhenOrThenBuilder<T>>(this, this, subqueryInitFactory, expressionFactory, parameterManager));
        return subqueryInitFactory.createSubqueryInitiator(restrictionBuilder, superExprLeftSubqueryPredicateBuilderListener);
    }

    @Override
    public SubqueryInitiator<CaseWhenOrThenBuilder<T>> orExists() {
        SubqueryBuilderListenerImpl<CaseWhenOrThenBuilder<T>> rightSubqueryPredicateBuilderListener = startBuilder(new RightHandsideSubqueryPredicateBuilder<CaseWhenOrThenBuilder<T>>(this, new ExistsPredicate()));
        return subqueryInitFactory.createSubqueryInitiator((CaseWhenOrThenBuilder<T>) this, rightSubqueryPredicateBuilderListener);
    }

    @Override
    public SubqueryInitiator<CaseWhenOrThenBuilder<T>> orNotExists() {
        SubqueryBuilderListenerImpl<CaseWhenOrThenBuilder<T>> rightSubqueryPredicateBuilderListener = startBuilder(new RightHandsideSubqueryPredicateBuilder<CaseWhenOrThenBuilder<T>>(this, new ExistsPredicate(true)));
        return subqueryInitFactory.createSubqueryInitiator((CaseWhenOrThenBuilder<T>) this, rightSubqueryPredicateBuilderListener);
    }

    @Override
    public CaseWhenAndBuilder<CaseWhenOrThenBuilder<T>> and() {
        return startBuilder(new CaseWhenAndBuilderImpl<CaseWhenOrThenBuilder<T>>(this, this, subqueryInitFactory, expressionFactory, parameterManager));
    }

    @Override
    public T thenExpression(String expression) {
        verifyBuilderEnded();
        if (predicate.getChildren().isEmpty()) {
            throw new IllegalStateException("No or clauses specified!");
        }
        if (whenClause != null) {
            throw new IllegalStateException("Method then/thenExpression called multiple times");
        }
        whenClause = new WhenClauseExpression(predicate, expressionFactory.createScalarExpression(expression));
        listener.onBuilderEnded(this);
        return result;
    }

    @Override
    public T then(Object value) {
        verifyBuilderEnded();
        if (predicate.getChildren().isEmpty()) {
            throw new IllegalStateException("No or clauses specified!");
        }
        if (whenClause != null) {
            throw new IllegalStateException("Method then/thenExpression called multiple times");
        }
        whenClause = new WhenClauseExpression(predicate, parameterManager.addParameterExpression(value));
        listener.onBuilderEnded(this);
        return result;
    }

    @Override
    public void onBuilderEnded(PredicateBuilder builder) {
        super.onBuilderEnded(builder);
        predicate.getChildren().add(builder.getPredicate());
    }

    @Override
    public Expression getExpression() {
        return whenClause;
    }

}
