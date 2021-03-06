/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiStatement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class IfStatementSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiIfStatement;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();
    result.addAll(expandToWholeLine(editorText, e.getTextRange(), false));

    PsiIfStatement statement = (PsiIfStatement)e;

    final PsiKeyword elseKeyword = statement.getElseElement();
    if (elseKeyword != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                    statement.getTextRange().getEndOffset()),
                                      false));

      final PsiStatement branch = statement.getElseBranch();
      if (branch instanceof PsiIfStatement) {
        PsiIfStatement elseIf = (PsiIfStatement)branch;
        final PsiKeyword element = elseIf.getElseElement();
        if (element != null) {
          result.addAll(expandToWholeLine(editorText,
                                          new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                        elseIf.getThenBranch().getTextRange().getEndOffset()),
                                          false));
        }
      }
    }

    return result;
  }
}
