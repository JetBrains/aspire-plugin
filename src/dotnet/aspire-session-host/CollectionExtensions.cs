using System;
using System.Collections.Generic;

namespace JetBrains.Rider.Aspire.SessionHost;

public static class CollectionExtensions
{
  public static bool TryAdd<TKey, TValue>(this IDictionary<TKey, TValue> dictionary, TKey key, TValue value)
  {
    ArgumentNullException.ThrowIfNull(dictionary);
    if (dictionary.ContainsKey(key))
      return false;
    dictionary.Add(key, value);
    return true;
  }
}