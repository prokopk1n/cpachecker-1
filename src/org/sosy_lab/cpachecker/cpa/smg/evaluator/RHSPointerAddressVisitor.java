// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.evaluator;

import java.util.List;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cpa.smg.SMGCPA;
import org.sosy_lab.cpachecker.cpa.smg.SMGInconsistentException;
import org.sosy_lab.cpachecker.cpa.smg.SMGOptions;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.SMGTransferRelationKind;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGAddressValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownExpValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

class RHSPointerAddressVisitor extends PointerVisitor {

  private final SMGRightHandSideEvaluator smgRightHandSideEvaluator;
  private final SMGTransferRelationKind kind;

  public RHSPointerAddressVisitor(
      SMGRightHandSideEvaluator pSmgRightHandSideEvaluator,
      CFAEdge pEdge,
      SMGState pSmgState,
      SMGTransferRelationKind pKind,
      SMGOptions pOptions) {
    super(pSmgRightHandSideEvaluator, pEdge, pSmgState, pOptions);
    smgRightHandSideEvaluator = pSmgRightHandSideEvaluator;
    kind = pKind;
  }

  @Override
  protected List<SMGAddressValueAndState> createAddressOfFunction(
      CIdExpression pIdFunctionExpression) throws SMGInconsistentException {
    SMGState state = getInitialSmgState();
    CFunctionDeclaration functionDcl = (CFunctionDeclaration) pIdFunctionExpression.getDeclaration();
    SMGObject functionObject = state.getObjectForFunction(functionDcl);

    if (functionObject == null) {
      functionObject = state.createObjectForFunction(functionDcl);
    }

    return smgRightHandSideEvaluator.createAddress(
        state, functionObject, SMGZeroValue.INSTANCE);
  }

  @Override
  public List<? extends SMGValueAndState> visit(CFunctionCallExpression pIastFunctionCallExpression)
      throws CPATransferException {
    return smgRightHandSideEvaluator.builtins.handleFunctioncall(
        pIastFunctionCallExpression, getInitialSmgState(), getCfaEdge(), kind);
  }

  @Override
  public List<SMGAddressValueAndState> visit(CStringLiteralExpression pStringLiteralExpression)
      throws CPATransferException {
    SMGState smgState = getInitialSmgState();
    // create a new global region for string literal expression
    SMGObject region =
        smgState.addGlobalVariable(
            smgExpressionEvaluator.machineModel.getSizeofCharInBits()
                * (pStringLiteralExpression.getContentString().length() + 1),
            pStringLiteralExpression.getContentString() + "ID" + SMGCPA.getNewValue());

    CArrayType cParamType = pStringLiteralExpression.transformTypeToArrayType();

    long fieldOffset = 0;
    for (CCharLiteralExpression cCharLiteralExpression :
        pStringLiteralExpression.expandStringLiteral(cParamType)) {
      SMGKnownExpValue explicitOfCharValue =
          SMGKnownExpValue.valueOf(cCharLiteralExpression.getCharacter());
      SMGKnownSymbolicValue symbolicOfCharValue =
          smgState.getSymbolicOfExplicit(explicitOfCharValue);
      if (symbolicOfCharValue == null) {
        symbolicOfCharValue = SMGKnownSymValue.of();
        smgState.putExplicit(symbolicOfCharValue, explicitOfCharValue);
      }
      smgRightHandSideEvaluator.assignFieldToState(
          smgState, getCfaEdge(), region, fieldOffset, symbolicOfCharValue, cParamType.getType());
      fieldOffset += smgExpressionEvaluator.machineModel.getSizeofCharInBits();
    }

    // return pointer for new region
    return smgRightHandSideEvaluator.createAddress(smgState, region, SMGZeroValue.INSTANCE);
  }
}