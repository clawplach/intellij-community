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

/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HighlightNamesUtil {
  private static final Logger LOG = Logger.getInstance("#" + HighlightNamesUtil.class.getName());

  @Nullable
  public static HighlightInfo highlightMethodName(@NotNull PsiMethod method,
                                                  final PsiElement elementToHighlight,
                                                  final boolean isDeclaration,
                                                  @NotNull EditorColorsScheme colorsScheme) {
    return highlightMethodName(method, elementToHighlight, elementToHighlight.getTextRange(), colorsScheme, isDeclaration);
  }

  @Nullable
  public static HighlightInfo highlightMethodName(@NotNull PsiMethod method,
                                                  final PsiElement elementToHighlight,
                                                  TextRange range, @NotNull EditorColorsScheme colorsScheme, final boolean isDeclaration) {
    boolean isInherited = false;

    if (!isDeclaration) {
      if (isCalledOnThis(elementToHighlight)) {
        PsiClass enclosingClass = PsiTreeUtil.getParentOfType(elementToHighlight, PsiClass.class);
        isInherited = enclosingClass != null && enclosingClass.isInheritor(method.getContainingClass(), true);
      }
    }

    HighlightInfoType type = getMethodNameHighlightType(method, isDeclaration, isInherited);
    if (type != null && elementToHighlight != null) {
      TextAttributes attributes = mergeWithScopeAttributes(method, type, colorsScheme);
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
      if (attributes != null) {
        builder.textAttributes(attributes);
      }
      return builder.createUnconditionally();
    }
    return null;
  }

  private static boolean isCalledOnThis(PsiElement elementToHighlight) {
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(elementToHighlight, PsiMethodCallExpression.class);
    if (methodCallExpression != null) {
      PsiElement qualifier = methodCallExpression.getMethodExpression().getQualifier();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        return true;
      }
    }
    return false;
  }

  private static TextAttributes mergeWithScopeAttributes(final PsiElement element,
                                                         @NotNull HighlightInfoType type,
                                                         @NotNull EditorColorsScheme colorsScheme) {
    TextAttributes regularAttributes = HighlightInfo.getAttributesByType(element, type, colorsScheme);
    if (element == null) return regularAttributes;
    TextAttributes scopeAttributes = getScopeAttributes(element, colorsScheme);
    return TextAttributes.merge(scopeAttributes, regularAttributes);
  }

  @Nullable
  public static HighlightInfo highlightClassName(PsiClass aClass, PsiElement elementToHighlight, @NotNull EditorColorsScheme colorsScheme) {
    HighlightInfoType type = getClassNameHighlightType(aClass, elementToHighlight);
    if (elementToHighlight != null) {
      TextAttributes attributes = mergeWithScopeAttributes(aClass, type, colorsScheme);
      TextRange range = elementToHighlight.getTextRange();
      if (elementToHighlight instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)elementToHighlight;
        PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null) {
          final TextRange paramListRange = parameterList.getTextRange();
          if (paramListRange.getEndOffset() > paramListRange.getStartOffset()) {
            range = new TextRange(range.getStartOffset(), paramListRange.getStartOffset());
          }
        }
      }

      // This will highlight @ sign in annotation as well.
      final PsiElement parent = elementToHighlight.getParent();
      if (parent instanceof PsiAnnotation) {
        final PsiAnnotation psiAnnotation = (PsiAnnotation)parent;
        range = new TextRange(psiAnnotation.getTextRange().getStartOffset(), range.getEndOffset());
      }

      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
      if (attributes != null) {
        builder.textAttributes(attributes);
      }
      return builder.createUnconditionally();
    }
    return null;
  }

  @Nullable
  public static HighlightInfo highlightVariableName(final PsiVariable variable,
                                                    final PsiElement elementToHighlight,
                                                    @NotNull EditorColorsScheme colorsScheme) {
    HighlightInfoType varType = getVariableNameHighlightType(variable);
    if (varType != null) {
      if (variable instanceof PsiField) {
        TextAttributes attributes = mergeWithScopeAttributes(variable, varType, colorsScheme);
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(varType).range(elementToHighlight.getTextRange());
        if (attributes != null) {
          builder.textAttributes(attributes);
        }
        return builder.createUnconditionally();
      }
      return HighlightInfo.newHighlightInfo(varType).range(elementToHighlight).create();
    }
    return null;
  }

  @Nullable
  public static HighlightInfo highlightClassNameInQualifier(final PsiJavaCodeReferenceElement element,
                                                            @NotNull EditorColorsScheme colorsScheme) {
    PsiExpression qualifierExpression = null;
    if (element instanceof PsiReferenceExpression) {
      qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
    }
    if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
      if (resolved instanceof PsiClass) {
        return highlightClassName((PsiClass)resolved, qualifierExpression, colorsScheme);
      }
    }
    return null;
  }

  private static HighlightInfoType getMethodNameHighlightType(@NotNull PsiMethod method, boolean isDeclaration, boolean isInheritedMethod) {
    if (method.isConstructor()) {
      return isDeclaration ? HighlightInfoType.CONSTRUCTOR_DECLARATION : HighlightInfoType.CONSTRUCTOR_CALL;
    }
    if (isDeclaration) return HighlightInfoType.METHOD_DECLARATION;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return HighlightInfoType.STATIC_METHOD;
    }
    if (isInheritedMethod) return HighlightInfoType.INHERITED_METHOD;
    if(method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return HighlightInfoType.ABSTRACT_METHOD;
    }
    return HighlightInfoType.METHOD_CALL;
  }

  @Nullable
  private static HighlightInfoType getVariableNameHighlightType(PsiVariable var) {
    if (var instanceof PsiLocalVariable
        || var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiForeachStatement) {
      return HighlightInfoType.LOCAL_VARIABLE;
    }
    if (var instanceof PsiField) {
      return var.hasModifierProperty(PsiModifier.STATIC) ? var.hasModifierProperty(PsiModifier.FINAL)
                                                            ? HighlightInfoType.STATIC_FINAL_FIELD
                                                            : HighlightInfoType.STATIC_FIELD : HighlightInfoType.INSTANCE_FIELD;
    }
    if (var instanceof PsiParameter) {
      return HighlightInfoType.PARAMETER;
    }
    return null;
  }

  @NotNull
  private static HighlightInfoType getClassNameHighlightType(@Nullable PsiClass aClass, @Nullable PsiElement element) {
    if (element instanceof PsiJavaCodeReferenceElement && element.getParent() instanceof PsiAnonymousClass) {
      return HighlightInfoType.ANONYMOUS_CLASS_NAME;
    }
    if (aClass != null) {
      if (aClass.isAnnotationType()) return HighlightInfoType.ANNOTATION_NAME;
      if (aClass.isInterface()) return HighlightInfoType.INTERFACE_NAME;
      if (aClass.isEnum()) return HighlightInfoType.ENUM_NAME;
      if (aClass instanceof PsiTypeParameter) return HighlightInfoType.TYPE_PARAMETER_NAME;
      final PsiModifierList modList = aClass.getModifierList();
      if (modList != null && modList.hasModifierProperty(PsiModifier.ABSTRACT)) return HighlightInfoType.ABSTRACT_CLASS_NAME;
    }
    // use class by default
    return HighlightInfoType.CLASS_NAME;
  }

  @Nullable
  public static HighlightInfo highlightReassignedVariable(PsiVariable variable, PsiElement elementToHighlight) {
    if (variable instanceof PsiLocalVariable) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.REASSIGNED_LOCAL_VARIABLE).range(elementToHighlight).create();
    }
    if (variable instanceof PsiParameter) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.REASSIGNED_PARAMETER).range(elementToHighlight).create();
    }
    return null;
  }

  private static TextAttributes getScopeAttributes(@NotNull PsiElement element, @NotNull EditorColorsScheme colorsScheme) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    TextAttributes result = null;
    final DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(file.getProject());
    List<Pair<NamedScope,NamedScopesHolder>> scopes = daemonCodeAnalyzer.getScopeBasedHighlightingCachedScopes();
    for (Pair<NamedScope, NamedScopesHolder> scope : scopes) {
      NamedScope namedScope = scope.getFirst();
      NamedScopesHolder scopesHolder = scope.getSecond();
      PackageSet packageSet = namedScope.getValue();
      if (packageSet != null && packageSet.contains(file, scopesHolder)) {
        TextAttributesKey scopeKey = ColorAndFontOptions.getScopeTextAttributeKey(namedScope.getName());
        TextAttributes attributes = colorsScheme.getAttributes(scopeKey);
        if (attributes == null || attributes.isEmpty()) {
          continue;
        }
        result = TextAttributes.merge(attributes, result);
      }
    }
    return result;
  }

  public static TextRange getMethodDeclarationTextRange(@NotNull PsiMethod method) {
    if (method instanceof JspHolderMethod) return TextRange.EMPTY_RANGE;
    int start = stripAnnotationsFromModifierList(method.getModifierList());
    final TextRange throwsRange = method.getThrowsList().getTextRange();
    LOG.assertTrue(throwsRange != null, method);
    int end = throwsRange.getEndOffset();
    return new TextRange(start, end);
  }

  public static TextRange getFieldDeclarationTextRange(@NotNull PsiField field) {
    int start = stripAnnotationsFromModifierList(field.getModifierList());
    int end = field.getNameIdentifier().getTextRange().getEndOffset();
    return new TextRange(start, end);
  }

  public static TextRange getClassDeclarationTextRange(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiEnumConstantInitializer) {
      return ((PsiEnumConstantInitializer)aClass).getEnumConstant().getNameIdentifier().getTextRange();
    }
    final PsiElement psiElement = aClass instanceof PsiAnonymousClass
                                  ? ((PsiAnonymousClass)aClass).getBaseClassReference()
                                  : aClass.getModifierList() == null ? aClass.getNameIdentifier() : aClass.getModifierList();
    if(psiElement == null) return new TextRange(aClass.getTextRange().getStartOffset(), aClass.getTextRange().getStartOffset());
    int start = stripAnnotationsFromModifierList(psiElement);
    PsiElement endElement = aClass instanceof PsiAnonymousClass ?
                            ((PsiAnonymousClass)aClass).getBaseClassReference() :
                            aClass.getImplementsList();
    if (endElement == null) endElement = aClass.getNameIdentifier();
    TextRange endTextRange = endElement == null ? null : endElement.getTextRange();
    int end = endTextRange == null ? start : endTextRange.getEndOffset();
    return new TextRange(start, end);
  }

  private static int stripAnnotationsFromModifierList(@NotNull PsiElement element) {
    TextRange textRange = element.getTextRange();
    if (textRange == null) return 0;
    PsiAnnotation lastAnnotation = null;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof PsiAnnotation) lastAnnotation = (PsiAnnotation)child;
    }
    if (lastAnnotation == null) {
      return textRange.getStartOffset();
    }
    ASTNode node = lastAnnotation.getNode();
    if (node != null) {
      do {
        node = TreeUtil.nextLeaf(node);
      }
      while (node != null && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(node.getElementType()));
    }
    if (node != null) return node.getTextRange().getStartOffset();
    return textRange.getStartOffset();
  }
}
