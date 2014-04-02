package cz.cvut.kbss.jopa.sessions;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import org.semanticweb.owlapi.model.IRI;

import cz.cvut.kbss.jopa.adapters.IndirectCollection;
import cz.cvut.kbss.jopa.exceptions.OWLEntityExistsException;
import cz.cvut.kbss.jopa.exceptions.OWLInferredAttributeModifiedException;
import cz.cvut.kbss.jopa.exceptions.OWLPersistenceException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.Repository;
import cz.cvut.kbss.jopa.model.RepositoryID;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.Metamodel;
import cz.cvut.kbss.jopa.model.metamodel.PropertiesSpecification;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.owlapi.EntityManagerImpl.State;
import cz.cvut.kbss.jopa.utils.EntityPropertiesUtils;
import cz.cvut.kbss.ontodriver.Connection;
import cz.cvut.kbss.ontodriver.exceptions.MetamodelNotSetException;
import cz.cvut.kbss.ontodriver.exceptions.OntoDriverException;

public class UnitOfWorkImpl extends AbstractSession implements UnitOfWork, QueryFactory {

	private final Map<Object, Object> cloneMapping;
	private final Map<Object, Object> cloneToOriginals;
	private Map<Object, Object> deletedObjects;
	private Map<Object, Object> newObjectsCloneToOriginal;
	private Map<Object, Object> newObjectsOriginalToClone;
	private Map<Object, Object> newObjectsKeyToClone;
	private final RepositoryMap repositoryMap;

	private boolean hasChanges;
	private boolean hasNew;
	private boolean hasDeleted;
	private boolean shouldReleaseAfterCommit;
	private boolean shouldClearCacheAfterCommit;
	private boolean useTransactionalOntology;

	private boolean isActive;
	private boolean inCommit;
	private int repositoryCount;

	private UnitOfWorkChangeSet uowChangeSet;

	private AbstractSession parent;
	private EntityManager entityManager;
	private Connection storageConnection;

	private final MergeManager mergeManager;
	private final CloneBuilder cloneBuilder;
	private final ChangeManager changeManager;
	private final QueryFactory queryFactory;
	/**
	 * This is a shortcut for the second level cache. Performance reasons (to
	 * prevent server session method call chain).
	 */
	protected final CacheManager cacheManager;

	public UnitOfWorkImpl(AbstractSession parent) {
		this.parent = parent;
		this.cloneMapping = createMap();
		this.cloneToOriginals = createMap();
		this.repositoryCount = parent.getRepositories().size();
		this.repositoryMap = new RepositoryMap(repositoryCount);
		this.cloneBuilder = new CloneBuilderImpl(this);
		this.cacheManager = parent.getLiveObjectCache();
		this.storageConnection = acquireConnection();
		this.queryFactory = new QueryFactoryImpl(this, storageConnection);
		this.mergeManager = new MergeManagerImpl(this);
		this.changeManager = new ChangeManagerImpl();
		this.inCommit = false;
		this.useTransactionalOntology = true;
		this.isActive = true;
	}

	/**
	 * This method returns null, since we don't support nested Units of Work
	 * yet.
	 */
	@Override
	public UnitOfWork acquireUnitOfWork() {
		return null;
	}

	@Override
	protected Connection acquireConnection() {
		return parent.acquireConnection();
	}

	@Override
	public <T> T readObject(Class<T> cls, Object primaryKey, RepositoryID repository) {
		if (cls == null || primaryKey == null || repository == null) {
			throw new NullPointerException("Null passed to readObject. cls = " + cls
					+ ", primaryKey = " + primaryKey + ", repository = " + repository);
		}
		return readObjectInternal(cls, primaryKey, repository);
	}

	private <T> T readObjectInternal(Class<T> cls, Object primaryKey, RepositoryID repository) {
		assert cls != null;
		assert primaryKey != null;
		assert repository != null;
		// First try to find the object among new uncommitted objects
		Object result = getNewObjectsKeyToClone().get(primaryKey);
		if (result != null && (isInRepository(repository, result))) {
			// The result can be returned, since it is already registered in
			// this UOW
			return cls.cast(result);
		}
		// Make a copy of the repository identifier because it may be modified
		// by the subsequent operations
		repository = new RepositoryID(repository);
		// Search the cache
		result = getObjectFromCache(repository, cls, primaryKey);
		if (result == null) {
			// The object is not in the session cache, so search the ontology
			result = storageFind(cls, primaryKey, repository);
		}
		if (result == null) {
			return null;
		}
		Object clone = registerExistingObject(result, repository);
		checkForCollections(clone);
		registerWithContext(repository, clone);
		return cls.cast(clone);
	}

