extern alias SystemCollections;

using System.Collections.Generic;

namespace AspireSessionHost;

public static class CollectionExtensions
{
  public static bool TryAdd<TKey, TValue>(this IDictionary<TKey, TValue> dictionary, TKey key, TValue value)
  {
      return SystemCollections::System.Collections.Generic.CollectionExtensions.TryAdd(dictionary, key, value);
  }
}