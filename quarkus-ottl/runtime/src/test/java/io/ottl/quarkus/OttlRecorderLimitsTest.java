/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.quarkus;

import io.ottl.OttlProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OttlRecorderLimitsTest {

    @Test
    void defaultsAreAccepted() {
        assertThat(OttlRecorder.limits(config(10_000, 100)).maxSourceLength()).isEqualTo(10_000);
        assertThat(OttlRecorder.limits(config(10_000, 100)).maxPathDepth()).isEqualTo(16);
    }

    @Test
    void nonPositiveAndUnboundedValuesAreRejected() {
        assertThatThrownBy(() -> OttlRecorder.limits(config(0, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-source-length");
        assertThatThrownBy(() -> OttlRecorder.limits(config(10, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-statements");
        assertThatThrownBy(() -> OttlRecorder.limits(config(OttlRecorder.MAX_CONFIGURED_SOURCE_LENGTH + 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-source-length");
    }

    private static OttlConfig config(int source, int statements) {
        return new OttlConfig() {
            @Override
            public OttlProfile profile() {
                return OttlProfile.JAVA;
            }

            @Override
            public int maxSourceLength() {
                return source;
            }

            @Override
            public int maxStatements() {
                return statements;
            }
        };
    }
}
