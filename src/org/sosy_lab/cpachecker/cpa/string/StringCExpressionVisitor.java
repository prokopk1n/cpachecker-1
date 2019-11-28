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

import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.ifcsecurity.util.SetUtil;
import org.sosy_lab.cpachecker.cpa.string.util.CIString;
import org.sosy_lab.cpachecker.cpa.string.util.bottomCIString;
import org.sosy_lab.cpachecker.cpa.string.util.explicitCIString;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;

public class StringCExpressionVisitor
    extends DefaultCExpressionVisitor<CIString, UnrecognizedCodeException>
    implements CRightHandSideVisitor<CIString, UnrecognizedCodeException> {

  private final CFAEdge cfaEdge;
  private final CIStringState state;
  private final BuiltinFunctions builtins;

  public StringCExpressionVisitor(CFAEdge edge, CIStringState pState, BuiltinFunctions pBuiltins) {
    cfaEdge = edge;
    state = pState;
    builtins = pBuiltins;
  }

  @Override
  protected CIString visitDefault(CExpression pExp) {
    return bottomCIString.INSTANCE;
  }

  @Override
  public CIString visit(CArraySubscriptExpression e) throws UnrecognizedCodeException {
    return (e.getArrayExpression()).accept(this);
  }

  @Override
  public CIString visit(CCharLiteralExpression e) throws UnrecognizedCodeException {
    return new explicitCIString(Character.toString(e.getCharacter()));
  }

  @Override
  public CIString visit(CStringLiteralExpression e) throws UnrecognizedCodeException {
    return new explicitCIString(e.getContentString());
  }

  @Override
  public CIString visit(CBinaryExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIString visit(CCastExpression e) throws UnrecognizedCodeException {
    return e.getOperand().accept(this);
  }

  @Override
  public CIString visit(CComplexCastExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIString visit(CFieldReference e) throws UnrecognizedCodeException {
    return state.getCIString(e.toQualifiedASTString());
  }

  @Override
  public CIString visit(CIdExpression e) throws UnrecognizedCodeException {
    return state.getCIString(e.getDeclaration().getQualifiedName());
  }

  @Override
  public CIString visit(CImaginaryLiteralExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIString visit(CFloatLiteralExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIString visit(CIntegerLiteralExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIString visit(CTypeIdExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIString visit(CUnaryExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIString visit(CPointerExpression e) throws UnrecognizedCodeException {
    return (e.getOperand()).accept(this);
  }

  @Override
  public CIString visit(CAddressOfLabelExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIString visit(CFunctionCallExpression fCallExpression)
      throws UnrecognizedCodeException {

    CExpression fNameExpression = fCallExpression.getFunctionNameExpression();

    if (fNameExpression instanceof CIdExpression) {
      String funcName = ((CIdExpression) fNameExpression).getName();

      if (builtins.isABuiltin(funcName)) {
        return evaluateFunctionExpression(funcName, fCallExpression);
      }
    }
    return bottomCIString.INSTANCE;
  }

  private CIString evaluateFunctionExpression(
      String fName,
      CFunctionCallExpression expression) throws UnrecognizedCodeException {
    switch (fName) {
      case "strtok":
        return evaluateSTRTOK(expression);
      case "strstr":
        return evaluateSTRSTR(expression);
      case "strpbrk":
        return evaluateSTRSTR(expression);
      default:
        return explicitCIString.EMPTY;
    }
  }

  private CIString evaluateSTRTOK(CFunctionCallExpression expression)
      throws UnrecognizedCodeException {

    // char *strtok(char *string, const char *delim)

    CExpression s1 = expression.getParameterExpressions().get(0);
    CExpression s2 = expression.getParameterExpressions().get(1);


    CIString ciStr1 = s1.accept(this);
    CIString ciStr2 = s2.accept(this);

    if (ciStr1.isBottom()) {
      // if string = NULL
      if (!builtins.isNEW()) {
        return builtins.getPrevCIString();
      }
      return bottomCIString.INSTANCE;

    } else {
      // if string != NULL
      explicitCIString exCIStr1 = (explicitCIString) ciStr1;

      if (exCIStr1.isEmpty()) {
        // if string is empty we return NULL
        return bottomCIString.INSTANCE;
      }

      builtins.setNEWFalse();

      // Exists one symbol from delim in string?
      Boolean isInters =
          !SetUtil.generalizedIntersect(ciStr1.getMaybe().asSet(), ciStr2.getMaybe().asSet())
              .isEmpty();

      if (isInters) {
        // now we can't say which symbols are certainly in string
        exCIStr1.clearCertainly();
      }
      builtins.setPrevCIString(exCIStr1);
      return exCIStr1;
    }
  }

  private CIString evaluateSTRSTR(CFunctionCallExpression expression)
      throws UnrecognizedCodeException {

    // char *strstr(const char *str1, const char *str2)
    CExpression s1 = expression.getParameterExpressions().get(0);
    CExpression s2 = expression.getParameterExpressions().get(1);

    CIString ciStr1 = s1.accept(this);
    CIString ciStr2 = s2.accept(this);

    if (ciStr1.isBottom() || ciStr2.isBottom()) {
      // ERROR
      // TODO: write it
      return bottomCIString.INSTANCE;
    }

    explicitCIString exCIStr1 = (explicitCIString) ciStr1;
    explicitCIString exCIStr2 = (explicitCIString) ciStr2;

    if (exCIStr1.isLessOrEqual(exCIStr2)) {
      // if str2 is found in str1
      if (exCIStr2.isEmpty()) {
        return exCIStr1;
      }
      // we know only that str2 is in certainly
      return exCIStr1.join(exCIStr2);

    } else {
      // if the str2 is not found in str1 return NULL
      return bottomCIString.INSTANCE;
      }
  }
}
