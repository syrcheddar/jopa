package cz.cvut.kbss.jopa.query.sparql;

import cz.cvut.kbss.jopa.environment.OWLClassA;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.sessions.ConnectionWrapper;
import cz.cvut.kbss.jopa.sessions.UnitOfWorkImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SparqlQueryFactoryTest {

    private static final String QUERY = "SELECT ?x ?y ?z WHERE { ?x ?y ?z. }";
    private static final Class<OWLClassA> CLS = OWLClassA.class;

    @Mock
    private UnitOfWorkImpl uowMock;

    @Mock
    private ConnectionWrapper connectionMock;

    private SparqlQueryFactory factory;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(uowMock.useBackupOntologyForQueryProcessing()).thenReturn(Boolean.FALSE);
        when(uowMock.useTransactionalOntologyForQueryProcessing()).thenReturn(Boolean.TRUE);
        this.factory = new SparqlQueryFactory(uowMock, connectionMock);
    }

    @Test
    public void testCreateNativeQuery() {
        final Query q = factory.createNativeQuery(QUERY);
        assertNotNull(q);
        verify(uowMock).useBackupOntologyForQueryProcessing();
    }

    @Test(expected = NullPointerException.class)
    public void testCreateNativeQueryNull() {
        final Query q = factory.createNativeQuery(null);
        assert q == null;
    }

    @Test
    public void testCreateNativeQueryTyped() {
        final TypedQuery<OWLClassA> q = factory.createNativeQuery(QUERY, CLS);
        assertNotNull(q);
        verify(uowMock).useBackupOntologyForQueryProcessing();
    }

    @Test(expected = NullPointerException.class)
    public void testCreateNativeQueryTypedNullQuery() {
        final TypedQuery<OWLClassA> q = factory.createNativeQuery(null, CLS);
        assert q == null;
    }

    @Test(expected = NullPointerException.class)
    public void testCreateNativeQueryTypedNullType() {
        final TypedQuery<OWLClassA> q = factory.createNativeQuery(QUERY, null);
        assert q == null;
    }

    @Test
    public void testCreateQuery() {
        final Query q = factory.createQuery(QUERY);
        assertNotNull(q);
        verify(uowMock).useBackupOntologyForQueryProcessing();
    }

    @Test(expected = NullPointerException.class)
    public void testCreateQueryNull() {
        final Query q = factory.createQuery(null);
        assert q == null;
    }

    @Test
    public void testCreateQueryTyped() {
        final TypedQuery<OWLClassA> q = factory.createQuery(QUERY, CLS);
        assertNotNull(q);
        verify(uowMock).useBackupOntologyForQueryProcessing();
    }

    @Test(expected = NullPointerException.class)
    public void testCreateQueryTypedNullQuery() {
        final TypedQuery<OWLClassA> q = factory.createQuery(null, CLS);
        assert q == null;
    }

    @Test(expected = NullPointerException.class)
    public void testCreateQueryTypedNullType() {
        final TypedQuery<OWLClassA> q = factory.createQuery(QUERY, null);
        assert q == null;
    }
}