	/**
	 * This method calculates the changes that were to the registered entities
	 * and adds these changes into the given change set for future commit to the
	 * ontology.
	 * 
	 * @param changeSet
	 * @param registeredObjects
	 */
	protected void calculateChanges(UnitOfWorkChangeSet changeSet,
			Map<Object, Object> registeredObjects) {
		if (hasNew()) {
			calculateNewObjects(changeSet);
		}
		if (hasDeleted()) {
			for (Object clone : getDeletedObjects().keySet()) {
				Object original = cloneToOriginals.get(clone);
				if (original == null) {
					throw new OWLPersistenceException("Cannot find an original for clone!");
				}
				RepositoryID repo = getRepositoryID(clone);
				changeSet.addDeletedObject(ChangeSetFactory.createObjectChangeSet(original, clone,
						repo));
			}
		}
		if (hasChanges()) {
			try {
				for (Object clone : cloneMapping.keySet()) {
					if (getDeletedObjects().containsKey(clone)) {
						// Make sure deleted objects are not persisted again
						continue;
					}
					Object original = cloneToOriginals.get(clone);
					if (original == null && !getNewObjectsCloneToOriginal().containsKey(clone)) {
						throw new OWLPersistenceException("Cannot find an original for clone!");
					}
					if (original == null) {
						continue; // It was a new object
					}
					RepositoryID repo = getRepositoryID(clone);
					ObjectChangeSet chSet = ChangeSetFactory.createObjectChangeSet(original, clone,
							repo);
					final boolean anyChanges = changeManager.calculateChanges(chSet);
					if (anyChanges) {
						changeSet.addObjectChangeSet(chSet);
					}
				}
			} catch (IllegalAccessException e) {
				throw new OWLPersistenceException(e);
			} catch (IllegalArgumentException e) {
				throw new OWLPersistenceException(e);
			} catch (OWLInferredAttributeModifiedException e) {
				LOG.severe("Inferred attribute modified. This transaction won't be able to commit.");
				if (entityManager != null) {
					entityManager.getTransaction().setRollbackOnly();
				}
			}
		}
	}

	/**
	 * Create object change sets for the new objects and adds them into our
	 * UnitOfWorkChangeSet.
	 * 
	 * @param changeSet
	 *            UnitOfWorkChangeSet
	 */
	protected void calculateNewObjects(UnitOfWorkChangeSet changeSet) {
		if (changeSet == null) {
			return;
		}
		Iterator<?> it = getNewObjectsCloneToOriginal().keySet().iterator();
		while (it.hasNext()) {
			Object clone = it.next();
			final RepositoryID c = getRepositoryID(clone);
			Object original = getNewObjectsCloneToOriginal().get(clone);
			if (original == null) {
				original = this.cloneBuilder.buildClone(clone, c);
			}
			if (original == null || clone == null) {
				throw new OWLPersistenceException(
						"Error while calculating changes for new objects. Original or clone not found.");
			}
			getNewObjectsCloneToOriginal().put(clone, original);
			getNewObjectsOriginalToClone().put(original, clone);
			changeSet.addNewObjectChangeSet(ChangeSetFactory.createObjectChangeSet(original, clone,
					c));
		}
	}

	public void clear() {
		this.cloneMapping.clear();
		this.cloneToOriginals.clear();
		this.deletedObjects = null;
		this.newObjectsCloneToOriginal = null;
		this.newObjectsOriginalToClone = null;
		this.newObjectsKeyToClone = null;
		this.hasChanges = false;
		this.hasDeleted = false;
		this.hasNew = false;
	}

	public boolean contains(Object entity) {
		if (entity == null) {
			throw new NullPointerException();
		}
		return isObjectManaged(entity);
	}

