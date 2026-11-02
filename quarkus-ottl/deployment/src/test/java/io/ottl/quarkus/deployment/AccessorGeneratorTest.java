/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import io.ottl.quarkus.deployment.fixt.CollisionFoo;
import io.ottl.quarkus.deployment.fixt.CollisionFoo_Bar;
import io.ottl.spi.PropertyAccessor;
import io.quarkus.gizmo.ClassOutput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises generated accessor bytecode, not only the validation messages. */
public class AccessorGeneratorTest {

    @Test
    void mutableBeanReadsAndWritesThroughGeneratedBytecode() {
        PropertyAccessor accessor = accessor(Person.class);
        Person person = new Person();
        assertThat(accessor.descriptor()).isEqualTo(
                "active:boolean:rw;name:java.lang.String:rw;score:int:rw");
        assertThat(accessor.set(person, "name", "Ada")).isSameAs(person);
        assertThat(accessor.set(person, "score", 4L)).isSameAs(person);
        assertThat(accessor.set(person, "active", true)).isSameAs(person);
        assertThat(person.getName()).isEqualTo("Ada");
        assertThat(person.getScore()).isEqualTo(4);
        assertThat(accessor.get(person, "name")).isEqualTo("Ada");
        assertThat(accessor.get(person, "score")).isEqualTo(4);
        assertThat(accessor.get(person, "active")).isEqualTo(true);
    }

    @Test
    void booleanGetterPrefersIsOverGetWhenTypesMatch() {
        PropertyAccessor accessor = accessor(Flag.class);
        assertThat(accessor.get(new Flag(), "active")).isEqualTo(true);
        assertThat(accessor.descriptor()).contains("active:boolean:r");
    }

    @Test
    void readOnlyPropertyIsNotWritten() {
        PropertyAccessor accessor = accessor(ReadOnly.class);
        ReadOnly value = new ReadOnly();
        assertThat(accessor.descriptor()).contains("name:java.lang.String:r");
        assertThat(accessor.descriptor()).doesNotContain(":rw").doesNotContain(":w");
        assertThat(accessor.set(value, "name", "other")).isSameAs(value);
        assertThat(value.getName()).isEqualTo("Ada");
    }

    @Test
    void writeOnlyPropertyCanBeSetAndDoesNotRead() {
        PropertyAccessor accessor = accessor(WriteOnly.class);
        WriteOnly value = new WriteOnly();
        assertThat(accessor.descriptor()).isEqualTo("name:java.lang.String:w");
        assertThat(accessor.get(value, "name")).isNull();
        accessor.set(value, "name", "Ada");
        assertThat(value.current()).isEqualTo("Ada");
    }

    @Test
    void recordCopyLeavesTheOriginalUntouched() {
        PropertyAccessor accessor = accessor(Point.class);
        Point original = new Point(1, "a");
        Point updated = (Point) accessor.set(original, "x", 9L);
        assertThat(updated).isNotSameAs(original);
        assertThat(original.x()).isEqualTo(1);
        assertThat(updated.x()).isEqualTo(9);
        assertThat(updated.label()).isEqualTo("a");
        assertThat(accessor.descriptor()).contains("x:int:rw").contains("label:java.lang.String:rw");
    }

    @Test
    void nestedRecordComponentsCopyIndependently() {
        PropertyAccessor outer = accessor(Outer.class);
        PropertyAccessor inner = accessor(Inner.class);
        Outer original = new Outer("s1", new Inner("Paris"));
        Inner moved = (Inner) inner.set(original.place(), "city", "Berlin");
        Outer updated = (Outer) outer.set(original, "place", moved);
        assertThat(original.place().city()).isEqualTo("Paris");
        assertThat(updated).isNotSameAs(original);
        assertThat(updated.id()).isEqualTo("s1");
        assertThat(updated.place().city()).isEqualTo("Berlin");
    }

    @Test
    void overloadedSettersAreRejected() {
        assertThatThrownBy(() -> accessor(AmbiguousAmount.class))
                .isInstanceOf(AccessorGenerationException.class)
                .hasMessageContaining(AmbiguousAmount.class.getName())
                .hasMessageContaining("amount")
                .hasMessageContaining("long")
                .hasMessageContaining("java.lang.String");
    }

    @Test
    void getterAndSetterTypeMismatchIsRejected() {
        assertThatThrownBy(() -> accessor(MismatchedStatus.class))
                .isInstanceOf(AccessorGenerationException.class)
                .hasMessageContaining("status")
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining("java.lang.Integer");
    }

    @Test
    void conflictingBooleanGettersAreRejected() {
        assertThatThrownBy(() -> accessor(ConflictingFlag.class))
                .isInstanceOf(AccessorGenerationException.class)
                .hasMessageContaining("active");
    }

    @Test
    void packagePrivateTypeIsRejected() {
        assertThatThrownBy(() -> accessor(HiddenBean.class))
                .isInstanceOf(AccessorGenerationException.class)
                .hasMessageContaining("not public");
    }

    @Test
    void publicNestedTypeInsidePackagePrivateOuterIsRejected() {
        assertThatThrownBy(() -> accessor(HiddenOuter.Visible.class))
                .isInstanceOf(AccessorGenerationException.class)
                .hasMessageContaining(HiddenOuter.class.getName());
    }

    @Test
    void interfacesAndAbstractClassesAreRejected() {
        assertThatThrownBy(() -> accessor(Marker.class))
                .isInstanceOf(AccessorGenerationException.class)
                .hasMessageContaining("interface");
        assertThatThrownBy(() -> accessor(AbstractBean.class))
                .isInstanceOf(AccessorGenerationException.class)
                .hasMessageContaining("abstract");
    }

