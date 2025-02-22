package cz.cvut.kbss.jopa.test;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;

import java.net.URI;
import java.util.Set;

@OWLClass(iri = Vocabulary.C_OWL_CLASS_CHILD_C)
public class OWLChildClassC implements OWLInterfaceC, OWLInterfaceD {
    private String name;

    private Boolean attributeB;

    Set<String> titles;
    @Id
    private URI id;

    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void setAttributeB(Boolean attr) {
        this.attributeB = attr;
    }


    public Boolean getAttributeB() {
        return attributeB;
    }

    @Override
    public Set<String> getTitles() {
        return titles;
    }

    public void setTitles(Set<String> titles) {
        this.titles = titles;
    }
}

