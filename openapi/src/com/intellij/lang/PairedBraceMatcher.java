/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.lang;

/**
 * Defines the brace matching support required for a custom language. For paired
 * brace matching to work, the language must also provide a
 * {@link com.intellij.openapi.fileTypes.SyntaxHighlighter} and return the correct
 * lexer from <code>getHighlightingLexer()</code>.
 *
 * @author max
 * @see Language#getPairedBraceMatcher()
 * @see BracePair
 */
public interface PairedBraceMatcher {
  /**
   * Returns the array of definitions for brace pairs that need to be matched when
   * editing code in the language.
   *
   * @return the array of brace pair definitions.
   */
  BracePair[] getPairs();
}
