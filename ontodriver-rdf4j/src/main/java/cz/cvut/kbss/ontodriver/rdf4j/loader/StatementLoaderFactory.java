package cz.cvut.kbss.ontodriver.rdf4j.loader;

import cz.cvut.kbss.ontodriver.rdf4j.connector.Connector;
import cz.cvut.kbss.ontodriver.rdf4j.util.AxiomBuilder;
import org.eclipse.rdf4j.model.Resource;

public interface StatementLoaderFactory {

    StatementLoader create(Connector connector, Resource subject, AxiomBuilder axiomBuilder);
}
