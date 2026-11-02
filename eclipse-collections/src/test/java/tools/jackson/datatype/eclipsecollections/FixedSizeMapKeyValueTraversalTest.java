package tools.jackson.datatype.eclipsecollections;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.map.FixedSizeMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.factory.Maps;
import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.datatype.eclipsecollections.deser.map.TypeHandlerPairs;

import static org.junit.jupiter.api.Assertions.*;

class FixedSizeMapKeyValueTraversalTest extends ModuleTestBase {
    private final tools.jackson.databind.ObjectMapper MAPPER = mapperWithModule();

    @Test
    void fixedSizeFinisherUsesOnlySizeAndDirectKeyValueTraversal() throws Exception {
        for (int size = 0; size <= 3; size++) {
            Map<Object, Object> source = new LinkedHashMap<>();
            if (size > 0) source.put(null, "null-key");
            if (size > 1) source.put("null-value", null);
            if (size > 2) source.put("tail", 17);
            int[] traversals = {0};
            int[] visits = {0};
            MutableMap<Object, Object> sourceProtocol = directOnly(source, traversals, visits, null);
            FixedSizeMap<?, ?> result = finish(sourceProtocol);
            assertEquals(source, result);
            assertEquals(1, traversals[0]);
            assertEquals(size, visits[0]);
        }
    }

    @Test
    void directTraversalPreservesTheOriginalPairTraversalOrderAndValues() throws Exception {
        Object[] keys = {null, "Aa", "BB", "AaAa", "BBBB", -1, 0, 1};
        for (int first = 0; first < keys.length; first++) {
            for (int second = 0; second < keys.length; second++) {
                for (int third = 0; third < keys.length; third++) {
                    MutableMap<Object, Object> source = Maps.mutable.empty();
                    source.put(keys[first], null);
                    source.put(keys[second], "middle");
                    source.put(keys[third], 17);
                    FixedSizeMap<?, ?> before = finishWithPairs(source);
                    FixedSizeMap<?, ?> after = finish(source);
                    assertEquals(before, after);
                    assertArrayEquals(before.keySet().toArray(), after.keySet().toArray());
                    assertArrayEquals(before.values().toArray(), after.values().toArray());
                }
            }
        }
    }

    @Test
    void traversalFailurePropagatesWithoutWrappingOrFallback() {
        RuntimeException failure = new RuntimeException("direct-traversal-failure");
        int[] traversals = {0};
        MutableMap<Object, Object> source = directOnly(Map.of("key", "value"),
                traversals, new int[1], failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> finish(source)));
        assertEquals(1, traversals[0]);
    }

    @Test
    void publicDeserializerRetainsSmallMapAndTooManyEntriesContracts() throws Exception {
        TypeReference<FixedSizeMap<String, String>> type = new TypeReference<>() { };
        for (String json : new String[] {"{}", "{\"a\":null}",
                "{\"Aa\":\"first\",\"BB\":null}",
                "{\"a\":\"first\",\"b\":null,\"c\":\"last\"}"}) {
            FixedSizeMap<String, String> decoded = MAPPER.readValue(json, type);
            assertEquals(decoded, MAPPER.readValue(MAPPER.writeValueAsString(decoded), type));
            assertThrows(UnsupportedOperationException.class, () -> decoded.put("extra", "value"));
        }
        MismatchedInputException failure = assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{\"a\":1,\"b\":2,\"c\":3,\"d\":4}", FixedSizeMap.class));
        assertTrue(failure.getMessage().contains("more than 3 entries"));
    }

    @SuppressWarnings("unchecked")
    private static MutableMap<Object, Object> directOnly(Map<Object, Object> source,
            int[] traversals, int[] visits, RuntimeException failure) {
        return (MutableMap<Object, Object>) Proxy.newProxyInstance(
                MutableMap.class.getClassLoader(), new Class<?>[] {MutableMap.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("size")) return source.size();
                    if (method.getName().equals("forEachKeyValue")) {
                        traversals[0]++;
                        if (failure != null) throw failure;
                        Procedure2<Object, Object> procedure = (Procedure2<Object, Object>) arguments[0];
                        source.forEach((key, value) -> { visits[0]++; procedure.value(key, value); });
                        return null;
                    }
                    throw new AssertionError("Unexpected per-entry protocol: " + method.getName());
                });
    }

    private static FixedSizeMap<?, ?> finish(MutableMap<Object, Object> source) throws Exception {
        Method method = TypeHandlerPairs.class.getDeclaredMethod("toFixedSize",
                DeserializationContext.class, MutableMap.class);
        method.setAccessible(true);
        try {
            return (FixedSizeMap<?, ?>) method.invoke(null, null, source);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error serious) throw serious;
            throw error;
        }
    }

    private static FixedSizeMap<?, ?> finishWithPairs(MutableMap<Object, Object> source) {
        Object[] values = new Object[6];
        int offset = 0;
        for (Pair<Object, Object> pair : source.keyValuesView()) {
            values[offset++] = pair.getOne();
            values[offset++] = pair.getTwo();
        }
        return switch (source.size()) {
            case 0 -> Maps.fixedSize.of();
            case 1 -> Maps.fixedSize.of(values[0], values[1]);
            case 2 -> Maps.fixedSize.of(values[0], values[1], values[2], values[3]);
            default -> Maps.fixedSize.of(values[0], values[1], values[2], values[3], values[4], values[5]);
        };
    }
}
