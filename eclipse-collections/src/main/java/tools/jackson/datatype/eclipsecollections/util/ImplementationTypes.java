package tools.jackson.datatype.eclipsecollections.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Helper methods for resolving concrete Eclipse Collections implementation classes
 * (like {@code IntIntHashMap} or {@code PairImpl}, as named by polymorphic type ids)
 * into supported API interface types. Shared by collection, map and tuple handling.
 *<p>
 * NOTE: only meant for use by this module: package is not exported.
 */
public final class ImplementationTypes
{
    private ImplementationTypes() { }

    /**
     * Only concrete classes of Eclipse Collections itself are considered implementation
     * types: user-defined types implementing Eclipse Collections interfaces are left to
     * standard databind handling (bean deserialization, creators).
     */
    public static boolean isEclipseImplementationClass(Class<?> rawClass) {
        return !rawClass.isInterface()
                && rawClass.getName().startsWith("org.eclipse.collections.");
    }

    /**
     * @return Types from {@code supportedTypes} assignable from given class that are not
     *    supertypes of any other such type: usually exactly one (or none if class is not
     *    supported). Result does not depend on ordering of {@code supportedTypes}.
     */
    public static List<Class<?>> mostSpecificTypes(Class<?> rawClass,
            Collection<Class<?>> supportedTypes) {
        List<Class<?>> mostSpecific = new ArrayList<>();
        for (Class<?> supported : supportedTypes) {
            if (!supported.isAssignableFrom(rawClass)) {
                continue;
            }
            // Only retain the current most-specific frontier. A removed supertype
            // cannot become most specific again as further candidates arrive.
            boolean dominated = false;
            for (int index = 0; index < mostSpecific.size(); index++) {
                Class<?> existing = mostSpecific.get(index);
                if (existing != supported && supported.isAssignableFrom(existing)) {
                    dominated = true;
                    break;
                }
            }
            if (dominated) {
                continue;
            }

            // Compact in encounter order, keeping duplicate occurrences of the
            // same Class just as the original supportedTypes collection does.
            int retained = 0;
            int size = mostSpecific.size();
            for (int index = 0; index < size; index++) {
                Class<?> existing = mostSpecific.get(index);
                if (existing == supported || !existing.isAssignableFrom(supported)) {
                    if (retained != index) {
                        mostSpecific.set(retained, existing);
                    }
                    retained++;
                }
            }
            while (mostSpecific.size() > retained) {
                mostSpecific.remove(mostSpecific.size() - 1);
            }
            mostSpecific.add(supported);
        }
        return mostSpecific;
    }
}
