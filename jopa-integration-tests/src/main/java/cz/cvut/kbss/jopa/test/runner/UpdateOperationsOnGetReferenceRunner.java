package cz.cvut.kbss.jopa.test.runner;

import cz.cvut.kbss.jopa.test.*;
import cz.cvut.kbss.jopa.test.environment.DataAccessor;
import cz.cvut.kbss.jopa.test.environment.Generators;
import cz.cvut.kbss.jopa.test.environment.PersistenceFactory;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public abstract class UpdateOperationsOnGetReferenceRunner extends BaseRunner {

    protected UpdateOperationsOnGetReferenceRunner(Logger logger, PersistenceFactory persistenceFactory,
                                                   DataAccessor dataAccessor) {
        super(logger, persistenceFactory, dataAccessor);
    }

    @Test
    public void getReferenceResultCanBeUsedAsAttributeValueInUpdate() {
        this.em = getEntityManager("getReferenceResultCanBeUsedAsAttributeValueInUpdate", true);
        entityD.setOwlClassA(null);
        persist(entityD, entityA);

        em.getTransaction().begin();
        final OWLClassD d = em.find(OWLClassD.class, entityD.getUri());
        d.setOwlClassA(em.getReference(OWLClassA.class, entityA.getUri()));
        em.getTransaction().commit();

        final OWLClassD result = em.find(OWLClassD.class, entityD.getUri());
        assertNotNull(result.getOwlClassA());
        assertEquals(entityA.getUri(), result.getOwlClassA().getUri());
    }

    @Test
    public void getReferenceResultDataAttributesCanBeAssignedNewValuesInUpdate() {
        this.em = getEntityManager("getReferenceResultDataAttributesCanBeAssignedNewValuesInUpdate", true);
        persist(entityM);

        em.getTransaction().begin();
        final OWLClassM m = em.getReference(OWLClassM.class, entityM.getKey());
        final int iVal = Generators.randomInt();
        m.setIntAttribute(iVal);
        assertEquals(iVal, m.getIntAttribute().intValue());
        final long lVal = System.currentTimeMillis();
        m.setLongAttribute(lVal);
        final Set<Integer> intSet = IntStream.generate(Generators::randomInt).limit(5).boxed()
                                             .collect(Collectors.toSet());
        m.setIntegerSet(intSet);
        em.getTransaction().commit();

        final OWLClassM result = em.find(OWLClassM.class, entityM.getKey());
        assertEquals(iVal, result.getIntAttribute().intValue());
        assertEquals(lVal, result.getLongAttribute().longValue());
        assertEquals(intSet, result.getIntegerSet());
        // Ensure other values were not changed
        assertEquals(entityM.getBooleanAttribute(), result.getBooleanAttribute());
        assertEquals(entityM.getEnumAttribute(), result.getEnumAttribute());
        assertEquals(entityM.getDateAttribute(), result.getDateAttribute());
        assertEquals(entityM.getDoubleAttribute(), result.getDoubleAttribute());
    }

    @Test
    public void getReferenceResultObjectAttributesCanBeAssignedNewValuesInUpdate() {
        this.em = getEntityManager("getReferenceResultObjectAttributesCanBeAssignedNewValuesInUpdate", true);
        entityD.setOwlClassA(null);
        persist(entityD, entityA);

        em.getTransaction().begin();
        final OWLClassD d = em.getReference(OWLClassD.class, entityD.getUri());
        final OWLClassA a = em.getReference(OWLClassA.class, entityA.getUri());
        d.setOwlClassA(a);
        em.getTransaction().commit();

        final OWLClassD result = em.find(OWLClassD.class, entityD.getUri());
        assertEquals(entityA.getUri(), result.getOwlClassA().getUri());
    }

    @Test
    public void getReferenceResultPluralObjectAttributeCanBeUpdated() {
        this.em = getEntityManager("getReferenceResultPluralObjectAttributeCanBeUpdated", true);
        final OWLClassJ entityJ = new OWLClassJ(Generators.generateUri());
        entityJ.setOwlClassA(Collections.singleton(entityA));
        persist(entityJ);

        em.getTransaction().begin();
        final OWLClassJ j = em.getReference(OWLClassJ.class, entityJ.getUri());
        final OWLClassA newA = new OWLClassA(Generators.generateUri());
        newA.setStringAttribute("newA");
        j.getOwlClassA().add(newA);
        em.persist(newA);
        em.getTransaction().commit();

        final OWLClassJ result = em.find(OWLClassJ.class, entityJ.getUri());
        assertEquals(j.getOwlClassA().size(), result.getOwlClassA().size());
        assertTrue(j.getOwlClassA().stream().anyMatch(a -> a.getUri().equals(entityA.getUri())));
        assertTrue(j.getOwlClassA().stream().anyMatch(a -> a.getUri().equals(newA.getUri())));
    }

    @Test
    public void getReferenceResultListAttributeCanBeUpdated() {
        this.em = getEntityManager("getReferenceResultListAttributeCanBeUpdated", true);
        final OWLClassK entityK = new OWLClassK();
        entityK.setReferencedList(Collections.singletonList(entityE));
        entityK.setSimpleList(Collections.singletonList(entityE));
        persist(entityK, entityE);

        em.getTransaction().begin();
        final OWLClassK upd = em.getReference(OWLClassK.class, entityK.getUri());
        final OWLClassE newE = new OWLClassE();
        upd.getReferencedList().add(newE);
        upd.getSimpleList().add(newE);
        em.persist(newE);
        em.getTransaction().commit();

        final OWLClassK result = em.find(OWLClassK.class, entityK.getUri());
        assertEquals(upd.getReferencedList().size(), result.getReferencedList().size());
        assertEquals(upd.getSimpleList().size(), result.getSimpleList().size());
        assertTrue(result.getReferencedList().stream().anyMatch(a -> a.getUri().equals(newE.getUri())));
        assertTrue(result.getSimpleList().stream().anyMatch(a -> a.getUri().equals(newE.getUri())));
    }
}