    @Test
    void inheritedPropertiesAreVisibleAndBridgesDoNotHideTheSpecializedSetter() {
        PropertyAccessor accessor = accessor(StringChild.class);
        assertThat(accessor.descriptor()).contains("value:java.lang.String:rw");
        assertThat(accessor.descriptor()).doesNotContain("java.lang.Object");
        StringChild child = new StringChild();
        accessor.set(child, "value", "hi");
        assertThat(accessor.get(child, "value")).isEqualTo("hi");
    }

    @Test
    void staticMethodsAreNotBeanProperties() {
        PropertyAccessor accessor = accessor(StaticNoise.class);
        assertThat(accessor.descriptor()).isEqualTo("shown:java.lang.String:r");
        assertThat(accessor.get(new StaticNoise(), "hidden")).isNull();
    }

    @Test
    void packagePrivateSetterIsNotAWritableProperty() {
        PropertyAccessor accessor = accessor(HiddenSetter.class);
        HiddenSetter value = new HiddenSetter();
        assertThat(accessor.descriptor()).isEqualTo("name:java.lang.String:r");
        accessor.set(value, "name", "other");
        assertThat(value.getName()).isEqualTo("a");
    }

    @Test
    void acronymPropertyUsesJavaBeansDecapitalization() {
        PropertyAccessor accessor = accessor(Acronym.class);
        Acronym value = new Acronym();
        accessor.set(value, "URL", "https://example");
        assertThat(accessor.get(value, "URL")).isEqualTo("https://example");
        assertThat(accessor.descriptor()).startsWith("URL:");
    }

    @Test
    void generatedClassNamesAreUniqueForNestedAndUnderscoreTypes() throws Exception {
        String flat = GeneratedNames.accessorClass(CollisionFoo_Bar.class.getName());
        String nested = GeneratedNames.accessorClass(CollisionFoo.Bar.class.getName());
        assertThat(flat).isNotEqualTo(nested);
        assertThat(GeneratedNames.decode(flat.substring(flat.lastIndexOf('.') + 1).substring(2)))
                .isEqualTo(CollisionFoo_Bar.class.getName());
        assertThat(GeneratedNames.decode(nested.substring(nested.lastIndexOf('.') + 1).substring(2)))
                .isEqualTo(CollisionFoo.Bar.class.getName());

        Map<String, byte[]> bytes = new LinkedHashMap<>();
        ClassOutput output = (name, data) -> bytes.put(name.replace('/', '.'), data);
        AccessorGenerator.generate(CollisionFoo_Bar.class, output);
        AccessorGenerator.generate(CollisionFoo.Bar.class, output);
        ClassLoader loader = loader(bytes);
        PropertyAccessor flatAccessor = newAccessor(loader, flat);
        PropertyAccessor nestedAccessor = newAccessor(loader, nested);
        assertThat(flatAccessor.get(new CollisionFoo_Bar(), "id")).isEqualTo("flat");
        assertThat(nestedAccessor.get(new CollisionFoo.Bar(), "id")).isEqualTo("nested");
    }

    private static PropertyAccessor accessor(Class<?> type) {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        AccessorGenerator.generate(type, (name, data) -> bytes.put(name.replace('/', '.'), data));
        String className = GeneratedNames.accessorClass(type.getName());
        try {
            return newAccessor(loader(bytes), className);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load " + className, e);
        }
    }

    private static PropertyAccessor newAccessor(ClassLoader loader, String className) throws Exception {
        Class<?> generated = loader.loadClass(className);
        return (PropertyAccessor) generated.getDeclaredConstructor().newInstance();
    }

    private static ClassLoader loader(Map<String, byte[]> bytes) {
        ClassLoader parent = AccessorGeneratorTest.class.getClassLoader();
        return new ClassLoader(parent) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] data = bytes.get(name);
                if (data == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, data, 0, data.length);
            }
        };
    }

    public static class Person {
        private String name;
        private int score;
        private boolean active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static class Flag {
        public boolean isActive() {
            return true;
        }

        public boolean getActive() {
            return false;
        }
    }

    public static class ReadOnly {
        public String getName() {
            return "Ada";
        }
    }

    public static class WriteOnly {
        private String name;

        public void setName(String name) {
            this.name = name;
        }

        public String current() {
            return name;
        }
    }

    public record Point(int x, String label) {
    }

    public record Inner(String city) {
    }

    public record Outer(String id, Inner place) {
    }

    public static class AmbiguousAmount {
        public void setAmount(long value) {
        }

        public void setAmount(String value) {
        }
    }

    public static class MismatchedStatus {
        public String getStatus() {
            return "NEW";
        }

        public void setStatus(Integer status) {
        }
    }

    public static class ConflictingFlag {
        public boolean isActive() {
            return true;
        }

        public String getActive() {
            return "yes";
        }
    }

    public interface Marker {
        String getName();
    }

    public abstract static class AbstractBean {
        public String getName() {
            return "x";
        }
    }

    public static class GenericBase<T> {
        private T value;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

    public static class StringChild extends GenericBase<String> {
        @Override
        public String getValue() {
            return super.getValue();
        }

        @Override
        public void setValue(String value) {
            super.setValue(value);
        }
    }

    public static class StaticNoise {
        public static String getHidden() {
            return "no";
        }

        public String getShown() {
            return "yes";
        }
    }

    public static class HiddenSetter {
        private String name = "a";

        public String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }
    }

    public static class Acronym {
        private String url;

        public String getURL() {
            return url;
        }

        public void setURL(String url) {
            this.url = url;
        }
    }
}

class HiddenBean {
    public String getName() {
        return "x";
    }
}

class HiddenOuter {
    public static class Visible {
        public String getName() {
            return "x";
        }
    }
}