	public void commit() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("UnitOfWork commit started.");
		}
		if (!isActive()) {
			throw new OWLPersistenceException("Cannot commit inactive Unit of Work!");
		}
		this.inCommit = true;
		commitUnitOfWork();
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("UnitOfWork commit finished.");
		}
	}

	public void rollback() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("UnitOfWork rollback started.");
		}
		if (!isActive()) {
			throw new OWLPersistenceException("Cannot rollback inactive Unit of Work!");
		}
		rollbackInternal();
		clear();
	}

	/**
	 * Commit this Unit of Work.
	 */
	protected void commitUnitOfWork() {
		commitToOntology();
		storageCommit();
		mergeChangesIntoParent();
		postCommit();
	}

	/**
	 * Clean up after the commit.
	 */
	private void postCommit() {
		// Remove indirect collections from clones
		for (Object clone : cloneMapping.keySet()) {
			removeIndirectCollections(clone);
		}
		getNewObjectsCloneToOriginal().clear();
		getNewObjectsOriginalToClone().clear();
		getNewObjectsKeyToClone().clear();
		getDeletedObjects().clear();
		cloneToOriginals.clear();
		cloneMapping.clear();
		repositoryMap.clear();
		this.hasChanges = false;
		this.hasDeleted = false;
		this.hasNew = false;
		this.inCommit = false;
		this.cloneBuilder.reset();
		this.uowChangeSet = null;
		if (shouldClearCacheAfterCommit) {
			cacheManager.acquireWriteLock();
			try {
				cacheManager.evictAll();
			} finally {
				cacheManager.releaseWriteLock();
			}
			this.shouldReleaseAfterCommit = true;
		}
	}

	/**
	 * If there are any changes, commit them to the ontology.
	 */
	protected void commitToOntology() {
		boolean hasChanges = this.hasNew || this.hasChanges || this.hasDeleted;
		if (hasChanges) {
			calculateChanges(getUowChangeSet(), cloneMapping);
		}
	}

	private void rollbackInternal() {
		try {
			storageConnection.rollback();
		} catch (OntoDriverException e) {
			throw new OWLPersistenceException(e);
		}
	}

	/**
	 * Creates and returns a new IdentityHashMap. This is a factory method.
	 * 
	 * @return Map<Object, Object>
	 */
	private Map<Object, Object> createMap() {
		return new IdentityHashMap<Object, Object>();
	}

	public AbstractSession getParent() {
		return parent;
	}

	/**
	 * Gets current state of the specified entity. </p>
	 * 
	 * Note that since no repository is specified we can only determine if the
	 * entity is managed or removed. Therefore if the case is different this
	 * method returns {@value State#NEW}.
	 * 
	 * @param entity
	 *            The entity to check
	 * @return State of the entity
	 */
	public State getState(Object entity) {
		if (entity == null) {
			throw new NullPointerException();
		}
		if (getDeletedObjects().containsKey(entity)) {
			return State.REMOVED;
		} else if (cloneMapping.containsKey(entity)
				&& !getNewObjectsCloneToOriginal().containsKey(entity)) {
			return State.MANAGED;
		} else {
			return State.NEW;
		}
	}

	/**
	 * Checks the state of the specified entity with regards to the specified
	 * repository.
	 * 
	 * @param entity
	 *            Object
	 * @param repository
	 *            Repository identifier
	 * @return The state of the specified entity
	 */
	public State getState(Object entity, RepositoryID repository) {
		if (entity == null || repository == null) {
			throw new NullPointerException();
		}
		if (getDeletedObjects().containsKey(entity)) {
			return State.REMOVED;
		} else if (getNewObjectsCloneToOriginal().containsKey(entity)
				&& isInRepository(repository, entity)) {
			return State.NEW;
		} else if (cloneMapping.containsKey(entity) && isInRepository(repository, entity)) {
			return State.MANAGED;
		} else if (storageContains(getIdentifier(entity), repository)) {
			// TODO Think of a different strategy here, we don't want to have to
			// ask the storage if it contains a detached object
			return State.DETACHED;
		} else {
			return State.NEW;
		}
	}

	/**
	 * Tries to find the original object for the given clone. It searches the
	 * existing objects, new objects and deleted objects.
	 * 
	 * @param clone
	 *            Object
	 * @return The original object for the given clone
	 */
	public Object getOriginal(Object clone) {
		if (clone == null) {
			return null;
		}
		Object original = cloneToOriginals.get(clone);
		if (original == null) {
			original = getNewObjectsCloneToOriginal().get(clone);
		}
		return original;
	}

	/**
	 * Check if this UnitOfWork contains this original entity. This method is
	 * used by the CloneBuilder so it does not have to clone already managed
	 * referenced objects.
	 * 
	 * @param entity
	 *            The original entity.
	 * @return True if the original is managed in this UnitOfWork.
	 */
	boolean containsOriginal(Object entity) {
		if (entity == null) {
			return false;
		}
		return cloneToOriginals.containsValue(entity);
	}

	/**
	 * Finds clone for the specified original. This method assumes that the
	 * original is managed in this persistence context (UnitOfWork). However, if
	 * not, this method just goes through all the managed objects and if it does
	 * not find match, returns null.
	 * 
	 * @param original
	 *            The original object whose clone we are looking for.
	 * @return The clone or null, if there is none.
	 */
	Object getCloneForOriginal(Object original) {
		Iterator<Entry<Object, Object>> it = cloneToOriginals.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Object, Object> entry = it.next();
			// We use IdentityMap, so we can use ==
			if (entry.getValue() == original) {
				return entry.getKey();
			}
		}
		return null;
	}

	public boolean hasDeleted() {
		return hasDeleted;
	}

	public boolean hasChanges() {
		return hasChanges || hasDeleted || hasNew;
	}

	public boolean hasNew() {
		return hasNew;
	}

	public void setHasChanges() {
		this.hasChanges = true;
	}

	public Map<Object, Object> getDeletedObjects() {
		if (deletedObjects == null) {
			this.deletedObjects = createMap();
		}
		return deletedObjects;
	}

	public Map<Object, Object> getNewObjectsCloneToOriginal() {
		if (newObjectsCloneToOriginal == null) {
			this.newObjectsCloneToOriginal = createMap();
		}
		return newObjectsCloneToOriginal;
	}

	public Map<Object, Object> getNewObjectsOriginalToClone() {
		if (newObjectsOriginalToClone == null) {
			this.newObjectsOriginalToClone = createMap();
		}
		return newObjectsOriginalToClone;
	}

	public Map<Object, Object> getNewObjectsKeyToClone() {
		if (newObjectsKeyToClone == null) {
			// Cannot use identity map, since it compares the key references
			// which may not be the same
			this.newObjectsKeyToClone = new HashMap<Object, Object>();
		}
		return newObjectsKeyToClone;
	}

	@Override
	public CacheManager getLiveObjectCache() {
		return parent.getLiveObjectCache();
	}

	/**
	 * Get a set of classes managed in this persistence unit.
	 * 
	 * @return Set of managed classes.
	 */
	public Set<Class<?>> getManagedTypes() {
		if (parent == null) {
			return Collections.emptySet();
		}
		return parent.getManagedTypes();
	}

	public MergeManager getMergeManager() {
		return mergeManager;
	}

	public UnitOfWorkChangeSet getUowChangeSet() {
		if (this.uowChangeSet == null) {
			this.uowChangeSet = ChangeSetFactory.createUoWChangeSet();
		}
		return uowChangeSet;
	}

	public boolean isActive() {
		return this.isActive;
	}

	/**
	 * Returns true if the given clone represents a newly created object.
	 * Otherwise returns false.
	 * 
	 * @param clone
	 *            Object
	 * @return boolean
	 */
	public boolean isObjectNew(Object clone) {
		if (clone == null)
			return false;
		return getNewObjectsCloneToOriginal().containsKey(clone);
	}

	/**
	 * Returns true if the given object is already managed.
	 * 
	 * @param entity
	 *            Object
	 * @return boolean
	 */
	public boolean isObjectManaged(Object entity) {
		if (entity == null) {
			throw new NullPointerException();
		}
		return (cloneMapping.containsKey(entity) && !getDeletedObjects().containsKey(entity));
	}

	private boolean doesEntityExist(Object entity, Object primaryKey, RepositoryID repository) {
		assert entity != null;
		assert repository != null;
		if (cloneMapping.containsKey(entity) && !getDeletedObjects().containsKey(entity)
				&& isInRepository(repository, entity)) {
			return true;
		}
		if (primaryKey != null) {
			getLiveObjectCache().acquireReadLock();
			try {
				return getLiveObjectCache().contains(repository, entity.getClass(), primaryKey);
			} finally {
				getLiveObjectCache().releaseReadLock();
			}
		}
		return false;
	}

	/**
	 * Persists changed value of the specified field.
	 * 
	 * @param entity
	 *            Entity with changes (the clone)
	 * @param f
	 *            The field whose value has changed
	 * @throws IllegalStateException
	 *             If this UoW is not in transaction
	 */
	public void attributeChanged(Object entity, Field f) {
		if (!isInTransaction()) {
			throw new IllegalStateException("This unit of work is not in a transaction.");
		}
		final RepositoryID repo = getRepositoryID(entity);
		if (repo == null) {
			throw new OWLPersistenceException("Unable to find repository for entity " + entity
					+ ". Is it registered in this UoW?");
		}
		storageMerge(getIdentifier(entity), entity, f, repo);
		setHasChanges();
		// Let's see how this works
		setIndirectCollectionIfPresent(entity, f);
	}

	/**
	 * Merge the changes from this Unit of Work's change set into the parent
	 * session and to the original objects. Also mark new objects as existing,
	 * since they are already persisted.
	 */
	public void mergeChangesIntoParent() {
		if (hasChanges()) {
			getLiveObjectCache().acquireWriteLock();
			try {
				mergeManager.mergeChangesFromChangeSet(getUowChangeSet());
			} finally {
				getLiveObjectCache().releaseWriteLock();
			}
		}
		// Mark new persistent object as existing and managed
		Iterator<?> it = getNewObjectsCloneToOriginal().keySet().iterator();
		while (it.hasNext()) {
			Object clone = it.next();
			Object original = getNewObjectsCloneToOriginal().get(clone);
			if (original != null) {
				getNewObjectsOriginalToClone().remove(original);
			}
			it.remove();
			// getNewObjectsCloneToOriginal().remove(clone);
			// Clones are already in cloneMapping, so just put them here
			cloneToOriginals.put(clone, original);
		}
		// Remove the clones and originals of the deleted objects from the
		// context
		Iterator<?> deletedIt = getUowChangeSet().getDeletedObjects().keySet().iterator();
		while (deletedIt.hasNext()) {
			ObjectChangeSet ochSet = (ObjectChangeSet) deletedIt.next();
			Object clone = ochSet.getCloneObject();
			cloneMapping.remove(clone);
			cloneToOriginals.remove(clone);
		}
	}

	@Override
	public <T> T mergeDetached(T entity, RepositoryID repository) {
		if (entity == null || repository == null) {
			throw new NullPointerException("Null passed to mergeDetached: entity = " + entity
					+ ", repository = " + repository);
		}
		return mergeDetachedInternal(entity, repository);
	}

	private <T> T mergeDetachedInternal(T entity, RepositoryID repository) {
		assert entity != null;
		final IRI iri = getIdentifier(entity);
		if (!storageContains(iri, repository)) {
			throw new OWLPersistenceException("Entity " + entity + " not found in repository "
					+ repository);
		}
		// This cast is OK, we just clone the entity instance
		final T clone = (T) registerExistingObject(entity, repository);

		// Propagate the entity's state into storage
		final EntityType<?> et = getMetamodel().entity(entity.getClass());
		for (Attribute<?, ?> att : et.getAttributes()) {
			storageMerge(iri, clone, att.getJavaField(), repository);
			setIndirectCollectionIfPresent(clone, att.getJavaField());
		}
		final TypesSpecification<?, ?> ts = et.getTypes();
		if (ts != null) {
			storageMerge(iri, clone, ts.getJavaField(), repository);
			setIndirectCollectionIfPresent(clone, ts.getJavaField());
		}
		final PropertiesSpecification<?, ?> ps = et.getProperties();
		if (ps != null) {
			storageMerge(iri, clone, ps.getJavaField(), repository);
			setIndirectCollectionIfPresent(clone, ps.getJavaField());
		}

		cacheManager.acquireReadLock();
		try {
			if (cacheManager.contains(repository, clone.getClass(), iri)) {
				cacheManager.releaseReadLock();
				cacheManager.acquireWriteLock();
				try {
					cacheManager.evict(repository, entity.getClass(), iri);
					cacheManager.acquireReadLock();
				} finally {
					cacheManager.releaseWriteLock();
				}
			}
		} finally {
			cacheManager.releaseReadLock();
		}
		setHasChanges();
		return clone;
	}

	/**
	 * {@inheritDoc}
	 */
	void registerEntityWithContext(Object entity, UnitOfWorkImpl uow) {
		parent.registerEntityWithContext(entity, uow);
	}

	/**
	 * {@inheritDoc}
	 */
	public Object registerExistingObject(Object object, RepositoryID repository) {
		if (object == null) {
			return null;
		}
		if (cloneToOriginals.containsValue(object)) {
			return getCloneForOriginal(object);
		}
		Object clone = this.cloneBuilder.buildClone(object, repository);
		cloneMapping.put(clone, clone);
		cloneToOriginals.put(clone, object);
		registerEntityWithContext(clone, this);
		return clone;
	}

	/**
	 * Release this Unit of Work. Releasing an active Unit of Work with
	 * uncommitted changes causes all pending changes to be discarded.
	 */
	public void release() {
		clear();
		if (storageConnection != null) {
			try {
				storageConnection.close();
			} catch (OntoDriverException e) {
				LOG.log(Level.SEVERE, "Exception caugth when closing connection.", e);
			}
		}
		this.isActive = false;
		if (LOG.isLoggable(Level.CONFIG)) {
			LOG.config("UnitOfWork released.");
		}
	}

	@Override
	public void revertObject(Object object) {
		final RepositoryID repo = getRepositoryID(object);
		if (repo == null) {
			throw new IllegalArgumentException("Unable to find entity " + object
					+ " in this persistence context.");
		}
		final ObjectChangeSet chSet = ChangeSetFactory.createObjectChangeSet(getOriginal(object),
				object, repo);
		try {
			final boolean anyChanges = changeManager.calculateChanges(chSet);
			if (anyChanges) {
				mergeManager.mergeChangesOnObject(object, chSet);
			}
		} catch (IllegalAccessException e) {
			throw new OWLPersistenceException(e);
		} catch (IllegalArgumentException e) {
			throw new OWLPersistenceException(e);
		} catch (OWLInferredAttributeModifiedException e) {
			throw new OWLPersistenceException(e);
		}
	}

	public void removeObjectFromCache(Object object, RepositoryID repository) {
		if (object == null || repository == null) {
			return;
		}
		final IRI primaryKey = getIdentifier(object);
		if (primaryKey == null) {
			return;
		}
		cacheManager.acquireWriteLock();
		cacheManager.evict(repository, object.getClass(), primaryKey);
		cacheManager.releaseWriteLock();
	}

	@Override
	public void registerNewObject(Object entity, RepositoryID repository) {
		if (entity == null || repository == null) {
			throw new NullPointerException("Null passed to registerNewObject: entity " + entity
					+ ", repository = " + repository);
		}
		registerNewObjectInternal(entity, repository);
	}

	/**
	 * Registers the specified entity for persist in this Unit of Work.
	 * 
	 * @param entity
	 *            The entity to register
	 * @param context
	 *            URI of context. Optional
	 */
	private void registerNewObjectInternal(Object entity, RepositoryID repository) {
		assert entity != null;
		IRI id = getIdentifier(entity);
		if (id == null) {
			// Check if the ID is generated
			final Class<?> cls = entity.getClass();
			final EntityType<?> eType = getMetamodel().entity(cls);
			if (!eType.getIdentifier().isGenerated()) {
				throw new OWLPersistenceException("The id for entity " + entity
						+ " is null and it is not specified as \'generated\' ");
			}
		}
		if ((doesEntityExist(entity, id, repository) || storageContains(id, repository))
				&& !entity.getClass().isEnum()) {
			throw new OWLEntityExistsException("An entity with URI " + id
					+ " is already persisted in repository " + repository);
		}
		storagePersist(id, entity, repository);
		if (id == null) {
			// If the ID was null, extract it from the entity
			// It is present now
			id = getIdentifier(entity);
		}
		Object clone = entity;
		// Original is null until commit
		Object original = null;
		cloneMapping.put(clone, clone);
		getNewObjectsCloneToOriginal().put(clone, original);
		registerEntityWithContext(clone, this);
		getNewObjectsKeyToClone().put(id, clone);
		checkForCollections(clone);
		this.hasNew = true;
	}

	/**
	 * Remove the specified entity from the ontology.
	 * 
	 * @param object
	 *            Clone of the object to delete
	 */
	public void removeObject(Object object) {
		if (object == null) {
			return;
		}
		if (!isObjectManaged(object)) {
			throw new IllegalArgumentException(
					"Cannot remove entity which is not managed in the current persistence context.");
		}
		if (getDeletedObjects().containsKey(object)) {
			return;
		}
		final Object primaryKey = getIdentifier(object);
		final RepositoryID repo = getRepositoryID(object);

		if (hasNew() && getNewObjectsCloneToOriginal().containsKey(object)) {
			unregisterObject(object);
			getNewObjectsKeyToClone().remove(primaryKey);
		} else {
			getDeletedObjects().put(object, object);
			this.hasDeleted = true;
		}
		storageRemove(primaryKey, object, repo);
	}

	/**
	 * Remove the registered object from this Unit of Work.
	 * 
	 * @param object
	 *            Clone of the original object
	 */
	public void unregisterObject(Object object) {
		if (object == null) {
			return;
		}
		cloneMapping.remove(object);
		cloneToOriginals.remove(object);

		getDeletedObjects().remove(object);
		if (hasNew()) {
			Object newOriginal = getNewObjectsCloneToOriginal().remove(object);
			if (newOriginal != null) {
				getNewObjectsOriginalToClone().remove(newOriginal);
			}
		}
		removeIndirectCollections(object);
	}

	public boolean shouldReleaseAfterCommit() {
		return shouldReleaseAfterCommit;
	}

	public void setShouldClearAfterCommit(boolean shouldClearCache) {
		this.shouldClearCacheAfterCommit = shouldClearCache;
	}

	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public void writeUncommittedChanges() {
		if (!hasChanges()) {
			return;
		}
		commitUnitOfWork();
	}

	@Override
	Metamodel getMetamodel() {
		return parent.getMetamodel();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isInTransaction() {
		if (entityManager == null) {
			return false;
		}
		return entityManager.getTransaction().isActive();
	}

	/**
	 * Returns {@code true} if this UoW is currently committing changes.
	 * 
	 * @return
	 */
	public boolean isInCommit() {
		return inCommit;
	}

	/**
	 * Checks whether the specified {@code cls} is an entity type.
	 * 
	 * @param cls
	 *            Class
	 * @return {@code true} if the {@code cls} is a managed type, {@code false}
	 *         otherwise
	 */
	public boolean isManagedType(Class<?> cls) {
		if (cls == null) {
			return false;
		}
		return getManagedTypes().contains(cls);
	}

	/**
	 * Loads lazily loaded field on the specified entity. </p>
	 * 
	 * @param entity
	 *            Entity
	 * @param field
	 *            The field to load
	 * @throws NullPointerException
	 *             If {@code entity} or {@code fieldName} is {@code null}
	 * @throws OWLPersistenceException
	 *             If an error during loading occurs
	 */
	public <T> void loadEntityField(T entity, Field field) {
		if (entity == null || field == null) {
			throw new NullPointerException();
		}
		try {
			if (field.get(entity) != null) {
				return;
			}
			final RepositoryID repository = getRepositoryID(entity);
			if (repository == null) {
				throw new OWLPersistenceException(
						"Unable to find repository identifier for entity " + entity
								+ ". Is it managed by this UoW?");
			}
			storageConnection.loadFieldValue(entity, field, repository);
			final Class<?> cls = field.getType();
			final Object orig = field.get(entity);
			final Object entityOriginal = getOriginal(entity);
			if (entityOriginal != null) {
				field.set(entityOriginal, orig);
			}
			Object clone;
			if (orig == null) {
				clone = null;
			} else {
				if (isManagedType(cls)) {
					clone = registerExistingObject(orig, repository);
				} else {
					clone = cloneBuilder.buildClone(entity, field, orig, repository);
				}
			}
			field.set(entity, clone);
		} catch (OntoDriverException e) {
			throw new OWLPersistenceException(e);
		} catch (IllegalArgumentException e) {
			throw new OWLPersistenceException(e);
		} catch (IllegalAccessException e) {
			throw new OWLPersistenceException(e);
		}
	}

	@Override
	public List<Repository> getRepositories() {
		// Server session has them cached
		return parent.getRepositories();
	}

	@Override
	public boolean checkConsistency(RepositoryID repository) {
		if (repository == null) {
			throw new NullPointerException();
		}
		try {
			return storageConnection.isConsistent(repository);
		} catch (OntoDriverException e) {
			throw new OWLPersistenceException(e);
		}
	}

	@Override
	public void setUseTransactionalOntologyForQueryProcessing() {
		this.useTransactionalOntology = true;
	}

	@Override
	public boolean useTransactionalOntologyForQueryProcessing() {
		return useTransactionalOntology;
	}

	@Override
	public void setUseBackupOntologyForQueryProcessing() {
		this.useTransactionalOntology = false;
	}

	@Override
	public boolean useBackupOntologyForQueryProcessing() {
		return !useTransactionalOntology;
	}

	@Override
	public Query<List<String>> createNativeQuery(String sparql, RepositoryID repository) {
		return queryFactory.createNativeQuery(sparql, repository);
	}

	@Override
	public <T> TypedQuery<T> createNativeQuery(String sparql, Class<T> resultClass,
			RepositoryID repository) {
		return queryFactory.createNativeQuery(sparql, resultClass, repository);
	}

	@Override
	public Query createQuery(String query, RepositoryID repository) {
		return queryFactory.createQuery(query, repository);
	}

	@Override
	public <T> TypedQuery<T> createQuery(String query, Class<T> resultClass, RepositoryID repository) {
		return queryFactory.createQuery(query, resultClass, repository);
	}

	/**
	 * Check if the specified entity contains a collection. If so, replace it
	 * with its indirect representation so that changes in that collection can
	 * be tracked.
	 * 
	 * @param entity
	 *            The entity to check
	 */
	private void checkForCollections(Object entity) {
		Field[] fields = entity.getClass().getDeclaredFields();
		for (Field f : fields) {
			setIndirectCollectionIfPresent(entity, f);
		}
	}

	/**
	 * Create and set indirect collection on the specified entity field.</p>
	 * 
	 * If the specified field is of Collection type and it is not already an
	 * indirect collection, create new one and set it as the value of the
	 * specified field on the specified entity.
	 * 
	 * @param entity
	 *            The entity collection will be set on
	 * @param field
	 *            The field to set
	 * @throws IllegalArgumentException
	 *             Reflection
	 * @throws IllegalAccessException
	 *             Reflection
	 */
	public void setIndirectCollectionIfPresent(Object entity, Field field) {
		if (entity == null || field == null) {
			throw new NullPointerException();
		}
		if (!field.isAccessible()) {
			field.setAccessible(true);
		}
		try {
			Object value = field.get(entity);
			Object indirectCollection = null;
			if (value == null || value instanceof IndirectCollection) {
				return;
			}
			if (value instanceof Collection || value instanceof Map) {
				indirectCollection = ((CloneBuilderImpl) cloneBuilder).createIndirectCollection(
						value, entity, field);
				field.set(entity, indirectCollection);
			}
		} catch (IllegalAccessException e) {
			LOG.severe("Unable to set indirect collection on entity " + entity);
			throw new OWLPersistenceException(e);
		}
	}

	/**
	 * Remove indirect collection implementations from the specified entity (if
	 * present).
	 * 
	 * @param entity
	 *            The entity to remove indirect collections from
	 */
	private void removeIndirectCollections(Object entity) {
		Field[] fields = entity.getClass().getDeclaredFields();
		try {
			for (Field f : fields) {
				if (!f.isAccessible()) {
					f.setAccessible(true);
				}
				final Object ob = f.get(entity);
				if (ob == null) {
					continue;
				}
				if (ob instanceof IndirectCollection) {
					IndirectCollection<?> indCol = (IndirectCollection<?>) ob;
					f.set(entity, indCol.getReferencedCollection());
				}
			}
		} catch (IllegalAccessException e) {
			LOG.severe("Unable to set indirect collection on entity " + entity);
			throw new OWLPersistenceException(e);
		}
	}

	/**
	 * Get entity with the specified primary key from the cache. </p>
	 * 
	 * If the cache does not contain any object with the specified primary key
	 * and class, null is returned. This method is just a delegate for the cache
	 * methods, it handles locks.
	 * 
	 * @param contextUri
	 * @param cls
	 * @param primaryKey
	 * @return Cached object or null
	 */
	private <T> T getObjectFromCache(RepositoryID repository, Class<T> cls, Object primaryKey) {
		assert cls != null;
		assert primaryKey != null;
		cacheManager.acquireReadLock();
		try {
			final T entity = cacheManager.get(repository, cls, primaryKey);
			return entity;
		} finally {
			cacheManager.releaseReadLock();
		}
	}

	void putObjectIntoCache(Object primaryKey, Object entity, RepositoryID repository) {
		cacheManager.acquireWriteLock();
		try {
			cacheManager.add(repository, primaryKey, entity);
		} finally {
			cacheManager.releaseWriteLock();
		}
	}

	private IRI getIdentifier(Object entity) {
		assert entity != null;
		return EntityPropertiesUtils.getPrimaryKey(entity, getMetamodel());
	}

	private void registerWithContext(RepositoryID repository, Object entity) {
		assert repository != null;
		assert entity != null;
		assert repository.getRepository() < repositoryCount;

		repositoryMap.add(repository, entity, null);
		repositoryMap.addEntityToRepository(entity, repository);
	}

	private boolean isInRepository(RepositoryID repository, Object entity) {
		assert repository != null;
		assert entity != null;
		assert repository.getRepository() < repositoryCount;

		if (repository.getContexts().isEmpty()) {
			return false;
		}
		return repositoryMap.contains(repository, entity);
	}

	private RepositoryID getRepositoryID(Object entity) {
		assert entity != null;

		return repositoryMap.getRepositoryID(entity);
	}

	private boolean storageContains(Object primaryKey, RepositoryID repository) {
		if (primaryKey == null || repository == null) {
			return false;
		}
		try {
			return storageConnection.contains(primaryKey, repository);
		} catch (OntoDriverException e) {
			throw new OWLPersistenceException(e);
		}
	}

	private <T> T storageFind(Class<T> cls, Object primaryKey, RepositoryID repository) {
		assert cls != null;
		assert primaryKey != null;
		try {
			final T result = storageConnection.find(cls, primaryKey, repository);
			if (result != null) {
				putObjectIntoCache(primaryKey, result, repository);
			}
			return result;
		} catch (MetamodelNotSetException e) {
			throw new OWLPersistenceException(e);
		} catch (OntoDriverException e) {
			throw new OWLPersistenceException(e);
		}
	}

	private <T> void storageMerge(Object primaryKey, T entity, Field field, RepositoryID repository) {
		assert primaryKey != null;
		assert entity != null;
		assert repository != null;
		try {
			storageConnection.merge(primaryKey, entity, field, repository);
		} catch (MetamodelNotSetException e) {
			throw new OWLPersistenceException(e);
		} catch (OntoDriverException e) {
			throw new OWLPersistenceException(e);
		}
	}

	private <T> void storagePersist(Object primaryKey, T entity, RepositoryID repository) {
		assert entity != null;
		assert repository != null;
		try {
			storageConnection.persist(primaryKey, entity, repository);
		} catch (MetamodelNotSetException e) {
			throw new OWLPersistenceException(e);
		} catch (OntoDriverException e) {
			throw new OWLPersistenceException(e);
		}
	}

	private <T> void storageRemove(Object primaryKey, T entity, RepositoryID repository) {
		assert primaryKey != null;
		assert entity != null;
		assert repository != null;
		try {
			storageConnection.remove(primaryKey, repository);
		} catch (MetamodelNotSetException e) {
			throw new OWLPersistenceException(e);
		} catch (OntoDriverException e) {
			throw new OWLPersistenceException(e);
		}
	}

	private void storageCommit() {
		try {
			storageConnection.commit();
		} catch (Exception e) {
			entityManager.removeCurrentPersistenceContext();
			throw new OWLPersistenceException(e);
		}
	}
}
