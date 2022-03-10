// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.evaluator;

import java.util.List;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.smg.SMGBuiltins;
import org.sosy_lab.cpachecker.cpa.smg.SMGOptions;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.SMGTransferRelationKind;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGValueAndState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

class RHSExpressionValueVisitor extends ExpressionValueVisitor {

  private final SMGBuiltins builtins;
  private final SMGTransferRelationKind kind;

  public RHSExpressionValueVisitor(
      SMGRightHandSideEvaluator pSmgRightHandSideEvaluator,
      SMGBuiltins pBuiltins,
      CFAEdge pEdge,
      SMGState pSmgState,
      SMGTransferRelationKind pKind,
      SMGOptions pOptions) {
    super(pSmgRightHandSideEvaluator, pEdge, pSmgState, pOptions);
    builtins = pBuiltins;
    kind = pKind;
  }

  @Override
  public List<? extends SMGValueAndState> visit(CFunctionCallExpression pIastFunctionCallExpression)
      throws CPATransferException {
    return builtins.handleFunctioncall(
        pIastFunctionCallExpression, getInitialSmgState(), getCfaEdge(), kind);
  }
}