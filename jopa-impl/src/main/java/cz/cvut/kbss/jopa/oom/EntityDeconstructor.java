package cz.cvut.kbss.jopa.oom;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.FieldSpecification;
import cz.cvut.kbss.ontodriver_new.MutationAxiomDescriptor;
import cz.cvut.kbss.ontodriver_new.model.Assertion;
import cz.cvut.kbss.ontodriver_new.model.NamedResource;
import cz.cvut.kbss.ontodriver_new.model.Value;

class EntityDeconstructor {

	private final ObjectOntologyMapperImpl mapper;

	EntityDeconstructor(ObjectOntologyMapperImpl mapper) {
		this.mapper = mapper;
	}

	<T> MutationAxiomDescriptor mapEntityToAxioms(URI primaryKey, T entity, EntityType<T> et,
			Descriptor descriptor) {
		assert primaryKey != null;
		final MutationAxiomDescriptor axiomDescriptor = new MutationAxiomDescriptor(
				NamedResource.create(primaryKey));
		axiomDescriptor.setSubjectContext(descriptor.getContext());
		try {
			addClassAssertions(axiomDescriptor, entity, et, descriptor);
			addUnmappedPropertyAssertions(axiomDescriptor, entity, et, descriptor);
			addPropertyAssertions(axiomDescriptor, entity, et, descriptor);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new EntityDeconstructionException(e);
		}
		return axiomDescriptor;
	}

	private <T> void addClassAssertions(MutationAxiomDescriptor axiomDescriptor, T entity,
			EntityType<T> et, Descriptor descriptor) throws IllegalArgumentException,
			IllegalAccessException {
		final OWLClass clsType = entity.getClass().getAnnotation(OWLClass.class);
		assert clsType != null;
		final Assertion entityClassAssertion = Assertion.createClassAssertion(false);
		axiomDescriptor.addAssertionValue(entityClassAssertion,
				new Value<URI>(URI.create(clsType.iri())));
		axiomDescriptor.setAssertionContext(entityClassAssertion, descriptor.getContext());
		if (et.getTypes() != null) {
			final Field typesField = et.getTypes().getJavaField();
			typesField.setAccessible(true);
			final Set<String> types = (Set<String>) typesField.get(entity);
			if (types == null || types.isEmpty()) {
				return;
			}
			setAssertionContext(axiomDescriptor, descriptor, et.getTypes(),
					Assertion.createClassAssertion(et.getTypes().isInferred()));
			for (String t : types) {
				try {
					final URI typeUri = URI.create(t);
					axiomDescriptor.addAssertionValue(
							Assertion.createClassAssertion(et.getTypes().isInferred()),
							new Value<URI>(typeUri));
				} catch (IllegalArgumentException e) {
					throw new EntityDeconstructionException("The type " + t
							+ " is not a valid URI.", e);
				}
			}
		}
	}

	private <T> void addUnmappedPropertyAssertions(MutationAxiomDescriptor axiomDescriptor,
			T entity, EntityType<T> et, Descriptor descriptor) throws IllegalArgumentException,
			IllegalAccessException {
		if (et.getProperties() != null) {
			final Field propsField = et.getProperties().getJavaField();
			propsField.setAccessible(true);
			final Map<String, Set<String>> props = (Map<String, Set<String>>) propsField
					.get(entity);
			if (props == null || props.isEmpty()) {
				return;
			}
			for (Entry<String, Set<String>> e : props.entrySet()) {
				final Assertion property = Assertion.createPropertyAssertion(
						URI.create(e.getKey()), et.getProperties().isInferred());
				setAssertionContext(axiomDescriptor, descriptor, et.getProperties(), property);
				axiomDescriptor.addAssertion(property);
				for (String value : e.getValue()) {
					axiomDescriptor.addAssertionValue(property, new Value<>(value));
				}
			}
		}
	}

	private <T> void addPropertyAssertions(MutationAxiomDescriptor axiomDescriptor, T entity,
			EntityType<T> et, Descriptor descriptor) throws IllegalArgumentException,
			IllegalAccessException {
		for (Attribute<?, ?> att : et.getAttributes()) {
			final FieldStrategy fs = FieldStrategy.createFieldStrategy(et, att, descriptor, mapper);
			final Collection<Value<?>> vals = fs.extractAttributeValuesFromInstance(entity);
			if (vals.isEmpty()) {
				continue;
			}
			final Assertion propertyAssertion = fs.createAssertion();
			axiomDescriptor.addAssertion(propertyAssertion);
			setAssertionContext(axiomDescriptor, descriptor, att, propertyAssertion);
			for (Value<?> v : vals) {
				axiomDescriptor.addAssertionValue(propertyAssertion, v);
			}
		}
	}

	private void setAssertionContext(MutationAxiomDescriptor axiomDescriptor,
			Descriptor descriptor, FieldSpecification<?, ?> att, final Assertion propertyAssertion) {
		final URI attContext = descriptor.getAttributeDescriptor(att).getContext();
		if (attContext != null) {
			axiomDescriptor.setAssertionContext(propertyAssertion, attContext);
		}
	}
}