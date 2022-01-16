/**
 * Copyright (C) 2022 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.jopa.query.criteria.expressions;

import cz.cvut.kbss.jopa.model.query.criteria.Expression;
import cz.cvut.kbss.jopa.model.query.criteria.Order;

public class OrderImpl implements Order {
    private final Expression<?> expression;
    private boolean ascending;

    public OrderImpl(Expression<?> expression, boolean ascending) {
        this.expression = expression;
        this.ascending = ascending;
    }

    public OrderImpl(Expression<?> expression) {
        this.expression = expression;
        this.ascending = true;
    }

    @Override
    public Expression<?> getExpression() {
        return expression;
    }

    @Override
    public boolean isAscending() {
        return ascending;
    }

    @Override
    public Order reverse() {
        ascending = !ascending;
        return this;
    }
}