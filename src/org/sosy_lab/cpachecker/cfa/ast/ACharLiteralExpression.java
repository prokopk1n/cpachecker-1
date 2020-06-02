// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast;

import org.sosy_lab.cpachecker.cfa.types.Type;

/**
 * This is the abstract Class for Character Literals.
 */
public abstract class ACharLiteralExpression extends ALiteralExpression {


  private static final long serialVersionUID = 6806494425621157804L;
  private final char character;

  public ACharLiteralExpression(FileLocation pFileLocation, Type pType, char pCharacter) {
    super(pFileLocation, pType);
    character = pCharacter;
  }

  public char getCharacter() {
    return character;
  }

  @Override
  public Character getValue() {
    return getCharacter();
  }

  @Override
  public String toASTString() {
    if (character >= ' ' && character < 128) {
      return "'" + character + "'";
    } else {
      return "'\\x" + Integer.toHexString(character) + "'";
    }
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 7;
    result = prime * result + character;
    result = prime * result + super.hashCode();
    return result;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof ACharLiteralExpression)
        || !super.equals(obj)) {
      return false;
    }

    ACharLiteralExpression other = (ACharLiteralExpression) obj;

    return other.character == character;
  }

}
