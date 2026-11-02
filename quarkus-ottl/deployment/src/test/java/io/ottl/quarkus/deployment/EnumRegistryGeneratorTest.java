/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus.deployment;

import io.ottl.spi.EnumConstantRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Generated enum registries load constants with getstatic and do not become property bags. */
public class EnumRegistryGeneratorTest {

    public enum Tone {
        LOW,
        HIGH
    }

    @Test
    void generatedRegistryResolvesConstantsBySimpleName() throws Exception {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        EnumRegistryGenerator.generate(List.of(Tone.class),
                (name, data) -> bytes.put(name.replace('/', '.'), data));
        ClassLoader loader = loader(bytes);
        EnumConstantRegistry registry = (EnumConstantRegistry) loader.loadClass(EnumRegistryGenerator.REGISTRY_CLASS)
                .getDeclaredConstructor()
                .newInstance();

        EnumConstantRegistry.Match match = registry.match(List.of("Tone", "HIGH"));
        assertThat(match.value()).isEqualTo(Tone.HIGH);
        assertThat(match.propertyAccess()).isFalse();
        assertThat(registry.match(List.of("Tone", "HIGH", "name")).propertyAccess()).isTrue();
        assertThat(registry.types()).extracting(EnumConstantRegistry.EnumType::simpleName).containsExactly("Tone");
    }

    @Test
    void packagePrivateEnumIsRejected() {
        assertThatThrownBy(() -> AccessibleTypeModel.validateEnum(Hidden.class))
                .isInstanceOf(AccessorGenerationException.class)
                .hasMessageContaining("not public");
    }

    private static ClassLoader loader(Map<String, byte[]> bytes) {
        ClassLoader parent = EnumRegistryGeneratorTest.class.getClassLoader();
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

    enum Hidden {
        A
    }
}
