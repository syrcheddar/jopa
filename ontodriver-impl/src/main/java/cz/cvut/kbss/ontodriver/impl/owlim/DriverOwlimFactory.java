package cz.cvut.kbss.ontodriver.impl.owlim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import cz.cvut.kbss.ontodriver.JopaStatement;
import cz.cvut.kbss.ontodriver.Context;
import cz.cvut.kbss.ontodriver.DriverAbstractFactory;
import cz.cvut.kbss.ontodriver.DriverStatement;
import cz.cvut.kbss.ontodriver.OntologyStorageProperties;
import cz.cvut.kbss.ontodriver.OwlimOntologyStorageProperties;
import cz.cvut.kbss.ontodriver.PersistenceProviderFacade;
import cz.cvut.kbss.ontodriver.StorageConnector;
import cz.cvut.kbss.ontodriver.StorageModule;
import cz.cvut.kbss.ontodriver.exceptions.OntoDriverException;
import cz.cvut.kbss.ontodriver.impl.jena.JenaCachingStorageConnector;
import cz.cvut.kbss.ontodriver.impl.jena.OwlapiBasedJenaConnector;
import cz.cvut.kbss.ontodriver.impl.owlapi.OwlapiBasedCachingJenaModule;
import cz.cvut.kbss.ontodriver.impl.owlapi.OwlapiStatement;

/**
 * Factory for OWLIM storage connectors. </p>
 * 
 * The current version uses OWLIM's Jena connector and supports only local
 * repositories.
 * 
 * @author kidney
 * 
 */
public class DriverOwlimFactory extends DriverAbstractFactory {

	private final Map<Context, OwlapiBasedJenaConnector> centralConnectors;

	public DriverOwlimFactory(List<Context> contexts,
			Map<Context, OntologyStorageProperties> ctxsToProperties, Map<String, String> properties)
			throws OntoDriverException {
		super(contexts, ctxsToProperties, properties);
		this.centralConnectors = new HashMap<Context, OwlapiBasedJenaConnector>();
	}

	@Override
	public void close() throws OntoDriverException {
		if (!isOpen()) {
			return;
		}
		super.close();
		for (OwlapiBasedJenaConnector c : centralConnectors.values()) {
			c.close();
		}
	}

	@Override
	public StorageModule createStorageModule(Context ctx,
			PersistenceProviderFacade persistenceProvider, boolean autoCommit)
			throws OntoDriverException {
		ensureState(ctx, persistenceProvider);
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Creating caching Jena storage module.");
		}
		final StorageModule m = new OwlapiBasedCachingJenaModule(ctx, persistenceProvider, this);
		registerModule(m);
		return m;
	}

	@Override
	public StorageConnector createStorageConnector(Context ctx, boolean autoCommit)
			throws OntoDriverException {
		ensureState(ctx);
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Creating OWLIM storage connector.");
		}
		return createConnectorInternal(ctx);
	}

	private synchronized OwlapiBasedJenaConnector createConnectorInternal(Context ctx)
			throws OntoDriverException {
		assert ctx != null;
		if (!centralConnectors.containsKey(ctx)) {
			createCentralConnector(ctx);
		}
		final JenaCachingStorageConnector conn = new JenaCachingStorageConnector(
				centralConnectors.get(ctx));
		registerConnector(conn);
		return conn;
	}

	private void createCentralConnector(Context ctx) throws OntoDriverException {
		final OntologyStorageProperties p = contextsToProperties.get(ctx);
		if (!(p instanceof OwlimOntologyStorageProperties)) {
			throw new OntoDriverException(
					"The storage properties is not suitable for OWLIM based connectors.");
		}
		final OwlimOntologyStorageProperties owlimP = (OwlimOntologyStorageProperties) p;
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("Creating central OWLIM storage connector.");
		}
		final JenaBasedOwlimConnector c = new JenaBasedOwlimConnector(owlimP, properties);
		centralConnectors.put(ctx, c);
	}

	@Override
	public DriverStatement createStatement(JopaStatement statement) throws OntoDriverException {
		if (statement == null) {
			throw new NullPointerException();
		}
		return new OwlapiStatement(statement);
	}
}