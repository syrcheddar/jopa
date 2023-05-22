package cz.cvut.kbss.jopa.model.metamodel;

import cz.cvut.kbss.jopa.exception.MetamodelInitializationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnotatedAccessorTest {

    @Test
    void fromParsesGenericGetterCorrectly() throws NoSuchMethodException {
        Method genericGetter = MethodHolder.class.getDeclaredMethod("getString");

        AnnotatedAccessor accessor = AnnotatedAccessor.from(genericGetter);


        assertEquals(genericGetter, accessor.getMethod());
        assertEquals("string", accessor.getPropertyName());
        assertEquals(String.class, accessor.getPropertyType());
    }

    @Test
    void fromParsesGenericSetterCorrectly() throws NoSuchMethodException {
        Method genericGetter = MethodHolder.class.getDeclaredMethod("setString", String.class);

        AnnotatedAccessor accessor = AnnotatedAccessor.from(genericGetter);


        assertEquals(genericGetter, accessor.getMethod());
        assertEquals("string", accessor.getPropertyName());
        assertEquals(String.class, accessor.getPropertyType());

    }

    @ParameterizedTest
    @ValueSource(strings = {"isFoo", "getFoo", "hasFoo"})
    void fromParsesBooleanIsGetterCorrectly(String methodName) throws NoSuchMethodException {
        Method genericGetter = MethodHolder.class.getDeclaredMethod(methodName);

        AnnotatedAccessor accessor = AnnotatedAccessor.from(genericGetter);


        assertEquals(genericGetter, accessor.getMethod());
        assertEquals("foo", accessor.getPropertyName());
        assertEquals(Boolean.class, accessor.getPropertyType());
    }

    private static Stream<Arguments> fromThrowsExceptionOnInvalidMethodsTestValues() {
        return Stream.of(
                Arguments.arguments("getBar",new Class[0]),
                Arguments.arguments("invalidGetter",new Class[0]),
                Arguments.arguments("getA", new Class[]{String.class}),
                Arguments.arguments("setA",new Class[0]),
                Arguments.arguments("setB", new Class[]{String.class, String.class})

        );
    }
    @ParameterizedTest
    @MethodSource("fromThrowsExceptionOnInvalidMethodsTestValues")
    void fromThrowsExceptionOnInvalidMethods(String methodName, Class<?>[] methodParameters) throws NoSuchMethodException {
        Method genericGetter = MethodHolder.class.getDeclaredMethod(methodName,methodParameters);

        assertThrows(MetamodelInitializationException.class, () -> AnnotatedAccessor.from(genericGetter));

    }

    private abstract static class MethodHolder {
        abstract String getString();

        abstract String setString(String s);

        abstract Boolean getFoo();

        abstract Boolean hasFoo();

        abstract Boolean isFoo();

        //-- invalid getters
        abstract void  getBar();
        abstract String getA(String s);
        abstract String invalidGetter();

        //-- invalid setters
        abstract String setA();
        abstract String setB(String a, String c);

    }
}