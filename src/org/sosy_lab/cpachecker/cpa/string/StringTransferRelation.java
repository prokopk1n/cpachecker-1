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
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
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
import org.sosy_lab.cpachecker.cpa.string.util.CIString;
import org.sosy_lab.cpachecker.cpa.string.util.ExplicitCIString;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;

public class StringTransferRelation
    extends ForwardingTransferRelation<CIStringState, CIStringState, SingletonPrecision> {

  private final BuiltinFunctions builtins = new BuiltinFunctions();

  public StringTransferRelation() {
  }

  @Override
  protected CIStringState handleBlankEdge(BlankEdge cfaEdge) {
    return state;
  }

  @Override
  protected CIStringState handleReturnStatementEdge(CReturnStatementEdge returnEdge)
      throws UnrecognizedCodeException {

    CIStringState newState = state;
    if (returnEdge.asAssignment().isPresent()) {
      CAssignment ass = returnEdge.asAssignment().get();
      StringCExpressionVisitor visitor =
          new StringCExpressionVisitor(returnEdge, newState, builtins);

        newState =
            removeAndAddCIString(
                newState,
                ass.getLeftHandSide(),
                ass.getRightHandSide().accept(visitor));
        return newState;
    }

    return state;
  }

  @Override
  protected @Nullable CIStringState
      handleAssumption(CAssumeEdge cfaEdge, CExpression expression, boolean truthAssumption)
          throws CPATransferException {

    CIStringState newState = state;
    if (!(expression instanceof CBinaryExpression)) {
      return null;
    }

    BinaryOperator operator = ((CBinaryExpression)expression).getOperator();
    CExpression operand1 = ((CBinaryExpression)expression).getOperand1();
    CExpression operand2 = ((CBinaryExpression)expression).getOperand2();

    if (!truthAssumption) {
      operator = operator.getOppositLogicalOperator();
    }

    if (operator != BinaryOperator.EQUALS && operator != BinaryOperator.NOT_EQUALS) {
      return state;
    }

    StringCExpressionVisitor visitor = new StringCExpressionVisitor(cfaEdge, newState, builtins);
    CIString ciStr1 = operand1.accept(visitor);
    CIString ciStr2 = operand2.accept(visitor);

    // if we don't check this cpa_string will break on (x == 1) for example
    if (!ciStr1.isBottom() && !ciStr2.isBottom()) {

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
    return newState;
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
        newState.removeAndAddCIString(
            decl.getQualifiedName(),
            evaluateCIString(newState, exp, declarationEdge));

    return newState;

  }

  @Override
  protected CIStringState handleStatementEdge(CStatementEdge cfaEdge, CStatement statement)
      throws UnrecognizedCodeException {

    if (statement instanceof CAssignment) {
      return handleAssignment((CAssignment) statement, cfaEdge);
    } else if (statement instanceof CFunctionCall) {

      CFunctionCall fCall = (CFunctionCall) statement;
      CFunctionCallExpression fCallExp = fCall.getFunctionCallExpression();
      CExpression fNameExpression = fCallExp.getFunctionNameExpression();

      if (fNameExpression instanceof CIdExpression) {
        String funcName = ((CIdExpression) fNameExpression).getName();

        if (builtins.isABuiltin(funcName)) {
          return handleBuiltinFunctionCall(cfaEdge, fCallExp, funcName);
        }
      }
    }
    return state;
  }

  protected CIStringState
      handleBuiltinFunctionCall(
          CStatementEdge cfaEdge,
          CFunctionCallExpression fCallExp,
          String calledFunctionName) throws UnrecognizedCodeException {
    switch (calledFunctionName) {
      case "strcpy":
        return evaluateSTRCPY("strcpy", cfaEdge, fCallExp);
      case "strncpy":
        return evaluateSTRCPY("strncpy", cfaEdge, fCallExp);
      case "strcat":
        return evaluateSTRCAT("strcat", cfaEdge, fCallExp);
      case "strncat":
        return evaluateSTRCAT("strncat", cfaEdge, fCallExp);
      case "memcpy":
        return evaluateSTRCAT("memcpy", cfaEdge, fCallExp);
      case "memmove":
        return evaluateSTRCAT("memmove", cfaEdge, fCallExp);
      default:
        return state;
    }
  }

  private CIStringState handleAssignment(CAssignment assignExpression, CStatementEdge cfaEdge)
      throws UnrecognizedCodeException {

    CIStringState newState = state;

    CExpression op1 = assignExpression.getLeftHandSide();
    CRightHandSide op2 = assignExpression.getRightHandSide();

    return removeAndAddCIString(newState, op1, evaluateCIString(newState, op2, cfaEdge));

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

      newState = newState.removeAndAddCIString(formalParameterName, ciString);

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
            removeAndAddCIString(
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

  private CIStringState
      evaluateSTRCPY(String fName, CStatementEdge cfaEdge, CFunctionCallExpression expression)
      throws UnrecognizedCodeException {
    CIStringState newState = state;

    CExpression s1 = expression.getParameterExpressions().get(0);
    CExpression s2 = expression.getParameterExpressions().get(1);

    StringCExpressionVisitor visitor = new StringCExpressionVisitor(cfaEdge, newState, builtins);
    CIString ciStr2 = s2.accept(visitor);

    if (!ciStr2.isBottom() || !fName.equals("strcpy")) {

      ExplicitCIString newCIStr = (ExplicitCIString) ciStr2;
      newCIStr.clearCertainly();

      return removeAndAddCIString(newState, s1, newCIStr);

    } else {
      return removeAndAddCIString(newState, s1, ciStr2);
    }
  }

  private CIStringState
      evaluateSTRCAT(String fName, CStatementEdge cfaEdge, CFunctionCallExpression expression)
      throws UnrecognizedCodeException {
    CIStringState newState = state;

    CExpression s1 = expression.getParameterExpressions().get(0);
    CExpression s2 = expression.getParameterExpressions().get(1);

    StringCExpressionVisitor visitor = new StringCExpressionVisitor(cfaEdge, newState, builtins);
    CIString ciStr1 = s1.accept(visitor);
    CIString ciStr2 = s2.accept(visitor);

    if (!ciStr1.isBottom() && !ciStr2.isBottom()) {

      ExplicitCIString exCIStr1 = (ExplicitCIString) ciStr1;
      ExplicitCIString exCIStr2 = (ExplicitCIString) ciStr2;

      if (fName.equals("strcat")) {
        exCIStr1.addToSertainly(exCIStr2.getCertainly().asSet());
      }
      exCIStr1.addToMaybe(exCIStr2.getMaybe().asSet());

      return removeAndAddCIString(newState, s1, exCIStr1);
    }
    return newState;
  }

  private CIStringState
      removeAndAddCIString(CIStringState newState, CExpression expression, CIString ciString) {

    if (expression instanceof CArraySubscriptExpression) {
      expression = ((CArraySubscriptExpression) expression).getArrayExpression();
      newState = addCIString(newState, expression, ciString);
    }

    if (expression instanceof CIdExpression) {
        newState =
          newState.removeAndAddCIString(
              ((CIdExpression) expression).getDeclaration().getQualifiedName(),
                ciString);
    } else if (expression instanceof CFieldReference) {
      newState = newState.removeAndAddCIString(expression.toQualifiedASTString(), ciString);
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
          newState.addCIString(
              ((CIdExpression) expression).getDeclaration().getQualifiedName(),
              ciString);
    } else if (expression instanceof CFieldReference) {
      newState = newState.addCIString(expression.toQualifiedASTString(), ciString);
    }

    return newState;
  }

  // return new domain(expression)
  private CIString
      evaluateCIString(CIStringState ciStringState, CRightHandSide expression, CFAEdge cfaEdge)
          throws UnrecognizedCodeException {
    return expression.accept(new StringCExpressionVisitor(cfaEdge, ciStringState, builtins));
  }

}
