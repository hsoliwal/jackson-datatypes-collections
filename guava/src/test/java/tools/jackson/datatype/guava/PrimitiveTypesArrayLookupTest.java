// SPDX-License-Identifier: Apache-2.0
package tools.jackson.datatype.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import com.google.common.base.Optional;
import tools.jackson.datatype.guava.util.PrimitiveTypes;

/** Category TYPE_ENUM_INDEX: stable Guava primitive list type lookup, unchanged public contract. */
public final class PrimitiveTypesArrayLookupTest {
    private static final Class<?>[] LIST_TYPES = {
        PrimitiveTypes.BooleansType, PrimitiveTypes.BytesType,
        PrimitiveTypes.CharsType, PrimitiveTypes.DoublesType,
        PrimitiveTypes.FloatsType, PrimitiveTypes.IntsType,
        PrimitiveTypes.LongsType, PrimitiveTypes.ShortsType
    };

    @Test
    public void sameExactTypeAndDeclarationOrderAsLegacyFreshValuesScan() {
        PrimitiveTypes.Primitives[] declarations = PrimitiveTypes.Primitives.values();
        assertEquals(8, declarations.length);
        assertEquals(LIST_TYPES.length, declarations.length);
        for (int i = 0; i < LIST_TYPES.length; i++) {
            Optional<PrimitiveTypes.Primitives> expected = originalScan(LIST_TYPES[i]);
            assertEquals(Optional.of(declarations[i]), expected);
            assertEquals(expected, PrimitiveTypes.isAssignableFromPrimitive(LIST_TYPES[i]));
        }
        // Public values() still returns a fresh copy, never the private cached array.
        assertNotSame(PrimitiveTypes.Primitives.values(), PrimitiveTypes.Primitives.values());
    }

    @Test
    public void absentAndNullBehaviorAndDeserializerFactoryStayUnchanged() throws Exception {
        for (Class<?> other : new Class<?>[] {
                Object.class, List.class, ArrayList.class, String.class, int[].class
        }) {
            assertEquals(Optional.absent(), PrimitiveTypes.isAssignableFromPrimitive(other));
        }
        assertThrows(NullPointerException.class,
                () -> PrimitiveTypes.isAssignableFromPrimitive(null));
        assertNotSame(PrimitiveTypes.Primitives.INT.newDeserializer(),
                PrimitiveTypes.Primitives.INT.newDeserializer());
        var field = PrimitiveTypes.Primitives.class.getDeclaredField("LOOKUP_ORDER");
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    public void repeatedAndConcurrentLookupsMatchOriginalDeclarationScan() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int task = 0; task < 4; task++) {
                jobs.add(() -> {
                    for (int iteration = 0; iteration < 1000; iteration++) {
                        Class<?> valueType = LIST_TYPES[iteration % LIST_TYPES.length];
                        if (!originalScan(valueType).equals(
                                PrimitiveTypes.isAssignableFromPrimitive(valueType))) {
                            return false;
                        }
                    }
                    return true;
                });
            }
            List<Future<Boolean>> answers = workers.invokeAll(jobs);
            for (Future<Boolean> answer : answers) assertTrue(answer.get());
            assertFalse(answers.isEmpty());
        } finally {
            workers.shutdownNow();
        }
    }

    private static Optional<PrimitiveTypes.Primitives> originalScan(Class<?> valueType) {
        for (PrimitiveTypes.Primitives candidate : PrimitiveTypes.Primitives.values()) {
            if (candidate.type().isAssignableFrom(valueType)) return Optional.of(candidate);
        }
        return Optional.absent();
    }
}
