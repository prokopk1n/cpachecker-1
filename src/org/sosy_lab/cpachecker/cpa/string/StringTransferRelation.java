/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.string;

import com.google.common.base.Optional;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.cpa.ifcsecurity.util.SetUtil;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;

public class StringTransferRelation
    extends ForwardingTransferRelation<CIStringState, CIStringState, SingletonPrecision> {

  public StringTransferRelation() {
  }

  @Override
  protected CIStringState handleBlankEdge(BlankEdge cfaEdge) {
    return state;
  }

  @Override
  protected CIStringState handleReturnStatementEdge(CReturnStatementEdge returnEdge) {

    if (returnEdge.asAssignment().isPresent()) {
      CAssignment ass = returnEdge.asAssignment().get();
      StringCExpressionVisitor visitor = new StringCExpressionVisitor(returnEdge, state);

      CIStringState newState = state;
      try {
        newState =
            addCIString(
                newState,
                ((CIdExpression) ass.getLeftHandSide()).getDeclaration().getQualifiedName(),
                ((CExpression) ass.getRightHandSide()).accept(visitor));
        return newState;

      } catch (UnrecognizedCodeException e) {
        e.printStackTrace();
      }
    }

    return state;
  }

  @Override
  protected @Nullable CIStringState
      handleAssumption(CAssumeEdge cfaEdge, CExpression expression, boolean truthAssumption)
          throws CPATransferException {

    if (!(expression instanceof CBinaryExpression)) {
      return null;
    }

    BinaryOperator operator = ((CBinaryExpression)expression).getOperator();
    CExpression operand1 = ((CBinaryExpression)expression).getOperand1();
    CExpression operand2 = ((CBinaryExpression)expression).getOperand2();

    if (!truthAssumption) {
      operator = operator.getOppositLogicalOperator();
    }

    if (operator.getOperator() != "==" && operator.getOperator() != "!=") {
      return state;
    }

    StringCExpressionVisitor visitor = new StringCExpressionVisitor(cfaEdge, state);
    CIString ciStr1 = operand1.accept(visitor);
    CIString ciStr2 = operand2.accept(visitor);

    // if we don't check this cpastring will break on (x == 1) for example
    if (!ciStr1.isBottom() || !ciStr2.isBottom()) {

      Set<Character> set =
        SetUtil.generalizedIntersect(ciStr1.getMaybe().asSet(), ciStr2.getMaybe().asSet());

      switch (operator) {
        case EQUALS: {
          if (set.isEmpty()) {
            return null;
          }
        }
          break;
        case NOT_EQUALS: {
          if (!set.isEmpty()) {
            return null;
          }
        }
          break;
        default:
          break;
      }
    }
    return state;
  }



  @Override
  protected CIStringState
      handleDeclarationEdge(CDeclarationEdge declarationEdge, CDeclaration declaration)
          throws UnrecognizedCodeException {

    CIStringState newState = state;

    if (!(declaration instanceof CVariableDeclaration)) {
      return newState;
    }
    CVariableDeclaration decl = (CVariableDeclaration) declaration;

    // we need initilizer to be CInitializerExpression to take expression from it
    if (!(decl.getInitializer() instanceof CInitializerExpression)) {
      return newState;
    }

    CInitializerExpression init = (CInitializerExpression) decl.getInitializer();
    CExpression exp = init.getExpression();

    newState =
        addCIString(
            newState,
            decl.getQualifiedName(),
            evaluateCIString(newState, exp, declarationEdge));

    return newState;

  }

  @Override
  protected CIStringState handleStatementEdge(CStatementEdge cfaEdge, CStatement statement)
      throws UnrecognizedCodeException {

    // expression is an assignment operation, e.g. a = b;
    if (statement instanceof CAssignment) {
      return handleAssignment((CAssignment) statement, cfaEdge);
    }
    return state;
  }

  private CIStringState handleAssignment(CAssignment assignExpression, CStatementEdge cfaEdge)
      throws UnrecognizedCodeException {
    CIStringState newState = state;

    CExpression op1 = assignExpression.getLeftHandSide();
    CRightHandSide op2 = assignExpression.getRightHandSide();

    if (op1 instanceof CIdExpression) {
      return addCIString(newState, op1, evaluateCIString(newState, op2, cfaEdge));
    } else if (op1 instanceof CArraySubscriptExpression) {
      op1 = ((CArraySubscriptExpression) op1).getArrayExpression();
      return addCIString(newState, op1, evaluateCIString(newState, op2, cfaEdge));
    } else if (op1 instanceof CFieldReference) {
      newState =
          addCIString(
              newState,
              op1.toQualifiedASTString(),
              evaluateCIString(newState, op2, cfaEdge));
    }

    return newState;

  }

  @Override
  protected CIStringState handleFunctionCallEdge(
      CFunctionCallEdge cfaEdge,
      List<CExpression> arguments,
      List<CParameterDeclaration> parameters,
      String calledFunctionName)
      throws UnrecognizedCodeException {

    CIStringState newState = state;

    for (int i = 0; i < parameters.size(); i++) {

      CIString ciString = evaluateCIString(newState, arguments.get(i), cfaEdge);
      String formalParameterName = parameters.get(i).getQualifiedName();

      newState = addCIString(newState, formalParameterName, ciString);

    }

    return newState;
  }

  @Override
  protected CIStringState handleFunctionReturnEdge(
      CFunctionReturnEdge cfaEdge,
      CFunctionSummaryEdge fnkCall,
      CFunctionCall summaryExpr,
      String callerFunctionName)
      throws CPATransferException {

    CIStringState newState = state;
    Optional<CVariableDeclaration> retVar = fnkCall.getFunctionEntry().getReturnVariable();

    if (retVar.isPresent()) {
      newState = newState.removeCIString(retVar.get().getQualifiedName());
    }

    if (summaryExpr instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement funcExp = (CFunctionCallAssignmentStatement) summaryExpr;

      if (state.contains(retVar.get().getQualifiedName())) {
        newState =
            addCIString(
                newState,
                funcExp.getLeftHandSide(),
                state.getCIString(retVar.get().getQualifiedName()));
      }

    } else if (summaryExpr instanceof CFunctionCallStatement) {
      // TODO: what should we do here?
    } else {
      throw new UnrecognizedCodeException("on function return", cfaEdge, summaryExpr);
    }

    return newState;
  }

  private CIStringState addCIString(CIStringState newState, String name, CIString ciString) {
    try {
      newState = newState.addCIString(name, ciString);
    } catch (CPAException | InterruptedException e) {
      e.printStackTrace();
    }
    return newState;
  }

  private CIStringState
      addCIString(CIStringState newState, CExpression expression, CIString ciString) {

    if (expression instanceof CIdExpression) {
        newState =
            addCIString(
                newState,
              ((CIdExpression) expression).getDeclaration().getQualifiedName(),
                ciString);
    }
    return newState;
  }

  // return new domain(expression)
  private CIString
      evaluateCIString(CIStringState ciStringState, CRightHandSide expression, CFAEdge cfaEdge)
          throws UnrecognizedCodeException {
    return expression.accept(new StringCExpressionVisitor(cfaEdge, ciStringState));
  }

}
