package cz.cvut.kbss.ontodriver.sesame.connector;

import cz.cvut.kbss.ontodriver.config.DriverConfiguration;
import cz.cvut.kbss.ontodriver.sesame.config.SesameConfigParam;
import cz.cvut.kbss.ontodriver.sesame.exceptions.RepositoryCreationException;
import cz.cvut.kbss.ontodriver.sesame.exceptions.RepositoryNotFoundException;
import cz.cvut.kbss.ontodriver.sesame.exceptions.SesameDriverException;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigSchema;
import org.eclipse.rdf4j.repository.manager.LocalRepositoryManager;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.eclipse.rdf4j.repository.manager.RepositoryManager;
import org.eclipse.rdf4j.repository.manager.RepositoryProvider;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.config.SailRepositoryConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.config.SailImplConfig;
import org.eclipse.rdf4j.sail.inferencer.fc.ForwardChainingRDFSInferencer;
import org.eclipse.rdf4j.sail.inferencer.fc.config.ForwardChainingRDFSInferencerConfig;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.nativerdf.config.NativeStoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.Set;

class RepositoryConnectorInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryConnectorInitializer.class);

    private static final String[] KNOWN_REMOTE_SCHEMES = {"http", "https", "ftp"};
    private static final String LOCAL_NATIVE_REPO = "repositories/";
    private static final String FILE_SCHEME = "file";
    private static final String CLASSPATH_PREFIX = "classpath:";

    private final DriverConfiguration configuration;

    private RepositoryManager manager;
    private Repository repository;

    RepositoryConnectorInitializer(DriverConfiguration configuration) {
        this.configuration = configuration;
    }

    void initializeRepository() throws SesameDriverException {
        final URI serverUri = configuration.getStorageProperties().getPhysicalURI();
        LOG.debug("Initializing connector to repository at {}", serverUri);
        try {
            final boolean isRemote = isRemoteRepository(serverUri);
            if (isRemote) {
                this.repository = connectToRemoteRepository(serverUri.toString());
            } else {
                this.repository = createLocalRepository();
            }
            verifyRepositoryCreated(serverUri, isRemote);
            repository.initialize();
        } catch (RepositoryException | RepositoryConfigException e) {
            throw new SesameDriverException("Failed to acquire sesame repository connection.", e);
        }
    }

    private static boolean isRemoteRepository(URI uri) {
        final String scheme = uri.getScheme();
        for (String s : KNOWN_REMOTE_SCHEMES) {
            if (s.equals(scheme)) {
                return true;
            }
        }
        return false;
    }

    private Repository connectToRemoteRepository(String repoUri) {
        this.manager = RepositoryProvider.getRepositoryManagerOfRepository(repoUri);
        final RemoteRepositoryManager remoteManager = (RemoteRepositoryManager) manager;
        final String username = configuration.getProperty(SesameConfigParam.USERNAME, "");
        if (!username.isEmpty()) {
            final String password = configuration.getProperty(SesameConfigParam.PASSWORD, "");
            remoteManager.setUsernameAndPassword(username, password);
        }
        return manager.getRepository(RepositoryProvider.getRepositoryIdOfRepository(repoUri));
    }

    private Repository createLocalRepository() {
        if (configuration.isSet(SesameConfigParam.REPOSITORY_CONFIG)) {
            return createRepositoryFromConfig();
        }
        final URI localUri = configuration.getStorageProperties().getPhysicalURI();
        if (!isFileUri(localUri) && configuration.is(SesameConfigParam.USE_VOLATILE_STORAGE)) {
            return createInMemoryRepository();
        } else {
            return createNativeRepository(configuration, localUri);
        }
    }

    private Repository createRepositoryFromConfig() {
        final RepositoryConfig repoConfig = loadRepositoryConfig();
        // TODO BaseDir should be resolved from physical URI configuration
        this.manager = new LocalRepositoryManager(null);
        manager.addRepositoryConfig(repoConfig);
        return manager.getRepository(configuration.getStorageProperties().getPhysicalURI().toString());
    }

    private RepositoryConfig loadRepositoryConfig() {
        try (final InputStream is = getConfigFileContent()) {
            final Model configModel = Rio.parse(is, "", RDFFormat.TURTLE);
            final Set<Resource> resources =
                    configModel.filter(null, RDF.TYPE, RepositoryConfigSchema.REPOSITORY).subjects();
            assert resources.size() == 1;
            return RepositoryConfig.create(configModel, resources.iterator().next());
        } catch (IOException e) {
            throw new RepositoryCreationException("Unable to create repository from the specified configuration.", e);
        }
    }

    private InputStream getConfigFileContent() {
        final String configPath = configuration.getProperty(SesameConfigParam.REPOSITORY_CONFIG);
        if (configPath.startsWith(CLASSPATH_PREFIX)) {
            final InputStream is =
                    getClass().getClassLoader().getResourceAsStream(configPath.substring(CLASSPATH_PREFIX.length()));
            if (is == null) {
                throw new RepositoryCreationException(
                        "Unable to find repository configuration file on classpath location " + configPath);
            }
            return is;
        } else {
            try {
                return new FileInputStream(new File(configPath));
            } catch (FileNotFoundException e) {
                throw new RepositoryCreationException("Unable to find repository configuration file at " + configPath,
                        e);
            }
        }
    }

    private static boolean isFileUri(URI uri) {
        return uri.getScheme() != null && uri.getScheme().equals(FILE_SCHEME);
    }

    /**
     * Creates a local in-memory Sesame repository which is disposed when the VM shuts down.
     */
    private Repository createInMemoryRepository() {
        LOG.trace("Creating local in-memory repository.");
        final MemoryStore ms = new MemoryStore();
        if (configuration.is(SesameConfigParam.USE_INFERENCE)) {
            return new SailRepository(new ForwardChainingRDFSInferencer(ms));
        } else {
            return new SailRepository(ms);
        }
    }

    /**
     * Creates native repository.
     * <p>
     * This kind of repository stores data in files and is persistent after the VM shuts down.
     */
    private Repository createNativeRepository(DriverConfiguration configuration, final URI localUri) {
        LOG.trace("Creating local native repository at " + localUri);
        final String[] tmp = localUri.toString().split(LOCAL_NATIVE_REPO);
        if (tmp.length != 2) {
            throw new RepositoryCreationException(
                    "Unsupported local Sesame repository path. Expected file://path/repositories/id but got "
                            + localUri);
        }
        String repoId = tmp[1];
        if (repoId.charAt(repoId.length() - 1) == '/') {
            repoId = repoId.substring(0, repoId.length() - 1);
        }
        try {
            this.manager = RepositoryProvider.getRepositoryManagerOfRepository(localUri.toASCIIString());
            final RepositoryConfig cfg = createLocalNativeRepositoryConfig(repoId, configuration);
            manager.addRepositoryConfig(cfg);
            return manager.getRepository(repoId);
        } catch (RepositoryConfigException | RepositoryException e) {
            throw new RepositoryCreationException("Unable to create local repository at " + localUri, e);
        }
    }

    private static RepositoryConfig createLocalNativeRepositoryConfig(String repoId,
                                                                      DriverConfiguration configuration) {
        SailImplConfig backend = new NativeStoreConfig();
        if (configuration.is(SesameConfigParam.USE_INFERENCE)) {
            backend = new ForwardChainingRDFSInferencerConfig(backend);
        }
        final SailRepositoryConfig repoType = new SailRepositoryConfig(backend);
        return new RepositoryConfig(repoId, repoType);
    }

    private void verifyRepositoryCreated(URI serverUri, boolean isRemote) {
        if (repository == null) {
            if (isRemote) {
                throw new RepositoryNotFoundException("Unable to reach repository at " + serverUri);
            } else {
                throw new RepositoryCreationException("Unable to create local repository at " + serverUri);
            }
        }
    }

    RepositoryManager getManager() {
        return manager;
    }

    Repository getRepository() {
        return repository;
    }
}
