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
package org.sosy_lab.cpachecker.cpa.string.util;

import org.sosy_lab.cpachecker.cpa.smg.util.PersistentSet;

public enum BottomCIString implements CIString {

  INSTANCE;

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public String toString() {
    return Character.toString('\u22A5');

  }

  @Override
  public CIString join(CIString pOther) {
    return this;
  }

  @Override
  public boolean isLessOrEqual(CIString pOther) {
    return true;
  }

  @Override
  public PersistentSet<Character> getCertainly() {
    return null;
  }

  @Override
  public PersistentSet<Character> getMaybe() {
    return null;
  }

}
