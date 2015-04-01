package cz.cvut.kbss.ontodriver.owlapi.util;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

/**
 * Add axiom wrapper which allows us to set ontology to which it is applied.
 */
public class MutableAddAxiom extends AddAxiom implements MutableAxiomChange {

    private OWLOntology ontology;

    /**
     * @param ont   the ontology to which the change is to be applied
     * @param axiom The added axiom
     */
    public MutableAddAxiom(OWLOntology ont, OWLAxiom axiom) {
        super(ont, axiom);
        this.ontology = ont;
    }

    @Override
    public void setOntology(OWLOntology ontology) {
        this.ontology = ontology;
    }

    @Override
    public OWLOntology getOntology() {
        return ontology;
    }
}