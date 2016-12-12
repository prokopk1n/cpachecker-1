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
package org.sosy_lab.cpachecker.cpa.automaton;

import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonASTComparator.ASTMatcherProvider;

/**
 * Matches a pattern against all dereferenced expression in an AST.
 */
public class AutomatonASTDerefMatcher {

  private final ASTMatcherProvider patternAST;

  public AutomatonASTDerefMatcher(ASTMatcherProvider pAstMatcherProvider) {
    patternAST = pAstMatcherProvider;
  }

  boolean matches(CAstNode pSource, AutomatonExpressionArguments pArgs) {
    return true;
  }
}
