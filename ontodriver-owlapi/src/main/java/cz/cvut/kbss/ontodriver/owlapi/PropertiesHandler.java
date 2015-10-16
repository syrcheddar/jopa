package cz.cvut.kbss.ontodriver.owlapi;

import cz.cvut.kbss.ontodriver.exceptions.OntoDriverException;
import cz.cvut.kbss.ontodriver.owlapi.connector.OntologyStructures;
import cz.cvut.kbss.ontodriver_new.model.Assertion;
import cz.cvut.kbss.ontodriver_new.model.Axiom;
import cz.cvut.kbss.ontodriver_new.model.NamedResource;
import cz.cvut.kbss.ontodriver_new.model.Value;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

class PropertiesHandler {

    private final OwlapiAdapter adapter;
    private final OntologyStructures snapshot;

    PropertiesHandler(OwlapiAdapter adapter, OntologyStructures snapshot) {
        this.adapter = adapter;
        this.snapshot = snapshot;
    }

    public Collection<Axiom<?>> getProperties(NamedResource subject, boolean includeInferred)
            throws OntoDriverException {
        if (includeInferred) {
            return getPropertiesIncludingInferred(subject);
        } else {
            return getExplicitProperties(subject);
        }
    }

    private Collection<Axiom<?>> getPropertiesIncludingInferred(NamedResource subject) {
        return new InferredAxiomLoader(adapter, snapshot).loadPropertyAxioms(subject);
    }

    private Collection<Axiom<?>> getExplicitProperties(NamedResource subject) {
        return new ExplicitAxiomLoader(adapter, snapshot).loadPropertyAxioms(subject);
    }

    public void addProperties(NamedResource subject, Map<Assertion, Set<Value<?>>> properties)
            throws OntoDriverException {
        new AxiomSaver(adapter, snapshot).persistAxioms(subject, properties);
    }

    public void removeProperties(NamedResource subject, Map<Assertion, Set<Value<?>>> properties)
            throws OntoDriverException {

    }
}