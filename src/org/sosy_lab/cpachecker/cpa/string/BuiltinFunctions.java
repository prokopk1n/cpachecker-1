/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.cpa.string;

import com.google.common.collect.Sets;
import java.util.Set;
import org.sosy_lab.cpachecker.cpa.string.util.CIString;
import org.sosy_lab.cpachecker.cpa.string.util.bottomCIString;

public class BuiltinFunctions {

  private final Set<String> BFUNC =
      Sets.newHashSet(
          "strcpy",
          "strncpy",
          "strcat",
          "strncat",
          "memcpy",
          "memmove",
          "strtok",
          "strstr",
          "strpbrk");

  private Boolean STRTOK_NEW;
  private CIString prevCIString;

  public final boolean isABuiltin(String fName) {
    return BFUNC.contains(fName);
  }

  BuiltinFunctions() {
    STRTOK_NEW = true;
    prevCIString = bottomCIString.INSTANCE;
  }

  public void setNEWTrue() {
    STRTOK_NEW = true;
  }

  public void setNEWFalse() {
    STRTOK_NEW = false;
  }

  public Boolean isNEW() {
    return STRTOK_NEW;
  }

  public void setPrevCIString(CIString ciString) {
    prevCIString = ciString;
  }

  public CIString getPrevCIString() {
    return prevCIString;
  }
}
