package cz.cvut.kbss.jopa.oom.converter;

import cz.cvut.kbss.jopa.environment.OneOfEnum;
import cz.cvut.kbss.jopa.environment.utils.Generators;
import cz.cvut.kbss.jopa.exception.InvalidEnumMappingException;
import cz.cvut.kbss.jopa.model.annotations.Individual;
import cz.cvut.kbss.jopa.vocabulary.OWL;
import cz.cvut.kbss.ontodriver.model.NamedResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ObjectOneOfEnumConverterTest {

    private final ObjectOneOfEnumConverter<OneOfEnum> sut = new ObjectOneOfEnumConverter<>(OneOfEnum.class);

    @Test
    void constructorThrowsInvalidEnumMappingExceptionWhenObjectPropertyEnumConstantsHasNoIndividualMapped() {
        assertThrows(InvalidEnumMappingException.class,
                     () -> new ObjectOneOfEnumConverter<>(ObjectOneOfTestEnum.class));
    }

    enum ObjectOneOfTestEnum {
        @Individual(iri = OWL.OBJECT_PROPERTY)
        A,
        B
    }

    @ParameterizedTest
    @MethodSource("mappedEnumValues")
    void convertToAxiomValueReturnsNamedResourceMappedToSpecifiedConstant(OneOfEnum value, String iri) {
        assertEquals(NamedResource.create(iri), sut.convertToAxiomValue(value));
    }

    static Stream<Arguments> mappedEnumValues() {
        return Stream.of(OneOfEnum.values()).map(constant -> {
            try {
                return Arguments.of(constant, constant.getDeclaringClass()
                                                      .getDeclaredField(constant.name())
                                                      .getAnnotation(Individual.class)
                                                      .iri());
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("mappedEnumValues")
    void convertToAttributeReturnsEnumConstantMappedToSpecifiedNamedResourceIri(OneOfEnum value, String iri) {
        assertEquals(value, sut.convertToAttribute(NamedResource.create(iri)));
    }

    @Test
    void convertToAttributeThrowsInvalidEnumMappingExceptionForUnknownNamedResource() {
        assertThrows(InvalidEnumMappingException.class,
                     () -> sut.convertToAttribute(NamedResource.create(Generators.createIndividualIdentifier())));
    }

    @Test
    void supportsAxiomValueTypeReturnsTrueForNamedResource() {
        assertTrue(sut.supportsAxiomValueType(NamedResource.class));
        assertFalse(sut.supportsAxiomValueType(String.class));
        assertFalse(sut.supportsAxiomValueType(Integer.class));
    }
}
