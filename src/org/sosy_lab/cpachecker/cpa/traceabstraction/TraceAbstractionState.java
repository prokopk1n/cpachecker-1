// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.traceabstraction;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;

public class TraceAbstractionState implements AbstractState, Graphable {

  private static final TraceAbstractionState TOP = new TraceAbstractionState(ImmutableMap.of());

  /** Create a new 'TOP'-state with empty predicates. */
  static TraceAbstractionState createInitState() {
    return TOP;
  }

  /**
   * This map links {@link InterpolationSequence}-objects to predicates that currently hold in this
   * state. Only entries with non-trivial predicates are contained (i.e., other than true and
   * false).
   */
  private final ImmutableMap<InterpolationSequence, AbstractionPredicate> activePredicates;

  TraceAbstractionState(Map<InterpolationSequence, AbstractionPredicate> pActivePredicates) {
    activePredicates = ImmutableMap.copyOf(pActivePredicates);
  }

  boolean containsPredicates() {
    return !activePredicates.isEmpty();
  }

  ImmutableMap<InterpolationSequence, AbstractionPredicate> getActivePredicates() {
    return activePredicates;
  }

  @Override
  public int hashCode() {
    return Objects.hash(activePredicates);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof TraceAbstractionState)) {
      return false;
    }
    TraceAbstractionState other = (TraceAbstractionState) obj;
    return Objects.equals(activePredicates, other.activePredicates);
  }

  @Override
  public String toString() {
    if (!containsPredicates()) {
      return "_empty_preds_";
    }

    return FluentIterable.from(activePredicates.entrySet())
        .transform(x -> x.getKey() + ":" + x.getValue())
        .join(Joiner.on("; "));
  }

  @Override
  public String toDOTLabel() {
    // TODO: remove graphable interface eventually
    // (currently used for debugging)
    if (!containsPredicates()) {
      return "";
    }

    return FluentIterable.from(activePredicates.values())
        .transform(AbstractionPredicate::getSymbolicAtom)
        .join(Joiner.on("; "));
  }

  @Override
  public boolean shouldBeHighlighted() {
    return false;
  }
}