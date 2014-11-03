package cz.cvut.kbss.jopa.oom;

import java.net.URI;

import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.oom.exceptions.EntityDeconstructionException;
import cz.cvut.kbss.ontodriver_new.model.Assertion;
import cz.cvut.kbss.ontodriver_new.model.Axiom;
import cz.cvut.kbss.ontodriver_new.model.Value;

class SingularObjectPropertyStrategy<X> extends FieldStrategy<Attribute<? super X, ?>, X> {

	private Object value;

	SingularObjectPropertyStrategy(EntityType<X> et, Attribute<? super X, ?> att,
			Descriptor descriptor, EntityMappingHelper mapper) {
		super(et, att, descriptor, mapper);
	}

	@Override
	void addValueFromAxiom(Axiom<?> ax) {
		// TODO Check that this cast is OK
		final URI valueIdentifier = (URI) ax.getValue().getValue();
		this.value = mapper.getEntityFromCacheOrOntology(attribute.getJavaType(), valueIdentifier,
				descriptor);
	}

	@Override
	void buildInstanceFieldValue(Object instance) throws IllegalArgumentException,
			IllegalAccessException {
		setValueOnInstance(instance, value);
	}

	@Override
	void buildAxiomValuesFromInstance(X instance, AxiomValueGatherer valueBuilder)
			throws IllegalArgumentException, IllegalAccessException {
		final Object value = extractFieldValueFromInstance(instance);
		Value<?> val = value != null ? extractReferenceIdentifier(value) : Value.nullValue();
		valueBuilder.addValue(createAssertion(), val, getAttributeContext());
	}

	private <V> Value<URI> extractReferenceIdentifier(final V value) {
		final EntityType<V> valEt = (EntityType<V>) mapper.getEntityType(value.getClass());
		if (valEt == null) {
			throw new EntityDeconstructionException("Value of field " + attribute.getJavaField()
					+ " is not a recognized entity.");
		}
		final URI id = resolveValueIdentifier(value, valEt);
		cascadeResolver.resolveFieldCascading(attribute, value, getAttributeContext());
		return new Value<>(id);
	}

	@Override
	Assertion createAssertion() {
		return Assertion.createObjectPropertyAssertion(attribute.getIRI().toURI(),
				attribute.isInferred());
	}
}
