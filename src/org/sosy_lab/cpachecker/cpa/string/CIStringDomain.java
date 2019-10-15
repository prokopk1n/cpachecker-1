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

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.ifcsecurity.util.SetUtil;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class CIStringDomain implements AbstractDomain {

  @Override
  public AbstractState join(AbstractState pState1, AbstractState pState2)
      throws CPAException, InterruptedException {

    CIString str1 = (CIString) pState1;
    CIString str2 = (CIString) pState2;
    CIString str = new CIString();

    if (str1 == null || str2 == null) {
      return null;
    }
    str.SetCertainly(SetUtil.intersect(str1.GetCertainly(), str2.GetCertainly()));
    str.SetMaybe(SetUtil.union(str1.GetMaybe(), str2.GetMaybe()));

    return str;
  }

  @Override
  public boolean isLessOrEqual(AbstractState pState1, AbstractState pState2)
      throws CPAException, InterruptedException {
    CIString str1 = (CIString) pState1;
    CIString str2 = (CIString) pState2;

    if (str1 != null && str2 != null) {
      if (str1.equals(CIString.BOTTOM)) {
        return true;
      }
      return str1.GetCertainly().containsAll(str2.GetCertainly())
          && str2.GetMaybe().containsAll(str1.GetMaybe());
    }
    return false;
  }

}

