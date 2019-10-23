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
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;

public class StringCExpressionVisitor
  extends DefaultCExpressionVisitor<CIStringState, UnrecognizedCodeException>
  implements CRightHandSideVisitor<CIStringState, UnrecognizedCodeException> {

  private CFAEdge cfaEdge;
  private CIStringState state;

  public StringCExpressionVisitor(
      CFAEdge pEdgeOfExpr,
      CIStringState pState) {
    cfaEdge = pEdgeOfExpr;
    state = pState;
  }

  @Override
  protected CIStringState visitDefault(CExpression pExp) {
    return state;
  }

  @Override
  public CIStringState visit(CArraySubscriptExpression e) throws UnrecognizedCodeException {

    CExpression exp = e.getArrayExpression();

    if (exp instanceof CCharLiteralExpression) {
      return visit((CCharLiteralExpression) exp);
    }
    if (exp instanceof CStringLiteralExpression) {
      return visit((CStringLiteralExpression) exp);
    }

    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CCharLiteralExpression e) throws UnrecognizedCodeException {
    char exp = e.getCharacter();
    return new CIStringState(Character.toString(exp));
  }

  @Override
  public CIStringState visit(CStringLiteralExpression e) throws UnrecognizedCodeException {
    String exp = e.getContentString();
    return new CIStringState(exp);
  }

  @Override
  public CIStringState visit(CBinaryExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CCastExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CComplexCastExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CFieldReference e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CIdExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CImaginaryLiteralExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CFloatLiteralExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CIntegerLiteralExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CTypeIdExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CUnaryExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CPointerExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CAddressOfLabelExpression e) throws UnrecognizedCodeException {
    return visitDefault(e);
  }

  @Override
  public CIStringState visit(CFunctionCallExpression pIastFunctionCallExpression)
      throws UnrecognizedCodeException {
    // TODO Auto-generated method stub
    return null;
  }

}
