package cz.cvut.kbss.jopa.test.runner;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.test.OWLClassQ;
import cz.cvut.kbss.jopa.test.OWLClassS;
import cz.cvut.kbss.jopa.test.OWLClassT;
import cz.cvut.kbss.jopa.test.Vocabulary;
import cz.cvut.kbss.jopa.test.environment.Generators;
import cz.cvut.kbss.jopa.test.environment.Triple;
import org.junit.Test;
import org.slf4j.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import static cz.cvut.kbss.jopa.CommonVocabulary.*;
import static org.junit.Assert.*;

public abstract class RetrieveOperationsWithInheritanceRunner extends BaseInheritanceRunner {

    public RetrieveOperationsWithInheritanceRunner(Logger logger) {
        super(logger);
    }

    @Test
    public void findReadsAttributesOfMappedSuperclass() throws Exception {
        final Collection<Triple> data = new ArrayList<>();
        entityQ.setUri(Generators.generateUri());
        data.add(
                new Triple(entityQ.getUri(), URI.create(RDF_TYPE), URI.create(Vocabulary.cOWLClassQ)));
        data.add(new Triple(entityQ.getUri(), URI.create(RDFS_LABEL), entityQ.getLabel()));
        data.add(
                new Triple(entityQ.getUri(), URI.create(Vocabulary.qParentStringAttribute), entityQ.getParentString()));
        data.add(new Triple(entityQ.getUri(), URI.create(Vocabulary.qStringAttribute), entityQ.getStringAttribute()));
        data.addAll(triplesForA());
        data.add(new Triple(entityQ.getUri(), URI.create(Vocabulary.hasOwlClassA), entityA.getUri()));
        final EntityManager em = getEntityManager("findReadsAttributesOfMappedSuperclass", false);
        persistTestData(data, em);

        final OWLClassQ result = em.find(OWLClassQ.class, entityQ.getUri());
        assertNotNull(result);
        assertEquals(entityQ.getStringAttribute(), result.getStringAttribute());
        assertEquals(entityQ.getLabel(), result.getLabel());
        assertEquals(entityQ.getParentString(), result.getParentString());
        assertNotNull(result.getOwlClassA());
        assertEquals(entityQ.getOwlClassA().getUri(), result.getOwlClassA().getUri());
    }

    private Collection<Triple> triplesForA() {
        final Collection<Triple> data = new ArrayList<>();
        entityA.setUri(Generators.generateUri());
        data.add(
                new Triple(entityA.getUri(), URI.create(RDF_TYPE), URI.create(Vocabulary.cOwlClassA)));
        data.add(new Triple(entityA.getUri(), URI.create(Vocabulary.pAStringAttribute), entityA.getStringAttribute()));
        return data;
    }

    @Test
    public void findReadsAttributesOfEntitySuperclass() throws Exception {
        final Collection<Triple> data = triplesForEntityT();
        final EntityManager em = getEntityManager("findReadsAttributesOfEntitySuperclass", false);
        persistTestData(data, em);

        final OWLClassT result = em.find(OWLClassT.class, entityT.getUri());
        assertNotNull(result);
        assertEquals(entityT.getName(), result.getName());
        assertEquals(entityT.getDescription(), result.getDescription());
        assertEquals(entityT.getIntAttribute(), result.getIntAttribute());
        assertNotNull(result.getOwlClassA());
        assertEquals(entityA.getUri(), result.getOwlClassA().getUri());
    }

    private Collection<Triple> triplesForEntityT() {
        final Collection<Triple> data = new ArrayList<>();
        entityT.setUri(Generators.generateUri());
        data.add(
                new Triple(entityT.getUri(), URI.create(RDF_TYPE), URI.create(Vocabulary.cOWLClassT)));
        data.add(new Triple(entityT.getUri(), URI.create(RDFS_LABEL), entityT.getName()));
        data.add(new Triple(entityT.getUri(), URI.create(DC_DESCRIPTION), entityT.getDescription()));
        data.add(new Triple(entityT.getUri(), URI.create(Vocabulary.tIntegerAttribute), entityT.getIntAttribute()));
        data.addAll(triplesForA());
        data.add(new Triple(entityT.getUri(), URI.create(Vocabulary.hasOwlClassA), entityA.getUri()));
        return data;
    }

    @Test
    public void findLoadsSuperclassInstanceWhenRequestedAndClassAssertionIsPresent() throws Exception {
        final Collection<Triple> data = triplesForEntityT();
        data.add(new Triple(entityT.getUri(), URI.create(RDF_TYPE), URI.create(Vocabulary.cOWLClassS)));

        final EntityManager em = getEntityManager("findLoadsSuperclassInstanceWhenRequestedAndClassAssertionIsPresent",
                false);
        persistTestData(data, em);

        final OWLClassS result = em.find(OWLClassS.class, entityT.getUri());
        assertNotNull(result);
        assertEquals(entityT.getName(), result.getName());
        assertEquals(entityT.getDescription(), result.getDescription());
        assertTrue(result.getTypes().contains(Vocabulary.cOWLClassT));
    }
}
