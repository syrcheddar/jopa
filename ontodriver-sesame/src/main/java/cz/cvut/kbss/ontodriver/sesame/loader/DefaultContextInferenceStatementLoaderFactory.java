package cz.cvut.kbss.ontodriver.sesame.loader;

import cz.cvut.kbss.ontodriver.sesame.connector.Connector;
import cz.cvut.kbss.ontodriver.sesame.util.AxiomBuilder;
import org.eclipse.rdf4j.model.Resource;

public class DefaultContextInferenceStatementLoaderFactory implements StatementLoaderFactory {

    @Override
    public StatementLoader create(Connector connector, Resource subject, AxiomBuilder axiomBuilder) {
        return new DefaultContextInferenceStatementLoader(connector, subject, axiomBuilder);
    }
}
