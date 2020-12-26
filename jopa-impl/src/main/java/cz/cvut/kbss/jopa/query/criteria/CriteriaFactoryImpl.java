package cz.cvut.kbss.jopa.query.criteria;

import cz.cvut.kbss.jopa.model.CriteriaQueryImpl;
import cz.cvut.kbss.jopa.model.QueryImpl;
import cz.cvut.kbss.jopa.model.query.criteria.CriteriaQuery;
import cz.cvut.kbss.jopa.sessions.ConnectionWrapper;
import cz.cvut.kbss.jopa.sessions.CriteriaFactory;
import cz.cvut.kbss.jopa.sessions.UnitOfWorkImpl;
import cz.cvut.kbss.jopa.utils.ErrorUtils;

import java.util.Objects;

public class CriteriaFactoryImpl implements CriteriaFactory {

    private final UnitOfWorkImpl uow;
    private final ConnectionWrapper connection;

    public CriteriaFactoryImpl(UnitOfWorkImpl uow, ConnectionWrapper connection) {
        assert uow != null;
        assert connection != null;
        this.uow = uow;
        this.connection = connection;
    }

    @Override
    public <X> CriteriaQuery<X> from(Class<X> resultClass) {
        CriteriaQueryHolder<X> criteriaQueryHolder = new CriteriaQueryHolder<X>(resultClass);
        return new CriteriaQueryImpl<X>(criteriaQueryHolder, connection);
    }

}
