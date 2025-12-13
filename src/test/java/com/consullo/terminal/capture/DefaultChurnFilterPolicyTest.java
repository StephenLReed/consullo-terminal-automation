package com.consullo.terminal.capture;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the default churn suppression policy.
 *
 * @since 1.0
 */
public class DefaultChurnFilterPolicyTest {

  @Test
  @DisplayName("Should suppress minimal spinner tokens")
  void shouldSuppressRow_SpinnerTokens_Suppresses() throws Exception {
    final DefaultChurnFilterPolicy policy = new DefaultChurnFilterPolicy();
    final List<String> samples = new ArrayList<>(0);

    assertThat(policy.shouldSuppressRow("|", samples)).isTrue();
    assertThat(policy.shouldSuppressRow("/", samples)).isTrue();
    assertThat(policy.shouldSuppressRow("-", samples)).isTrue();
    assertThat(policy.shouldSuppressRow("\\", samples)).isTrue();
    assertThat(policy.shouldSuppressRow("...", samples)).isTrue();
  }

  @Test
  @DisplayName("Should suppress simple progress-like lines")
  void shouldSuppressRow_ProgressLike_Suppresses() throws Exception {
    final DefaultChurnFilterPolicy policy = new DefaultChurnFilterPolicy();
    final List<String> samples = new ArrayList<>(0);

    assertThat(policy.shouldSuppressRow("[==========     ] 50%", samples)).isTrue();
  }

  @Test
  @DisplayName("Should not suppress ordinary content lines")
  void shouldSuppressRow_NormalText_DoesNotSuppress() throws Exception {
    final DefaultChurnFilterPolicy policy = new DefaultChurnFilterPolicy();
    final List<String> samples = new ArrayList<>(0);

    assertThat(policy.shouldSuppressRow("Hello world", samples)).isFalse();
  }
}
