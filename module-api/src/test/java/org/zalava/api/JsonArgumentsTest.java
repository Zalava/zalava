package org.zalava.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JsonArgumentsTest {
  @Test
  void snapshotsNestedValuesPreservingNullAndExactNumbers() {
    var values =
        new ArrayList<>(
            Arrays.asList(
                null,
                "text",
                true,
                (byte) 1,
                (short) 2,
                3,
                4L,
                new BigInteger("123456789012345678901234567890"),
                new BigDecimal("0.123456789012345678901234567890"),
                1.25f,
                2.5d));
    var source = new LinkedHashMap<String, Object>();
    source.put("explicitNull", null);
    source.put("nested", Map.of("values", values));
    var snapshot = JsonArguments.immutable(source);
    source.clear();
    values.clear();
    assertThat(snapshot).containsKey("explicitNull").doesNotContainKey("missing");
    assertThat(snapshot.get("explicitNull")).isNull();
    Map<String, Object> nested = (Map<String, Object>) snapshot.get("nested");
    List<?> copied = (List<?>) nested.get("values");
    assertThat(copied).hasSize(11);
    assertThat(copied.get(8)).isEqualTo(new BigDecimal("0.123456789012345678901234567890"));
    assertThatThrownBy(() -> snapshot.put("new", true))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> nested.clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(copied::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThat(JsonArguments.immutable(Map.of())).isEmpty();
  }

  @Test
  void rejectsUnsupportedValuesNonStringKeysAndNonFiniteNumbers() {
    assertThatThrownBy(() -> JsonArguments.immutable(null))
        .isInstanceOf(IllegalArgumentException.class);
    for (Object value :
        List.of(
            new Object(),
            Set.of(1),
            new int[] {1},
            new AtomicInteger(1),
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Float.NaN,
            Float.NEGATIVE_INFINITY)) {
      assertThatThrownBy(() -> JsonArguments.immutable(Map.of("value", value)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported JSON");
    }
    var nullKey = new LinkedHashMap<Object, Object>();
    nullKey.put(null, "value");
    for (Map<?, ?> nested : List.of(Map.of(1, "value"), nullKey)) {
      assertThatThrownBy(() -> JsonArguments.immutable(Map.of("nested", nested)))
          .hasMessageContaining("keys must be strings");
    }
  }

  @Test
  void rejectsCyclesButAllowsSharedAcyclicValues() {
    var cyclicMap = new LinkedHashMap<String, Object>();
    cyclicMap.put("self", cyclicMap);
    assertThatThrownBy(() -> JsonArguments.immutable(cyclicMap)).hasMessageContaining("cycles");
    var cyclicList = new ArrayList<Object>();
    cyclicList.add(cyclicList);
    assertThatThrownBy(() -> JsonArguments.immutable(Map.of("list", cyclicList)))
        .hasMessageContaining("cycles");
    var shared = List.of("value");
    assertThat(JsonArguments.immutable(Map.of("first", shared, "second", shared)))
        .containsEntry("first", shared)
        .containsEntry("second", shared);
  }
}
