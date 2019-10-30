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
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
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

      CIStringState newState;
      try {
        newState =
            addCIString(
                state,
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

    Set<Character> set =
        SetUtil.generalizedIntersect(ciStr1.getMaybe().asSet(), ciStr2.getMaybe().asSet());

    switch (operator) {
      case EQUALS: {
          if(set.isEmpty()) {
            return null;
          }
      }
        break;
      case NOT_EQUALS: {
        if(!set.isEmpty()) {
          return null;
        }
      }
        break;
      default:
        break;
    }
    return state;
  }



  @Override
  protected CIStringState
      handleDeclarationEdge(ADeclarationEdge declarationEdge, ADeclaration declaration)
          throws UnrecognizedCodeException {

    if (!(declaration instanceof CVariableDeclaration)) {
      return state;
    }
    CVariableDeclaration decl = (CVariableDeclaration) declaration;

    if (!(decl.getInitializer() instanceof CInitializerExpression)) {
      return state;
    }
    CInitializerExpression init = (CInitializerExpression) decl.getInitializer();
    CExpression exp = init.getExpression();

    if (!(exp instanceof CStringLiteralExpression)) {
      return state;
    }

    String value = ((CStringLiteralExpression) exp).getContentString();

      CIStringState newState =
          addCIString(state, decl.getQualifiedName(), new CIString(value));
      return newState;

  }

  @Override
  protected CIStringState handleStatementEdge(CStatementEdge cfaEdge, CStatement statement)
      throws UnrecognizedCodeException {
    CIStringState newState = state;
    // expression is an assignment operation, e.g. a = b;
    if (statement instanceof CAssignment) {

      CAssignment assignExpression = (CAssignment) statement;
      CExpression op1 = assignExpression.getLeftHandSide();
      CRightHandSide op2 = assignExpression.getRightHandSide();

      newState = addCIString(newState, op1, evaluateCIString(state, op2, cfaEdge));
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

      CIString ciString = evaluateCIString(state, arguments.get(i), cfaEdge);
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
      //
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

    if (expression instanceof CArraySubscriptExpression) {
      expression = ((CArraySubscriptExpression) expression).getArrayExpression();
    }

    if (expression instanceof CIdExpression) {
        newState =
            addCIString(
                newState,
              ((CIdExpression) expression).getDeclaration().getQualifiedName(),
                ciString);
    }
    return newState;
  }

  private CIString
      evaluateCIString(CIStringState ciState, CRightHandSide expression, CFAEdge cfaEdge)
          throws UnrecognizedCodeException {
    return expression.accept(new StringCExpressionVisitor(cfaEdge, ciState));
  }

}
