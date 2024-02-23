using Google.Protobuf;

namespace AspireSessionHost.OTel;

internal static class OTelUtils
{
    private const string ServiceName = "service.name";

    internal static string? GetServiceName(this Resource resource)
    {
        foreach (var attribute in resource.Attributes)
        {
            if (attribute.Key == ServiceName && attribute.Value.ValueCase is AnyValue.ValueOneofCase.StringValue)
            {
                return attribute.Value.StringValue;
            }
        }

        return null;
    }

    internal static string ToHexString(this ByteString bytes)
    {
        return ToHexString(bytes.Memory);
    }

    private static string ToHexString(ReadOnlyMemory<byte> bytes)
    {
        if (bytes.Length == 0)
        {
            return string.Empty;
        }

        return string.Create(bytes.Length * 2, bytes, static (chars, bytes) =>
        {
            var data = bytes.Span;
            for (var pos = 0; pos < data.Length; pos++)
            {
                ToCharsBuffer(data[pos], chars, pos * 2);
            }
        });
    }

    private static void ToCharsBuffer(byte value, Span<char> buffer, int startingIndex = 0)
    {
        var difference = ((value & 0xF0U) << 4) + (value & 0x0FU) - 0x8989U;
        var packedResult = (((uint)-(int)difference & 0x7070U) >> 4) + difference + 0xB9B9U | 0x2020U;

        buffer[startingIndex + 1] = (char)(packedResult & 0xFF);
        buffer[startingIndex] = (char)(packedResult >> 8);
    }
}
