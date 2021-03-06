/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.xml.*;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DefaultXmlTagNameProvider implements XmlTagNameProvider {
  @Override
  public void addTagNameVariants(List<LookupElement> elements, @NotNull XmlTag tag, String prefix) {
    final List<String> namespaces;
    if (prefix.isEmpty()) {
      namespaces = new ArrayList<String>(Arrays.asList(tag.knownNamespaces()));
      namespaces.add(XmlUtil.EMPTY_URI); // empty namespace
    }
    else {
      namespaces = new ArrayList<String>(Collections.singletonList(tag.getNamespace()));
    }
    List<String> nsInfo = new ArrayList<String>();
    final String[] variants = getTagNameVariants(tag, namespaces, nsInfo);
    for (int i = 0, variantsLength = variants.length; i < variantsLength; i++) {
      String qname = variants[i];
      if (!prefix.isEmpty() && qname.startsWith(prefix + ":")) {
        qname = qname.substring(prefix.length() + 1);
      }
      LookupElementBuilder lookupElement = LookupElementBuilder.create(qname);
      final int separator = qname.indexOf(':');
      if (separator > 0) {
        lookupElement = lookupElement.withLookupString(qname.substring(separator + 1));
      }
      String ns = nsInfo.get(i);
      if (StringUtil.isNotEmpty(ns)) {
        lookupElement = lookupElement.withTypeText(ns, true);
      }
      elements.add(lookupElement.withInsertHandler(XmlTagInsertHandler.INSTANCE));
    }
  }

  public static String[] getTagNameVariants(final XmlTag element,
                                            final Collection<String> namespaces,
                                            @Nullable List<String> nsInfo) {

    final List<String> variants = TagNameVariantCollector
      .getTagNameVariants(element, namespaces, nsInfo, new Function<XmlElementDescriptor, String>() {
        @Override
        public String fun(XmlElementDescriptor descriptor) {
          return descriptor.getName(element);
        }
      });
    return ArrayUtil.toStringArray(variants);
  }
}
