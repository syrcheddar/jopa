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
package cz.cvut.kbss.jopa.transactions;

import cz.cvut.kbss.jopa.model.AbstractEntityManager;
import cz.cvut.kbss.jopa.sessions.UnitOfWork;

public class EntityTransactionWrapper extends TransactionWrapperImpl {

    private EntityTransaction entityTransaction;

    public EntityTransactionWrapper(AbstractEntityManager entityManger) {
        super(entityManger);
    }

    @Override
    public Object checkForTransaction() {
        if (entityTransaction != null && entityTransaction.isActive()) {
            return entityTransaction;
        }
        return null;
    }

    @Override
    public void registerUOWWithTransaction(UnitOfWork uow) {
        // Do nothing
    }

    @Override
    public EntityTransaction getTransaction() {
        if (entityTransaction == null) {
            entityTransaction = new EntityTransactionImpl(this);
        }
        return entityTransaction;
    }

    void begin() {
        setTransactionUOW(getEntityManager().getCurrentPersistenceContext());
    }
}
