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
package com.intellij.psi.impl.source.xml;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlTagNameProvider;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TagNameReference implements PsiReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.TagNameReference");

  protected final boolean myStartTagFlag;
  private final ASTNode myNameElement;

  public TagNameReference(ASTNode nameElement, boolean startTagFlag) {
    myStartTagFlag = startTagFlag;
    myNameElement = nameElement;
  }

  public PsiElement getElement() {
    PsiElement element = myNameElement.getPsi();
    final PsiElement parent = element.getParent();
    return parent instanceof XmlTag ? parent : element;
  }

  @Nullable
  protected XmlTag getTagElement() {
    final PsiElement element = getElement();
    if(element == myNameElement.getPsi()) return null;
    return (XmlTag)element;
  }

  public TextRange getRangeInElement() {
    final ASTNode nameElement = getNameElement();
    if (nameElement == null){
      return TextRange.EMPTY_RANGE;
    }

    int colon = nameElement.getText().indexOf(':') + 1;
    if (myStartTagFlag) {
      final int parentOffset = ((TreeElement)nameElement).getStartOffsetInParent();
      return new TextRange(parentOffset + colon, parentOffset + nameElement.getTextLength());
    }
    else {
      final PsiElement element = getElement();
      if (element == myNameElement) return new TextRange(colon, myNameElement.getTextLength());

      final int elementLength = element.getTextLength();
      int diffFromEnd = 0;

      for(ASTNode node = element.getNode().getLastChildNode(); node != nameElement && node != null; node = node.getTreePrev()) {
        diffFromEnd += node.getTextLength();
      }

      final int nameEnd = elementLength - diffFromEnd;
      return new TextRange(nameEnd - nameElement.getTextLength() + colon, nameEnd);
    }
  }

  private ASTNode getNameElement() {
    return myNameElement;
  }

  public PsiElement resolve() {
    final XmlTag tag = getTagElement();
    final XmlElementDescriptor descriptor = tag != null ? tag.getDescriptor():null;

    LOG.debug("Descriptor for tag " +
              (tag != null ? tag.getName() : "NULL") +
              " is " +
              (descriptor != null ? (descriptor.toString() + ": " + descriptor.getClass().getCanonicalName()) : "NULL"));

    if (descriptor != null){
      return descriptor instanceof AnyXmlElementDescriptor ? tag : descriptor.getDeclaration();
    }
    return null;
  }

  @NotNull
  public String getCanonicalText() {
    return getNameElement().getText();
  }

  @Nullable
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final XmlTag element = getTagElement();
    if (element == null || !myStartTagFlag) return element;

    if (newElementName.indexOf(':') == -1) {
      final String namespacePrefix = element.getNamespacePrefix();
      final int index = newElementName.lastIndexOf('.');

      if (index != -1) {
        final PsiElement psiElement = resolve();
        
        if (psiElement instanceof PsiFile || (psiElement != null && psiElement.isEquivalentTo(psiElement.getContainingFile()))) {
          newElementName = newElementName.substring(0, index);
        }
      }
      newElementName = prependNamespacePrefix(newElementName, namespacePrefix);
    }
    element.setName(newElementName);
    return element;
  }

  private static String prependNamespacePrefix(String newElementName, String namespacePrefix) {
    newElementName = (namespacePrefix.length() > 0 ? namespacePrefix + ":":namespacePrefix) + newElementName;
    return newElementName;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    PsiMetaData metaData = null;

    if (element instanceof PsiMetaOwner){
      final PsiMetaOwner owner = (PsiMetaOwner)element;
      metaData = owner.getMetaData();

      if (metaData instanceof XmlElementDescriptor){
        return getTagElement().setName(metaData.getName(getElement())); // TODO: need to evaluate new ns prefix
      }
    } else if (element instanceof PsiFile) {
      final XmlTag tagElement = getTagElement();
      if (tagElement == null || !myStartTagFlag) return tagElement;
      String newElementName = ((PsiFile)element).getName();
      final int index = newElementName.lastIndexOf('.');

      // TODO: need to evaluate new ns prefix
      newElementName = prependNamespacePrefix(newElementName.substring(0, index), tagElement.getNamespacePrefix());

      return getTagElement().setName(newElementName);
    }

    throw new IncorrectOperationException("Cant bind to not a xml element definition!"+element+","+metaData);
  }

  public boolean isReferenceTo(PsiElement element) {
    return getElement().getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  public LookupElement[] getVariants(){
    final PsiElement element = getElement();
    if(!myStartTagFlag){
      if (element instanceof XmlTag) {
        return new LookupElement[]{createClosingTagLookupElement((XmlTag)element, false)};
      }
      return LookupElement.EMPTY_ARRAY;
    }
    return getTagNameVariants((XmlTag)element, ((XmlTag)element).getNamespacePrefix());
  }

  public LookupElement createClosingTagLookupElement(XmlTag tag, boolean includePrefix) {
    LookupElementBuilder builder = LookupElementBuilder.create(includePrefix || !myNameElement.getText().contains(":") ? tag.getName() : tag.getLocalName());
    return TailTypeDecorator.withTail(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder),
                                      TailType.createSimpleTailType('>'));
  }

  public static LookupElement[] getTagNameVariants(final @NotNull XmlTag tag, final String prefix) {
    List<LookupElement> elements = new ArrayList<LookupElement>();
    for (XmlTagNameProvider tagNameProvider : XmlTagNameProvider.EP_NAME.getExtensions()) {
      tagNameProvider.addTagNameVariants(elements, tag, prefix);
    }
    return elements.toArray(new LookupElement[elements.size()]);
  }

  public boolean isSoft() {
    return false;
  }

  @Nullable
  static TagNameReference createTagNameReference(XmlElement element, @NotNull ASTNode nameElement, boolean startTagFlag) {
    final XmlExtension extension = XmlExtension.getExtensionByElement(element);
    return extension == null ? null : extension.createTagNameReference(nameElement, startTagFlag);
  }
}
