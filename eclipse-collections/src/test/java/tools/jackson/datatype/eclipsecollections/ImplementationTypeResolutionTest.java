package tools.jackson.datatype.eclipsecollections;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.RandomAccess;
import java.util.Set;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.datatype.eclipsecollections.deser.ImplementationTypeDeserializer;
import tools.jackson.datatype.eclipsecollections.util.ImplementationTypes;

import static org.junit.jupiter.api.Assertions.*;

class ImplementationTypeResolutionTest {
    private static final List<Class<?>> SUPPORTED = Arrays.asList(
            Object.class, Serializable.class, Cloneable.class, Iterable.class,
            Collection.class, List.class, Set.class, RandomAccess.class,
            ArrayList.class, LinkedList.class, HashSet.class,
            RichIterable.class, ListIterable.class, MutableList.class, FastList.class,
            IntList.class, MutableIntList.class, IntArrayList.class);

    @Test
    void frontierMatchesPairwiseDefinitionAcrossOrdersAndDuplicates() {
        Random random = new Random(0x5EEDC011L);
        Class<?>[] implementations = {ArrayList.class, LinkedList.class, HashSet.class,
                FastList.class, IntArrayList.class, String.class, DiamondList.class};
        for (int round = 0; round < 2000; round++) {
            List<Class<?>> supported = new ArrayList<>();
            int count = random.nextInt(40);
            for (int index = 0; index < count; index++) {
                supported.add(SUPPORTED.get(random.nextInt(SUPPORTED.size())));
            }
            Class<?> implementation = implementations[round % implementations.length];
            assertEquals(pairwiseDefinition(implementation, supported),
                    ImplementationTypes.mostSpecificTypes(implementation, supported),
                    () -> implementation + " from " + supported);
        }
    }

    @Test
    void unrelatedInterfacesAndDuplicateWinnersKeepEncounterOrder() {
        List<Class<?>> supported = Arrays.asList(Object.class, Serializable.class,
                Collection.class, Cloneable.class, List.class, Serializable.class,
                RandomAccess.class, List.class);
        List<Class<?>> expected = Arrays.asList(Serializable.class, Cloneable.class,
                List.class, Serializable.class, RandomAccess.class, List.class);
        assertEquals(expected, ImplementationTypes.mostSpecificTypes(ArrayList.class, supported));
        Collections.reverse(supported);
        assertEquals(pairwiseDefinition(ArrayList.class, supported),
                ImplementationTypes.mostSpecificTypes(ArrayList.class, supported));
    }

    @Test
    void emptyResultsRemainMutableAndInvalidInputsKeepTheirContract() {
        List<Class<?>> result = ImplementationTypes.mostSpecificTypes(String.class,
                Collections.singleton(List.class));
        assertTrue(result.isEmpty());
        result.add(Object.class);
        assertEquals(Collections.singletonList(Object.class), result);
        assertEquals(Collections.emptyList(),
                ImplementationTypes.mostSpecificTypes(null, Collections.emptyList()));
        assertThrows(NullPointerException.class,
                () -> ImplementationTypes.mostSpecificTypes(null, List.of(Object.class)));
        assertThrows(NullPointerException.class,
                () -> ImplementationTypes.mostSpecificTypes(String.class, null));
        assertThrows(NullPointerException.class,
                () -> ImplementationTypes.mostSpecificTypes(String.class,
                        Arrays.asList(Object.class, null)));
    }

    @Test
    void apiInterfaceDiscoveryRetainsDepthFirstOrderThroughDiamonds() {
        Class<?>[] implementations = {FastList.class, IntArrayList.class, DiamondList.class,
                Lists.immutable.empty().getClass(), Lists.immutable.of(1).getClass(),
                Lists.immutable.of(1, 2, 3, 4, 5).getClass(),
                Maps.mutable.of("key", "value").getClass(), Sets.mutable.of(1, 2).getClass(),
                String.class};
        for (Class<?> implementation : implementations) {
            LinkedHashSet<Class<?>> expected = new LinkedHashSet<>();
            for (Class<?> type = implementation; type != null; type = type.getSuperclass()) {
                collectAllPaths(type, expected);
            }
            assertArrayEquals(expected.toArray(new Class<?>[0]),
                    new ExposedDeserializer(implementation).apiTypes(), implementation.getName());
        }
    }

    // The definition examines all matching pairs, independently of the streaming
    // frontier implementation, and preserves duplicate matching Class entries.
    private static List<Class<?>> pairwiseDefinition(Class<?> implementation,
            Collection<Class<?>> supported) {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> candidate : supported) {
            if (!candidate.isAssignableFrom(implementation)) {
                continue;
            }
            boolean dominated = false;
            for (Class<?> other : supported) {
                if (other != candidate && other.isAssignableFrom(implementation)
                        && candidate.isAssignableFrom(other)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static void collectAllPaths(Class<?> type, Set<Class<?>> result) {
        for (Class<?> iface : type.getInterfaces()) {
            if (iface.getName().startsWith("org.eclipse.collections.api.")) {
                result.add(iface);
            }
            collectAllPaths(iface, result);
        }
    }

    interface Left extends ListIterable<Object> { }
    interface Right extends ListIterable<Object> { }
    interface Diamond extends Left, Right { }
    abstract static class DiamondList extends FastList<Object> implements Diamond {
        private static final long serialVersionUID = 1L;
    }

    private static class ExposedDeserializer extends ImplementationTypeDeserializer {
        ExposedDeserializer(Class<?> type) {
            super(type, new ValueDeserializer<Object>() {
                @Override
                public Class<?> handledType() {
                    return Object.class;
                }

                @Override
                public Object deserialize(JsonParser parser, DeserializationContext context) {
                    return null;
                }
            });
        }

        Class<?>[] apiTypes() {
            return _apiTypes.clone();
        }
    }
}
