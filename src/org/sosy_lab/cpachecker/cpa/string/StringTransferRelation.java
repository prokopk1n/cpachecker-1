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

import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.cpa.ifcsecurity.util.SetUtil;
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
  protected CIStringState handleReturnStatementEdge(AReturnStatementEdge returnEdge)
      throws UnrecognizedCodeException {
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
    CIStringState ciStr1 = operand1.accept(visitor);
    CIStringState ciStr2 = operand2.accept(visitor);

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

    CIStringState newState = new CIStringState(value);
    return newState;
  }

}
