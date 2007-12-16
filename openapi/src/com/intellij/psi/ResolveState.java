/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;

import java.util.HashMap;
import java.util.Map;

public class ResolveState {
  private Map<Object, Object> myValues = null;
  private final static Map<Object, Object> ourDefaults = new HashMap<Object, Object>();
  private static final ResolveState ourInitialState = new ResolveState();

  static {
    ourDefaults.put(PsiSubstitutor.KEY, PsiSubstitutor.EMPTY); // TODO Remove to Java module.
  }

  public static ResolveState initial() {
    return ourInitialState;
  }

  public static <T> void defaultsTo(Key<T> key, T value) {
    ourDefaults.put(key, value);
  }

  public <T> ResolveState put(Key<T> key, T value) {
    final ResolveState copy = new ResolveState();
    copy.myValues = new THashMap<Object, Object>();
    if (myValues != null) {
      copy.myValues.putAll(myValues);
    }
    copy.myValues.put(key, value);
    return copy;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T get(Key<T> key) {
    T value = myValues != null ? (T)myValues.get(key) : null;
    if (value == null) {
      value = (T)ourDefaults.get(key);
    }

    return value;
  }
}